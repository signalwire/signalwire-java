/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import java.util.Collections;
import java.util.Map;

/**
 * The upstream JSON-RPC call {@link ChatGateway#prepare} built from a browser request: the service
 * method, its params, and the handle minted for a new conversation (if this call created one).
 * Java's typed stand-in for the reference's {@code (method, params, minted_handle)} tuple.
 */
public final class PreparedCall {

  private final String method;
  private final Map<String, Object> params;
  private final String mintedHandle;

  PreparedCall(String method, Map<String, Object> params, String mintedHandle) {
    this.method = method;
    this.params = Collections.unmodifiableMap(params);
    this.mintedHandle = mintedHandle;
  }

  /**
   * The chat service's JSON-RPC method: {@code chat}, {@code create_conversation}, {@code
   * end_conversation} or {@code chat_log}.
   *
   * @return the method.
   */
  public String getMethod() {
    return method;
  }

  /**
   * The JSON-RPC params, built by the gateway (never copied from the browser).
   *
   * @return an unmodifiable view of the params.
   */
  public Map<String, Object> getParams() {
    return params;
  }

  /**
   * The handle minted for a new conversation, set only on the call that created it.
   *
   * @return the minted handle, or {@code null}.
   */
  public String getMintedHandle() {
    return mintedHandle;
  }
}
