/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-backed unit tests translated from
 * signalwire-python/tests/unit/rest/test_compat_conferences.py.
 *
 * <p>Covers the Conference resource plus participant, recording, and stream
 * sub-collections — the full surface of {@code CompatConferences}.
 */
class CompatConferencesMockTest {

    private static final String CONF_BASE = "/api/laml/2010-04-01/Accounts/test_proj/Conferences";

    private RestClient client;
    private MockTest.Harness mock;

    @BeforeEach
    void setUp() {
        MockTest.Bound bound = MockTest.newClient();
        this.client = bound.client;
        this.mock = bound.harness;
    }

    private static Map<String, Object> kw(Object... entries) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            m.put((String) entries[i], entries[i + 1]);
        }
        return m;
    }

    // ── Conference itself ───────────────────────────────────────────────

    @Nested
    @DisplayName("CompatConferences.list")
    class List_ {

        @Test
        @DisplayName("returns_paginated_list")
        void returnsPaginatedList() {
            Map<String, Object> result = client.compat().conferences().list();
            assertNotNull(result);
            assertTrue(result.containsKey("conferences"),
                    "expected 'conferences' key, got " + result.keySet());
            assertTrue(result.get("conferences") instanceof List);
            assertTrue(result.get("page") instanceof Number);
        }

        @Test
        @DisplayName("journal_records_get_to_conferences")
        void journalRecordsGet() {
            client.compat().conferences().list();
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(CONF_BASE, j.path);
            assertNotNull(j.getMatchedRoute(), "spec gap: conferences.list");
        }
    }

    @Nested
    @DisplayName("CompatConferences.get")
    class Get {

        @Test
        @DisplayName("returns_conference_resource")
        void returnsConference() {
            Map<String, Object> result = client.compat().conferences().get("CF_TEST");
            assertNotNull(result);
            assertTrue(result.containsKey("friendly_name") || result.containsKey("status"),
                    "expected friendly_name/status, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_get_with_sid")
        void journalRecordsGet() {
            client.compat().conferences().get("CF_GETSID");
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(CONF_BASE + "/CF_GETSID", j.path);
        }
    }

    @Nested
    @DisplayName("CompatConferences.update")
    class Update {

        @Test
        @DisplayName("returns_updated_conference")
        void returnsUpdated() {
            Map<String, Object> result = client.compat().conferences().update(
                    "CF_X", kw("Status", "completed"));
            assertNotNull(result);
            assertTrue(result.containsKey("friendly_name") || result.containsKey("status"));
        }

        @Test
        @DisplayName("journal_records_post_with_status")
        void journalRecordsPost() {
            client.compat().conferences().update(
                    "CF_UPD",
                    kw("Status", "completed", "AnnounceUrl", "https://a.b"));
            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(CONF_BASE + "/CF_UPD", j.path);
            Map<String, Object> body = j.bodyMap();
            assertNotNull(body);
            assertEquals("completed", body.get("Status"));
            assertEquals("https://a.b", body.get("AnnounceUrl"));
        }
    }

    // ── Participants ────────────────────────────────────────────────────

    @Nested
    @DisplayName("CompatConferences.getParticipant")
    class GetParticipant {

        @Test
        @DisplayName("returns_participant")
        void returnsParticipant() {
            Map<String, Object> result = client.compat().conferences()
                    .getParticipant("CF_P", "CA_P");
            assertNotNull(result);
            assertTrue(result.containsKey("call_sid") || result.containsKey("conference_sid"),
                    "expected call_sid/conference_sid, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_get_to_participant")
        void journalRecordsGet() {
            client.compat().conferences().getParticipant("CF_GP", "CA_GP");
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(CONF_BASE + "/CF_GP/Participants/CA_GP", j.path);
        }
    }

    @Nested
    @DisplayName("CompatConferences.updateParticipant")
    class UpdateParticipant {

        @Test
        @DisplayName("returns_participant_resource")
        void returnsParticipant() {
            Map<String, Object> result = client.compat().conferences().updateParticipant(
                    "CF_UP", "CA_UP", kw("Muted", true));
            assertNotNull(result);
            assertTrue(result.containsKey("call_sid") || result.containsKey("conference_sid"));
        }

        @Test
        @DisplayName("journal_records_post_with_mute_flag")
        void journalRecordsPost() {
            client.compat().conferences().updateParticipant(
                    "CF_M", "CA_M", kw("Muted", true, "Hold", false));
            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(CONF_BASE + "/CF_M/Participants/CA_M", j.path);
            Map<String, Object> body = j.bodyMap();
            assertNotNull(body);
            assertEquals(Boolean.TRUE, body.get("Muted"));
            assertEquals(Boolean.FALSE, body.get("Hold"));
        }
    }

    @Nested
    @DisplayName("CompatConferences.removeParticipant")
    class RemoveParticipant {

        @Test
        @DisplayName("returns_empty_or_object")
        void returnsObject() {
            Map<String, Object> result = client.compat().conferences()
                    .removeParticipant("CF_R", "CA_R");
            assertNotNull(result);
        }

        @Test
        @DisplayName("journal_records_delete_call")
        void journalRecordsDelete() {
            client.compat().conferences().removeParticipant("CF_RM", "CA_RM");
            MockTest.JournalEntry j = mock.last();
            assertEquals("DELETE", j.method);
            assertEquals(CONF_BASE + "/CF_RM/Participants/CA_RM", j.path);
        }
    }

    // ── Recordings ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("CompatConferences.listRecordings")
    class ListRecordings {

        @Test
        @DisplayName("returns_paginated_recordings")
        void returnsRecordings() {
            Map<String, Object> result = client.compat().conferences()
                    .listRecordings("CF_LR");
            assertNotNull(result);
            assertTrue(result.containsKey("recordings"),
                    "expected 'recordings' key, got " + result.keySet());
            assertTrue(result.get("recordings") instanceof List);
        }

        @Test
        @DisplayName("journal_records_get_recordings")
        void journalRecordsGet() {
            client.compat().conferences().listRecordings("CF_LRX");
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(CONF_BASE + "/CF_LRX/Recordings", j.path);
        }
    }

    @Nested
    @DisplayName("CompatConferences.getRecording")
    class GetRecording {

        @Test
        @DisplayName("returns_recording_resource")
        void returnsRecording() {
            Map<String, Object> result = client.compat().conferences()
                    .getRecording("CF_GR", "RE_GR");
            assertNotNull(result);
            assertTrue(result.containsKey("sid") || result.containsKey("call_sid"));
        }

        @Test
        @DisplayName("journal_records_get_recording")
        void journalRecordsGet() {
            client.compat().conferences().getRecording("CF_GRX", "RE_GRX");
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(CONF_BASE + "/CF_GRX/Recordings/RE_GRX", j.path);
        }
    }

    @Nested
    @DisplayName("CompatConferences.updateRecording")
    class UpdateRecording {

        @Test
        @DisplayName("returns_recording_resource")
        void returnsRecording() {
            Map<String, Object> result = client.compat().conferences().updateRecording(
                    "CF_URC", "RE_URC", kw("Status", "paused"));
            assertNotNull(result);
            assertTrue(result.containsKey("sid") || result.containsKey("status"));
        }

        @Test
        @DisplayName("journal_records_post_with_status")
        void journalRecordsPost() {
            client.compat().conferences().updateRecording(
                    "CF_UR", "RE_UR", kw("Status", "paused"));
            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(CONF_BASE + "/CF_UR/Recordings/RE_UR", j.path);
            Map<String, Object> body = j.bodyMap();
            assertNotNull(body);
            assertEquals("paused", body.get("Status"));
        }
    }

    @Nested
    @DisplayName("CompatConferences.deleteRecording")
    class DeleteRecording {

        @Test
        @DisplayName("no_exception_on_delete")
        void noExceptionOnDelete() {
            Map<String, Object> result = client.compat().conferences()
                    .deleteRecording("CF_DR", "RE_DR");
            assertNotNull(result);
        }

        @Test
        @DisplayName("journal_records_delete")
        void journalRecordsDelete() {
            client.compat().conferences().deleteRecording("CF_DRX", "RE_DRX");
            MockTest.JournalEntry j = mock.last();
            assertEquals("DELETE", j.method);
            assertEquals(CONF_BASE + "/CF_DRX/Recordings/RE_DRX", j.path);
        }
    }

    // ── Streams ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CompatConferences.startStream")
    class StartStream {

        @Test
        @DisplayName("returns_stream_resource")
        void returnsStream() {
            Map<String, Object> result = client.compat().conferences().startStream(
                    "CF_SS", kw("Url", "wss://a.b/s"));
            assertNotNull(result);
            assertTrue(result.containsKey("sid") || result.containsKey("name"));
        }

        @Test
        @DisplayName("journal_records_post_to_streams")
        void journalRecordsPost() {
            client.compat().conferences().startStream(
                    "CF_SSX", kw("Url", "wss://a.b/s", "Name", "strm"));
            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(CONF_BASE + "/CF_SSX/Streams", j.path);
            Map<String, Object> body = j.bodyMap();
            assertNotNull(body);
            assertEquals("wss://a.b/s", body.get("Url"));
        }
    }

    @Nested
    @DisplayName("CompatConferences.stopStream")
    class StopStream {

        @Test
        @DisplayName("returns_stream_resource")
        void returnsStream() {
            Map<String, Object> result = client.compat().conferences().stopStream(
                    "CF_TS", "ST_TS", kw("Status", "stopped"));
            assertNotNull(result);
            assertTrue(result.containsKey("sid") || result.containsKey("status"));
        }

        @Test
        @DisplayName("journal_records_post_to_specific_stream")
        void journalRecordsPost() {
            client.compat().conferences().stopStream(
                    "CF_TSX", "ST_TSX", kw("Status", "stopped"));
            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(CONF_BASE + "/CF_TSX/Streams/ST_TSX", j.path);
            Map<String, Object> body = j.bodyMap();
            assertNotNull(body);
            assertEquals("stopped", body.get("Status"));
        }
    }
}
