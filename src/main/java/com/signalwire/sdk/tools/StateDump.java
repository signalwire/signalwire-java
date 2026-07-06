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
import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.prefabs.InfoGathererAgent;
import com.signalwire.sdk.server.AgentServer;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.skills.SkillRegistry;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swml.SWMLVerbHandler;
import com.signalwire.sdk.swml.Service;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * StateDump — the Java port's STATE dump program for the cross-port state differ
 * (porting-sdk/scripts/diff_port_state.py).
 *
 * <p>For each {@code state_corpus} case it builds the target object, applies the mutation chain via
 * the Java SDK's native API, reads the observable state through the public accessor / rendered
 * representation, and prints ONE JSON object mapping
 *
 * <pre>
 *   case-id -&gt; observed-state
 * </pre>
 *
 * to stdout. The differ canonicalizes both sides and byte-compares against the Python oracle. Only
 * stdout carries JSON. Mirrors Go's {@code cmd/state-dump/main.go}.
 *
 * <p>Run via the {@code stateDump} Gradle task:
 *
 * <pre>
 *   ./gradlew --no-daemon -q stateDump
 * </pre>
 */
final class StateDump {

  private StateDump() {}

  /** A minimal custom verb handler — the Java analog of the corpus's throwaway "greet" handler. */
  private static final class GreetVerbHandler extends SWMLVerbHandler {
    @Override
    public String getVerbName() {
      return "greet";
    }

    @Override
    public SWMLVerbHandler.ValidationResult validateConfig(Map<String, Object> cfg) {
      return new SWMLVerbHandler.ValidationResult(true, new ArrayList<>());
    }

    @Override
    public Map<String, Object> buildConfig(Map<String, Object> params) {
      return params;
    }
  }

  private static AgentBase demoAgent() {
    return AgentBase.builder().name("demo").route("/demo").build();
  }

  /** Ordered map helper. */
  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  @SafeVarargs
  private static <T> List<T> list(T... items) {
    return new ArrayList<>(Arrays.asList(items));
  }

  public static void main(String[] args) {
    Logger.setGlobalLevel(Logger.Level.OFF);
    // serializeNulls: keep explicit null values (e.g. lookup_missing) in the
    // JSON so the oracle's {"lookup_missing": null} byte-matches — Gson drops
    // nulls by default.
    Gson gson = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    Map<String, Object> out = new LinkedHashMap<>();

    // ---- global_data: set MERGES into the accumulated global data ----
    {
      AgentBase a = demoAgent();
      a.setGlobalData(map("company", "SignalWire", "tier", "gold"));
      out.put("state_set_global_data", a.getGlobalData());
    }
    {
      AgentBase a = demoAgent();
      a.updateGlobalData(map("k1", "v1"));
      a.updateGlobalData(map("k2", "v2"));
      out.put("state_update_global_data", a.getGlobalData());
    }
    {
      // MERGE semantics: overlapping key wins, sibling survives.
      AgentBase a = demoAgent();
      a.setGlobalData(map("a", 1, "b", 2));
      a.setGlobalData(map("b", 99, "c", 3));
      out.put("state_global_data_merge", a.getGlobalData());
    }

    // ---- sip-username registration on AgentBase (lowercased set) ----
    {
      AgentBase a = demoAgent();
      a.registerSipUsername("Bob");
      a.registerSipUsername("alice");
      out.put("state_register_sip_username", sorted(a.getSipUsernames()));
    }
    {
      // dedup + case-fold: "Bob","BOB","bob" collapse to one.
      AgentBase a = demoAgent();
      a.registerSipUsername("Bob");
      a.registerSipUsername("BOB");
      a.registerSipUsername("bob");
      out.put("state_register_sip_username_dedup", sorted(a.getSipUsernames()));
    }

    // ---- AgentServer sip-username mapping (username -> route) + lookup ----
    {
      AgentServer s = new AgentServer();
      s.setupSipRouting("/sip", false);
      s.registerSipUsername("Bob", "/agent");
      s.registerSipUsername("sales", "/sales");
      out.put(
          "server_sip_username_mapping",
          map(
              "mapping", s.getSipUsernameMapping(),
              "lookup_bob", s.getSipRoute("bob"),
              "lookup_BOB", s.getSipRoute("BOB"),
              "lookup_missing", s.getSipRoute("nope")));
    }
    {
      // unregister removes the agent route from the registry.
      AgentServer s = new AgentServer();
      s.register(AgentBase.builder().name("agent").route("/agent").build(), "/agent");
      s.register(AgentBase.builder().name("other").route("/other").build(), "/other");
      s.unregister("/agent");
      List<String> routes = new ArrayList<>();
      for (Map.Entry<String, AgentBase> e : s.getAgents()) {
        routes.add(e.getKey());
      }
      out.put("server_unregister", sorted(routes));
    }

    // ---- routing-callback registration on Service (path-normalized) ----
    {
      Service svc = new Service("svc", "/svc");
      java.util.function.BiFunction<Map<String, Object>, Map<String, String>, String> noop =
          (b, h) -> null;
      svc.registerRoutingCallback(noop, "/sip/");
      svc.registerRoutingCallback(noop, "voice");
      out.put("state_register_routing_callback", sorted(svc.routingCallbackPaths()));
    }

    // ---- verb-handler registration (VerbHandlerRegistry: ai preloaded) ----
    {
      Service svc = new Service("svc", "/svc");
      svc.registerVerbHandler(new GreetVerbHandler());
      out.put(
          "state_register_verb_handler",
          map(
              "verbs", svc.verbRegistry().handlerNames(),
              "has_greet", svc.verbRegistry().hasHandler("greet"),
              "has_ai", svc.verbRegistry().hasHandler("ai"),
              "has_missing", svc.verbRegistry().hasHandler("nope")));
    }

    // ---- skill registration (SkillRegistry: name -> factory, idempotent) ----
    {
      SkillRegistry reg = new SkillRegistry();
      Supplier<SkillBase> noopFactory = () -> null;
      reg.registerSkill("custom_alpha", noopFactory);
      reg.registerSkill("custom_beta", noopFactory);
      reg.registerSkill("custom_alpha", noopFactory); // idempotent
      out.put("state_register_skill", reg.registeredNames());
    }

    // ---- InfoGatherer.submit_answer: records answer + advances index ----
    {
      InfoGathererAgent ig = new InfoGathererAgent("demo", new ArrayList<>());
      out.put(
          "infogatherer_submit_answer_first",
          submitAnswerDelta(
              ig,
              map("answer", "Alice"),
              map(
                  "global_data",
                  map(
                      "questions",
                      list(
                          map("key_name", "name", "question_text", "What is your name?"),
                          map("key_name", "email", "question_text", "What is your email?")),
                      "question_index",
                      0,
                      "answers",
                      new ArrayList<>()))));
    }
    {
      InfoGathererAgent ig = new InfoGathererAgent("demo", new ArrayList<>());
      out.put(
          "infogatherer_submit_answer_last",
          submitAnswerDelta(
              ig,
              map("answer", "a@b.com"),
              map(
                  "global_data",
                  map(
                      "questions",
                      list(
                          map("key_name", "name", "question_text", "What is your name?"),
                          map("key_name", "email", "question_text", "What is your email?")),
                      "question_index",
                      1,
                      "answers",
                      list(map("key_name", "name", "answer", "Alice"))))));
    }

    // ---- contexts/steps navigation (valid_steps rendered per step) ----
    {
      AgentBase a = demoAgent();
      ContextBuilder cb = a.defineContexts();
      Context ctx = cb.addContext("default");
      ctx.addStep("greet").setText("Greet the caller.").setValidSteps(list("collect"));
      ctx.addStep("collect").setText("Collect their info.").setValidSteps(list("greet"));
      out.put("state_contexts_navigation", contextsNav(cb));
    }

    System.out.println(gson.toJson(out));
  }

