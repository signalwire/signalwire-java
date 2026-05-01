/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-relay-backed unit tests translated from
 * {@code signalwire-python/tests/unit/relay/test_event_dispatch_mock.py}.
 *
 * <p>Edge cases in the SDK's recv loop and event router that don't fit neatly
 * into per-action / per-call test files: sub-command journaling, unknown
 * event_types, multi-action concurrency, event ACKs, ping/pong, dial routing.
 */
class EventDispatchMockTest {

    private RelayClient client;
    private RelayMockTest.Harness mock;

    @BeforeEach
    void setUp() {
        this.mock = RelayMockTest.harness();
        this.client = RelayClient.builder()
                .project("test_proj")
                .token("test_tok")
                .space(mock.wsUrl())
                .contexts(List.of("default"))
                .build();
        client.connect(10_000);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            try { client.disconnect(); } catch (Exception ignored) {}
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Call answeredCall(String callId) throws Exception {
        AtomicReference<Call> capturedRef = new AtomicReference<>();
        CompletableFuture<Void> handlerDone = new CompletableFuture<>();
        client.onCall(call -> {
            capturedRef.set(call);
            call.answer();
            handlerDone.complete(null);
        });
        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId(callId));
        handlerDone.get(5, TimeUnit.SECONDS);
        Call call = capturedRef.get();
        call.setState("answered");
        return call;
    }

    private static Map<String, Object> bareEventFrame(String eventType, Map<String, Object> params) {
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("event_type", eventType);
        outer.put("params", params);
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", Constants.JSONRPC_VERSION);
        frame.put("id", UUID.randomUUID().toString());
        frame.put("method", "signalwire.event");
        frame.put("params", outer);
        return frame;
    }

    private static Map<String, Object> bareEventFrameWithId(String id, String eventType, Map<String, Object> params) {
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("event_type", eventType);
        outer.put("params", params);
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", Constants.JSONRPC_VERSION);
        frame.put("id", id);
        frame.put("method", "signalwire.event");
        frame.put("params", outer);
        return frame;
    }

