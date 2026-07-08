package com.signalwire.sdk.runtime;

import com.signalwire.sdk.swml.Service;
import com.signalwire.sdk.swml.Service.HttpResult;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-platform serverless dispatch that funnels every platform's request shape through the
 * framework-free {@link Service#handleRequest} dispatch core — the same core the in-process HTTP
 * server, {@code serve()}, and {@code asRouter()} use.
 *
 * <p>Java's Lambda path lives in {@link com.signalwire.sdk.runtime.lambda.LambdaAgentHandler}. This
 * adapter adds the remaining platforms the reference (Python {@code serverless_mixin.py}) and the
 * PHP reference ({@code Serverless\Adapter}) dispatch: <b>CGI</b> and <b>Google Cloud Functions</b>
 * (GCF). Each extracts {@code (method, path, headers, body)} from the platform's shape, calls
 * {@link Service#handleRequest}, and marshals the returned {@code (status, headers, body)} triple
 * back into the platform's response form — a real dispatched response, never a fall-through to the
 * long-running server or an "unsupported" error.
 *
 * <p>Following the injectable-env pattern used throughout the runtime package (Java cannot mutate
 * {@link System#getenv()}), the platform-reading entrypoints take an {@link EnvProvider} and the
 * request body explicitly so the {@code swaig-test --simulate-serverless} harness and unit tests
 * can drive every branch deterministically.
 */
public final class ServerlessAdapter {

  private ServerlessAdapter() {}

  /**
   * Response envelope shared by the platform adapters: an HTTP status, headers, and body. GCF wraps
   * this directly; CGI serialises it to a status-line + headers + body byte stream.
   *
   * @param status the HTTP status code.
   * @param headers the response headers.
   * @param body the response body.
   */
  public record Response(int status, Map<String, String> headers, String body) {}

  /**
   * Dispatch a Google Cloud Functions invocation through {@link Service#handleRequest}.
   *
   * <p>Mirrors Python {@code _handle_google_cloud_function_request} and PHP {@code
   * Adapter::handleGcf}: reads the method / path / headers off the request, forwards the body, and
   * returns the {@code (status, headers, body)} response the Flask/functions-framework layer emits.
   *
   * @param agent the SWML service / agent request handler.
   * @param method the HTTP method (e.g. {@code "POST"}).
   * @param path the request path (e.g. {@code "/"} or {@code "/sip"}).
   * @param headers the request headers (case-insensitive Authorization lookup happens downstream).
   * @param body the raw request body, or {@code null} for a body-less GET.
   * @return the dispatched response.
   */
  public static Response handleGcf(
      Service agent, String method, String path, Map<String, String> headers, String body) {
    HttpResult result = dispatch(agent, method, path, headers, body);
    Map<String, String> respHeaders = new LinkedHashMap<>(result.headers());
    respHeaders.putIfAbsent("Content-Type", "application/json");
    return new Response(result.status(), respHeaders, result.body());
  }

  /**
   * Dispatch a CGI/FastCGI invocation through {@link Service#handleRequest} and serialise the
   * response to the CGI wire form (a {@code Status:} line, the response headers, a blank line, then
   * the body).
   *
   * <p>Mirrors Python's {@code mode == "cgi"} branch and PHP {@code Adapter::handleCgi}. Path and
   * method are read from the supplied {@link EnvProvider} ({@code PATH_INFO} / {@code
   * REQUEST_METHOD} — the standard CGI meta-variables); the body is passed explicitly (the real CGI
   * runtime reads it from stdin, bounded by {@code CONTENT_LENGTH}).
   *
   * @param agent the SWML service / agent request handler.
   * @param env the CGI environment (supplies {@code REQUEST_METHOD}, {@code PATH_INFO}).
   * @param headers the request headers (e.g. Authorization reconstructed from {@code HTTP_*}).
   * @param body the request body (already read from stdin), or {@code null}.
   * @return the full CGI response payload (status line + headers + blank line + body).
   */
  public static String handleCgi(
      Service agent, EnvProvider env, Map<String, String> headers, String body) {
    String method = orDefault(env.get("REQUEST_METHOD"), "GET");
    String path = env.get("PATH_INFO");
    if (path == null || path.isEmpty()) {
      path = orDefault(env.get("REQUEST_URI"), "/");
    }
    // Strip a query string from the path.
    int q = path.indexOf('?');
    if (q >= 0) {
      path = path.substring(0, q);
    }

    HttpResult result = dispatch(agent, method, path, headers, body);

    StringBuilder sb = new StringBuilder();
    sb.append("Status: ").append(result.status()).append(' ').append(statusText(result.status()));
    sb.append("\r\n");
    boolean hasContentType = false;
    for (Map.Entry<String, String> h : result.headers().entrySet()) {
      sb.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
      if ("Content-Type".equalsIgnoreCase(h.getKey())) {
        hasContentType = true;
      }
    }
    if (!hasContentType) {
      sb.append("Content-Type: application/json\r\n");
    }
    sb.append("\r\n");
    sb.append(result.body() != null ? result.body() : "");
    return sb.toString();
  }

  /**
   * Decode a possibly-base64 body (Lambda/API-Gateway carry {@code isBase64Encoded}). Exposed so
   * the Lambda adapter and tests share one decoder.
   *
   * @param body the raw body string, or {@code null}.
   * @param base64 whether {@code body} is base64-encoded.
   * @return the decoded UTF-8 body, or {@code null} if the input was {@code null} or undecodable.
   */
  public static String decodeBody(String body, boolean base64) {
    if (body == null) {
      return null;
    }
    if (!base64) {
      return body;
    }
    try {
      return new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static HttpResult dispatch(
      Service agent, String method, String path, Map<String, String> headers, String body) {
    Map<String, String> h = headers != null ? headers : new LinkedHashMap<>();
    Map<String, Object> parsed = null;
    if (body != null && !body.isEmpty()) {
      try {
        parsed =
            new com.google.gson.Gson()
                .fromJson(
                    body,
                    new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
      } catch (Exception ignored) {
        // Leave parsed = null; handleRequest treats a null/empty body as a plain render.
      }
    }
    // handleRequest reconstructs the callback path from the URL; a bare path is sufficient.
    String url = path == null || path.isEmpty() ? "/" : path;
    return agent.handleRequest(method == null ? "GET" : method, url, h, parsed);
  }

  private static String orDefault(String v, String dflt) {
    return v == null || v.isEmpty() ? dflt : v;
  }

  private static String statusText(int status) {
    switch (status) {
      case 200:
        return "OK";
      case 307:
        return "Temporary Redirect";
      case 401:
        return "Unauthorized";
      case 403:
        return "Forbidden";
      case 404:
        return "Not Found";
      case 413:
        return "Payload Too Large";
      case 500:
        return "Internal Server Error";
      default:
        return "";
    }
  }
}
