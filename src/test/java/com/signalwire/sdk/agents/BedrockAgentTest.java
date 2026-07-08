/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.agents;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** WIRE tests for {@link BedrockAgent} — the rendered SWML must carry amazon_bedrock, not ai. */
class BedrockAgentTest {

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> mainSection(Map<String, Object> swml) {
    Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
    return (List<Map<String, Object>>) sections.get("main");
  }

  private Map<String, Object> findVerb(List<Map<String, Object>> main, String key) {
    for (Map<String, Object> verb : main) {
      if (verb.containsKey(key)) {
        return verb;
      }
    }
    return null;
  }

  @Test
  void testDefaults() {
    BedrockAgent agent = new BedrockAgent();
    assertEquals("bedrock_agent", agent.getName());
    assertEquals("/bedrock", agent.getRoute());
    assertEquals("matthew", agent.getVoiceId());
    assertEquals(0.7, agent.getTemperature());
    assertEquals(0.9, agent.getTopP());
    assertEquals(1024, agent.getMaxTokens());
  }

  @Test
  void testRenderSwmlEmitsAmazonBedrockNotAi() {
    BedrockAgent agent = new BedrockAgent();
    agent.setPromptText("You are helpful.");
    Map<String, Object> swml = agent.renderSwml("https://example.com/bedrock");
    List<Map<String, Object>> main = mainSection(swml);

    assertNull(findVerb(main, "ai"), "the ai verb must be replaced");
    assertNotNull(findVerb(main, "amazon_bedrock"), "amazon_bedrock verb must be present");
  }

  @Test
  @SuppressWarnings("unchecked")
  void testVoiceAndInferenceParamsInsidePrompt() {
    BedrockAgent agent = new BedrockAgent();
    agent.setPromptText("You are helpful.");
    Map<String, Object> swml = agent.renderSwml("https://example.com/bedrock");
    Map<String, Object> verb = findVerb(mainSection(swml), "amazon_bedrock");
    Map<String, Object> bedrock = (Map<String, Object>) verb.get("amazon_bedrock");
    Map<String, Object> prompt = (Map<String, Object>) bedrock.get("prompt");

    assertEquals("matthew", prompt.get("voice_id"));
    assertEquals(0.7, prompt.get("temperature"));
    assertEquals(0.9, prompt.get("top_p"));
    // The original prompt text is preserved.
    assertEquals("You are helpful.", prompt.get("text"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testSetVoiceAndInferenceParamsReflectedInWire() {
    BedrockAgent agent = new BedrockAgent();
    agent.setPromptText("hi");
    agent.setVoice("joanna").setInferenceParams(0.2, 0.5, 2048);

    Map<String, Object> verb =
        findVerb(mainSection(agent.renderSwml("https://x")), "amazon_bedrock");
    Map<String, Object> prompt =
        (Map<String, Object>) ((Map<String, Object>) verb.get("amazon_bedrock")).get("prompt");
    assertEquals("joanna", prompt.get("voice_id"));
    assertEquals(0.2, prompt.get("temperature"));
    assertEquals(0.5, prompt.get("top_p"));
    assertEquals(2048, agent.getMaxTokens());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testTextModelOnlyKeysStripped() throws Exception {
    BedrockAgent agent = new BedrockAgent();
    // BedrockAgent overrides setPromptLlmParams to a warning no-op (Python parity), so the
    // text-model-only keys can never be injected via public API on a BedrockAgent. The stripping
    // is defensive: it must remove these keys should any base render/mixin path place them in
    // ai.prompt. Seed the inherited promptLlmParams field directly so the base render merges them
    // into ai.prompt, then assert the Bedrock transform strips them from the WIRE output.
    Map<String, Object> llm = new LinkedHashMap<>();
    llm.put("barge_confidence", 0.5);
    llm.put("presence_penalty", 0.1);
    llm.put("frequency_penalty", 0.2);
    llm.put("some_other", "kept");
    java.lang.reflect.Field f =
        com.signalwire.sdk.agent.AgentBase.class.getDeclaredField("promptLlmParams");
    f.setAccessible(true);
    ((Map<String, Object>) f.get(agent)).putAll(llm);
    agent.setPromptText("hi");

    Map<String, Object> verb =
        findVerb(mainSection(agent.renderSwml("https://x")), "amazon_bedrock");
    Map<String, Object> prompt =
        (Map<String, Object>) ((Map<String, Object>) verb.get("amazon_bedrock")).get("prompt");
    assertFalse(prompt.containsKey("barge_confidence"));
    assertFalse(prompt.containsKey("presence_penalty"));
    assertFalse(prompt.containsKey("frequency_penalty"));
    assertEquals("kept", prompt.get("some_other"));
  }

  @Test
  void testSetLlmModelWarnsAndNoOps() {
    BedrockAgent agent = new BedrockAgent();
    assertSame(agent, agent.setLlmModel("gpt-4"));
  }

  @Test
  void testSetLlmTemperatureRedirects() {
    BedrockAgent agent = new BedrockAgent();
    agent.setLlmTemperature(0.33);
    assertEquals(0.33, agent.getTemperature());
  }

  @Test
  void testSetPromptAndPostPromptLlmParamsNoOp() {
    BedrockAgent agent = new BedrockAgent();
    assertSame(agent, agent.setPromptLlmParams(Map.of("temperature", 0.9)));
    assertSame(agent, agent.setPostPromptLlmParams(Map.of("temperature", 0.9)));
  }

  @Test
  void testToStringRepr() {
    BedrockAgent agent = new BedrockAgent("my_bot", "/bot");
    assertEquals("BedrockAgent(name='my_bot', route='/bot', voice='matthew')", agent.toString());
  }

  @Test
  void testSystemPromptConstructorSeedsPrompt() {
    BedrockAgent agent =
        new BedrockAgent("b", "/bedrock", "Seed prompt", "matthew", 0.7, 0.9, 1024);
    assertEquals("Seed prompt", agent.getRawPrompt());
  }
}
