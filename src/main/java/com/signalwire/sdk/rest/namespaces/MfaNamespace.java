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
 * MFA (Multi-Factor Authentication) namespace.
 *
 * <p>Mirrors {@code signalwire.rest.namespaces.mfa.MfaResource}:
 * sms / call / verify endpoints.
 */
public class MfaNamespace {

    private static final String BASE = "/relay/rest/mfa";
    private final HttpClient httpClient;

    public MfaNamespace(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String getBasePath() { return BASE; }

    public Map<String, Object> sms(Map<String, Object> body) {
        return httpClient.post(BASE + "/sms", body);
    }

    public Map<String, Object> call(Map<String, Object> body) {
        return httpClient.post(BASE + "/call", body);
    }

    public Map<String, Object> verify(String requestId, Map<String, Object> body) {
        return httpClient.post(BASE + "/" + requestId + "/verify", body);
    }
}
