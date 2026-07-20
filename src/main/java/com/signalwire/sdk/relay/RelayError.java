/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

/**
 * Runtime exception for RELAY-level failures (request timeout, error-frame, non-2xx result code,
 * dead/half-open connection, dial timeout, connect rejected, etc.).
 *
 * <p>Mirrors the Python reference {@code RelayError(code, message)} (relay/client.py:1295): it
 * carries the RELAY error {@link #getCode() code} so callers can branch on it. Server-returned
 * errors use the code from the error frame / non-2xx result; client-side failures (timeout, dead
 * connection) use {@link #UNKNOWN_CODE} ({@value #UNKNOWN_CODE}), matching the reference's {@code
 * RelayError(-1, ...)} convention.
 */
public class RelayError extends RuntimeException {

  /** Sentinel code for a client-side failure with no server code (Python's {@code -1}). */
  public static final int UNKNOWN_CODE = -1;

  private final int code;

  /**
   * RELAY error with an explicit server code (parity with Python {@code RelayError(code, msg)}).
   */
  public RelayError(int code, String message) {
    super("RELAY error " + code + ": " + message);
    this.code = code;
  }

  public RelayError(String message) {
    super(message);
    this.code = UNKNOWN_CODE;
  }

  public RelayError(String message, Throwable cause) {
    super(message, cause);
    this.code = UNKNOWN_CODE;
  }

  public RelayError(int code, String message, Throwable cause) {
    super("RELAY error " + code + ": " + message, cause);
    this.code = code;
  }

  /**
   * The RELAY error code (from the server error frame or non-2xx result), or {@link #UNKNOWN_CODE}
   * for a client-side failure.
   */
  public int getCode() {
    return code;
  }
}
