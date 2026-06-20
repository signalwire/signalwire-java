/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;

/** REST namespace for compliance resources (CNAM, SHAKEN/STIR). */
public class ComplianceNamespace {

  private final CrudResource cnamRegistrations;
  private final CrudResource shakenStir;

  public ComplianceNamespace(HttpClient httpClient) {
    this.cnamRegistrations = new CrudResource(httpClient, "/compliance/cnam");
    this.shakenStir = new CrudResource(httpClient, "/compliance/shaken_stir");
  }

  public CrudResource cnamRegistrations() {
    return cnamRegistrations;
  }

  public CrudResource shakenStir() {
    return shakenStir;
  }
}
