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
 * body, or a connection refused. This program drives the SAME corpus the differ's Python oracle
 * runs (porting-sdk/scripts/envelope_corpus.py — the single source of truth, mirrored natively
 * below) against the live {@code mock_signalwire} harness ({@link MockTest}), and for each case
 * observes the RAISED typed error reduced to the shared cross-port artifact:
 *
 * <pre>
 *   {
 *     "raised": bool,            // a typed error was raised (vs a success)
 *     "error_kind": "typed"|"bare:&lt;Class&gt;"|null,
 *                                // "typed" == a member of the RestError family;
 *                                //   "bare:&lt;Class&gt;" == a leaked exception
 *     "status_code": int|null,   // the HTTP status the client decoded (null for a
 *                                //   transport failure -- no response reached)
 *     "body_error_code": string|null,  // errors[0].code decoded from the body
 *     "request_count": int       // journal hits for the path (1 == no retry,
 *                                //   0 == transport: nothing reached the server)
 *   }
 * </pre>
 *
 * <p>Prints ONE JSON object mapping corpus-id -&gt; artifact to stdout; the differ byte-compares
 * each entry against Python's golden oracle.
 *
 * <p>A {@code transport} case exercises the connection-refused path: the client is pointed at a
 * DEAD port (a free port bound then released, nothing listening), so no mock scenario is armed and
 * {@code request_count} is 0. A correct client raises its TYPED transport error ({@link
 * SignalWireRestTransportError}, a member of the {@link RestError} family, statusCode {@link
 * SignalWireRestTransportError#NO_STATUS} -&gt; reported as {@code null}); a client leaking a bare
 * transport exception would report {@code "bare:<name>"} and fail the byte-compare.
 *
 * <p>Each case uses a FRESH {@link MockTest#newClient()} (a unique random project -&gt; unique auth
 * header), so the journal view is scoped to that one case with zero entries to start -- no explicit
 * journal reset is needed. The mock server itself is probed-or-spawned by {@link MockTest} exactly
 * as the mock-backed unit tests do (honors {@code MOCK_SIGNALWIRE_PORT} when set, otherwise picks a
 * free port and spawns {@code python -m mock_signalwire}).
 *
 * <p>Run via the {@code envelopeDump} Gradle task (test classpath, since this lives alongside
 * {@link MockTest}):
 *
 * <pre>
 *   ./gradlew --no-daemon -q envelopeDump
 * </pre>
 */
final class EnvelopeDump {

  private EnvelopeDump() {}

  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  /** The endpoint every mock-armed case targets: a list route in every port's REST client. */
  private static final String ENDPOINT = "fabric.list_fabric_addresses";

  private static final String CALL_PATH = "/api/fabric/addresses";

  /**
   * One corpus case. {@code status == null} => no scenario override (the 200 baseline); {@code
   * transport} => the connection-refused path (dead port, no scenario armed).
   */
  private record Case(String id, Integer status, Object response, boolean transport) {
    static Case success(String id) {
      return new Case(id, null, null, false);
    }

    static Case armed(String id, int status, Object response) {
      return new Case(id, status, response, false);
    }

    static Case transportCase(String id) {
      return new Case(id, null, null, true);
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

  /**
   * Mirrors porting-sdk/scripts/envelope_corpus.py CORPUS exactly (case ids + scenarios). Keep in
   * lock-step with the Python source -- the differ compares each artifact against Python's oracle
   * for the same id.
   */
  private static final List<Case> CORPUS =
      List.of(
          Case.success("envelope_200_success"),
          Case.armed("envelope_404_typed", 404, errorsBody("NOT_FOUND", "no such address")),
          Case.armed("envelope_429_retry_after", 429, errorsBody("RATE_LIMITED", "slow down")),
          Case.armed("envelope_503_unavailable", 503, errorsBody("UNAVAILABLE", "maintenance")),
          // A deliberately non-JSON-object body: still a typed error, body_error_code null.
          Case.armed("envelope_500_malformed_body", 500, "not-json-at-all <garbage"),
          Case.armed(
              "envelope_200_with_error_body", 200, errorsBody("SOFT_FAIL", "ignored on 2xx")),
          Case.armed("envelope_503_delayed", 503, errorsBody("UNAVAILABLE", "slow-fail")),
          Case.transportCase("envelope_transport_refused"));

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
    RestClient client = bound.client;

    if (c.transport()) {
      // Point THIS case's client at a dead port -- connection-refused, nothing
      // reaches the mock, request_count stays 0.
      client = RestClient.withBaseUrl("http://127.0.0.1:" + deadPort(), bound.project, "test_tok");
    } else if (c.status() != null) {
      bound.harness.scenarioSetRaw(ENDPOINT, c.status(), c.response());
    }

    boolean raised = false;
    String errorKind = null;
    Integer statusCode = null;
    String bodyErrorCode = null;

    try {
      client.fabric().addresses().list();
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
        if (CALL_PATH.equals(j.path)) {
          requestCount++;
        }
      }
    }

    return artifact(raised, errorKind, statusCode, bodyErrorCode, requestCount);
  }

  public static void main(String[] args) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Case c : CORPUS) {
      out.put(c.id(), runCase(c));
    }
    System.out.println(GSON.toJson(out));
  }
}
