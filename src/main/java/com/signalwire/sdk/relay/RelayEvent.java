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

  /**
   * The {@code event_type} discriminator from the outer {@code signalwire.event} envelope (for
   * example {@code calling.call.play}) — the value {@link #fromRawParams(Map)} dispatches on to
   * choose the typed subclass.
   *
   * @return the wire event type, or the empty string when the payload omitted it.
   */
  public String getEventType() {
    return eventType;
  }

  /**
   * Server-assigned event time as a Unix epoch value with fractional seconds, taken from the
   * envelope's {@code timestamp}.
   *
   * @return the epoch timestamp, or {@code 0.0} when the payload carried no numeric timestamp.
   */
  public double getTimestamp() {
    return timestamp;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  /** Top-level {@code call_id} for the event, when present in {@code params}. */
  public String getCallId() {
    return getStringParam("call_id");
  }

  /**
   * Read an inner-{@code params} value coerced to its string form.
   *
   * @param key the wire key inside the event's inner {@code params} object.
   * @return the value's {@code toString()}, or {@code null} when the key is absent.
   */
  public String getStringParam(String key) {
    Object val = params.get(key);
    return val != null ? val.toString() : null;
  }

  /**
   * Read an inner-{@code params} value coerced to its string form, substituting a caller-supplied
   * value when the key is absent.
   *
   * @param key the wire key inside the event's inner {@code params} object.
   * @param defaultValue returned when the key is absent (a present-but-null value also yields it).
   * @return the value's string form, or {@code defaultValue}.
   */
  public String getStringParam(String key, String defaultValue) {
    String val = getStringParam(key);
    return val != null ? val : defaultValue;
  }

  /** Read a numeric param as {@code double} (0.0 when absent/non-numeric). */
  public double getDoubleParam(String key) {
    return getDouble(params, key, 0.0);
  }

  /** Read a numeric param as {@code long} (0 when absent/non-numeric). */
  public long getLongParam(String key) {
    return getInt(params, key, 0);
  }

  /**
   * Create the appropriate typed event subclass from a raw {@code signalwire.event} payload. An
   * alias of {@link #fromRawParams(Map)}; both take the OUTER payload ({@code event_type} / {@code
   * timestamp} / {@code params}), not the inner {@code params} map alone.
   */
  public static RelayEvent fromPayload(Map<String, Object> payload) {
    return fromRawParams(payload);
  }

  /**
   * Parse a raw {@code signalwire.event} payload into a typed event object: dispatches by {@code
   * event_type} to the matching subclass, falling back to a plain {@link RelayEvent} for an
   * unrecognised type. An alias of {@link #fromRawParams(Map)}.
   */
  public static RelayEvent parseEvent(Map<String, Object> payload) {
    return fromRawParams(payload);
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
    } else if (eventType.equals(Constants.EVENT_CALL_DENOISE)) {
      return new DenoiseEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_ECHO)) {
      return new EchoEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALL_HOLD)) {
      return new HoldEvent(eventType, timestamp, innerParams);
    } else if (eventType.equals(Constants.EVENT_CALLING_ERROR)) {
      return new CallingErrorEvent(eventType, timestamp, innerParams);
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

  /**
   * A short diagnostic rendering carrying only the event type — deliberately excludes {@code
   * params}, which routinely holds call/message identifiers and inbound message bodies that must
   * not leak into logs.
   *
   * @return {@code RelayEvent{type=<event_type>}}.
   */
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
    return new ArrayList<>();
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> getMap(Map<String, Object> map, String key) {
    Object val = map.get(key);
    if (val instanceof Map) {
      return (Map<String, Object>) val;
    }
    return Collections.emptyMap();
  }

  /** Extract {@code event_type} from an outer payload (per-subclass {@code fromPayload} helper). */
  static String payloadEventType(Map<String, Object> payload) {
    return getStr(payload, "event_type", "");
  }

  /**
   * Extract {@code timestamp} from an outer payload: the inner {@code params} copy wins, falling
   * back to the outer one, then to {@code 0.0}.
   */
  @SuppressWarnings("unchecked")
  static double payloadTimestamp(Map<String, Object> payload) {
    Map<String, Object> inner =
        (Map<String, Object>) payload.getOrDefault("params", Collections.emptyMap());
    return getDouble(inner, "timestamp", getDouble(payload, "timestamp", 0.0));
  }

  /** Extract the inner {@code params} map from an outer payload. */
  @SuppressWarnings("unchecked")
  static Map<String, Object> payloadParams(Map<String, Object> payload) {
    return (Map<String, Object>) payload.getOrDefault("params", Collections.emptyMap());
  }

  // ── Event subclasses ─────────────────────────────────────────────

  /** Call state change event ({@code calling.call.state}). */
  public static class CallStateEvent extends RelayEvent {
    public CallStateEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link CallStateEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallStateEvent fromPayload(Map<String, Object> payload) {
      return new CallStateEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /**
     * UUID of the call leg whose state changed. Every {@code calling.*} method echoes this back, so
     * it is the key events are routed on to the owning {@code Call}.
     */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /**
     * UUID of the RELAY node currently hosting this call leg. Must be sent back on every subsequent
     * calling method for the leg; it can change across the call's lifetime.
     */
    public String getNodeId() {
      return getStringParam("node_id");
    }

    /**
     * Lifecycle state of the call leg: {@code created}, {@code ringing}, {@code answered} or {@code
     * ended}. See {@link CallState} for the typed form.
     */
    public String getCallState() {
      return getStringParam("call_state");
    }

    /**
     * Why the leg terminated (for example {@code hangup}, {@code busy}, {@code noAnswer}, {@code
     * error}). Only populated once {@link #getCallState()} reaches {@code ended}.
     */
    public String getEndReason() {
      return getStringParam("end_reason");
    }

    /**
     * Client-supplied correlation tag echoed from the originating {@code calling.dial}. Because the
     * dial RPC response carries no {@code call_id}, this is how an outbound leg is matched to the
     * request that created it.
     */
    public String getTag() {
      return getStringParam("tag");
    }

    /**
     * Whether this leg was placed by the client ({@code outbound}) or arrived from the network
     * ({@code inbound}).
     */
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

    /**
     * Build a {@link CallReceiveEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallReceiveEvent fromPayload(Map<String, Object> payload) {
      return new CallReceiveEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /**
     * UUID the platform assigned the inbound leg. Use it for every subsequent calling method — the
     * call does not exist to the client under any other identifier.
     */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /** UUID of the RELAY node hosting the inbound leg; required on every calling method for it. */
    public String getNodeId() {
      return getStringParam("node_id");
    }

    /**
     * Lifecycle state at the moment the leg was offered — normally {@code created} for an
     * unanswered inbound call.
     */
    public String getCallState() {
      return getStringParam("call_state");
    }

    /**
     * The subscribed context this call was delivered on. A client only receives inbound calls for
     * contexts it explicitly subscribed to at connect time.
     */
    public String getContext() {
      return getStringParam("context");
    }

    public Map<String, Object> getDevice() {
      return getMap(getParams(), "device");
    }

    /**
     * Always {@code inbound} for a received call; present for symmetry with {@link CallStateEvent}.
     */
    public String getDirection() {
      return getStringParam("direction");
    }

    /** UUID of the SignalWire project the inbound call was billed and routed to. */
    public String getProjectId() {
      return getStringParam("project_id");
    }

    /**
     * Platform identifier for this billing/routing segment of the call, distinct from the {@code
     * call_id} of the leg.
     */
    public String getSegmentId() {
      return getStringParam("segment_id");
    }

    /** Correlation tag carried on the inbound leg, when the originator supplied one. */
    public String getTag() {
      return getStringParam("tag");
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

    /**
     * Build a {@link CallDialEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallDialEvent fromPayload(Map<String, Object> payload) {
      return new CallDialEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /**
     * The client-generated tag passed to {@code calling.dial}. This event carries no top-level
     * {@code call_id}, so the tag is the ONLY way to match the outcome to the dial request that
     * produced it.
     */
    public String getTag() {
      return getStringParam("tag");
    }

    /** UUID of the RELAY node hosting the winning leg, needed for subsequent calling methods. */
    public String getNodeId() {
      return getStringParam("node_id");
    }

    /**
     * Raw dial outcome as sent on the wire: {@code dialing} (still in progress), {@code answered}
     * (a leg was answered) or {@code failed} (every leg failed). Kept as a string so a server-side
     * addition to the set does not break dispatch; {@link #getDialStateEnum()} gives the typed
     * view.
     */
    public String getDialState() {
      return getStringParam("dial_state");
    }

    /**
     * The dial outcome as a typed {@link DialState}, <em>alongside</em> the raw string {@link
     * #getDialState()} (which stays canonical and forward-compatible). Returns {@code
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

    public Map<String, Object> getCall() {
      return getMap(getParams(), "call");
    }

    /**
     * UUID of the answered leg, read from the nested {@code params.call} object rather than the top
     * level — this event has no top-level {@code call_id}. With parallel dialing only the winning
     * leg appears here.
     *
     * @return the winning leg's call id, or {@code null} while no leg has answered.
     */
    @Override
    public String getCallId() {
      return getStr(getCallInfo(), "call_id", null);
    }
  }

  /** Play event ({@code calling.call.play}). */
  public static class CallPlayEvent extends RelayEvent {
    public CallPlayEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link CallPlayEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallPlayEvent fromPayload(Map<String, Object> payload) {
      return new CallPlayEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call the playback is running on. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /**
     * Client-generated identifier for this specific playback, echoed by the server. Multiple
     * actions can run concurrently on one call, so this is what disambiguates their events.
     */
    public String getControlId() {
      return getStringParam("control_id");
    }

    /**
     * Playback state: {@code playing}, {@code paused}, {@code error} or {@code finished}. Note that
     * a {@code finished} play on a {@code play_and_collect} shares its control id with the collect
     * phase and does NOT mean input was collected.
     */
    public String getState() {
      return getStringParam("state");
    }
  }

  /** Record event ({@code calling.call.record}). */
  public static class CallRecordEvent extends RelayEvent {
    public CallRecordEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link CallRecordEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallRecordEvent fromPayload(Map<String, Object> payload) {
      return new CallRecordEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call being recorded. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /**
     * Client-generated identifier for this recording, echoed by the server so concurrent actions on
     * the same call can be told apart.
     */
    public String getControlId() {
      return getStringParam("control_id");
    }

    /** Recording state: {@code recording}, {@code no_input}, {@code finished} or an error state. */
    public String getState() {
      return getStringParam("state");
    }

    /**
     * Where the finished recording can be fetched. Accepts the field at the top level or nested
     * under {@code record}, since the platform reports it in both shapes depending on event stage.
     *
     * @return the recording URL, or {@code null} before the recording completes.
     */
    public String getUrl() {
      String url = getStringParam("url");
      if (url != null) return url;
      // Record data may be nested
      Map<String, Object> record = getMap(getParams(), "record");
      return getStr(record, "url", null);
    }

    /**
     * Recording length in seconds, read from the top level or from the nested {@code record}
     * object.
     *
     * @return the duration, or {@code 0.0} when not yet reported.
     */
    public double getDuration() {
      Object d = getParams().get("duration");
      if (d instanceof Number) return ((Number) d).doubleValue();
      Map<String, Object> record = getMap(getParams(), "record");
      return getDouble(record, "duration", 0.0);
    }

    /**
     * Recorded media size in bytes, read from the top level or from the nested {@code record}
     * object.
     *
     * @return the byte size, or {@code 0} when not yet reported.
     */
    public long getSize() {
      Object s = getParams().get("size");
      if (s instanceof Number) return ((Number) s).longValue();
      Map<String, Object> record = getMap(getParams(), "record");
      Object rs = record.get("size");
      return rs instanceof Number ? ((Number) rs).longValue() : 0L;
    }

    public Map<String, Object> getRecord() {
      return getMap(getParams(), "record");
    }
  }

  /** Detect event ({@code calling.call.detect}). */
  public static class CallDetectEvent extends RelayEvent {
    public CallDetectEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link CallDetectEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallDetectEvent fromPayload(Map<String, Object> payload) {
      return new CallDetectEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call detection is running on. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /** Client-generated identifier for this detect action, echoed by the server. */
    public String getControlId() {
      return getStringParam("control_id");
    }

    /** Results are in detect.params.event, not a simple state field. */
    public Map<String, Object> getDetect() {
      return getMap(getParams(), "detect");
    }

    /**
     * The detection outcome, dug out of {@code detect.params.event} — for example {@code machine},
     * {@code human}, {@code fax} or {@code finished}. Detect reports its result here rather than in
     * a flat {@code state} field like the other actions.
     *
     * @return the detected event name, or {@code null} when the event carries no result yet.
     */
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

    /**
     * Build a {@link CallCollectEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallCollectEvent fromPayload(Map<String, Object> payload) {
      return new CallCollectEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call input is being collected on. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /**
     * Client-generated identifier for this collect action. A {@code play_and_collect} shares one
     * control id across both phases, so also filter on the event type before resolving.
     */
    public String getControlId() {
      return getStringParam("control_id");
    }

    /** Collect state, e.g. {@code collecting}, {@code finished} or {@code error}. */
    public String getState() {
      return getStringParam("state");
    }

    /**
     * Kind of input collected — {@code digit}, {@code speech}, or a no-input/error outcome. Reads
     * the wire key {@code type}; renamed here because {@code getType()} would not say what it is
     * the type OF.
     */
    public String getResultType() {
      return getStringParam("type");
    }

    /**
     * The collect result object ({@code {type, params}}) — the nested {@code result} value, NOT the
     * top-level {@code params}. Empty when the wire omitted {@code result}.
     */
    public Map<String, Object> getResult() {
      return getMap(getParams(), "result");
    }

    /**
     * Whether this is the final collect result. {@code null} when the wire omitted {@code final}.
     */
    public Boolean getFinal() {
      Object v = getParams().get("final");
      return v instanceof Boolean ? (Boolean) v : null;
    }
  }

  /** Fax event ({@code calling.call.fax}). */
  public static class CallFaxEvent extends RelayEvent {
    public CallFaxEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link CallFaxEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallFaxEvent fromPayload(Map<String, Object> payload) {
      return new CallFaxEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call carrying the fax. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /** Client-generated identifier for this fax action, echoed by the server. */
    public String getControlId() {
      return getStringParam("control_id");
    }

    /** Fax state, e.g. {@code page}, {@code error} or {@code finished}. */
    public String getState() {
      return getStringParam("state");
    }

    public Map<String, Object> getFax() {
      return getMap(getParams(), "fax");
    }
  }

  /** Tap event ({@code calling.call.tap}). */
  public static class CallTapEvent extends RelayEvent {
    public CallTapEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link CallTapEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallTapEvent fromPayload(Map<String, Object> payload) {
      return new CallTapEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call whose media is being tapped. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /** Client-generated identifier for this tap, echoed by the server. */
    public String getControlId() {
      return getStringParam("control_id");
    }

    /**
     * Tap state: {@code tapping} while media is being forwarded, {@code finished} once it stops.
     */
    public String getState() {
      return getStringParam("state");
    }

    public Map<String, Object> getDevice() {
      return getMap(getParams(), "device");
    }

    public Map<String, Object> getTap() {
      return getMap(getParams(), "tap");
    }
  }

  /** Stream event ({@code calling.call.stream}). */
  public static class CallStreamEvent extends RelayEvent {
    public CallStreamEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link CallStreamEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallStreamEvent fromPayload(Map<String, Object> payload) {
      return new CallStreamEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call whose media is being streamed. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /** Client-generated identifier for this stream, echoed by the server. */
    public String getControlId() {
      return getStringParam("control_id");
    }

    /** Stream state: {@code streaming} while media is flowing, {@code finished} once it stops. */
    public String getState() {
      return getStringParam("state");
    }

    /** Name the stream was started under, when one was supplied. */
    public String getName() {
      return getStringParam("name");
    }

    /** Destination the audio is being streamed to. */
    public String getUrl() {
      return getStringParam("url");
    }
  }

  /** Transcribe event ({@code calling.call.transcribe}). */
  public static class CallTranscribeEvent extends RelayEvent {
    public CallTranscribeEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link CallTranscribeEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallTranscribeEvent fromPayload(Map<String, Object> payload) {
      return new CallTranscribeEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call being transcribed. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /** Client-generated identifier for this transcription, echoed by the server. */
    public String getControlId() {
      return getStringParam("control_id");
    }

    /** Transcription state, e.g. {@code transcribing} or {@code finished}. */
    public String getState() {
      return getStringParam("state");
    }

    /**
     * Length in seconds of the audio transcribed so far.
     *
     * @return the duration, or {@code 0.0} when the event reports none.
     */
    public double getDuration() {
      return getDoubleParam("duration");
    }

    /**
     * Identifier of the recording the transcript was produced from, when the transcription was
     * backed by one.
     */
    public String getRecordingId() {
      return getStringParam("recording_id");
    }

    /**
     * Size in bytes of the associated media.
     *
     * @return the byte size, or {@code 0} when the event reports none.
     */
    public long getSize() {
      return getLongParam("size");
    }

    /** Where the transcript or its source media can be fetched. */
    public String getUrl() {
      return getStringParam("url");
    }
  }

  /** Connect event ({@code calling.call.connect}). */
  public static class CallConnectEvent extends RelayEvent {
    public CallConnectEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link CallConnectEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallConnectEvent fromPayload(Map<String, Object> payload) {
      return new CallConnectEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call that initiated the connect (the A leg). */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /**
     * Bridge state between the two legs: {@code connecting}, {@code connected}, {@code
     * disconnected} or {@code failed}. Distinct from a leg's own {@code call_state}.
     */
    public String getConnectState() {
      return getStringParam("connect_state");
    }

    public Map<String, Object> getPeer() {
      return getMap(getParams(), "peer");
    }
  }

  /** Refer event ({@code calling.call.refer}). */
  public static class CallReferEvent extends RelayEvent {
    public CallReferEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link CallReferEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallReferEvent fromPayload(Map<String, Object> payload) {
      return new CallReferEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call the SIP REFER was issued on. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /**
     * Progress of the transfer itself, e.g. {@code referring}, {@code completed} or {@code failed}.
     */
    public String getReferState() {
      return getStringParam("refer_state");
    }

    /**
     * Generic state field, present alongside {@link #getReferState()} for events that report the
     * action state rather than the transfer outcome.
     */
    public String getState() {
      return getStringParam("state");
    }

    /**
     * SIP status code from the NOTIFY the transferee sent back reporting how the transfer
     * progressed — this, not the REFER response, tells you whether the transfer actually succeeded.
     */
    public String getSipNotifyResponseCode() {
      return getStringParam("sip_notify_response_code");
    }

    /**
     * SIP status code the far end returned to the REFER request itself. A 2xx here means the REFER
     * was accepted for processing, not that the transfer completed.
     */
    public String getSipReferResponseCode() {
      return getStringParam("sip_refer_response_code");
    }

    /** The {@code Refer-To} target URI the call was asked to transfer to. */
    public String getSipReferTo() {
      return getStringParam("sip_refer_to");
    }
  }

  /** Send digits event ({@code calling.call.send_digits}). */
  public static class CallSendDigitsEvent extends RelayEvent {
    public CallSendDigitsEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link CallSendDigitsEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallSendDigitsEvent fromPayload(Map<String, Object> payload) {
      return new CallSendDigitsEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call the digits were sent on. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /** Client-generated identifier for this send-digits action, echoed by the server. */
    public String getControlId() {
      return getStringParam("control_id");
    }

    /** Send-digits state, e.g. {@code sending} or {@code finished}. */
    public String getState() {
      return getStringParam("state");
    }
  }

  /** Pay event ({@code calling.call.pay}). */
  public static class CallPayEvent extends RelayEvent {
    public CallPayEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link CallPayEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static CallPayEvent fromPayload(Map<String, Object> payload) {
      return new CallPayEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call the payment session is running on. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /** Client-generated identifier for this pay action, echoed by the server. */
    public String getControlId() {
      return getStringParam("control_id");
    }

    /** Payment-session state reported by the platform for this stage of the flow. */
    public String getState() {
      return getStringParam("state");
    }
  }

  /** Conference event ({@code calling.conference}). */
  public static class ConferenceEvent extends RelayEvent {
    public ConferenceEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link ConferenceEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static ConferenceEvent fromPayload(Map<String, Object> payload) {
      return new ConferenceEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID the platform assigned the conference. */
    public String getConferenceId() {
      return getStringParam("conference_id");
    }

    /** UUID of the participant leg this conference event concerns. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /** Human-readable conference name the participants joined under. */
    public String getName() {
      return getStringParam("name");
    }

    /** What happened in the conference — for example a participant joining or leaving. */
    public String getStatus() {
      return getStringParam("status");
    }
  }

  /** Queue event ({@code calling.queue}). */
  public static class QueueEvent extends RelayEvent {
    public QueueEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link QueueEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static QueueEvent fromPayload(Map<String, Object> payload) {
      return new QueueEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the queued call leg. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /** Client-generated identifier for the queueing action, echoed by the server. */
    public String getControlId() {
      return getStringParam("control_id");
    }

    /**
     * What happened to the call in the queue — for example it was enqueued, advanced, or dequeued.
     */
    public String getStatus() {
      return getStringParam("status");
    }

    /** Queue identifier. Note the wire key is the bare {@code id}, not {@code queue_id}. */
    public String getQueueId() {
      return getStringParam("id");
    }

    /** Queue name. Note the wire key is the bare {@code name}, not {@code queue_name}. */
    public String getQueueName() {
      return getStringParam("name");
    }

    /**
     * This call's place in the queue, counting from the front.
     *
     * @return the position, or {@code 0} when the event reports none.
     */
    public int getPosition() {
      Object v = getParams().get("position");
      return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    /**
     * Total number of calls waiting in the queue at the time of this event.
     *
     * @return the queue depth, or {@code 0} when the event reports none.
     */
    public int getSize() {
      Object v = getParams().get("size");
      return v instanceof Number ? ((Number) v).intValue() : 0;
    }
  }

  /** Authorization state event ({@code signalwire.authorization.state}). */
  public static class AuthorizationStateEvent extends RelayEvent {
    public AuthorizationStateEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Opaque encrypted re-authentication token ({@code <encryptedBase64>:<tagBase64>}) the server
     * issues so a reconnect can skip the full authentication round-trip. Store it and send it back
     * in {@code signalwire.connect} params; the server silently falls back to normal authentication
     * if it is stale. Treat it as a credential — it grants session re-entry.
     */
    public String getAuthorizationState() {
      return getStringParam("authorization_state");
    }
  }

  /** Inbound messaging event ({@code messaging.receive}). */
  public static class MessagingReceiveEvent extends RelayEvent {
    public MessagingReceiveEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link MessagingReceiveEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static MessagingReceiveEvent fromPayload(Map<String, Object> payload) {
      return new MessagingReceiveEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /**
     * UUID the platform assigned the inbound message; the key subsequent {@code messaging.state}
     * events are routed on.
     */
    public String getMessageId() {
      return getStringParam("message_id");
    }

    /**
     * The subscribed context the message was delivered on. Only messages for contexts the client
     * subscribed to at connect time arrive here.
     */
    public String getContext() {
      return getStringParam("context");
    }

    /** Always {@code inbound} for a received message. */
    public String getDirection() {
      return getStringParam("direction");
    }

    /** Sender's number in E.164 form. */
    public String getFromNumber() {
      return getStringParam("from_number");
    }

    /** Recipient number in E.164 form — one of the project's own numbers. */
    public String getToNumber() {
      return getStringParam("to_number");
    }

    /**
     * The message text as sent. This is untrusted end-user input: never interpolate it into a
     * prompt, a command, or a log line without treating it as such.
     */
    public String getBody() {
      return getStringParam("body");
    }

    /**
     * URLs of any MMS attachments carried by the message.
     *
     * @return the media URLs, or an empty list when the message had none.
     */
    public List<String> getMedia() {
      return getStringList(getParams(), "media");
    }

    /**
     * How many SMS segments the message occupied — the unit billing is charged in.
     *
     * @return the segment count, defaulting to {@code 1} when the wire omitted it.
     */
    public int getSegments() {
      return getInt(getParams(), "segments", 1);
    }

    /** Delivery state, always {@code received} for an inbound message. */
    public String getMessageState() {
      return getStringParam("message_state");
    }

    /**
     * Client-supplied correlation tags carried on the message.
     *
     * @return the tags, or an empty list when none were set.
     */
    public List<String> getTags() {
      return getStringList(getParams(), "tags");
    }
  }

  /** Outbound messaging state event ({@code messaging.state}). */
  public static class MessagingStateEvent extends RelayEvent {
    public MessagingStateEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /**
     * Build a {@link MessagingStateEvent} from a raw {@code signalwire.event} payload ({@code
     * event_type}/{@code timestamp}/{@code params}).
     */
    public static MessagingStateEvent fromPayload(Map<String, Object> payload) {
      return new MessagingStateEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /**
     * UUID of the outbound message this state update belongs to, matching the id returned by {@code
     * messaging.send}. Route the event to the tracked {@code Message} on this key.
     */
    public String getMessageId() {
      return getStringParam("message_id");
    }

    /** The context the message was sent under. */
    public String getContext() {
      return getStringParam("context");
    }

    /** Always {@code outbound} for a message the client sent. */
    public String getDirection() {
      return getStringParam("direction");
    }

    /** Sender number in E.164 form — the project number the message was sent from. */
    public String getFromNumber() {
      return getStringParam("from_number");
    }

    /** Destination number in E.164 form. */
    public String getToNumber() {
      return getStringParam("to_number");
    }

    /** The message text that was sent. */
    public String getBody() {
      return getStringParam("body");
    }

    /**
     * URLs of MMS attachments included in the outbound message.
     *
     * @return the media URLs, or an empty list when there were none.
     */
    public List<String> getMedia() {
      return getStringList(getParams(), "media");
    }

    /**
     * How many SMS segments the message occupied — the unit billing is charged in.
     *
     * @return the segment count, defaulting to {@code 1} when the wire omitted it.
     */
    public int getSegments() {
      return getInt(getParams(), "segments", 1);
    }

    /**
     * Current delivery state, progressing through values such as {@code queued}, {@code sent},
     * {@code delivered} and {@code undelivered}/{@code failed}.
     */
    public String getMessageState() {
      return getStringParam("message_state");
    }

    /**
     * Why delivery failed, populated only for a failure state.
     *
     * @return the failure reason, or {@code null} when delivery has not failed.
     */
    public String getReason() {
      return getStringParam("reason");
    }

    /**
     * Client-supplied correlation tags echoed back on the state event.
     *
     * @return the tags, or an empty list when none were set.
     */
    public List<String> getTags() {
      return getStringList(getParams(), "tags");
    }
  }

  /** Denoise state event ({@code calling.call.denoise}). */
  public static class DenoiseEvent extends RelayEvent {
    public DenoiseEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /** Build a {@link DenoiseEvent} from a raw {@code signalwire.event} payload. */
    public static DenoiseEvent fromPayload(Map<String, Object> payload) {
      return new DenoiseEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call whose denoise state changed. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /** Whether denoise is currently active on the call. */
    public boolean isDenoised() {
      Object val = getParams().get("denoised");
      return val instanceof Boolean ? (Boolean) val : false;
    }
  }

  /** Echo state event ({@code calling.call.echo}). */
  public static class EchoEvent extends RelayEvent {
    public EchoEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /** Build an {@link EchoEvent} from a raw {@code signalwire.event} payload. */
    public static EchoEvent fromPayload(Map<String, Object> payload) {
      return new EchoEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call echo is running on. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /** Whether echo is currently active on the call. */
    public String getState() {
      return getStringParam("state");
    }
  }

  /** Hold state event ({@code calling.call.hold}). */
  public static class HoldEvent extends RelayEvent {
    public HoldEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /** Build a {@link HoldEvent} from a raw {@code signalwire.event} payload. */
    public static HoldEvent fromPayload(Map<String, Object> payload) {
      return new HoldEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /** UUID of the call whose hold state changed. */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /** Whether the call is currently held or has been resumed. */
    public String getState() {
      return getStringParam("state");
    }
  }

  /** Calling error event ({@code calling.error}). */
  public static class CallingErrorEvent extends RelayEvent {
    public CallingErrorEvent(String eventType, double timestamp, Map<String, Object> params) {
      super(eventType, timestamp, params);
    }

    /** Build a {@link CallingErrorEvent} from a raw {@code signalwire.event} payload. */
    public static CallingErrorEvent fromPayload(Map<String, Object> payload) {
      return new CallingErrorEvent(
          payloadEventType(payload), payloadTimestamp(payload), payloadParams(payload));
    }

    /**
     * UUID of the call the error relates to.
     *
     * @return the call id, or {@code null} for an error not scoped to a single call.
     */
    @Override
    public String getCallId() {
      return getStringParam("call_id");
    }

    /** The error code carried by the event. */
    public String getCode() {
      return getStringParam("code");
    }

    /** The human-readable error message. */
    public String getMessage() {
      return getStringParam("message");
    }
  }
}
