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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-relay-backed tests proving the Tier-3 typed state accessors report the
 * right enum for a <em>real</em> dispatched event over the shared
 * {@code mock_relay} WebSocket (no Mockito, no transport stubbing) — and that
 * the typed accessor always agrees with the parity string getter.
 *
 * <ul>
 *   <li>{@link CallState}: a real {@code dial}-dance winner that progresses
 *       {@code created → ringing → answered} ends up with
 *       {@code getCallState() == ANSWERED}, agreeing with {@code getState()};</li>
 *   <li>{@link MessageState}: a real outbound {@code messaging.send} that
 *       receives a pushed {@code messaging.state(delivered)} event ends up with
 *       {@code getMessageState() == DELIVERED} (terminal), agreeing with
 *       {@code getState()};</li>
 *   <li>{@link DialState}: the {@code calling.call.dial} event the mock emits
 *       carries a {@code dial_state} the typed accessor decodes to
 *       {@code DialState.ANSWERED} (terminal).</li>
 * </ul>
 */
class RelayStateEnumMockTest {

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
        // Scope the harness view to this client's session (parallel-safe).
        this.mock = mock.scopedTo(client);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            try { client.disconnect(); } catch (Exception ignored) {}
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static Map<String, Object> phoneDevice() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("to_number", "+15551112222");
        params.put("from_number", "+15553334444");
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("type", "phone");
        device.put("params", params);
        return device;
    }

    private void armDial(String tag, String winnerCallId, List<String> states) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tag", tag);
        body.put("winner_call_id", winnerCallId);
        body.put("states", states);
        body.put("node_id", "node-mock-1");
        body.put("device", phoneDevice());
        body.put("delay_ms", 1);
        mock.armDial(body);
    }

    private Map<String, Object> dialOptions(String tag) {
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("tag", tag);
        return opts;
    }

    private static Map<String, Object> messagingStateFrame(String messageId, String state,
                                                           Map<String, Object> extras) {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("message_id", messageId);
        inner.put("message_state", state);
        if (extras != null) inner.putAll(extras);
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("event_type", Constants.EVENT_MESSAGING_STATE);
        outer.put("params", inner);
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", Constants.JSONRPC_VERSION);
        frame.put("id", UUID.randomUUID().toString());
        frame.put("method", "signalwire.event");
        frame.put("params", outer);
        return frame;
    }

    // ── CallState over a real dial flow ──────────────────────────────

    @Test
    @DisplayName("CallState: a real dial winner reaches ANSWERED, agreeing with getState()")
    void callStateTypedAfterRealDial() {
        armDial("t-cs", "winner-cs", List.of("created", "ringing", "answered"));
        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        Call call = client.dial(devices, dialOptions("t-cs"), 5_000);

        // string getter (parity)
        assertEquals("answered", call.getState());
        // typed accessor decodes the SAME live wire state
        assertEquals(CallState.ANSWERED, call.getCallState().orElseThrow());
        assertEquals(call.getState(), call.getCallState().orElseThrow().getValue());
        // answered is not terminal for a call lifecycle
        assertFalse(call.getCallState().orElseThrow().isTerminal());
    }

    @Test
    @DisplayName("CallState: a pushed ended state event flips getCallState() to ENDED (terminal)")
    void callStateBecomesEndedTerminal() throws Exception {
        armDial("t-end", "winner-end", List.of("created", "answered"));
        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        Call call = client.dial(devices, dialOptions("t-end"), 5_000);
        assertEquals(CallState.ANSWERED, call.getCallState().orElseThrow());

        // Push a real calling.call.state(ended) event for this call_id.
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("call_id", call.getCallId());
        inner.put("node_id", "node-mock-1");
        inner.put("call_state", "ended");
        inner.put("end_reason", "hangup");
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("event_type", Constants.EVENT_CALL_STATE);
        outer.put("params", inner);
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", Constants.JSONRPC_VERSION);
        frame.put("id", UUID.randomUUID().toString());
        frame.put("method", "signalwire.event");
        frame.put("params", outer);
        mock.push(frame);

        // Wait for the state to propagate through real dispatch.
        for (int i = 0; i < 100; i++) {
            if ("ended".equals(call.getState())) break;
            Thread.sleep(20);
        }
        assertEquals("ended", call.getState());
        assertEquals(CallState.ENDED, call.getCallState().orElseThrow());
        assertTrue(call.getCallState().orElseThrow().isTerminal());
    }

    // ── MessageState over a real send + state push ───────────────────

    @Test
    @DisplayName("MessageState: a real send + pushed delivered reaches DELIVERED (terminal)")
    void messageStateTypedAfterRealDelivery() throws Exception {
        Message msg = client.sendMessage("ctx", "+15553334444",
                "+15551112222", "hi", null);
        // initial queued state, typed
        assertEquals("queued", msg.getState());
        assertEquals(MessageState.QUEUED, msg.getMessageState().orElseThrow());
        assertFalse(msg.getMessageState().orElseThrow().isTerminal());

        mock.push(messagingStateFrame(msg.getMessageId(), "delivered",
                Map.of("from_number", "+15553334444",
                        "to_number", "+15551112222",
                        "body", "hi")));

        RelayEvent event = msg.waitForCompletion(5_000);
        assertNotNull(event);

        assertEquals("delivered", msg.getState());
        assertEquals(MessageState.DELIVERED, msg.getMessageState().orElseThrow());
        assertEquals(msg.getState(), msg.getMessageState().orElseThrow().getValue());
        assertTrue(msg.getMessageState().orElseThrow().isTerminal());
        assertTrue(msg.isDone());
    }

    @Test
    @DisplayName("MessageState: an intermediate sent event is typed but NOT terminal")
    void messageStateIntermediateNotTerminal() throws Exception {
        Message msg = client.sendMessage("ctx", "+15553334444",
                "+15551112222", "hi", null);
        mock.push(messagingStateFrame(msg.getMessageId(), "sent", null));

        for (int i = 0; i < 100; i++) {
            if ("sent".equals(msg.getState())) break;
            Thread.sleep(20);
        }
        assertEquals(MessageState.SENT, msg.getMessageState().orElseThrow());
        assertFalse(msg.getMessageState().orElseThrow().isTerminal());
        assertFalse(msg.isDone());
    }

    // ── DialState off the real dial event the mock emits ─────────────

    @Test
    @DisplayName("DialState: the mock's emitted calling.call.dial decodes to ANSWERED (terminal)")
    void dialStateTypedFromEmittedDialEvent() {
        armDial("t-ds", "winner-ds", List.of("created", "answered"));
        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        client.dial(devices, dialOptions("t-ds"), 5_000);

        // Find the answered calling.call.dial event the server actually sent.
        List<RelayMockTest.JournalEntry> sends =
                mock.journalSend(Constants.EVENT_CALL_DIAL);
        assertFalse(sends.isEmpty(), "no calling.call.dial event was pushed");

        RelayEvent.CallDialEvent answered = null;
        for (RelayMockTest.JournalEntry e : sends) {
            Map<String, Object> inner = e.innerParams();
            if (inner != null && "answered".equals(inner.get("dial_state"))) {
                answered = new RelayEvent.CallDialEvent(
                        Constants.EVENT_CALL_DIAL, 0.0, inner);
                break;
            }
        }
        assertNotNull(answered, "no answered dial event found in the journal");

        assertEquals("answered", answered.getDialState());
        assertEquals(DialState.ANSWERED, answered.getDialStateEnum().orElseThrow());
        assertTrue(answered.getDialStateEnum().orElseThrow().isTerminal());
    }
}
