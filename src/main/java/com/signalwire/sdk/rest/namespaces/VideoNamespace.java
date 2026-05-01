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
 * REST namespace for the Video API: rooms, room sessions/recordings,
 * conferences, conference tokens, and individual streams.
 *
 * <p>Mirrors {@code signalwire.rest.namespaces.video.VideoNamespace}. The
 * stream sub-resources hang off rooms and conferences; the top-level
 * {@code streams} resource exists for CRUD on individual streams keyed by
 * stream id.
 */
public class VideoNamespace {

    private final VideoRooms rooms;
    private final CrudResource roomTokens;
    private final VideoRoomSessions roomSessions;
    private final VideoRoomRecordings roomRecordings;
    private final VideoConferences conferences;
    private final VideoConferenceTokens conferenceTokens;
    private final VideoStreams streams;

    public VideoNamespace(HttpClient httpClient) {
        String base = "/video";
        this.rooms = new VideoRooms(httpClient, base + "/rooms");
        this.roomTokens = new CrudResource(httpClient, base + "/room_tokens");
        this.roomSessions = new VideoRoomSessions(httpClient, base + "/room_sessions");
        this.roomRecordings = new VideoRoomRecordings(httpClient, base + "/room_recordings");
        this.conferences = new VideoConferences(httpClient, base + "/conferences");
        this.conferenceTokens = new VideoConferenceTokens(httpClient, base + "/conference_tokens");
        this.streams = new VideoStreams(httpClient, base + "/streams");
    }

    public VideoRooms rooms() { return rooms; }
    public CrudResource roomTokens() { return roomTokens; }
    public VideoRoomSessions roomSessions() { return roomSessions; }
    public VideoRoomRecordings roomRecordings() { return roomRecordings; }
    public VideoConferences conferences() { return conferences; }
    public VideoConferenceTokens conferenceTokens() { return conferenceTokens; }
    public VideoStreams streams() { return streams; }

    /**
     * Legacy alias for {@link #roomRecordings()}; previous releases of the
     * Java port exposed video.recordings(). Kept for backwards compatibility.
     */
    public VideoRoomRecordings recordings() { return roomRecordings; }

    // ────────────────────────────────────────────────────────────────────
    // Sub-resource classes
    // ────────────────────────────────────────────────────────────────────

    /**
     * Video room management with stream sub-resources. Update uses PUT.
     */
    public static class VideoRooms extends CrudResource {

        public VideoRooms(HttpClient httpClient, String basePath) {
            super(httpClient, basePath);
        }

        @Override
        public Map<String, Object> update(String roomId, Map<String, Object> body) {
            return getHttpClient().put(getBasePath() + "/" + roomId, body);
        }

        public Map<String, Object> listStreams(String roomId) {
            return getHttpClient().get(getBasePath() + "/" + roomId + "/streams");
        }

        public Map<String, Object> listStreams(String roomId, Map<String, String> queryParams) {
            return getHttpClient().get(getBasePath() + "/" + roomId + "/streams", queryParams);
        }

        public Map<String, Object> createStream(String roomId, Map<String, Object> body) {
            return getHttpClient().post(getBasePath() + "/" + roomId + "/streams", body);
        }
    }

    /**
     * Video room sessions: list, get, plus events / members / recordings
     * sub-collections.
     */
    public static class VideoRoomSessions {

        private final HttpClient httpClient;
        private final String basePath;

        public VideoRoomSessions(HttpClient httpClient, String basePath) {
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

        public Map<String, Object> get(String sessionId) {
            return httpClient.get(basePath + "/" + sessionId);
        }

        public Map<String, Object> listEvents(String sessionId) {
            return httpClient.get(basePath + "/" + sessionId + "/events");
        }

        public Map<String, Object> listEvents(String sessionId, Map<String, String> queryParams) {
            return httpClient.get(basePath + "/" + sessionId + "/events", queryParams);
        }

        public Map<String, Object> listMembers(String sessionId) {
            return httpClient.get(basePath + "/" + sessionId + "/members");
        }

        public Map<String, Object> listMembers(String sessionId, Map<String, String> queryParams) {
            return httpClient.get(basePath + "/" + sessionId + "/members", queryParams);
        }

        public Map<String, Object> listRecordings(String sessionId) {
            return httpClient.get(basePath + "/" + sessionId + "/recordings");
        }

        public Map<String, Object> listRecordings(String sessionId, Map<String, String> queryParams) {
            return httpClient.get(basePath + "/" + sessionId + "/recordings", queryParams);
        }
    }

