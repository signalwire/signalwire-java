package com.signalwire.agents.relay;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RELAY Action and subclasses.
 */
class ActionTest {

    private Call dummyCall() {
        return new Call("call-1", "node-1");
    }

    private RelayEvent dummyEvent() {
        return new RelayEvent.CallStateEvent(Constants.EVENT_CALL_STATE, 0.0,
                Map.of("call_id", "call-1", "node_id", "node-1",
                        "call_state", Constants.CALL_STATE_ENDED));
    }

    @Test
    void testActionCreation() {
        Action action = new Action("ctrl-1", dummyCall());
        assertEquals("ctrl-1", action.getControlId());
        assertFalse(action.isDone());
        assertNull(action.getState());
    }

    @Test
    void testActionUpdateState() {
        Action action = new Action("ctrl-1", dummyCall());
        action.updateState(Constants.ACTION_STATE_PLAYING, dummyEvent());
        assertEquals(Constants.ACTION_STATE_PLAYING, action.getState());
        assertFalse(action.isDone());
    }

    @Test
    void testActionTerminalState() {
        Action action = new Action("ctrl-1", dummyCall());
        action.updateState(Constants.ACTION_STATE_FINISHED, dummyEvent());
        assertEquals(Constants.ACTION_STATE_FINISHED, action.getState());
        assertTrue(action.isDone());
    }

    @Test
    void testActionResolve() {
        Action action = new Action("ctrl-1", dummyCall());
        action.resolve(dummyEvent());
        assertTrue(action.isDone());
    }

    @Test
    void testActionTerminalStates() {
        assertTrue(Constants.isTerminalActionState(Constants.ACTION_STATE_FINISHED));
        assertTrue(Constants.isTerminalActionState(Constants.ACTION_STATE_ERROR));
        assertTrue(Constants.isTerminalActionState(Constants.ACTION_STATE_NO_INPUT));
        assertTrue(Constants.isTerminalActionState(Constants.ACTION_STATE_NO_MATCH));
        assertFalse(Constants.isTerminalActionState(Constants.ACTION_STATE_PLAYING));
        assertFalse(Constants.isTerminalActionState(Constants.ACTION_STATE_PAUSED));
        assertFalse(Constants.isTerminalActionState(Constants.ACTION_STATE_RECORDING));
    }

    @Test
    void testActionStates() {
        assertEquals("playing", Constants.ACTION_STATE_PLAYING);
        assertEquals("paused", Constants.ACTION_STATE_PAUSED);
        assertEquals("finished", Constants.ACTION_STATE_FINISHED);
        assertEquals("error", Constants.ACTION_STATE_ERROR);
        assertEquals("no_input", Constants.ACTION_STATE_NO_INPUT);
        assertEquals("no_match", Constants.ACTION_STATE_NO_MATCH);
        assertEquals("recording", Constants.ACTION_STATE_RECORDING);
    }

    @Test
    void testPlayAndCollectAction() {
        var action = new Action.PlayAndCollectAction("ctrl-1", dummyCall());
        assertEquals("ctrl-1", action.getControlId());
        assertFalse(action.isDone());
    }

    @Test
    void testPlayAction() {
        var action = new Action.PlayAction("ctrl-1", dummyCall());
        assertEquals("ctrl-1", action.getControlId());
        assertFalse(action.isDone());
    }

    @Test
    void testRecordAction() {
        var action = new Action.RecordAction("ctrl-1", dummyCall());
        assertEquals("ctrl-1", action.getControlId());
        assertFalse(action.isDone());
    }

    @Test
    void testActionGetResult() {
        Action action = new Action("ctrl-1", dummyCall());
        assertNull(action.getResult());
        action.updateState(Constants.ACTION_STATE_FINISHED, dummyEvent());
        assertNotNull(action.getResult());
    }

    @Test
    void testActionResolveOnlyOnce() {
        Action action = new Action("ctrl-1", dummyCall());
        action.resolve(dummyEvent());
        assertTrue(action.isDone());
        // Second resolve should not throw
        action.resolve(dummyEvent());
        assertTrue(action.isDone());
    }

    @Test
    void testActionOnCompleted() {
        Action action = new Action("ctrl-1", dummyCall());
        var called = new java.util.concurrent.atomic.AtomicBoolean(false);
        action.setOnCompleted(a -> called.set(true));
        action.resolve(dummyEvent());
        assertTrue(called.get());
    }

    @Test
    void testActionGetCall() {
        Call call = dummyCall();
        Action action = new Action("ctrl-1", call);
        assertSame(call, action.getCall());
    }
}
