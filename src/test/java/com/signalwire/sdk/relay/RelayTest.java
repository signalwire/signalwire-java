/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the RELAY client components. No live WebSocket connections -- tests verify
 * constants, events, actions, call logic, message tracking, and client creation.
 */
class RelayTest {

  // ── Constants ────────────────────────────────────────────────────

  @Nested
  @DisplayName("Constants")
  class ConstantsTests {

    @Test
    @DisplayName("Call states are defined correctly")
    void callStates() {
      assertEquals("created", Constants.CALL_STATE_CREATED);
      assertEquals("ringing", Constants.CALL_STATE_RINGING);
      assertEquals("answered", Constants.CALL_STATE_ANSWERED);
      assertEquals("ending", Constants.CALL_STATE_ENDING);
      assertEquals("ended", Constants.CALL_STATE_ENDED);
    }

    @Test
    @DisplayName("End reasons are defined correctly")
    void endReasons() {
      assertEquals("hangup", Constants.END_REASON_HANGUP);
      assertEquals("cancel", Constants.END_REASON_CANCEL);
      assertEquals("busy", Constants.END_REASON_BUSY);
      assertEquals("noAnswer", Constants.END_REASON_NO_ANSWER);
      assertEquals("decline", Constants.END_REASON_DECLINE);
      assertEquals("error", Constants.END_REASON_ERROR);
    }

    @Test
    @DisplayName("Message states are defined correctly")
    void messageStates() {
      assertEquals("queued", Constants.MESSAGE_STATE_QUEUED);
      assertEquals("initiated", Constants.MESSAGE_STATE_INITIATED);
      assertEquals("sent", Constants.MESSAGE_STATE_SENT);
      assertEquals("delivered", Constants.MESSAGE_STATE_DELIVERED);
      assertEquals("undelivered", Constants.MESSAGE_STATE_UNDELIVERED);
      assertEquals("failed", Constants.MESSAGE_STATE_FAILED);
      assertEquals("received", Constants.MESSAGE_STATE_RECEIVED);
    }

    @Test
    @DisplayName("Event types are defined correctly")
    void eventTypes() {
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
      assertEquals("messaging.receive", Constants.EVENT_MESSAGING_RECEIVE);
      assertEquals("messaging.state", Constants.EVENT_MESSAGING_STATE);
      assertEquals("signalwire.authorization.state", Constants.EVENT_AUTHORIZATION_STATE);
    }

    @Test
    @DisplayName("Terminal state checks work correctly")
    void terminalStates() {
      assertTrue(Constants.isTerminalCallState("ended"));
      assertFalse(Constants.isTerminalCallState("answered"));
      assertFalse(Constants.isTerminalCallState("ringing"));

      assertTrue(Constants.isTerminalActionState("finished"));
      assertTrue(Constants.isTerminalActionState("error"));
      assertTrue(Constants.isTerminalActionState("no_input"));
      assertTrue(Constants.isTerminalActionState("no_match"));
      assertFalse(Constants.isTerminalActionState("playing"));

      assertTrue(Constants.isTerminalMessageState("delivered"));
      assertTrue(Constants.isTerminalMessageState("undelivered"));
      assertTrue(Constants.isTerminalMessageState("failed"));
      assertFalse(Constants.isTerminalMessageState("queued"));
      assertFalse(Constants.isTerminalMessageState("sent"));
    }

    @Test
    @DisplayName("Call-gone code check works")
    void callGoneCodes() {
      assertTrue(Constants.isCallGoneCode("404"));
      assertTrue(Constants.isCallGoneCode("410"));
      assertFalse(Constants.isCallGoneCode("200"));
      assertFalse(Constants.isCallGoneCode("409"));
    }

    @Test
    @DisplayName("RPC methods are defined")
    void rpcMethods() {
      assertEquals("signalwire.connect", Constants.METHOD_CONNECT);
      assertEquals("signalwire.event", Constants.METHOD_EVENT);
      assertEquals("signalwire.ping", Constants.METHOD_PING);
      assertEquals("signalwire.disconnect", Constants.METHOD_DISCONNECT);
      assertEquals("calling.dial", Constants.METHOD_DIAL);
      assertEquals("calling.answer", Constants.METHOD_ANSWER);
      assertEquals("calling.end", Constants.METHOD_END);
      assertEquals("calling.play", Constants.METHOD_PLAY);
      assertEquals("calling.record", Constants.METHOD_RECORD);
      assertEquals("calling.detect", Constants.METHOD_DETECT);
      assertEquals("calling.collect", Constants.METHOD_COLLECT);
      assertEquals("calling.ai", Constants.METHOD_AI);
      assertEquals("messaging.send", Constants.METHOD_MESSAGING_SEND);
    }

    @Test
    @DisplayName("Action states are defined")
    void actionStates() {
      assertEquals("playing", Constants.ACTION_STATE_PLAYING);
      assertEquals("paused", Constants.ACTION_STATE_PAUSED);
      assertEquals("finished", Constants.ACTION_STATE_FINISHED);
      assertEquals("error", Constants.ACTION_STATE_ERROR);
      assertEquals("no_input", Constants.ACTION_STATE_NO_INPUT);
      assertEquals("no_match", Constants.ACTION_STATE_NO_MATCH);
    }

    @Test
    @DisplayName("Reconnection constants are defined")
    void reconnectionConstants() {
      assertEquals(1000L, Constants.RECONNECT_INITIAL_DELAY_MS);
      assertEquals(30000L, Constants.RECONNECT_MAX_DELAY_MS);
      assertEquals(2.0, Constants.RECONNECT_BACKOFF_MULTIPLIER);
    }

