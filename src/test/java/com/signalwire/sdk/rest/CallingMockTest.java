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
 * signalwire-python/tests/unit/rest/test_calling_mock.py.
 *
 * <p>Every command in {@link com.signalwire.sdk.rest.namespaces.CallingNamespace}
 * is exercised against the mock server so we know the SDK sends the right
 * wire request — method, path, command field, id, and params.
 */
class CallingMockTest {

    private static final String CALLS_PATH = "/api/calling/calls";

    private RestClient client;
    private MockTest.Harness mock;

    @BeforeEach
    void setUp() {
        MockTest.Bound bound = MockTest.newClient();
        this.client = bound.client;
        this.mock = bound.harness;
    }

    /**
     * Asserts the journal entry shape — method/path/command — and returns
     * the params map for caller-specific assertions. {@code expectedId} may
     * be null to require no {@code id} field at the body root (true for
     * dial / update which carry id inside params).
     */
    private Map<String, Object> commandAssert(MockTest.JournalEntry j, String command, String expectedId) {
        assertEquals("POST", j.method, "method");
        assertEquals(CALLS_PATH, j.path, "path");
        Map<String, Object> body = j.bodyMap();
        assertNotNull(body, "expected JSON body, got " + j.body);
        assertEquals(command, body.get("command"), "command");
        if (expectedId == null) {
            assertFalse(body.containsKey("id"),
                    "expected no id at body root, got " + body.get("id"));
        } else {
            assertEquals(expectedId, body.get("id"), "id");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) body.get("params");
        assertNotNull(params, "expected params object");
        return params;
    }

