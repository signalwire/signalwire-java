/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import com.signalwire.sdk.logging.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Represents a RELAY call with 30+ methods organized by category.
 * <p>
 * Methods are grouped into:
 * <ul>
 *   <li><b>Simple fire-and-response</b>: answer, hangup, pass, hold, etc.</li>
 *   <li><b>Action-based</b>: play, record, detect, collect, etc. (return {@link Action})</li>
 *   <li><b>Connection</b>: connect, disconnect, transfer</li>
 *   <li><b>Conference</b>: joinConference, leaveConference</li>
 *   <li><b>AI</b>: ai, aiMessage, aiHold, aiUnhold, amazonBedrock</li>
 * </ul>
 *
 * Event routing: the {@link RelayClient} routes events to calls by {@code call_id}.
 * Each call maintains an {@code actions} map keyed by {@code control_id} for action events.
 */
public class Call {

    private static final Logger log = Logger.getLogger(Call.class);

    private final String callId;
    private volatile String nodeId;
    private volatile String state;
    private volatile String endReason;
    private volatile String direction;
    private volatile String tag;
    private volatile Map<String, Object> device;

    /** Actions map: control_id -> Action */
    private final ConcurrentHashMap<String, Action> actions = new ConcurrentHashMap<>();

    /** Event listeners */
    private final List<Consumer<RelayEvent>> eventListeners = new ArrayList<>();

    /** Reference back to the client for executing RPC calls */
    private volatile RelayClient client;

    public Call(String callId, String nodeId) {
        this.callId = callId;
        this.nodeId = nodeId;
        this.state = Constants.CALL_STATE_CREATED;
    }

    // ── Getters ──────────────────────────────────────────────────────

    public String getCallId() { return callId; }
    public String getNodeId() { return nodeId; }
    public String getState() { return state; }
    public String getEndReason() { return endReason; }
    public String getDirection() { return direction; }
    public String getTag() { return tag; }
    public Map<String, Object> getDevice() { return device; }

    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
    public void setState(String state) { this.state = state; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
    public void setDirection(String direction) { this.direction = direction; }
    public void setTag(String tag) { this.tag = tag; }
    public void setDevice(Map<String, Object> device) { this.device = device; }
    public void setClient(RelayClient client) { this.client = client; }

    public boolean isEnded() {
        return Constants.CALL_STATE_ENDED.equals(state);
    }

    // ── Event dispatch ───────────────────────────────────────────────

    /**
     * Register an event listener on this call.
     */
    public void on(Consumer<RelayEvent> listener) {
        eventListeners.add(listener);
    }

    /**
     * Dispatch an event to this call. Routes action events by control_id.
     */
    public void dispatchEvent(RelayEvent event) {
        String eventType = event.getEventType();

        // Route action events by control_id
        String controlId = event.getStringParam("control_id");
        if (controlId != null) {
            Action action = actions.get(controlId);
            if (action != null) {
                String actionState = event.getStringParam("state");
                // For play_and_collect: CollectAction ignores play events for
                // completion (matches the Python gotcha — play(finished)
                // arrives BEFORE the collect terminal, but only the collect
                // event should resolve).
                if (action instanceof Action.PlayAndCollectAction
                        && Constants.EVENT_CALL_PLAY.equals(eventType)) {
                    log.debug("Ignoring play event on PlayAndCollectAction control_id=%s", controlId);
                }
                // PlayAndCollectAction: a calling.call.collect event with a
                // {result: ...} payload is the resolution signal — even
                // without an explicit "state" field. (Either result-only or
                // a state event resolves; play events are ignored above.)
                else if (action instanceof Action.PlayAndCollectAction
                        && Constants.EVENT_CALL_COLLECT.equals(eventType)) {
                    if (event.getParams().containsKey("result")) {
                        action.resolve(event);
                        actions.remove(controlId);
                    } else if (actionState != null) {
                        action.updateState(actionState, event);
                        if (action.isDone()) actions.remove(controlId);
                    }
                }
                // DetectAction: resolves on the FIRST event carrying a
                // {detect: ...} payload, not on state(finished). Mirrors the
                // production server's contract: state events are noise; the
                // detect payload IS the result.
                else if (action instanceof Action.DetectAction
                        && Constants.EVENT_CALL_DETECT.equals(eventType)
                        && event.getParams().containsKey("detect")) {
                    action.resolve(event);
                    actions.remove(controlId);
                }
                else if (actionState != null) {
                    action.updateState(actionState, event);
                    if (action.isDone()) {
                        actions.remove(controlId);
                    }
                }
            }
        }

        // Update call state
        if (Constants.EVENT_CALL_STATE.equals(eventType)) {
            RelayEvent.CallStateEvent stateEvent = (RelayEvent.CallStateEvent) event;
            this.state = stateEvent.getCallState();
            if (stateEvent.getEndReason() != null) {
                this.endReason = stateEvent.getEndReason();
            }
            if (stateEvent.getNodeId() != null) {
                this.nodeId = stateEvent.getNodeId();
            }
        }

        // Notify listeners
        for (Consumer<RelayEvent> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error in call event listener", e);
            }
        }
    }

