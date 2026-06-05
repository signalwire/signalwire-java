/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import com.google.gson.Gson;
import com.signalwire.sdk.swaig.FunctionResult;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FunctionResult covering all action categories.
 */
class FunctionResultTest {

    private static final Gson gson = new Gson();

    // ======== Core Serialization ========

    @Test
    void testBasicResponse() {
        var result = new FunctionResult("Hello world");
        var map = result.toMap();
        assertEquals("Hello world", map.get("response"));
        assertFalse(map.containsKey("action"));
        assertFalse(map.containsKey("post_process"));
    }

    @Test
    void testEmptyResultFallback() {
        var result = new FunctionResult();
        var map = result.toMap();
        assertEquals("Action completed.", map.get("response"));
    }

    @Test
    void testPostProcessOnlyWithActions() {
        // Post-process without actions should not include post_process
        var result = new FunctionResult("test", true);
        var map = result.toMap();
        assertFalse(map.containsKey("post_process"));

        // Post-process with actions should include post_process
        result.addAction("say", "hello");
        map = result.toMap();
        assertTrue((Boolean) map.get("post_process"));
    }

    @Test
    void testSetResponse() {
        var result = new FunctionResult("initial");
        result.setResponse("updated");
        assertEquals("updated", result.getResponse());
    }

    @Test
    void testSetPostProcess() {
        var result = new FunctionResult("test");
        result.setPostProcess(true).addAction("say", "hi");
        assertTrue(result.isPostProcess());
        assertTrue((Boolean) result.toMap().get("post_process"));
    }

    @Test
    void testAddAction() {
        var result = new FunctionResult("test")
                .addAction("say", "hello");
        var actions = result.getActions();
        assertEquals(1, actions.size());
        assertEquals("hello", actions.get(0).get("say"));
    }

    @Test
    void testAddMultipleActions() {
        var result = new FunctionResult("test")
                .addActions(List.of(
                        Map.of("say", "hello"),
                        Map.of("hangup", true)
                ));
        assertEquals(2, result.getActions().size());
    }

    // ======== Call Control ========

    @Test
    void testConnect() {
        var result = new FunctionResult("Transferring")
                .connect("+15551234567", true);

        var actions = result.getActions();
        assertEquals(1, actions.size());
        var action = actions.get(0);
        assertTrue(action.containsKey("SWML"));
        assertEquals("true", action.get("transfer"));
    }

    @Test
    void testConnectWithFrom() {
        var result = new FunctionResult("Transferring")
                .connect("+15551234567", false, "+15559876543");

        var actions = result.getActions();
        assertEquals(1, actions.size());
        var action = actions.get(0);
        assertEquals("false", action.get("transfer"));

        @SuppressWarnings("unchecked")
        var swml = (Map<String, Object>) action.get("SWML");
        assertNotNull(swml);
    }

    @Test
    void testSwmlTransfer() {
        var result = new FunctionResult("Transferring")
                .swmlTransfer("https://example.com/swml", "Goodbye!", true);

        var actions = result.getActions();
        assertEquals(1, actions.size());
        var action = actions.get(0);
        assertTrue(action.containsKey("SWML"));
        assertEquals("true", action.get("transfer"));
    }

    @Test
    void testHangup() {
        var result = new FunctionResult("Goodbye").hangup();
        var actions = result.getActions();
        assertEquals(1, actions.size());
        assertEquals(true, actions.get(0).get("hangup"));
    }

    @Test
    void testHold() {
        var result = new FunctionResult("Please hold").hold(300);
        assertEquals(300, result.getActions().get(0).get("hold"));
    }

    @Test
    void testHoldClampedMax() {
        var result = new FunctionResult("Hold").hold(2000);
        assertEquals(900, result.getActions().get(0).get("hold"));
    }

    @Test
    void testHoldClampedMin() {
        var result = new FunctionResult("Hold").hold(-10);
        assertEquals(0, result.getActions().get(0).get("hold"));
    }

    @Test
    void testWaitForUser() {
        var result = new FunctionResult("Waiting").waitForUser();
        assertEquals(true, result.getActions().get(0).get("wait_for_user"));
    }

    @Test
    void testWaitForUserAnswerFirst() {
        var result = new FunctionResult("test")
                .waitForUser(null, null, true);
        assertEquals("answer_first", result.getActions().get(0).get("wait_for_user"));
    }

