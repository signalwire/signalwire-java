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
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-relay-backed unit tests translated from
 * {@code signalwire-python/tests/unit/relay/test_outbound_call_mock.py}.
 */
class OutboundCallMockTest {

    private static final Pattern UUID_RE = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

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

    private static Map<String, Object> phoneDevice(String to, String from) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("to_number", to);
        params.put("from_number", from);
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("type", "phone");
        device.put("params", params);
        return device;
    }

    private static Map<String, Object> phoneDevice() {
        return phoneDevice("+15551112222", "+15553334444");
    }

    private void armDial(String tag, String winnerCallId, List<String> states,
                         List<Map<String, Object>> losers, int delayMs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tag", tag);
        body.put("winner_call_id", winnerCallId);
        body.put("states", states);
        body.put("node_id", "node-mock-1");
        body.put("device", phoneDevice());
        body.put("delay_ms", delayMs);
        if (losers != null && !losers.isEmpty()) {
            body.put("losers", losers);
        }
        mock.armDial(body);
    }

    private Map<String, Object> dialOptions(String tag) {
        Map<String, Object> opts = new LinkedHashMap<>();
        opts.put("tag", tag);
        return opts;
    }

    // ── Happy-path dial ────────────────────────────────────────────

    @Test
    @DisplayName("dial() returns Call with winner's call_id")
    void dialResolvesToCallWithWinnerId() {
        armDial("t-happy", "winner-1",
                List.of("created", "ringing", "answered"), null, 1);
        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        Call call = client.dial(devices, dialOptions("t-happy"), 5_000);
        assertNotNull(call);
        assertEquals("winner-1", call.getCallId());
        assertEquals("t-happy", call.getTag());
        assertEquals("answered", call.getState());
    }

    @Test
    @DisplayName("dial journal records calling.dial frame with right tag")
    void dialJournalRecordsFrame() {
        armDial("t-frame", "winner-frame",
                List.of("created", "answered"), null, 1);
        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        client.dial(devices, dialOptions("t-frame"), 5_000);

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_DIAL);
        assertEquals(1, entries.size());
        Map<String, Object> p = entries.get(0).params();
        assertEquals("t-frame", p.get("tag"));
        assertTrue(p.get("devices") instanceof List);
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> devs =
                (List<List<Map<String, Object>>>) p.get("devices");
        assertEquals("phone", devs.get(0).get(0).get("type"));
    }

    @Test
    @DisplayName("dial with max_duration carries it on the wire")
    void dialWithMaxDurationInFrame() {
        armDial("t-md", "winner-md",
                List.of("created", "answered"), null, 1);
        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        Map<String, Object> opts = dialOptions("t-md");
        opts.put("max_duration", 300);
        client.dial(devices, opts, 5_000);

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_DIAL);
        // Gson decodes ints as doubles in Object maps.
        Object maxDur = entries.get(0).params().get("max_duration");
        assertEquals(300.0, ((Number) maxDur).doubleValue());
    }

    @Test
    @DisplayName("dial auto-generates a UUID tag when none is provided")
    void dialAutoGeneratesUuidTag() throws Exception {
        // Push the dial-answer frame in a background thread once we see
        // the dial frame land in the journal.
        AtomicReference<String> seenTag = new AtomicReference<>();
        Thread pusher = new Thread(() -> {
            try {
                for (int i = 0; i < 200; i++) {
                    List<RelayMockTest.JournalEntry> entries =
                            mock.journalRecv(Constants.METHOD_DIAL);
                    if (!entries.isEmpty()) {
                        seenTag.set(
                                (String) entries.get(entries.size() - 1).params().get("tag"));
                        break;
                    }
                    Thread.sleep(10);
                }
                if (seenTag.get() == null) return;

                Map<String, Object> callInfo = new LinkedHashMap<>();
                callInfo.put("call_id", "auto-tag-winner");
                callInfo.put("node_id", "node-mock-1");
                callInfo.put("tag", seenTag.get());
                callInfo.put("device", phoneDevice());
                callInfo.put("dial_winner", true);

                Map<String, Object> innerParams = new LinkedHashMap<>();
                innerParams.put("tag", seenTag.get());
                innerParams.put("node_id", "node-mock-1");
                innerParams.put("dial_state", "answered");
                innerParams.put("call", callInfo);

                Map<String, Object> outer = new LinkedHashMap<>();
                outer.put("event_type", Constants.EVENT_CALL_DIAL);
                outer.put("params", innerParams);

                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("jsonrpc", Constants.JSONRPC_VERSION);
                frame.put("id", UUID.randomUUID().toString());
                frame.put("method", "signalwire.event");
                frame.put("params", outer);
                mock.push(frame);
            } catch (Exception ignored) {
            }
        }, "auto-tag-pusher");
        pusher.setDaemon(true);
        pusher.start();

        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        Call call = client.dial(devices, null, 5_000);

        pusher.join(2_000);
        assertEquals("auto-tag-winner", call.getCallId());
        assertNotNull(seenTag.get());
        assertTrue(UUID_RE.matcher(seenTag.get()).matches(),
                "expected UUID-shaped tag, got: " + seenTag.get());
        assertEquals(seenTag.get(), call.getTag());
    }

    // ── Failure paths ──────────────────────────────────────────────

    @Test
    @DisplayName("dial(failed) raises RelayError")
    void dialFailedRaisesRelayError() throws Exception {
        // Push a failure event after a small delay.
        Thread pusher = new Thread(() -> {
            try {
                for (int i = 0; i < 200; i++) {
                    if (!mock.journalRecv(Constants.METHOD_DIAL).isEmpty()) break;
                    Thread.sleep(10);
                }
                Map<String, Object> innerParams = new LinkedHashMap<>();
                innerParams.put("tag", "t-fail");
                innerParams.put("node_id", "node-mock-1");
                innerParams.put("dial_state", "failed");
                innerParams.put("call", new HashMap<>());

                Map<String, Object> outer = new LinkedHashMap<>();
                outer.put("event_type", Constants.EVENT_CALL_DIAL);
                outer.put("params", innerParams);

                Map<String, Object> frame = new LinkedHashMap<>();
                frame.put("jsonrpc", Constants.JSONRPC_VERSION);
                frame.put("id", UUID.randomUUID().toString());
                frame.put("method", "signalwire.event");
                frame.put("params", outer);
                mock.push(frame);
            } catch (Exception ignored) {}
        }, "fail-pusher");
        pusher.setDaemon(true);
        pusher.start();

        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        RelayError err = assertThrows(RelayError.class, () ->
                client.dial(devices, dialOptions("t-fail"), 5_000));
        assertTrue(err.getMessage().toLowerCase().contains("dial"),
                "expected dial-related error; got: " + err.getMessage());
        pusher.join(2_000);
    }

    @Test
    @DisplayName("dial times out cleanly when no dial event arrives")
    void dialTimeoutWhenNoDialEvent() {
        // No scenario armed, no event.
        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        RelayError err = assertThrows(RelayError.class, () ->
                client.dial(devices, dialOptions("t-timeout"), 500));
        assertTrue(err.getMessage().toLowerCase().contains("timed out")
                        || err.getMessage().toLowerCase().contains("timeout"),
                "expected timeout; got: " + err.getMessage());
    }

    // ── Parallel dial — winner + losers ───────────────────────────

    @Test
    @DisplayName("Mock's emitted calling.call.dial event carries dial_winner=true")
    void dialWinnerCarriesDialWinnerTrue() {
        List<Map<String, Object>> losers = new ArrayList<>();
        losers.add(Map.of("call_id", "LOSE-A", "states", List.of("created", "ended")));
        losers.add(Map.of("call_id", "LOSE-B", "states", List.of("created", "ended")));
        armDial("t-winner", "WIN-ID",
                List.of("created", "answered"), losers, 1);

        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        Call call = client.dial(devices, dialOptions("t-winner"), 5_000);
        assertEquals("WIN-ID", call.getCallId());

        List<RelayMockTest.JournalEntry> sends =
                mock.journalSend(Constants.EVENT_CALL_DIAL);
        assertFalse(sends.isEmpty(), "no calling.call.dial event was pushed");
        // Find the answered one.
        RelayMockTest.JournalEntry answered = null;
        for (RelayMockTest.JournalEntry e : sends) {
            Map<String, Object> inner = e.innerParams();
            if (inner != null && "answered".equals(inner.get("dial_state"))) {
                answered = e;
                break;
            }
        }
        assertNotNull(answered, "no answered dial event found");
        Map<String, Object> inner = answered.innerParams();
        @SuppressWarnings("unchecked")
        Map<String, Object> innerCall = (Map<String, Object>) inner.get("call");
        assertEquals(Boolean.TRUE, innerCall.get("dial_winner"));
        assertEquals("WIN-ID", innerCall.get("call_id"));
    }

    @Test
    @DisplayName("Loser legs receive their own state events ending in 'ended'")
    void dialLosersGetStateEvents() {
        List<Map<String, Object>> losers = List.of(
                Map.of("call_id", "L1", "states", List.of("created", "ended")));
        armDial("t-losers", "WIN-2",
                List.of("created", "answered"), losers, 1);

        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        client.dial(devices, dialOptions("t-losers"), 5_000);

        List<RelayMockTest.JournalEntry> sends =
                mock.journalSend(Constants.EVENT_CALL_STATE);
        boolean foundEnded = false;
        for (RelayMockTest.JournalEntry e : sends) {
            Map<String, Object> inner = e.innerParams();
            if ("L1".equals(inner.get("call_id")) && "ended".equals(inner.get("call_state"))) {
                foundEnded = true;
                break;
            }
        }
        assertTrue(foundEnded, "loser L1 never reached 'ended'");
    }

    @Test
    @DisplayName("Ended losers are cleaned up from internal _calls")
    void dialLosersCleanedUpFromCallsDict() throws Exception {
        List<Map<String, Object>> losers = List.of(
                Map.of("call_id", "LOSE-CL", "states", List.of("created", "ended")));
        armDial("t-cleanup", "WIN-CL",
                List.of("created", "answered"), losers, 1);

        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        Call call = client.dial(devices, dialOptions("t-cleanup"), 5_000);
        Thread.sleep(150);
        assertNull(client.getCalls().get("LOSE-CL"));
        assertNotNull(client.getCalls().get(call.getCallId()));
    }

    // ── Devices shape on the wire ───────────────────────────────

    @Test
    @DisplayName("Serial dial — one leg with two devices — flows through")
    void dialDevicesSerialTwoLegsOnWire() {
        armDial("t-serial", "WIN-SER",
                List.of("created", "answered"), null, 1);

        List<List<Map<String, Object>>> devs = List.of(List.of(
                phoneDevice("+15551110001", "+15553334444"),
                phoneDevice("+15551110002", "+15553334444")));
        client.dial(devs, dialOptions("t-serial"), 5_000);

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_DIAL);
        assertEquals(1, entries.size());
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> wire =
                (List<List<Map<String, Object>>>) entries.get(0).params().get("devices");
        assertEquals(1, wire.size());
        assertEquals(2, wire.get(0).size());
        @SuppressWarnings("unchecked")
        Map<String, Object> dev0Params = (Map<String, Object>) wire.get(0).get(0).get("params");
        assertEquals("+15551110001", dev0Params.get("to_number"));
    }

    @Test
    @DisplayName("Parallel dial — two legs — flows through")
    void dialDevicesParallelTwoLegsOnWire() {
        armDial("t-par", "WIN-PAR",
                List.of("created", "answered"), null, 1);

        List<List<Map<String, Object>>> devs = List.of(
                List.of(phoneDevice("+15551110001", "+15553334444")),
                List.of(phoneDevice("+15551110002", "+15553334444")));
        client.dial(devs, dialOptions("t-par"), 5_000);

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_DIAL);
        @SuppressWarnings("unchecked")
        List<List<Map<String, Object>>> wire =
                (List<List<Map<String, Object>>>) entries.get(0).params().get("devices");
        assertEquals(2, wire.size());
    }

    // ── State transitions during dial ─────────────────────────────

    @Test
    @DisplayName("Winner's state events flow created -> ringing -> answered")
    void dialRecordsCallStateProgressionOnWinner() {
        armDial("t-prog", "WIN-PROG",
                List.of("created", "ringing", "answered"), null, 1);

        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        Call call = client.dial(devices, dialOptions("t-prog"), 5_000);

        List<RelayMockTest.JournalEntry> sends =
                mock.journalSend(Constants.EVENT_CALL_STATE);
        List<String> winnerStates = new ArrayList<>();
        for (RelayMockTest.JournalEntry e : sends) {
            Map<String, Object> inner = e.innerParams();
            if ("WIN-PROG".equals(inner.get("call_id"))) {
                winnerStates.add((String) inner.get("call_state"));
            }
        }
        assertTrue(winnerStates.contains("created"));
        assertTrue(winnerStates.contains("ringing"));
        assertTrue(winnerStates.contains("answered"));
        assertEquals("answered", call.getState());
    }

    // ── After dial — call object is usable ───────────────────────

    @Test
    @DisplayName("Dial-winner Call can issue further commands (hangup)")
    void dialedCallCanSendSubsequentCommand() {
        armDial("t-after", "WIN-AFTER",
                List.of("created", "answered"), null, 1);

        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        Call call = client.dial(devices, dialOptions("t-after"), 5_000);
        call.hangup();

        List<RelayMockTest.JournalEntry> ends = mock.journalRecv(Constants.METHOD_END);
        assertFalse(ends.isEmpty(), "no calling.end frame in journal");
        assertEquals("WIN-AFTER",
                ends.get(ends.size() - 1).params().get("call_id"));
    }

    @Test
    @DisplayName("Dialed call can issue calling.play")
    void dialedCallCanPlay() {
        armDial("t-play", "WIN-PLAY",
                List.of("created", "answered"), null, 1);

        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        Call call = client.dial(devices, dialOptions("t-play"), 5_000);

        Map<String, Object> tts = new LinkedHashMap<>();
        tts.put("type", "tts");
        tts.put("params", Map.of("text", "hi"));
        call.play(List.of(tts));

        List<RelayMockTest.JournalEntry> plays = mock.journalRecv(Constants.METHOD_PLAY);
        assertFalse(plays.isEmpty(), "no calling.play frame after dial");
        Map<String, Object> p = plays.get(plays.size() - 1).params();
        assertEquals("WIN-PLAY", p.get("call_id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> playList = (List<Map<String, Object>>) p.get("play");
        assertEquals("tts", playList.get(0).get("type"));
    }

    // ── Tag preservation ──────────────────────────────────────────

    @Test
    @DisplayName("Explicit tag flows verbatim into Call.tag")
    void dialPreservesExplicitTag() {
        armDial("my-very-explicit-tag-99", "WIN-T",
                List.of("created", "answered"), null, 1);

        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        Call call = client.dial(devices, dialOptions("my-very-explicit-tag-99"), 5_000);
        assertEquals("my-very-explicit-tag-99", call.getTag());
    }

    // ── JSON-RPC envelope ─────────────────────────────────────────

    @Test
    @DisplayName("dial frame is JSON-RPC 2.0 with id+method+params")
    void dialUsesJsonRpc20() {
        armDial("t-rpc", "W",
                List.of("created", "answered"), null, 1);

        List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
        client.dial(devices, dialOptions("t-rpc"), 5_000);

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_DIAL);
        Map<String, Object> frame = entries.get(0).frame;
        assertEquals("2.0", frame.get("jsonrpc"));
        assertEquals(Constants.METHOD_DIAL, frame.get("method"));
        assertNotNull(frame.get("id"));
        assertNotNull(frame.get("params"));
    }
}
