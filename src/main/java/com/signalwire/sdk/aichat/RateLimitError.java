/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

/** Project or conversation rate limit hit (JSON-RPC {@code -32005} / {@code -32006}). */
public class RateLimitError extends AIChatError {

  private static final long serialVersionUID = 1L;

  /**
   * @param code the JSON-RPC error code.
   * @param message the server-provided error message.
   */
  public RateLimitError(Integer code, String message) {
    super(code, message);
  }
}
