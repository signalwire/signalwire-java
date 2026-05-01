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
 * Recordings namespace — list / get / delete (no create/update).
 *
 * <p>Mirrors {@code signalwire.rest.namespaces.recordings.RecordingsResource}.
 * Path: {@code /api/relay/rest/recordings}.
 */
public class RecordingNamespace {

    private static final String BASE = "/relay/rest/recordings";
    private final HttpClient httpClient;

    public RecordingNamespace(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String getBasePath() { return BASE; }

    public Map<String, Object> list() {
        return httpClient.get(BASE);
    }

    public Map<String, Object> list(Map<String, String> queryParams) {
        return httpClient.get(BASE, queryParams);
    }

    public Map<String, Object> get(String recordingId) {
        return httpClient.get(BASE + "/" + recordingId);
    }

    public Map<String, Object> delete(String recordingId) {
        return httpClient.delete(BASE + "/" + recordingId);
    }

    /**
     * Legacy CRUD-shaped accessor for backwards compat with the previous
     * Java surface ({@code client.recordings().recordings()}). Targets the
     * same path as this namespace.
     *
     * @deprecated prefer the direct namespace methods which match Python.
     */
    @Deprecated
    public com.signalwire.sdk.rest.CrudResource recordings() {
        return new com.signalwire.sdk.rest.CrudResource(httpClient, BASE);
    }
}