    private static Map<String, Object> kw(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("kw expects key/value pairs");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            m.put((String) entries[i], entries[i + 1]);
        }
        return m;
    }

    // ── Lifecycle ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Lifecycle commands")
    class Lifecycle {

        @Test
        void update() {
            Map<String, Object> body = client.calling().update(kw("id", "call-1", "state", "hold"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"), "response missing 'id'");
            Map<String, Object> p = commandAssert(mock.last(), "update", null);
            assertEquals("call-1", p.get("id"));
            assertEquals("hold", p.get("state"));
        }

        @Test
        void transfer() {
            Map<String, Object> body = client.calling().transfer("call-123",
                    kw("destination", "+15551234567", "from_number", "+15559876543"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"), "response missing 'id'");
            Map<String, Object> p = commandAssert(mock.last(), "calling.transfer", "call-123");
            assertEquals("+15551234567", p.get("destination"));
            assertEquals("+15559876543", p.get("from_number"));
        }

        @Test
        void disconnect() {
            Map<String, Object> body = client.calling().disconnect("call-456",
                    kw("reason", "busy"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.disconnect", "call-456");
            assertEquals("busy", p.get("reason"));
        }
    }

    // ── Play ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Play commands")
    class Play {

        @Test
        void playPause() {
            Map<String, Object> body = client.calling().playPause("call-1",
                    kw("control_id", "ctrl-1"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.play.pause", "call-1");
            assertEquals("ctrl-1", p.get("control_id"));
        }

        @Test
        void playResume() {
            Map<String, Object> body = client.calling().playResume("call-1",
                    kw("control_id", "ctrl-1"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.play.resume", "call-1");
            assertEquals("ctrl-1", p.get("control_id"));
        }

        @Test
        void playStop() {
            Map<String, Object> body = client.calling().playStop("call-1",
                    kw("control_id", "ctrl-1"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.play.stop", "call-1");
            assertEquals("ctrl-1", p.get("control_id"));
        }

        @Test
        void playVolume() {
            Map<String, Object> body = client.calling().playVolume("call-1",
                    kw("control_id", "ctrl-1", "volume", 2.5));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.play.volume", "call-1");
            assertEquals(2.5, ((Number) p.get("volume")).doubleValue(), 1e-9);
        }
    }

    // ── Record ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Record commands")
    class Record {

        @Test
        void record() {
            Map<String, Object> body = client.calling().record("call-1",
                    kw("record", kw("format", "mp3")));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.record", "call-1");
            @SuppressWarnings("unchecked")
            Map<String, Object> rec = (Map<String, Object>) p.get("record");
            assertNotNull(rec);
            assertEquals("mp3", rec.get("format"));
        }

        @Test
        void recordPause() {
            Map<String, Object> body = client.calling().recordPause("call-1",
                    kw("control_id", "rec-1"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.record.pause", "call-1");
            assertEquals("rec-1", p.get("control_id"));
        }

        @Test
        void recordResume() {
            Map<String, Object> body = client.calling().recordResume("call-1",
                    kw("control_id", "rec-1"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.record.resume", "call-1");
            assertEquals("rec-1", p.get("control_id"));
        }
    }

    // ── Collect ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Collect commands")
    class Collect {

        @Test
        void collect() {
            Map<String, Object> body = client.calling().collect("call-1",
                    kw("initial_timeout", 5, "digits", kw("max", 4)));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.collect", "call-1");
            assertEquals(5, ((Number) p.get("initial_timeout")).intValue());
        }

        @Test
        void collectStop() {
            Map<String, Object> body = client.calling().collectStop("call-1",
                    kw("control_id", "col-1"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.collect.stop", "call-1");
            assertEquals("col-1", p.get("control_id"));
        }

        @Test
        void collectStartInputTimers() {
            Map<String, Object> body = client.calling().collectStartInputTimers("call-1",
                    kw("control_id", "col-1"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(),
                    "calling.collect.start_input_timers", "call-1");
            assertEquals("col-1", p.get("control_id"));
        }
    }

    // ── Detect / tap / stream / denoise / transcribe ────────────────

    @Nested
    @DisplayName("Detect commands")
    class Detect {

        @Test
        void detect() {
            Map<String, Object> body = client.calling().detect("call-1",
                    kw("detect", kw("type", "machine", "params", kw())));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.detect", "call-1");
            @SuppressWarnings("unchecked")
            Map<String, Object> det = (Map<String, Object>) p.get("detect");
            assertNotNull(det);
            assertEquals("machine", det.get("type"));
        }

        @Test
        void detectStop() {
            Map<String, Object> body = client.calling().detectStop("call-1",
                    kw("control_id", "det-1"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.detect.stop", "call-1");
            assertEquals("det-1", p.get("control_id"));
        }
    }

    @Nested
    @DisplayName("Tap commands")
    class Tap {

        @Test
        void tap() {
            Map<String, Object> body = client.calling().tap("call-1",
                    kw("tap", kw("type", "audio"), "device", kw("type", "rtp")));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.tap", "call-1");
            @SuppressWarnings("unchecked")
            Map<String, Object> tapParams = (Map<String, Object>) p.get("tap");
            assertNotNull(tapParams);
            assertEquals("audio", tapParams.get("type"));
        }

        @Test
        void tapStop() {
            Map<String, Object> body = client.calling().tapStop("call-1",
                    kw("control_id", "tap-1"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.tap.stop", "call-1");
            assertEquals("tap-1", p.get("control_id"));
        }
    }

    @Nested
    @DisplayName("Stream commands")
    class Stream {

        @Test
        void stream() {
            Map<String, Object> body = client.calling().stream("call-1",
                    kw("url", "wss://example.com/audio"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.stream", "call-1");
            assertEquals("wss://example.com/audio", p.get("url"));
        }

        @Test
        void streamStop() {
            Map<String, Object> body = client.calling().streamStop("call-1",
                    kw("control_id", "stream-1"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.stream.stop", "call-1");
            assertEquals("stream-1", p.get("control_id"));
        }
    }

    @Nested
    @DisplayName("Denoise commands")
    class Denoise {

        @Test
        void denoise() {
            Map<String, Object> body = client.calling().denoise("call-1", kw());
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            commandAssert(mock.last(), "calling.denoise", "call-1");
        }

        @Test
        void denoiseStop() {
            Map<String, Object> body = client.calling().denoiseStop("call-1",
                    kw("control_id", "dn-1"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.denoise.stop", "call-1");
            assertEquals("dn-1", p.get("control_id"));
        }
    }

    @Nested
    @DisplayName("Transcribe commands")
    class Transcribe {

        @Test
        void transcribe() {
            Map<String, Object> body = client.calling().transcribe("call-1",
                    kw("language", "en-US", "transcribe", kw("engine", "google")));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.transcribe", "call-1");
            assertEquals("en-US", p.get("language"));
        }

        @Test
        void transcribeStop() {
            Map<String, Object> body = client.calling().transcribeStop("call-1",
                    kw("control_id", "tr-1"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.transcribe.stop", "call-1");
            assertEquals("tr-1", p.get("control_id"));
        }
    }

    // ── AI commands ──────────────────────────────────────────────────

    @Nested
    @DisplayName("AI commands")
    class AI {

        @Test
        void aiHold() {
            Map<String, Object> body = client.calling().aiHold("call-1", kw());
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            commandAssert(mock.last(), "calling.ai_hold", "call-1");
        }

        @Test
        void aiUnhold() {
            Map<String, Object> body = client.calling().aiUnhold("call-1", kw());
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            commandAssert(mock.last(), "calling.ai_unhold", "call-1");
        }

        @Test
        void aiStop() {
            Map<String, Object> body = client.calling().aiStop("call-1", kw());
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            commandAssert(mock.last(), "calling.ai.stop", "call-1");
        }
    }

    // ── Live transcribe / translate ──────────────────────────────────

    @Nested
    @DisplayName("Live commands")
    class Live {

        @Test
        void liveTranscribe() {
            Map<String, Object> body = client.calling().liveTranscribe("call-1",
                    kw("language", "en-US"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.live_transcribe", "call-1");
            assertEquals("en-US", p.get("language"));
        }

        @Test
        void liveTranslate() {
            Map<String, Object> body = client.calling().liveTranslate("call-1",
                    kw("source_language", "en", "target_language", "es"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.live_translate", "call-1");
            assertEquals("en", p.get("source_language"));
            assertEquals("es", p.get("target_language"));
        }
    }

    // ── Fax commands ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Fax commands")
    class Fax {

        @Test
        void sendFaxStop() {
            Map<String, Object> body = client.calling().sendFaxStop("call-1", kw());
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            commandAssert(mock.last(), "calling.send_fax.stop", "call-1");
        }

        @Test
        void receiveFaxStop() {
            Map<String, Object> body = client.calling().receiveFaxStop("call-1", kw());
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            commandAssert(mock.last(), "calling.receive_fax.stop", "call-1");
        }
    }

    // ── SIP refer + custom user_event ────────────────────────────────

    @Nested
    @DisplayName("Misc commands")
    class Misc {

        @Test
        void refer() {
            Map<String, Object> body = client.calling().refer("call-1",
                    kw("to", "sip:other@example.com"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.refer", "call-1");
            assertEquals("sip:other@example.com", p.get("to"));
        }

        @Test
        void userEvent() {
            Map<String, Object> body = client.calling().userEvent("call-1",
                    kw("event_name", "my-event", "payload", kw("foo", "bar")));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            Map<String, Object> p = commandAssert(mock.last(), "calling.user_event", "call-1");
            assertEquals("my-event", p.get("event_name"));
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) p.get("payload");
            assertNotNull(payload);
            assertEquals("bar", payload.get("foo"));
        }
    }
}
