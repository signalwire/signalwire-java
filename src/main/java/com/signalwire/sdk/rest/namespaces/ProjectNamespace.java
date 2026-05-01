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
 * REST namespace for project management resources.
 *
 * <p>Mirrors {@code signalwire.rest.namespaces.project.ProjectNamespace}:
 * exposes {@link ProjectTokens} for API token CRUD (PATCH for update,
 * DELETE for revoke).
 */
public class ProjectNamespace {

    private final HttpClient httpClient;
    private final ProjectTokens tokens;

    public ProjectNamespace(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.tokens = new ProjectTokens(httpClient);
    }

    public ProjectTokens tokens() { return tokens; }

    // ── Legacy single-method accessors (kept for backwards compat) ───

    /** Get project info. */
    public Map<String, Object> get() {
        return httpClient.get("/project");
    }

    /** Update project settings. */
    public Map<String, Object> update(Map<String, Object> body) {
        return httpClient.put("/project", body);
    }

    /** List project tokens (legacy single-method form). */
    public Map<String, Object> listTokens() {
        return httpClient.get("/project/tokens");
    }

    /** Create a project token (legacy single-method form). */
    public Map<String, Object> createToken(Map<String, Object> body) {
        return httpClient.post("/project/tokens", body);
    }

    // ────────────────────────────────────────────────────────────────────
    // Sub-resources
    // ────────────────────────────────────────────────────────────────────

    /**
     * Project API token management. Update is PATCH (matches Python's
     * {@code BaseResource.update -> http.patch}); delete revokes the token.
     */
    public static class ProjectTokens {

        private static final String BASE = "/project/tokens";
        private final HttpClient httpClient;

        public ProjectTokens(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        public String getBasePath() { return BASE; }

        public Map<String, Object> create(Map<String, Object> body) {
            return httpClient.post(BASE, body);
        }

        public Map<String, Object> update(String tokenId, Map<String, Object> body) {
            return httpClient.patch(BASE + "/" + tokenId, body);
        }

        public Map<String, Object> delete(String tokenId) {
            return httpClient.delete(BASE + "/" + tokenId);
        }
    }
}
