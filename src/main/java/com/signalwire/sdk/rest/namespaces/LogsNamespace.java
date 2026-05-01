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
 * Logs namespace — message, voice, fax, and conference logs (read-only).
 *
 * <p>Mirrors {@code signalwire.rest.namespaces.logs.LogsNamespace}: each
 * sub-resource fans out to a distinct sub-API root (message → /messaging/logs,
 * voice → /voice/logs, fax → /fax/logs, conference → /logs/conferences).
 */
public class LogsNamespace {

    private final MessageLogs messages;
    private final VoiceLogs voice;
    private final FaxLogs fax;
    private final ConferenceLogs conferences;

    public LogsNamespace(HttpClient httpClient) {
        this.messages = new MessageLogs(httpClient, "/messaging/logs");
        this.voice = new VoiceLogs(httpClient, "/voice/logs");
        this.fax = new FaxLogs(httpClient, "/fax/logs");
        this.conferences = new ConferenceLogs(httpClient, "/logs/conferences");
    }

    public MessageLogs messages() { return messages; }
    public VoiceLogs voice() { return voice; }
    public FaxLogs fax() { return fax; }
    public ConferenceLogs conferences() { return conferences; }

    // ────────────────────────────────────────────────────────────────────
    // Sub-resources
    // ────────────────────────────────────────────────────────────────────

    /** Read-only logs supporting list + per-id get. */
    public static class MessageLogs {

        private final HttpClient httpClient;
        private final String basePath;

        public MessageLogs(HttpClient httpClient, String basePath) {
            this.httpClient = httpClient;
            this.basePath = basePath;
        }

        public String getBasePath() { return basePath; }

        public Map<String, Object> list() {
            return httpClient.get(basePath);
        }

        public Map<String, Object> list(Map<String, String> queryParams) {
            return httpClient.get(basePath, queryParams);
        }

        public Map<String, Object> get(String logId) {
            return httpClient.get(basePath + "/" + logId);
        }
    }

    /** Voice logs add a per-id events sub-collection. */
    public static class VoiceLogs {

        private final HttpClient httpClient;
        private final String basePath;

        public VoiceLogs(HttpClient httpClient, String basePath) {
            this.httpClient = httpClient;
            this.basePath = basePath;
        }

        public String getBasePath() { return basePath; }

        public Map<String, Object> list() {
            return httpClient.get(basePath);
        }

        public Map<String, Object> list(Map<String, String> queryParams) {
            return httpClient.get(basePath, queryParams);
        }

        public Map<String, Object> get(String logId) {
            return httpClient.get(basePath + "/" + logId);
        }

        public Map<String, Object> listEvents(String logId) {
            return httpClient.get(basePath + "/" + logId + "/events");
        }

        public Map<String, Object> listEvents(String logId, Map<String, String> queryParams) {
            return httpClient.get(basePath + "/" + logId + "/events", queryParams);
        }
    }

    /** Fax logs — list + per-id get. */
    public static class FaxLogs {

        private final HttpClient httpClient;
        private final String basePath;

        public FaxLogs(HttpClient httpClient, String basePath) {
            this.httpClient = httpClient;
            this.basePath = basePath;
        }

        public String getBasePath() { return basePath; }

        public Map<String, Object> list() {
            return httpClient.get(basePath);
        }

        public Map<String, Object> list(Map<String, String> queryParams) {
            return httpClient.get(basePath, queryParams);
        }

        public Map<String, Object> get(String logId) {
            return httpClient.get(basePath + "/" + logId);
        }
    }

    /** Conference logs — list-only. */
    public static class ConferenceLogs {

        private final HttpClient httpClient;
        private final String basePath;

        public ConferenceLogs(HttpClient httpClient, String basePath) {
            this.httpClient = httpClient;
            this.basePath = basePath;
        }

        public String getBasePath() { return basePath; }

        public Map<String, Object> list() {
            return httpClient.get(basePath);
        }

        public Map<String, Object> list(Map<String, String> queryParams) {
            return httpClient.get(basePath, queryParams);
        }
    }
}
