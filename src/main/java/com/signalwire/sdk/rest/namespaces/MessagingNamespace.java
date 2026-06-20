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

/** REST namespace for messaging (SMS/MMS) resources. */
public class MessagingNamespace {

  private final CrudResource messages;
  private final HttpClient httpClient;

  public MessagingNamespace(HttpClient httpClient) {
    this.httpClient = httpClient;
    this.messages = new CrudResource(httpClient, "/messaging/messages");
  }

  public CrudResource messages() {
    return messages;
  }

  /** Send a message via REST. */
  public Map<String, Object> send(Map<String, Object> body) {
    return httpClient.post("/messaging/messages", body);
  }
}
