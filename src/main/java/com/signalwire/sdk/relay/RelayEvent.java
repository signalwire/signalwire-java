/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import java.util.*;

/**
 * Base class for all RELAY events.
 *
 * <p>Events arrive as {@code signalwire.event} messages with nested params:
 *
 * <pre>
 * {
 *   "params": {
 *     "event_type": "calling.call.play",
 *     "timestamp": 123457.1234,
 *     "params": { "call_id": "...", "control_id": "...", "state": "finished" }
 *   }
 * }
 * </pre>
 *
 * Subclasses provide typed access to specific event payloads.
 */
public class RelayEvent {

  private final String eventType;
  private final double timestamp;
  private final Map<String, Object> params;

  public RelayEvent(String eventType, double timestamp, Map<String, Object> params) {
    this.eventType = eventType;
    this.timestamp = timestamp;
    this.params = params != null ? params : Collections.emptyMap();
  }

  public String getEventType() {
    return eventType;
  }

  public double getTimestamp() {
    return timestamp;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  public String getStringParam(String key) {
    Object val = params.get(key);
    return val != null ? val.toString() : null;
  }

  public String getStringParam(String key, String defaultValue) {
    String val = getStringParam(key);
    return val != null ? val : defaultValue;
  }

  /** Create the appropriate event subclass from raw JSON-RPC event params. */
  public static RelayEvent fromRawParams(Map<String, Object> outerParams) {
    String eventType = getStr(outerParams, "event_type", "");
    double timestamp = getDouble(outerParams, "timestamp", 0.0);
    @SuppressWarnings("unchecked")
    Map<String, Object> innerParams =
        (Map<String, Object>) outerParams.getOrDefault("params", Collections.emptyMap());

    if (eventType.equals(Constants.EVENT_CALL_STATE)) {
      return new CallStateEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_RECEIVE)) {
      return new CallReceiveEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_DIAL)) {
      return new CallDialEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_PLAY)) {
      return new CallPlayEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_RECORD)) {
      return new CallRecordEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_DETECT)) {
      return new CallDetectEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_COLLECT)) {
      return new CallCollectEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_FAX)) {
      return new CallFaxEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_TAP)) {
      return new CallTapEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_STREAM)) {
      return new CallStreamEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_TRANSCRIBE)) {
      return new CallTranscribeEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_CONNECT)) {
      return new CallConnectEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_REFER)) {
      return new CallReferEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_SEND_DIGITS)) {
      return new CallSendDigitsEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_PAY)) {
      return new CallPayEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CONFERENCE)) {
      return new ConferenceEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_QUEUE)) {
      return new QueueEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_AUTHORIZATION_STATE)) {
      return new AuthorizationStateEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_MESSAGING_RECEIVE)) {
      return new MessagingReceiveEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_MESSAGING_STATE)) {
      return new MessagingStateEvent(eventType, timestamp, innerParams);
    }

    return new RelayEvent(eventType, timestamp, innerParams);
  }

  @Override
  public String toString() {
    return String.format("RelayEvent{type=%s}", eventType);
  }

  // ── Helper methods ───────────────────────────────────────────────

  static String getStr(Map<String, Object> map, String key, String defaultVal) {
    Object val = map.get(key);
    return val != null ? val.toString() : defaultVal;
  }

  static double getDouble(Map<String, Object> map, String key, double defaultVal) {
    Object val = map.get(key);
    if (val instanceof Number) {
      return ((Number) val).doubleValue();
    }
    return defaultVal;
  }

  static int getInt(Map<String, Object> map, String key, int defaultVal) {
    Object val = map.get(key);
    if (val instanceof Number) {
      return ((Number) val).intValue();
    }
    return defaultVal;
  }

  @SuppressWarnings("unchecked")
  static List<String> getStringList(Map<String, Object> map, String key) {
    Object val = map.get(key);
    if (val instanceof List) {
      List<String> result = new ArrayList<>();
      for (Object item : (List<?>) val) {
        result.add(item != null ? item.toString() : null);
      }
      return result;
    }
    return Collections.emptyList();
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> getMap(Map<String, Object> map, String key) {
    Object val = map.get(key);
    if (val instanceof Map) {
      return (Map<String, Object>) val;
    }
    return Collections.emptyMap();
  }

  // ── Event subclasses ─────────────────────────────────────────────

  /** Call state change event ({@code calling.call.state}). */
  public static class CallStateEvent extends RelayEvent {
    public CallStateEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getNodeId() {
      return getStringParam("node_id");
    }

    public String getCallState() {
      return getStringParam("call_state");
    }

    public String getEndReason() {
      return getStringParam("end_reason");
    }

    public String getTag() {
      return getStringParam("tag");
    }

    public String getDirection() {
      return getStringParam("direction");
    }

    public Map<String, Object> getDevice() {
      return getMap(getParams(), "device");
    }
  }

  /** Inbound call event ({@code calling.call.receive}). */
  public static class CallReceiveEvent extends RelayEvent {
    public CallReceiveEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getNodeId() {
      return getStringParam("node_id");
    }

    public String getCallState() {
      return getStringParam("call_state");
    }

    public String getContext() {
      return getStringParam("context");
    }

    public Map<String, Object> getDevice() {
      return getMap(getParams(), "device");
    }
  }

  /**
   * Dial completion event ({@code calling.call.dial}).
   *
   * <p>Note: No top-level {@code call_id}. The call info is nested at {@code params.call}.
   */
  public static class CallDialEvent extends RelayEvent {
    public CallDialEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getTag() {
      return getStringParam("tag");
    }

    public String getNodeId() {
      return getStringParam("node_id");
    }

    public String getDialState() {
      return getStringParam("dial_state");
    }

    /**
     * The dial outcome as a typed {@link DialState}, <em>alongside</em> the raw string {@link
     * #getDialState()} (which stays canonical for parity and forward-compat). Returns {@code
     * Optional.empty()} when the wire {@code dial_state} is not one of the three known {@link
     * DialState} values — the set mirrors server-emitted values that can grow, so an unrecognised
     * state is tolerated here rather than crashing dispatch.
     *
     * @return the typed dial state, or empty if unknown/unset.
     */
    public Optional<DialState> getDialStateEnum() {
      return Optional.ofNullable(DialState.fromWire(getDialState()));
    }

    public Map<String, Object> getCallInfo() {
      return getMap(getParams(), "call");
    }

    public String getCallId() {
      return getStr(getCallInfo(), "call_id", null);
    }
  }

  /** Play event ({@code calling.call.play}). */
  public static class CallPlayEvent extends RelayEvent {
    public CallPlayEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getControlId() {
      return getStringParam("control_id");
    }

    public String getState() {
      return getStringParam("state");
    }
  }

  /** Record event ({@code calling.call.record}). */
  public static class CallRecordEvent extends RelayEvent {
    public CallRecordEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getControlId() {
      return getStringParam("control_id");
    }

    public String getState() {
      return getStringParam("state");
    }

    public String getUrl() {
      String url = getStringParam("url");
      if (url != null) return url;
      // Record data may be nested
      Map<String, Object> record = getMap(getParams(), "record");
      return getStr(record, "url", null);
    }

    public double getDuration() {
      Object d = getParams().get("duration");
      if (d instanceof Number) return ((Number) d).doubleValue();
      Map<String, Object> record = getMap(getParams(), "record");
      return getDouble(record, "duration", 0.0);
    }

    public long getSize() {
      Object s = getParams().get("size");
      if (s instanceof Number) return ((Number) s).longValue();
      Map<String, Object> record = getMap(getParams(), "record");
      Object rs = record.get("size");
      return rs instanceof Number ? ((Number) rs).longValue() : 0L;
    }
  }

  /** Detect event ({@code calling.call.detect}). */
  public static class CallDetectEvent extends RelayEvent {
    public CallDetectEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getControlId() {
      return getStringParam("control_id");
    }

    /** Results are in detect.params.event, not a simple state field. */
    public Map<String, Object> getDetect() {
      return getMap(getParams(), "detect");
    }

    public String getDetectEvent() {
      Map<String, Object> detect = getDetect();
      Map<String, Object> detectParams = getMap(detect, "params");
      return getStr(detectParams, "event", null);
    }
  }

  /** Collect event ({@code calling.call.collect}). */
  public static class CallCollectEvent extends RelayEvent {
    public CallCollectEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getControlId() {
      return getStringParam("control_id");
    }

    public String getResultType() {
      return getStringParam("type");
    }

    public Map<String, Object> getResult() {
      return getMap(getParams(), "params");
    }
  }

  /** Fax event ({@code calling.call.fax}). */
  public static class CallFaxEvent extends RelayEvent {
    public CallFaxEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getControlId() {
      return getStringParam("control_id");
    }

    public String getState() {
      return getStringParam("state");
    }
  }

  /** Tap event ({@code calling.call.tap}). */
  public static class CallTapEvent extends RelayEvent {
    public CallTapEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getControlId() {
      return getStringParam("control_id");
    }

    public String getState() {
      return getStringParam("state");
    }
  }

  /** Stream event ({@code calling.call.stream}). */
  public static class CallStreamEvent extends RelayEvent {
    public CallStreamEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getControlId() {
      return getStringParam("control_id");
    }

    public String getState() {
      return getStringParam("state");
    }
  }

  /** Transcribe event ({@code calling.call.transcribe}). */
  public static class CallTranscribeEvent extends RelayEvent {
    public CallTranscribeEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getControlId() {
      return getStringParam("control_id");
    }

    public String getState() {
      return getStringParam("state");
    }
  }

  /** Connect event ({@code calling.call.connect}). */
  public static class CallConnectEvent extends RelayEvent {
    public CallConnectEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getConnectState() {
      return getStringParam("connect_state");
    }
  }

  /** Refer event ({@code calling.call.refer}). */
  public static class CallReferEvent extends RelayEvent {
    public CallReferEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getReferState() {
      return getStringParam("refer_state");
    }
  }

  /** Send digits event ({@code calling.call.send_digits}). */
  public static class CallSendDigitsEvent extends RelayEvent {
    public CallSendDigitsEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getState() {
      return getStringParam("state");
    }
  }

  /** Pay event ({@code calling.call.pay}). */
  public static class CallPayEvent extends RelayEvent {
    public CallPayEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getControlId() {
      return getStringParam("control_id");
    }

    public String getState() {
      return getStringParam("state");
    }
  }

  /** Conference event ({@code calling.conference}). */
  public static class ConferenceEvent extends RelayEvent {
    public ConferenceEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getConferenceId() {
      return getStringParam("conference_id");
    }

    public String getCallId() {
      return getStringParam("call_id");
    }
  }

  /** Queue event ({@code calling.queue}). */
  public static class QueueEvent extends RelayEvent {
    public QueueEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getCallId() {
      return getStringParam("call_id");
    }

    public String getQueueId() {
      return getStringParam("queue_id");
    }
  }

  /** Authorization state event ({@code signalwire.authorization.state}). */
  public static class AuthorizationStateEvent extends RelayEvent {
    public AuthorizationStateEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getAuthorizationState() {
      return getStringParam("authorization_state");
    }
  }

  /** Inbound messaging event ({@code messaging.receive}). */
  public static class MessagingReceiveEvent extends RelayEvent {
    public MessagingReceiveEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getMessageId() {
      return getStringParam("message_id");
    }

    public String getContext() {
      return getStringParam("context");
    }

    public String getDirection() {
      return getStringParam("direction");
    }

    public String getFromNumber() {
      return getStringParam("from_number");
    }

    public String getToNumber() {
      return getStringParam("to_number");
    }

    public String getBody() {
      return getStringParam("body");
    }

    public List<String> getMedia() {
      return getStringList(getParams(), "media");
    }

    public int getSegments() {
      return getInt(getParams(), "segments", 1);
    }

    public String getMessageState() {
      return getStringParam("message_state");
    }

    public List<String> getTags() {
      return getStringList(getParams(), "tags");
    }
  }

  /** Outbound messaging state event ({@code messaging.state}). */
  public static class MessagingStateEvent extends RelayEvent {
    public MessagingStateEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    public String getMessageId() {
      return getStringParam("message_id");
    }

    public String getContext() {
      return getStringParam("context");
    }

    public String getDirection() {
      return getStringParam("direction");
    }

    public String getFromNumber() {
      return getStringParam("from_number");
    }

    public String getToNumber() {
      return getStringParam("to_number");
    }

    public String getBody() {
      return getStringParam("body");
    }

    public List<String> getMedia() {
      return getStringList(getParams(), "media");
    }

    public int getSegments() {
      return getInt(getParams(), "segments", 1);
    }

    public String getMessageState() {
      return getStringParam("message_state");
    }

    public String getReason() {
      return getStringParam("reason");
    }

    public List<String> getTags() {
      return getStringList(getParams(), "tags");
    }
  }
}
