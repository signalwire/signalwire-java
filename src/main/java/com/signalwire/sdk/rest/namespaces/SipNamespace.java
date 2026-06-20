/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;

/** REST namespace for SIP resources. */
public class SipNamespace {

  private final CrudResource endpoints;
  private final CrudResource profiles;

  public SipNamespace(HttpClient httpClient) {
    this.endpoints = new CrudResource(httpClient, "/sip/endpoints");
    this.profiles = new CrudResource(httpClient, "/sip/profiles");
  }

  public CrudResource endpoints() {
    return endpoints;
  }

  public CrudResource profiles() {
    return profiles;
  }
}
