/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

/** Missing/rejected identity (HTTP 401 / JSON-RPC {@code -32009}). */
public class AuthenticationError extends AIChatError {

  private static final long serialVersionUID = 1L;

  /**
   * @param code the JSON-RPC error code.
   * @param message the server-provided error message.
   */
  public AuthenticationError(Integer code, String message) {
    super(code, message);
  }
}
