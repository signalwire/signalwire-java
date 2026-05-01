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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-backed unit tests translated from
 * signalwire-python/tests/unit/rest/test_compat_calls_streams.py.
 *
 * <p>Each Java test mirrors one Python test and asserts on both the SDK
 * response body shape and the wire request the mock journaled.
 */
class CompatCallsStreamsMockTest {

    private RestClient client;
    private MockTest.Harness mock;

    @BeforeEach
    void setUp() {
        MockTest.Bound bound = MockTest.newClient();
        this.client = bound.client;
        this.mock = bound.harness;
    }

    @Nested
    @DisplayName("CompatCalls.startStream → POST /Calls/{sid}/Streams")
    class StartStream {

        @Test
        @DisplayName("returns_stream_resource")
        void returnsStreamResource() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Url", "wss://example.com/stream");
            body.put("Name", "my-stream");
            Map<String, Object> result = client.compat().calls().startStream("CA_TEST", body);
            assertNotNull(result, "startStream returned null");
            // Stream resources carry a 'sid' or 'name' identifier.
            assertTrue(result.containsKey("sid") || result.containsKey("name"),
                    "expected stream sid/name in body, got keys " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_post_to_streams_collection")
        void journalRecordsPostToStreamsCollection() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Url", "wss://a.b/s");
            body.put("Name", "strm-x");
            client.compat().calls().startStream("CA_JX1", body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method, "method");
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Calls/CA_JX1/Streams",
                    j.path,
                    "path");
            Map<String, Object> jb = j.bodyMap();
            assertNotNull(jb, "expected JSON body, got " + j.body);
            assertEquals("wss://a.b/s", jb.get("Url"), "body[Url]");
            assertEquals("strm-x", jb.get("Name"), "body[Name]");
        }
    }

    @Nested
    @DisplayName("CompatCalls.stopStream → POST .../Streams/{stream_sid}")
    class StopStream {

        @Test
        @DisplayName("returns_stream_resource_with_status")
        void returnsStreamResourceWithStatus() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Status", "stopped");
            Map<String, Object> result = client.compat().calls()
                    .stopStream("CA_T1", "ST_T1", body);
            assertNotNull(result, "stopStream returned null");
            // The stop endpoint synthesises a stream resource (sid + status).
            assertTrue(result.containsKey("sid") || result.containsKey("status"),
                    "expected sid/status, got keys " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_post_to_specific_stream")
        void journalRecordsPostToSpecificStream() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Status", "stopped");
            client.compat().calls().stopStream("CA_S1", "ST_S1", body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method, "method");
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Calls/CA_S1/Streams/ST_S1",
                    j.path,
                    "path");
            Map<String, Object> jb = j.bodyMap();
            assertNotNull(jb);
            assertEquals("stopped", jb.get("Status"), "body[Status]");
        }
    }

    @Nested
    @DisplayName("CompatCalls.updateRecording")
    class UpdateRecording {

        @Test
        @DisplayName("returns_recording_resource")
        void returnsRecordingResource() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Status", "paused");
            Map<String, Object> result = client.compat().calls()
                    .updateRecording("CA_T2", "RE_T2", body);
            assertNotNull(result, "updateRecording returned null");
            assertTrue(result.containsKey("sid") || result.containsKey("status"),
                    "expected sid/status, got keys " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_post_to_specific_recording")
        void journalRecordsPostToSpecificRecording() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Status", "paused");
            client.compat().calls().updateRecording("CA_R1", "RE_R1", body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method, "method");
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Calls/CA_R1/Recordings/RE_R1",
                    j.path,
                    "path");
            Map<String, Object> jb = j.bodyMap();
            assertNotNull(jb);
            assertEquals("paused", jb.get("Status"));
        }
    }
}