    @Test
    void testWaitForUserWithTimeout() {
        var result = new FunctionResult("test")
                .waitForUser(null, 30, false);
        assertEquals(30, result.getActions().get(0).get("wait_for_user"));
    }

    @Test
    void testStop() {
        var result = new FunctionResult("Stopping").stop();
        assertEquals(true, result.getActions().get(0).get("stop"));
    }

    // ======== State & Data Management ========

    @Test
    void testUpdateGlobalData() {
        var result = new FunctionResult("Updated")
                .updateGlobalData(Map.of("key1", "value1", "key2", "value2"));

        var action = result.getActions().get(0);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) action.get("set_global_data");
        assertEquals("value1", data.get("key1"));
        assertEquals("value2", data.get("key2"));
    }

    @Test
    void testRemoveGlobalData() {
        var result = new FunctionResult("Removed")
                .removeGlobalData(List.of("key1", "key2"));

        var action = result.getActions().get(0);
        assertTrue(action.containsKey("unset_global_data"));
    }

    @Test
    void testSetMetadata() {
        var result = new FunctionResult("Set")
                .setMetadata(Map.of("meta_key", "meta_value"));

        var action = result.getActions().get(0);
        assertTrue(action.containsKey("set_meta_data"));
    }

    @Test
    void testRemoveMetadata() {
        var result = new FunctionResult("Removed")
                .removeMetadata(List.of("meta_key"));

        var action = result.getActions().get(0);
        assertTrue(action.containsKey("unset_meta_data"));
    }

    @Test
    void testSwmlUserEvent() {
        var result = new FunctionResult("Event sent")
                .swmlUserEvent(Map.of("type", "test_event", "data", "hello"));

        var action = result.getActions().get(0);
        assertTrue(action.containsKey("SWML"));
    }

    @Test
    void testSwmlChangeStep() {
        var result = new FunctionResult("Changing step")
                .swmlChangeStep("next_step");

        var action = result.getActions().get(0);
        assertEquals("next_step", action.get("change_step"));
    }

    @Test
    void testSwmlChangeContext() {
        var result = new FunctionResult("Changing context")
                .swmlChangeContext("support");

        var action = result.getActions().get(0);
        assertEquals("support", action.get("change_context"));
    }

    @Test
    void testSwitchContextSimple() {
        var result = new FunctionResult("Switching")
                .switchContext("You are now a support agent.");

        var action = result.getActions().get(0);
        assertEquals("You are now a support agent.", action.get("context_switch"));
    }

    @Test
    void testSwitchContextFull() {
        var result = new FunctionResult("Switching")
                .switchContext("New system prompt", "User said hello", true, true);

        var action = result.getActions().get(0);
        @SuppressWarnings("unchecked")
        var contextData = (Map<String, Object>) action.get("context_switch");
        assertEquals("New system prompt", contextData.get("system_prompt"));
        assertEquals("User said hello", contextData.get("user_prompt"));
        assertEquals(true, contextData.get("consolidate"));
        assertEquals(true, contextData.get("full_reset"));
    }

    @Test
    void testReplaceInHistoryString() {
        var result = new FunctionResult("test")
                .replaceInHistory("summary text");

        var action = result.getActions().get(0);
        assertEquals("summary text", action.get("replace_in_history"));
    }

    @Test
    void testReplaceInHistoryBoolean() {
        var result = new FunctionResult("test")
                .replaceInHistory(true);

        var action = result.getActions().get(0);
        assertEquals(true, action.get("replace_in_history"));
    }

    // ======== Media Control ========

    @Test
    void testSay() {
        var result = new FunctionResult("test").say("Hello world");
        assertEquals("Hello world", result.getActions().get(0).get("say"));
    }

    @Test
    void testPlayBackgroundFile() {
        var result = new FunctionResult("test")
                .playBackgroundFile("music.mp3");

        assertEquals("music.mp3", result.getActions().get(0).get("playback_bg"));
    }

    @Test
    void testPlayBackgroundFileWithWait() {
        var result = new FunctionResult("test")
                .playBackgroundFile("music.mp3", true);

        var action = result.getActions().get(0);
        @SuppressWarnings("unchecked")
        var params = (Map<String, Object>) action.get("playback_bg");
        assertEquals("music.mp3", params.get("file"));
        assertEquals(true, params.get("wait"));
    }

    @Test
    void testStopBackgroundFile() {
        var result = new FunctionResult("test").stopBackgroundFile();
        assertEquals(true, result.getActions().get(0).get("stop_playback_bg"));
    }

    @Test
    void testRecordCall() {
        var result = new FunctionResult("Recording")
                .recordCall("ctrl-1", true, "mp3", "both");

        var actions = result.getActions();
        assertEquals(1, actions.size());
        assertTrue(actions.get(0).containsKey("SWML"));
    }

    @Test
    void testStopRecordCall() {
        var result = new FunctionResult("Stopped")
                .stopRecordCall("ctrl-1");

        var actions = result.getActions();
        assertEquals(1, actions.size());
        assertTrue(actions.get(0).containsKey("SWML"));
    }

    // ======== Speech & AI Configuration ========

    @Test
    void testAddDynamicHints() {
        var result = new FunctionResult("test")
                .addDynamicHints(List.of("SignalWire", "SWML"));

        var action = result.getActions().get(0);
        assertTrue(action.containsKey("add_dynamic_hints"));
    }

    @Test
    void testClearDynamicHints() {
        var result = new FunctionResult("test").clearDynamicHints();
        assertTrue(result.getActions().get(0).containsKey("clear_dynamic_hints"));
    }

    @Test
    void testSetEndOfSpeechTimeout() {
        var result = new FunctionResult("test")
                .setEndOfSpeechTimeout(500);

        assertEquals(500, result.getActions().get(0).get("end_of_speech_timeout"));
    }

    @Test
    void testSetSpeechEventTimeout() {
        var result = new FunctionResult("test")
                .setSpeechEventTimeout(3000);

        assertEquals(3000, result.getActions().get(0).get("speech_event_timeout"));
    }

    @Test
    void testToggleFunctions() {
        var toggles = List.<Map<String, Object>>of(
                Map.of("function", "func1", "active", true),
                Map.of("function", "func2", "active", false)
        );
        var result = new FunctionResult("test")
                .toggleFunctions(toggles);

        assertTrue(result.getActions().get(0).containsKey("toggle_functions"));
    }

    @Test
    void testEnableFunctionsOnTimeout() {
        var result = new FunctionResult("test")
                .enableFunctionsOnTimeout(true);

        assertEquals(true, result.getActions().get(0).get("functions_on_speaker_timeout"));
    }

    @Test
    void testEnableExtensiveData() {
        var result = new FunctionResult("test")
                .enableExtensiveData(true);

        assertEquals(true, result.getActions().get(0).get("extensive_data"));
    }

    @Test
    void testUpdateSettings() {
        var result = new FunctionResult("test")
                .updateSettings(Map.of("temperature", 0.5));

        var action = result.getActions().get(0);
        @SuppressWarnings("unchecked")
        var settings = (Map<String, Object>) action.get("settings");
        assertEquals(0.5, settings.get("temperature"));
    }

    // ======== Advanced Features ========

    @Test
    void testExecuteSwmlMap() {
        Map<String, Object> swml = Map.of(
                "version", "1.0.0",
                "sections", Map.of("main", List.of(Map.of("hangup", Map.of())))
        );
        var result = new FunctionResult("test").executeSwml(swml);

        assertTrue(result.getActions().get(0).containsKey("SWML"));
    }

    @Test
    void testExecuteSwmlWithTransfer() {
        Map<String, Object> swml = Map.of(
                "version", "1.0.0",
                "sections", Map.of("main", List.of(Map.of("hangup", Map.of())))
        );
        var result = new FunctionResult("test").executeSwml(swml, true);

        var action = result.getActions().get(0);
        @SuppressWarnings("unchecked")
        var swmlData = (Map<String, Object>) action.get("SWML");
        assertEquals("true", swmlData.get("transfer"));
    }

    // ======== Join Conference ========
    // Parity with signalwire-python core/function_result.py join_conference:
    //   tests/unit/core/test_function_result.py::TestJoinConference.
    // The Java surface exposes all 18 optional params + the 7 validations the
    // Python reference has (record / trim / callback-method / max_participants /
    // beep / name). Each test pairs the behavioral call with an assertion on the
    // emitted SWML join_conference verb (no mocks).

    @SuppressWarnings("unchecked")
    private static Map<String, Object> joinConferenceVerb(FunctionResult result) {
        var action = result.getActions().get(0);
        var swml = (Map<String, Object>) action.get("SWML");
        var sections = (Map<String, Object>) swml.get("sections");
        var main = (List<Map<String, Object>>) sections.get("main");
        return main.get(0);
    }

    @Test
    void testJoinConferenceSimple() {
        // Parity: test_join_conference_simple_name_all_defaults.
        // All-defaults call emits the bare conference NAME string.
        var result = new FunctionResult("test")
                .joinConference("my-conference");

        var verb = joinConferenceVerb(result);
        assertEquals("my-conference", verb.get("join_conference"),
                "all-defaults form must emit the bare conference name string");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testJoinConferenceComplexParams() {
        // Parity: test_join_conference_complex_params. Non-default params emit
        // the full object form with each non-default key under snake_case.
        var result = new FunctionResult("test").joinConference(
                "team-meeting",
                /* muted */ true,
                /* beep */ "onEnter",
                /* startOnEnter */ false,
                /* endOnExit */ true,
                /* waitUrl */ "https://example.com/hold-music",
                /* maxParticipants */ 50,
                /* record */ "record-from-start",
                /* region */ "us-east",
                /* trim */ "do-not-trim",
                /* coach */ "call-id-123",
                /* statusCallbackEvent */ "start end",
                /* statusCallback */ "https://example.com/callback",
                /* statusCallbackMethod */ "GET",
                /* recordingStatusCallback */ "https://example.com/rec-callback",
                /* recordingStatusCallbackMethod */ "GET",
                /* recordingStatusCallbackEvent */ "in-progress",
                /* result */ Map.of("key", "value"));

        var verb = joinConferenceVerb(result);
        var join = (Map<String, Object>) verb.get("join_conference");
        assertEquals("team-meeting", join.get("name"));
        assertEquals(true, join.get("muted"));
        assertEquals("onEnter", join.get("beep"));
        assertEquals(false, join.get("start_on_enter"));
        assertEquals(true, join.get("end_on_exit"));
        assertEquals("https://example.com/hold-music", join.get("wait_url"));
        assertEquals(50, join.get("max_participants"));
        assertEquals("record-from-start", join.get("record"));
        assertEquals("us-east", join.get("region"));
        assertEquals("do-not-trim", join.get("trim"));
        assertEquals("call-id-123", join.get("coach"));
        assertEquals("start end", join.get("status_callback_event"));
        assertEquals("https://example.com/callback", join.get("status_callback"));
        assertEquals("GET", join.get("status_callback_method"));
        assertEquals("https://example.com/rec-callback", join.get("recording_status_callback"));
        assertEquals("GET", join.get("recording_status_callback_method"));
        assertEquals("in-progress", join.get("recording_status_callback_event"));
        assertEquals(Map.of("key", "value"), join.get("result"));
        // There is NO holdAudio/hold_audio key — Python uses wait_url for hold music.
        assertFalse(join.containsKey("hold_audio"));
        assertFalse(join.containsKey("holdAudio"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testJoinConferenceOnlyNonDefaultsEmitted() {
        // A param left at its default must NOT appear in the object form, while
        // a single non-default flips emission to the object form.
        var result = new FunctionResult("test")
                .joinConference("conf", true, "true", true, false, null, 250,
                        "do-not-record", null, "trim-silence", null, null, null,
                        "POST", null, "POST", "completed", null);

        var verb = joinConferenceVerb(result);
        var join = (Map<String, Object>) verb.get("join_conference");
        assertEquals("conf", join.get("name"));
        assertEquals(true, join.get("muted"));
        // Defaults must be absent.
        assertFalse(join.containsKey("beep"));
        assertFalse(join.containsKey("start_on_enter"));
        assertFalse(join.containsKey("max_participants"));
        assertFalse(join.containsKey("record"));
        assertFalse(join.containsKey("trim"));
        assertFalse(join.containsKey("status_callback_method"));
        assertFalse(join.containsKey("recording_status_callback_method"));
        assertFalse(join.containsKey("recording_status_callback_event"));
    }

    @Test
    void testJoinConferenceInvalidBeep() {
        // Parity: test_join_conference_invalid_beep.
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new FunctionResult("test").joinConference("conf", false, "invalid",
                        true, false, null, 250, "do-not-record", null, "trim-silence",
                        null, null, null, "POST", null, "POST", "completed", null));
        assertTrue(ex.getMessage().contains("beep must be one of"), ex.getMessage());
    }

    @Test
    void testJoinConferenceMaxParticipantsTooHigh() {
        // Parity: test_join_conference_max_participants_too_high.
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new FunctionResult("test").joinConference("conf", false, "true",
                        true, false, null, 300, "do-not-record", null, "trim-silence",
                        null, null, null, "POST", null, "POST", "completed", null));
        assertTrue(ex.getMessage().contains("max_participants must be a positive integer <= 250"),
                ex.getMessage());
    }

    @Test
    void testJoinConferenceMaxParticipantsZero() {
        // Parity: test_join_conference_max_participants_zero.
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new FunctionResult("test").joinConference("conf", false, "true",
                        true, false, null, 0, "do-not-record", null, "trim-silence",
                        null, null, null, "POST", null, "POST", "completed", null));
        assertTrue(ex.getMessage().contains("max_participants must be a positive integer <= 250"),
                ex.getMessage());
    }

    @Test
    void testJoinConferenceMaxParticipantsNegative() {
        // Parity: test_join_conference_max_participants_negative.
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new FunctionResult("test").joinConference("conf", false, "true",
                        true, false, null, -5, "do-not-record", null, "trim-silence",
                        null, null, null, "POST", null, "POST", "completed", null));
        assertTrue(ex.getMessage().contains("max_participants must be a positive integer <= 250"),
                ex.getMessage());
    }

    @Test
    void testJoinConferenceInvalidRecord() {
        // Parity: test_join_conference_invalid_record.
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new FunctionResult("test").joinConference("conf", false, "true",
                        true, false, null, 250, "always", null, "trim-silence",
                        null, null, null, "POST", null, "POST", "completed", null));
        assertTrue(ex.getMessage().contains("record must be one of"), ex.getMessage());
    }

    @Test
    void testJoinConferenceInvalidTrim() {
        // Parity: test_join_conference_invalid_trim.
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new FunctionResult("test").joinConference("conf", false, "true",
                        true, false, null, 250, "do-not-record", null, "bad-value",
                        null, null, null, "POST", null, "POST", "completed", null));
        assertTrue(ex.getMessage().contains("trim must be one of"), ex.getMessage());
    }

    @Test
    void testJoinConferenceEmptyName() {
        // Parity: test_join_conference_empty_name.
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new FunctionResult("test").joinConference("", true, "true",
                        true, false, null, 250, "do-not-record", null, "trim-silence",
                        null, null, null, "POST", null, "POST", "completed", null));
        assertTrue(ex.getMessage().contains("name cannot be empty"), ex.getMessage());
    }

    @Test
    void testJoinConferenceWhitespaceName() {
        // Parity: test_join_conference_whitespace_name.
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new FunctionResult("test").joinConference("   ", true, "true",
                        true, false, null, 250, "do-not-record", null, "trim-silence",
                        null, null, null, "POST", null, "POST", "completed", null));
        assertTrue(ex.getMessage().contains("name cannot be empty"), ex.getMessage());
    }

    @Test
    void testJoinConferenceInvalidStatusCallbackMethod() {
        // Parity: test_join_conference_invalid_status_callback_method.
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new FunctionResult("test").joinConference("conf", false, "true",
                        true, false, null, 250, "do-not-record", null, "trim-silence",
                        null, null, null, "PUT", null, "POST", "completed", null));
        assertTrue(ex.getMessage().contains("status_callback_method must be one of"),
                ex.getMessage());
    }

    @Test
    void testJoinConferenceInvalidRecordingStatusCallbackMethod() {
        // Parity: test_join_conference_invalid_recording_status_callback_method.
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new FunctionResult("test").joinConference("conf", false, "true",
                        true, false, null, 250, "do-not-record", null, "trim-silence",
                        null, null, null, "POST", null, "DELETE", "completed", null));
        assertTrue(ex.getMessage().contains("recording_status_callback_method must be one of"),
                ex.getMessage());
    }

    @Test
    void testJoinConferenceChaining() {
        // Parity: test_join_conference_chaining. Returns self for chaining.
        var result = new FunctionResult("test");
        var ret = result.joinConference("conf");
        assertSame(result, ret);
    }

    @Test
    void testJoinRoom() {
        var result = new FunctionResult("test")
                .joinRoom("my-room");

        assertTrue(result.getActions().get(0).containsKey("SWML"));
    }

    @Test
    void testSipRefer() {
        var result = new FunctionResult("test")
                .sipRefer("sip:user@example.com");

        assertTrue(result.getActions().get(0).containsKey("SWML"));
    }

    @Test
    void testTap() {
        var result = new FunctionResult("test")
                .tap("wss://example.com", "ctrl-1", "both", "PCMU");

        assertTrue(result.getActions().get(0).containsKey("SWML"));
    }

    @Test
    void testStopTap() {
        var result = new FunctionResult("test")
                .stopTap("ctrl-1");

        assertTrue(result.getActions().get(0).containsKey("SWML"));
    }

    @Test
    void testSendSms() {
        var result = new FunctionResult("SMS sent")
                .sendSms("+15551234567", "+15559876543", "Hello!", null, null);

        assertTrue(result.getActions().get(0).containsKey("SWML"));
    }

    @Test
    void testSendSmsRequiresBodyOrMedia() {
        assertThrows(IllegalArgumentException.class, () ->
                new FunctionResult("test").sendSms("+15551234567", "+15559876543", null, null, null));
    }

    @Test
    void testPay() {
        var result = new FunctionResult("Processing payment")
                .pay("https://connector.example.com", "dtmf", null, 600, 3);

        assertTrue(result.getActions().get(0).containsKey("SWML"));
    }

    // ======== RPC Actions ========

    @Test
    void testExecuteRpc() {
        var result = new FunctionResult("test")
                .executeRpc("ai_message", Map.of("role", "system", "message_text", "Hello"));

        assertTrue(result.getActions().get(0).containsKey("SWML"));
    }

    @Test
    void testRpcDial() {
        var result = new FunctionResult("Dialing")
                .rpcDial("+15551234567", "+15559876543", "https://example.com/swml");

        assertTrue(result.getActions().get(0).containsKey("SWML"));
    }

    @Test
    void testRpcAiMessage() {
        var result = new FunctionResult("Messaging")
                .rpcAiMessage("call-123", "Please take a message");

        assertTrue(result.getActions().get(0).containsKey("SWML"));
    }

    @Test
    void testRpcAiUnhold() {
        var result = new FunctionResult("Unholding")
                .rpcAiUnhold("call-123");

        assertTrue(result.getActions().get(0).containsKey("SWML"));
    }

    @Test
    void testSimulateUserInput() {
        var result = new FunctionResult("test")
                .simulateUserInput("Hello there");

        assertEquals("Hello there", result.getActions().get(0).get("user_input"));
    }

    // ======== Payment Helpers ========

    @Test
    void testCreatePaymentPrompt() {
        var prompt = FunctionResult.createPaymentPrompt(
                "payment-card-number",
                List.of(Map.of("type", "Say", "phrase", "Enter card number")),
                "visa mastercard",
                null
        );

        assertEquals("payment-card-number", prompt.get("for"));
        assertTrue(prompt.containsKey("actions"));
        assertEquals("visa mastercard", prompt.get("card_type"));
        assertFalse(prompt.containsKey("error_type"));
    }

    @Test
    void testCreatePaymentAction() {
        var action = FunctionResult.createPaymentAction("Say", "Enter your card");
        assertEquals("Say", action.get("type"));
        assertEquals("Enter your card", action.get("phrase"));
    }

    @Test
    void testCreatePaymentParameter() {
        var param = FunctionResult.createPaymentParameter("merchant_id", "12345");
        assertEquals("merchant_id", param.get("name"));
        assertEquals("12345", param.get("value"));
    }

    // ======== Fluent Chaining ========

    @Test
    void testFluentChaining() {
        var result = new FunctionResult("Multi-action result")
                .say("Let me check that")
                .updateGlobalData(Map.of("last_query", "weather"))
                .setEndOfSpeechTimeout(500)
                .enableExtensiveData(true);

        assertEquals(4, result.getActions().size());
        assertEquals("Multi-action result", result.getResponse());
    }

    @Test
    void testToJson() {
        var result = new FunctionResult("Hello").say("world");
        String json = result.toJson();
        assertNotNull(json);
        assertTrue(json.contains("\"response\":\"Hello\""));
        assertTrue(json.contains("\"action\""));
        assertTrue(json.contains("\"say\":\"world\""));
    }
}
