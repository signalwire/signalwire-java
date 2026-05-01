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
 * SIP Profile namespace — get / update (PUT) the project's singleton SIP
 * profile.
 *
 * <p>Mirrors {@code signalwire.rest.namespaces.sip_profile.SipProfileResource}.
 */
public class SipProfileNamespace {

    private static final String BASE = "/relay/rest/sip_profile";
    private final HttpClient httpClient;

    public SipProfileNamespace(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String getBasePath() { return BASE; }

    public Map<String, Object> get() {
        return httpClient.get(BASE);
    }

    public Map<String, Object> update(Map<String, Object> body) {
        return httpClient.put(BASE, body);
    }
}