    @Test
    @DisplayName("Dial states are defined")
    void dialStates() {
      assertEquals("dialing", Constants.DIAL_STATE_DIALING);
      assertEquals("answered", Constants.DIAL_STATE_ANSWERED);
      assertEquals("failed", Constants.DIAL_STATE_FAILED);
    }

    @Test
    @DisplayName("Media and device types are defined")
    void mediaAndDeviceTypes() {
      assertEquals("audio", Constants.MEDIA_TYPE_AUDIO);
      assertEquals("tts", Constants.MEDIA_TYPE_TTS);
      assertEquals("silence", Constants.MEDIA_TYPE_SILENCE);
      assertEquals("ringtone", Constants.MEDIA_TYPE_RINGTONE);
      assertEquals("phone", Constants.DEVICE_TYPE_PHONE);
      assertEquals("sip", Constants.DEVICE_TYPE_SIP);
      assertEquals("webrtc", Constants.DEVICE_TYPE_WEBRTC);
    }
  }

  // ── Events ───────────────────────────────────────────────────────

  @Nested
  @DisplayName("Events")
  class EventTests {

    @Test
    @DisplayName("Base RelayEvent stores properties")
    void baseEvent() {
      Map<String, Object> params = Map.of("key", "value");
      RelayEvent event = new RelayEvent("test.event", 12345.678, params);
      assertEquals("test.event", event.getEventType());
      assertEquals(12345.678, event.getTimestamp());
      assertEquals("value", event.getStringParam("key"));
      assertNull(event.getStringParam("missing"));
      assertEquals("default", event.getStringParam("missing", "default"));
    }

    @Test
    @DisplayName("CallStateEvent is created from raw params")
    void callStateEvent() {
      Map<String, Object> innerParams = new HashMap<>();
      innerParams.put("call_id", "call-123");
      innerParams.put("node_id", "node-456");
      innerParams.put("call_state", "answered");
      innerParams.put("tag", "tag-789");
      innerParams.put("direction", "inbound");

      Map<String, Object> outerParams = new HashMap<>();
      outerParams.put("event_type", Constants.EVENT_CALL_STATE);
      outerParams.put("timestamp", 12345.0);
      outerParams.put("params", innerParams);

      RelayEvent event = RelayEvent.fromRawParams(outerParams);
      assertInstanceOf(RelayEvent.CallStateEvent.class, event);

      RelayEvent.CallStateEvent stateEvent = (RelayEvent.CallStateEvent) event;
      assertEquals("call-123", stateEvent.getCallId());
      assertEquals("node-456", stateEvent.getNodeId());
      assertEquals("answered", stateEvent.getCallState());
      assertEquals("tag-789", stateEvent.getTag());
      assertEquals("inbound", stateEvent.getDirection());
    }

    @Test
    @DisplayName("CallReceiveEvent is created from raw params")
    void callReceiveEvent() {
      Map<String, Object> innerParams = new HashMap<>();
      innerParams.put("call_id", "call-abc");
      innerParams.put("node_id", "node-def");
      innerParams.put("call_state", "ringing");
      innerParams.put("context", "default");

      Map<String, Object> outerParams = new HashMap<>();
      outerParams.put("event_type", Constants.EVENT_CALL_RECEIVE);
      outerParams.put("timestamp", 99.0);
      outerParams.put("params", innerParams);

      RelayEvent event = RelayEvent.fromRawParams(outerParams);
      assertInstanceOf(RelayEvent.CallReceiveEvent.class, event);

      RelayEvent.CallReceiveEvent recvEvent = (RelayEvent.CallReceiveEvent) event;
      assertEquals("call-abc", recvEvent.getCallId());
      assertEquals("default", recvEvent.getContext());
    }

    @Test
    @DisplayName("CallDialEvent has nested call info")
    void callDialEvent() {
      Map<String, Object> callInfo = new HashMap<>();
      callInfo.put("call_id", "winner-uuid");
      callInfo.put("node_id", "node-uuid");

      Map<String, Object> innerParams = new HashMap<>();
      innerParams.put("tag", "my-tag-123");
      innerParams.put("dial_state", "answered");
      innerParams.put("node_id", "node-uuid");
      innerParams.put("call", callInfo);

      Map<String, Object> outerParams = new HashMap<>();
      outerParams.put("event_type", Constants.EVENT_CALL_DIAL);
      outerParams.put("timestamp", 100.0);
      outerParams.put("params", innerParams);

      RelayEvent event = RelayEvent.fromRawParams(outerParams);
      assertInstanceOf(RelayEvent.CallDialEvent.class, event);

      RelayEvent.CallDialEvent dialEvent = (RelayEvent.CallDialEvent) event;
      assertEquals("my-tag-123", dialEvent.getTag());
      assertEquals("answered", dialEvent.getDialState());
      assertEquals("winner-uuid", dialEvent.getCallId());
      assertEquals("node-uuid", dialEvent.getNodeId());
    }

    @Test
    @DisplayName("CallPlayEvent has control_id and state")
    void callPlayEvent() {
      Map<String, Object> innerParams = new HashMap<>();
      innerParams.put("call_id", "c1");
      innerParams.put("control_id", "ctl1");
      innerParams.put("state", "finished");

      Map<String, Object> outerParams = new HashMap<>();
      outerParams.put("event_type", Constants.EVENT_CALL_PLAY);
      outerParams.put("timestamp", 200.0);
      outerParams.put("params", innerParams);

      RelayEvent event = RelayEvent.fromRawParams(outerParams);
      assertInstanceOf(RelayEvent.CallPlayEvent.class, event);

      RelayEvent.CallPlayEvent playEvent = (RelayEvent.CallPlayEvent) event;
      assertEquals("c1", playEvent.getCallId());
      assertEquals("ctl1", playEvent.getControlId());
      assertEquals("finished", playEvent.getState());
    }

