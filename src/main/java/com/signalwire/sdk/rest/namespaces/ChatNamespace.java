/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;

/** REST namespace for chat resources. */
public class ChatNamespace {

  private final CrudResource channels;
  private final CrudResource messages;
  private final CrudResource members;

  public ChatNamespace(HttpClient httpClient) {
    this.channels = new CrudResource(httpClient, "/chat/channels");
    this.messages = new CrudResource(httpClient, "/chat/messages");
    this.members = new CrudResource(httpClient, "/chat/members");
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
