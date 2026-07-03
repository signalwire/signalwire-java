/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core.agent.prompt;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** Tests for {@link PromptManager}. */
class PromptManagerTest {

  @Test
  void testRawTextPrompt() {
    PromptManager pm = new PromptManager();
    pm.setPromptText("hello");
    assertEquals("hello", pm.getPrompt());
    assertEquals("hello", pm.getRawPrompt());
  }

  @Test
  void testPostPrompt() {
    PromptManager pm = new PromptManager();
    assertNull(pm.getPostPrompt());
    pm.setPostPrompt("summarize");
    assertEquals("summarize", pm.getPostPrompt());
  }

  @Test
  void testEmptyPromptReturnsNull() {
    assertNull(new PromptManager().getPrompt());
    assertNull(new PromptManager().getRawPrompt());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testPomSectionsPrompt() {
    PromptManager pm = new PromptManager();
    pm.promptAddSection("Role", "You are helpful.");
    Object prompt = pm.getPrompt();
    assertTrue(prompt instanceof List);
    List<Map<String, Object>> sections = (List<Map<String, Object>>) prompt;
    assertEquals(1, sections.size());
    assertEquals("Role", sections.get(0).get("title"));
    assertTrue(pm.promptHasSection("Role"));
    assertFalse(pm.promptHasSection("Missing"));
  }

  @Test
  void testTextThenPomSectionThrows() {
    // Oracle semantics: validation runs BEFORE the mutating action, checking (prompt_text set AND
    // pom non-empty). The FIRST prompt_add_section after set_prompt_text passes (pom still empty
    // at check time); the SECOND trips the guard, because pom is now non-empty AND prompt_text is
    // set. Parity with Python's `if self._prompt_text and ... self.agent.pom`.
    PromptManager pm = new PromptManager();
    pm.setPromptText("raw");
    pm.promptAddSection("A", "body"); // pom empty at check → allowed, adds section
    assertThrows(
        IllegalArgumentException.class,
        () -> pm.promptAddSection("B", "body2")); // pom now non-empty → throws
  }

  @Test
  void testPomSectionThenTextDoesNotThrow() {
    // The reverse order does NOT throw (parity with Python/Ruby): setPromptText validates before
    // setting promptText, so at check time promptText is still null and the guard is false.
    PromptManager pm = new PromptManager();
    pm.promptAddSection("A", "body");
    assertDoesNotThrow(() -> pm.setPromptText("raw"));
    // Raw text now wins in getPrompt (promptText is non-null).
    assertEquals("raw", pm.getPrompt());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testPromptAddToSectionAppendsBody() {
    PromptManager pm = new PromptManager();
    pm.promptAddToSection("Notes", "first", null, null);
    pm.promptAddToSection("Notes", "second", null, null);
    List<Map<String, Object>> sections = (List<Map<String, Object>>) pm.getPrompt();
    assertEquals("first\n\nsecond", sections.get(0).get("body"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testPromptAddToSectionAppendsBullets() {
    PromptManager pm = new PromptManager();
    pm.promptAddToSection("List", null, "one", null);
    pm.promptAddToSection("List", null, null, List.of("two", "three"));
    List<Map<String, Object>> sections = (List<Map<String, Object>>) pm.getPrompt();
    assertEquals(List.of("one", "two", "three"), sections.get(0).get("bullets"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testPromptAddSubsection() {
    PromptManager pm = new PromptManager();
    pm.promptAddSubsection("Parent", "Child", "child body", null);
    List<Map<String, Object>> sections = (List<Map<String, Object>>) pm.getPrompt();
    Map<String, Object> parent = sections.get(0);
    assertEquals("Parent", parent.get("title"));
    List<Map<String, Object>> subs = (List<Map<String, Object>>) parent.get("subsections");
    assertEquals("Child", subs.get(0).get("title"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testPromptAddSectionWithSubsections() {
    PromptManager pm = new PromptManager();
    List<Map<String, Object>> subs = new ArrayList<>();
    subs.add(Map.of("title", "Sub1", "body", "b1"));
    pm.promptAddSection("Main", "main body", null, false, false, subs);
    List<Map<String, Object>> sections = (List<Map<String, Object>>) pm.getPrompt();
    List<Map<String, Object>> gotSubs =
        (List<Map<String, Object>>) sections.get(0).get("subsections");
    assertEquals("Sub1", gotSubs.get(0).get("title"));
  }

  @Test
  void testSetPromptPomReplacesRawText() {
    PromptManager pm = new PromptManager();
    pm.setPromptText("raw");
    List<Map<String, Object>> pom = new ArrayList<>();
    pom.add(new LinkedHashMap<>(Map.of("title", "S", "body", "b")));
    pm.setPromptPom(pom);
    assertNull(pm.getRawPrompt());
    assertTrue(pm.getPrompt() instanceof List);
  }

  @Test
  void testDefineContextsWithMapTakesPrecedence() {
    PromptManager pm = new PromptManager();
    pm.setPromptText("raw");
    Map<String, Object> ctx = Map.of("contexts", Map.of("default", Map.of()));
    pm.defineContexts(ctx);
    assertEquals(ctx, pm.getContexts());
    // Contexts take precedence — getPrompt returns null even though text was set.
    assertNull(pm.getPrompt());
  }

  @Test
  void testDefineContextsRejectsInvalid() {
    PromptManager pm = new PromptManager();
    assertThrows(IllegalArgumentException.class, () -> pm.defineContexts("not a map"));
  }
}
