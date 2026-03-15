package com.signalwire.agents.relay;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RELAY messaging constants and Message class.
 */
class MessageTest {

    @Test
    void testMessageStates() {
        assertEquals("queued", Constants.MESSAGE_STATE_QUEUED);
        assertEquals("initiated", Constants.MESSAGE_STATE_INITIATED);
        assertEquals("sent", Constants.MESSAGE_STATE_SENT);
        assertEquals("delivered", Constants.MESSAGE_STATE_DELIVERED);
        assertEquals("undelivered", Constants.MESSAGE_STATE_UNDELIVERED);
        assertEquals("failed", Constants.MESSAGE_STATE_FAILED);
        assertEquals("received", Constants.MESSAGE_STATE_RECEIVED);
    }

    @Test
    void testTerminalMessageStates() {
        assertTrue(Constants.isTerminalMessageState(Constants.MESSAGE_STATE_DELIVERED));
        assertTrue(Constants.isTerminalMessageState(Constants.MESSAGE_STATE_UNDELIVERED));
        assertTrue(Constants.isTerminalMessageState(Constants.MESSAGE_STATE_FAILED));
        assertFalse(Constants.isTerminalMessageState(Constants.MESSAGE_STATE_QUEUED));
        assertFalse(Constants.isTerminalMessageState(Constants.MESSAGE_STATE_INITIATED));
        assertFalse(Constants.isTerminalMessageState(Constants.MESSAGE_STATE_SENT));
        assertFalse(Constants.isTerminalMessageState(Constants.MESSAGE_STATE_RECEIVED));
    }

    @Test
    void testMessagingSendMethod() {
        assertEquals("messaging.send", Constants.METHOD_MESSAGING_SEND);
    }

    @Test
    void testMessageCreation() {
        Message msg = new Message("msg-123");
        assertEquals("msg-123", msg.getMessageId());
        assertNull(msg.getState());
        assertFalse(msg.isDone());
    }

    @Test
    void testMessageProperties() {
        Message msg = new Message("msg-123");
        msg.setFromNumber("+15551234567");
        msg.setToNumber("+15559876543");
        msg.setBody("Hello");
        msg.setDirection("outbound");
        msg.setContext("default");
        msg.setSegments(2);
        msg.setMedia(List.of("https://example.com/image.jpg"));
        msg.setTags(List.of("tag1", "tag2"));

        assertEquals("+15551234567", msg.getFromNumber());
        assertEquals("+15559876543", msg.getToNumber());
        assertEquals("Hello", msg.getBody());
        assertEquals("outbound", msg.getDirection());
        assertEquals("default", msg.getContext());
        assertEquals(2, msg.getSegments());
        assertEquals(1, msg.getMedia().size());
        assertEquals(2, msg.getTags().size());
    }

    @Test
    void testMessageNullMediaSafe() {
        Message msg = new Message("msg-123");
        msg.setMedia(null);
        assertNotNull(msg.getMedia());
        assertTrue(msg.getMedia().isEmpty());
    }

    @Test
    void testMessageNullTagsSafe() {
        Message msg = new Message("msg-123");
        msg.setTags(null);
        assertNotNull(msg.getTags());
        assertTrue(msg.getTags().isEmpty());
    }

    @Test
    void testMessageIsDoneOnDelivered() {
        Message msg = new Message("msg-123");
        assertFalse(msg.isDone());

        // Simulate delivery event
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("message_id", "msg-123");
        params.put("message_state", Constants.MESSAGE_STATE_DELIVERED);
        var event = new RelayEvent.MessagingStateEvent(Constants.EVENT_MESSAGING_STATE, 0.0, params);
        msg.updateFromEvent(event);

        assertEquals(Constants.MESSAGE_STATE_DELIVERED, msg.getState());
        assertTrue(msg.isDone());
    }

    @Test
    void testMessageIsDoneOnFailed() {
        Message msg = new Message("msg-123");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("message_id", "msg-123");
        params.put("message_state", Constants.MESSAGE_STATE_FAILED);
        params.put("reason", "Invalid number");
        var event = new RelayEvent.MessagingStateEvent(Constants.EVENT_MESSAGING_STATE, 0.0, params);
        msg.updateFromEvent(event);

        assertTrue(msg.isDone());
        assertEquals("Invalid number", msg.getReason());
    }

    @Test
    void testMessageToString() {
        Message msg = new Message("msg-123");
        msg.setFromNumber("+15551234567");
        msg.setToNumber("+15559876543");
        String s = msg.toString();
        assertTrue(s.contains("msg-123"));
    }
}
