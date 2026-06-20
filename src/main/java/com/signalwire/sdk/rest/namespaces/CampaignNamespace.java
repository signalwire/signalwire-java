/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;

/** REST namespace for 10DLC campaign registration resources. */
public class CampaignNamespace {

  private final CrudResource brands;
  private final CrudResource campaigns;
  private final CrudResource orders;
  private final CrudResource assignments;

  public CampaignNamespace(HttpClient httpClient) {
    this.brands = new CrudResource(httpClient, "/campaign/brands");
    this.campaigns = new CrudResource(httpClient, "/campaign/campaigns");
    this.orders = new CrudResource(httpClient, "/campaign/orders");
    this.assignments = new CrudResource(httpClient, "/campaign/assignments");
  }

  public CrudResource brands() {
    return brands;
  }

  public CrudResource campaigns() {
    return campaigns;
  }

  public CrudResource orders() {
    return orders;
  }

  public CrudResource assignments() {
    return assignments;
  }
}