    @Test
    @DisplayName("CallRecordEvent handles nested record data")
    void callRecordEvent() {
      // Record data can be at top level or nested
      Map<String, Object> innerParams = new HashMap<>();
      innerParams.put("call_id", "c1");
      innerParams.put("control_id", "ctl1");
      innerParams.put("state", "finished");
      innerParams.put("url", "https://example.com/recording.mp3");
      innerParams.put("duration", 30.5);
      innerParams.put("size", 12345L);

      Map<String, Object> outerParams = new HashMap<>();
      outerParams.put("event_type", Constants.EVENT_CALL_RECORD);
      outerParams.put("timestamp", 300.0);
      outerParams.put("params", innerParams);

      RelayEvent event = RelayEvent.fromRawParams(outerParams);
      assertInstanceOf(RelayEvent.CallRecordEvent.class, event);

      RelayEvent.CallRecordEvent recordEvent = (RelayEvent.CallRecordEvent) event;
      assertEquals("https://example.com/recording.mp3", recordEvent.getUrl());
      assertEquals(30.5, recordEvent.getDuration());
      assertEquals(12345L, recordEvent.getSize());
    }

    @Test
    @DisplayName("CallDetectEvent accesses detect params")
    void callDetectEvent() {
      Map<String, Object> detectParams = new HashMap<>();
      detectParams.put("event", "HUMAN");

      Map<String, Object> detect = new HashMap<>();
      detect.put("type", "machine");
      detect.put("params", detectParams);

      Map<String, Object> innerParams = new HashMap<>();
      innerParams.put("call_id", "c1");
      innerParams.put("control_id", "ctl1");
      innerParams.put("detect", detect);

      Map<String, Object> outerParams = new HashMap<>();
      outerParams.put("event_type", Constants.EVENT_CALL_DETECT);
      outerParams.put("timestamp", 400.0);
      outerParams.put("params", innerParams);

      RelayEvent event = RelayEvent.fromRawParams(outerParams);
      assertInstanceOf(RelayEvent.CallDetectEvent.class, event);

      RelayEvent.CallDetectEvent detectEvent = (RelayEvent.CallDetectEvent) event;
      assertEquals("HUMAN", detectEvent.getDetectEvent());
    }

    @Test
    @DisplayName("CallCollectEvent has result type")
    void callCollectEvent() {
      Map<String, Object> innerParams = new HashMap<>();
      innerParams.put("call_id", "c1");
      innerParams.put("control_id", "ctl1");
      innerParams.put("type", "digit");

      Map<String, Object> outerParams = new HashMap<>();
      outerParams.put("event_type", Constants.EVENT_CALL_COLLECT);
      outerParams.put("timestamp", 500.0);
      outerParams.put("params", innerParams);

      RelayEvent event = RelayEvent.fromRawParams(outerParams);
      assertInstanceOf(RelayEvent.CallCollectEvent.class, event);
      assertEquals("digit", ((RelayEvent.CallCollectEvent) event).getResultType());
    }

    @Test
    @DisplayName("AuthorizationStateEvent extracts state")
    void authorizationStateEvent() {
      Map<String, Object> innerParams = new HashMap<>();
      innerParams.put("authorization_state", "encrypted-base64:tag-base64");

      Map<String, Object> outerParams = new HashMap<>();
      outerParams.put("event_type", Constants.EVENT_AUTHORIZATION_STATE);
      outerParams.put("timestamp", 600.0);
      outerParams.put("params", innerParams);

      RelayEvent event = RelayEvent.fromRawParams(outerParams);
      assertInstanceOf(RelayEvent.AuthorizationStateEvent.class, event);
      assertEquals(
          "encrypted-base64:tag-base64",
          ((RelayEvent.AuthorizationStateEvent) event).getAuthorizationState());
    }

    @Test
    @DisplayName("MessagingReceiveEvent extracts all fields")
    void messagingReceiveEvent() {
      Map<String, Object> innerParams = new HashMap<>();
      innerParams.put("message_id", "msg-123");
      innerParams.put("context", "my_context");
      innerParams.put("direction", "inbound");
      innerParams.put("from_number", "+15553333333");
      innerParams.put("to_number", "+15551111111");
      innerParams.put("body", "Hello");
      innerParams.put("media", List.of());
      innerParams.put("segments", 1);
      innerParams.put("message_state", "received");
      innerParams.put("tags", List.of("vip"));

      Map<String, Object> outerParams = new HashMap<>();
      outerParams.put("event_type", Constants.EVENT_MESSAGING_RECEIVE);
      outerParams.put("timestamp", 700.0);
      outerParams.put("params", innerParams);

      RelayEvent event = RelayEvent.fromRawParams(outerParams);
      assertInstanceOf(RelayEvent.MessagingReceiveEvent.class, event);

      RelayEvent.MessagingReceiveEvent msgEvent = (RelayEvent.MessagingReceiveEvent) event;
      assertEquals("msg-123", msgEvent.getMessageId());
      assertEquals("my_context", msgEvent.getContext());
      assertEquals("inbound", msgEvent.getDirection());
      assertEquals("+15553333333", msgEvent.getFromNumber());
      assertEquals("+15551111111", msgEvent.getToNumber());
      assertEquals("Hello", msgEvent.getBody());
      assertEquals(1, msgEvent.getSegments());
      assertEquals("received", msgEvent.getMessageState());
      assertEquals(List.of("vip"), msgEvent.getTags());
    }

