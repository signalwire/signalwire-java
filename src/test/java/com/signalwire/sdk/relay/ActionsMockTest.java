/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-relay-backed unit tests translated from
 * {@code signalwire-python/tests/unit/relay/test_actions_mock.py}.
 *
 * <p>For each major action (Play, Record, Detect, Collect, PlayAndCollect,
 * Pay, SendFax, ReceiveFax, Tap, Stream, Transcribe, AI), drive the SDK
 * against the mock and assert:
 * <ol>
 *   <li>The on-wire calling.&lt;verb&gt; frame carries call_id/control_id.</li>
 *   <li>Mock-pushed state events progress the action.</li>
 *   <li>Terminal state events resolve action.waitForCompletion().</li>
 *   <li>action.stop() (and pause/resume/volume) journal sub-command frames.</li>
 *   <li>The play_and_collect gotcha — only collect events resolve.</li>
 *   <li>The detect gotcha — detect resolves on first detect payload.</li>
 * </ol>
 */
class ActionsMockTest {

    private RelayClient client;
    private RelayMockTest.Harness mock;

    @BeforeEach
    void setUp() {
        this.mock = RelayMockTest.harness();
        this.client = RelayClient.builder()
                .project("test_proj")
                .token("test_tok")
                .space(mock.wsUrl())
                .contexts(List.of("default"))
                .build();
        client.connect(10_000);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            try { client.disconnect(); } catch (Exception ignored) {}
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /**
     * Push an inbound call, capture it from the on_call handler, answer it,
     * mark it as answered, return it.
     */
    private Call answeredInboundCall(String callId) throws Exception {
        AtomicReference<Call> capturedRef = new AtomicReference<>();
        CompletableFuture<Void> handlerReturned = new CompletableFuture<>();
        client.onCall(call -> {
            capturedRef.set(call);
            call.answer();
            handlerReturned.complete(null);
        });
        mock.inboundCall(new RelayMockTest.InboundCallSpec().callId(callId));
        handlerReturned.get(5, TimeUnit.SECONDS);
        Call call = capturedRef.get();
        assertNotNull(call);
        // Mark as answered so subsequent actions don't think it's gone.
        call.setState("answered");
        return call;
    }

    private static Map<String, Object> bareEventFrame(String eventType, Map<String, Object> params) {
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("event_type", eventType);
        outer.put("params", params);
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", Constants.JSONRPC_VERSION);
        frame.put("id", UUID.randomUUID().toString());
        frame.put("method", "signalwire.event");
        frame.put("params", outer);
        return frame;
    }

    private static Map<String, Object> tts(String text) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "tts");
        p.put("params", Map.of("text", text));
        return p;
    }

    private static Map<String, Object> silence(int duration) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "silence");
        p.put("params", Map.of("duration", duration));
        return p;
    }

    private static Map<String, Object> armEvent(Map<String, Object> emit, int delayMs) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("emit", emit);
        e.put("delay_ms", delayMs);
        return e;
    }

    // ── PlayAction ──────────────────────────────────────────────────

    @Test
    @DisplayName("play() journals calling.play with control_id and tts payload")
    void playJournalsCallingPlay() throws Exception {
        Call call = answeredInboundCall("call-play");
        call.play(List.of(tts("hi")), "play-ctl-1");

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_PLAY);
        assertEquals(1, entries.size());
        Map<String, Object> p = entries.get(0).params();
        assertEquals("call-play", p.get("call_id"));
        assertEquals("play-ctl-1", p.get("control_id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> playList = (List<Map<String, Object>>) p.get("play");
        assertEquals("tts", playList.get(0).get("type"));
    }

    @Test
    @DisplayName("play() resolves on finished event (armed scenario)")
    void playResolvesOnFinishedEvent() throws Exception {
        Call call = answeredInboundCall("call-play-fin");
        mock.armMethod(Constants.METHOD_PLAY, List.of(
                armEvent(Map.of("state", "playing"), 1),
                armEvent(Map.of("state", "finished"), 5)));

        Action.PlayAction action = call.play(List.of(silence(1)), "play-ctl-fin");
        RelayEvent event = action.waitForCompletion(5_000);
        assertNotNull(event);
        assertTrue(action.isDone());
        assertEquals("finished", event.getStringParam("state"));
    }

    @Test
    @DisplayName("play.stop() journals calling.play.stop")
    void playStopJournalsPlayStop() throws Exception {
        Call call = answeredInboundCall("call-play-stop");
        Action.PlayAction action = call.play(List.of(silence(60)), "play-ctl-stop");
        action.stop();

        List<RelayMockTest.JournalEntry> stops =
                mock.journalRecv(Constants.METHOD_PLAY_STOP);
        assertFalse(stops.isEmpty(), "no calling.play.stop frame");
        assertEquals("play-ctl-stop",
                stops.get(stops.size() - 1).params().get("control_id"));
    }

    @Test
    @DisplayName("play.pause/resume/volume journal sub-command frames")
    void playPauseResumeVolumeJournal() throws Exception {
        Call call = answeredInboundCall("call-play-prv");
        Action.PlayAction action = call.play(List.of(silence(60)), "play-ctl-prv");
        action.pause();
        action.resume();
        action.volume(-3.0);

        assertFalse(mock.journalRecv(Constants.METHOD_PLAY_PAUSE).isEmpty(),
                "no calling.play.pause");
        assertFalse(mock.journalRecv(Constants.METHOD_PLAY_RESUME).isEmpty(),
                "no calling.play.resume");
        List<RelayMockTest.JournalEntry> vols =
                mock.journalRecv(Constants.METHOD_PLAY_VOLUME);
        assertFalse(vols.isEmpty(), "no calling.play.volume");
        Object vol = vols.get(vols.size() - 1).params().get("volume");
        assertEquals(-3.0, ((Number) vol).doubleValue());
    }

    @Test
    @DisplayName("play onCompleted callback fires on terminal event")
    void playOnCompletedCallbackFires() throws Exception {
        Call call = answeredInboundCall("call-play-cb");
        mock.armMethod(Constants.METHOD_PLAY, List.of(
                armEvent(Map.of("state", "finished"), 1)));

        CompletableFuture<RelayEvent> callbackFired = new CompletableFuture<>();
        Action.PlayAction action = call.play(List.of(silence(1)), "play-ctl-cb");
        action.setOnCompleted(a -> callbackFired.complete(a.getResult()));

        action.waitForCompletion(5_000);
        RelayEvent event = callbackFired.get(2, TimeUnit.SECONDS);
        assertEquals("finished", event.getStringParam("state"));
    }

    // ── RecordAction ──────────────────────────────────────────────

    @Test
    @DisplayName("record() journals calling.record with audio config")
    void recordJournalsCallingRecord() throws Exception {
        Call call = answeredInboundCall("call-rec");
        call.recordAudio(Map.of("format", "mp3"), "rec-ctl-1");

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_RECORD);
        assertEquals(1, entries.size());
        Map<String, Object> p = entries.get(0).params();
        assertEquals("call-rec", p.get("call_id"));
        assertEquals("rec-ctl-1", p.get("control_id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> record = (Map<String, Object>) p.get("record");
        @SuppressWarnings("unchecked")
        Map<String, Object> audio = (Map<String, Object>) record.get("audio");
        assertEquals("mp3", audio.get("format"));
    }

    @Test
    @DisplayName("record() resolves on finished event")
    void recordResolvesOnFinished() throws Exception {
        Call call = answeredInboundCall("call-rec-fin");
        mock.armMethod(Constants.METHOD_RECORD, List.of(
                armEvent(Map.of("state", "recording"), 1),
                armEvent(Map.of("state", "finished", "url", "http://r.wav"), 5)));

        Action.RecordAction action = call.recordAudio(Map.of("format", "wav"), "rec-ctl-fin");
        RelayEvent event = action.waitForCompletion(5_000);
        assertNotNull(event);
        assertEquals("finished", event.getStringParam("state"));
    }

    @Test
    @DisplayName("record.stop() journals calling.record.stop")
    void recordStopJournalsRecordStop() throws Exception {
        Call call = answeredInboundCall("call-rec-stop");
        Action.RecordAction action = call.recordAudio(Map.of("format", "wav"), "rec-ctl-stop");
        action.stop();

        List<RelayMockTest.JournalEntry> stops =
                mock.journalRecv(Constants.METHOD_RECORD_STOP);
        assertFalse(stops.isEmpty());
        assertEquals("rec-ctl-stop",
                stops.get(stops.size() - 1).params().get("control_id"));
    }

    // ── DetectAction — gotcha: resolves on first detect payload ──

    @Test
    @DisplayName("detect resolves on first detect payload, not on state(finished)")
    void detectResolvesOnFirstDetectPayload() throws Exception {
        Call call = answeredInboundCall("call-det");
        // Arm: first event has detect payload (should resolve), second state=finished.
        Map<String, Object> detectInner = new LinkedHashMap<>();
        detectInner.put("type", "machine");
        detectInner.put("params", Map.of("event", "MACHINE"));
        mock.armMethod(Constants.METHOD_DETECT, List.of(
                armEvent(Map.of("detect", detectInner), 1),
                armEvent(Map.of("state", "finished"), 10)));

        Action.DetectAction action = call.detectWith(
                Map.of("type", "machine", "params", new HashMap<>()), "det-ctl-1");
        RelayEvent event = action.waitForCompletion(5_000);
        assertNotNull(event);
        @SuppressWarnings("unchecked")
        Map<String, Object> detect = (Map<String, Object>) event.getParams().get("detect");
        assertEquals("machine", detect.get("type"));
    }

    @Test
    @DisplayName("detect.stop() journals calling.detect.stop")
    void detectStopJournalsDetectStop() throws Exception {
        Call call = answeredInboundCall("call-det-stop");
        Action.DetectAction action = call.detectWith(
                Map.of("type", "fax", "params", new HashMap<>()), "det-stop");
        action.stop();

        List<RelayMockTest.JournalEntry> stops =
                mock.journalRecv(Constants.METHOD_DETECT_STOP);
        assertFalse(stops.isEmpty());
        assertEquals("det-stop",
                stops.get(stops.size() - 1).params().get("control_id"));
    }

    // ── PlayAndCollectAction — gotcha: ignore play(finished) ─────

    @Test
    @DisplayName("play_and_collect journals calling.play_and_collect")
    void playAndCollectJournals() throws Exception {
        Call call = answeredInboundCall("call-pac");
        call.playAndCollect(List.of(tts("Press 1")),
                Map.of("digits", Map.of("max", 1)),
                "pac-ctl-1");

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_PLAY_AND_COLLECT);
        assertEquals(1, entries.size());
        Map<String, Object> p = entries.get(0).params();
        assertEquals("call-pac", p.get("call_id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> playList = (List<Map<String, Object>>) p.get("play");
        assertEquals("tts", playList.get(0).get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> collect = (Map<String, Object>) p.get("collect");
        @SuppressWarnings("unchecked")
        Map<String, Object> digits = (Map<String, Object>) collect.get("digits");
        assertEquals(1.0, ((Number) digits.get("max")).doubleValue());
    }

    @Test
    @DisplayName("play_and_collect ignores play(finished); resolves on collect")
    void playAndCollectResolvesOnCollectOnly() throws Exception {
        Call call = answeredInboundCall("call-pac-go");
        Action.PlayAndCollectAction action = call.playAndCollect(
                List.of(silence(1)),
                Map.of("digits", Map.of("max", 1)),
                "pac-go");

        // Push play(finished) — must NOT resolve.
        mock.push(bareEventFrame(Constants.EVENT_CALL_PLAY,
                Map.of("call_id", "call-pac-go",
                        "control_id", "pac-go",
                        "state", "finished")));
        Thread.sleep(150);
        assertFalse(action.isDone(),
                "play_and_collect resolved on play(finished); should wait for collect");

        // Push collect — should resolve.
        mock.push(bareEventFrame(Constants.EVENT_CALL_COLLECT,
                Map.of("call_id", "call-pac-go",
                        "control_id", "pac-go",
                        "result", Map.of("type", "digit", "params", Map.of("digits", "1")))));
        RelayEvent event = action.waitForCompletion(2_000);
        assertNotNull(event);
        assertEquals(Constants.EVENT_CALL_COLLECT, event.getEventType());
    }

    @Test
    @DisplayName("play_and_collect.stop() journals calling.play_and_collect.stop")
    void playAndCollectStopJournals() throws Exception {
        Call call = answeredInboundCall("call-pac-stop");
        Action.PlayAndCollectAction action = call.playAndCollect(
                List.of(silence(1)),
                Map.of("digits", Map.of("max", 1)),
                "pac-stop");
        action.stop();

        List<RelayMockTest.JournalEntry> stops =
                mock.journalRecv(Constants.METHOD_PLAY_AND_COLLECT_STOP);
        assertFalse(stops.isEmpty());
        assertEquals("pac-stop",
                stops.get(stops.size() - 1).params().get("control_id"));
    }

    // ── StandaloneCollectAction ──────────────────────────────────

    @Test
    @DisplayName("collect() journals calling.collect")
    void collectJournals() throws Exception {
        Call call = answeredInboundCall("call-col");
        Action.CollectAction action = call.collectDigits(Map.of("max", 4), "col-ctl");
        // Note: action class for standalone collect is also CollectAction.

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_COLLECT);
        assertEquals(1, entries.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> digits = (Map<String, Object>) entries.get(0).params().get("digits");
        assertEquals(4.0, ((Number) digits.get("max")).doubleValue());
        assertEquals("col-ctl", entries.get(0).params().get("control_id"));
    }

    @Test
    @DisplayName("collect.stop() journals calling.collect.stop")
    void collectStopJournals() throws Exception {
        Call call = answeredInboundCall("call-col-stop");
        Action.CollectAction action = call.collectDigits(Map.of("max", 4), "col-stop");
        action.stop();

        List<RelayMockTest.JournalEntry> stops =
                mock.journalRecv(Constants.METHOD_COLLECT_STOP);
        assertFalse(stops.isEmpty());
        assertEquals("col-stop",
                stops.get(stops.size() - 1).params().get("control_id"));
    }

    // ── PayAction ────────────────────────────────────────────────

    @Test
    @DisplayName("pay() journals calling.pay")
    void payJournals() throws Exception {
        Call call = answeredInboundCall("call-pay");
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("control_id", "pay-ctl");
        opts.put("charge_amount", "9.99");
        call.pay("https://pay.example/connect", opts);

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_PAY);
        assertEquals(1, entries.size());
        Map<String, Object> p = entries.get(0).params();
        assertEquals("https://pay.example/connect", p.get("payment_connector_url"));
        assertEquals("pay-ctl", p.get("control_id"));
        assertEquals("9.99", p.get("charge_amount"));
    }

    @Test
    @DisplayName("pay() returns a PayAction instance")
    void payReturnsPayAction() throws Exception {
        Call call = answeredInboundCall("call-pay-act");
        Action.PayAction action = call.pay("https://pay.example/connect", "pay-act");
        assertNotNull(action);
        assertEquals("pay-act", action.getControlId());
    }

    @Test
    @DisplayName("pay.stop() journals calling.pay.stop")
    void payStopJournals() throws Exception {
        Call call = answeredInboundCall("call-pay-stop");
        Action.PayAction action = call.pay("https://pay.example/connect", "pay-stop");
        action.stop();

        List<RelayMockTest.JournalEntry> stops =
                mock.journalRecv(Constants.METHOD_PAY_STOP);
        assertFalse(stops.isEmpty());
        assertEquals("pay-stop",
                stops.get(stops.size() - 1).params().get("control_id"));
    }

    // ── FaxAction ────────────────────────────────────────────────

    @Test
    @DisplayName("send_fax() journals calling.send_fax")
    void sendFaxJournals() throws Exception {
        Call call = answeredInboundCall("call-sfax");
        call.sendFax("https://docs.example/test.pdf", "+15551112222", "sfax-ctl");

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_SEND_FAX);
        assertEquals(1, entries.size());
        Map<String, Object> p = entries.get(0).params();
        assertEquals("https://docs.example/test.pdf", p.get("document"));
        assertEquals("+15551112222", p.get("identity"));
        assertEquals("sfax-ctl", p.get("control_id"));
    }

    @Test
    @DisplayName("receive_fax() returns a ReceiveFaxAction instance")
    void receiveFaxReturnsAction() throws Exception {
        Call call = answeredInboundCall("call-rfax");
        Action.ReceiveFaxAction action = call.receiveFax("rfax-ctl");
        assertNotNull(action);
        assertEquals("rfax-ctl", action.getControlId());
    }

    // ── TapAction ─────────────────────────────────────────────────

    @Test
    @DisplayName("tap() journals calling.tap")
    void tapJournals() throws Exception {
        Call call = answeredInboundCall("call-tap");
        Map<String, Object> tapDevice = new LinkedHashMap<>();
        tapDevice.put("type", "rtp");
        tapDevice.put("params", Map.of("addr", "203.0.113.1", "port", 4000));
        call.tap(Map.of("type", "audio"), tapDevice, "tap-ctl");

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_TAP);
        assertEquals(1, entries.size());
        Map<String, Object> p = entries.get(0).params();
        @SuppressWarnings("unchecked")
        Map<String, Object> tap = (Map<String, Object>) p.get("tap");
        assertEquals("audio", tap.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> dev = (Map<String, Object>) p.get("device");
        @SuppressWarnings("unchecked")
        Map<String, Object> devParams = (Map<String, Object>) dev.get("params");
        assertEquals(4000.0, ((Number) devParams.get("port")).doubleValue());
        assertEquals("tap-ctl", p.get("control_id"));
    }

    @Test
    @DisplayName("tap.stop() journals calling.tap.stop")
    void tapStopJournals() throws Exception {
        Call call = answeredInboundCall("call-tap-stop");
        Map<String, Object> tapDevice = new LinkedHashMap<>();
        tapDevice.put("type", "rtp");
        tapDevice.put("params", Map.of("addr", "203.0.113.1", "port", 4000));
        Action.TapAction action = call.tap(Map.of("type", "audio"), tapDevice, "tap-stop");
        action.stop();

        List<RelayMockTest.JournalEntry> stops =
                mock.journalRecv(Constants.METHOD_TAP_STOP);
        assertFalse(stops.isEmpty());
        assertEquals("tap-stop",
                stops.get(stops.size() - 1).params().get("control_id"));
    }

    // ── StreamAction ─────────────────────────────────────────────

    @Test
    @DisplayName("stream() journals calling.stream")
    void streamJournals() throws Exception {
        Call call = answeredInboundCall("call-strm");
        call.stream("wss://stream.example/audio", "OPUS@48000h", "strm-ctl");

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_STREAM);
        assertEquals(1, entries.size());
        Map<String, Object> p = entries.get(0).params();
        assertEquals("wss://stream.example/audio", p.get("url"));
        assertEquals("OPUS@48000h", p.get("codec"));
        assertEquals("strm-ctl", p.get("control_id"));
    }

    @Test
    @DisplayName("stream.stop() journals calling.stream.stop")
    void streamStopJournals() throws Exception {
        Call call = answeredInboundCall("call-strm-stop");
        Action.StreamAction action = call.stream("wss://stream.example/audio", "strm-stop");
        action.stop();

        List<RelayMockTest.JournalEntry> stops =
                mock.journalRecv(Constants.METHOD_STREAM_STOP);
        assertFalse(stops.isEmpty());
        assertEquals("strm-stop",
                stops.get(stops.size() - 1).params().get("control_id"));
    }

    // ── TranscribeAction ─────────────────────────────────────────

    @Test
    @DisplayName("transcribe() journals calling.transcribe")
    void transcribeJournals() throws Exception {
        Call call = answeredInboundCall("call-tr");
        Action.TranscribeAction action = call.transcribe("tr-ctl");
        assertNotNull(action);

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_TRANSCRIBE);
        assertEquals(1, entries.size());
        assertEquals("tr-ctl", entries.get(0).params().get("control_id"));
    }

    @Test
    @DisplayName("transcribe.stop() journals calling.transcribe.stop")
    void transcribeStopJournals() throws Exception {
        Call call = answeredInboundCall("call-tr-stop");
        Action.TranscribeAction action = call.transcribe("tr-stop");
        action.stop();

        List<RelayMockTest.JournalEntry> stops =
                mock.journalRecv(Constants.METHOD_TRANSCRIBE_STOP);
        assertFalse(stops.isEmpty());
        assertEquals("tr-stop",
                stops.get(stops.size() - 1).params().get("control_id"));
    }

    // ── AIAction ─────────────────────────────────────────────────

    @Test
    @DisplayName("ai() journals calling.ai")
    void aiJournals() throws Exception {
        Call call = answeredInboundCall("call-ai");
        Action.AiAction action = call.ai(Map.of("text", "You are helpful."), "ai-ctl");
        assertNotNull(action);

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_AI);
        assertEquals(1, entries.size());
        Map<String, Object> p = entries.get(0).params();
        @SuppressWarnings("unchecked")
        Map<String, Object> prompt = (Map<String, Object>) p.get("prompt");
        assertEquals("You are helpful.", prompt.get("text"));
        assertEquals("ai-ctl", p.get("control_id"));
    }

    @Test
    @DisplayName("ai.stop() journals calling.ai.stop")
    void aiStopJournals() throws Exception {
        Call call = answeredInboundCall("call-ai-stop");
        Action.AiAction action = call.ai(Map.of("text", "You are helpful."), "ai-stop");
        action.stop();

        List<RelayMockTest.JournalEntry> stops =
                mock.journalRecv(Constants.METHOD_AI_STOP);
        assertFalse(stops.isEmpty());
        assertEquals("ai-stop",
                stops.get(stops.size() - 1).params().get("control_id"));
    }

    // ── General — control_id correlation across multiple actions ─

    @Test
    @DisplayName("Two actions with different control_ids resolve independently")
    void concurrentPlayAndRecordRouteIndependently() throws Exception {
        Call call = answeredInboundCall("call-multi");
        Action.PlayAction playAction = call.play(List.of(silence(60)), "ctl-play-x");
        Action.RecordAction recordAction = call.recordAudio(Map.of("format", "wav"), "ctl-rec-y");

        assertEquals("ctl-play-x", playAction.getControlId());
        assertEquals("ctl-rec-y", recordAction.getControlId());

        // Fire only play1's finished.
        mock.push(bareEventFrame(Constants.EVENT_CALL_PLAY,
                Map.of("call_id", "call-multi",
                        "control_id", "ctl-play-x",
                        "state", "finished")));
        playAction.waitForCompletion(2_000);
        assertTrue(playAction.isDone());
        assertFalse(recordAction.isDone());
    }
}
