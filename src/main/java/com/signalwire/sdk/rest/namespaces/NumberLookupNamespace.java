/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.HttpClient;
import java.util.Map;

/** REST namespace for number lookup (CNAM/carrier lookup) resources. */
public class NumberLookupNamespace {

  private final HttpClient httpClient;

  public NumberLookupNamespace(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  /**
   * Look up a phone number ({@code GET /api/relay/rest/lookup/phone_number/{e164}}).
   *
   * <p>The {@code /relay/rest} prefix matches the canonical relay-rest path (mirroring Python's
   * {@code LookupResource} base {@code /api/relay/rest/lookup}); the {@code HttpClient} appends
   * this after its {@code /api} base.
   */
  public Map<String, Object> lookup(String phoneNumber) {
    return httpClient.get("/relay/rest/lookup/phone_number/" + phoneNumber);
  }
}