    @Test
    @DisplayName("MessagingStateEvent extracts state and reason")
    void messagingStateEvent() {
      Map<String, Object> innerParams = new HashMap<>();
      innerParams.put("message_id", "msg-456");
      innerParams.put("message_state", "failed");
      innerParams.put("reason", "Invalid number");
      innerParams.put("direction", "outbound");
      innerParams.put("from_number", "+15551111111");
      innerParams.put("to_number", "+15552222222");

      Map<String, Object> outerParams = new HashMap<>();
      outerParams.put("event_type", Constants.EVENT_MESSAGING_STATE);
      outerParams.put("timestamp", 800.0);
      outerParams.put("params", innerParams);

      RelayEvent event = RelayEvent.fromRawParams(outerParams);
      assertInstanceOf(RelayEvent.MessagingStateEvent.class, event);

      RelayEvent.MessagingStateEvent stateEvent = (RelayEvent.MessagingStateEvent) event;
      assertEquals("msg-456", stateEvent.getMessageId());
      assertEquals("failed", stateEvent.getMessageState());
      assertEquals("Invalid number", stateEvent.getReason());
    }

    @Test
    @DisplayName("Unknown event type returns base RelayEvent")
    void unknownEvent() {
      Map<String, Object> outerParams = new HashMap<>();
      outerParams.put("event_type", "unknown.event.type");
      outerParams.put("timestamp", 999.0);
      outerParams.put("params", Map.of("foo", "bar"));

      RelayEvent event = RelayEvent.fromRawParams(outerParams);
      assertEquals(RelayEvent.class, event.getClass());
      assertEquals("unknown.event.type", event.getEventType());
    }

    @Test
    @DisplayName("All 20+ event subclasses are created for known types")
    void allEventSubclasses() {
      String[] knownTypes = {
        Constants.EVENT_CALL_STATE, Constants.EVENT_CALL_RECEIVE,
        Constants.EVENT_CALL_DIAL, Constants.EVENT_CALL_PLAY,
        Constants.EVENT_CALL_RECORD, Constants.EVENT_CALL_DETECT,
        Constants.EVENT_CALL_COLLECT, Constants.EVENT_CALL_FAX,
        Constants.EVENT_CALL_TAP, Constants.EVENT_CALL_STREAM,
        Constants.EVENT_CALL_TRANSCRIBE, Constants.EVENT_CALL_CONNECT,
        Constants.EVENT_CALL_REFER, Constants.EVENT_CALL_SEND_DIGITS,
        Constants.EVENT_CALL_PAY, Constants.EVENT_CONFERENCE,
        Constants.EVENT_QUEUE, Constants.EVENT_AUTHORIZATION_STATE,
        Constants.EVENT_MESSAGING_RECEIVE, Constants.EVENT_MESSAGING_STATE
      };

      for (String type : knownTypes) {
        Map<String, Object> outerParams = new HashMap<>();
        outerParams.put("event_type", type);
        outerParams.put("timestamp", 0.0);
        outerParams.put("params", new HashMap<>());

        RelayEvent event = RelayEvent.fromRawParams(outerParams);
        assertNotEquals(
            RelayEvent.class, event.getClass(), "Expected subclass for event type: " + type);
      }
    }

    @Test
    @DisplayName("Conference event has conferenceId")
    void conferenceEvent() {
      Map<String, Object> innerParams = new HashMap<>();
      innerParams.put("conference_id", "conf-123");
      innerParams.put("call_id", "call-456");

      Map<String, Object> outerParams = new HashMap<>();
      outerParams.put("event_type", Constants.EVENT_CONFERENCE);
      outerParams.put("timestamp", 0.0);
      outerParams.put("params", innerParams);

      RelayEvent event = RelayEvent.fromRawParams(outerParams);
      assertInstanceOf(RelayEvent.ConferenceEvent.class, event);
      assertEquals("conf-123", ((RelayEvent.ConferenceEvent) event).getConferenceId());
    }

    @Test
    @DisplayName("Event with null params uses empty map")
    void nullParams() {
      RelayEvent event = new RelayEvent("test", 0.0, null);
      assertNotNull(event.getParams());
      assertTrue(event.getParams().isEmpty());
    }
  }

  // ── Actions ──────────────────────────────────────────────────────

  @Nested
  @DisplayName("Actions")
  class ActionTests {

    @Test
    @DisplayName("Action tracks control_id and state")
    void actionBasics() {
      Call call = new Call("call-1", "node-1");
      Action action = new Action("ctrl-1", call);

      assertEquals("ctrl-1", action.getControlId());
      assertSame(call, action.getCall());
      assertFalse(action.isDone());
      assertNull(action.getResult());
      assertNull(action.getState());
    }

    @Test
    @DisplayName("Action resolves on terminal state")
    void actionTerminalState() {
      Call call = new Call("call-1", "node-1");
      Action action = new Action("ctrl-1", call);

      RelayEvent event = new RelayEvent("test", 0.0, Map.of("state", "finished"));
      action.updateState("finished", event);

      assertTrue(action.isDone());
      assertEquals("finished", action.getState());
      assertSame(event, action.getResult());
    }

    @Test
    @DisplayName("Action does not resolve on non-terminal state")
    void actionNonTerminal() {
      Call call = new Call("call-1", "node-1");
      Action action = new Action("ctrl-1", call);

      action.updateState("playing", new RelayEvent("test", 0.0, Map.of()));
      assertFalse(action.isDone());
      assertEquals("playing", action.getState());
    }