  /** Sorted copy of a string collection (differ canonicalizes; sorting keeps output stable). */
  private static List<String> sorted(java.util.Collection<String> c) {
    List<String> l = new ArrayList<>(c);
    java.util.Collections.sort(l);
    return l;
  }

  /**
   * Drive InfoGatherer.submitAnswer and reduce the result to the observable delta (mirrors
   * diff_port_state._observe "submit_answer_delta"): the set_global_data action's question_index +
   * answers, plus a {@code done} flag derived from the completion message.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> submitAnswerDelta(
      InfoGathererAgent ig, Map<String, Object> args, Map<String, Object> rawData) {
    FunctionResult res = ig.submitAnswer(args, rawData);
    Map<String, Object> m = res.toMap();
    Map<String, Object> gd = new LinkedHashMap<>();
    Object actionObj = m.get("action");
    if (actionObj instanceof List) {
      for (Object actObj : (List<Object>) actionObj) {
        if (actObj instanceof Map) {
          Object sgd = ((Map<String, Object>) actObj).get("set_global_data");
          if (sgd instanceof Map) {
            gd = (Map<String, Object>) sgd;
            break;
          }
        }
      }
    }
    String resp = m.get("response") instanceof String ? (String) m.get("response") : "";
    Map<String, Object> delta = new LinkedHashMap<>();
    delta.put("question_index", gd.get("question_index"));
    delta.put("answers", gd.get("answers"));
    // `done` mirrors the oracle's _is_complete: the completion message contains
    // "All questions have been answered" (vs a next-question instruction).
    delta.put("done", resp.contains("All questions have been answered"));
    return delta;
  }

  /** Render the builder and reduce to per-context {name, valid_steps}. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> contextsNav(ContextBuilder cb) {
    Map<String, Object> m = cb.toMap();
    Map<String, Object> nav = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : m.entrySet()) {
      Map<String, Object> cdoc = (Map<String, Object>) e.getValue();
      List<Map<String, Object>> steps = (List<Map<String, Object>>) cdoc.get("steps");
      List<Map<String, Object>> reduced = new ArrayList<>();
      if (steps != null) {
        for (Map<String, Object> s : steps) {
          Map<String, Object> r = new LinkedHashMap<>();
          r.put("name", s.get("name"));
          r.put("valid_steps", s.get("valid_steps"));
          reduced.add(r);
        }
      }
      nav.put(e.getKey(), reduced);
    }
    return nav;
  }
}
