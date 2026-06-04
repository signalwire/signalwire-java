/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swaig;

import com.google.gson.Gson;
import com.signalwire.sdk.swml.RecordFormat;

import java.util.*;

/**
 * SwaigFunctionResult - the response builder returned by tool handlers.
 * <p>
 * Every method returns {@code this} for fluent chaining. Contains 40+ action methods
 * covering call control, state management, media, speech/AI config, and advanced features.
 * <p>
 * Serialization rules:
 * <ul>
 *   <li>response - always included (string)</li>
 *   <li>action - only included if at least one action exists (array of objects)</li>
 *   <li>post_process - only included if true and actions exist (boolean)</li>
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

    /**
     * Add a single action to the response.
     */
    public FunctionResult addAction(String name, Object data) {
        Map<String, Object> actionMap = new LinkedHashMap<>();
        actionMap.put(name, data);
        actions.add(actionMap);
        return this;
    }

    /**
     * Add multiple actions.
     */
    public FunctionResult addActions(List<Map<String, Object>> actionList) {
        actions.addAll(actionList);
        return this;
    }

    // ======== Call Control ========

    /**
     * Connect/transfer the call to another destination via SWML connect verb.
     */
    public FunctionResult connect(String destination, boolean isFinal, String from) {
        Map<String, Object> connectParams = new LinkedHashMap<>();
        connectParams.put("to", destination);
        if (from != null && !from.isEmpty()) {
            connectParams.put("from", from);
        }

        Map<String, Object> swmlAction = new LinkedHashMap<>();
        swmlAction.put("SWML", Map.of(
                "sections", Map.of("main", List.of(Map.of("connect", connectParams))),
                "version", "1.0.0"
        ));
        swmlAction.put("transfer", String.valueOf(isFinal).toLowerCase());
        actions.add(swmlAction);
        return this;
    }

    public FunctionResult connect(String destination, boolean isFinal) {
        return connect(destination, isFinal, null);
    }

    /**
     * SWML transfer with AI response setup.
     */
    public FunctionResult swmlTransfer(String dest, String aiResponse, boolean isFinal) {
        Map<String, Object> swmlAction = new LinkedHashMap<>();
        swmlAction.put("SWML", Map.of(
                "version", "1.0.0",
                "sections", Map.of("main", List.of(
                        Map.of("set", Map.of("ai_response", aiResponse)),
                        Map.of("transfer", Map.of("dest", dest))
                ))
        ));
        swmlAction.put("transfer", String.valueOf(isFinal).toLowerCase());
        actions.add(swmlAction);
        return this;
    }

    /**
     * Hangup the call.
     */
    public FunctionResult hangup() {
        return addAction("hangup", true);
    }

    /**
     * Put the call on hold (timeout clamped 0-900).
     */
    public FunctionResult hold(int timeout) {
        return addAction("hold", Math.max(0, Math.min(timeout, 900)));
    }

    /**
     * Control how agent waits for user input.
     */
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

    /**
     * Stop the agent execution.
     */
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

    /**
     * Send a user event through SWML.
     */
    public FunctionResult swmlUserEvent(Map<String, Object> eventData) {
        Map<String, Object> swmlDoc = new LinkedHashMap<>();
        swmlDoc.put("sections", Map.of("main", List.of(
                Map.of("user_event", Map.of("event", eventData))
        )));
        swmlDoc.put("version", "1.0.0");
        return addAction("SWML", swmlDoc);
    }

    /**
     * Change the conversation step.
     */
    public FunctionResult swmlChangeStep(String stepName) {
        return addAction("change_step", stepName);
    }

    /**
     * Change the conversation context.
     */
    public FunctionResult swmlChangeContext(String contextName) {
        return addAction("change_context", contextName);
    }

    /**
     * Switch context with optional reset parameters.
     */
    public FunctionResult switchContext(String systemPrompt, String userPrompt,
                                       boolean consolidate, boolean fullReset) {
        if (systemPrompt != null && !systemPrompt.isEmpty()
                && (userPrompt == null || userPrompt.isEmpty())
                && !consolidate && !fullReset) {
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

    /**
     * Replace tool_call+result pair in conversation history.
     */
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

    /**
     * Play audio file in background.
     */
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
     * Start background call recording via SWML.
     */
    public FunctionResult recordCall(String controlId, boolean stereo, String format, String direction) {
        Map<String, Object> recordParams = new LinkedHashMap<>();
        recordParams.put("stereo", stereo);
        recordParams.put("format", format != null ? format : "wav");
        recordParams.put("direction", direction != null ? direction : "both");
        if (controlId != null && !controlId.isEmpty()) {
            recordParams.put("control_id", controlId);
        }
        return executeSwml(Map.of(
                "version", "1.0.0",
                "sections", Map.of("main", List.of(Map.of("record_call", recordParams)))
        ), false);
    }

    /**
     * Typed overload of {@link #recordCall(String, boolean, String, String)}.
     * Accepts a {@link RecordFormat} so a misspelled container format fails at
     * compile time instead of being rejected by the server. Delegates to the
     * string path via {@link RecordFormat#getValue()}, so the SWML
     * {@code record_call} payload is identical.
     */
    public FunctionResult recordCall(String controlId, boolean stereo, RecordFormat format, String direction) {
        return recordCall(controlId, stereo, format != null ? format.getValue() : null, direction);
    }

    public FunctionResult recordCall() {
        return recordCall(null, false, "wav", "both");
    }

    /**
     * Stop an active background call recording.
     */
    public FunctionResult stopRecordCall(String controlId) {
        Map<String, Object> stopParams = new LinkedHashMap<>();
        if (controlId != null && !controlId.isEmpty()) {
            stopParams.put("control_id", controlId);
        }
        return executeSwml(Map.of(
                "version", "1.0.0",
                "sections", Map.of("main", List.of(Map.of("stop_record_call", stopParams)))
        ), false);
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

    /**
     * Toggle specific SWAIG functions on/off.
     */
    public FunctionResult toggleFunctions(List<Map<String, Object>> toggles) {
        return addAction("toggle_functions", toggles);
    }

    public FunctionResult enableFunctionsOnTimeout(boolean enabled) {
        return addAction("functions_on_speaker_timeout", enabled);
    }

    public FunctionResult enableExtensiveData(boolean enabled) {
        return addAction("extensive_data", enabled);
    }

    /**
     * Update agent runtime settings (temperature, top_p, etc.).
     */
    public FunctionResult updateSettings(Map<String, Object> settings) {
        return addAction("settings", settings);
    }

    // ======== Advanced Features ========

    /**
     * Execute SWML content with optional transfer behavior.
     */
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
     * Join a conference via SWML.
     */
    public FunctionResult joinConference(String name, boolean muted, String beep, String holdAudio) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        if (muted) params.put("muted", true);
        if (beep != null && !beep.equals("true")) params.put("beep", beep);
        if (holdAudio != null) params.put("wait_url", holdAudio);
        return executeSwml(Map.of(
                "version", "1.0.0",
                "sections", Map.of("main", List.of(Map.of("join_conference", params)))
        ), false);
    }

    /**
     * Join a conference (simple form, name only).
     */
    public FunctionResult joinConference(String name) {
        return executeSwml(Map.of(
                "version", "1.0.0",
                "sections", Map.of("main", List.of(Map.of("join_conference", name)))
        ), false);
    }

    /**
     * Join a RELAY room.
     */
    public FunctionResult joinRoom(String name) {
        return executeSwml(Map.of(
                "version", "1.0.0",
                "sections", Map.of("main", List.of(Map.of("join_room", Map.of("name", name))))
        ), false);
    }

    /**
     * Send SIP REFER.
     */
    public FunctionResult sipRefer(String toUri) {
        return executeSwml(Map.of(
                "version", "1.0.0",
                "sections", Map.of("main", List.of(Map.of("sip_refer", Map.of("to_uri", toUri))))
        ), false);
    }

    /**
     * Start call tap.
     */
    public FunctionResult tap(String uri, String controlId, String direction, String codec) {
        Map<String, Object> tapParams = new LinkedHashMap<>();
        tapParams.put("uri", uri);
        if (controlId != null && !controlId.isEmpty()) tapParams.put("control_id", controlId);
        if (direction != null && !direction.equals("both")) tapParams.put("direction", direction);
        if (codec != null && !codec.equals("PCMU")) tapParams.put("codec", codec);
        return executeSwml(Map.of(
                "version", "1.0.0",
                "sections", Map.of("main", List.of(Map.of("tap", tapParams)))
        ), false);
    }

    /**
     * Stop a tap stream.
     */
    public FunctionResult stopTap(String controlId) {
        Map<String, Object> stopParams = new LinkedHashMap<>();
        if (controlId != null && !controlId.isEmpty()) {
            stopParams.put("control_id", controlId);
        }
        return executeSwml(Map.of(
                "version", "1.0.0",
                "sections", Map.of("main", List.of(Map.of("stop_tap", stopParams)))
        ), false);
    }

    public FunctionResult stopTap() {
        return stopTap(null);
    }

    /**
     * Send SMS via SWML.
     */
    public FunctionResult sendSms(String toNumber, String fromNumber, String body,
                                  List<String> media, List<String> tags) {
        if ((body == null || body.isEmpty()) && (media == null || media.isEmpty())) {
            throw new IllegalArgumentException("Either body or media must be provided");
        }
        Map<String, Object> smsParams = new LinkedHashMap<>();
        smsParams.put("to_number", toNumber);
        smsParams.put("from_number", fromNumber);
        if (body != null && !body.isEmpty()) smsParams.put("body", body);
        if (media != null && !media.isEmpty()) smsParams.put("media", media);
        if (tags != null && !tags.isEmpty()) smsParams.put("tags", tags);
        return executeSwml(Map.of(
                "version", "1.0.0",
                "sections", Map.of("main", List.of(Map.of("send_sms", smsParams)))
        ), false);
    }

    /**
     * Process payment via SWML pay action.
     */
    public FunctionResult pay(String connectorUrl, String inputMethod, String statusUrl,
                              int timeout, int maxAttempts) {
        Map<String, Object> payParams = new LinkedHashMap<>();
        payParams.put("payment_connector_url", connectorUrl);
        payParams.put("input", inputMethod != null ? inputMethod : "dtmf");
        payParams.put("timeout", String.valueOf(timeout));
        payParams.put("max_attempts", String.valueOf(maxAttempts));
        if (statusUrl != null && !statusUrl.isEmpty()) {
            payParams.put("status_url", statusUrl);
        }
        return executeSwml(Map.of(
                "version", "1.0.0",
                "sections", Map.of("main", List.of(
                        Map.of("set", Map.of("ai_response",
                                "The payment status is ${pay_result}, do not mention anything else about collecting payment if successful.")),
                        Map.of("pay", payParams)
                ))
        ), false);
    }

    // ======== RPC Actions ========

    /**
     * Execute an RPC method via SWML.
     */
    public FunctionResult executeRpc(String method, Map<String, Object> params,
                                     String callId, String nodeId) {
        Map<String, Object> rpcParams = new LinkedHashMap<>();
        rpcParams.put("method", method);
        if (callId != null && !callId.isEmpty()) rpcParams.put("call_id", callId);
        if (nodeId != null && !nodeId.isEmpty()) rpcParams.put("node_id", nodeId);
        if (params != null && !params.isEmpty()) rpcParams.put("params", params);
        return executeSwml(Map.of(
                "version", "1.0.0",
                "sections", Map.of("main", List.of(Map.of("execute_rpc", rpcParams)))
        ), false);
    }

    public FunctionResult executeRpc(String method, Map<String, Object> params) {
        return executeRpc(method, params, null, null);
    }

    /**
     * Dial out to a number with a destination SWML URL.
     */
    public FunctionResult rpcDial(String toNumber, String fromNumber, String destSwml) {
        return executeRpc("dial", Map.of(
                "devices", Map.of(
                        "type", "phone",
                        "params", Map.of("to_number", toNumber, "from_number", fromNumber)
                ),
                "dest_swml", destSwml
        ));
    }

    /**
     * Inject a message into an AI agent on another call.
     */
    public FunctionResult rpcAiMessage(String callId, String messageText) {
        return executeRpc("ai_message",
                Map.of("role", "system", "message_text", messageText),
                callId, null);
    }

    /**
     * Unhold another call.
     */
    public FunctionResult rpcAiUnhold(String callId) {
        return executeRpc("ai_unhold", Map.of(), callId, null);
    }

    /**
     * Queue simulated user input.
     */
    public FunctionResult simulateUserInput(String text) {
        return addAction("user_input", text);
    }

    // ======== Payment Helpers (static) ========

    public static Map<String, Object> createPaymentPrompt(String forSituation,
                                                          List<Map<String, String>> payActions,
                                                          String cardType, String errorType) {
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

    /**
     * Convert to the Map structure expected by SWAIG.
     */
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

    /**
     * Render as JSON string.
     */
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