    /**
     * Resolve all pending actions (e.g., on call ended or call-gone).
     */
    public void resolveAllActions(RelayEvent event) {
        for (Action action : actions.values()) {
            action.resolve(event);
        }
        actions.clear();
    }

    // ── Action tracking ──────────────────────────────────────────────

    /**
     * Register an action by control_id.
     */
    public void registerAction(Action action) {
        actions.put(action.getControlId(), action);
    }

    /**
     * Get an action by control_id.
     */
    public Action getAction(String controlId) {
        return actions.get(controlId);
    }

    // ── Simple fire-and-response methods ─────────────────────────────

    /**
     * Answer the call.
     */
    public Map<String, Object> answer() {
        Map<String, Object> params = callParams();
        return executeOnCall(Constants.METHOD_ANSWER, params);
    }

    /**
     * Hang up the call.
     */
    public Map<String, Object> hangup() {
        return hangup(Constants.END_REASON_HANGUP);
    }

    /**
     * Hang up the call with a specific reason.
     */
    public Map<String, Object> hangup(String reason) {
        Map<String, Object> params = callParams();
        params.put("reason", reason);
        return executeOnCall(Constants.METHOD_END, params);
    }

    /**
     * Pass on an inbound call offer.
     */
    public Map<String, Object> pass() {
        return executeOnCall(Constants.METHOD_PASS, callParams());
    }

    /**
     * Put the call on hold.
     */
    public Map<String, Object> hold() {
        return executeOnCall(Constants.METHOD_HOLD, callParams());
    }

    /**
     * Take the call off hold.
     */
    public Map<String, Object> unhold() {
        return executeOnCall(Constants.METHOD_UNHOLD, callParams());
    }

    /**
     * Enable denoise on the call.
     */
    public Map<String, Object> denoise() {
        return executeOnCall(Constants.METHOD_DENOISE, callParams());
    }

    /**
     * Disable denoise on the call.
     */
    public Map<String, Object> denoiseStop() {
        return executeOnCall(Constants.METHOD_DENOISE_STOP, callParams());
    }

    /**
     * Start echo on the call.
     */
    public Map<String, Object> echo(Map<String, Object> options) {
        Map<String, Object> params = callParams();
        if (options != null) {
            params.putAll(options);
        }
        return executeOnCall(Constants.METHOD_ECHO, params);
    }

    /**
     * Transfer the call.
     */
    public Map<String, Object> transfer(String dest) {
        Map<String, Object> params = callParams();
        params.put("dest", dest);
        return executeOnCall(Constants.METHOD_TRANSFER, params);
    }

    /**
     * Bind a digit sequence to a method.
     */
    public Map<String, Object> bindDigit(String digits, String bindMethod, Map<String, Object> options) {
        Map<String, Object> params = callParams();
        params.put("digits", digits);
        params.put("bind_method", bindMethod);
        if (options != null) {
            params.putAll(options);
        }
        return executeOnCall(Constants.METHOD_BIND_DIGIT, params);
    }

