/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;

/** REST namespace for SWML script resources. */
public class SwmlNamespace {

  private final CrudResource scripts;

  public SwmlNamespace(HttpClient httpClient) {
    this.scripts = new CrudResource(httpClient, "/relay/swml/scripts");
  }

  public CrudResource scripts() {
    return scripts;
  }
}
