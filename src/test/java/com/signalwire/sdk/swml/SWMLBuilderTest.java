/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the fluent {@link SWMLBuilder} — verb helpers emit exact wire keys, fluent chaining
 * returns this, and {@link SWMLBuilder#verb(String, Map)} dispatches schema verbs (Java analog of
 * Python {@code __getattr__}).
 */
class SWMLBuilderTest {

  private Service service;
  private SWMLBuilder builder;

  @BeforeEach
  void setUp() {
    service = new Service("builder-test");
    builder = new SWMLBuilder(service);
  }

  /** The list of verbs in the main section. */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> main() {
    Map<String, Object> sections = (Map<String, Object>) builder.build().get("sections");
    return (List<Map<String, Object>>) sections.get("main");
  }

  @Test
  void testAnswerEmptyConfig() {
    assertSame(builder, builder.answer());
    assertEquals(Map.of(), main().get(0).get("answer"));
  }

  @Test
  void testAnswerWithOptions() {
    builder.answer(30, "PCMU");
    assertEquals(Map.of("max_duration", 30, "codecs", "PCMU"), main().get(0).get("answer"));
  }

  @Test
  void testHangupReason() {
    builder.hangup("busy");
    assertEquals(Map.of("reason", "busy"), main().get(0).get("hangup"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testAiTextPromptWireShape() {
    builder.ai("you are helpful", null, null, null, null, null);
    // prompt must be an OBJECT {"text": ...}, never a bare string.
    Map<String, Object> ai = (Map<String, Object>) main().get(0).get("ai");
    assertEquals(Map.of("text", "you are helpful"), ai.get("prompt"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testAiPomPostPromptSwaigAndKwargs() {
    List<Map<String, Object>> pom = List.of(Map.of("title", "Role"));
    builder.ai(
        null,
        pom,
        "summarize",
        "https://ex.com/pp",
        Map.of("functions", List.of()),
        Map.of("temperature", 0.4));
    Map<String, Object> ai = (Map<String, Object>) main().get(0).get("ai");

    assertEquals(Map.of("pom", pom), ai.get("prompt"));
    assertEquals(Map.of("text", "summarize"), ai.get("post_prompt"));
    assertEquals("https://ex.com/pp", ai.get("post_prompt_url"));
    assertEquals(Map.of("functions", List.of()), ai.get("SWAIG"));
    // kwargs merge at top level (parity with Python config.update(kwargs)).
    assertEquals(0.4, (Double) ai.get("temperature"), 1e-9);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testPlayUrl() {
    builder.play("https://ex.com/a.mp3", null, 5.0, null, null, null, null);
    Map<String, Object> play = (Map<String, Object>) main().get(0).get("play");
    assertEquals("https://ex.com/a.mp3", play.get("url"));
    assertEquals(5.0, (Double) play.get("volume"), 1e-9);
    assertFalse(play.containsKey("urls"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testPlayUrlsList() {
    builder.play(null, List.of("a.mp3", "b.mp3"), null, null, null, null, null);
    Map<String, Object> play = (Map<String, Object>) main().get(0).get("play");
    assertEquals(List.of("a.mp3", "b.mp3"), play.get("urls"));
    assertFalse(play.containsKey("url"));
  }

  @Test
  void testPlayRequiresUrlOrUrls() {
    assertThrows(
        IllegalArgumentException.class,
        () -> builder.play(null, null, null, null, null, null, null));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testSayPrefixesUrl() {
    builder.say("hello there", "en-US-Neural", "en-US", null, null);
    Map<String, Object> play = (Map<String, Object>) main().get(0).get("play");
    assertEquals("say:hello there", play.get("url"));
    assertEquals("en-US-Neural", play.get("say_voice"));
    assertEquals("en-US", play.get("say_language"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testAddSection() {
    builder.addSection("intro");
    Map<String, Object> sections = (Map<String, Object>) builder.build().get("sections");
    assertTrue(sections.containsKey("intro"));
  }

  @Test
  void testResetClearsDocument() {
    builder.answer().hangup();
    assertFalse(main().isEmpty());
    assertSame(builder, builder.reset());
    assertTrue(main().isEmpty());
  }

  @Test
  void testRenderIsJsonString() {
    builder.answer();
    Type type = new TypeToken<Map<String, Object>>() {}.getType();
    Map<String, Object> parsed = new Gson().fromJson(builder.render(), type);
    @SuppressWarnings("unchecked")
    Map<String, Object> sections = (Map<String, Object>) parsed.get("sections");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> mainVerbs = (List<Map<String, Object>>) sections.get("main");
    assertTrue(mainVerbs.get(0).containsKey("answer"));
  }

  @Test
  void testFluentChainingReturnsThis() {
    SWMLBuilder result = builder.reset().answer().say("hi").hangup("done");
    assertSame(builder, result);
    assertEquals("answer", firstKey(main().get(0)));
    assertEquals("play", firstKey(main().get(1)));
    assertEquals("hangup", firstKey(main().get(2)));
  }

  // ---- verb() / sleepVerb() (Java analog of Python __getattr__) ----

  @Test
  void testVerbAutovivifiesSchemaVerb() {
    builder.verb("denoise", null);
    assertEquals(Map.of(), main().get(0).get("denoise"));
  }

  @Test
  void testSleepVerbIsBareInteger() {
    builder.sleepVerb(2000);
    // SWML `sleep` emits a raw integer, not a config object.
    assertEquals(2000, main().get(0).get("sleep"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testVerbPassesConfig() {
    builder.verb("record_call", Map.of("stereo", true, "format", "wav"));
    Map<String, Object> rc = (Map<String, Object>) main().get(0).get("record_call");
    assertEquals(true, rc.get("stereo"));
    assertEquals("wav", rc.get("format"));
  }

  @Test
  void testVerbDropsNullValues() {
    java.util.Map<String, Object> cfg = new java.util.LinkedHashMap<>();
    cfg.put("format", "wav");
    cfg.put("stereo", null);
    builder.verb("record_call", cfg);
    @SuppressWarnings("unchecked")
    Map<String, Object> rc = (Map<String, Object>) main().get(0).get("record_call");
    assertEquals(Map.of("format", "wav"), rc);
  }

  @Test
  void testUnknownVerbThrows() {
    assertThrows(IllegalArgumentException.class, () -> builder.verb("definitely_not_a_verb", null));
  }

  @Test
  void testVerbRejectsSleep() {
    // sleep must go through sleepVerb(int) — it emits a bare integer.
    assertThrows(IllegalArgumentException.class, () -> builder.verb("sleep", Map.of()));
  }

  private static String firstKey(Map<String, Object> m) {
    return m.keySet().iterator().next();
  }
}