    /**
     * Top-level video room recordings collection: list, get, delete, plus
     * a per-recording events sub-collection.
     */
    public static class VideoRoomRecordings {

        private final HttpClient httpClient;
        private final String basePath;

        public VideoRoomRecordings(HttpClient httpClient, String basePath) {
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

        public Map<String, Object> get(String recordingId) {
            return httpClient.get(basePath + "/" + recordingId);
        }

        public Map<String, Object> delete(String recordingId) {
            return httpClient.delete(basePath + "/" + recordingId);
        }

        public Map<String, Object> listEvents(String recordingId) {
            return httpClient.get(basePath + "/" + recordingId + "/events");
        }

        public Map<String, Object> listEvents(String recordingId, Map<String, String> queryParams) {
            return httpClient.get(basePath + "/" + recordingId + "/events", queryParams);
        }
    }

    /**
     * Video conferences with token + stream sub-collections. Update uses PUT.
     */
    public static class VideoConferences extends CrudResource {

        public VideoConferences(HttpClient httpClient, String basePath) {
            super(httpClient, basePath);
        }

        @Override
        public Map<String, Object> update(String conferenceId, Map<String, Object> body) {
            return getHttpClient().put(getBasePath() + "/" + conferenceId, body);
        }

        public Map<String, Object> listConferenceTokens(String conferenceId) {
            return getHttpClient().get(
                    getBasePath() + "/" + conferenceId + "/conference_tokens");
        }

        public Map<String, Object> listConferenceTokens(
                String conferenceId, Map<String, String> queryParams) {
            return getHttpClient().get(
                    getBasePath() + "/" + conferenceId + "/conference_tokens", queryParams);
        }

        public Map<String, Object> listStreams(String conferenceId) {
            return getHttpClient().get(getBasePath() + "/" + conferenceId + "/streams");
        }

        public Map<String, Object> listStreams(
                String conferenceId, Map<String, String> queryParams) {
            return getHttpClient().get(
                    getBasePath() + "/" + conferenceId + "/streams", queryParams);
        }

        public Map<String, Object> createStream(String conferenceId, Map<String, Object> body) {
            return getHttpClient().post(getBasePath() + "/" + conferenceId + "/streams", body);
        }
    }

    /**
     * Video conference tokens (top-level): get + reset.
     */
    public static class VideoConferenceTokens {

        private final HttpClient httpClient;
        private final String basePath;

        public VideoConferenceTokens(HttpClient httpClient, String basePath) {
            this.httpClient = httpClient;
            this.basePath = basePath;
        }

        public String getBasePath() { return basePath; }

        public Map<String, Object> get(String tokenId) {
            return httpClient.get(basePath + "/" + tokenId);
        }

        public Map<String, Object> reset(String tokenId) {
            return httpClient.post(basePath + "/" + tokenId + "/reset", null);
        }
    }

    /**
     * Top-level streams resource (per stream id): get / update (PUT) / delete.
     */
    public static class VideoStreams {

        private final HttpClient httpClient;
        private final String basePath;

        public VideoStreams(HttpClient httpClient, String basePath) {
            this.httpClient = httpClient;
            this.basePath = basePath;
        }

        public String getBasePath() { return basePath; }

        public Map<String, Object> get(String streamId) {
            return httpClient.get(basePath + "/" + streamId);
        }

        public Map<String, Object> update(String streamId, Map<String, Object> body) {
            return httpClient.put(basePath + "/" + streamId, body);
        }

        public Map<String, Object> delete(String streamId) {
            return httpClient.delete(basePath + "/" + streamId);
        }
    }
}
