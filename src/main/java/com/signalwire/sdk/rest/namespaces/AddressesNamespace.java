/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.HttpClient;

import java.util.Map;

/**
 * Address management namespace.
 *
 * <p>Mirrors {@code signalwire.rest.namespaces.addresses.AddressesResource}:
 * list / create / get / delete (no update endpoint exists for addresses).
 * Paths sit under {@code /api/relay/rest/addresses}.
 */
public class AddressesNamespace {

    private static final String BASE = "/relay/rest/addresses";
    private final HttpClient httpClient;

    public AddressesNamespace(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String getBasePath() { return BASE; }

    public Map<String, Object> list() {
        return httpClient.get(BASE);
    }

    public Map<String, Object> list(Map<String, String> queryParams) {
        return httpClient.get(BASE, queryParams);
    }

    public Map<String, Object> create(Map<String, Object> body) {
        return httpClient.post(BASE, body);
    }

    public Map<String, Object> get(String addressId) {
        return httpClient.get(BASE + "/" + addressId);
    }

    public Map<String, Object> delete(String addressId) {
        return httpClient.delete(BASE + "/" + addressId);
    }
}
