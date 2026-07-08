/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signalwire.sdk.agent.AgentBase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behavioral contract #2 (BEHAVIORAL_CONTRACTS.md): {@code setPromptLlmParams} / {@code
 * setPostPromptLlmParams} MERGE across calls (mirroring Python's {@code
 * self._prompt_llm_params.update(params)} in ai_config_mixin.py), rather than REPLACE.
 *
 * <p>Two successive calls with distinct keys must BOTH survive into the rendered SWML AI config.
 * The prior implementation cleared the map on every call, so the first key was dropped — these
 * tests would have failed against that replace-stub.
 */
class PromptLlmParamsMergeTest {

  @SuppressWarnings("unchecked")
  private static Map<String, Object> aiVerb(AgentBase agent) {
    Map<String, Object> swml = agent.renderSwml(null);
    Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
    List<Object> main = (List<Object>) sections.get("main");
    for (Object verb : main) {
      Map<String, Object> verbMap = (Map<String, Object>) verb;
      if (verbMap.containsKey("ai")) {
        return (Map<String, Object>) verbMap.get("ai");
      }
    }
    throw new AssertionError("no ai verb in rendered SWML: " + swml);
  }

  @Test
  @DisplayName("setPromptLlmParams merges across calls (both keys present, not just the last)")
  void promptLlmParamsMerge() {
    AgentBase agent = AgentBase.builder().name("merge").route("/").build();
    agent.setPromptText("hi");
    // Two calls with DISTINCT keys — a replace-stub would keep only top_p.
    agent.setPromptLlmParams(Map.of("temperature", 0.5));
    agent.setPromptLlmParams(Map.of("top_p", 0.9));

    Map<String, Object> ai = aiVerb(agent);
    @SuppressWarnings("unchecked")
    Map<String, Object> prompt = (Map<String, Object>) ai.get("prompt");

    assertTrue(prompt.containsKey("temperature"), "temperature must survive the second set call");
    assertTrue(prompt.containsKey("top_p"), "top_p must be present after the second set call");
    assertEquals(0.5, ((Number) prompt.get("temperature")).doubleValue(), 1e-9);
    assertEquals(0.9, ((Number) prompt.get("top_p")).doubleValue(), 1e-9);
  }

  @Test
  @DisplayName("setPostPromptLlmParams merges across calls (both keys present, not just the last)")
  void postPromptLlmParamsMerge() {
    AgentBase agent = AgentBase.builder().name("merge").route("/").build();
    agent.setPromptText("hi");
    agent.setPostPrompt("summarize");
    agent.setPostPromptLlmParams(Map.of("temperature", 0.3));
    agent.setPostPromptLlmParams(Map.of("top_p", 0.7));

    Map<String, Object> ai = aiVerb(agent);
    @SuppressWarnings("unchecked")
    Map<String, Object> postPrompt = (Map<String, Object>) ai.get("post_prompt");

    assertTrue(
        postPrompt.containsKey("temperature"), "temperature must survive the second set call");
    assertTrue(postPrompt.containsKey("top_p"), "top_p must be present after the second set call");
    assertEquals(0.3, ((Number) postPrompt.get("temperature")).doubleValue(), 1e-9);
    assertEquals(0.7, ((Number) postPrompt.get("top_p")).doubleValue(), 1e-9);
  }
}
