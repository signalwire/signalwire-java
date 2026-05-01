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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-backed unit tests translated from
 * signalwire-python/tests/unit/rest/test_compat_recordings_transcriptions.py.
 *
 * <p>Both resources expose the same surface (list / get / delete) and use the
 * account-scoped LAML path.
 */
class CompatRecordingsTranscriptionsMockTest {

    private RestClient client;
    private MockTest.Harness mock;

    @BeforeEach
    void setUp() {
        MockTest.Bound bound = MockTest.newClient();
        this.client = bound.client;
        this.mock = bound.harness;
    }

    // ── Recordings ──────────────────────────────────────────────────

    @Nested
    @DisplayName("CompatRecordings.list")
    class RecordingsList {

        @Test
        @DisplayName("returns_paginated_recordings")
        void returnsPaginatedRecordings() {
            Map<String, Object> result = client.compat().recordings().list();
            assertNotNull(result);
            assertTrue(result.containsKey("recordings"),
                    "expected 'recordings' key, got " + result.keySet());
            assertTrue(result.get("recordings") instanceof List);
        }

        @Test
        @DisplayName("journal_records_get")
        void journalRecordsGet() {
            client.compat().recordings().list();
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Recordings",
                    j.path);
        }
    }

    @Nested
    @DisplayName("CompatRecordings.get")
    class RecordingsGet {

        @Test
        @DisplayName("returns_recording_resource")
        void returnsRecordingResource() {
            Map<String, Object> result = client.compat().recordings().get("RE_TEST");
            assertNotNull(result);
            // Recording resources carry call_sid + duration + sid.
            assertTrue(result.containsKey("sid") || result.containsKey("call_sid"),
                    "expected sid/call_sid, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_get_with_sid")
        void journalRecordsGet() {
            client.compat().recordings().get("RE_GET");
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Recordings/RE_GET",
                    j.path);
        }
    }

    @Nested
    @DisplayName("CompatRecordings.delete")
    class RecordingsDelete {

        @Test
        @DisplayName("no_exception_on_delete")
        void noExceptionOnDelete() {
            Map<String, Object> result = client.compat().recordings().delete("RE_D");
            assertNotNull(result);
        }

        @Test
        @DisplayName("journal_records_delete")
        void journalRecordsDelete() {
            client.compat().recordings().delete("RE_DEL");
            MockTest.JournalEntry j = mock.last();
            assertEquals("DELETE", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Recordings/RE_DEL",
                    j.path);
        }
    }

    // ── Transcriptions ──────────────────────────────────────────────

    @Nested
    @DisplayName("CompatTranscriptions.list")
    class TranscriptionsList {

        @Test
        @DisplayName("returns_paginated_transcriptions")
        void returnsPaginatedTranscriptions() {
            Map<String, Object> result = client.compat().transcriptions().list();
            assertNotNull(result);
            assertTrue(result.containsKey("transcriptions"),
                    "expected 'transcriptions' key, got " + result.keySet());
            assertTrue(result.get("transcriptions") instanceof List);
        }

        @Test
        @DisplayName("journal_records_get")
        void journalRecordsGet() {
            client.compat().transcriptions().list();
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Transcriptions",
                    j.path);
        }
    }

    @Nested
    @DisplayName("CompatTranscriptions.get")
    class TranscriptionsGet {

        @Test
        @DisplayName("returns_transcription_resource")
        void returnsTranscriptionResource() {
            Map<String, Object> result = client.compat().transcriptions().get("TR_TEST");
            assertNotNull(result);
            // Transcription resources carry duration + transcription_text + sid.
            assertTrue(result.containsKey("sid") || result.containsKey("duration"),
                    "expected sid/duration, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_get_with_sid")
        void journalRecordsGet() {
            client.compat().transcriptions().get("TR_GET");
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Transcriptions/TR_GET",
                    j.path);
        }
    }

    @Nested
    @DisplayName("CompatTranscriptions.delete")
    class TranscriptionsDelete {

        @Test
        @DisplayName("no_exception_on_delete")
        void noExceptionOnDelete() {
            Map<String, Object> result = client.compat().transcriptions().delete("TR_D");
            assertNotNull(result);
        }

        @Test
        @DisplayName("journal_records_delete")
        void journalRecordsDelete() {
            client.compat().transcriptions().delete("TR_DEL");
            MockTest.JournalEntry j = mock.last();
            assertEquals("DELETE", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Transcriptions/TR_DEL",
                    j.path);
        }
    }
}
