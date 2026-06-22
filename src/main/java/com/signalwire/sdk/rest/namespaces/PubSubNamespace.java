/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.HttpClient;
import java.util.Map;

/**
 * REST namespace for Pub/Sub resources — token minting only (matches Python's flat PubSubResource).
 */
public class PubSubNamespace {

  private final HttpClient httpClient;

  public PubSubNamespace(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  /**
   * Create a pub/sub token: POST {@code /api/pubsub/tokens}.
   *
   * <p>Mirrors Python's {@code PubSubResource.create_token(**kwargs)} — the canonical
   * pubsub-namespace entry point.
   *
   * @param body token request fields (e.g. {@code ttl}, {@code channels}, {@code member_id})
   * @return the created token representation
   */
  public Map<String, Object> createToken(Map<String, Object> body) {
    return httpClient.post("/pubsub/tokens", body);
  }
}
