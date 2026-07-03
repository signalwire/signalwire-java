package com.signalwire.sdk.relay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Tests for RELAY Call object: state management, event dispatch, action tracking. */
class CallTest {

  private RelayEvent.CallStateEvent makeStateEvent(
      String callId, String nodeId, String state, String endReason) {
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
    assertEquals("node-1", call.getNodeId().orElseThrow());
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
    assertEquals("inbound", call.getDirection().orElseThrow());
    assertEquals("my-tag", call.getTag().orElseThrow());
    assertEquals("phone", call.getDevice().get("type"));
    assertEquals(Constants.END_REASON_HANGUP, call.getEndReason().orElseThrow());
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
    call.dispatchEvent(
        makeStateEvent(
            "call-123", "node-1", Constants.CALL_STATE_ENDED, Constants.END_REASON_HANGUP));
    assertEquals(Constants.CALL_STATE_ENDED, call.getState());
    assertEquals(Constants.END_REASON_HANGUP, call.getEndReason().orElseThrow());
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
    call.on(
        event -> {
          throw new RuntimeException("Listener error");
        });
    AtomicBoolean secondCalled = new AtomicBoolean(false);
    call.on(event -> secondCalled.set(true));

    call.dispatchEvent(makeStateEvent("call-123", "node-1", Constants.CALL_STATE_RINGING, null));
    assertTrue(secondCalled.get());
  }

  @Test
  void testSetNodeId() {
    Call call = new Call("call-123", "node-1");
    call.setNodeId("node-2");
    assertEquals("node-2", call.getNodeId().orElseThrow());
  }

  @Test
  void testSetClient() {
    Call call = new Call("call-123", "node-1");
    call.setClient(null);
    assertNotNull(call);
  }

  // ── State-wait helpers (Python parity: Call.wait_for*) ───────────

  @Test
  void testWaitForReturnsImmediatelyWhenAlreadyPastTarget() {
    Call call = new Call("call-1", "node-1");
    call.setState(Constants.CALL_STATE_ANSWERED);
    // ringing is earlier than answered — must resolve immediately.
    RelayEvent e = call.waitForRinging();
    assertNotNull(e);
    assertInstanceOf(RelayEvent.CallStateEvent.class, e);
    assertEquals(Constants.CALL_STATE_ANSWERED, ((RelayEvent.CallStateEvent) e).getCallState());
  }

  @Test
  void testWaitForResolvesOnLaterStateEvent() throws Exception {
    Call call = new Call("call-1", "node-1"); // starts CREATED
    Thread producer =
        new Thread(
            () -> {
              try {
                Thread.sleep(50);
              } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
              }
              call.dispatchEvent(
                  makeStateEvent("call-1", "node-1", Constants.CALL_STATE_ANSWERED, null));
            });
    producer.start();

    RelayEvent e = call.waitForAnswered();
    producer.join();
    assertNotNull(e);
    assertEquals(Constants.CALL_STATE_ANSWERED, ((RelayEvent.CallStateEvent) e).getCallState());
  }

  @Test
  void testWaitForTimesOutWhenStateNeverReached() {
    Call call = new Call("call-1", "node-1"); // stays CREATED
    RelayEvent e = call.waitFor(Constants.CALL_STATE_ENDED, 60);
    assertNull(e, "expected null on timeout");
  }

  @Test
  void testWaitForEndedResolvesOnEndEvent() throws Exception {
    Call call = new Call("call-1", "node-1");
    Thread producer =
        new Thread(
            () -> {
              try {
                Thread.sleep(30);
              } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
              }
              call.dispatchEvent(
                  makeStateEvent(
                      "call-1", "node-1", Constants.CALL_STATE_ENDED, Constants.END_REASON_HANGUP));
            });
    producer.start();
    RelayEvent e = call.waitForEnded();
    producer.join();
    assertNotNull(e);
    assertEquals(Constants.CALL_STATE_ENDED, ((RelayEvent.CallStateEvent) e).getCallState());
  }
}
