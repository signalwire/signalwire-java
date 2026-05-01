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
 * Number Groups namespace — full CRUD on number groups + membership ops.
 *
 * <p>Mirrors
 * {@code signalwire.rest.namespaces.number_groups.NumberGroupsResource}: the
 * group CRUD uses PUT for update; membership get/delete operate against a
 * separate top-level {@code /api/relay/rest/number_group_memberships}
 * collection (not a sub-collection of the group).
 */
public class NumberGroupsNamespace {

    private static final String BASE = "/relay/rest/number_groups";
    private static final String MEMBERSHIP_BASE = "/relay/rest/number_group_memberships";
    private final HttpClient httpClient;

    public NumberGroupsNamespace(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String getBasePath() { return BASE; }

    // ── Group CRUD ───────────────────────────────────────────────────────

    public Map<String, Object> list() {
        return httpClient.get(BASE);
    }

    public Map<String, Object> list(Map<String, String> queryParams) {
        return httpClient.get(BASE, queryParams);
    }

    public Map<String, Object> create(Map<String, Object> body) {
        return httpClient.post(BASE, body);
    }

    public Map<String, Object> get(String groupId) {
        return httpClient.get(BASE + "/" + groupId);
    }

    public Map<String, Object> update(String groupId, Map<String, Object> body) {
        return httpClient.put(BASE + "/" + groupId, body);
    }

    public Map<String, Object> delete(String groupId) {
        return httpClient.delete(BASE + "/" + groupId);
    }

    // ── Memberships ──────────────────────────────────────────────────────

    public Map<String, Object> listMemberships(String groupId) {
        return httpClient.get(BASE + "/" + groupId + "/number_group_memberships");
    }

    public Map<String, Object> listMemberships(String groupId, Map<String, String> queryParams) {
        return httpClient.get(BASE + "/" + groupId + "/number_group_memberships", queryParams);
    }

    public Map<String, Object> addMembership(String groupId, Map<String, Object> body) {
        return httpClient.post(BASE + "/" + groupId + "/number_group_memberships", body);
    }

    public Map<String, Object> getMembership(String membershipId) {
        return httpClient.get(MEMBERSHIP_BASE + "/" + membershipId);
    }

    public Map<String, Object> deleteMembership(String membershipId) {
        return httpClient.delete(MEMBERSHIP_BASE + "/" + membershipId);
    }
}
