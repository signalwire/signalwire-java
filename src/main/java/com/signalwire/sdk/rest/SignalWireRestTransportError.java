/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

/**
 * Raised when a REST request never reached a response — a transport-level failure (connection
 * refused, DNS failure, connection reset, TLS error).
 *
 * <p>A member of the {@link RestError} family: there is no HTTP response, so there is no status
 * code ({@link #getStatusCode()} returns {@link #NO_STATUS}, the sentinel for "no HTTP status") and
 * the response body is empty. The underlying transport exception (an {@link java.io.IOException} or
 * similar) is preserved via the standard {@link Throwable} cause chain. Because it extends {@link
 * RestError}, a caller catching that one type handles both an HTTP-error response and a transport
 * failure with a single {@code catch} — instead of a bare {@code IOException} leaking through.
 */
public class SignalWireRestTransportError extends RestError {

  /** Sentinel {@code statusCode} meaning "no HTTP response was ever reached". */
  public static final int NO_STATUS = 0;

  /**
   * @param method the HTTP method of the failed request
   * @param path the request path that failed to reach a server
   * @param url the full request URL that failed to reach a server
   * @param cause the underlying transport exception (e.g. {@link java.io.IOException})
   */
  public SignalWireRestTransportError(String method, String path, String url, Throwable cause) {
    super(NO_STATUS, method, path, url, causeMessage(cause), cause);
  }

  private static String causeMessage(Throwable cause) {
    return cause != null ? String.valueOf(cause.getMessage()) : "";
  }
}
