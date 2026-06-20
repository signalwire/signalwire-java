/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swml.RecordFormat;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Tests for FunctionResult covering all action categories. */
class FunctionResultTest {

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
    var result = new FunctionResult("test").addAction("say", "hello");
    var actions = result.getActions();
    assertEquals(1, actions.size());
    assertEquals("hello", actions.get(0).get("say"));
  }

  @Test
  void testAddMultipleActions() {
    var result =
        new FunctionResult("test")
            .addActions(List.of(Map.of("say", "hello"), Map.of("hangup", true)));
    assertEquals(2, result.getActions().size());
  }

  // ======== Call Control ========

  @Test
  void testConnect() {
    var result = new FunctionResult("Transferring").connect("+15551234567", true);

    var actions = result.getActions();
    assertEquals(1, actions.size());
    var action = actions.get(0);
    assertTrue(action.containsKey("SWML"));
    assertEquals("true", action.get("transfer"));
  }

  @Test
  void testConnectWithFrom() {
    var result = new FunctionResult("Transferring").connect("+15551234567", false, "+15559876543");

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
    var result =
        new FunctionResult("Transferring")
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
    var result = new FunctionResult("test").waitForUser(null, null, true);
    assertEquals("answer_first", result.getActions().get(0).get("wait_for_user"));
  }

  @Test
  void testWaitForUserWithTimeout() {
    var result = new FunctionResult("test").waitForUser(null, 30, false);
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
    var result =
        new FunctionResult("Updated").updateGlobalData(Map.of("key1", "value1", "key2", "value2"));

    var action = result.getActions().get(0);
    @SuppressWarnings("unchecked")
    var data = (Map<String, Object>) action.get("set_global_data");
    assertEquals("value1", data.get("key1"));
    assertEquals("value2", data.get("key2"));
  }

  @Test
  void testRemoveGlobalData() {
    var result = new FunctionResult("Removed").removeGlobalData(List.of("key1", "key2"));

    var action = result.getActions().get(0);
    assertTrue(action.containsKey("unset_global_data"));
  }

  @Test
  void testSetMetadata() {
    var result = new FunctionResult("Set").setMetadata(Map.of("meta_key", "meta_value"));

    var action = result.getActions().get(0);
    assertTrue(action.containsKey("set_meta_data"));
  }

  @Test
  void testRemoveMetadata() {
    var result = new FunctionResult("Removed").removeMetadata(List.of("meta_key"));

    var action = result.getActions().get(0);
    assertTrue(action.containsKey("unset_meta_data"));
  }

  @Test
  void testSwmlUserEvent() {
    var result =
        new FunctionResult("Event sent")
            .swmlUserEvent(Map.of("type", "test_event", "data", "hello"));

    var action = result.getActions().get(0);
    assertTrue(action.containsKey("SWML"));
  }

  @Test
  void testSwmlChangeStep() {
    var result = new FunctionResult("Changing step").swmlChangeStep("next_step");

    var action = result.getActions().get(0);
    assertEquals("next_step", action.get("change_step"));
  }

  @Test
  void testSwmlChangeContext() {
    var result = new FunctionResult("Changing context").swmlChangeContext("support");

    var action = result.getActions().get(0);
    assertEquals("support", action.get("change_context"));
  }

  @Test
  void testSwitchContextSimple() {
    var result = new FunctionResult("Switching").switchContext("You are now a support agent.");

    var action = result.getActions().get(0);
    assertEquals("You are now a support agent.", action.get("context_switch"));
  }

  @Test
  void testSwitchContextFull() {
    var result =
        new FunctionResult("Switching")
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
    var result = new FunctionResult("test").replaceInHistory("summary text");

    var action = result.getActions().get(0);
    assertEquals("summary text", action.get("replace_in_history"));
  }

  @Test
  void testReplaceInHistoryBoolean() {
    var result = new FunctionResult("test").replaceInHistory(true);

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
    var result = new FunctionResult("test").playBackgroundFile("music.mp3");

    assertEquals("music.mp3", result.getActions().get(0).get("playback_bg"));
  }

  @Test
  void testPlayBackgroundFileWithWait() {
    var result = new FunctionResult("test").playBackgroundFile("music.mp3", true);

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

  // ---- record_call: parity with Python tests/unit/core TestRecordCall ----
  // Each test pairs the behavioral call with an assertion on the emitted
  // SWML record_call verb (no mocks). The reference ALWAYS emits stereo /
  // format / direction / beep / input_sensitivity, validates format and
  // direction, and emits the 6 optional keys only when supplied.

  @SuppressWarnings("unchecked")
  private static Map<String, Object> swmlVerb(FunctionResult result, String verbName) {
    var action = result.getActions().get(0);
    var swml = (Map<String, Object>) action.get("SWML");
    var sections = (Map<String, Object>) swml.get("sections");
    var main = (List<Map<String, Object>>) sections.get("main");
    for (var item : main) {
      if (item.containsKey(verbName)) {
        return (Map<String, Object>) item.get(verbName);
      }
    }
    throw new AssertionError("verb '" + verbName + "' not found in SWML main");
  }

  @Test
  void testRecordCallDefaultParams() {
    // Parity: test_record_call_default_params. All-default call still emits
    // stereo/format/direction/beep/input_sensitivity; no control_id.
    var rec = swmlVerb(new FunctionResult().recordCall(), "record_call");
    assertEquals(false, rec.get("stereo"));
    assertEquals("wav", rec.get("format"));
    assertEquals("both", rec.get("direction"));
    assertEquals(false, rec.get("beep"));
    assertEquals(44.0, rec.get("input_sensitivity"));
    assertFalse(rec.containsKey("control_id"));
  }

  @Test
  void testRecordCallCustomParams() {
    // Parity: test_record_call_custom_params. Every param surfaces with its
    // snake_case wire key.
    var rec =
        swmlVerb(
            new FunctionResult()
                .recordCall(
                    "rec-1",
                    true,
                    "mp3",
                    "speak",
                    "#",
                    true,
                    50.0,
                    10.0,
                    5.0,
                    600.0,
                    "https://example.com/rec-status"),
            "record_call");
    assertEquals("rec-1", rec.get("control_id"));
    assertEquals(true, rec.get("stereo"));
    assertEquals("mp3", rec.get("format"));
    assertEquals("speak", rec.get("direction"));
    assertEquals("#", rec.get("terminators"));
    assertEquals(true, rec.get("beep"));
    assertEquals(50.0, rec.get("input_sensitivity"));
    assertEquals(10.0, rec.get("initial_timeout"));
    assertEquals(5.0, rec.get("end_silence_timeout"));
    assertEquals(600.0, rec.get("max_length"));
    assertEquals("https://example.com/rec-status", rec.get("status_url"));
  }

  @Test
  void testRecordCallOptionalsOmittedByDefault() {
    // The 6 optional keys must be absent when not supplied (beep /
    // input_sensitivity are NOT optional — they are always emitted).
    var rec = swmlVerb(new FunctionResult().recordCall(), "record_call");
    assertFalse(rec.containsKey("control_id"));
    assertFalse(rec.containsKey("terminators"));
    assertFalse(rec.containsKey("initial_timeout"));
    assertFalse(rec.containsKey("end_silence_timeout"));
    assertFalse(rec.containsKey("max_length"));
    assertFalse(rec.containsKey("status_url"));
  }

  @Test
  void testRecordCallInvalidFormat() {
    // Parity: test_record_call_invalid_format. Byte-exact Python message.
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new FunctionResult().recordCall(null, false, "ogg", "both"));
    assertTrue(ex.getMessage().contains("format must be 'wav', 'mp3', or 'mp4'"), ex.getMessage());
  }

  @Test
  void testRecordCallFormatMp4() {
    // Parity: test_record_call_format_mp4. mp4 is accepted.
    var rec = swmlVerb(new FunctionResult().recordCall(null, false, "mp4", "both"), "record_call");
    assertEquals("mp4", rec.get("format"));
  }

  @Test
  void testRecordCallInvalidDirection() {
    // Parity: test_record_call_invalid_direction. Byte-exact Python message.
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new FunctionResult().recordCall(null, false, "wav", "left"));
    assertTrue(
        ex.getMessage().contains("direction must be 'speak', 'listen', or 'both'"),
        ex.getMessage());
  }

  @Test
  void testRecordCallDirectionListen() {
    // Parity: test_record_call_direction_listen.
    var rec =
        swmlVerb(new FunctionResult().recordCall(null, false, "wav", "listen"), "record_call");
    assertEquals("listen", rec.get("direction"));
  }

  @Test
  void testRecordCallChaining() {
    // Parity: test_record_call_chaining.
    var result = new FunctionResult();
    assertSame(result, result.recordCall());
  }

  @Test
  void testRecordCallTypedFormatOverload() {
    // The RecordFormat-typed overload emits the identical wire format.
    var rec =
        swmlVerb(
            new FunctionResult().recordCall("c", true, RecordFormat.MP3, "both"), "record_call");
    assertEquals("mp3", rec.get("format"));
    assertEquals(false, rec.get("beep"));
    assertEquals(44.0, rec.get("input_sensitivity"));
  }

  @Test
  void testStopRecordCall() {
    var result = new FunctionResult("Stopped").stopRecordCall("ctrl-1");

    var stop = swmlVerb(result, "stop_record_call");
    assertEquals("ctrl-1", stop.get("control_id"));
  }

  @Test
  void testStopRecordCallWithoutControlId() {
    var stop = swmlVerb(new FunctionResult().stopRecordCall(), "stop_record_call");
    assertTrue(stop.isEmpty());
  }

  // ======== Speech & AI Configuration ========

  @Test
  void testAddDynamicHints() {
    var result = new FunctionResult("test").addDynamicHints(List.of("SignalWire", "SWML"));

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
    var result = new FunctionResult("test").setEndOfSpeechTimeout(500);

    assertEquals(500, result.getActions().get(0).get("end_of_speech_timeout"));
  }

  @Test
  void testSetSpeechEventTimeout() {
    var result = new FunctionResult("test").setSpeechEventTimeout(3000);

    assertEquals(3000, result.getActions().get(0).get("speech_event_timeout"));
  }

  @Test
  void testToggleFunctions() {
    var toggles =
        List.<Map<String, Object>>of(
            Map.of("function", "func1", "active", true),
            Map.of("function", "func2", "active", false));
    var result = new FunctionResult("test").toggleFunctions(toggles);

    assertTrue(result.getActions().get(0).containsKey("toggle_functions"));
  }

  @Test
  void testEnableFunctionsOnTimeout() {
    var result = new FunctionResult("test").enableFunctionsOnTimeout(true);

    assertEquals(true, result.getActions().get(0).get("functions_on_speaker_timeout"));
  }

  @Test
  void testEnableExtensiveData() {
    var result = new FunctionResult("test").enableExtensiveData(true);

    assertEquals(true, result.getActions().get(0).get("extensive_data"));
  }

  @Test
  void testUpdateSettings() {
    var result = new FunctionResult("test").updateSettings(Map.of("temperature", 0.5));

    var action = result.getActions().get(0);
    @SuppressWarnings("unchecked")
    var settings = (Map<String, Object>) action.get("settings");
    assertEquals(0.5, settings.get("temperature"));
  }

  // ======== Advanced Features ========

  @Test
  void testExecuteSwmlMap() {
    Map<String, Object> swml =
        Map.of("version", "1.0.0", "sections", Map.of("main", List.of(Map.of("hangup", Map.of()))));
    var result = new FunctionResult("test").executeSwml(swml);

    assertTrue(result.getActions().get(0).containsKey("SWML"));
  }

  @Test
  void testExecuteSwmlWithTransfer() {
    Map<String, Object> swml =
        Map.of("version", "1.0.0", "sections", Map.of("main", List.of(Map.of("hangup", Map.of()))));
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
    var result = new FunctionResult("test").joinConference("my-conference");

    var verb = joinConferenceVerb(result);
    assertEquals(
        "my-conference",
        verb.get("join_conference"),
        "all-defaults form must emit the bare conference name string");
  }

  @Test
  @SuppressWarnings("unchecked")
  void testJoinConferenceComplexParams() {
    // Parity: test_join_conference_complex_params. Non-default params emit
    // the full object form with each non-default key under snake_case.
    var result =
        new FunctionResult("test")
            .joinConference(
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
    var result =
        new FunctionResult("test")
            .joinConference(
                "conf",
                true,
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
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new FunctionResult("test")
                    .joinConference(
                        "conf",
                        false,
                        "invalid",
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
                        null));
    assertTrue(ex.getMessage().contains("beep must be one of"), ex.getMessage());
  }

  @Test
  void testJoinConferenceMaxParticipantsTooHigh() {
    // Parity: test_join_conference_max_participants_too_high.
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new FunctionResult("test")
                    .joinConference(
                        "conf",
                        false,
                        "true",
                        true,
                        false,
                        null,
                        300,
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
                        null));
    assertTrue(
        ex.getMessage().contains("max_participants must be a positive integer <= 250"),
        ex.getMessage());
  }

  @Test
  void testJoinConferenceMaxParticipantsZero() {
    // Parity: test_join_conference_max_participants_zero.
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new FunctionResult("test")
                    .joinConference(
                        "conf",
                        false,
                        "true",
                        true,
                        false,
                        null,
                        0,
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
                        null));
    assertTrue(
        ex.getMessage().contains("max_participants must be a positive integer <= 250"),
        ex.getMessage());
  }

  @Test
  void testJoinConferenceMaxParticipantsNegative() {
    // Parity: test_join_conference_max_participants_negative.
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new FunctionResult("test")
                    .joinConference(
                        "conf",
                        false,
                        "true",
                        true,
                        false,
                        null,
                        -5,
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
                        null));
    assertTrue(
        ex.getMessage().contains("max_participants must be a positive integer <= 250"),
        ex.getMessage());
  }

  @Test
  void testJoinConferenceInvalidRecord() {
    // Parity: test_join_conference_invalid_record.
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new FunctionResult("test")
                    .joinConference(
                        "conf",
                        false,
                        "true",
                        true,
                        false,
                        null,
                        250,
                        "always",
                        null,
                        "trim-silence",
                        null,
                        null,
                        null,
                        "POST",
                        null,
                        "POST",
                        "completed",
                        null));
    assertTrue(ex.getMessage().contains("record must be one of"), ex.getMessage());
  }

  @Test
  void testJoinConferenceInvalidTrim() {
    // Parity: test_join_conference_invalid_trim.
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new FunctionResult("test")
                    .joinConference(
                        "conf",
                        false,
                        "true",
                        true,
                        false,
                        null,
                        250,
                        "do-not-record",
                        null,
                        "bad-value",
                        null,
                        null,
                        null,
                        "POST",
                        null,
                        "POST",
                        "completed",
                        null));
    assertTrue(ex.getMessage().contains("trim must be one of"), ex.getMessage());
  }

  @Test
  void testJoinConferenceEmptyName() {
    // Parity: test_join_conference_empty_name.
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new FunctionResult("test")
                    .joinConference(
                        "",
                        true,
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
                        null));
    assertTrue(ex.getMessage().contains("name cannot be empty"), ex.getMessage());
  }

  @Test
  void testJoinConferenceWhitespaceName() {
    // Parity: test_join_conference_whitespace_name.
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new FunctionResult("test")
                    .joinConference(
                        "   ",
                        true,
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
                        null));
    assertTrue(ex.getMessage().contains("name cannot be empty"), ex.getMessage());
  }

  @Test
  void testJoinConferenceInvalidStatusCallbackMethod() {
    // Parity: test_join_conference_invalid_status_callback_method.
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new FunctionResult("test")
                    .joinConference(
                        "conf",
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
                        "PUT",
                        null,
                        "POST",
                        "completed",
                        null));
    assertTrue(ex.getMessage().contains("status_callback_method must be one of"), ex.getMessage());
  }

  @Test
  void testJoinConferenceInvalidRecordingStatusCallbackMethod() {
    // Parity: test_join_conference_invalid_recording_status_callback_method.
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new FunctionResult("test")
                    .joinConference(
                        "conf",
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
                        "DELETE",
                        "completed",
                        null));
    assertTrue(
        ex.getMessage().contains("recording_status_callback_method must be one of"),
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
    var result = new FunctionResult("test").joinRoom("my-room");

    assertTrue(result.getActions().get(0).containsKey("SWML"));
  }

  @Test
  void testSipRefer() {
    var result = new FunctionResult("test").sipRefer("sip:user@example.com");

    assertTrue(result.getActions().get(0).containsKey("SWML"));
  }

  // ---- tap: parity with Python tests/unit/core TestTap ----
  // Only uri is always emitted; direction/codec/rtp_ptime are emitted only
  // when non-default; direction/codec/rtp_ptime are validated.

  @Test
  void testTapDefaultParams() {
    // Parity: test_tap_default_params. Default params are absent.
    var tap =
        swmlVerb(new FunctionResult().tap("rtp://192.168.1.1:5000", null, "both", "PCMU"), "tap");
    assertEquals("rtp://192.168.1.1:5000", tap.get("uri"));
    assertFalse(tap.containsKey("direction"));
    assertFalse(tap.containsKey("codec"));
    assertFalse(tap.containsKey("rtp_ptime"));
  }

  @Test
  void testTapCustomParams() {
    // Parity: test_tap_custom_params. All custom params surface.
    var tap =
        swmlVerb(
            new FunctionResult()
                .tap(
                    "ws://example.com/tap",
                    "my-tap-1",
                    "speak",
                    "PCMA",
                    30,
                    "https://example.com/status"),
            "tap");
    assertEquals("ws://example.com/tap", tap.get("uri"));
    assertEquals("my-tap-1", tap.get("control_id"));
    assertEquals("speak", tap.get("direction"));
    assertEquals("PCMA", tap.get("codec"));
    assertEquals(30, tap.get("rtp_ptime"));
    assertEquals("https://example.com/status", tap.get("status_url"));
  }

  @Test
  void testTapInvalidDirection() {
    // Parity: test_tap_invalid_direction.
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new FunctionResult().tap("rtp://1.2.3.4:5000", null, "invalid", "PCMU"));
    assertTrue(ex.getMessage().contains("direction must be one of"), ex.getMessage());
  }

  @Test
  void testTapInvalidCodec() {
    // Parity: test_tap_invalid_codec. G729 is a RELAY codec, not a SWAIG tap codec.
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new FunctionResult().tap("rtp://1.2.3.4:5000", null, "both", "G729"));
    assertTrue(ex.getMessage().contains("codec must be one of"), ex.getMessage());
  }

  @Test
  void testTapInvalidRtpPtime() {
    // Parity: test_tap_invalid_rtp_ptime (rtp_ptime=0).
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new FunctionResult().tap("rtp://1.2.3.4:5000", null, "both", "PCMU", 0, null));
    assertTrue(ex.getMessage().contains("rtp_ptime must be a positive integer"), ex.getMessage());
  }

  @Test
  void testTapNegativeRtpPtime() {
    // Parity: test_tap_negative_rtp_ptime (rtp_ptime=-10).
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new FunctionResult().tap("rtp://1.2.3.4:5000", null, "both", "PCMU", -10, null));
    assertTrue(ex.getMessage().contains("rtp_ptime must be a positive integer"), ex.getMessage());
  }

  @Test
  void testTapDirectionHear() {
    // Parity: test_tap_direction_hear.
    var tap = swmlVerb(new FunctionResult().tap("rtp://1.2.3.4:5000", null, "hear", "PCMU"), "tap");
    assertEquals("hear", tap.get("direction"));
  }

  @Test
  void testTapChaining() {
    // Parity: test_tap_chaining.
    var result = new FunctionResult();
    assertSame(result, result.tap("rtp://1.2.3.4:5000", null, "both", "PCMU"));
  }

  @Test
  void testStopTap() {
    var stop = swmlVerb(new FunctionResult().stopTap("ctrl-1"), "stop_tap");
    assertEquals("ctrl-1", stop.get("control_id"));
  }

  @Test
  void testStopTapWithoutControlId() {
    var stop = swmlVerb(new FunctionResult().stopTap(), "stop_tap");
    assertTrue(stop.isEmpty());
  }

  // ---- send_sms: parity with Python tests/unit/core TestSendSms ----

  @Test
  void testSendSmsWithBody() {
    // Parity: test_send_sms_with_body.
    var sms =
        swmlVerb(
            new FunctionResult()
                .sendSms("+15551234567", "+15559876543", "Hello from AI", null, null),
            "send_sms");
    assertEquals("+15551234567", sms.get("to_number"));
    assertEquals("+15559876543", sms.get("from_number"));
    assertEquals("Hello from AI", sms.get("body"));
    assertFalse(sms.containsKey("media"));
  }

  @Test
  void testSendSmsWithMedia() {
    // Parity: test_send_sms_with_media.
    var sms =
        swmlVerb(
            new FunctionResult()
                .sendSms(
                    "+15551234567",
                    "+15559876543",
                    null,
                    List.of("https://example.com/image.png"),
                    null),
            "send_sms");
    assertFalse(sms.containsKey("body"));
    assertEquals(List.of("https://example.com/image.png"), sms.get("media"));
  }

  @Test
  void testSendSmsRequiresBodyOrMedia() {
    // Parity: test_send_sms_missing_both_raises_value_error.
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new FunctionResult().sendSms("+15551234567", "+15559876543", null, null, null));
    assertTrue(ex.getMessage().contains("Either body or media must be provided"), ex.getMessage());
  }

  @Test
  void testSendSmsWithTagsAndRegion() {
    // Parity: test_send_sms_with_tags_and_region. region is the restored param.
    var sms =
        swmlVerb(
            new FunctionResult()
                .sendSms(
                    "+15551234567",
                    "+15559876543",
                    "Tagged message",
                    null,
                    List.of("support", "urgent"),
                    "us-east"),
            "send_sms");
    assertEquals(List.of("support", "urgent"), sms.get("tags"));
    assertEquals("us-east", sms.get("region"));
  }

  @Test
  void testSendSmsChaining() {
    // Parity: test_send_sms_chaining.
    var result = new FunctionResult();
    assertSame(result, result.sendSms("+1555", "+1556", "hi", null, null));
  }

  // ---- pay: parity with Python tests/unit/core TestPay ----
  // The leading set{ai_response} verb plus the always-emitted pay keys; the
  // 16 restored params; postal_code bool->string; caller-overridable ai_response.

  @SuppressWarnings("unchecked")
  private static Map<String, Object> paySetVerb(FunctionResult result) {
    var action = result.getActions().get(0);
    var swml = (Map<String, Object>) action.get("SWML");
    var sections = (Map<String, Object>) swml.get("sections");
    var main = (List<Map<String, Object>>) sections.get("main");
    return (Map<String, Object>) main.get(0).get("set");
  }

  @Test
  void testPayDefaultParams() {
    // Parity: test_pay_default_params. The convenience 5-arg form leaves
    // the 14 other params at their reference defaults.
    var result = new FunctionResult().pay("https://pay.example.com/connector", "dtmf", null, 5, 1);
    // First main item is set{ai_response}.
    assertTrue(paySetVerb(result).containsKey("ai_response"));
    var pay = swmlVerb(result, "pay");
    assertEquals("https://pay.example.com/connector", pay.get("payment_connector_url"));
    assertEquals("dtmf", pay.get("input"));
    assertEquals("credit-card", pay.get("payment_method"));
    assertEquals("5", pay.get("timeout"));
    assertEquals("1", pay.get("max_attempts"));
    assertEquals("true", pay.get("security_code"));
    assertEquals("true", pay.get("postal_code"));
    assertEquals("0", pay.get("min_postal_code_length"));
    assertEquals("reusable", pay.get("token_type"));
    assertEquals("usd", pay.get("currency"));
    assertEquals("en-US", pay.get("language"));
    assertEquals("woman", pay.get("voice"));
    assertEquals("visa mastercard amex", pay.get("valid_card_types"));
    // Default ai_response is the reference string.
    assertEquals(FunctionResult.DEFAULT_PAY_AI_RESPONSE, paySetVerb(result).get("ai_response"));
  }

  @Test
  void testPayAllCustomParams() {
    // Parity: test_pay_all_custom_params. Every restored param round-trips,
    // numbers stringified, security_code=false, postal_code as a literal
    // postcode string, ai_response caller-overridden.
    var result =
        new FunctionResult()
            .pay(
                "https://pay.example.com",
                "voice",
                "https://status.example.com",
                "credit-card",
                10,
                3,
                false,
                "90210",
                5,
                "one-time",
                "49.99",
                "eur",
                "fr-FR",
                "man",
                "Monthly subscription",
                "visa amex",
                null,
                null,
                "Payment processed.");
    var pay = swmlVerb(result, "pay");
    assertEquals("voice", pay.get("input"));
    assertEquals("https://status.example.com", pay.get("status_url"));
    assertEquals("10", pay.get("timeout"));
    assertEquals("3", pay.get("max_attempts"));
    assertEquals("false", pay.get("security_code"));
    assertEquals("90210", pay.get("postal_code"));
    assertEquals("5", pay.get("min_postal_code_length"));
    assertEquals("one-time", pay.get("token_type"));
    assertEquals("49.99", pay.get("charge_amount"));
    assertEquals("eur", pay.get("currency"));
    assertEquals("fr-FR", pay.get("language"));
    assertEquals("man", pay.get("voice"));
    assertEquals("Monthly subscription", pay.get("description"));
    assertEquals("visa amex", pay.get("valid_card_types"));
    assertEquals("Payment processed.", paySetVerb(result).get("ai_response"));
  }

  @Test
  void testPayWithPromptsAndParameters() {
    // Parity: test_pay_with_prompts_and_parameters.
    var prompts =
        List.<Map<String, Object>>of(
            Map.of(
                "for",
                "payment-card-number",
                "actions",
                List.of(Map.of("type", "Say", "phrase", "Enter card"))));
    var parameters = List.<Map<String, String>>of(Map.of("name", "store_id", "value", "123"));
    var result =
        new FunctionResult()
            .pay(
                "https://pay.example.com",
                "dtmf",
                null,
                "credit-card",
                5,
                1,
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
                parameters,
                prompts,
                FunctionResult.DEFAULT_PAY_AI_RESPONSE);
    var pay = swmlVerb(result, "pay");
    assertEquals(prompts, pay.get("prompts"));
    assertEquals(parameters, pay.get("parameters"));
  }

  @Test
  void testPayPostalCodeBooleanFalse() {
    // Parity: test_pay_postal_code_boolean_false. Boolean.FALSE -> "false".
    var result =
        new FunctionResult()
            .pay(
                "https://pay.example.com",
                "dtmf",
                null,
                "credit-card",
                5,
                1,
                true,
                Boolean.FALSE,
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
                FunctionResult.DEFAULT_PAY_AI_RESPONSE);
    assertEquals("false", swmlVerb(result, "pay").get("postal_code"));
  }

  @Test
  void testPayOptionalsOmittedByDefault() {
    // status_url/charge_amount/description/parameters/prompts absent by default.
    var pay =
        swmlVerb(new FunctionResult().pay("https://pay.example.com", "dtmf", null, 5, 1), "pay");
    assertFalse(pay.containsKey("status_url"));
    assertFalse(pay.containsKey("charge_amount"));
    assertFalse(pay.containsKey("description"));
    assertFalse(pay.containsKey("parameters"));
    assertFalse(pay.containsKey("prompts"));
  }

  @Test
  void testPayChaining() {
    // Parity: test_pay_chaining.
    var result = new FunctionResult();
    assertSame(result, result.pay("https://pay.example.com", "dtmf", null, 5, 1));
  }

  // ======== RPC Actions ========

  @Test
  @SuppressWarnings("unchecked")
  void testExecuteRpc() {
    var result =
        new FunctionResult("test")
            .executeRpc("ai_message", Map.of("role", "system", "message_text", "Hello"));

    var rpc = swmlVerb(result, "execute_rpc");
    assertEquals("ai_message", rpc.get("method"));
    var params = (Map<String, Object>) rpc.get("params");
    assertEquals("system", params.get("role"));
    assertEquals("Hello", params.get("message_text"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testRpcDial() {
    // Parity: test_rpc_dial_basic. device_type defaults to "phone".
    var result =
        new FunctionResult("Dialing")
            .rpcDial("+15551234567", "+15559876543", "https://example.com/call-agent");

    var rpc = swmlVerb(result, "execute_rpc");
    assertEquals("dial", rpc.get("method"));
    var params = (Map<String, Object>) rpc.get("params");
    assertEquals("https://example.com/call-agent", params.get("dest_swml"));
    var devices = (Map<String, Object>) params.get("devices");
    assertEquals("phone", devices.get("type"));
    var devParams = (Map<String, Object>) devices.get("params");
    assertEquals("+15551234567", devParams.get("to_number"));
    assertEquals("+15559876543", devParams.get("from_number"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testRpcDialCustomDeviceType() {
    // Parity: test_rpc_dial_custom_device_type. device_type is no longer
    // hard-coded "phone".
    var result =
        new FunctionResult()
            .rpcDial("+15551234567", "+15559876543", "https://example.com/swml", "sip");
    var params = (Map<String, Object>) swmlVerb(result, "execute_rpc").get("params");
    var devices = (Map<String, Object>) params.get("devices");
    assertEquals("sip", devices.get("type"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testRpcAiMessage() {
    // Parity: test_rpc_ai_message_basic. role defaults to "system",
    // call_id is a top-level sibling of params.
    var result = new FunctionResult("Messaging").rpcAiMessage("call-abc", "Please take a message.");

    var rpc = swmlVerb(result, "execute_rpc");
    assertEquals("ai_message", rpc.get("method"));
    assertEquals("call-abc", rpc.get("call_id"));
    var params = (Map<String, Object>) rpc.get("params");
    assertEquals("system", params.get("role"));
    assertEquals("Please take a message.", params.get("message_text"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testRpcAiMessageCustomRole() {
    // Parity: test_rpc_ai_message_custom_role. role is no longer hard-coded.
    var result = new FunctionResult().rpcAiMessage("call-xyz", "User said hello", "user");
    var params = (Map<String, Object>) swmlVerb(result, "execute_rpc").get("params");
    assertEquals("user", params.get("role"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testRpcAiUnhold() {
    var result = new FunctionResult("Unholding").rpcAiUnhold("call-123");

    var rpc = swmlVerb(result, "execute_rpc");
    assertEquals("ai_unhold", rpc.get("method"));
    assertEquals("call-123", rpc.get("call_id"));
  }

  @Test
  void testSimulateUserInput() {
    var result = new FunctionResult("test").simulateUserInput("Hello there");

    assertEquals("Hello there", result.getActions().get(0).get("user_input"));
  }

  // ======== Payment Helpers ========

  @Test
  void testCreatePaymentPrompt() {
    var prompt =
        FunctionResult.createPaymentPrompt(
            "payment-card-number",
            List.of(Map.of("type", "Say", "phrase", "Enter card number")),
            "visa mastercard",
            null);

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
    var result =
        new FunctionResult("Multi-action result")
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