    /**
     * Clear digit bindings.
     */
    public Map<String, Object> clearDigitBindings() {
        return clearDigitBindings(null);
    }

    /**
     * Clear digit bindings for a specific realm.
     */
    public Map<String, Object> clearDigitBindings(String realm) {
        Map<String, Object> params = callParams();
        if (realm != null) {
            params.put("realm", realm);
        }
        return executeOnCall(Constants.METHOD_CLEAR_DIGIT_BINDINGS, params);
    }

    /**
     * Send a user event.
     */
    public Map<String, Object> userEvent(String event) {
        Map<String, Object> params = callParams();
        if (event != null) {
            params.put("event", event);
        }
        return executeOnCall(Constants.METHOD_USER_EVENT, params);
    }

    /**
     * Start live transcription.
     */
    public Map<String, Object> liveTranscribe(Map<String, Object> action) {
        Map<String, Object> params = callParams();
        params.put("action", action);
        return executeOnCall(Constants.METHOD_LIVE_TRANSCRIBE, params);
    }

    /**
     * Start live translation.
     */
    public Map<String, Object> liveTranslate(Map<String, Object> action, Map<String, Object> options) {
        Map<String, Object> params = callParams();
        params.put("action", action);
        if (options != null) {
            params.putAll(options);
        }
        return executeOnCall(Constants.METHOD_LIVE_TRANSLATE, params);
    }

    /**
     * SIP REFER transfer.
     */
    public Map<String, Object> refer(Map<String, Object> deviceSpec, Map<String, Object> options) {
        Map<String, Object> params = callParams();
        params.put("device", deviceSpec);
        if (options != null) {
            params.putAll(options);
        }
        return executeOnCall(Constants.METHOD_REFER, params);
    }

    /**
     * Send DTMF digits.
     */
    public Map<String, Object> sendDigits(String digits) {
        String controlId = UUID.randomUUID().toString();
        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        params.put("digits", digits);
        return executeOnCall(Constants.METHOD_SEND_DIGITS, params);
    }

    // ── Connection methods ───────────────────────────────────────────

    /**
     * Connect another device to this call.
     */
    public Map<String, Object> connect(List<List<Map<String, Object>>> devices, Map<String, Object> options) {
        Map<String, Object> params = callParams();
        params.put("devices", devices);
        if (options != null) {
            params.putAll(options);
        }
        return executeOnCall(Constants.METHOD_CONNECT_CALL, params);
    }

    /**
     * Disconnect all connected calls.
     */
    public Map<String, Object> disconnect() {
        return executeOnCall(Constants.METHOD_DISCONNECT_CALL, callParams());
    }

    // ── Conference methods ───────────────────────────────────────────

    /**
     * Join a conference.
     */
    public Map<String, Object> joinConference(String name, Map<String, Object> options) {
        Map<String, Object> params = callParams();
        params.put("name", name);
        if (options != null) {
            params.putAll(options);
        }
        return executeOnCall(Constants.METHOD_JOIN_CONFERENCE, params);
    }

    /**
     * Leave a conference.
     */
    public Map<String, Object> leaveConference(String conferenceId) {
        Map<String, Object> params = callParams();
        params.put("conference_id", conferenceId);
        return executeOnCall(Constants.METHOD_LEAVE_CONFERENCE, params);
    }

    // ── Room methods ─────────────────────────────────────────────────

    /**
     * Join a room.
     */
    public Map<String, Object> joinRoom(String name, Map<String, Object> options) {
        Map<String, Object> params = callParams();
        params.put("name", name);
        if (options != null) {
            params.putAll(options);
        }
        return executeOnCall(Constants.METHOD_JOIN_ROOM, params);
    }

    /**
     * Leave a room.
     */
    public Map<String, Object> leaveRoom() {
        return executeOnCall(Constants.METHOD_LEAVE_ROOM, callParams());
    }

    // ── Queue methods ────────────────────────────────────────────────

