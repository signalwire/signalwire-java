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
 * Queues namespace — full CRUD with member operations.
 *
 * <p>Mirrors {@code signalwire.rest.namespaces.queues.QueuesResource}: queue
 * CRUD uses PUT for update; members are addressable both per-id and via the
 * special {@code /next} endpoint.
 */
public class QueueNamespace {

    private static final String BASE = "/relay/rest/queues";
    private final HttpClient httpClient;

    public QueueNamespace(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String getBasePath() { return BASE; }

    // ── Queue CRUD ───────────────────────────────────────────────────────

    public Map<String, Object> list() {
        return httpClient.get(BASE);
    }

    public Map<String, Object> list(Map<String, String> queryParams) {
        return httpClient.get(BASE, queryParams);
    }

    public Map<String, Object> create(Map<String, Object> body) {
        return httpClient.post(BASE, body);
    }

    public Map<String, Object> get(String queueId) {
        return httpClient.get(BASE + "/" + queueId);
    }

    public Map<String, Object> update(String queueId, Map<String, Object> body) {
        return httpClient.put(BASE + "/" + queueId, body);
    }

    public Map<String, Object> delete(String queueId) {
        return httpClient.delete(BASE + "/" + queueId);
    }

    // ── Members ──────────────────────────────────────────────────────────

    public Map<String, Object> listMembers(String queueId) {
        return httpClient.get(BASE + "/" + queueId + "/members");
    }

    public Map<String, Object> listMembers(String queueId, Map<String, String> queryParams) {
        return httpClient.get(BASE + "/" + queueId + "/members", queryParams);
    }

    public Map<String, Object> getNextMember(String queueId) {
        return httpClient.get(BASE + "/" + queueId + "/members/next");
    }

    public Map<String, Object> getMember(String queueId, String memberId) {
        return httpClient.get(BASE + "/" + queueId + "/members/" + memberId);
    }

    /**
     * Returns the legacy {@code com.signalwire.sdk.rest.CrudResource}-shaped
     * accessor so older callers like {@code client.queues().queues()} keep
     * compiling. The returned resource targets the same path as this
     * namespace ({@code /relay/rest/queues}).
     *
     * @deprecated direct calls on this namespace ({@link #list()},
     *             {@link #get(String)} etc.) match Python parity. Prefer those.
     */
    @Deprecated
    public com.signalwire.sdk.rest.CrudResource queues() {
        return new com.signalwire.sdk.rest.CrudResource(httpClient, BASE) {
            @Override
            public Map<String, Object> update(String id, Map<String, Object> body) {
                return getHttpClient().put(getBasePath() + "/" + id, body);
            }
        };
    }
}