    private static Map<String, Object> silence(int duration) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "silence");
        p.put("params", Map.of("duration", duration));
        return p;
    }

    // ── Sub-command journaling ──────────────────────────────────────

    @Test
    @DisplayName("record.pause(behavior) journals calling.record.pause with behavior field")
    void recordPauseJournalsRecordPause() throws Exception {
        Call call = answeredCall("ec-rec-pa");
        Action.RecordAction action = call.recordAudio(Map.of("format", "wav"), "ec-rec-pa-1");
        action.pauseWithBehavior("continuous");

        List<RelayMockTest.JournalEntry> pauses =
                mock.journalRecv(Constants.METHOD_RECORD_PAUSE);
        assertFalse(pauses.isEmpty());
        Map<String, Object> p = pauses.get(pauses.size() - 1).params();
        assertEquals("ec-rec-pa-1", p.get("control_id"));
        assertEquals("continuous", p.get("behavior"));
    }

    @Test
    @DisplayName("record.resume() journals calling.record.resume")
    void recordResumeJournalsRecordResume() throws Exception {
        Call call = answeredCall("ec-rec-re");
        Action.RecordAction action = call.recordAudio(Map.of("format", "wav"), "ec-rec-re-1");
        action.resume();

        List<RelayMockTest.JournalEntry> resumes =
                mock.journalRecv(Constants.METHOD_RECORD_RESUME);
        assertFalse(resumes.isEmpty());
        assertEquals("ec-rec-re-1",
                resumes.get(resumes.size() - 1).params().get("control_id"));
    }

    @Test
    @DisplayName("collect.startInputTimers() journals start_input_timers method")
    void collectStartInputTimersJournals() throws Exception {
        Call call = answeredCall("ec-col-sit");
        Action.CollectAction action = call.collectDigits(Map.of("max", 4), false, "ec-col-sit-1");
        action.startInputTimers();

        List<RelayMockTest.JournalEntry> starts =
                mock.journalRecv(Constants.METHOD_COLLECT_START_INPUT_TIMERS);
        assertFalse(starts.isEmpty());
        assertEquals("ec-col-sit-1",
                starts.get(starts.size() - 1).params().get("control_id"));
    }

    @Test
    @DisplayName("play.volume() carries a negative value verbatim")
    void playVolumeCarriesNegativeValue() throws Exception {
        Call call = answeredCall("ec-pvol");
        Action.PlayAction action = call.play(List.of(silence(60)), "ec-pvol-1");
        action.volume(-5.5);

        List<RelayMockTest.JournalEntry> vols =
                mock.journalRecv(Constants.METHOD_PLAY_VOLUME);
        assertFalse(vols.isEmpty());
        Object vol = vols.get(vols.size() - 1).params().get("volume");
        assertEquals(-5.5, ((Number) vol).doubleValue());
    }

    // ── Unknown event types — recv loop survives ────────────────────

    @Test
    @DisplayName("Pushing an unknown event_type doesn't crash the SDK")
    void unknownEventTypeDoesNotCrash() throws Exception {
        mock.push(bareEventFrame("nonsense.unknown", Map.of("foo", "bar")));
        Thread.sleep(100);
        assertTrue(client.isConnected());
    }

    @Test
    @DisplayName("Event with bad call_id is dropped silently")
    void eventWithBadCallIdDropped() throws Exception {
        mock.push(bareEventFrame(Constants.EVENT_CALL_PLAY,
                Map.of("call_id", "no-such-call-bogus",
                        "control_id", "stranger",
                        "state", "playing")));
        Thread.sleep(100);
        assertTrue(client.isConnected());
    }

    @Test
    @DisplayName("Empty event_type is logged and skipped")
    void emptyEventTypeDropped() throws Exception {
        mock.push(bareEventFrame("", Map.of("call_id", "x")));
        Thread.sleep(100);
        assertTrue(client.isConnected());
    }

    // ── Multi-action concurrency: 3 actions on one call ──────────

    @Test
    @DisplayName("Three concurrent actions resolve independently by control_id")
    void threeConcurrentActionsResolveIndependently() throws Exception {
        Call call = answeredCall("ec-3acts");
        Action.PlayAction play1 = call.play(List.of(silence(60)), "3a-p1");
        Action.PlayAction play2 = call.play(List.of(silence(60)), "3a-p2");
        Action.RecordAction rec = call.recordAudio(Map.of("format", "wav"), "3a-r1");

        // Fire only play1's finished.
        mock.push(bareEventFrame(Constants.EVENT_CALL_PLAY,
                Map.of("call_id", "ec-3acts",
                        "control_id", "3a-p1",
                        "state", "finished")));
        play1.waitForCompletion(2_000);
        assertTrue(play1.isDone());
        assertFalse(play2.isDone());
        assertFalse(rec.isDone());

        // Fire play2's.
        mock.push(bareEventFrame(Constants.EVENT_CALL_PLAY,
                Map.of("call_id", "ec-3acts",
                        "control_id", "3a-p2",
                        "state", "finished")));
        play2.waitForCompletion(2_000);
        assertTrue(play2.isDone());
        assertFalse(rec.isDone());
    }

    // ── Event ACK round-trip ─────────────────────────────────────────

    @Test
    @DisplayName("After receiving signalwire.event, SDK sends back a JSON-RPC ACK")
    void eventAckSentBackToServer() throws Exception {
        String evtId = "evt-ack-test-1";
        mock.push(bareEventFrameWithId(evtId, Constants.EVENT_CALL_PLAY,
                Map.of("call_id", "anything",
                        "control_id", "x",
                        "state", "playing")));
        Thread.sleep(200);

        // Look for a recv frame with id == evtId AND a "result" key (the ACK).
        List<RelayMockTest.JournalEntry> all = mock.journal();
        boolean foundAck = false;
        for (RelayMockTest.JournalEntry e : all) {
            if (!"recv".equals(e.direction)) continue;
            if (!evtId.equals(e.frame.get("id"))) continue;
            if (e.frame.containsKey("result")) {
                foundAck = true;
                break;
            }
        }
        assertTrue(foundAck, "no event ACK found for id=" + evtId);
    }

    // ── Tag-based dial routing — call.call_id nested ──────────────

    @Test
    @DisplayName("calling.call.dial event with no top-level call_id routes via tag")
    void dialEventRoutesViaTagWhenNoTopLevelCallId() {
        // Use the existing client. arm dial scenario.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tag", "ec-tag-route");
        body.put("winner_call_id", "WINTAG");
        body.put("states", List.of("created", "answered"));
        body.put("node_id", "n");
        body.put("device", Map.of("type", "phone", "params", new HashMap<>()));
        body.put("delay_ms", 1);
        mock.armDial(body);

        Map<String, Object> deviceParams = new LinkedHashMap<>();
        deviceParams.put("to_number", "+1");
        deviceParams.put("from_number", "+2");
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("type", "phone");
        device.put("params", deviceParams);

        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("tag", "ec-tag-route");
        Call call = client.dial(List.of(List.of(device)), opts, 5_000);
        assertEquals("WINTAG", call.getCallId());

        // Verify the dial event the mock pushed had no top-level call_id —
        // only call.call_id nested.
        List<RelayMockTest.JournalEntry> sends =
                mock.journalSend(Constants.EVENT_CALL_DIAL);
        assertFalse(sends.isEmpty(), "no calling.call.dial event in journal");
        Map<String, Object> inner =
                sends.get(sends.size() - 1).innerParams();
        assertNotNull(inner);
        assertFalse(inner.containsKey("call_id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> innerCall = (Map<String, Object>) inner.get("call");
        assertEquals("WINTAG", innerCall.get("call_id"));
    }

    // ── Server ping handling ──────────────────────────────────────

    @Test
    @DisplayName("SDK ACKs a server-initiated signalwire.ping")
    void serverPingAckedBySdk() throws Exception {
        String pingId = "ping-test-1";
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", Constants.JSONRPC_VERSION);
        frame.put("id", pingId);
        frame.put("method", Constants.METHOD_PING);
        frame.put("params", new HashMap<>());
        mock.push(frame);
        Thread.sleep(200);

        List<RelayMockTest.JournalEntry> all = mock.journal();
        boolean foundPong = false;
        for (RelayMockTest.JournalEntry e : all) {
            if (!"recv".equals(e.direction)) continue;
            if (!pingId.equals(e.frame.get("id"))) continue;
            if (e.frame.containsKey("result")) {
                foundPong = true;
                break;
            }
        }
        assertTrue(foundPong, "SDK did not respond to ping");
    }

    // ── Authorization state captured for reconnect ───────────────

    @Test
    @DisplayName("signalwire.authorization.state event updates SDK internal state")
    void authorizationStateEventCaptured() throws Exception {
        mock.push(bareEventFrame(Constants.EVENT_AUTHORIZATION_STATE,
                Map.of("authorization_state", "test-auth-state-blob")));
        for (int i = 0; i < 100; i++) {
            if (client.getAuthorizationState() != null) break;
            Thread.sleep(20);
        }
        assertEquals("test-auth-state-blob", client.getAuthorizationState());
    }

    // ── calling.error event — does not raise ─────────────────────

    @Test
    @DisplayName("Emitted calling.error event is logged but doesn't crash recv loop")
    void callingErrorEventDoesNotCrash() throws Exception {
        mock.push(bareEventFrame("calling.error",
                Map.of("code", "5001", "message", "synthetic error")));
        Thread.sleep(100);
        assertTrue(client.isConnected());
    }

    // ── State event for an answered call updates Call.state ──────

    @Test
    @DisplayName("State event for an existing call updates its state")
    void callStateEventUpdatesState() throws Exception {
        Call call = answeredCall("ec-stt");
        mock.push(bareEventFrame(Constants.EVENT_CALL_STATE,
                Map.of("call_id", "ec-stt",
                        "call_state", "ending",
                        "direction", "inbound")));
        for (int i = 0; i < 100; i++) {
            if ("ending".equals(call.getState())) break;
            Thread.sleep(20);
        }
        assertEquals("ending", call.getState());
    }

    @Test
    @DisplayName("Custom event listeners on a Call fire on matching events")
    void callListenerFiresOnEvent() throws Exception {
        Call call = answeredCall("ec-list");
        CompletableFuture<RelayEvent> fired = new CompletableFuture<>();
        call.on(event -> {
            if (Constants.EVENT_CALL_PLAY.equals(event.getEventType())) {
                fired.complete(event);
            }
        });

        mock.push(bareEventFrame(Constants.EVENT_CALL_PLAY,
                Map.of("call_id", "ec-list",
                        "control_id", "x",
                        "state", "playing")));
        RelayEvent event = fired.get(2, TimeUnit.SECONDS);
        assertEquals(Constants.EVENT_CALL_PLAY, event.getEventType());
    }
}
