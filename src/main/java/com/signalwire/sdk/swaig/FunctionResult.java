/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swaig;

import com.google.gson.Gson;
import com.signalwire.sdk.swml.Codec;
import com.signalwire.sdk.swml.RecordDirection;
import com.signalwire.sdk.swml.RecordFormat;
import com.signalwire.sdk.swml.TapDirection;
import java.util.*;

/**
 * SwaigFunctionResult - the response builder returned by tool handlers.
 *
 * <p>Every method returns {@code this} for fluent chaining. Contains 40+ action methods covering
 * call control, state management, media, speech/AI config, and advanced features.
 *
 * <p>Serialization rules:
 *
 * <ul>
 *   <li>response - always included (string)
 *   <li>action - only included if at least one action exists (array of objects)
 *   <li>post_process - only included if true and actions exist (boolean)
 * </ul>
 */
public class FunctionResult {

  private static final Gson gson = new Gson();

  private String response;
  private final List<Map<String, Object>> actions;
  private boolean postProcess;

  public FunctionResult() {
    this("", false);
  }

  public FunctionResult(String response) {
    this(response, false);
  }

  public FunctionResult(String response, boolean postProcess) {
    this.response = response != null ? response : "";
    this.actions = new ArrayList<>();
    this.postProcess = postProcess;
  }

  // -------- Core Setters --------

  public FunctionResult setResponse(String response) {
    this.response = response != null ? response : "";
    return this;
  }

  public FunctionResult setPostProcess(boolean postProcess) {
    this.postProcess = postProcess;
    return this;
  }

  /** Add a single action to the response. */
  public FunctionResult addAction(String name, Object data) {
    Map<String, Object> actionMap = new LinkedHashMap<>();
    actionMap.put(name, data);
    actions.add(actionMap);
    return this;
  }

  /** Add multiple actions. */
  public FunctionResult addActions(List<Map<String, Object>> actionList) {
    actions.addAll(actionList);
    return this;
  }

  // ======== Call Control ========

  /** Connect/transfer the call to another destination via SWML connect verb. */
  public FunctionResult connect(String destination, boolean isFinal, String from) {
    Map<String, Object> connectParams = new LinkedHashMap<>();
    connectParams.put("to", destination);
    if (from != null && !from.isEmpty()) {
      connectParams.put("from", from);
    }

    Map<String, Object> swmlAction = new LinkedHashMap<>();
    swmlAction.put(
        "SWML",
        Map.of(
            "sections",
            Map.of("main", List.of(Map.of("connect", connectParams))),
            "version",
            "1.0.0"));
    swmlAction.put("transfer", String.valueOf(isFinal).toLowerCase());
    actions.add(swmlAction);
    return this;
  }

  public FunctionResult connect(String destination, boolean isFinal) {
    return connect(destination, isFinal, null);
  }

  /** SWML transfer with AI response setup. */
  public FunctionResult swmlTransfer(String dest, String aiResponse, boolean isFinal) {
    Map<String, Object> swmlAction = new LinkedHashMap<>();
    swmlAction.put(
        "SWML",
        Map.of(
            "version",
            "1.0.0",
            "sections",
            Map.of(
                "main",
                List.of(
                    Map.of("set", Map.of("ai_response", aiResponse)),
                    Map.of("transfer", Map.of("dest", dest))))));
    swmlAction.put("transfer", String.valueOf(isFinal).toLowerCase());
    actions.add(swmlAction);
    return this;
  }

  /** Hangup the call. */
  public FunctionResult hangup() {
    return addAction("hangup", true);
  }

  /** Put the call on hold (timeout clamped 0-900). */
  public FunctionResult hold(int timeout) {
    return addAction("hold", Math.max(0, Math.min(timeout, 900)));
  }

  /** Control how agent waits for user input. */
  public FunctionResult waitForUser(Boolean enabled, Integer timeout, boolean answerFirst) {
    Object waitValue;
    if (answerFirst) {
      waitValue = "answer_first";
    } else if (timeout != null) {
      waitValue = timeout;
    } else if (enabled != null) {
      waitValue = enabled;
    } else {
      waitValue = true;
    }
    return addAction("wait_for_user", waitValue);
  }

  public FunctionResult waitForUser() {
    return waitForUser(null, null, false);
  }

  /** Stop the agent execution. */
  public FunctionResult stop() {
    return addAction("stop", true);
  }

  // ======== State & Data Management ========

  public FunctionResult updateGlobalData(Map<String, Object> data) {
    return addAction("set_global_data", data);
  }

  public FunctionResult removeGlobalData(Object keys) {
    return addAction("unset_global_data", keys);
  }

  public FunctionResult setMetadata(Map<String, Object> data) {
    return addAction("set_meta_data", data);
  }

  public FunctionResult removeMetadata(Object keys) {
    return addAction("unset_meta_data", keys);
  }

  /** Send a user event through SWML. */
  public FunctionResult swmlUserEvent(Map<String, Object> eventData) {
    Map<String, Object> swmlDoc = new LinkedHashMap<>();
    swmlDoc.put(
        "sections", Map.of("main", List.of(Map.of("user_event", Map.of("event", eventData)))));
    swmlDoc.put("version", "1.0.0");
    return addAction("SWML", swmlDoc);
  }

  /** Change the conversation step. */
  public FunctionResult swmlChangeStep(String stepName) {
    return addAction("change_step", stepName);
  }

  /** Change the conversation context. */
  public FunctionResult swmlChangeContext(String contextName) {
    return addAction("change_context", contextName);
  }

