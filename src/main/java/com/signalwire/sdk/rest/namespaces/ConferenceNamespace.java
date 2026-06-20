/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;

/** REST namespace for conference resources. */
public class ConferenceNamespace {

  private final CrudResource conferences;
  private final CrudResource participants;

  public ConferenceNamespace(HttpClient httpClient) {
    this.conferences = new CrudResource(httpClient, "/calling/conferences");
    this.participants = new CrudResource(httpClient, "/calling/conferences/participants");
  }

  public CrudResource conferences() {
    return conferences;
  }

  public CrudResource participants() {
    return participants;
  }
}
