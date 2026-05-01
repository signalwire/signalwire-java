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
 * {@code signalwire-python/tests/unit/relay/test_inbound_call_mock.py}.
 */
class InboundCallMockTest {

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

    private static Map<String, Object> statePushFrame(String callId, String callState) {
        return statePushFrame(callId, callState, "", "inbound");
    }

    private static Map<String, Object> statePushFrame(String callId, String callState,
                                                      String tag, String direction) {
        Map<String, Object> deviceParams = new LinkedHashMap<>();
        deviceParams.put("from_number", "+15551110000");
        deviceParams.put("to_number", "+15552220000");
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("type", "phone");
        device.put("params", deviceParams);

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("call_id", callId);
        inner.put("node_id", "mock-relay-node-1");
        inner.put("tag", tag);
        inner.put("call_state", callState);
        inner.put("direction", direction);
        inner.put("device", device);

        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("event_type", Constants.EVENT_CALL_STATE);
        outer.put("params", inner);

        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", Constants.JSONRPC_VERSION);
        frame.put("id", UUID.randomUUID().toString());
        frame.put("method", "signalwire.event");
        frame.put("params", outer);
        return frame;
    }

    // ── Basic inbound-call handler dispatch ───────────────────────

    @Test
    @DisplayName("Pushed inbound call invokes the registered on_call handler")
    void onCallHandlerFires() throws Exception {
        List<Call> seen = new ArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        client.onCall(call -> {
            seen.add(call);
            done.complete(null);
        });

        mock.inboundCall(new RelayMockTest.InboundCallSpec()
                .callId("c-handler")
                .fromNumber("+15551110000")
                .toNumber("+15552220000"));
        done.get(5, TimeUnit.SECONDS);

        assertEquals(1, seen.size());
        assertEquals("c-handler", seen.get(0).getCallId());
    }

    @Test
    @DisplayName("Inbound Call has the right call_id and direction")
    void inboundCallFields() throws Exception {
        AtomicReference<String> callId = new AtomicReference<>();
        AtomicReference<String> direction = new AtomicReference<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        client.onCall(call -> {
            callId.set(call.getCallId());
            direction.set(call.getDirection());
            done.complete(null);
        });

        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId("c-dir"));
        done.get(5, TimeUnit.SECONDS);

