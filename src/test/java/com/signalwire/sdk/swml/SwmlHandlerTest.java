/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.swml.SWMLVerbHandler.ValidationResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the SWML verb-handler trio ({@link SWMLVerbHandler} / {@link AIVerbHandler} / {@link
 * VerbHandlerRegistry}) — build/validate configs and registry round-trips.
 */
class SwmlHandlerTest {

  private AIVerbHandler handler;

  @BeforeEach
  void setUp() {
    handler = new AIVerbHandler();
  }

  // ---- AIVerbHandler ----

  @Test
  void testGetVerbName() {
    assertEquals("ai", handler.getVerbName());
  }

  @Test
  void testBuildConfigTextPromptWireKeys() {
    Map<String, Object> config = handler.buildConfig("hello", null, null, null, null, null, null);
    // Exact emitted key/value shape — prompt must be an object {"text": ...}.
    assertEquals(Map.of("text", "hello"), config.get("prompt"));
    assertEquals(Map.of(), config.get("params"));
  }

  @Test
  void testBuildConfigPomPrompt() {
    List<Map<String, Object>> pom = List.of(Map.of("title", "Role", "body", "assistant"));
    Map<String, Object> config = handler.buildConfig(null, pom, null, null, null, null, null);
    assertEquals(Map.of("pom", pom), config.get("prompt"));
  }

  @Test
  void testBuildConfigRoutesTopLevelKeys() {
    Map<String, Object> kwargs = new LinkedHashMap<>();
    kwargs.put("languages", List.of(Map.of("code", "en")));
    kwargs.put("hints", List.of("foo"));
    kwargs.put("pronounce", List.of(Map.of("x", "y")));
    kwargs.put("global_data", Map.of("k", "v"));
    Map<String, Object> config = handler.buildConfig("hi", null, null, null, null, null, kwargs);

    // languages/hints/pronounce/global_data go to top level.
    assertEquals(List.of(Map.of("code", "en")), config.get("languages"));
    assertEquals(List.of("foo"), config.get("hints"));
    assertEquals(List.of(Map.of("x", "y")), config.get("pronounce"));
    assertEquals(Map.of("k", "v"), config.get("global_data"));
    // ...and not into params.
    assertEquals(Map.of(), config.get("params"));
  }

  @Test
  void testBuildConfigRoutesOtherKeysIntoParams() {
    Map<String, Object> kwargs = new LinkedHashMap<>();
    kwargs.put("temperature", 0.7);
    kwargs.put("top_p", 0.9);
    Map<String, Object> config = handler.buildConfig("hi", null, null, null, null, null, kwargs);
    // Everything not a recognised top-level key goes into params.
    assertEquals(Map.of("temperature", 0.7, "top_p", 0.9), config.get("params"));
  }

  @Test
  void testBuildConfigPostPromptAndSwaig() {
    Map<String, Object> swaig = Map.of("functions", List.of());
    Map<String, Object> config =
        handler.buildConfig("hi", null, null, "summarize", "https://ex.com/pp", swaig, null);

    assertEquals(Map.of("text", "summarize"), config.get("post_prompt"));
    assertEquals("https://ex.com/pp", config.get("post_prompt_url"));
    assertEquals(swaig, config.get("SWAIG"));
  }

  @Test
  void testBuildConfigContextsInPrompt() {
    Map<String, Object> contexts = Map.of("default", Map.of("steps", List.of()));
    Map<String, Object> config = handler.buildConfig("hi", null, contexts, null, null, null, null);
    @SuppressWarnings("unchecked")
    Map<String, Object> prompt = (Map<String, Object>) config.get("prompt");
    assertEquals(contexts, prompt.get("contexts"));
  }

  @Test
  void testBuildConfigRequiresABasePrompt() {
    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class,
            () -> handler.buildConfig(null, null, null, null, null, null, null));
    assertTrue(err.getMessage().contains("must be provided as base prompt"));
  }

  @Test
  void testBuildConfigRejectsBothPrompts() {
    IllegalArgumentException err =
        assertThrows(
            IllegalArgumentException.class,
            () -> handler.buildConfig("a", List.of(Map.of("x", 1)), null, null, null, null, null));
    assertTrue(err.getMessage().contains("mutually exclusive"));
  }

  @Test
  void testValidateConfigValid() {
    ValidationResult r = handler.validateConfig(Map.of("prompt", Map.of("text", "hi")));
    assertTrue(r.isValid());
    assertTrue(r.getErrors().isEmpty());
  }

  @Test
  void testValidateConfigMissingPrompt() {
    ValidationResult r = handler.validateConfig(Map.of());
    assertFalse(r.isValid());
    assertTrue(r.getErrors().contains("Missing required field 'prompt'"));
  }

  @Test
  void testValidateConfigPromptNotObject() {
    ValidationResult r = handler.validateConfig(Map.of("prompt", "a bare string"));
    assertFalse(r.isValid());
    assertTrue(r.getErrors().contains("'prompt' must be an object"));
  }

  @Test
  void testValidateConfigBothTextAndPom() {
    Map<String, Object> prompt = new LinkedHashMap<>();
    prompt.put("text", "a");
    prompt.put("pom", List.of());
    ValidationResult r = handler.validateConfig(Map.of("prompt", prompt));
    assertFalse(r.isValid());
    assertTrue(r.getErrors().stream().anyMatch(e -> e.contains("mutually exclusive")));
  }

  @Test
  void testValidateConfigBadSwaig() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("prompt", Map.of("text", "a"));
    config.put("SWAIG", "nope");
    ValidationResult r = handler.validateConfig(config);
    assertFalse(r.isValid());
    assertTrue(r.getErrors().contains("'SWAIG' must be an object"));
  }

  // ---- SWMLVerbHandler base + VerbHandlerRegistry ----

  @Test
  void testBaseHandlerAbstractMethodsThrow() {
    SWMLVerbHandler base = new SWMLVerbHandler() {};
    assertThrows(UnsupportedOperationException.class, base::getVerbName);
    assertThrows(UnsupportedOperationException.class, () -> base.validateConfig(Map.of()));
    assertThrows(UnsupportedOperationException.class, () -> base.buildConfig(Map.of()));
  }

  @Test
  void testRegistryRegistersAiByDefault() {
    VerbHandlerRegistry registry = new VerbHandlerRegistry();
    assertTrue(registry.hasHandler("ai"));
    assertInstanceOf(AIVerbHandler.class, registry.getHandler("ai"));
  }

  @Test
  void testRegistryGetMissingReturnsNull() {
    VerbHandlerRegistry registry = new VerbHandlerRegistry();
    assertFalse(registry.hasHandler("nonexistent"));
    assertNull(registry.getHandler("nonexistent"));
  }

  @Test
  void testRegistryRegisterRoundtrip() {
    VerbHandlerRegistry registry = new VerbHandlerRegistry();
    SWMLVerbHandler custom =
        new SWMLVerbHandler() {
          @Override
          public String getVerbName() {
            return "custom";
          }

          @Override
          public ValidationResult validateConfig(Map<String, Object> config) {
            return new ValidationResult(true, List.of());
          }

          @Override
          public Map<String, Object> buildConfig(Map<String, Object> kwargs) {
            return Map.of();
          }
        };
    registry.registerHandler(custom);
    assertTrue(registry.hasHandler("custom"));
    assertSame(custom, registry.getHandler("custom"));
  }
}
