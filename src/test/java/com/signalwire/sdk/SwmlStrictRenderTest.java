/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.contexts.Context;
import com.signalwire.sdk.contexts.ContextBuilder;
import com.signalwire.sdk.contexts.Step;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swml.SchemaValidationError;
import com.signalwire.sdk.swml.Service;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SWML strict-render contract (Wave-2 P#5) — Java port of Python's {@code
 * tests/unit/core/test_swml_strict_render.py}.
 *
 * <p>Building / rendering an SWML document with a MISSHAPEN config, an UNKNOWN verb, or a
 * MISSPELLED key must RAISE a clear error — not silently drop or accept it. Two gaps this suite
 * pins:
 *
 * <ul>
 *   <li>GAP 1 — the {@code ai} verb must reject unknown/misspelled top-level keys like every other
 *       verb; {@code ai.params} stays the deliberate open door for LLM tuning.
 *   <li>GAP 2 — a step's {@code setFunctions([...])} referencing a function that is neither a
 *       registered SWAIG tool nor a reserved native tool must raise (dangling reference).
 * </ul>
 */
class SwmlStrictRenderTest {

  /** A Service with schema validation ON — the production default. */
  private static Service strictService() {
    return new Service("strict", "/strict");
  }

  private static AgentBase strictAgent() {
    return AgentBase.builder().name("ctxagent").route("/ctx").build();
  }

  // ---------------------------------------------------------------------------
  // Baseline: unknown verb + good-verb regression guards.
  // ---------------------------------------------------------------------------

  @Test
  void unknownVerbRaises() {
    Service svc = strictService();
    SchemaValidationError ex =
        assertThrows(SchemaValidationError.class, () -> svc.addVerb("foobar", Map.of()));
    assertTrue(ex.getMessage().contains("foobar"), "message should name the bad verb: " + ex);
  }

  @Test
  void goodVerbRenders() {
    Service svc = strictService();
    assertDoesNotThrow(() -> svc.addVerb("answer", Map.of("max_duration", 5)));
  }

  // ---------------------------------------------------------------------------
  // Misspelled / unknown / wrong-typed keys on closed verbs must raise.
  // ---------------------------------------------------------------------------

  @Test
  void answerMisspelledKeyRaises() {
    Service svc = strictService();
    assertThrows(
        SchemaValidationError.class, () -> svc.addVerb("answer", Map.of("maxduration", 5)));
  }

  @Test
  void answerUnknownKeyRaises() {
    Service svc = strictService();
    assertThrows(SchemaValidationError.class, () -> svc.addVerb("answer", Map.of("wibble", 1)));
  }

  @Test
  void playMisspelledKeyRaises() {
    Service svc = strictService();
    assertThrows(
        SchemaValidationError.class, () -> svc.addVerb("play", Map.of("urlz", List.of("say:hi"))));
  }

  @Test
  void playValidPlusUnknownKeyRaises() {
    Service svc = strictService();
    assertThrows(
        SchemaValidationError.class, () -> svc.addVerb("play", Map.of("url", "say:hi", "foo", 1)));
  }

  @Test
  void recordMisspelledKeyRaises() {
    Service svc = strictService();
    assertThrows(
        SchemaValidationError.class, () -> svc.addVerb("record", Map.of("formatt", "wav")));
  }

  @Test
  void wrongTypedConfigRaises() {
    Service svc = strictService();
    assertThrows(
        SchemaValidationError.class,
        () -> svc.addVerb("answer", Map.of("max_duration", "notanumber")));
  }

  @Test
  void answerVerbRenders() {
    Service svc = strictService();
    assertDoesNotThrow(() -> svc.addVerb("answer", Map.of("max_duration", 5)));
  }

  @Test
  void playVerbRenders() {
    Service svc = strictService();
    assertDoesNotThrow(() -> svc.addVerb("play", Map.of("url", "say:hi")));
  }

  // ---------------------------------------------------------------------------
  // GAP 1 — the ai verb rejects unknown/misspelled top-level keys; params open.
  // ---------------------------------------------------------------------------

  @Test
  void aiGoodConfigRenders() {
    Service svc = strictService();
    assertDoesNotThrow(() -> svc.addVerb("ai", Map.of("prompt", Map.of("text", "hi"))));
  }

  @Test
  void aiGoodConfigWithSwaigRenders() {
    Service svc = strictService();
    assertDoesNotThrow(
        () ->
            svc.addVerb(
                "ai",
                Map.of("prompt", Map.of("text", "hi"), "SWAIG", Map.of("functions", List.of()))));
  }

  @Test
  void aiMisspelledTopLevelKeyRaises() {
    Service svc = strictService();
    assertThrows(
        SchemaValidationError.class,
        () -> svc.addVerb("ai", Map.of("prompt", Map.of("text", "hi"), "temperatur", 0.5)));
  }

  @Test
  void aiUnknownTopLevelKeyRaises() {
    Service svc = strictService();
    assertThrows(
        SchemaValidationError.class,
        () -> svc.addVerb("ai", Map.of("prompt", Map.of("text", "hi"), "zzz", 1)));
  }