    /**
     * Enter a queue.
     */
    public Map<String, Object> queueEnter(String queueName, Map<String, Object> options) {
        String controlId = UUID.randomUUID().toString();
        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        params.put("queue_name", queueName);
        if (options != null) {
            params.putAll(options);
        }
        return executeOnCall(Constants.METHOD_QUEUE_ENTER, params);
    }

    /**
     * Leave a queue.
     */
    public Map<String, Object> queueLeave(String queueName, Map<String, Object> options) {
        String controlId = UUID.randomUUID().toString();
        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        params.put("queue_name", queueName);
        if (options != null) {
            params.putAll(options);
        }
        return executeOnCall(Constants.METHOD_QUEUE_LEAVE, params);
    }

    // ── Action-based methods ─────────────────────────────────────────

    /**
     * Play media on the call.
     *
     * @param media list of media objects
     * @param options optional parameters (volume, direction, loop, etc.)
     * @return a PlayAction that can be waited on, paused, resumed, stopped
     */
    public Action.PlayAction play(List<Map<String, Object>> media, Map<String, Object> options) {
        String controlId = controlIdFromOptions(options);
        Action.PlayAction action = new Action.PlayAction(controlId, this);
        registerAction(action);

        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        params.put("play", media);
        if (options != null) {
            for (Map.Entry<String, Object> entry : options.entrySet()) {
                if (!"control_id".equals(entry.getKey())) {
                    params.put(entry.getKey(), entry.getValue());
                }
            }
        }

        Map<String, Object> result = executeOnCall(Constants.METHOD_PLAY, params);
        if (isCallGone(result)) {
            action.resolve(null);
        }
        return action;
    }

    /**
     * Play media on the call with default options.
     */
    public Action.PlayAction play(List<Map<String, Object>> media) {
        return play(media, (Map<String, Object>) null);
    }

    /**
     * Play media with an explicit control_id (test helper).
     */
    public Action.PlayAction play(List<Map<String, Object>> media, String controlId) {
        return play(media, mapOf("control_id", controlId));
    }

    /**
     * Record the call.
     *
     * @param recordConfig record configuration object
     * @param options optional parameters
     * @return a RecordAction
     */
    public Action.RecordAction record(Map<String, Object> recordConfig, Map<String, Object> options) {
        String controlId = controlIdFromOptions(options);
        Action.RecordAction action = new Action.RecordAction(controlId, this);
        registerAction(action);

        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        if (recordConfig != null) {
            params.put("record", recordConfig);
        }
        mergeWithoutKey(params, options, "control_id");

        Map<String, Object> result = executeOnCall(Constants.METHOD_RECORD, params);
        if (isCallGone(result)) {
            action.resolve(null);
        }
        return action;
    }

    /**
     * Record with an explicit control_id (test helper). The {@code audioConfig}
     * is wrapped as {@code record: {audio: <config>}} on the wire to match the
     * Python {@code call.record(audio=..., control_id=...)} pattern.
     */
    public Action.RecordAction recordAudio(Map<String, Object> audioConfig, String controlId) {
        Map<String, Object> recordCfg = new LinkedHashMap<>();
        recordCfg.put("audio", audioConfig != null ? audioConfig : new LinkedHashMap<>());
        return record(recordCfg, mapOf("control_id", controlId));
    }

    /**
     * Detect answering machine, fax, or digits.
     *
     * @param detectConfig detect configuration
     * @param options optional parameters (timeout, etc.)
     * @return a DetectAction
     */
    public Action.DetectAction detect(Map<String, Object> detectConfig, Map<String, Object> options) {
        String controlId = controlIdFromOptions(options);
        Action.DetectAction action = new Action.DetectAction(controlId, this);
        registerAction(action);

        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        if (detectConfig != null) {
            params.put("detect", detectConfig);
        }
        mergeWithoutKey(params, options, "control_id");

        Map<String, Object> result = executeOnCall(Constants.METHOD_DETECT, params);
        if (isCallGone(result)) {
            action.resolve(null);
        }
        return action;
    }

