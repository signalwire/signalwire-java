/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;

import java.util.Map;

/**
 * REST namespace for DataSphere (knowledge base) resources.
 *
 * <p>Mirrors {@code signalwire.rest.namespaces.datasphere.DatasphereNamespace}:
 * documents are CRUD-shaped, plus {@code search} (POST) and chunk-level
 * operations.
 */
public class DatasphereNamespace {

    private final DatasphereDocuments documents;
    private final HttpClient httpClient;

    public DatasphereNamespace(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.documents = new DatasphereDocuments(httpClient, "/datasphere/documents");
    }

    public DatasphereDocuments documents() { return documents; }

    /**
     * Legacy convenience for knowledge-base search; the canonical entry point
     * is {@link DatasphereDocuments#search(Map)}.
     */
    public Map<String, Object> search(Map<String, Object> body) {
        return documents.search(body);
    }

    // ────────────────────────────────────────────────────────────────────
    // Sub-resources
    // ────────────────────────────────────────────────────────────────────

    /**
     * Document management with search and chunk operations.
     */
    public static class DatasphereDocuments extends CrudResource {

        public DatasphereDocuments(HttpClient httpClient, String basePath) {
            super(httpClient, basePath);
        }

        public Map<String, Object> search(Map<String, Object> body) {
            return getHttpClient().post(getBasePath() + "/search", body);
        }

        public Map<String, Object> listChunks(String documentId) {
            return getHttpClient().get(getBasePath() + "/" + documentId + "/chunks");
        }

        public Map<String, Object> listChunks(String documentId, Map<String, String> queryParams) {
            return getHttpClient().get(getBasePath() + "/" + documentId + "/chunks", queryParams);
        }

        public Map<String, Object> getChunk(String documentId, String chunkId) {
            return getHttpClient().get(getBasePath() + "/" + documentId + "/chunks/" + chunkId);
        }

        public Map<String, Object> deleteChunk(String documentId, String chunkId) {
            return getHttpClient().delete(getBasePath() + "/" + documentId + "/chunks/" + chunkId);
        }
    }
}
