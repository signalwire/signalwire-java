/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

/**
 * Base error for AI Chat service failures.
 *
 * <p>Every typed subclass carries the JSON-RPC error {@code code} (or {@code null} when the failure
 * rode the success envelope, as with {@link SummaryError}) and the server {@code message}. Callers
 * catch this one family ({@code catch (AIChatError e)}) for every AI-Chat failure and can branch on
 * {@link #getCode()} or the subclass type.
 *
 * <p>Unchecked (extends {@link RuntimeException}), matching the SDK's existing error style ({@code
 * com.signalwire.sdk.rest.RestError}). Mirrors the python reference {@code
 * signalwire.ai_chat.AIChatError}.
 */
public class AIChatError extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final Integer code;
  private final String serverMessage;

  /**
   * @param code the JSON-RPC error code, or {@code null} for a success-envelope failure.
   * @param message the server-provided error message.
   */
  public AIChatError(Integer code, String message) {
    super("[" + code + "] " + message);
    this.code = code;
    this.serverMessage = message;
  }

  /**
   * The JSON-RPC error code, or {@code null} when the failure rode the success envelope (e.g.
   * {@link SummaryError}).
   *
   * @return the error code, or {@code null}.
   */
  public Integer getCode() {
    return code;
  }

  /**
   * The server-provided error message (without the {@code [code]} prefix {@link #getMessage()}
   * adds).
   *
   * @return the raw server message.
   */
  public String getServerMessage() {
    return serverMessage;
  }
}
