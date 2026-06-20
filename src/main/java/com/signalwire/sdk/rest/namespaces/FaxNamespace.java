/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;

/** REST namespace for fax resources. */
public class FaxNamespace {

  private final CrudResource faxes;

  public FaxNamespace(HttpClient httpClient) {
    this.faxes = new CrudResource(httpClient, "/fax/faxes");
  }

  public CrudResource faxes() {
    return faxes;
  }
}
