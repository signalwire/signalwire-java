/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

/**
 * Exception for SignalWire REST API errors.
 *
 * <p>Contains the HTTP status code and error message from the server.
 */
public class RestError extends RuntimeException {

  private final int statusCode;
  private final String method;
  private final String path;
  private final String url;
  private final String responseBody;

  public RestError(int statusCode, String method, String path, String url, String responseBody) {
    super(formatMessage(statusCode, method, url, responseBody));
    this.statusCode = statusCode;
    this.method = method;
    this.path = path;
    this.url = url;
    this.responseBody = responseBody;
  }

  public RestError(
      int statusCode,
      String method,
      String path,
      String url,
      String responseBody,
      Throwable cause) {
    super(formatMessage(statusCode, method, url, responseBody), cause);
    this.statusCode = statusCode;
    this.method = method;
    this.path = path;
    this.url = url;
    this.responseBody = responseBody;
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
      int statusCode, String method, String url, String responseBody) {
    StringBuilder sb = new StringBuilder();
    sb.append("SignalWire REST API error: ");
    sb.append(method).append(" ").append(url);
    sb.append(" returned ").append(statusCode);
    if (responseBody != null && !responseBody.isEmpty()) {
      String body =
          responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
      sb.append(" - ").append(body);
    }
    return sb.toString();
  }
}
