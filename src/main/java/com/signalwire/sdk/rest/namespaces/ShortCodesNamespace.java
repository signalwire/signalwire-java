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
 * Short Codes namespace — list / get / update (PUT). No create/delete.
 *
 * <p>Mirrors {@code signalwire.rest.namespaces.short_codes.ShortCodesResource}.
 */
public class ShortCodesNamespace {

    private static final String BASE = "/relay/rest/short_codes";
    private final HttpClient httpClient;

    public ShortCodesNamespace(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String getBasePath() { return BASE; }

    public Map<String, Object> list() {
        return httpClient.get(BASE);
    }

    public Map<String, Object> list(Map<String, String> queryParams) {
        return httpClient.get(BASE, queryParams);
    }

    public Map<String, Object> get(String shortCodeId) {
        return httpClient.get(BASE + "/" + shortCodeId);
    }

    public Map<String, Object> update(String shortCodeId, Map<String, Object> body) {
        return httpClient.put(BASE + "/" + shortCodeId, body);
    }
}