    /**
     * Detect with an explicit control_id (test helper). Wraps in
     * {@code detect: <config>}.
     */
    public Action.DetectAction detectWith(Map<String, Object> detectConfig, String controlId) {
        return detect(detectConfig, mapOf("control_id", controlId));
    }

    /**
     * Collect digits or speech input.
     *
     * @param collectConfig collect configuration
     * @param options optional parameters
     * @return a CollectAction
     */
    public Action.CollectAction collect(Map<String, Object> collectConfig, Map<String, Object> options) {
        String controlId = controlIdFromOptions(options);
        Action.CollectAction action = new Action.CollectAction(controlId, this);
        registerAction(action);

        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        if (collectConfig != null) {
            params.putAll(collectConfig);
        }
        mergeWithoutKey(params, options, "control_id");

        Map<String, Object> result = executeOnCall(Constants.METHOD_COLLECT, params);
        if (isCallGone(result)) {
            action.resolve(null);
        }
        return action;
    }

    /**
     * Collect digits with an explicit control_id (test helper).
     */
    public Action.CollectAction collectDigits(Map<String, Object> digitsConfig, String controlId) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        if (digitsConfig != null) cfg.put("digits", digitsConfig);
        return collect(cfg, mapOf("control_id", controlId));
    }

    /**
     * Collect with all flags (test helper for start_input_timers etc).
     */
    public Action.CollectAction collectDigits(Map<String, Object> digitsConfig, boolean startInputTimers, String controlId) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        if (digitsConfig != null) cfg.put("digits", digitsConfig);
        cfg.put("start_input_timers", startInputTimers);
        return collect(cfg, mapOf("control_id", controlId));
    }

    /**
     * Play media and collect input.
     *
     * @param media list of media objects
     * @param collectConfig collect configuration
     * @param options optional parameters
     * @return a PlayAndCollectAction
     */
    public Action.PlayAndCollectAction playAndCollect(
            List<Map<String, Object>> media,
            Map<String, Object> collectConfig,
            Map<String, Object> options) {
        String controlId = controlIdFromOptions(options);
        Action.PlayAndCollectAction action = new Action.PlayAndCollectAction(controlId, this);
        registerAction(action);

        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        params.put("play", media);
        params.put("collect", collectConfig);
        mergeWithoutKey(params, options, "control_id");

        Map<String, Object> result = executeOnCall(Constants.METHOD_PLAY_AND_COLLECT, params);
        if (isCallGone(result)) {
            action.resolve(null);
        }
        return action;
    }

    /**
     * Play and collect with an explicit control_id (test helper).
     */
    public Action.PlayAndCollectAction playAndCollect(
            List<Map<String, Object>> media,
            Map<String, Object> collectConfig,
            String controlId) {
        return playAndCollect(media, collectConfig, mapOf("control_id", controlId));
    }

    /**
     * Process payment via DTMF.
     *
     * @param paymentConnectorUrl connector URL
     * @param options optional parameters
     * @return a PayAction
     */
    public Action.PayAction pay(String paymentConnectorUrl, Map<String, Object> options) {
        String controlId = controlIdFromOptions(options);
        Action.PayAction action = new Action.PayAction(controlId, this);
        registerAction(action);

        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        params.put("payment_connector_url", paymentConnectorUrl);
        mergeWithoutKey(params, options, "control_id");

        Map<String, Object> result = executeOnCall(Constants.METHOD_PAY, params);
        if (isCallGone(result)) {
            action.resolve(null);
        }
        return action;
    }

    /**
     * Pay with an explicit control_id (test helper).
     */
    public Action.PayAction pay(String paymentConnectorUrl, String controlId) {
        return pay(paymentConnectorUrl, mapOf("control_id", controlId));
    }

    /**
     * Send a fax.
     *
     * @param documentUrl URL to PDF document
     * @param options optional parameters
     * @return a SendFaxAction
     */
    public Action.SendFaxAction sendFax(String documentUrl, Map<String, Object> options) {
        String controlId = controlIdFromOptions(options);
        Action.SendFaxAction action = new Action.SendFaxAction(controlId, this);
        registerAction(action);

        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        params.put("document", documentUrl);
        mergeWithoutKey(params, options, "control_id");

        Map<String, Object> result = executeOnCall(Constants.METHOD_SEND_FAX, params);
        if (isCallGone(result)) {
            action.resolve(null);
        }
        return action;
    }

    /**
     * Send fax with explicit identity and control_id (test helper).
     */
    public Action.SendFaxAction sendFax(String documentUrl, String identity, String controlId) {
        Map<String, Object> opts = new LinkedHashMap<>();
        if (identity != null) opts.put("identity", identity);
        opts.put("control_id", controlId);
        return sendFax(documentUrl, opts);
    }

    /**
     * Receive a fax.
     *
     * @param options optional parameters
     * @return a ReceiveFaxAction
     */
    public Action.ReceiveFaxAction receiveFax(Map<String, Object> options) {
        String controlId = controlIdFromOptions(options);
        Action.ReceiveFaxAction action = new Action.ReceiveFaxAction(controlId, this);
        registerAction(action);

        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        mergeWithoutKey(params, options, "control_id");

        Map<String, Object> result = executeOnCall(Constants.METHOD_RECEIVE_FAX, params);
        if (isCallGone(result)) {
            action.resolve(null);
        }
        return action;
    }

    /**
     * Receive fax with an explicit control_id (test helper).
     */
    public Action.ReceiveFaxAction receiveFax(String controlId) {
        return receiveFax(mapOf("control_id", controlId));
    }

    /**
     * Tap audio from the call.
     *
     * @param tapConfig tap configuration
     * @param tapDevice tap device (rtp or ws)
     * @param options optional parameters
     * @return a TapAction
     */
    public Action.TapAction tap(Map<String, Object> tapConfig, Map<String, Object> tapDevice, Map<String, Object> options) {
        String controlId = controlIdFromOptions(options);
        Action.TapAction action = new Action.TapAction(controlId, this);
        registerAction(action);

        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        params.put("tap", tapConfig);
        params.put("device", tapDevice);
        mergeWithoutKey(params, options, "control_id");

        Map<String, Object> result = executeOnCall(Constants.METHOD_TAP, params);
        if (isCallGone(result)) {
            action.resolve(null);
        }
        return action;
    }

    /**
     * Tap with an explicit control_id (test helper).
     */
    public Action.TapAction tap(Map<String, Object> tapConfig, Map<String, Object> tapDevice, String controlId) {
        return tap(tapConfig, tapDevice, mapOf("control_id", controlId));
    }

    /**
     * Start streaming audio from the call.
     *
     * @param url WebSocket URL
     * @param options optional parameters (name, codec, track, etc.)
     * @return a StreamAction
     */
    public Action.StreamAction stream(String url, Map<String, Object> options) {
        String controlId = controlIdFromOptions(options);
        Action.StreamAction action = new Action.StreamAction(controlId, this);
        registerAction(action);

        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        params.put("url", url);
        mergeWithoutKey(params, options, "control_id");

        Map<String, Object> result = executeOnCall(Constants.METHOD_STREAM, params);
        if (isCallGone(result)) {
            action.resolve(null);
        }
        return action;
    }

    /**
     * Stream with an explicit control_id (test helper).
     */
    public Action.StreamAction stream(String url, String controlId) {
        return stream(url, mapOf("control_id", controlId));
    }

    /**
     * Stream with codec and control_id (test helper).
     */
    public Action.StreamAction stream(String url, String codec, String controlId) {
        Map<String, Object> opts = new LinkedHashMap<>();
        if (codec != null) opts.put("codec", codec);
        opts.put("control_id", controlId);
        return stream(url, opts);
    }

    /**
     * Start transcription on the call.
     *
     * @param options optional parameters
     * @return a TranscribeAction
     */
    public Action.TranscribeAction transcribe(Map<String, Object> options) {
        String controlId = controlIdFromOptions(options);
        Action.TranscribeAction action = new Action.TranscribeAction(controlId, this);
        registerAction(action);

        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        mergeWithoutKey(params, options, "control_id");

        Map<String, Object> result = executeOnCall(Constants.METHOD_TRANSCRIBE, params);
        if (isCallGone(result)) {
            action.resolve(null);
        }
        return action;
    }

    /**
     * Transcribe with an explicit control_id (test helper).
     */
    public Action.TranscribeAction transcribe(String controlId) {
        return transcribe(mapOf("control_id", controlId));
    }

    /**
     * Start an AI agent on the call.
     *
     * @param aiConfig AI configuration
     * @return an AiAction
     */
    public Action.AiAction ai(Map<String, Object> aiConfig) {
        String controlId = controlIdFromOptions(aiConfig);
        Action.AiAction action = new Action.AiAction(controlId, this);
        registerAction(action);

        Map<String, Object> params = callParams();
        params.put("control_id", controlId);
        mergeWithoutKey(params, aiConfig, "control_id");

        Map<String, Object> result = executeOnCall(Constants.METHOD_AI, params);
        if (isCallGone(result)) {
            action.resolve(null);
        }
        return action;
    }

    /**
     * AI with explicit prompt and control_id (test helper).
     */
    public Action.AiAction ai(Map<String, Object> prompt, String controlId) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        if (prompt != null) cfg.put("prompt", prompt);
        cfg.put("control_id", controlId);
        return ai(cfg);
    }

    // ── AI helper methods ────────────────────────────────────────────

    /**
     * Start Amazon Bedrock AI on the call.
     */
    public Map<String, Object> amazonBedrock(Map<String, Object> config) {
        Map<String, Object> params = callParams();
        if (config != null) {
            params.putAll(config);
        }
        return executeOnCall(Constants.METHOD_AMAZON_BEDROCK, params);
    }

    /**
     * Send a message to an active AI session.
     */
    public Map<String, Object> aiMessage(Map<String, Object> messageConfig) {
        Map<String, Object> params = callParams();
        if (messageConfig != null) {
            params.putAll(messageConfig);
        }
        return executeOnCall(Constants.METHOD_AI_MESSAGE, params);
    }

    /**
     * Put AI on hold.
     */
    public Map<String, Object> aiHold(Map<String, Object> options) {
        Map<String, Object> params = callParams();
        if (options != null) {
            params.putAll(options);
        }
        return executeOnCall(Constants.METHOD_AI_HOLD, params);
    }

    /**
     * Resume AI from hold.
     */
    public Map<String, Object> aiUnhold(Map<String, Object> options) {
        Map<String, Object> params = callParams();
        if (options != null) {
            params.putAll(options);
        }
        return executeOnCall(Constants.METHOD_AI_UNHOLD, params);
    }

    // ── Internal ─────────────────────────────────────────────────────

    /**
     * Execute an RPC method on this call, handling 404/410 gracefully.
     */
    Map<String, Object> executeOnCall(String method, Map<String, Object> params) {
        if (client == null) {
            log.warn("No client attached to call %s, cannot execute %s", callId, method);
            return Collections.emptyMap();
        }
        return client.executeOnCall(method, params);
    }

    private Map<String, Object> callParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("node_id", nodeId);
        params.put("call_id", callId);
        return params;
    }

    private boolean isCallGone(Map<String, Object> result) {
        if (result == null) return false;
        Object code = result.get("code");
        return code != null && Constants.isCallGoneCode(code.toString());
    }

    private static String controlIdFromOptions(Map<String, Object> options) {
        if (options != null) {
            Object cid = options.get("control_id");
            if (cid instanceof String && !((String) cid).isEmpty()) {
                return (String) cid;
            }
        }
        return UUID.randomUUID().toString();
    }

    private static Map<String, Object> mapOf(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    private static void mergeWithoutKey(Map<String, Object> target, Map<String, Object> src, String excludeKey) {
        if (src == null) return;
        for (Map.Entry<String, Object> entry : src.entrySet()) {
            if (!excludeKey.equals(entry.getKey())) {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public String toString() {
        return String.format("Call{id=%s, state=%s, direction=%s}", callId, state, direction);
    }
}
