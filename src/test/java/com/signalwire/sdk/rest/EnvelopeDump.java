/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EnvelopeDump — the Java port's ENVELOPE-DUMP program for the cross-port REST error-ENVELOPE
 * behavioral differ (porting-sdk/scripts/diff_port_envelope.py).
 *
 * <p>A wire-shape check (REST-COVERAGE) proves a route's SUCCESS body and that AN error status
 * surfaces; it cannot express HOW the REST client handles an error envelope, a 429/503, a malformed
 * body, a connection refused, or the RequestOptions retry/timeout/abort envelope (plan 4.2). This
 * program drives the SAME corpus the differ's Python oracle runs
 * (porting-sdk/scripts/envelope_corpus.py — the single source of truth, mirrored natively below)
 * against the live {@code mock_signalwire} harness ({@link MockTest}), and for each case observes
 * the RAISED typed error (or the success) reduced to the shared cross-port artifact:
 *
 * <pre>
 *   {
 *     "raised": bool,            // a typed error was raised (vs a success)
 *     "error_kind": "typed"|"bare:&lt;Class&gt;"|null,
 *     "status_code": int|null,   // the HTTP status the client decoded (null for transport)
 *     "body_error_code": string|null,  // errors[0].code decoded from the body
 *     "request_count": int       // journal hits for the path (1 == no retry, retries+1 for
 *                                //   a retry-armed case, 0 == transport)
 *   }
 * </pre>
 *
 * <p>Prints ONE JSON object mapping corpus-id -&gt; artifact to stdout; the differ byte-compares
 * each entry against Python's golden oracle.
 *
 * <p>Per-request {@link RequestOptions} (the {@code request_options} corpus field) are threaded
 * through the low-level {@link HttpClient} verb overloads directly — exactly as the Python oracle
 * calls {@code client._request(..., request_options=...)} — so the retry/idempotency contract is
 * exercised at the transport level (the CRUD resource surface intentionally carries no {@code
 * request_options} param, matching the reference). A {@code scenarioRepeat} count arms the SAME
 * override N times (FIFO) so a retry-armed case sees the failure on every attempt.
 *
 * <p>Run via the {@code envelopeDump} Gradle task:
 *
 * <pre>
 *   ./gradlew --no-daemon -q envelopeDump
 * </pre>
 */
final class EnvelopeDump {

  private EnvelopeDump() {}

  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  /** The GET list endpoint most cases target: a list route in every port's REST client. */
  private static final String ENDPOINT = "fabric.list_fabric_addresses";

  private static final String GET_PATH = "/fabric/addresses";
  private static final String GET_JOURNAL_PATH = "/api/fabric/addresses";

  /** A POST route present in every port — for the idempotency-asymmetry cases. */
  private static final String CREATE_ENDPOINT = "relay-rest.create_address";

  private static final String POST_PATH = "/relay/rest/addresses";
  private static final String POST_JOURNAL_PATH = "/api/relay/rest/addresses";

  /**
   * One corpus case. {@code status == null} => no scenario override (the 200 baseline); {@code
   * transport} => the connection-refused path (dead port, no scenario armed).
   *
   * @param method HTTP verb ("GET" or "POST")
   * @param scenarioRepeat how many times to arm the same override (FIFO)
   * @param requestOptions per-request RequestOptions, or null for the port default
   */
  private record Case(
      String id,
      String method,
      String endpoint,
      String path,
      String journalPath,
      Integer status,
      Object response,
      Map<String, String> headers,
      Integer delayMs,
      int scenarioRepeat,
      RequestOptions requestOptions,
      boolean transport) {

    static Case get(String id, Integer status, Object response) {
      return new Case(
          id,
          "GET",
          ENDPOINT,
          GET_PATH,
          GET_JOURNAL_PATH,
          status,
          response,
          null,
          null,
          1,
          null,
          false);
    }

    static Case getSuccess(String id) {
      return get(id, null, null);
    }

    static Case transportCase(String id) {
      return new Case(
          id, "GET", ENDPOINT, GET_PATH, GET_JOURNAL_PATH, null, null, null, null, 1, null, true);
    }
  }

  private static Map<String, Object> errorsBody(String code, String message) {
    Map<String, Object> err = new LinkedHashMap<>();
    err.put("code", code);
    err.put("message", message);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("errors", List.of(err));
    return body;
  }

  private static RequestOptions retryOpts(int retries) {
    return RequestOptions.builder().retries(retries).retryBackoff(0.0).build();
  }