  /** Switch context with optional reset parameters. */
  public FunctionResult switchContext(
      String systemPrompt, String userPrompt, boolean consolidate, boolean fullReset) {
    if (systemPrompt != null
        && !systemPrompt.isEmpty()
        && (userPrompt == null || userPrompt.isEmpty())
        && !consolidate
        && !fullReset) {
      return addAction("context_switch", systemPrompt);
    }
    Map<String, Object> contextData = new LinkedHashMap<>();
    if (systemPrompt != null && !systemPrompt.isEmpty()) {
      contextData.put("system_prompt", systemPrompt);
    }
    if (userPrompt != null && !userPrompt.isEmpty()) {
      contextData.put("user_prompt", userPrompt);
    }
    if (consolidate) {
      contextData.put("consolidate", true);
    }
    if (fullReset) {
      contextData.put("full_reset", true);
    }
    return addAction("context_switch", contextData);
  }

  public FunctionResult switchContext(String systemPrompt) {
    return switchContext(systemPrompt, null, false, false);
  }

  /** Replace tool_call+result pair in conversation history. */
  public FunctionResult replaceInHistory(String text) {
    return addAction("replace_in_history", text);
  }

  public FunctionResult replaceInHistory(boolean summary) {
    return addAction("replace_in_history", summary);
  }

  // ======== Media Control ========

  public FunctionResult say(String text) {
    return addAction("say", text);
  }

  /** Play audio file in background. */
  public FunctionResult playBackgroundFile(String filename, boolean wait) {
    if (wait) {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("file", filename);
      params.put("wait", true);
      return addAction("playback_bg", params);
    }
    return addAction("playback_bg", filename);
  }

  public FunctionResult playBackgroundFile(String filename) {
    return playBackgroundFile(filename, false);
  }

  public FunctionResult stopBackgroundFile() {
    return addAction("stop_playback_bg", true);
  }

  /**
   * Start background call recording via SWML — full parity with the Python reference {@code
   * signalwire.core.function_result.FunctionResult.record_call}.
   *
   * <p>Validates {@code format} ∈ {wav, mp3, mp4} and {@code direction} ∈ {speak, listen, both}
   * with byte-exact Python {@code ValueError} messages. The reference ALWAYS emits {@code stereo},
   * {@code format}, {@code direction}, {@code beep} and {@code input_sensitivity}; {@code
   * control_id}, {@code terminators}, {@code initial_timeout}, {@code end_silence_timeout}, {@code
   * max_length} and {@code status_url} are emitted only when supplied.
   *
   * @param controlId recording identifier (pairs with stopRecordCall; {@code null} to omit)
   * @param stereo record in stereo (default {@code false})
   * @param format "wav", "mp3" or "mp4" (default "wav")
   * @param direction "speak", "listen" or "both" (default "both")
   * @param terminators digits that stop recording ({@code null} to omit)
   * @param beep play a beep before recording (default {@code false}; ALWAYS emitted)
   * @param inputSensitivity input sensitivity (default 44.0; ALWAYS emitted)
   * @param initialTimeout seconds to wait for speech start ({@code null} to omit)
   * @param endSilenceTimeout seconds of trailing silence before ending ({@code null} to omit)
   * @param maxLength maximum recording length in seconds ({@code null} to omit)
   * @param statusUrl URL for recording status events ({@code null} to omit)
   * @return this, for chaining
   * @throws IllegalArgumentException if format or direction is invalid
   */
  public FunctionResult recordCall(
      String controlId,
      boolean stereo,
      String format,
      String direction,
      String terminators,
      boolean beep,
      double inputSensitivity,
      Double initialTimeout,
      Double endSilenceTimeout,
      Double maxLength,
      String statusUrl) {
    String fmt = format != null ? format : "wav";
    String dir = direction != null ? direction : "both";
    // Validate format. Matches the SWML record_call verb schema
    // ($defs/RecordCall.format = {wav, mp3, mp4}).
    if (!List.of("wav", "mp3", "mp4").contains(fmt)) {
      throw new IllegalArgumentException("format must be 'wav', 'mp3', or 'mp4'");
    }
    // Validate direction.
    if (!List.of("speak", "listen", "both").contains(dir)) {
      throw new IllegalArgumentException("direction must be 'speak', 'listen', or 'both'");
    }

    Map<String, Object> recordParams = new LinkedHashMap<>();
    recordParams.put("stereo", stereo);
    recordParams.put("format", fmt);
    recordParams.put("direction", dir);
    recordParams.put("beep", beep);
    recordParams.put("input_sensitivity", inputSensitivity);
    if (controlId != null && !controlId.isEmpty()) recordParams.put("control_id", controlId);
    if (terminators != null && !terminators.isEmpty()) recordParams.put("terminators", terminators);
    if (initialTimeout != null) recordParams.put("initial_timeout", initialTimeout);
    if (endSilenceTimeout != null) recordParams.put("end_silence_timeout", endSilenceTimeout);
    if (maxLength != null) recordParams.put("max_length", maxLength);
    if (statusUrl != null && !statusUrl.isEmpty()) recordParams.put("status_url", statusUrl);
    return executeSwml(
        Map.of(
            "version",
            "1.0.0",
            "sections",
            Map.of("main", List.of(Map.of("record_call", recordParams)))),
        false);
  }

