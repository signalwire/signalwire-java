/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

/** The conversation does not exist in this project (JSON-RPC {@code -32001}). */
public class ConversationNotFoundError extends AIChatError {

  private static final long serialVersionUID = 1L;

  /**
   * @param code the JSON-RPC error code.
   * @param message the server-provided error message.
   */
  public ConversationNotFoundError(Integer code, String message) {
    super(code, message);
  }
}
