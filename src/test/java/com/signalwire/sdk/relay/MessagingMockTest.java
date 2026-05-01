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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-relay-backed unit tests translated from
 * {@code signalwire-python/tests/unit/relay/test_messaging_mock.py}.
 */
class MessagingMockTest {

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

    private static Map<String, Object> stateEventFrame(
            String messageId, String state, Map<String, Object> extras) {
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

    // ── send_message — outbound ─────────────────────────────────────

    @Test
    @DisplayName("send_message journals messaging.send with body and tags")
    void sendMessageJournalsMessagingSend() {
        Message msg = client.sendMessage("ctx", "+15553334444",
                "+15551112222", "hello", null, List.of("t1", "t2"));
        assertNotNull(msg);
        assertNotNull(msg.getMessageId(), "mock generates a message_id");
        assertEquals("hello", msg.getBody());

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_MESSAGING_SEND);
        assertEquals(1, entries.size(),
                "expected 1 messaging.send; got " + entries.size());
        Map<String, Object> p = entries.get(0).params();
        assertEquals("+15551112222", p.get("to_number"));
        assertEquals("+15553334444", p.get("from_number"));
        assertEquals("hello", p.get("body"));
        assertEquals(List.of("t1", "t2"), p.get("tags"));
    }

    @Test
    @DisplayName("send_message with media only carries media but no body")
    void sendMessageWithMediaOnly() {
        Message msg = client.sendMessage("ctx", "+15553334444",
                "+15551112222", null,
                List.of("https://media.example/cat.jpg"));
        assertNotNull(msg);

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_MESSAGING_SEND);
        assertEquals(1, entries.size());
        Map<String, Object> p = entries.get(0).params();
        assertEquals(List.of("https://media.example/cat.jpg"), p.get("media"));
        Object body = p.get("body");
        assertTrue(body == null || (body instanceof String && ((String) body).isEmpty()),
                "body should not be set in media-only send; got: " + body);
    }

    @Test
    @DisplayName("send_message includes context on the wire")
    void sendMessageIncludesContext() {
        client.sendMessage("custom-ctx", "+15553334444",
                "+15551112222", "hi", null);
        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_MESSAGING_SEND);
        assertEquals(1, entries.size());
        assertEquals("custom-ctx", entries.get(0).params().get("context"));
    }

    @Test
    @DisplayName("send_message returns initial state queued")
    void sendMessageReturnsInitialStateQueued() {
        Message msg = client.sendMessage("ctx", "+15553334444",
                "+15551112222", "hi", null);
        assertEquals("queued", msg.getState());
        assertFalse(msg.isDone());
    }

    @Test
    @DisplayName("Pushed messaging.state(delivered) resolves Message.wait()")
    void sendMessageResolvesOnDelivered() throws Exception {
        Message msg = client.sendMessage("ctx", "+15553334444",
                "+15551112222", "hi", null);
        Map<String, Object> frame = stateEventFrame(msg.getMessageId(), "delivered",
                Map.of("from_number", "+15553334444",
                        "to_number", "+15551112222",
                        "body", "hi"));
        mock.push(frame);

        RelayEvent event = msg.waitForCompletion(5_000);
        assertNotNull(event);
        assertEquals("delivered", msg.getState());
        assertTrue(msg.isDone());
    }

    @Test
    @DisplayName("Pushed messaging.state(undelivered) resolves with reason")
    void sendMessageResolvesOnUndelivered() throws Exception {
        Message msg = client.sendMessage("ctx", "+15553334444",
                "+15551112222", "hi", null);
        Map<String, Object> frame = stateEventFrame(msg.getMessageId(), "undelivered",
                Map.of("reason", "carrier_blocked"));
        mock.push(frame);

        msg.waitForCompletion(5_000);
        assertEquals("undelivered", msg.getState());
        assertEquals("carrier_blocked", msg.getReason());
    }

    @Test
    @DisplayName("Pushed messaging.state(failed) resolves Message")
    void sendMessageResolvesOnFailed() throws Exception {
        Message msg = client.sendMessage("ctx", "+15553334444",
                "+15551112222", "hi", null);
        Map<String, Object> frame = stateEventFrame(msg.getMessageId(), "failed",
                Map.of("reason", "spam"));
        mock.push(frame);

        msg.waitForCompletion(5_000);
        assertEquals("failed", msg.getState());
    }

    @Test
    @DisplayName("Intermediate state does not resolve the message")
    void sendMessageIntermediateStateDoesNotResolve() throws Exception {
        Message msg = client.sendMessage("ctx", "+15553334444",
                "+15551112222", "hi", null);
        Map<String, Object> frame = stateEventFrame(msg.getMessageId(), "sent", null);
        mock.push(frame);

        // Wait for state propagation.
        for (int i = 0; i < 100; i++) {
            if ("sent".equals(msg.getState())) break;
            Thread.sleep(20);
        }
        assertEquals("sent", msg.getState());
        assertFalse(msg.isDone());
    }

    // ── Inbound messages ───────────────────────────────────────────

    @Test
    @DisplayName("Pushed messaging.receive event invokes the on_message handler")
    void inboundMessageFiresOnMessageHandler() throws Exception {
        AtomicReference<Message> seen = new AtomicReference<>();
        CompletableFuture<Void> done = new CompletableFuture<>();

        client.onMessage(m -> {
            seen.set(m);
            done.complete(null);
        });

        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("message_id", "in-msg-1");
        inner.put("context", "default");
        inner.put("direction", "inbound");
        inner.put("from_number", "+15551110000");
        inner.put("to_number", "+15552220000");
        inner.put("body", "hello back");
        inner.put("media", List.of());
        inner.put("segments", 1);
        inner.put("message_state", "received");
        inner.put("tags", List.of("incoming"));

        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("event_type", Constants.EVENT_MESSAGING_RECEIVE);
        outer.put("params", inner);

        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", Constants.JSONRPC_VERSION);
        frame.put("id", UUID.randomUUID().toString());
        frame.put("method", "signalwire.event");
        frame.put("params", outer);

        mock.push(frame);

        done.get(5, java.util.concurrent.TimeUnit.SECONDS);
        Message m = seen.get();
        assertNotNull(m);
        assertEquals("in-msg-1", m.getMessageId());
        assertEquals("inbound", m.getDirection());
        assertEquals("+15551110000", m.getFromNumber());
        assertEquals("+15552220000", m.getToNumber());
        assertEquals("hello back", m.getBody());
        assertEquals(List.of("incoming"), m.getTags());
    }

    // ── State progression — full pipeline ─────────────────────────

    @Test
    @DisplayName("Full state progression sent -> delivered updates state")
    void fullMessageStateProgression() throws Exception {
        Message msg = client.sendMessage("ctx", "+15553334444",
                "+15551112222", "full pipeline", null);
        // Push intermediate "sent".
        mock.push(stateEventFrame(msg.getMessageId(), "sent", null));
        for (int i = 0; i < 100; i++) {
            if ("sent".equals(msg.getState())) break;
            Thread.sleep(20);
        }
        assertEquals("sent", msg.getState());

        // Then "delivered".
        mock.push(stateEventFrame(msg.getMessageId(), "delivered", null));
        msg.waitForCompletion(5_000);
        assertEquals("delivered", msg.getState());
    }
}
