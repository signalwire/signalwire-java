/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Port of the reference tests/unit/core/test_post_prompt_normalize.py. */
class PostPromptTest {

  private static final String FENCED =
      "```json\n{\"summary\": \"s\", \"already_answered\": [\"pricing\"]}\n```";

  private static Map<String, Object> withNull(String key) {
    Map<String, Object> m = new HashMap<>();
    m.put(key, null);
    return m;
  }

  // ── parse shapes ──────────────────────────────────────────────────

  @Test
  void flatKeysFromTheVoiceEngine() {
    assertEquals(
        Map.of("summary", "s", "user_goal", "g"),
        PostPrompt.parsePostPromptData(Map.of("summary", "s", "user_goal", "g")));
  }

  @Test
  void fencedRawFromTheChatEngine() {
    assertEquals(
        Map.of("summary", "s", "already_answered", List.of("pricing")),
        PostPrompt.parsePostPromptData(Map.of("raw", FENCED)));
  }

  @Test
  void objectWrappedInAListUnderParsed() {
    assertEquals(
        Map.of("summary", "s3"),
        PostPrompt.parsePostPromptData(
            Map.of("parsed", List.of(Map.of("summary", "s3")), "raw", "...")));
  }

  @Test
  void parsedWrapperWinsOverTheGenericSweep() {
    var result = PostPrompt.parsePostPromptData(Map.of("parsed", List.of(Map.of("summary", "s"))));
    assertFalse(result.containsKey("parsed"));
  }

  @Test
  void parsedAsABareMap() {
    assertEquals(
        Map.of("summary", "s"),
        PostPrompt.parsePostPromptData(Map.of("parsed", Map.of("summary", "s"))));
  }

  @Test
  void proseInsteadOfJsonIsKept() {
    assertEquals(
        Map.of("summary", "They asked about pricing."),
        PostPrompt.parsePostPromptData(Map.of("raw", "They asked about pricing.")));
  }

  @Test
  void jsonThatIsNotAnObject() {
    assertEquals(
        Map.of("summary", "just a string"),
        PostPrompt.parsePostPromptData(Map.of("raw", "\"just a string\"")));
  }

  @Test
  void junkDegradesRatherThanThrowing() {
    for (Object junk :
        Arrays.asList(
            null,
            Map.of(),
            "text",
            42,
            List.of(),
            Map.of("raw", ""),
            Map.of("raw", "   "),
            withNull("raw"))) {
      assertEquals(Map.of(), PostPrompt.parsePostPromptData(junk), "junk: " + junk);
    }
  }

  // ── strip fence ───────────────────────────────────────────────────

  @Test
  void stripJsonFenceUnwraps() {
    assertEquals("{\"a\":1}", PostPrompt.stripJsonFence("```json\n{\"a\":1}\n```"));
    assertEquals("plain", PostPrompt.stripJsonFence("```\nplain\n```"));
    assertEquals("no fence at all", PostPrompt.stripJsonFence("no fence at all"));
    assertEquals("", PostPrompt.stripJsonFence(""));
  }

  // ── dialogue turns ────────────────────────────────────────────────

  private static List<Object> log() {
    return new ArrayList<>(
        List.of(
            Map.of("role", "user", "content", "hi"),
            Map.of("role", "assistant", "content", "hello"),
            Map.of("role", "system", "content", "the prompt"),
            Map.of("role", "system-log", "content", "step trace"),
            Map.of("role", "tool", "content", "tool output"),
            Map.of("role", "assistant", "content", "", "tool_calls", List.of(Map.of("id", 1))),
            Map.of("role", "assistant-manual", "content", "let me look that up"),
            Map.of("role", "assistant", "content", "   "),
            "not even a dict"));
  }

  @Test
  void keepsOnlyRealDialogue() {
    assertEquals(
        List.of(
            Map.of("role", "user", "content", "hi"),
            Map.of("role", "assistant", "content", "hello")),
        PostPrompt.dialogueTurns(log()));
  }

  @Test
  void dropsTheChatSummaryEcho() {
    var l = log();
    l.add(Map.of("role", "assistant", "content", FENCED));
    assertFalse(
        PostPrompt.dialogueTurns(l, PostPrompt.DIALOGUE_ROLES, FENCED)
            .contains(Map.of("role", "assistant", "content", FENCED)));
  }

  @Test
  void keepsTheEchoWhenNotAskedToDropIt() {
    var l = log();
    l.add(Map.of("role", "assistant", "content", FENCED));
    assertEquals(3, PostPrompt.dialogueTurns(l).size());
  }

  @Test
  void junkLogsYieldNothing() {
    for (Object junk : Arrays.asList(null, List.of(), "nonsense", 42)) {
      assertEquals(List.of(), PostPrompt.dialogueTurns(junk), "junk: " + junk);
    }
  }

  // ── normalize ─────────────────────────────────────────────────────

  @Test
  void voiceBody() {
    var result =
        PostPrompt.normalizePostPrompt(
            Map.of(
                "conversation_type",
                "voice",
                "call_id",
                "c-1",
                "post_prompt_data",
                Map.of("parsed", List.of(Map.of("summary", "v"))),
                "raw_call_log",
                List.of(Map.of("role", "user", "content", "hi"))));
    assertEquals("voice", result.medium());
    assertNull(result.conversationId());
    assertEquals(Map.of("summary", "v"), result.summary());
    assertEquals("c-1", result.callId());
    assertEquals(1, result.dialogue().size());
  }

  @Test
  void chatBody() {
    var result =
        PostPrompt.normalizePostPrompt(
            Map.of(
                "conversation_type",
                "chat",
                "conversation_id",
                "conv-9",
                "post_prompt_data",
                Map.of("raw", FENCED),
                "raw_messages",
                List.of(
                    Map.of("role", "user", "content", "hi"),
                    Map.of("role", "assistant", "content", FENCED))));
    assertEquals("chat", result.medium());
    assertEquals("conv-9", result.conversationId());
    assertEquals(List.of("pricing"), result.summary().get("already_answered"));
    assertEquals(List.of(Map.of("role", "user", "content", "hi")), result.dialogue());
  }

  @Test
  void callLogKeyIsAlsoAccepted() {
    var result =
        PostPrompt.normalizePostPrompt(
            Map.of("call_log", List.of(Map.of("role", "user", "content", "hi"))));
    assertEquals(1, result.dialogue().size());
  }

  @Test
  void junkBodyYieldsEmptyFields() {
    for (Object junk : Arrays.asList(null, "text", 42, List.of())) {
      var result = PostPrompt.normalizePostPrompt(junk);
      assertEquals("", result.medium());
      assertEquals(Map.of(), result.summary());
      assertTrue(result.dialogue().isEmpty());
    }
  }

  @Test
  void rawIsPreserved() {
    Map<String, Object> body = Map.of("conversation_type", "voice", "extra", "kept");
    assertSame(body, PostPrompt.normalizePostPrompt(body).raw());
  }
}
