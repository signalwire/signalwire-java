/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.HttpClient;
import java.util.Map;

/** REST namespace for chat resources — token minting only (matches Python's flat ChatResource). */
public class ChatNamespace {

  private final HttpClient httpClient;

  public ChatNamespace(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  /**
   * Create a chat token: POST {@code /api/chat/tokens}.
   *
   * <p>Mirrors Python's {@code ChatResource.create_token(**kwargs)} — the canonical chat-namespace
   * entry point.
   *
   * @param body token request fields (e.g. {@code ttl}, {@code channels}, {@code member_id})
   * @return the created token representation
   */
  public Map<String, Object> createToken(Map<String, Object> body) {
    return httpClient.post("/chat/tokens", body);
  }
}
