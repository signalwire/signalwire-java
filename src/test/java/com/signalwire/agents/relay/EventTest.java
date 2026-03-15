package com.signalwire.agents.relay;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RELAY events: types, creation, param access.
 */
class EventTest {

    private RelayEvent.CallStateEvent makeCallStateEvent(String state, String endReason) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("call_id", "call-1");
        params.put("node_id", "node-1");
        params.put("call_state", state);
        if (endReason != null) params.put("end_reason", endReason);
        return new RelayEvent.CallStateEvent(Constants.EVENT_CALL_STATE, 0.0, params);
    }

    @Test
    void testCallStateEvent() {
        var event = makeCallStateEvent(Constants.CALL_STATE_ANSWERED, null);
        assertEquals(Constants.EVENT_CALL_STATE, event.getEventType());
        assertEquals("call-1", event.getCallId());
        assertEquals(Constants.CALL_STATE_ANSWERED, event.getCallState());
        assertNull(event.getEndReason());
    }

    @Test
    void testCallStateEventWithEndReason() {
        var event = makeCallStateEvent(Constants.CALL_STATE_ENDED, Constants.END_REASON_HANGUP);
        assertEquals(Constants.END_REASON_HANGUP, event.getEndReason());
        assertEquals("node-1", event.getNodeId());
    }

    @Test
    void testEventTypes() {
        assertEquals("calling.call.state", Constants.EVENT_CALL_STATE);
        assertEquals("calling.call.receive", Constants.EVENT_CALL_RECEIVE);
        assertEquals("calling.call.dial", Constants.EVENT_CALL_DIAL);
        assertEquals("calling.call.play", Constants.EVENT_CALL_PLAY);
        assertEquals("calling.call.record", Constants.EVENT_CALL_RECORD);
        assertEquals("calling.call.detect", Constants.EVENT_CALL_DETECT);
        assertEquals("calling.call.collect", Constants.EVENT_CALL_COLLECT);
        assertEquals("calling.call.fax", Constants.EVENT_CALL_FAX);
        assertEquals("calling.call.tap", Constants.EVENT_CALL_TAP);
        assertEquals("calling.call.stream", Constants.EVENT_CALL_STREAM);
        assertEquals("calling.call.transcribe", Constants.EVENT_CALL_TRANSCRIBE);
        assertEquals("calling.call.connect", Constants.EVENT_CALL_CONNECT);
        assertEquals("calling.call.refer", Constants.EVENT_CALL_REFER);
        assertEquals("calling.call.send_digits", Constants.EVENT_CALL_SEND_DIGITS);
        assertEquals("calling.call.pay", Constants.EVENT_CALL_PAY);
        assertEquals("calling.conference", Constants.EVENT_CONFERENCE);
        assertEquals("calling.queue", Constants.EVENT_QUEUE);
    }

    @Test
    void testMessagingEventTypes() {
        assertEquals("messaging.receive", Constants.EVENT_MESSAGING_RECEIVE);
        assertEquals("messaging.state", Constants.EVENT_MESSAGING_STATE);
    }

    @Test
    void testAuthorizationEventType() {
        assertEquals("signalwire.authorization.state", Constants.EVENT_AUTHORIZATION_STATE);
    }

    @Test
    void testRelayEventGetStringParam() {
        var event = makeCallStateEvent(Constants.CALL_STATE_ANSWERED, null);
        assertEquals(Constants.CALL_STATE_ANSWERED, event.getCallState());
    }

    @Test
    void testRelayEventBaseClass() {
        RelayEvent event = new RelayEvent("test.event", 123.45, Map.of("key", "value"));
        assertEquals("test.event", event.getEventType());
        assertEquals(123.45, event.getTimestamp(), 0.01);
        assertEquals("value", event.getStringParam("key"));
        assertNull(event.getStringParam("missing"));
        assertEquals("default", event.getStringParam("missing", "default"));
    }

    @Test
    void testRelayEventFromRawParams() {
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("event_type", Constants.EVENT_CALL_STATE);
        outer.put("timestamp", 123.0);
        outer.put("params", Map.of("call_id", "c1", "node_id", "n1", "call_state", "answered"));
        RelayEvent event = RelayEvent.fromRawParams(outer);
        assertInstanceOf(RelayEvent.CallStateEvent.class, event);
    }

    @Test
    void testMessagingReceiveEvent() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("message_id", "msg-1");
        params.put("context", "default");
        params.put("from_number", "+15551234567");
        params.put("to_number", "+15559876543");
        params.put("body", "Hello");
        var event = new RelayEvent.MessagingReceiveEvent(Constants.EVENT_MESSAGING_RECEIVE, 0.0, params);
        assertEquals("msg-1", event.getMessageId());
        assertEquals("default", event.getContext());
        assertEquals("+15551234567", event.getFromNumber());
        assertEquals("Hello", event.getBody());
    }
}
