/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.rest.RequestOptionsSupport.EffectiveOptions;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP client for the SignalWire REST API.
 *
 * <p>Uses {@code java.net.http.HttpClient} (JDK 11+ built-in) with Basic Auth and JSON content
 * types. Provides low-level GET, POST, PUT, DELETE, PATCH methods used by {@link CrudResource} and
 * namespace classes.
 *
 * <p>Transport behavior — per-attempt timeout, opt-in idempotency-aware retries with exponential
 * backoff (honoring {@code Retry-After}), and cooperative cancellation — is governed by the {@link
 * RequestOptions} envelope (plan 4.2), supplied at two levels: a client default (stored on this
 * client) and an optional per-request override on each verb. An unset field on either resolves to
 * the next level down and finally the built-in floor. Mirrors the Python reference's {@code
 * HttpClient}.
 */
public class HttpClient {

  private static final Logger log = Logger.getLogger(HttpClient.class);
  private static final Gson gson = new Gson();
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

  private final String baseUrl;
  private final String authHeader;
  private final java.net.http.HttpClient httpClient;
  private final RequestOptions requestOptions;

  /**
   * Create an HTTP client.
   *
   * @param space SignalWire space (e.g., "example.signalwire.com")
   * @param project project ID used as Basic Auth username
   * @param token API token used as Basic Auth password
   */
  public HttpClient(String space, String project, String token) {
    this(space, project, token, null);
  }

  /**
   * Create an HTTP client with a client-default {@link RequestOptions} applied to every request.
   *
   * @param space SignalWire space (e.g., "example.signalwire.com")
   * @param project project ID used as Basic Auth username
   * @param token API token used as Basic Auth password
   * @param requestOptions client-default transport options (may be {@code null})
   */
  public HttpClient(String space, String project, String token, RequestOptions requestOptions) {
    // A loopback host (127.0.0.1[:port] / localhost[:port] / [::1][:port]) is a local
    // mock/dev server that speaks plain HTTP — use http:// for it; every other host is
    // the real platform over https://. This lets a shipped example run verbatim against
    // the local mock (SIGNALWIRE_SPACE=127.0.0.1:<port>) without a code change or a
    // separate URL knob; production is unaffected (a real <name>.signalwire.com space is
    // never loopback). Parity with the Python reference rest/_base.py `_is_loopback_host`.
    String scheme = isLoopbackHost(space) ? "http" : "https";
    this.baseUrl = scheme + "://" + space + "/api";
    this.authHeader = basicAuth(project, token);
    this.requestOptions = requestOptions;
    this.httpClient = newHttpClient();
  }

  /**
   * True if {@code host} (a bare host or {@code host:port}) is a local loopback address — a local
   * mock/dev server that speaks plain HTTP. Used to pick http:// vs https:// so a shipped example
   * runs verbatim against the local mock. A real SignalWire space ({@code <name>.signalwire.com})
   * is never loopback, so production is unaffected. Mirrors the Python reference {@code
   * rest/_base.py::_is_loopback_host}.
   */
  static boolean isLoopbackHost(String host) {
    if (host == null) {
      return false;
    }
    // Match the Python reference exactly: hostname = host.rsplit(":", 1)[0] if ":" in host
    // else host. So any colon (bare host:port or an IPv6 literal) → take everything before
    // the LAST colon; then compare against the loopback set.
    int lastColon = host.lastIndexOf(':');
    String hostname = lastColon >= 0 ? host.substring(0, lastColon) : host;
    return "127.0.0.1".equals(hostname)
        || "localhost".equals(hostname)
        || "::1".equals(hostname)
        || "[::1]".equals(hostname);
  }

  /**
   * Create an HTTP client with an explicit base URL (e.g., plain HTTP for local integration tests,
   * or to point a {@link RestClient} at an audit fixture). Production callers use the {@code
   * (space, project, token)} constructor instead.
   *
   * @param baseUrl fully qualified base URL ending in {@code /api}
   * @param project project ID used as Basic Auth username
   * @param token API token used as Basic Auth password
   * @return a configured HTTP client
   */
  public static HttpClient withBaseUrl(String baseUrl, String project, String token) {
    return withBaseUrl(baseUrl, project, token, null);
  }

  /**
   * Create an HTTP client with an explicit base URL and a client-default {@link RequestOptions}.
   *
   * @param baseUrl fully qualified base URL ending in {@code /api}
   * @param project project ID used as Basic Auth username
   * @param token API token used as Basic Auth password
   * @param requestOptions client-default transport options (may be {@code null})
   * @return a configured HTTP client
   */
  public static HttpClient withBaseUrl(
      String baseUrl, String project, String token, RequestOptions requestOptions) {
    return new HttpClient(baseUrl, project, token, requestOptions, null);
  }