  /**
   * Mirrors porting-sdk/scripts/envelope_corpus.py CORPUS exactly (case ids + scenarios +
   * request_options + scenario_repeat + POST cases). Keep in lock-step with the Python source — the
   * differ compares each artifact against Python's oracle for the same id.
   */
  private static final List<Case> corpus() {
    // 429 case also carries a Retry-After header (still no retry by DEFAULT).
    Case c429h =
        new Case(
            "envelope_429_retry_after",
            "GET",
            ENDPOINT,
            GET_PATH,
            GET_JOURNAL_PATH,
            429,
            errorsBody("RATE_LIMITED", "slow down"),
            Map.of("Retry-After", "2"),
            null,
            1,
            null,
            false);
    Case cDelayed =
        new Case(
            "envelope_503_delayed",
            "GET",
            ENDPOINT,
            GET_PATH,
            GET_JOURNAL_PATH,
            503,
            errorsBody("UNAVAILABLE", "slow-fail"),
            null,
            200,
            1,
            null,
            false);

    return List.of(
        Case.getSuccess("envelope_200_success"),
        Case.get("envelope_404_typed", 404, errorsBody("NOT_FOUND", "no such address")),
        c429h,
        Case.get("envelope_503_unavailable", 503, errorsBody("UNAVAILABLE", "maintenance")),
        // A deliberately non-JSON-object body: still a typed error, body_error_code null.
        Case.get("envelope_500_malformed_body", 500, "not-json-at-all <garbage"),
        Case.get("envelope_200_with_error_body", 200, errorsBody("SOFT_FAIL", "ignored on 2xx")),
        cDelayed,
        Case.transportCase("envelope_transport_refused"),
        // ---- RequestOptions envelope — opt-in retry (plan 4.2) --------------------
        // GET 503 with retries=1: retried once into the mock's default 200.
        new Case(
            "envelope_get_retry_once_succeeds",
            "GET",
            ENDPOINT,
            GET_PATH,
            GET_JOURNAL_PATH,
            503,
            errorsBody("UNAVAILABLE", "transient"),
            null,
            null,
            1,
            retryOpts(1),
            false),
        // GET 503 armed on BOTH attempts with retries=1: retries exhausted, typed 503 raised.
        new Case(
            "envelope_get_retry_exhausted",
            "GET",
            ENDPOINT,
            GET_PATH,
            GET_JOURNAL_PATH,
            503,
            errorsBody("UNAVAILABLE", "down"),
            null,
            null,
            2,
            retryOpts(1),
            false),
        // POST 500 with retries=2: NOT retried (idempotency safety).
        new Case(
            "envelope_post_500_not_retried",
            "POST",
            CREATE_ENDPOINT,
            POST_PATH,
            POST_JOURNAL_PATH,
            500,
            errorsBody("SERVER_ERROR", "boom"),
            null,
            null,
            1,
            retryOpts(2),
            false),
        // POST 503 with retries=1: retried (throttle is safe for a non-idempotent method).
        new Case(
            "envelope_post_503_retried",
            "POST",
            CREATE_ENDPOINT,
            POST_PATH,
            POST_JOURNAL_PATH,
            503,
            errorsBody("UNAVAILABLE", "throttled"),
            null,
            null,
            1,
            retryOpts(1),
            false));
  }

  /** Bind then immediately release a loopback TCP port -- a DEAD port once released. */
  private static int deadPort() {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    } catch (Exception e) {
      throw new IllegalStateException("EnvelopeDump: cannot allocate a dead port", e);
    }
  }

  /** Decode {@code errors[0].code} out of a raw response body (JSON string), or null. */
  private static String decodeBodyErrorCode(String body) {
    if (body == null || body.isEmpty()) {
      return null;
    }
    try {
      Object decoded = GSON.fromJson(body, Object.class);
      if (decoded instanceof Map<?, ?> map) {
        Object errs = map.get("errors");
        if (errs instanceof List<?> list
            && !list.isEmpty()
            && list.get(0) instanceof Map<?, ?> first) {
          Object code = first.get("code");
          return code instanceof String s ? s : null;
        }
      }
    } catch (Exception ignored) {
      // non-JSON body -> no decodable error code
    }
    return null;
  }

  private static Map<String, Object> artifact(
      boolean raised,
      String errorKind,
      Integer statusCode,
      String bodyErrorCode,
      int requestCount) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("raised", raised);
    m.put("error_kind", errorKind);
    m.put("status_code", statusCode);
    m.put("body_error_code", bodyErrorCode);
    m.put("request_count", requestCount);
    return m;
  }

  private static Map<String, Object> runCase(Case c) {
    MockTest.Bound bound = MockTest.newClient();
    HttpClient http = bound.client.getHttpClient();

    if (c.transport()) {
      // Point THIS case's client at a dead port -- connection-refused, nothing
      // reaches the mock, request_count stays 0.
      http =
          HttpClient.withBaseUrl(
              "http://127.0.0.1:" + deadPort() + "/api", bound.project, "test_tok");
    } else if (c.status() != null) {
      // Arm the override scenarioRepeat times (FIFO) so a retry-armed case sees the
      // failure on every attempt.
      for (int i = 0; i < c.scenarioRepeat(); i++) {
        bound.harness.scenarioSetFull(
            c.endpoint(), c.status(), c.response(), c.headers(), c.delayMs());
      }
    }

    boolean raised = false;
    String errorKind = null;
    Integer statusCode = null;
    String bodyErrorCode = null;

    try {
      if ("POST".equals(c.method())) {
        http.post(c.path(), Map.of("label", "x"), c.requestOptions());
      } else {
        http.get(c.path(), null, c.requestOptions());
      }
    } catch (RestError e) {
      raised = true;
      errorKind = "typed";
      int status = e.getStatusCode();
      statusCode = (status == SignalWireRestTransportError.NO_STATUS) ? null : status;
      bodyErrorCode = decodeBodyErrorCode(e.getResponseBody());
    } catch (Exception e) {
      // A leaked, non-family exception -- the contract violation the gate catches.
      raised = true;
      errorKind = "bare:" + e.getClass().getSimpleName();
    }

    int requestCount = 0;
    if (!c.transport()) {
      for (MockTest.JournalEntry j : bound.harness.journal()) {
        if (c.journalPath().equals(j.path)) {
          requestCount++;
        }
      }
    }

    return artifact(raised, errorKind, statusCode, bodyErrorCode, requestCount);
  }

  public static void main(String[] args) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Case c : corpus()) {
      out.put(c.id(), runCase(c));
    }
    System.out.println(GSON.toJson(out));
  }
}