  /**
   * Fully-typed, full-arity overload of {@link #recordCall(String, boolean, String, String, String,
   * boolean, double, Double, Double, Double, String)}: the closed-set {@code format} ({@link
   * RecordFormat}) and {@code direction} ({@link RecordDirection}) are typed so a misspelled
   * container format or direction fails at compile time instead of being rejected by the server's
   * {@code ValueError}, while every Python {@code record_call} optional (terminators / beep /
   * input_sensitivity / initial_timeout / end_silence_timeout / max_length / status_url) is still
   * exposed positionally. Delegates to the string path via {@link RecordFormat#getValue()} / {@link
   * RecordDirection#getValue()}, so the emitted SWML {@code record_call} payload is byte-identical
   * to the bare-string call. This is the canonical reference-parity surface; the all-{@link String}
   * full-arity overload above is the forward-compatible escape hatch (and the convenience/typed
   * lower-arity overloads below just delegate here).
   */
  public FunctionResult recordCall(
      String controlId,
      boolean stereo,
      RecordFormat format,
      RecordDirection direction,
      String terminators,
      boolean beep,
      double inputSensitivity,
      Double initialTimeout,
      Double endSilenceTimeout,
      Double maxLength,
      String statusUrl) {
    return recordCall(
        controlId,
        stereo,
        format != null ? format.getValue() : null,
        direction != null ? direction.getValue() : null,
        terminators,
        beep,
        inputSensitivity,
        initialTimeout,
        endSilenceTimeout,
        maxLength,
        statusUrl);
  }

  /**
   * Start background call recording (convenience form). Delegates to the full-arity {@link
   * #recordCall(String, boolean, String, String, String, boolean, double, Double, Double, Double,
   * String)} with {@code beep}=false, {@code input_sensitivity}=44.0 and the trailing options at
   * their reference defaults — so the emitted SWML always carries the always-on {@code beep}/{@code
   * input_sensitivity} keys.
   */
  public FunctionResult recordCall(
      String controlId, boolean stereo, String format, String direction) {
    return recordCall(
        controlId, stereo, format, direction, null, false, 44.0, null, null, null, null);
  }

  /**
   * Typed overload of {@link #recordCall(String, boolean, String, String)}. Accepts a {@link
   * RecordFormat} so a misspelled container format fails at compile time instead of being rejected
   * by the server. Delegates to the string path via {@link RecordFormat#getValue()}, so the SWML
   * {@code record_call} payload is identical.
   */
  public FunctionResult recordCall(
      String controlId, boolean stereo, RecordFormat format, String direction) {
    return recordCall(controlId, stereo, format != null ? format.getValue() : null, direction);
  }

  /**
   * Typed-direction overload of {@link #recordCall(String, boolean, String, String)}. Accepts a
   * {@link RecordDirection} ({@code speak}/{@code listen}/{@code both}) so a misspelled direction
   * fails at compile time instead of being rejected by the server's {@code ValueError}. Delegates
   * to the string path via {@link RecordDirection#getValue()}, so the SWML {@code record_call}
   * payload is byte-identical. Note this set differs from {@link TapDirection} ({@code listen} here
   * vs {@code hear} for {@code tap}).
   */
  public FunctionResult recordCall(
      String controlId, boolean stereo, String format, RecordDirection direction) {
    return recordCall(controlId, stereo, format, direction != null ? direction.getValue() : null);
  }

  /**
   * Fully-typed overload of {@link #recordCall(String, boolean, String, String)}: both the
   * container format ({@link RecordFormat}) and the audio direction ({@link RecordDirection}) are
   * typed closed sets. Delegates to the string path via {@link RecordFormat#getValue()} / {@link
   * RecordDirection#getValue()}, so the emitted SWML is identical to the bare-string call.
   */
  public FunctionResult recordCall(
      String controlId, boolean stereo, RecordFormat format, RecordDirection direction) {
    return recordCall(
        controlId,
        stereo,
        format != null ? format.getValue() : null,
        direction != null ? direction.getValue() : null);
  }

  public FunctionResult recordCall() {
    return recordCall(null, false, "wav", "both");
  }

  /** Stop an active background call recording. */
  public FunctionResult stopRecordCall(String controlId) {
    Map<String, Object> stopParams = new LinkedHashMap<>();
    if (controlId != null && !controlId.isEmpty()) {
      stopParams.put("control_id", controlId);
    }
    return executeSwml(
        Map.of(
            "version",
            "1.0.0",
            "sections",
            Map.of("main", List.of(Map.of("stop_record_call", stopParams)))),
        false);
  }

  public FunctionResult stopRecordCall() {
    return stopRecordCall(null);
  }

  // ======== Speech & AI Configuration ========

  public FunctionResult addDynamicHints(List<Object> hints) {
    return addAction("add_dynamic_hints", hints);
  }

  public FunctionResult clearDynamicHints() {
    return addAction("clear_dynamic_hints", new LinkedHashMap<>());
  }

  public FunctionResult setEndOfSpeechTimeout(int milliseconds) {
    return addAction("end_of_speech_timeout", milliseconds);
  }

  public FunctionResult setSpeechEventTimeout(int milliseconds) {
    return addAction("speech_event_timeout", milliseconds);
  }

  /** Toggle specific SWAIG functions on/off. */
  public FunctionResult toggleFunctions(List<Map<String, Object>> toggles) {
    return addAction("toggle_functions", toggles);
  }

  public FunctionResult enableFunctionsOnTimeout(boolean enabled) {
    return addAction("functions_on_speaker_timeout", enabled);
  }

  public FunctionResult enableExtensiveData(boolean enabled) {
    return addAction("extensive_data", enabled);
  }

  /** Update agent runtime settings (temperature, top_p, etc.). */
  public FunctionResult updateSettings(Map<String, Object> settings) {
    return addAction("settings", settings);
  }

  // ======== Advanced Features ========