    @Test
    @DisplayName("Action waitForCompletion returns when resolved")
    void actionWait() throws Exception {
      Call call = new Call("call-1", "node-1");
      Action action = new Action("ctrl-1", call);

      // Resolve asynchronously
      RelayEvent event = new RelayEvent("test", 0.0, Map.of());
      var unused =
          CompletableFuture.runAsync(
              () -> {
                try {
                  Thread.sleep(50);
                } catch (InterruptedException ignored) {
                }
                action.resolve(event);
              });

      RelayEvent result = action.waitForCompletion(5000);
      assertSame(event, result);
      assertTrue(action.isDone());
    }

    @Test
    @DisplayName("Action onCompleted callback fires on resolve")
    void actionOnCompleted() {
      Call call = new Call("call-1", "node-1");
      Action action = new Action("ctrl-1", call);

      AtomicBoolean callbackFired = new AtomicBoolean(false);
      action.setOnCompleted(a -> callbackFired.set(true));

      action.resolve(new RelayEvent("test", 0.0, Map.of()));
      assertTrue(callbackFired.get());
    }

    @Test
    @DisplayName("Action onCompleted errors are caught and logged")
    void actionOnCompletedError() {
      Call call = new Call("call-1", "node-1");
      Action action = new Action("ctrl-1", call);

      action.setOnCompleted(
          a -> {
            throw new RuntimeException("callback error");
          });
      // Should not throw
      assertDoesNotThrow(() -> action.resolve(new RelayEvent("test", 0.0, Map.of())));
      assertTrue(action.isDone());
    }

    @Test
    @DisplayName("Action only resolves once")
    void actionResolvesOnce() {
      Call call = new Call("call-1", "node-1");
      Action action = new Action("ctrl-1", call);

      RelayEvent first = new RelayEvent("first", 0.0, Map.of());
      RelayEvent second = new RelayEvent("second", 0.0, Map.of());

      action.resolve(first);
      action.resolve(second); // Should be ignored

      assertSame(first, action.getResult());
    }

    @Test
    @DisplayName("PlayAction is a subclass with correct type")
    void playAction() {
      Call call = new Call("call-1", "node-1");
      Action.PlayAction action = new Action.PlayAction("ctrl-1", call);
      assertInstanceOf(Action.PlayAction.class, action);
      assertInstanceOf(Action.class, action);
    }

    @Test
    @DisplayName("RecordAction is a subclass")
    void recordAction() {
      Call call = new Call("call-1", "node-1");
      Action.RecordAction action = new Action.RecordAction("ctrl-1", call);
      assertInstanceOf(Action.RecordAction.class, action);
    }

    @Test
    @DisplayName("DetectAction is a subclass")
    void detectAction() {
      Call call = new Call("call-1", "node-1");
      Action.DetectAction action = new Action.DetectAction("ctrl-1", call);
      assertInstanceOf(Action.DetectAction.class, action);
    }

    @Test
    @DisplayName("CollectAction is a subclass")
    void collectAction() {
      Call call = new Call("call-1", "node-1");
      Action.CollectAction action = new Action.CollectAction("ctrl-1", call);
      assertInstanceOf(Action.CollectAction.class, action);
    }

    @Test
    @DisplayName("PlayAndCollectAction is a subclass")
    void playAndCollectAction() {
      Call call = new Call("call-1", "node-1");
      Action.PlayAndCollectAction action = new Action.PlayAndCollectAction("ctrl-1", call);
      assertInstanceOf(Action.PlayAndCollectAction.class, action);
    }

    @Test
    @DisplayName("PayAction is a subclass")
    void payAction() {
      Call call = new Call("call-1", "node-1");
      Action.PayAction action = new Action.PayAction("ctrl-1", call);
      assertInstanceOf(Action.PayAction.class, action);
    }

    @Test
    @DisplayName("SendFaxAction is a subclass")
    void sendFaxAction() {
      Call call = new Call("call-1", "node-1");
      Action.SendFaxAction action = new Action.SendFaxAction("ctrl-1", call);
      assertInstanceOf(Action.SendFaxAction.class, action);
    }

    @Test
    @DisplayName("ReceiveFaxAction is a subclass")
    void receiveFaxAction() {
      Call call = new Call("call-1", "node-1");
      Action.ReceiveFaxAction action = new Action.ReceiveFaxAction("ctrl-1", call);
      assertInstanceOf(Action.ReceiveFaxAction.class, action);
    }

    @Test
    @DisplayName("TapAction terminal state is only 'finished'")
    void tapActionTerminal() {
      Call call = new Call("call-1", "node-1");
      Action.TapAction action = new Action.TapAction("ctrl-1", call);

      action.updateState("error", new RelayEvent("test", 0.0, Map.of()));
      assertFalse(action.isDone()); // error is not terminal for TapAction

      action.updateState("finished", new RelayEvent("test", 0.0, Map.of()));
      assertTrue(action.isDone());
    }

    @Test
    @DisplayName("StreamAction terminal state is only 'finished'")
    void streamActionTerminal() {
      Call call = new Call("call-1", "node-1");
      Action.StreamAction action = new Action.StreamAction("ctrl-1", call);

      action.updateState("no_input", new RelayEvent("test", 0.0, Map.of()));
      assertFalse(action.isDone());

      action.updateState("finished", new RelayEvent("test", 0.0, Map.of()));
      assertTrue(action.isDone());
    }

    @Test
    @DisplayName("AiAction terminal states include finished and error")
    void aiActionTerminal() {
      Call call = new Call("call-1", "node-1");
      Action.AiAction action1 = new Action.AiAction("ctrl-1", call);
      action1.updateState("finished", new RelayEvent("test", 0.0, Map.of()));
      assertTrue(action1.isDone());

      Action.AiAction action2 = new Action.AiAction("ctrl-2", call);
      action2.updateState("error", new RelayEvent("test", 0.0, Map.of()));
      assertTrue(action2.isDone());
    }

