/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.contexts.Context;
import com.signalwire.sdk.contexts.ContextBuilder;
import com.signalwire.sdk.contexts.Step;
import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swml.Service;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * StrictRenderDump — the Java port's SWML STRICT-RENDER dump program for the cross-port
 * strict-render differ (porting-sdk/scripts/diff_port_strict_render.py).
 *
 * <p>For each case in the shared {@code strict_render_corpus} it builds the target in the Java
 * idiom (a {@link Service} for verb-level cases, an {@link AgentBase} + contexts builder for
 * contexts-level cases), catches any build/validation exception as {@code "raised"}, and reports a
 * clean build as {@code "ok"}. It emits ONE JSON object mapping
 *
 * <pre>
 *   case-id -&gt; "raised" | "ok"
 * </pre>
 *
 * to stdout (logs to stderr). The differ compares each outcome against the Python oracle. Only
 * stdout carries JSON. Mirrors Go's {@code cmd/strict-render-dump}.
 *
 * <p>Run via the {@code strictRenderDump} Gradle task:
 *
 * <pre>
 *   ./gradlew --quiet --console=plain strictRenderDump
 * </pre>
 *
 * <p>The corpus itself is the source of truth (porting-sdk/scripts/strict_render_corpus.py); the
 * case builders below reproduce each case's chain in Java. Because a case absent from a port's dump
 * is PENDING (not a failure) the set here must stay in lockstep with that corpus.
 */
final class StrictRenderDump {

  private StrictRenderDump() {}

  private record Case(String id, Runnable build) {}

  /** A strict SWMLService with schema validation ON (the production default). */
  private static Service strictService() {
    return new Service("s", "/s");
  }

  private static AgentBase agent() {
    return AgentBase.builder().name("a").route("/a").build();
  }

  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  /**
   * Interpret the corpus's {@code _ctx_add_step} verb: reuse the named context if present, else add
   * it, then add the step and apply text / functions / valid_contexts.
   */
  private static void ctxAddStep(
      ContextBuilder contexts,
      String contextName,
      String stepName,
      String text,
      List<String> functions,
      List<String> validContexts) {
    Context ctx = contexts.getContext(contextName);
    if (ctx == null) {
      ctx = contexts.addContext(contextName);
    }
    Step step = ctx.addStep(stepName);
    if (text != null) {
      step.setText(text);
    }
    if (functions != null) {
      step.setFunctions(new ArrayList<>(functions));
    }
    if (validContexts != null) {
      step.setValidContexts(new ArrayList<>(validContexts));
    }
  }

  private static List<Case> cases() {
    List<Case> c = new ArrayList<>();

    // ---- verb-level (SWMLService, validation ON) ----
    c.add(new Case("strict_unknown_verb", () -> strictService().addVerb("foobar", map())));
    c.add(
        new Case(
            "strict_answer_misspelled_key",
            () -> strictService().addVerb("answer", map("maxduration", 5))));
    c.add(
        new Case(
            "strict_answer_unknown_key",
            () -> strictService().addVerb("answer", map("wibble", 1))));
    c.add(
        new Case(
            "strict_play_misspelled_key",
            () -> strictService().addVerb("play", map("urlz", List.of("say:hi")))));
    c.add(
        new Case(
            "strict_play_valid_plus_unknown_key",
            () -> strictService().addVerb("play", map("url", "say:hi", "foo", 1))));
    c.add(
        new Case(
            "strict_record_misspelled_key",
            () -> strictService().addVerb("record", map("formatt", "wav"))));
    c.add(
        new Case(
            "strict_answer_wrong_type",
            () -> strictService().addVerb("answer", map("max_duration", "notanumber"))));
    c.add(
        new Case(
            "strict_ai_misspelled_top_key",
            () ->
                strictService()
                    .addVerb("ai", map("prompt", map("text", "hi"), "temperatur", 0.5))));
    c.add(
        new Case(
            "strict_ai_unknown_top_key",
            () -> strictService().addVerb("ai", map("prompt", map("text", "hi"), "zzz", 1))));
    c.add(
        new Case(
            "strict_ai_missing_prompt",
            () -> strictService().addVerb("ai", map("post_prompt", map("text", "bye")))));

    // ---- valid documents must still render ----
    c.add(
        new Case(
            "strict_answer_ok", () -> strictService().addVerb("answer", map("max_duration", 5))));
    c.add(new Case("strict_play_ok", () -> strictService().addVerb("play", map("url", "say:hi"))));
    c.add(
        new Case(
            "strict_ai_ok", () -> strictService().addVerb("ai", map("prompt", map("text", "hi")))));
    c.add(
        new Case(
            "strict_ai_params_open_ok",
            () ->
                strictService()
                    .addVerb(
                        "ai",
                        map("prompt", map("text", "hi"), "params", map("some_future_param", 1)))));

    // ---- contexts-level (AgentBase; dangling refs) ----
    c.add(
        new Case(
            "strict_dangling_step_function",
            () -> {
              AgentBase a = agent();
              a.defineTool(
                  "order_status",
                  "look up an order",
                  map(),
                  (args, raw) -> new FunctionResult("ok"));
              ContextBuilder contexts = a.defineContexts();
              ctxAddStep(
                  contexts,
                  "default",
                  "help",
                  "help",
                  List.of("order_status", "get_datetime"),
                  null);
              contexts.toMap();
            }));
    c.add(
        new Case(
            "strict_registered_step_function_ok",
            () -> {
              AgentBase a = agent();
              a.defineTool(
                  "order_status",
                  "look up an order",
                  map(),
                  (args, raw) -> new FunctionResult("ok"));
              ContextBuilder contexts = a.defineContexts();
              ctxAddStep(contexts, "default", "help", "help", List.of("order_status"), null);
              contexts.toMap();
            }));
    c.add(
        new Case(
            "strict_reserved_native_function_ok",
            () -> {
              AgentBase a = agent();
              ContextBuilder contexts = a.defineContexts();
              ctxAddStep(
                  contexts,
                  "default",
                  "help",
                  "help",
                  List.of("next_step", "change_context"),
                  null);
              contexts.toMap();
            }));
    c.add(
        new Case(
            "strict_dangling_valid_context",
            () -> {
              AgentBase a = agent();
              ContextBuilder contexts = a.defineContexts();
              ctxAddStep(contexts, "default", "help", "help", null, List.of("nowhere"));
              contexts.toMap();
            }));

    return c;
  }

  public static void main(String[] args) {
    Logger.setGlobalLevel(Logger.Level.OFF);
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    Map<String, Object> out = new LinkedHashMap<>();
    for (Case cs : cases()) {
      String outcome;
      try {
        cs.build().run();
        outcome = "ok";
      } catch (RuntimeException e) {
        outcome = "raised";
      }
      out.put(cs.id(), outcome);
    }
    System.out.println(gson.toJson(out));
  }
}
