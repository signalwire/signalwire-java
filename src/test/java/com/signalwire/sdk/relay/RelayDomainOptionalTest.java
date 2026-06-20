/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@code Optional<T>} accessors on the user-facing RELAY handle objects ({@link Call}
 * and {@link Message}) report present / absent correctly against real, directly-constructed state —
 * no mocks, no transport.
 *
 * <p>The nullable scalar fields (node_id / end_reason / direction / tag on a Call; context /
 * direction / from_number / to_number / body / reason / result on a Message) are only populated as
 * events arrive or the object is built, so the contract these tests pin is: <em>absent before the
 * value is set, present (and exactly the value supplied) afterwards.</em>
 */
class RelayDomainOptionalTest {

  // ── Call ──────────────────────────────────────────────────────────

  @Test
  @DisplayName("Call: nullable scalar accessors are empty on a fresh call")
  void callOptionalsAbsentInitially() {
    // A freshly-constructed Call has only callId + nodeId from the ctor and
    // the default CALL_STATE_CREATED. end_reason / direction / tag are unset.
    Call call = new Call("call-1", null);

    assertTrue(call.getNodeId().isEmpty(), "node_id passed as null -> empty");
    assertTrue(call.getEndReason().isEmpty(), "end_reason unset -> empty");
    assertTrue(call.getDirection().isEmpty(), "direction unset -> empty");
    assertTrue(call.getTag().isEmpty(), "tag unset -> empty");
  }

  @Test
  @DisplayName("Call: nullable scalar accessors carry the exact value once set")
  void callOptionalsPresentAfterSet() {
    Call call = new Call("call-1", "node-9");
    call.setDirection("inbound");
    call.setTag("tag-42");
    call.setEndReason("hangup");

    // ctor-supplied node id
    assertTrue(call.getNodeId().isPresent());
    assertEquals("node-9", call.getNodeId().orElseThrow());
    // setter-supplied values
    assertEquals("inbound", call.getDirection().orElseThrow());
    assertEquals("tag-42", call.getTag().orElseThrow());
    assertEquals("hangup", call.getEndReason().orElseThrow());
  }

  @Test
  @DisplayName("Call: state event populates end_reason + node_id Optionals")
  void callOptionalsPopulatedByStateEvent() {
    Call call = new Call("call-1", null);
    assertTrue(call.getNodeId().isEmpty());
    assertTrue(call.getEndReason().isEmpty());

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("call_id", "call-1");
    params.put("node_id", "node-from-event");
    params.put("call_state", Constants.CALL_STATE_ENDED);
    params.put("end_reason", "busy");
    call.dispatchEvent(new RelayEvent.CallStateEvent(Constants.EVENT_CALL_STATE, 0.0, params));

    assertEquals("node-from-event", call.getNodeId().orElseThrow());
    assertEquals("busy", call.getEndReason().orElseThrow());
  }

  @Test
  @DisplayName("Call.getNodeId() round-trips into the wire node_id (orElse(null))")
  void callNodeIdFeedsWire() {
    // The Optional accessor must still feed the raw wire value an action
    // sends: present -> the id, absent -> a null node_id (unchanged shape).
    Call present = new Call("c", "node-x");
    assertEquals("node-x", present.getNodeId().orElse(null));

    Call absent = new Call("c", null);
    assertEquals(null, absent.getNodeId().orElse(null));
  }

  // ── Message ───────────────────────────────────────────────────────

  @Test
  @DisplayName("Message: nullable scalar accessors are empty on a bare message")
  void messageOptionalsAbsentInitially() {
    Message msg = new Message("msg-1");

    assertTrue(msg.getContext().isEmpty());
    assertTrue(msg.getDirection().isEmpty());
    assertTrue(msg.getFromNumber().isEmpty());
    assertTrue(msg.getToNumber().isEmpty());
    assertTrue(msg.getBody().isEmpty());
    assertTrue(msg.getReason().isEmpty());
    assertTrue(msg.getResult().isEmpty(), "result empty until terminal state");
    assertFalse(msg.isDone());
  }

  @Test
  @DisplayName("Message: nullable scalar accessors carry the exact value once set")
  void messageOptionalsPresentAfterSet() {
    Message msg = new Message("msg-1");
    msg.setContext("ctx");
    msg.setDirection("outbound");
    msg.setFromNumber("+15551112222");
    msg.setToNumber("+15553334444");
    msg.setBody("hello");

    assertEquals("ctx", msg.getContext().orElseThrow());
    assertEquals("outbound", msg.getDirection().orElseThrow());
    assertEquals("+15551112222", msg.getFromNumber().orElseThrow());
    assertEquals("+15553334444", msg.getToNumber().orElseThrow());
    assertEquals("hello", msg.getBody().orElseThrow());
    // body-only set: reason still absent
    assertTrue(msg.getReason().isEmpty());
  }

  @Test
  @DisplayName("Message: a failed terminal event fills reason + result Optionals")
  void messageReasonAndResultAfterFailure() {
    Message msg = new Message("msg-1");
    assertTrue(msg.getReason().isEmpty());
    assertTrue(msg.getResult().isEmpty());

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("message_id", "msg-1");
    params.put("message_state", Constants.MESSAGE_STATE_FAILED);
    params.put("reason", "carrier_blocked");
    RelayEvent.MessagingStateEvent event =
        new RelayEvent.MessagingStateEvent(Constants.EVENT_MESSAGING_STATE, 0.0, params);
    msg.updateFromEvent(event);

    assertTrue(msg.isDone());
    assertEquals("carrier_blocked", msg.getReason().orElseThrow());
    // result is the exact terminal event instance that resolved the message
    Optional<RelayEvent> result = msg.getResult();
    assertTrue(result.isPresent());
    assertSame(event, result.orElseThrow());
  }

  @Test
  @DisplayName("Message.fromReceiveEvent populates the descriptive Optionals")
  void messageFromInboundEvent() {
    Map<String, Object> inner = new LinkedHashMap<>();
    inner.put("message_id", "in-1");
    inner.put("context", "default");
    inner.put("direction", "inbound");
    inner.put("from_number", "+15550001111");
    inner.put("to_number", "+15550002222");
    inner.put("body", "hi there");
    inner.put("message_state", "received");
    RelayEvent.MessagingReceiveEvent event =
        new RelayEvent.MessagingReceiveEvent(Constants.EVENT_MESSAGING_RECEIVE, 0.0, inner);

    Message msg = Message.fromReceiveEvent(event);

    assertEquals("default", msg.getContext().orElseThrow());
    assertEquals("inbound", msg.getDirection().orElseThrow());
    assertEquals("+15550001111", msg.getFromNumber().orElseThrow());
    assertEquals("+15550002222", msg.getToNumber().orElseThrow());
    assertEquals("hi there", msg.getBody().orElseThrow());
    // not a terminal state -> not done, no result yet
    assertFalse(msg.isDone());
    assertTrue(msg.getResult().isEmpty());
  }
}