    @Test
    @DisplayName("Action toString includes class name and controlId")
    void actionToString() {
      Call call = new Call("call-1", "node-1");
      Action action = new Action.PlayAction("ctrl-123", call);
      String str = action.toString();
      assertTrue(str.contains("PlayAction"));
      assertTrue(str.contains("ctrl-123"));
    }

    @Test
    @DisplayName("All 11 action subclasses exist")
    void allSubclasses() {
      Call call = new Call("c", "n");
      assertNotNull(new Action.PlayAction("1", call));
      assertNotNull(new Action.RecordAction("2", call));
      assertNotNull(new Action.DetectAction("3", call));
      assertNotNull(new Action.CollectAction("4", call));
      assertNotNull(new Action.PlayAndCollectAction("5", call));
      assertNotNull(new Action.PayAction("6", call));
      assertNotNull(new Action.SendFaxAction("7", call));
      assertNotNull(new Action.ReceiveFaxAction("8", call));
      assertNotNull(new Action.TapAction("9", call));
      assertNotNull(new Action.StreamAction("10", call));
      assertNotNull(new Action.TranscribeAction("11", call));
    }
  }

  // ── Call ─────────────────────────────────────────────────────────

  @Nested
  @DisplayName("Call")
  class CallTests {

    @Test
    @DisplayName("Call stores basic properties")
    void callBasics() {
      Call call = new Call("call-123", "node-456");
      assertEquals("call-123", call.getCallId());
      assertEquals("node-456", call.getNodeId().orElseThrow());
      assertEquals(Constants.CALL_STATE_CREATED, call.getState());
      assertFalse(call.isEnded());
    }

    @Test
    @DisplayName("Call setters work")
    void callSetters() {
      Call call = new Call("c", "n");
      call.setState("answered");
      call.setDirection("inbound");
      call.setTag("my-tag");
      call.setEndReason("hangup");
      call.setDevice(Map.of("type", "phone"));

      assertEquals("answered", call.getState());
      assertEquals("inbound", call.getDirection().orElseThrow());
      assertEquals("my-tag", call.getTag().orElseThrow());
      assertEquals("hangup", call.getEndReason().orElseThrow());
      assertEquals("phone", call.getDevice().get("type"));
    }

    @Test
    @DisplayName("Call dispatches state events and updates state")
    void callStateDispatch() {
      Call call = new Call("call-1", "node-1");

      Map<String, Object> params = new HashMap<>();
      params.put("call_id", "call-1");
      params.put("node_id", "node-1");
      params.put("call_state", "answered");

      RelayEvent.CallStateEvent event =
          new RelayEvent.CallStateEvent(Constants.EVENT_CALL_STATE, 0.0, params);
      call.dispatchEvent(event);

      assertEquals("answered", call.getState());
      assertFalse(call.isEnded());
    }

    @Test
    @DisplayName("Call dispatches ended state and marks as ended")
    void callEndedDispatch() {
      Call call = new Call("call-1", "node-1");

      Map<String, Object> params = new HashMap<>();
      params.put("call_id", "call-1");
      params.put("call_state", "ended");
      params.put("end_reason", "hangup");

      RelayEvent.CallStateEvent event =
          new RelayEvent.CallStateEvent(Constants.EVENT_CALL_STATE, 0.0, params);
      call.dispatchEvent(event);

      assertEquals("ended", call.getState());
      assertEquals("hangup", call.getEndReason().orElseThrow());
      assertTrue(call.isEnded());
    }

    @Test
    @DisplayName("Call dispatches action events by control_id")
    void callActionDispatch() {
      Call call = new Call("call-1", "node-1");
      Action.PlayAction action = new Action.PlayAction("ctrl-1", call);
      call.registerAction(action);

      Map<String, Object> params = new HashMap<>();
      params.put("call_id", "call-1");
      params.put("control_id", "ctrl-1");
      params.put("state", "finished");

      RelayEvent.CallPlayEvent event =
          new RelayEvent.CallPlayEvent(Constants.EVENT_CALL_PLAY, 0.0, params);
      call.dispatchEvent(event);

      assertTrue(action.isDone());
      assertEquals("finished", action.getState());
    }

    @Test
    @DisplayName("Call ignores play events on PlayAndCollectAction")
    void playAndCollectIgnoresPlayEvents() {
      Call call = new Call("call-1", "node-1");
      Action.PlayAndCollectAction action = new Action.PlayAndCollectAction("ctrl-1", call);
      call.registerAction(action);

      // Play event should be ignored for completion
      Map<String, Object> playParams = new HashMap<>();
      playParams.put("call_id", "call-1");
      playParams.put("control_id", "ctrl-1");
      playParams.put("state", "finished");

      RelayEvent.CallPlayEvent playEvent =
          new RelayEvent.CallPlayEvent(Constants.EVENT_CALL_PLAY, 0.0, playParams);
      call.dispatchEvent(playEvent);

      assertFalse(action.isDone()); // Should NOT resolve on play finished

      // Collect event should resolve
      Map<String, Object> collectParams = new HashMap<>();
      collectParams.put("call_id", "call-1");
      collectParams.put("control_id", "ctrl-1");
      collectParams.put("state", "finished");
      collectParams.put("type", "digit");

      RelayEvent.CallCollectEvent collectEvent =
          new RelayEvent.CallCollectEvent(Constants.EVENT_CALL_COLLECT, 0.0, collectParams);
      call.dispatchEvent(collectEvent);

      assertTrue(action.isDone()); // Resolved on collect
    }

