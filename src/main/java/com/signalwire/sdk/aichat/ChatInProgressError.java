/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

/** Another message is being processed for this conversation (JSON-RPC {@code -32007}). */
public class ChatInProgressError extends AIChatError {

  private static final long serialVersionUID = 1L;

  /**
   * @param code the JSON-RPC error code.
   * @param message the server-provided error message.
   */
  public ChatInProgressError(Integer code, String message) {
    super(code, message);
  }
}