  /** Execute SWML content with optional transfer behavior. */
  public FunctionResult executeSwml(Object swmlContent, boolean transfer) {
    Map<String, Object> swmlData;
    if (swmlContent instanceof String str) {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = gson.fromJson(str, Map.class);
        swmlData = new LinkedHashMap<>(parsed);
      } catch (Exception e) {
        swmlData = new LinkedHashMap<>();
        swmlData.put("raw_swml", str);
      }
    } else if (swmlContent instanceof Map<?, ?> m) {
      @SuppressWarnings("unchecked")
      Map<String, Object> cast = (Map<String, Object>) m;
      swmlData = new LinkedHashMap<>(cast);
    } else {
      throw new IllegalArgumentException("swmlContent must be a String or Map");
    }
    if (transfer) {
      swmlData.put("transfer", "true");
    }
    return addAction("SWML", swmlData);
  }

  public FunctionResult executeSwml(Object swmlContent) {
    return executeSwml(swmlContent, false);
  }

  /**
   * Join an ad-hoc audio conference with RELAY and CXML calls using SWML.
   *
   * <p>Functional parity with the Python reference {@code
   * signalwire.core.function_result.FunctionResult.join_conference}. Every optional parameter the
   * reference exposes is a positional argument here, with the same default and the same validation.
   * Hold music is {@code waitUrl} (snake_case wire key {@code wait_url}) — there is no separate
   * "hold audio" parameter; the reference uses {@code wait_url}.
   *
   * @param name conference name (required, must be non-blank)
   * @param muted join muted (default {@code false})
   * @param beep "true", "false", "onEnter", "onExit" (default "true")
   * @param startOnEnter conference starts when this participant enters (default {@code true})
   * @param endOnExit conference ends when this participant exits (default {@code false})
   * @param waitUrl SWML URL for hold music ({@code null} for default)
   * @param maxParticipants maximum participants, 1..250 (default 250)
   * @param record "do-not-record" or "record-from-start" (default "do-not-record")
   * @param region conference region ({@code null} for default)
   * @param trim "trim-silence" or "do-not-trim" (default "trim-silence")
   * @param coach SWML Call ID / CXML CallSid for coaching ({@code null} for none)
   * @param statusCallbackEvent space-separated status events ({@code null} for none)
   * @param statusCallback URL for status callbacks ({@code null} for none)
   * @param statusCallbackMethod "GET" or "POST" (default "POST")
   * @param recordingStatusCallback URL for recording status callbacks ({@code null} for none)
   * @param recordingStatusCallbackMethod "GET" or "POST" (default "POST")
   * @param recordingStatusCallbackEvent recording events (default "completed")
   * @param result switch-on-return value/cond ({@code null} for none)
   * @return this, for chaining
   * @throws IllegalArgumentException if any validated value is invalid
   */
  public FunctionResult joinConference(
      String name,
      boolean muted,
      String beep,
      boolean startOnEnter,
      boolean endOnExit,
      String waitUrl,
      int maxParticipants,
      String record,
      String region,
      String trim,
      String coach,
      String statusCallbackEvent,
      String statusCallback,
      String statusCallbackMethod,
      String recordingStatusCallback,
      String recordingStatusCallbackMethod,
      String recordingStatusCallbackEvent,
      Object result) {

    // Validate beep. Error strings mirror Python's f-string list rendering
    // (single-quoted members) so a ported substring match passes.
    List<String> validBeepValues = List.of("true", "false", "onEnter", "onExit");
    if (!validBeepValues.contains(beep)) {
      throw new IllegalArgumentException(
          "beep must be one of ['true', 'false', 'onEnter', 'onExit']");
    }

    // Validate max_participants.
    if (maxParticipants <= 0 || maxParticipants > 250) {
      throw new IllegalArgumentException("max_participants must be a positive integer <= 250");
    }

    // Validate record.
    List<String> validRecordValues = List.of("do-not-record", "record-from-start");
    if (!validRecordValues.contains(record)) {
      throw new IllegalArgumentException(
          "record must be one of ['do-not-record', 'record-from-start']");
    }

    // Validate trim.
    List<String> validTrimValues = List.of("trim-silence", "do-not-trim");
    if (!validTrimValues.contains(trim)) {
      throw new IllegalArgumentException("trim must be one of ['trim-silence', 'do-not-trim']");
    }

    // Validate callback methods.
    List<String> validMethods = List.of("GET", "POST");
    if (!validMethods.contains(statusCallbackMethod)) {
      throw new IllegalArgumentException("status_callback_method must be one of ['GET', 'POST']");
    }
    if (!validMethods.contains(recordingStatusCallbackMethod)) {
      throw new IllegalArgumentException(
          "recording_status_callback_method must be one of ['GET', 'POST']");
    }

    // Validate name (non-blank).
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("name cannot be empty");
    }

    // Build the join_conference payload. Simple form when everything is at
    // its default: emit the bare conference name string. Otherwise the
    // object form with each non-default param under its snake_case wire key.
    boolean allDefaults =
        !muted
            && "true".equals(beep)
            && startOnEnter
            && !endOnExit
            && waitUrl == null
            && maxParticipants == 250
            && "do-not-record".equals(record)
            && region == null
            && "trim-silence".equals(trim)
            && coach == null
            && statusCallbackEvent == null
            && statusCallback == null
            && "POST".equals(statusCallbackMethod)
            && recordingStatusCallback == null
            && "POST".equals(recordingStatusCallbackMethod)
            && "completed".equals(recordingStatusCallbackEvent)
            && result == null;

    Object joinParams;
    if (allDefaults) {
      joinParams = name;
    } else {
      Map<String, Object> params = new LinkedHashMap<>();
      params.put("name", name);
      if (muted) params.put("muted", true);
      if (!"true".equals(beep)) params.put("beep", beep);
      if (!startOnEnter) params.put("start_on_enter", false);
      if (endOnExit) params.put("end_on_exit", true);
      if (waitUrl != null) params.put("wait_url", waitUrl);
      if (maxParticipants != 250) params.put("max_participants", maxParticipants);
      if (!"do-not-record".equals(record)) params.put("record", record);
      if (region != null) params.put("region", region);
      if (!"trim-silence".equals(trim)) params.put("trim", trim);
      if (coach != null) params.put("coach", coach);
      if (statusCallbackEvent != null) params.put("status_callback_event", statusCallbackEvent);
      if (statusCallback != null) params.put("status_callback", statusCallback);
      if (!"POST".equals(statusCallbackMethod))
        params.put("status_callback_method", statusCallbackMethod);
      if (recordingStatusCallback != null)
        params.put("recording_status_callback", recordingStatusCallback);
      if (!"POST".equals(recordingStatusCallbackMethod)) {
        params.put("recording_status_callback_method", recordingStatusCallbackMethod);
      }
      if (!"completed".equals(recordingStatusCallbackEvent)) {
        params.put("recording_status_callback_event", recordingStatusCallbackEvent);
      }
      if (result != null) params.put("result", result);
      joinParams = params;
    }

    Map<String, Object> swmlDoc = new LinkedHashMap<>();
    swmlDoc.put("version", "1.0.0");
    swmlDoc.put(
        "sections",
        Map.of("main", List.of(Collections.singletonMap("join_conference", joinParams))));
    return executeSwml(swmlDoc, false);
  }

  /**
   * Join a conference (simple form, name only). Delegates to the full-arity overload with every
   * option at its reference default, so an all-defaults call emits the bare conference name string.
   */
  public FunctionResult joinConference(String name) {
    return joinConference(
        name,
        false,
        "true",
        true,
        false,
        null,
        250,
        "do-not-record",
        null,
        "trim-silence",
        null,
        null,
        null,
        "POST",
        null,
        "POST",
        "completed",
        null);
  }

  /** Join a RELAY room. */
  public FunctionResult joinRoom(String name) {
    return executeSwml(
        Map.of(
            "version",
            "1.0.0",
            "sections",
            Map.of("main", List.of(Map.of("join_room", Map.of("name", name))))),
        false);
  }

  /** Send SIP REFER. */
  public FunctionResult sipRefer(String toUri) {
    return executeSwml(
        Map.of(
            "version",
            "1.0.0",
            "sections",
            Map.of("main", List.of(Map.of("sip_refer", Map.of("to_uri", toUri))))),
        false);
  }

  /**
   * Start a background call tap via SWML — full parity with the Python reference {@code
   * signalwire.core.function_result.FunctionResult.tap}.
   *
   * <p>Validates {@code direction} ∈ {speak, hear, both}, {@code codec} ∈ {PCMU, PCMA}, and {@code
   * rtp_ptime > 0} with byte-exact Python {@code ValueError} messages (rendered through {@link
   * IllegalArgumentException}). Only {@code uri} is always emitted; each other key is emitted only
   * when it differs from its reference default ({@code direction != "both"}, {@code codec !=
   * "PCMU"}, {@code rtp_ptime != 20}), plus {@code control_id} and {@code status_url} when
   * supplied.
   *
   * @param uri tap media-stream destination (required; rtp://, ws://, wss://)
   * @param controlId tap identifier ({@code null} for an auto-generated one)
   * @param direction "speak", "hear" or "both" (default "both")
   * @param codec "PCMU" or "PCMA" (default "PCMU")
   * @param rtpPtime RTP packetization time in ms, &gt; 0 (default 20)
   * @param statusUrl URL for status-change requests ({@code null} to omit)
   * @return this, for chaining
   * @throws IllegalArgumentException if direction, codec or rtp_ptime is invalid
   */
  public FunctionResult tap(
      String uri,
      String controlId,
      String direction,
      String codec,
      int rtpPtime,
      String statusUrl) {
    // Validate direction. Error strings mirror Python's f-string list
    // rendering (single-quoted members) so a ported substring match passes.
    List<String> validDirections = List.of("speak", "hear", "both");
    if (!validDirections.contains(direction)) {
      throw new IllegalArgumentException("direction must be one of ['speak', 'hear', 'both']");
    }
    // Validate codec.
    List<String> validCodecs = List.of("PCMU", "PCMA");
    if (!validCodecs.contains(codec)) {
      throw new IllegalArgumentException("codec must be one of ['PCMU', 'PCMA']");
    }
    // Validate rtp_ptime.
    if (rtpPtime <= 0) {
      throw new IllegalArgumentException("rtp_ptime must be a positive integer");
    }

    Map<String, Object> tapParams = new LinkedHashMap<>();
    tapParams.put("uri", uri);
    if (controlId != null && !controlId.isEmpty()) tapParams.put("control_id", controlId);
    if (!"both".equals(direction)) tapParams.put("direction", direction);
    if (!"PCMU".equals(codec)) tapParams.put("codec", codec);
    if (rtpPtime != 20) tapParams.put("rtp_ptime", rtpPtime);
    if (statusUrl != null && !statusUrl.isEmpty()) tapParams.put("status_url", statusUrl);
    return executeSwml(
        Map.of("version", "1.0.0", "sections", Map.of("main", List.of(Map.of("tap", tapParams)))),
        false);
  }

  /**
   * Fully-typed, full-arity overload of {@link #tap(String, String, String, String, int, String)}:
   * the closed-set {@code direction} ({@link TapDirection}) and {@code codec} ({@link Codec}) are
   * typed so a misspelled direction or codec fails at compile time instead of being rejected by the
   * server's {@code ValueError}, while the remaining Python {@code tap} params ({@code rtp_ptime} /
   * {@code status_url}) are still exposed positionally. Delegates to the string path via {@link
   * TapDirection#getValue()} / {@link Codec#getValue()}, so the emitted SWML {@code tap} payload is
   * byte-identical to the bare-string call. This is the canonical reference-parity surface; the
   * all-{@link String} full-arity overload above is the forward-compatible escape hatch (and the
   * convenience/typed lower-arity overloads below just delegate here).
   */
  public FunctionResult tap(
      String uri,
      String controlId,
      TapDirection direction,
      Codec codec,
      int rtpPtime,
      String statusUrl) {
    return tap(
        uri,
        controlId,
        direction != null ? direction.getValue() : null,
        codec != null ? codec.getValue() : null,
        rtpPtime,
        statusUrl);
  }

  /**
   * Start a call tap (convenience form). Delegates to the full-arity {@link #tap(String, String,
   * String, String, int, String)} with {@code rtp_ptime} at its reference default (20) and no
   * {@code status_url}.
   */
  public FunctionResult tap(String uri, String controlId, String direction, String codec) {
    return tap(uri, controlId, direction, codec, 20, null);
  }

  /**
   * Typed-direction overload of {@link #tap(String, String, String, String)}. Accepts a {@link
   * TapDirection} ({@code speak}/{@code hear}/{@code both}) so a misspelled direction fails at
   * compile time instead of being rejected by the server's {@code ValueError}. Delegates via {@link
   * TapDirection#getValue()}, so the SWML {@code tap} payload is byte-identical. Note this set
   * differs from {@link RecordDirection} ({@code hear} here vs {@code listen} for {@code
   * record_call}).
   */
  public FunctionResult tap(String uri, String controlId, TapDirection direction, String codec) {
    return tap(uri, controlId, direction != null ? direction.getValue() : null, codec);
  }

  /**
   * Typed-codec overload of {@link #tap(String, String, String, String)}. Accepts a {@link Codec}
   * ({@code PCMU}/{@code PCMA}) so a misspelled codec fails at compile time. Delegates via {@link
   * Codec#getValue()}, so the SWML {@code tap} payload is byte-identical. This is the SWAIG tap
   * codec set only — RELAY {@code stream}/{@code connect} use a larger superset.
   */
  public FunctionResult tap(String uri, String controlId, String direction, Codec codec) {
    return tap(uri, controlId, direction, codec != null ? codec.getValue() : null);
  }

  /**
   * Fully-typed overload of {@link #tap(String, String, String, String)}: both the audio direction
   * ({@link TapDirection}) and the media codec ({@link Codec}) are typed closed sets. Delegates via
   * the per-enum {@code getValue()}, so the emitted SWML is identical to the bare-string call.
   */
  public FunctionResult tap(String uri, String controlId, TapDirection direction, Codec codec) {
    return tap(
        uri,
        controlId,
        direction != null ? direction.getValue() : null,
        codec != null ? codec.getValue() : null);
  }

  /** Stop a tap stream. */
  public FunctionResult stopTap(String controlId) {
    Map<String, Object> stopParams = new LinkedHashMap<>();
    if (controlId != null && !controlId.isEmpty()) {
      stopParams.put("control_id", controlId);
    }
    return executeSwml(
        Map.of(
            "version",
            "1.0.0",
            "sections",
            Map.of("main", List.of(Map.of("stop_tap", stopParams)))),
        false);
  }

  public FunctionResult stopTap() {
    return stopTap(null);
  }

  /**
   * Send SMS via SWML — full parity with the Python reference {@code
   * signalwire.core.function_result.FunctionResult.send_sms}. Either {@code body} or {@code media}
   * must be supplied. {@code to_number} and {@code from_number} are always emitted; {@code body},
   * {@code media}, {@code tags} and {@code region} are emitted only when supplied.
   *
   * @param toNumber E.164 destination (required)
   * @param fromNumber E.164 origin (required)
   * @param body message body ({@code null} if media supplied)
   * @param media media URLs ({@code null} if body supplied)
   * @param tags tags for UI search ({@code null} to omit)
   * @param region region to originate the message from ({@code null} to omit)
   * @return this, for chaining
   * @throws IllegalArgumentException if neither body nor media is provided
   */
  public FunctionResult sendSms(
      String toNumber,
      String fromNumber,
      String body,
      List<String> media,
      List<String> tags,
      String region) {
    if ((body == null || body.isEmpty()) && (media == null || media.isEmpty())) {
      throw new IllegalArgumentException("Either body or media must be provided");
    }
    Map<String, Object> smsParams = new LinkedHashMap<>();
    smsParams.put("to_number", toNumber);
    smsParams.put("from_number", fromNumber);
    if (body != null && !body.isEmpty()) smsParams.put("body", body);
    if (media != null && !media.isEmpty()) smsParams.put("media", media);
    if (tags != null && !tags.isEmpty()) smsParams.put("tags", tags);
    if (region != null && !region.isEmpty()) smsParams.put("region", region);
    return executeSwml(
        Map.of(
            "version", "1.0.0", "sections", Map.of("main", List.of(Map.of("send_sms", smsParams)))),
        false);
  }

  /**
   * Send SMS (convenience form). Delegates to the full-arity {@link #sendSms(String, String,
   * String, List, List, String)} with no {@code region}.
   */
  public FunctionResult sendSms(
      String toNumber, String fromNumber, String body, List<String> media, List<String> tags) {
    return sendSms(toNumber, fromNumber, body, media, tags, null);
  }

  /**
   * Default {@code ai_response} for {@link #pay}. Set as a {@code set} verb ahead of the {@code
   * pay} verb so the AI relays the payment outcome via the {@code ${pay_result}} variable.
   * Caller-overridable through the full-arity {@code pay(...)} overload.
   */
  public static final String DEFAULT_PAY_AI_RESPONSE =
      "The payment status is ${pay_result}, do not mention anything else about collecting payment if successful.";

  /**
   * Process payment via SWML pay action — full parity with the Python reference {@code
   * signalwire.core.function_result.FunctionResult.pay}.
   *
   * <p>Every optional parameter the reference exposes is a positional argument here, in the same
   * order, with the same default and the same emitted wire key. The reference ALWAYS emits {@code
   * payment_connector_url}, {@code input}, {@code payment_method}, {@code timeout}, {@code
   * max_attempts}, {@code security_code}, {@code min_postal_code_length}, {@code token_type},
   * {@code currency}, {@code language}, {@code voice}, {@code valid_card_types} and {@code
   * postal_code}; {@code status_url}, {@code charge_amount}, {@code description}, {@code
   * parameters} and {@code prompts} are emitted only when supplied. Numeric values are stringified
   * to match Python's {@code str(...)}. A {@code set} verb carrying {@code ai_response} is emitted
   * before the {@code pay} verb.
   *
   * @param connectorUrl payment connector URL (required)
   * @param inputMethod "dtmf" (the SWML schema is {@code const:"dtmf"}; default "dtmf")
   * @param statusUrl URL for status-change notifications ({@code null} to omit)
   * @param paymentMethod payment method (default "credit-card")
   * @param timeout seconds to wait for next digit (default 5)
   * @param maxAttempts retry attempts (default 1)
   * @param securityCode prompt for the security code (default {@code true})
   * @param postalCode prompt for postal code ({@link Boolean}) or the literal postcode ({@link
   *     String}); default {@code Boolean.TRUE}
   * @param minPostalCodeLength minimum postal-code digits (default 0)
   * @param tokenType "one-time" or "reusable" (default "reusable")
   * @param chargeAmount amount to charge, decimal string ({@code null} to omit)
   * @param currency currency code (default "usd")
   * @param language prompt language (default "en-US")
   * @param voice TTS voice (default "woman")
   * @param description custom payment description ({@code null} to omit)
   * @param validCardTypes space-separated card types (default "visa mastercard amex")
   * @param parameters name/value pairs for the connector ({@code null} to omit)
   * @param prompts custom prompt configurations ({@code null} to omit)
   * @param aiResponse {@code ai_response} text for the leading {@code set} verb (default {@link
   *     #DEFAULT_PAY_AI_RESPONSE})
   * @return this, for chaining
   */
  public FunctionResult pay(
      String connectorUrl,
      String inputMethod,
      String statusUrl,
      String paymentMethod,
      int timeout,
      int maxAttempts,
      boolean securityCode,
      Object postalCode,
      int minPostalCodeLength,
      String tokenType,
      String chargeAmount,
      String currency,
      String language,
      String voice,
      String description,
      String validCardTypes,
      List<Map<String, String>> parameters,
      List<Map<String, Object>> prompts,
      String aiResponse) {
    Map<String, Object> payParams = new LinkedHashMap<>();
    payParams.put("payment_connector_url", connectorUrl);
    payParams.put("input", inputMethod != null ? inputMethod : "dtmf");
    payParams.put("payment_method", paymentMethod != null ? paymentMethod : "credit-card");
    payParams.put("timeout", String.valueOf(timeout));
    payParams.put("max_attempts", String.valueOf(maxAttempts));
    payParams.put("security_code", String.valueOf(securityCode).toLowerCase());
    payParams.put("min_postal_code_length", String.valueOf(minPostalCodeLength));
    payParams.put("token_type", tokenType != null ? tokenType : "reusable");
    payParams.put("currency", currency != null ? currency : "usd");
    payParams.put("language", language != null ? language : "en-US");
    payParams.put("voice", voice != null ? voice : "woman");
    payParams.put(
        "valid_card_types", validCardTypes != null ? validCardTypes : "visa mastercard amex");

    // postal_code: boolean -> "true"/"false"; otherwise the literal postcode string.
    if (postalCode instanceof Boolean b) {
      payParams.put("postal_code", String.valueOf(b).toLowerCase());
    } else if (postalCode != null) {
      payParams.put("postal_code", postalCode);
    } else {
      // Reference default is True.
      payParams.put("postal_code", "true");
    }

    // Optional params — emitted only when supplied (truthy in Python).
    if (statusUrl != null && !statusUrl.isEmpty()) payParams.put("status_url", statusUrl);
    if (chargeAmount != null && !chargeAmount.isEmpty())
      payParams.put("charge_amount", chargeAmount);
    if (description != null && !description.isEmpty()) payParams.put("description", description);
    if (parameters != null && !parameters.isEmpty()) payParams.put("parameters", parameters);
    if (prompts != null && !prompts.isEmpty()) payParams.put("prompts", prompts);

    String aiResp = aiResponse != null ? aiResponse : DEFAULT_PAY_AI_RESPONSE;
    Map<String, Object> swmlDoc = new LinkedHashMap<>();
    swmlDoc.put("version", "1.0.0");
    swmlDoc.put(
        "sections",
        Map.of(
            "main",
            List.of(
                Collections.singletonMap("set", Collections.singletonMap("ai_response", aiResp)),
                Collections.singletonMap("pay", payParams))));
    return executeSwml(swmlDoc, false);
  }

  /**
   * Process payment (convenience form). Delegates to the full-arity {@link #pay(String, String,
   * String, String, int, int, boolean, Object, int, String, String, String, String, String, String,
   * String, List, List, String)} with every other option at its reference default, so the emitted
   * SWML is identical to the reference with those defaults.
   */
  public FunctionResult pay(
      String connectorUrl, String inputMethod, String statusUrl, int timeout, int maxAttempts) {
    return pay(
        connectorUrl,
        inputMethod,
        statusUrl,
        "credit-card",
        timeout,
        maxAttempts,
        true,
        Boolean.TRUE,
        0,
        "reusable",
        null,
        "usd",
        "en-US",
        "woman",
        null,
        "visa mastercard amex",
        null,
        null,
        DEFAULT_PAY_AI_RESPONSE);
  }

  // ======== RPC Actions ========

  /** Execute an RPC method via SWML. */
  public FunctionResult executeRpc(
      String method, Map<String, Object> params, String callId, String nodeId) {
    Map<String, Object> rpcParams = new LinkedHashMap<>();
    rpcParams.put("method", method);
    if (callId != null && !callId.isEmpty()) rpcParams.put("call_id", callId);
    if (nodeId != null && !nodeId.isEmpty()) rpcParams.put("node_id", nodeId);
    if (params != null && !params.isEmpty()) rpcParams.put("params", params);
    return executeSwml(
        Map.of(
            "version",
            "1.0.0",
            "sections",
            Map.of("main", List.of(Map.of("execute_rpc", rpcParams)))),
        false);
  }

  public FunctionResult executeRpc(String method, Map<String, Object> params) {
    return executeRpc(method, params, null, null);
  }

  /**
   * Dial out to a number with a destination SWML URL — full parity with the Python reference {@code
   * FunctionResult.rpc_dial}. {@code deviceType} flows through to {@code params.devices.type}
   * (Python defaults "phone") instead of being hard-coded.
   *
   * @param toNumber E.164 number to dial (required)
   * @param fromNumber E.164 caller ID (required)
   * @param destSwml URL to the SWML handling the outbound leg (required)
   * @param deviceType device type for the dial (default "phone")
   * @return this, for chaining
   */
  public FunctionResult rpcDial(
      String toNumber, String fromNumber, String destSwml, String deviceType) {
    return executeRpc(
        "dial",
        Map.of(
            "devices",
            Map.of(
                "type",
                deviceType != null ? deviceType : "phone",
                "params",
                Map.of("to_number", toNumber, "from_number", fromNumber)),
            "dest_swml",
            destSwml));
  }

  /**
   * Dial out (convenience form). Delegates to the full-arity {@link #rpcDial(String, String,
   * String, String)} with {@code device_type} at its reference default ("phone").
   */
  public FunctionResult rpcDial(String toNumber, String fromNumber, String destSwml) {
    return rpcDial(toNumber, fromNumber, destSwml, "phone");
  }

  /**
   * Inject a message into an AI agent on another call — full parity with the Python reference
   * {@code FunctionResult.rpc_ai_message}. {@code role} flows through to {@code params.role}
   * (Python defaults "system") instead of being hard-coded.
   *
   * @param callId target call ID (required)
   * @param messageText message text to inject (required)
   * @param role message role (default "system")
   * @return this, for chaining
   */
  public FunctionResult rpcAiMessage(String callId, String messageText, String role) {
    return executeRpc(
        "ai_message",
        Map.of("role", role != null ? role : "system", "message_text", messageText),
        callId,
        null);
  }

  /**
   * Inject a message (convenience form). Delegates to the full-arity {@link #rpcAiMessage(String,
   * String, String)} with {@code role} at its reference default ("system").
   */
  public FunctionResult rpcAiMessage(String callId, String messageText) {
    return rpcAiMessage(callId, messageText, "system");
  }

  /** Unhold another call. */
  public FunctionResult rpcAiUnhold(String callId) {
    return executeRpc("ai_unhold", Map.of(), callId, null);
  }

  /** Queue simulated user input. */
  public FunctionResult simulateUserInput(String text) {
    return addAction("user_input", text);
  }

  // ======== Payment Helpers (static) ========

  public static Map<String, Object> createPaymentPrompt(
      String forSituation,
      List<Map<String, String>> payActions,
      String cardType,
      String errorType) {
    Map<String, Object> prompt = new LinkedHashMap<>();
    prompt.put("for", forSituation);
    prompt.put("actions", payActions);
    if (cardType != null && !cardType.isEmpty()) prompt.put("card_type", cardType);
    if (errorType != null && !errorType.isEmpty()) prompt.put("error_type", errorType);
    return prompt;
  }

  public static Map<String, String> createPaymentAction(String actionType, String phrase) {
    return Map.of("type", actionType, "phrase", phrase);
  }

  public static Map<String, String> createPaymentParameter(String name, String value) {
    return Map.of("name", name, "value", value);
  }

  // ======== Serialization ========

  /** Convert to the Map structure expected by SWAIG. */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<>();
    if (response != null && !response.isEmpty()) {
      result.put("response", response);
    }
    if (!actions.isEmpty()) {
      result.put("action", actions);
    }
    if (postProcess && !actions.isEmpty()) {
      result.put("post_process", true);
    }
    if (result.isEmpty()) {
      result.put("response", "Action completed.");
    }
    return result;
  }

  /** Render as JSON string. */
  public String toJson() {
    return gson.toJson(toMap());
  }

  // ======== Getters for testing ========

  public String getResponse() {
    return response;
  }

  public List<Map<String, Object>> getActions() {
    return Collections.unmodifiableList(actions);
  }

  public boolean isPostProcess() {
    return postProcess;
  }
}