        assertEquals("c-dir", callId.get());
        assertEquals("inbound", direction.get());
    }

    @Test
    @DisplayName("Inbound call carries from/to in device.params")
    void inboundCallCarriesFromToInDevice() throws Exception {
        AtomicReference<Map<String, Object>> deviceRef = new AtomicReference<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        client.onCall(call -> {
            deviceRef.set(call.getDevice());
            done.complete(null);
        });

        mock.inboundCall(new RelayMockTest.InboundCallSpec()
                .callId("c-from-to")
                .fromNumber("+15551112233")
                .toNumber("+15554445566"));
        done.get(5, TimeUnit.SECONDS);

        Map<String, Object> device = deviceRef.get();
        assertNotNull(device);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) device.get("params");
        assertNotNull(params);
        assertEquals("+15551112233", params.get("from_number"));
        assertEquals("+15554445566", params.get("to_number"));
    }

    @Test
    @DisplayName("Inbound call initial state is created")
    void inboundCallInitialStateCreated() throws Exception {
        AtomicReference<String> stateRef = new AtomicReference<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        client.onCall(call -> {
            stateRef.set(call.getState());
            done.complete(null);
        });

        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId("c-state"));
        done.get(5, TimeUnit.SECONDS);
        assertEquals("created", stateRef.get());
    }

    // ── Handler answers — calling.answer journaled ─────────────────

    @Test
    @DisplayName("Answer in handler journals calling.answer")
    void answerInHandlerJournalsCallingAnswer() throws Exception {
        CompletableFuture<Void> answered = new CompletableFuture<>();
        client.onCall(call -> {
            call.answer();
            answered.complete(null);
        });

        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId("c-ans"));
        answered.get(5, TimeUnit.SECONDS);
        Thread.sleep(100); // let the answer round-trip land

        List<RelayMockTest.JournalEntry> answers =
                mock.journalRecv(Constants.METHOD_ANSWER);
        assertFalse(answers.isEmpty(), "no calling.answer in journal");
        assertEquals("c-ans",
                answers.get(answers.size() - 1).params().get("call_id"));
    }

    @Test
    @DisplayName("Pushing answered after handler answers updates Call state")
    void answerThenStateEventAdvancesCallState() throws Exception {
        AtomicReference<Call> capturedRef = new AtomicReference<>();
        CompletableFuture<Void> handlerReturned = new CompletableFuture<>();
        client.onCall(call -> {
            capturedRef.set(call);
            call.answer();
            handlerReturned.complete(null);
        });

        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId("c-ans-state"));
        handlerReturned.get(5, TimeUnit.SECONDS);

        mock.push(statePushFrame("c-ans-state", "answered"));
        // Wait for state propagation.
        Call captured = capturedRef.get();
        for (int i = 0; i < 100; i++) {
            if ("answered".equals(captured.getState())) break;
            Thread.sleep(20);
        }
        assertEquals("answered", captured.getState());
    }

    // ── Handler hangs up / passes ──────────────────────────────────

    @Test
    @DisplayName("hangup() in handler journals calling.end with reason")
    void hangupInHandlerJournalsCallingEnd() throws Exception {
        CompletableFuture<Void> hung = new CompletableFuture<>();
        client.onCall(call -> {
            call.hangup("busy");
            hung.complete(null);
        });

        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId("c-hangup"));
        hung.get(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        List<RelayMockTest.JournalEntry> ends = mock.journalRecv(Constants.METHOD_END);
        assertFalse(ends.isEmpty(), "no calling.end in journal");
        Map<String, Object> p = ends.get(ends.size() - 1).params();
        assertEquals("c-hangup", p.get("call_id"));
        assertEquals("busy", p.get("reason"));
    }

    @Test
    @DisplayName("pass() in handler journals calling.pass")
    void passInHandlerJournalsCallingPass() throws Exception {
        CompletableFuture<Void> passed = new CompletableFuture<>();
        client.onCall(call -> {
            call.pass();
            passed.complete(null);
        });

        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId("c-pass"));
        passed.get(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        List<RelayMockTest.JournalEntry> passes = mock.journalRecv(Constants.METHOD_PASS);
        assertFalse(passes.isEmpty(), "no calling.pass in journal");
        assertEquals("c-pass",
                passes.get(passes.size() - 1).params().get("call_id"));
    }

    // ── Multiple inbound calls — independent state ────────────────

    @Test
    @DisplayName("Two inbound calls give the handler two distinct Call objects")
    void multipleInboundCallsUniqueObjects() throws Exception {
        List<Call> seen = new ArrayList<>();
        CompletableFuture<Void> received = new CompletableFuture<>();
        client.onCall(call -> {
            synchronized (seen) {
                seen.add(call);
                if (seen.size() == 2) received.complete(null);
            }
        });

        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId("c-seq-1"));
        Thread.sleep(100);
        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId("c-seq-2"));
        received.get(5, TimeUnit.SECONDS);

        synchronized (seen) {
            assertEquals("c-seq-1", seen.get(0).getCallId());
            assertEquals("c-seq-2", seen.get(1).getCallId());
            assertNotSame(seen.get(0), seen.get(1));
        }
    }

    @Test
    @DisplayName("State on one inbound call doesn't leak to another")
    void multipleInboundCallsNoStateBleed() throws Exception {
        java.util.concurrent.ConcurrentHashMap<String, Call> calls = new java.util.concurrent.ConcurrentHashMap<>();
        CompletableFuture<Void> bothReceived = new CompletableFuture<>();
        client.onCall(call -> {
            calls.put(call.getCallId(), call);
            call.answer();
            if (calls.size() == 2) bothReceived.complete(null);
        });

        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId("cb-1"));
        Thread.sleep(50);
        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId("cb-2"));
        bothReceived.get(5, TimeUnit.SECONDS);

        // Push answered to only cb-1
        mock.push(statePushFrame("cb-1", "answered"));
        Call cb1 = calls.get("cb-1");
        for (int i = 0; i < 100; i++) {
            if ("answered".equals(cb1.getState())) break;
            Thread.sleep(20);
        }
        assertEquals("answered", calls.get("cb-1").getState());
        assertNotEquals("answered", calls.get("cb-2").getState());
    }

    // ── Scripted state sequences ──────────────────────────────────

    @Test
    @DisplayName("Pushing answered then ended advances Call state, then cleanup")
    void scriptedStateSequence() throws Exception {
        AtomicReference<Call> capturedRef = new AtomicReference<>();
        CompletableFuture<Void> handlerDone = new CompletableFuture<>();
        client.onCall(call -> {
            capturedRef.set(call);
            call.answer();
            handlerDone.complete(null);
        });

        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId("c-scripted"));
        handlerDone.get(5, TimeUnit.SECONDS);

        mock.push(statePushFrame("c-scripted", "answered"));
        mock.push(statePushFrame("c-scripted", "ended"));
        Call captured = capturedRef.get();
        for (int i = 0; i < 100; i++) {
            if ("ended".equals(captured.getState())) break;
            Thread.sleep(20);
        }
        assertEquals("ended", captured.getState());
        // Ended calls are dropped from the registry.
        assertNull(client.getCalls().get("c-scripted"));
    }

    // ── Handler patterns: raises, etc. ────────────────────────────

    @Test
    @DisplayName("Handler exception does not crash the client")
    void handlerExceptionDoesNotCrashClient() throws Exception {
        CompletableFuture<Void> fired = new CompletableFuture<>();
        client.onCall(call -> {
            fired.complete(null);
            throw new RuntimeException("intentional from handler");
        });

        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId("c-raise"));
        fired.get(5, TimeUnit.SECONDS);
        Thread.sleep(100);

        // The client is still alive.
        assertTrue(client.isConnected());
    }

    // ── scenario_play — full inbound flow ─────────────────────────

    @Test
    @DisplayName("scenario_play timeline drives a full inbound-call flow")
    void scenarioPlayFullInboundFlow() throws Exception {
        CompletableFuture<Void> handlerStarted = new CompletableFuture<>();
        AtomicReference<Call> capturedRef = new AtomicReference<>();
        client.onCall(call -> {
            capturedRef.set(call);
            call.answer();
            handlerStarted.complete(null);
        });

        // Build the timeline: push receive, expect answer, push answered, push ended.
        Map<String, Object> deviceParams = new LinkedHashMap<>();
        deviceParams.put("from_number", "+15551110000");
        deviceParams.put("to_number", "+15552220000");
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("type", "phone");
        device.put("params", deviceParams);

        Map<String, Object> recvInner = new LinkedHashMap<>();
        recvInner.put("call_id", "c-scen");
        recvInner.put("node_id", "mock-relay-node-1");
        recvInner.put("tag", "");
        recvInner.put("call_state", "created");
        recvInner.put("direction", "inbound");
        recvInner.put("device", device);
        recvInner.put("context", "default");

        Map<String, Object> recvOuter = new LinkedHashMap<>();
        recvOuter.put("event_type", Constants.EVENT_CALL_RECEIVE);
        recvOuter.put("params", recvInner);

        Map<String, Object> recvFrame = new LinkedHashMap<>();
        recvFrame.put("jsonrpc", Constants.JSONRPC_VERSION);
        recvFrame.put("id", UUID.randomUUID().toString());
        recvFrame.put("method", "signalwire.event");
        recvFrame.put("params", recvOuter);

        List<Map<String, Object>> timeline = new ArrayList<>();
        timeline.add(Map.of("push", Map.of("frame", recvFrame)));
        timeline.add(Map.of("expect_recv", Map.of(
                "method", Constants.METHOD_ANSWER,
                "timeout_ms", 5000)));
        timeline.add(Map.of("push", Map.of("frame", statePushFrame("c-scen", "answered"))));
        timeline.add(Map.of("sleep_ms", 50));
        timeline.add(Map.of("push", Map.of("frame", statePushFrame("c-scen", "ended"))));

        Map<String, Object> result = mock.scenarioPlay(timeline);
        assertEquals("completed", result.get("status"),
                "scenario didn't complete: " + result);
        assertTrue(handlerStarted.isDone());

        Call captured = capturedRef.get();
        for (int i = 0; i < 100; i++) {
            if ("ended".equals(captured.getState())) break;
            Thread.sleep(20);
        }
        assertEquals("ended", captured.getState());
    }

    // ── Wire shape — calling.call.receive ─────────────────────────

    @Test
    @DisplayName("journal_send records the calling.call.receive frame")
    void inboundCallJournalSendRecordsReceive() throws Exception {
        CompletableFuture<Void> handlerDone = new CompletableFuture<>();
        client.onCall(call -> handlerDone.complete(null));

        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId("c-wire"));
        handlerDone.get(5, TimeUnit.SECONDS);

        List<RelayMockTest.JournalEntry> sends =
                mock.journalSend(Constants.EVENT_CALL_RECEIVE);
        assertFalse(sends.isEmpty(), "no calling.call.receive frame");
        Map<String, Object> inner = sends.get(sends.size() - 1).innerParams();
        assertEquals("c-wire", inner.get("call_id"));
        assertEquals("inbound", inner.get("direction"));
    }

    // ── Inbound without a registered handler — does not crash ────

    @Test
    @DisplayName("Inbound without handler does not crash")
    void inboundWithoutHandlerDoesNotCrash() throws Exception {
        // No handler registered.
        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId("c-nohandler"));
        Thread.sleep(200);
        assertTrue(client.isConnected());
    }
}