    @Test
    @DisplayName("Call resolveAllActions resolves all pending actions")
    void resolveAllActions() {
      Call call = new Call("call-1", "node-1");
      Action a1 = new Action.PlayAction("ctrl-1", call);
      Action a2 = new Action.RecordAction("ctrl-2", call);
      call.registerAction(a1);
      call.registerAction(a2);

      call.resolveAllActions(new RelayEvent("ended", 0.0, Map.of()));

      assertTrue(a1.isDone());
      assertTrue(a2.isDone());
    }

    @Test
    @DisplayName("Call event listeners are notified")
    void callEventListeners() {
      Call call = new Call("call-1", "node-1");
      AtomicReference<String> received = new AtomicReference<>();

      call.on(event -> received.set(event.getEventType()));

      Map<String, Object> params = new HashMap<>();
      params.put("call_id", "call-1");
      params.put("call_state", "answered");
      call.dispatchEvent(new RelayEvent.CallStateEvent(Constants.EVENT_CALL_STATE, 0.0, params));

      assertEquals(Constants.EVENT_CALL_STATE, received.get());
    }

    @Test
    @DisplayName("Call toString shows id, state, direction")
    void callToString() {
      Call call = new Call("call-1", "node-1");
      call.setDirection("inbound");
      String str = call.toString();
      assertTrue(str.contains("call-1"));
      assertTrue(str.contains("created"));
      assertTrue(str.contains("inbound"));
    }

    @Test
    @DisplayName("Call without client returns empty map on execute")
    void callNoClient() {
      Call call = new Call("call-1", "node-1");
      Map<String, Object> result = call.answer();
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Call getAction retrieves registered actions")
    void callGetAction() {
      Call call = new Call("call-1", "node-1");
      Action action = new Action.PlayAction("ctrl-1", call);
      call.registerAction(action);

      assertSame(action, call.getAction("ctrl-1"));
      assertNull(call.getAction("nonexistent"));
    }
  }

  // ── Message ──────────────────────────────────────────────────────

  @Nested
  @DisplayName("Message")
  class MessageTests {

    @Test
    @DisplayName("Message stores basic properties")
    void messageBasics() {
      Message msg = new Message("msg-123");
      assertEquals("msg-123", msg.getMessageId());
      assertFalse(msg.isDone());
      assertTrue(msg.getResult().isEmpty());
    }

    @Test
    @DisplayName("Message setters work")
    void messageSetters() {
      Message msg = new Message("msg-1");
      msg.setContext("ctx");
      msg.setDirection("outbound");
      msg.setFromNumber("+15551111111");
      msg.setToNumber("+15552222222");
      msg.setBody("Hello");
      msg.setMedia(List.of("https://example.com/image.jpg"));
      msg.setSegments(1);
      msg.setTags(List.of("vip"));

      assertEquals("ctx", msg.getContext().orElseThrow());
      assertEquals("outbound", msg.getDirection().orElseThrow());
      assertEquals("+15551111111", msg.getFromNumber().orElseThrow());
      assertEquals("+15552222222", msg.getToNumber().orElseThrow());
      assertEquals("Hello", msg.getBody().orElseThrow());
      assertEquals(1, msg.getMedia().size());
      assertEquals(1, msg.getSegments());
      assertEquals(List.of("vip"), msg.getTags());
    }

    @Test
    @DisplayName("Message resolves on terminal state")
    void messageTerminalState() {
      Message msg = new Message("msg-1");

      Map<String, Object> params = new HashMap<>();
      params.put("message_id", "msg-1");
      params.put("message_state", "delivered");
      params.put("direction", "outbound");

      msg.updateFromEvent(
          new RelayEvent.MessagingStateEvent(Constants.EVENT_MESSAGING_STATE, 0.0, params));

      assertTrue(msg.isDone());
      assertEquals("delivered", msg.getState());
    }

    @Test
    @DisplayName("Message onCompleted callback fires")
    void messageOnCompleted() {
      Message msg = new Message("msg-1");
      AtomicBoolean fired = new AtomicBoolean(false);
      msg.setOnCompleted(m -> fired.set(true));

      Map<String, Object> params = new HashMap<>();
      params.put("message_id", "msg-1");
      params.put("message_state", "failed");
      params.put("reason", "Invalid");

      msg.updateFromEvent(
          new RelayEvent.MessagingStateEvent(Constants.EVENT_MESSAGING_STATE, 0.0, params));

      assertTrue(fired.get());
      assertEquals("failed", msg.getState());
      assertEquals("Invalid", msg.getReason().orElseThrow());
    }

    @Test
    @DisplayName("Message state listeners are notified")
    void messageStateListeners() {
      Message msg = new Message("msg-1");
      AtomicReference<String> receivedState = new AtomicReference<>();

      msg.on(
          event -> {
            if (event instanceof RelayEvent.MessagingStateEvent) {
              receivedState.set(((RelayEvent.MessagingStateEvent) event).getMessageState());
            }
          });

      Map<String, Object> params = new HashMap<>();
      params.put("message_id", "msg-1");
      params.put("message_state", "sent");

      msg.updateFromEvent(
          new RelayEvent.MessagingStateEvent(Constants.EVENT_MESSAGING_STATE, 0.0, params));

      assertEquals("sent", receivedState.get());
      assertFalse(msg.isDone()); // sent is not terminal
    }