  private HttpClient(
      String baseUrl, String project, String token, RequestOptions requestOptions, Void unused) {
    this.baseUrl = baseUrl;
    this.authHeader = basicAuth(project, token);
    this.requestOptions = requestOptions;
    this.httpClient = newHttpClient();
  }

  private static String basicAuth(String project, String token) {
    String credentials = project + ":" + token;
    return "Basic "
        + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
  }

  private static java.net.http.HttpClient newHttpClient() {
    java.net.http.HttpClient.Builder builder =
        java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT);
    // A5 fleet CA-var contract (hard-cut, no aliases): a custom CA bundle for the REST transport is
    // supplied via SIGNALWIRE_REST_CA_FILE. When set, build an SSLContext that trusts ONLY that
    // bundle so a private/self-signed CA is honored (parity with the Python reference
    // rest/_base.py:163 `self._session.verify = _rest_ca_file`). Unset → the JDK default trust
    // store.
    String caFile = System.getenv("SIGNALWIRE_REST_CA_FILE");
    if (caFile != null && !caFile.isEmpty()) {
      builder.sslContext(TlsContext.fromCaFile(caFile));
    }
    return builder.build();
  }

  // ── Public methods ───────────────────────────────────────────────

  /** GET request, returns parsed JSON as a Map. */
  public Map<String, Object> get(String path) {
    return get(path, null, null);
  }

  /** GET request with query parameters. */
  public Map<String, Object> get(String path, Map<String, String> queryParams) {
    return get(path, queryParams, null);
  }

  /** GET request with query parameters and a per-request {@link RequestOptions} override. */
  public Map<String, Object> get(
      String path, Map<String, String> queryParams, RequestOptions requestOptions) {
    String url = buildUrl(path, queryParams);
    return execute("GET", path, url, null, requestOptions);
  }

  /** POST request with JSON body. */
  public Map<String, Object> post(String path, Map<String, Object> body) {
    return post(path, body, null);
  }

  /** POST request with JSON body and a per-request {@link RequestOptions} override. */
  public Map<String, Object> post(
      String path, Map<String, Object> body, RequestOptions requestOptions) {
    String url = buildUrl(path, null);
    String json = body != null ? gson.toJson(body) : "{}";
    return execute("POST", path, url, json, requestOptions);
  }

  /** PUT request with JSON body. */
  public Map<String, Object> put(String path, Map<String, Object> body) {
    return put(path, body, null);
  }

  /** PUT request with JSON body and a per-request {@link RequestOptions} override. */
  public Map<String, Object> put(
      String path, Map<String, Object> body, RequestOptions requestOptions) {
    String url = buildUrl(path, null);
    String json = body != null ? gson.toJson(body) : "{}";
    return execute("PUT", path, url, json, requestOptions);
  }

  /**
   * PATCH request with JSON body. java.net.http.HttpRequest doesn't have a dedicated builder for
   * PATCH, so use {@code method("PATCH", ...)}.
   */
  public Map<String, Object> patch(String path, Map<String, Object> body) {
    return patch(path, body, null);
  }

  /** PATCH request with JSON body and a per-request {@link RequestOptions} override. */
  public Map<String, Object> patch(
      String path, Map<String, Object> body, RequestOptions requestOptions) {
    String url = buildUrl(path, null);
    String json = body != null ? gson.toJson(body) : "{}";
    return execute("PATCH", path, url, json, requestOptions);
  }

  /** DELETE request. */
  public Map<String, Object> delete(String path) {
    return delete(path, null);
  }

  /** DELETE request with a per-request {@link RequestOptions} override. */
  public Map<String, Object> delete(String path, RequestOptions requestOptions) {
    String url = buildUrl(path, null);
    return execute("DELETE", path, url, null, requestOptions);
  }

  /** Get the base URL. */
  public String getBaseUrl() {
    return baseUrl;
  }

  // ── Internal ─────────────────────────────────────────────────────

  private HttpRequest buildRequest(String method, String url, String jsonBody, Duration timeout) {
    HttpRequest.Builder b =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .timeout(timeout);
    switch (method) {
      case "GET":
        b.GET();
        break;
      case "DELETE":
        b.DELETE();
        break;
      case "POST":
        b.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .header("Content-Type", "application/json");
        break;
      case "PUT":
        b.PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
            .header("Content-Type", "application/json");
        break;
      default:
        b.method(method, HttpRequest.BodyPublishers.ofString(jsonBody))
            .header("Content-Type", "application/json");
    }
    return b.build();
  }

  /**
   * Drive the request with the resolved {@link RequestOptions} envelope: cooperative-cancellation
   * check before each attempt, per-attempt timeout, and opt-in idempotency-aware retry with
   * exponential backoff (honoring {@code Retry-After}). Total attempts = {@code retries + 1}.
   * Mirrors the Python reference's {@code HttpClient._request}.
   */
  private Map<String, Object> execute(
      String method, String path, String url, String jsonBody, RequestOptions perRequest) {
    EffectiveOptions opts = RequestOptionsSupport.resolve(this.requestOptions, perRequest);
    Duration timeout = Duration.ofMillis(Math.max(1L, Math.round(opts.timeout() * 1000.0)));

    int attempt = 0;
    while (true) {
      attempt++;

      // Cancelled before this attempt — surface as the transport-error family
      // (no response was produced), not a bare exception.
      if (opts.abortSignal() != null && opts.abortSignal().isSet()) {
        throw new SignalWireRestTransportError(
            method,
            path,
            url,
            new java.util.concurrent.CancellationException("request cancelled by abortSignal"));
      }

      HttpRequest request = buildRequest(method, url, jsonBody, timeout);
      try {
        log.debug("%s %s", method, url);
        HttpResponse<String> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();

        if (statusCode >= 200 && statusCode < 300) {
          String body = response.body();
          if (body == null || body.isEmpty()) {
            return Collections.emptyMap();
          }
          return gson.fromJson(body, new TypeToken<Map<String, Object>>() {}.getType());
        }

        // A non-2xx response. Retry if attempts remain AND the status is retryable
        // for this method (idempotency-aware), honoring Retry-After then backoff.
        if (attempt <= opts.retries()
            && RequestOptionsSupport.statusIsRetryable(method, statusCode, opts)) {
          double delay = retryAfterSeconds(response);
          if (delay < 0) {
            delay = opts.retryBackoff() * Math.pow(2, attempt - 1);
          }
          sleep(delay);
          continue;
        }
        throw new RestError(
            statusCode, method, path, url, response.body(), firstValueHeaders(response));
      } catch (RestError e) {
        throw e;
      } catch (InterruptedException e) {
        // Restore the interrupt status that HttpClient.send() cleared when it threw,
        // so callers up the stack can still observe the cancellation. Do NOT swallow it.
        Thread.currentThread().interrupt();
        throw new SignalWireRestTransportError(method, path, url, e);
      } catch (Exception e) {
        // Transport-level failure: the request never reached a response (connection
        // refused, DNS failure, connection reset, TLS error, per-attempt timeout).
        // Retry if attempts remain, else wrap in the typed SignalWireRestError family
        // (SignalWireRestTransportError, statusCode == NO_STATUS) so a caller catching
        // RestError handles it too, instead of a bare transport exception leaking out.
        // The underlying exception is preserved via the standard cause chain (Java's
        // equivalent of Python's `raise ... from exc`). Mirrors the Python reference.
        if (attempt <= opts.retries()) {
          sleep(opts.retryBackoff() * Math.pow(2, attempt - 1));
          continue;
        }
        throw new SignalWireRestTransportError(method, path, url, e);
      }
    }
  }

  /**
   * Flatten the response headers into a first-value {@code Map<String,String>} for {@link
   * RestError} (§6.6 error-observability). Multi-valued headers keep their first value — sufficient
   * for the request-id headers we surface.
   */
  private static Map<String, String> firstValueHeaders(HttpResponse<String> response) {
    Map<String, String> out = new LinkedHashMap<>();
    response
        .headers()
        .map()
        .forEach(
            (k, v) -> {
              if (v != null && !v.isEmpty()) {
                out.put(k, v.get(0));
              }
            });
    return out;
  }

  /** Parse a {@code Retry-After} header (delta-seconds form) if present, else {@code -1}. */
  private double retryAfterSeconds(HttpResponse<String> response) {
    Optional<String> value = response.headers().firstValue("Retry-After");
    if (value.isEmpty()) {
      return -1;
    }
    try {
      return Double.parseDouble(value.get().trim());
    } catch (NumberFormatException e) {
      // HTTP-date form: fall back to computed backoff.
      return -1;
    }
  }

  /**
   * Backoff sleep between retries. A seam kept simple: the mock proves attempt ORDERING, not real
   * time (the differ pins {@code retryBackoff = 0}).
   */
  private void sleep(double seconds) {
    if (seconds <= 0) {
      return;
    }
    try {
      Thread.sleep(Math.round(seconds * 1000.0));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private String buildUrl(String path, Map<String, String> queryParams) {
    StringBuilder url = new StringBuilder(baseUrl);
    if (path != null && !path.isEmpty()) {
      if (!path.startsWith("/")) {
        url.append("/");
      }
      url.append(path);
    }

    if (queryParams != null && !queryParams.isEmpty()) {
      url.append("?");
      boolean first = true;
      for (Map.Entry<String, String> entry : queryParams.entrySet()) {
        if (!first) url.append("&");
        url.append(encodeParam(entry.getKey())).append("=").append(encodeParam(entry.getValue()));
        first = false;
      }
    }

    return url.toString();
  }

  private String encodeParam(String value) {
    try {
      return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    } catch (Exception e) {
      return value;
    }
  }
}
