/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Exception for SignalWire REST API errors.
 *
 * <p>Contains the HTTP status code and error message from the server.
 *
 * <p>§6.6 error-observability: {@link #getHeaders()} exposes the response header map (empty for a
 * transport error that produced no response) and {@link #getRequestId()} is the platform request id
 * pulled from those headers — client-side observability with NO wire-contract change, so a caller
 * can log/correlate a failure against SignalWire's own request id.
 */
public class RestError extends RuntimeException {

  /**
   * Response header names SignalWire (and common proxies) use for the platform request id, in
   * preference order. Matched case-insensitively. Mirrors the reference's {@code
   * _REQUEST_ID_HEADERS} (_base.py).
   */
  private static final List<String> REQUEST_ID_HEADERS =
      List.of("x-request-id", "x-signalwire-request-id", "request-id", "x-amzn-requestid");

  private final int statusCode;
  private final String method;
  private final String path;
  private final String url;
  private final String responseBody;
  private final Map<String, String> headers;
  private final String requestId;

  public RestError(int statusCode, String method, String path, String url, String responseBody) {
    this(statusCode, method, path, url, responseBody, (Map<String, String>) null);
  }

  public RestError(
      int statusCode,
      String method,
      String path,
      String url,
      String responseBody,
      Map<String, String> headers) {
    super(formatMessage(statusCode, method, url, responseBody, extractRequestId(headers)));
    this.statusCode = statusCode;
    this.method = method;
    this.path = path;
    this.url = url;
    this.responseBody = responseBody;
    this.headers = headers != null ? Map.copyOf(headers) : Collections.emptyMap();
    this.requestId = extractRequestId(headers);
  }

  public RestError(
      int statusCode,
      String method,
      String path,
      String url,
      String responseBody,
      Throwable cause) {
    this(statusCode, method, path, url, responseBody, null, cause);
  }

  public RestError(
      int statusCode,
      String method,
      String path,
      String url,
      String responseBody,
      Map<String, String> headers,
      Throwable cause) {
    super(formatMessage(statusCode, method, url, responseBody, extractRequestId(headers)), cause);
    this.statusCode = statusCode;
    this.method = method;
    this.path = path;
    this.url = url;
    this.responseBody = responseBody;
    this.headers = headers != null ? Map.copyOf(headers) : Collections.emptyMap();
    this.requestId = extractRequestId(headers);
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getMethod() {
    return method;
  }

  public String getPath() {
    return path;
  }

  /** The full request URL (including query string) that produced the error. */
  public String getUrl() {
    return url;
  }

  public String getResponseBody() {
    return responseBody;
  }

  /**
   * The response headers captured from the failing response (empty for a transport error that
   * produced no response). §6.6 client-side observability — no wire-contract change.
   */
  public Map<String, String> getHeaders() {
    return headers;
  }

  /**
   * The platform request id pulled from the response headers ({@code x-request-id} / {@code
   * x-signalwire-request-id} / {@code request-id} / {@code x-amzn-requestid}, case-insensitive),
   * for correlating a failure against SignalWire's own logs. Empty when no response reached or no
   * such header was present.
   */
  public Optional<String> getRequestId() {
    return Optional.ofNullable(requestId);
  }

  private static String extractRequestId(Map<String, String> headers) {
    if (headers == null || headers.isEmpty()) {
      return null;
    }
    for (String name : REQUEST_ID_HEADERS) {
      for (Map.Entry<String, String> e : headers.entrySet()) {
        if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
          return e.getValue();
        }
      }
    }
    return null;
  }

  /** Whether the error is a client error (4xx). */
  public boolean isClientError() {
    return statusCode >= 400 && statusCode < 500;
  }

  /** Whether the error is a server error (5xx). */
  public boolean isServerError() {
    return statusCode >= 500 && statusCode < 600;
  }

  /** Whether the resource was not found (404). */
  public boolean isNotFound() {
    return statusCode == 404;
  }

  /** Whether access was denied (401 or 403). */
  public boolean isUnauthorized() {
    return statusCode == 401 || statusCode == 403;
  }

  private static String formatMessage(
      int statusCode, String method, String url, String responseBody, String requestId) {
    StringBuilder sb = new StringBuilder();
    sb.append("SignalWire REST API error: ");
    sb.append(method).append(" ").append(url);
    if (statusCode == SignalWireRestTransportError.NO_STATUS) {
      // Transport failure: the request never reached a response (connection
      // refused / DNS / reset / TLS). Mirrors the Python reference's
      // "{method} {url} failed to reach the server: {body}" message shape.
      sb.append(" failed to reach the server");
    } else {
      sb.append(" returned ").append(statusCode);
    }
    if (responseBody != null && !responseBody.isEmpty()) {
      String body =
          responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
      sb.append(" - ").append(body);
    }
    if (requestId != null && !requestId.isEmpty()) {
      sb.append(" (request-id: ").append(requestId).append(")");
    }
    return sb.toString();
  }
}