    @Test
    @DisplayName("Message fromReceiveEvent creates correct Message")
    void messageFromReceiveEvent() {
      Map<String, Object> params = new HashMap<>();
      params.put("message_id", "msg-recv");
      params.put("context", "default");
      params.put("direction", "inbound");
      params.put("from_number", "+15551234567");
      params.put("to_number", "+15559876543");
      params.put("body", "Hi there");
      params.put("media", List.of("https://example.com/img.png"));
      params.put("segments", 1);
      params.put("message_state", "received");
      params.put("tags", List.of("auto"));

      RelayEvent.MessagingReceiveEvent event =
          new RelayEvent.MessagingReceiveEvent(Constants.EVENT_MESSAGING_RECEIVE, 0.0, params);

      Message msg = Message.fromReceiveEvent(event);
      assertEquals("msg-recv", msg.getMessageId());
      assertEquals("default", msg.getContext().orElseThrow());
      assertEquals("inbound", msg.getDirection().orElseThrow());
      assertEquals("+15551234567", msg.getFromNumber().orElseThrow());
      assertEquals("+15559876543", msg.getToNumber().orElseThrow());
      assertEquals("Hi there", msg.getBody().orElseThrow());
      assertEquals(1, msg.getMedia().size());
      assertEquals("received", msg.getState());
    }

    @Test
    @DisplayName("Message setMedia with null uses empty list")
    void messageNullMedia() {
      Message msg = new Message("msg-1");
      msg.setMedia(null);
      assertNotNull(msg.getMedia());
      assertTrue(msg.getMedia().isEmpty());
    }

    @Test
    @DisplayName("Message setTags with null uses empty list")
    void messageNullTags() {
      Message msg = new Message("msg-1");
      msg.setTags(null);
      assertNotNull(msg.getTags());
      assertTrue(msg.getTags().isEmpty());
    }

    @Test
    @DisplayName("Message toString contains key info")
    void messageToString() {
      Message msg = new Message("msg-123");
      msg.setFromNumber("+15551111111");
      msg.setToNumber("+15552222222");
      String str = msg.toString();
      assertTrue(str.contains("msg-123"));
    }

    @Test
    @DisplayName("Message waitForCompletion with timeout returns null on timeout")
    void messageWaitTimeout() {
      Message msg = new Message("msg-1");
      RelayEvent result = msg.waitForCompletion(50);
      assertNull(result); // times out
    }
  }

  // ── Client creation ──────────────────────────────────────────────

  @Nested
  @DisplayName("RelayClient")
  class ClientTests {

    @Test
    @DisplayName("Builder creates client with required params")
    void builderCreates() {
      var client =
          RelayClient.builder()
              .project("proj-1")
              .token("tok-1")
              .space("example.signalwire.com")
              .contexts(List.of("default"))
              .build();

      assertEquals("proj-1", client.getProject());
      assertEquals("example.signalwire.com", client.getSpace());
      assertEquals(List.of("default"), client.getContexts());
      assertFalse(client.isConnected());
    }

    // Missing project/token (and no JWT, no env fallback) is an error — mirrors
    // Python's ValueError (relay/client.py). build() reads SIGNALWIRE_PROJECT_ID
    // / _API_TOKEN / _JWT_TOKEN as a fallback, so these throw only when the
    // credential is absent from BOTH the builder and the environment. Unit tests
    // run without SIGNALWIRE_* creds in the env.
    @Test
    @DisplayName("Builder fails without project")
    void builderNoProject() {
      assertThrows(
          IllegalArgumentException.class,
          () -> RelayClient.builder().token("tok").space("space").build());
    }

    @Test
    @DisplayName("Builder fails without token")
    void builderNoToken() {
      assertThrows(
          IllegalArgumentException.class,
          () -> RelayClient.builder().project("proj").space("space").build());
    }

    @Test
    @DisplayName("Builder defaults space to the RELAY host when unset")
    void builderNoSpace() {
      // Python parity: host defaults to DEFAULT_RELAY_HOST (relay.signalwire.com)
      // when neither the space arg nor SIGNALWIRE_SPACE is set — it is never an
      // error on its own.
      var client = RelayClient.builder().project("proj").token("tok").build();
      assertEquals("relay.signalwire.com", client.getSpace());
    }

    @Test
    @DisplayName("Contexts list is unmodifiable from getter")
    void contextsUnmodifiable() {
      var client =
          RelayClient.builder()
              .project("p")
              .token("t")
              .space("s")
              .contexts(List.of("a", "b"))
              .build();

      assertThrows(UnsupportedOperationException.class, () -> client.getContexts().add("c"));
    }

    @Test
    @DisplayName("Contexts can be null in builder")
    void contextsNull() {
      var client = RelayClient.builder().project("p").token("t").space("s").build();

      assertNotNull(client.getContexts());
      assertTrue(client.getContexts().isEmpty());
    }

    @Test
    @DisplayName("Internal maps are initialized empty")
    void internalMaps() {
      var client = RelayClient.builder().project("p").token("t").space("s").build();

      assertTrue(client.getCalls().isEmpty());
      assertTrue(client.getPendingDials().isEmpty());
      assertTrue(client.getPendingRequests().isEmpty());
      assertTrue(client.getMessages().isEmpty());
    }

    @Test
    @DisplayName("maxActiveCalls defaults to 1000, builder override clamps to >= 1")
    void maxActiveCalls() {
      var deflt = RelayClient.builder().project("p").token("t").space("s").build();
      assertEquals(1000, deflt.getMaxActiveCalls());

      var custom =
          RelayClient.builder().project("p").token("t").space("s").maxActiveCalls(5).build();
      assertEquals(5, custom.getMaxActiveCalls());

      var clamped =
          RelayClient.builder().project("p").token("t").space("s").maxActiveCalls(0).build();
      assertEquals(1, clamped.getMaxActiveCalls());
    }
  }
}
