package com.signalwire.agents.relay;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RELAY Call object: state management, event dispatch, action tracking.
 */
class CallTest {

    private RelayEvent.CallStateEvent makeStateEvent(String callId, String nodeId, String state, String endReason) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("call_id", callId);
        params.put("node_id", nodeId);
        params.put("call_state", state);
        if (endReason != null) params.put("end_reason", endReason);
        return new RelayEvent.CallStateEvent(Constants.EVENT_CALL_STATE, 0.0, params);
    }

    @Test
    void testCallCreation() {
        Call call = new Call("call-123", "node-1");
        assertEquals("call-123", call.getCallId());
        assertEquals("node-1", call.getNodeId());
        assertEquals(Constants.CALL_STATE_CREATED, call.getState());
        assertFalse(call.isEnded());
    }

    @Test
    void testCallSetters() {
        Call call = new Call("call-123", "node-1");
        call.setState(Constants.CALL_STATE_ANSWERED);
        call.setDirection("inbound");
        call.setTag("my-tag");
        call.setDevice(Map.of("type", "phone"));
        call.setEndReason(Constants.END_REASON_HANGUP);

        assertEquals(Constants.CALL_STATE_ANSWERED, call.getState());
        assertEquals("inbound", call.getDirection());
        assertEquals("my-tag", call.getTag());
        assertEquals("phone", call.getDevice().get("type"));
        assertEquals(Constants.END_REASON_HANGUP, call.getEndReason());
    }

    @Test
    void testIsEnded() {
        Call call = new Call("call-123", "node-1");
        assertFalse(call.isEnded());
        call.setState(Constants.CALL_STATE_ENDED);
        assertTrue(call.isEnded());
    }

    @Test
    void testEventListener() {
        Call call = new Call("call-123", "node-1");
        AtomicBoolean received = new AtomicBoolean(false);
        call.on(event -> received.set(true));

        call.dispatchEvent(makeStateEvent("call-123", "node-1", Constants.CALL_STATE_RINGING, null));
        assertTrue(received.get());
    }

    @Test
    void testCallStateEventUpdatesState() {
        Call call = new Call("call-123", "node-1");
        call.dispatchEvent(makeStateEvent("call-123", "node-1", Constants.CALL_STATE_ANSWERED, null));
        assertEquals(Constants.CALL_STATE_ANSWERED, call.getState());
    }

    @Test
    void testCallStateEventUpdatesEndReason() {
        Call call = new Call("call-123", "node-1");
        call.dispatchEvent(makeStateEvent("call-123", "node-1", Constants.CALL_STATE_ENDED, Constants.END_REASON_HANGUP));
        assertEquals(Constants.CALL_STATE_ENDED, call.getState());
        assertEquals(Constants.END_REASON_HANGUP, call.getEndReason());
    }

    @Test
    void testActionRegistration() {
        Call call = new Call("call-123", "node-1");
        Action action = new Action("ctrl-1", call);
        call.registerAction(action);
        assertNotNull(call.getAction("ctrl-1"));
    }

    @Test
    void testResolveAllActions() {
        Call call = new Call("call-123", "node-1");
        Action a1 = new Action("ctrl-1", call);
        Action a2 = new Action("ctrl-2", call);
        call.registerAction(a1);
        call.registerAction(a2);

        RelayEvent event = makeStateEvent("call-123", "node-1", Constants.CALL_STATE_ENDED, null);
        call.resolveAllActions(event);

        assertTrue(a1.isDone());
        assertTrue(a2.isDone());
        assertNull(call.getAction("ctrl-1"));
        assertNull(call.getAction("ctrl-2"));
    }

    @Test
    void testEventListenerErrorHandled() {
        Call call = new Call("call-123", "node-1");
        call.on(event -> { throw new RuntimeException("Listener error"); });
        AtomicBoolean secondCalled = new AtomicBoolean(false);
        call.on(event -> secondCalled.set(true));

        call.dispatchEvent(makeStateEvent("call-123", "node-1", Constants.CALL_STATE_RINGING, null));
        assertTrue(secondCalled.get());
    }

    @Test
    void testSetNodeId() {
        Call call = new Call("call-123", "node-1");
        call.setNodeId("node-2");
        assertEquals("node-2", call.getNodeId());
    }

    @Test
    void testSetClient() {
        Call call = new Call("call-123", "node-1");
        call.setClient(null);
        assertNotNull(call);
    }
}
