/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;
import java.util.Map;

/** REST namespace for chat resources. */
public class ChatNamespace {

  private final HttpClient httpClient;
  private final CrudResource channels;
  private final CrudResource messages;
  private final CrudResource members;

  public ChatNamespace(HttpClient httpClient) {
    this.httpClient = httpClient;
    this.channels = new CrudResource(httpClient, "/chat/channels");
    this.messages = new CrudResource(httpClient, "/chat/messages");
    this.members = new CrudResource(httpClient, "/chat/members");
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

  public CrudResource channels() {
    return channels;
  }

  public CrudResource messages() {
    return messages;
  }

  public CrudResource members() {
    return members;
  }
}
