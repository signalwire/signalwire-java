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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-relay-backed tests for the typed Call convenience wrappers
 * (playTts/playAudio/playSilence/playRingtone, detectDigit/
 * detectAnsweringMachine/detectFax, promptTts/promptAudio).
 * <p>
 * Each test dials a Call against the shared mock relay, invokes a convenience
 * method, then asserts the EXACT RELAY media/detect wire shape that landed in
 * the mock's receive journal. These are behavioral + wire assertions against
 * the real shared mock — never a transport mock. Mirrors the Python reference
 * convenience wrappers in {@code signalwire/relay/call.py}.
 */
class CallConvenienceMockTest {

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

    private static Map<String, Object> phoneDevice() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("to_number", "+15551112222");
        params.put("from_number", "+15553334444");
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("type", "phone");
        device.put("params", params);
        return device;
    }

    /** Dial an answered Call so convenience commands have a live call to hit. */
    private Call dialAnswered(String tag, String callId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tag", tag);
        body.put("winner_call_id", callId);
        body.put("states", List.of("created", "answered"));
        body.put("node_id", "node-mock-1");
        body.put("device", phoneDevice());
        body.put("delay_ms", 1);
        mock.armDial(body);

        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("tag", tag);
        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        Call call = client.dial(devices, opts, 5_000);
        assertNotNull(call, "dial did not resolve to a Call");
        assertEquals(callId, call.getCallId());
        return call;
    }

    /** Last calling.play frame's "play" media list. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lastPlayMedia() {
        List<RelayMockTest.JournalEntry> plays = mock.journalRecv(Constants.METHOD_PLAY);
        assertFalse(plays.isEmpty(), "no calling.play frame in journal");
        Map<String, Object> p = plays.get(plays.size() - 1).params();
        return (List<Map<String, Object>>) p.get("play");
    }

    /** Last calling.detect frame's "detect" object. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> lastDetect() {
        List<RelayMockTest.JournalEntry> ds = mock.journalRecv(Constants.METHOD_DETECT);
        assertFalse(ds.isEmpty(), "no calling.detect frame in journal");
        Map<String, Object> p = ds.get(ds.size() - 1).params();
        return (Map<String, Object>) p.get("detect");
    }

    /** Last calling.detect frame's full params (for timeout assertions). */
    private Map<String, Object> lastDetectParams() {
        List<RelayMockTest.JournalEntry> ds = mock.journalRecv(Constants.METHOD_DETECT);
        assertFalse(ds.isEmpty(), "no calling.detect frame in journal");
        return ds.get(ds.size() - 1).params();
    }

    /** Last calling.play_and_collect frame's params. */
    private Map<String, Object> lastPromptParams() {
        List<RelayMockTest.JournalEntry> pc =
                mock.journalRecv(Constants.METHOD_PLAY_AND_COLLECT);
        assertFalse(pc.isEmpty(), "no calling.play_and_collect frame in journal");
        return pc.get(pc.size() - 1).params();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paramsOf(Map<String, Object> mediaEntry) {
        return (Map<String, Object>) mediaEntry.get("params");
    }

    // ── playTts ──────────────────────────────────────────────────────

    @Test
    @DisplayName("playTts builds [{type:tts,params:{text}}] on the wire")
    void playTtsMinimalShape() {
        Call call = dialAnswered("t-tts", "C-TTS");
        call.playTts("hello world");

        List<Map<String, Object>> media = lastPlayMedia();
        assertEquals(1, media.size());
        assertEquals("tts", media.get(0).get("type"));
        Map<String, Object> params = paramsOf(media.get(0));
        assertEquals("hello world", params.get("text"));
        // No language/gender/voice when not supplied.
        assertFalse(params.containsKey("language"));
        assertFalse(params.containsKey("gender"));
        assertFalse(params.containsKey("voice"));
    }

    @Test
    @DisplayName("playTts folds language/gender/voice into tts params and volume into play")
    void playTtsFullShape() {
        Call call = dialAnswered("t-tts2", "C-TTS2");
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("language", "en-US");
        opts.put("gender", "female");
        opts.put("voice", "spore");
        opts.put("volume", 3.0);
        call.playTts("hi there", opts);

        List<RelayMockTest.JournalEntry> plays = mock.journalRecv(Constants.METHOD_PLAY);
        Map<String, Object> frame = plays.get(plays.size() - 1).params();
        // volume rides at the play level, NOT inside the tts params.
        assertEquals(3.0, ((Number) frame.get("volume")).doubleValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> media = (List<Map<String, Object>>) frame.get("play");
        Map<String, Object> params = paramsOf(media.get(0));
        assertEquals("hi there", params.get("text"));
        assertEquals("en-US", params.get("language"));
        assertEquals("female", params.get("gender"));
        assertEquals("spore", params.get("voice"));
        assertFalse(params.containsKey("volume"), "volume must not leak into tts params");
    }

    // ── playAudio ────────────────────────────────────────────────────

    @Test
    @DisplayName("playAudio builds [{type:audio,params:{url}}] on the wire")
    void playAudioShape() {
        Call call = dialAnswered("t-audio", "C-AUD");
        call.playAudio("https://example.com/a.wav");

        List<Map<String, Object>> media = lastPlayMedia();
        assertEquals("audio", media.get(0).get("type"));
        assertEquals("https://example.com/a.wav", paramsOf(media.get(0)).get("url"));
    }

    @Test
    @DisplayName("playAudio routes volume to the play level")
    void playAudioVolume() {
        Call call = dialAnswered("t-audio2", "C-AUD2");
        call.playAudio("https://example.com/b.wav", Map.of("volume", -2.0));

        List<RelayMockTest.JournalEntry> plays = mock.journalRecv(Constants.METHOD_PLAY);
        Map<String, Object> frame = plays.get(plays.size() - 1).params();
        assertEquals(-2.0, ((Number) frame.get("volume")).doubleValue());
    }

    // ── playSilence ──────────────────────────────────────────────────

    @Test
    @DisplayName("playSilence builds [{type:silence,params:{duration}}] on the wire")
    void playSilenceShape() {
        Call call = dialAnswered("t-sil", "C-SIL");
        call.playSilence(2.5);

        List<Map<String, Object>> media = lastPlayMedia();
        assertEquals("silence", media.get(0).get("type"));
        assertEquals(2.5, ((Number) paramsOf(media.get(0)).get("duration")).doubleValue());
    }

    // ── playRingtone ─────────────────────────────────────────────────

    @Test
    @DisplayName("playRingtone builds [{type:ringtone,params:{name}}] on the wire")
    void playRingtoneMinimalShape() {
        Call call = dialAnswered("t-rt", "C-RT");
        call.playRingtone("us");

        List<Map<String, Object>> media = lastPlayMedia();
        assertEquals("ringtone", media.get(0).get("type"));
        Map<String, Object> params = paramsOf(media.get(0));
        assertEquals("us", params.get("name"));
        assertFalse(params.containsKey("duration"));
    }

    @Test
    @DisplayName("playRingtone folds duration into ringtone params, volume into play")
    void playRingtoneFullShape() {
        Call call = dialAnswered("t-rt2", "C-RT2");
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("duration", 5.0);
        opts.put("volume", 1.0);
        call.playRingtone("gb", opts);

        List<RelayMockTest.JournalEntry> plays = mock.journalRecv(Constants.METHOD_PLAY);
        Map<String, Object> frame = plays.get(plays.size() - 1).params();
        assertEquals(1.0, ((Number) frame.get("volume")).doubleValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> media = (List<Map<String, Object>>) frame.get("play");
        Map<String, Object> params = paramsOf(media.get(0));
        assertEquals("gb", params.get("name"));
        assertEquals(5.0, ((Number) params.get("duration")).doubleValue());
        assertFalse(params.containsKey("volume"), "volume must not leak into ringtone params");
    }

    // ── detectDigit ──────────────────────────────────────────────────

    @Test
    @DisplayName("detectDigit builds {type:digit,params:{}} with empty params by default")
    void detectDigitMinimalShape() {
        Call call = dialAnswered("t-dig", "C-DIG");
        call.detectDigit();

        Map<String, Object> detect = lastDetect();
        assertEquals("digit", detect.get("type"));
        assertTrue(paramsOf(detect).isEmpty(), "no digits expected when not supplied");
    }

    @Test
    @DisplayName("detectDigit folds digits into params and timeout to detect level")
    void detectDigitFullShape() {
        Call call = dialAnswered("t-dig2", "C-DIG2");
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("digits", "123#");
        opts.put("timeout", 10.0);
        call.detectDigit(opts);

        Map<String, Object> frame = lastDetectParams();
        // timeout rides at the detect level, not inside the digit params.
        assertEquals(10.0, ((Number) frame.get("timeout")).doubleValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> detect = (Map<String, Object>) frame.get("detect");
        assertEquals("digit", detect.get("type"));
        Map<String, Object> params = paramsOf(detect);
        assertEquals("123#", params.get("digits"));
        assertFalse(params.containsKey("timeout"), "timeout must not leak into digit params");
    }

    // ── detectAnsweringMachine ───────────────────────────────────────

    @Test
    @DisplayName("detectAnsweringMachine builds {type:machine,params:{}} by default")
    void detectAmdMinimalShape() {
        Call call = dialAnswered("t-amd", "C-AMD");
        call.detectAnsweringMachine();

        Map<String, Object> detect = lastDetect();
        assertEquals("machine", detect.get("type"));
        assertTrue(paramsOf(detect).isEmpty(),
                "machine params must contain only the supplied keys");
    }

    @Test
    @DisplayName("detectAnsweringMachine emits only the supplied AMD keys, timeout at detect level")
    void detectAmdSelectiveShape() {
        Call call = dialAnswered("t-amd2", "C-AMD2");
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("initial_timeout", 4.0);
        opts.put("detect_interruptions", true);
        opts.put("machine_words_threshold", 6);
        opts.put("timeout", 30.0);
        call.detectAnsweringMachine(opts);

        Map<String, Object> frame = lastDetectParams();
        assertEquals(30.0, ((Number) frame.get("timeout")).doubleValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> detect = (Map<String, Object>) frame.get("detect");
        assertEquals("machine", detect.get("type"));
        Map<String, Object> params = paramsOf(detect);
        assertEquals(4.0, ((Number) params.get("initial_timeout")).doubleValue());
        assertEquals(Boolean.TRUE, params.get("detect_interruptions"));
        assertEquals(6.0, ((Number) params.get("machine_words_threshold")).doubleValue());
        // Keys not supplied must be absent (only-provided contract).
        assertFalse(params.containsKey("end_silence_timeout"));
        assertFalse(params.containsKey("machine_voice_threshold"));
        assertFalse(params.containsKey("detect_message_end"));
        assertFalse(params.containsKey("timeout"), "timeout must not leak into machine params");
    }

    // ── detectFax ────────────────────────────────────────────────────

    @Test
    @DisplayName("detectFax builds {type:fax,params:{}} by default")
    void detectFaxMinimalShape() {
        Call call = dialAnswered("t-fax", "C-FAX");
        call.detectFax();

        Map<String, Object> detect = lastDetect();
        assertEquals("fax", detect.get("type"));
        assertTrue(paramsOf(detect).isEmpty());
    }

    @Test
    @DisplayName("detectFax folds tone into params and timeout to detect level")
    void detectFaxFullShape() {
        Call call = dialAnswered("t-fax2", "C-FAX2");
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("tone", "CED");
        opts.put("timeout", 12.0);
        call.detectFax(opts);

        Map<String, Object> frame = lastDetectParams();
        assertEquals(12.0, ((Number) frame.get("timeout")).doubleValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> detect = (Map<String, Object>) frame.get("detect");
        assertEquals("fax", detect.get("type"));
        assertEquals("CED", paramsOf(detect).get("tone"));
    }

    // ── promptTts ────────────────────────────────────────────────────

    @Test
    @DisplayName("promptTts plays [{type:tts,params:{text,...}}] and forwards collect")
    void promptTtsShape() {
        Call call = dialAnswered("t-ptts", "C-PTTS");
        Map<String, Object> collect = new LinkedHashMap<>();
        Map<String, Object> digits = new LinkedHashMap<>();
        digits.put("max", 1);
        collect.put("digits", digits);

        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("voice", "spore");
        opts.put("volume", 2.0);
        call.promptTts("press a digit", collect, opts);

        Map<String, Object> frame = lastPromptParams();
        assertEquals(2.0, ((Number) frame.get("volume")).doubleValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> media = (List<Map<String, Object>>) frame.get("play");
        assertEquals("tts", media.get(0).get("type"));
        Map<String, Object> params = paramsOf(media.get(0));
        assertEquals("press a digit", params.get("text"));
        assertEquals("spore", params.get("voice"));

        @SuppressWarnings("unchecked")
        Map<String, Object> wireCollect = (Map<String, Object>) frame.get("collect");
        assertNotNull(wireCollect.get("digits"), "collect config must be forwarded verbatim");
    }

    // ── promptAudio ──────────────────────────────────────────────────

    @Test
    @DisplayName("promptAudio plays [{type:audio,params:{url}}] and forwards collect")
    void promptAudioShape() {
        Call call = dialAnswered("t-paud", "C-PAUD");
        Map<String, Object> collect = new LinkedHashMap<>();
        Map<String, Object> speech = new LinkedHashMap<>();
        speech.put("end_silence_timeout", 1.0);
        collect.put("speech", speech);

        call.promptAudio("https://example.com/p.wav", collect);

        Map<String, Object> frame = lastPromptParams();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> media = (List<Map<String, Object>>) frame.get("play");
        assertEquals("audio", media.get(0).get("type"));
        assertEquals("https://example.com/p.wav", paramsOf(media.get(0)).get("url"));

        @SuppressWarnings("unchecked")
        Map<String, Object> wireCollect = (Map<String, Object>) frame.get("collect");
        assertNotNull(wireCollect.get("speech"), "collect config must be forwarded verbatim");
    }
}
