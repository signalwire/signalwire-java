/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;

/** REST namespace for billing resources. */
public class BillingNamespace {

  private final CrudResource invoices;
  private final CrudResource usage;

  public BillingNamespace(HttpClient httpClient) {
    this.invoices = new CrudResource(httpClient, "/billing/invoices");
    this.usage = new CrudResource(httpClient, "/billing/usage");
  }

  public CrudResource invoices() {
    return invoices;
  }

  public CrudResource usage() {
    return usage;
  }
}
