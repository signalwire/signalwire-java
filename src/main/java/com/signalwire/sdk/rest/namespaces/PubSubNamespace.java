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

/** REST namespace for Pub/Sub resources. */
public class PubSubNamespace {

  private final CrudResource channels;
  private final HttpClient httpClient;

  public PubSubNamespace(HttpClient httpClient) {
    this.httpClient = httpClient;
    this.channels = new CrudResource(httpClient, "/pubsub/channels");
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

  public CrudResource channels() {
    return channels;
  }

  /** Publish a message to a channel. */
  public Map<String, Object> publish(String channel, Map<String, Object> body) {
    return httpClient.post("/pubsub/channels/" + channel + "/publish", body);
  }
}
