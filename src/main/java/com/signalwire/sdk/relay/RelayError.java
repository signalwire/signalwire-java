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
 * <p>Carries the RELAY error {@link #getCode() code} so callers can branch on it. Server-returned
 * errors use the code from the error frame / non-2xx result; client-side failures (timeout, dead
 * connection) use {@link #UNKNOWN_CODE} ({@value #UNKNOWN_CODE}).
 */
public class RelayError extends RuntimeException {

  /** Sentinel code for a client-side failure that carries no server code. */
  public static final int UNKNOWN_CODE = -1;

  private final int code;

  /**
   * The RAW server message (the {@code message} construction param). Kept separately from the
   * {@link Throwable} detail message, which is decorated as {@code "RELAY error {code}: {message}"}
   * — reading {@code getMessage()} would return that decorated form, so the bare value is stored
   * explicitly.
   */
  private final String serverMessage;

  /** RELAY error with an explicit server code. */
  public RelayError(int code, String message) {
    super("RELAY error " + code + ": " + message);
    this.code = code;
    this.serverMessage = message;
  }

  public RelayError(String message) {
    super(message);
    this.code = UNKNOWN_CODE;
    this.serverMessage = message;
  }

  public RelayError(String message, Throwable cause) {
    super(message, cause);
    this.code = UNKNOWN_CODE;
    this.serverMessage = message;
  }

  public RelayError(int code, String message, Throwable cause) {
    super("RELAY error " + code + ": " + message, cause);
    this.code = code;
    this.serverMessage = message;
  }

  /**
   * The RELAY error code (from the server error frame or non-2xx result), or {@link #UNKNOWN_CODE}
   * for a client-side failure.
   */
  public int getCode() {
    return code;
  }

  /**
   * The raw server message (the {@code message} construction param), undecorated. Named {@code
   * getServerMessage} because {@link Throwable#getMessage()} is already taken by the decorated
   * detail message — the same reason {@code AIChatError} uses this spelling.
   *
   * @return the undecorated server message.
   */
  public String getServerMessage() {
    return serverMessage;
  }
}