  @Test
  void aiMissingPromptRaises() {
    Service svc = strictService();
    assertThrows(
        SchemaValidationError.class,
        () -> svc.addVerb("ai", Map.of("post_prompt", Map.of("text", "bye"))));
  }

  @Test
  void aiParamsSubObjectStaysOpen() {
    // params is the deliberate open door for LLM tuning; a key inside it is NOT
    // a misspelling and must render.
    Service svc = strictService();
    assertDoesNotThrow(
        () ->
            svc.addVerb(
                "ai",
                Map.of("prompt", Map.of("text", "hi"), "params", Map.of("some_future_param", 1))));
  }

  @Test
  void aiEmptyPromptPomRenders() {
    // The ai verb is validated TOP-LEVEL-KEYS ONLY: its deep sub-schema is not
    // enforced, so a promptless agent's empty prompt.pom [] (which the bundled
    // JSON-schema's AIPromptPom would reject via minItems) must still render.
    // Guards against re-tightening the ai verb into a false-rejecting deep pass.
    Service svc = strictService();
    assertDoesNotThrow(() -> svc.addVerb("ai", Map.of("prompt", Map.of("pom", List.of()))));
  }

  @Test
  void aiFullSwaigShapeRenders() {
    // A realistic rendered ai verb — SWAIG.defaults + a function carrying a
    // secure web_hook_url and meta_data_token — must render (top-level-only ai
    // validation does not descend into the SWAIG function shapes).
    Service svc = strictService();
    Map<String, Object> fn =
        Map.of(
            "function", "lookup",
            "description", "look up",
            "parameters", Map.of("type", "object", "properties", Map.of()),
            "web_hook_url", "https://example/swaig?__token=abc",
            "meta_data_token", "tok");
    Map<String, Object> swaig =
        Map.of(
            "defaults", Map.of("web_hook_url", "https://example/swaig"),
            "functions", List.of(fn));
    assertDoesNotThrow(
        () -> svc.addVerb("ai", Map.of("prompt", Map.of("text", "hi"), "SWAIG", swaig)));
  }

  // ---------------------------------------------------------------------------
  // GAP 2 — dangling set_functions reference must raise.
  // ---------------------------------------------------------------------------

  private static void addStep(
      ContextBuilder contexts, String stepName, String text, List<String> functions) {
    Context ctx = contexts.getContext("default");
    if (ctx == null) {
      ctx = contexts.addContext("default");
    }
    Step step = ctx.addStep(stepName);
    step.setText(text);
    if (functions != null) {
      step.setFunctions(functions);
    }
  }

  @Test
  void danglingFunctionRefRaises() {
    AgentBase agent = strictAgent();
    agent.defineTool(
        "order_status", "look up an order", Map.of(), (args, raw) -> new FunctionResult("ok"));
    ContextBuilder contexts = agent.defineContexts();
    addStep(contexts, "help", "help the caller", List.of("order_status", "get_datetime"));

    IllegalStateException ex = assertThrows(IllegalStateException.class, contexts::toMap);
    assertTrue(
        ex.getMessage().contains("get_datetime"),
        "message should name the dangling function: " + ex.getMessage());
  }

  @Test
  void registeredFunctionRefRenders() {
    AgentBase agent = strictAgent();
    agent.defineTool(
        "order_status", "look up an order", Map.of(), (args, raw) -> new FunctionResult("ok"));
    ContextBuilder contexts = agent.defineContexts();
    addStep(contexts, "help", "help the caller", List.of("order_status"));

    Map<String, Object> doc = assertDoesNotThrow(contexts::toMap);
    assertTrue(doc.containsKey("default"));
  }

  @Test
  void reservedNativeToolRefAllowed() {
    // next_step / change_context are auto-injected natives; referencing them
    // explicitly must not be treated as dangling.
    AgentBase agent = strictAgent();
    ContextBuilder contexts = agent.defineContexts();
    addStep(contexts, "help", "help the caller", List.of("next_step", "change_context"));

    Map<String, Object> doc = assertDoesNotThrow(contexts::toMap);
    assertTrue(doc.containsKey("default"));
  }

  @Test
  void functionsNoneAndEmptyRender() {
    // "none" and [] are explicit disable-all — never dangling.
    for (Object value : new Object[] {"none", List.of()}) {
      AgentBase agent = strictAgent();
      ContextBuilder contexts = agent.defineContexts();
      Context ctx = contexts.addContext("default");
      Step step = ctx.addStep("help");
      step.setText("help the caller");
      step.setFunctions(value);
      Map<String, Object> doc = assertDoesNotThrow(contexts::toMap);
      assertTrue(doc.containsKey("default"));
    }
  }

  @Test
  void danglingValidContextRaises() {
    AgentBase agent = strictAgent();
    ContextBuilder contexts = agent.defineContexts();
    Context ctx = contexts.addContext("default");
    Step step = ctx.addStep("help");
    step.setText("help the caller");
    step.setValidContexts(List.of("nowhere"));
    assertThrows(IllegalStateException.class, contexts::toMap);
  }
}
