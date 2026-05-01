/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST-based call control. All commands are dispatched as
 * {@code POST /api/calling/calls} with a {@code "command"} field that names
 * the operation, optionally an {@code "id"} field that names the target call,
 * and a {@code "params"} object containing keyword arguments. The mock
 * server (and the real Calling API) matches commands against the registered
 * routes via the {@code command} field.
 */
public class CallingNamespace {

    private final HttpClient httpClient;
    private final CrudResource calls;
    private static final String BASE_PATH = "/calling/calls";

    public CallingNamespace(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.calls = new CrudResource(httpClient, BASE_PATH);
    }

    public CrudResource calls() { return calls; }

    /**
     * Dispatches a calling command. {@code callId} may be null when the
     * command targets the resource collection (e.g. {@code dial} /
     * {@code update}); {@code params} is a kwargs-shaped map.
     */
    private Map<String, Object> execute(String command, String callId, Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("command", command);
        if (callId != null) {
            body.put("id", callId);
        }
        body.put("params", params == null ? new HashMap<>() : params);
        return httpClient.post(BASE_PATH, body);
    }

    // ── Lifecycle ────────────────────────────────────────────────────

    public Map<String, Object> dial(Map<String, Object> params) {
        return execute("dial", null, params);
    }

    public Map<String, Object> update(Map<String, Object> params) {
        return execute("update", null, params);
    }

    public Map<String, Object> end(String callId, Map<String, Object> params) {
        return execute("calling.end", callId, params);
    }

    public Map<String, Object> transfer(String callId, Map<String, Object> params) {
        return execute("calling.transfer", callId, params);
    }

    public Map<String, Object> disconnect(String callId, Map<String, Object> params) {
        return execute("calling.disconnect", callId, params);
    }

    // ── Play ─────────────────────────────────────────────────────────

    public Map<String, Object> play(String callId, Map<String, Object> params) {
        return execute("calling.play", callId, params);
    }

    public Map<String, Object> playPause(String callId, Map<String, Object> params) {
        return execute("calling.play.pause", callId, params);
    }

    public Map<String, Object> playResume(String callId, Map<String, Object> params) {
        return execute("calling.play.resume", callId, params);
    }

    public Map<String, Object> playStop(String callId, Map<String, Object> params) {
        return execute("calling.play.stop", callId, params);
    }

    public Map<String, Object> playVolume(String callId, Map<String, Object> params) {
        return execute("calling.play.volume", callId, params);
    }

    // ── Record ───────────────────────────────────────────────────────

    public Map<String, Object> record(String callId, Map<String, Object> params) {
        return execute("calling.record", callId, params);
    }

    public Map<String, Object> recordPause(String callId, Map<String, Object> params) {
        return execute("calling.record.pause", callId, params);
    }

    public Map<String, Object> recordResume(String callId, Map<String, Object> params) {
        return execute("calling.record.resume", callId, params);
    }

    public Map<String, Object> recordStop(String callId, Map<String, Object> params) {
        return execute("calling.record.stop", callId, params);
    }

    // ── Collect ──────────────────────────────────────────────────────

    public Map<String, Object> collect(String callId, Map<String, Object> params) {
        return execute("calling.collect", callId, params);
    }

    public Map<String, Object> collectStop(String callId, Map<String, Object> params) {
        return execute("calling.collect.stop", callId, params);
    }

    public Map<String, Object> collectStartInputTimers(String callId, Map<String, Object> params) {
        return execute("calling.collect.start_input_timers", callId, params);
    }

    // ── Detect ───────────────────────────────────────────────────────

    public Map<String, Object> detect(String callId, Map<String, Object> params) {
        return execute("calling.detect", callId, params);
    }

    public Map<String, Object> detectStop(String callId, Map<String, Object> params) {
        return execute("calling.detect.stop", callId, params);
    }

    // ── Tap ──────────────────────────────────────────────────────────

    public Map<String, Object> tap(String callId, Map<String, Object> params) {
        return execute("calling.tap", callId, params);
    }

    public Map<String, Object> tapStop(String callId, Map<String, Object> params) {
        return execute("calling.tap.stop", callId, params);
    }

    // ── Stream ───────────────────────────────────────────────────────

    public Map<String, Object> stream(String callId, Map<String, Object> params) {
        return execute("calling.stream", callId, params);
    }

    public Map<String, Object> streamStop(String callId, Map<String, Object> params) {
        return execute("calling.stream.stop", callId, params);
    }

    // ── Denoise ──────────────────────────────────────────────────────

    public Map<String, Object> denoise(String callId, Map<String, Object> params) {
        return execute("calling.denoise", callId, params);
    }

    public Map<String, Object> denoiseStop(String callId, Map<String, Object> params) {
        return execute("calling.denoise.stop", callId, params);
    }

    // ── Transcribe ───────────────────────────────────────────────────

    public Map<String, Object> transcribe(String callId, Map<String, Object> params) {
        return execute("calling.transcribe", callId, params);
    }

    public Map<String, Object> transcribeStop(String callId, Map<String, Object> params) {
        return execute("calling.transcribe.stop", callId, params);
    }

    // ── AI ───────────────────────────────────────────────────────────

    public Map<String, Object> aiMessage(String callId, Map<String, Object> params) {
        return execute("calling.ai_message", callId, params);
    }

    public Map<String, Object> aiHold(String callId, Map<String, Object> params) {
        return execute("calling.ai_hold", callId, params);
    }

    public Map<String, Object> aiUnhold(String callId, Map<String, Object> params) {
        return execute("calling.ai_unhold", callId, params);
    }

    public Map<String, Object> aiStop(String callId, Map<String, Object> params) {
        return execute("calling.ai.stop", callId, params);
    }

    // ── Live transcribe / translate ───────────────────────────────────

    public Map<String, Object> liveTranscribe(String callId, Map<String, Object> params) {
        return execute("calling.live_transcribe", callId, params);
    }

    public Map<String, Object> liveTranslate(String callId, Map<String, Object> params) {
        return execute("calling.live_translate", callId, params);
    }

    // ── Fax ──────────────────────────────────────────────────────────

    public Map<String, Object> sendFaxStop(String callId, Map<String, Object> params) {
        return execute("calling.send_fax.stop", callId, params);
    }

    public Map<String, Object> receiveFaxStop(String callId, Map<String, Object> params) {
        return execute("calling.receive_fax.stop", callId, params);
    }

    // ── SIP ──────────────────────────────────────────────────────────

    public Map<String, Object> refer(String callId, Map<String, Object> params) {
        return execute("calling.refer", callId, params);
    }

    // ── Custom events ────────────────────────────────────────────────

    public Map<String, Object> userEvent(String callId, Map<String, Object> params) {
        return execute("calling.user_event", callId, params);
    }
}
