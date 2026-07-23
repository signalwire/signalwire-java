/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

/**
 * Summary generation failed.
 *
 * <p>{@code summarize} returns EXACTLY ONE of {@code {summary}} (success) or {@code {error}}
 * (generation failed), and the failure rides the JSON-RPC <em>success</em> envelope — not an {@code
 * error} object — so it never reaches the error-code mapping. Surfaced here so a failed summary
 * can't masquerade as an empty string. {@link #getCode()} is {@code null} (no JSON-RPC code).
 */
public class SummaryError extends AIChatError {

  private static final long serialVersionUID = 1L;

  /**
   * @param code always {@code null} — the failure rode the success envelope.
   * @param message the server-provided failure message.
   */
  public SummaryError(Integer code, String message) {
    super(code, message);
  }
}
