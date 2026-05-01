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
 * Imported Phone Numbers namespace — create-only.
 *
 * <p>Mirrors
 * {@code signalwire.rest.namespaces.imported_numbers.ImportedNumbersResource}.
 */
public class ImportedNumbersNamespace {

    private static final String BASE = "/relay/rest/imported_phone_numbers";
    private final HttpClient httpClient;

    public ImportedNumbersNamespace(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String getBasePath() { return BASE; }

    public Map<String, Object> create(Map<String, Object> body) {
        return httpClient.post(BASE, body);
    }
}
