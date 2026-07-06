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
import com.signalwire.sdk.logging.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * SwmlDump — the Java port's SWML dump program for the cross-port SWML differ
 * (porting-sdk/scripts/diff_port_swml.py).
 *
 * <p>For each {@code swml_corpus} case it builds an {@link AgentBase}, applies the setter chain,
 * renders the SWML document, and extracts the observed dotted path (e.g. {@code "ai.prompt.pom"}) —
 * emitting ONE JSON object mapping
 *
 * <pre>
 *   case-id -&gt; extracted-fragment
 * </pre>
 *
 * to stdout. The differ canonicalizes both sides and byte-compares against the Python oracle. Only
 * stdout carries JSON. Mirrors Go's {@code cmd/swml-dump/main.go}.
 *
 * <p>Run via the {@code swmlDump} Gradle task:
 *
 * <pre>
 *   ./gradlew --no-daemon -q swmlDump
 * </pre>
 */
final class SwmlDump {

  private SwmlDump() {}

  /** Construct a demo AgentBase (name "demo", route "/demo") — POM is on by default. */
  private static AgentBase newAgent() {
    return AgentBase.builder().name("demo").route("/demo").build();
  }

  private static Map<String, Object> render(AgentBase a) {
    return a.renderSwml(null);
  }

  /**
   * Walk a dotted path into a rendered SWML doc. "ai.prompt" means: find the ai verb in
   * sections.main, then index into it — the Java mirror of diff_port_swml._extract.
   */
  @SuppressWarnings("unchecked")
  private static Object extract(Map<String, Object> doc, String path) {
    Object ai = null;
    Object sectionsObj = doc.get("sections");
    if (sectionsObj instanceof Map) {
      Object mainObj = ((Map<String, Object>) sectionsObj).get("main");
      if (mainObj instanceof List) {
        for (Object sec : (List<Object>) mainObj) {
          if (sec instanceof Map && ((Map<String, Object>) sec).containsKey("ai")) {
            ai = ((Map<String, Object>) sec).get("ai");
            break;
          }
        }
      }
    }
    Object node;
    if (ai != null) {
      Map<String, Object> wrap = new LinkedHashMap<>();
      wrap.put("ai", ai);
      node = wrap;
    } else {
      node = doc;
    }
    for (String part : path.split("\\.", -1)) {
      if (!(node instanceof Map)) {
        return null;
      }
      node = ((Map<String, Object>) node).get(part);
    }
    return node;
  }

  /** Reduce a map fragment to the listed keys (mirrors the oracle's `pick`). */
  @SuppressWarnings("unchecked")
  private static Object pick(Object frag, String... keys) {
    if (!(frag instanceof Map)) {
      return frag;
    }
    Map<String, Object> m = (Map<String, Object>) frag;
    Map<String, Object> out = new LinkedHashMap<>();
    for (String k : keys) {
      out.put(k, m.get(k));
    }
    return out;
  }

  /** Ordered map helper (String key -> Object value). */
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

  /** case pairs a stable id with the fragment it produces. */
  private record Case(String id, Supplier<Object> build) {}

  private static List<Case> cases() {
    List<Case> c = new ArrayList<>();

    // swml_set_prompt_llm_params: two calls MERGE into ai.prompt.
    c.add(
        new Case(
            "swml_set_prompt_llm_params",
            () -> {
              AgentBase a = newAgent();
              a.setPromptLlmParams(map("temperature", 0.5));
              a.setPromptLlmParams(map("top_p", 0.9));
              return pick(extract(render(a), "ai.prompt"), "temperature", "top_p");
            }));

    // swml_set_post_prompt_llm_params: establish a post-prompt, then merge params.
    c.add(
        new Case(
            "swml_set_post_prompt_llm_params",
            () -> {
              AgentBase a = newAgent();
              a.setPostPrompt("Summarize the call.");
              a.setPostPromptLlmParams(map("temperature", 0.3));
              a.setPostPromptLlmParams(map("top_p", 0.8));
              return pick(extract(render(a), "ai.post_prompt"), "temperature", "top_p");
            }));

    // swml_add_language: engine/model/voice carried into ai.languages.
    c.add(
        new Case(
            "swml_add_language",
            () -> {
              AgentBase a = newAgent();
              a.addLanguage("English", "en-US", "rime.spore", null, null, "rime", "mistv2");
              return extract(render(a), "ai.languages");
            }));

    // swml_add_pattern_hint: structured hint into ai.hints.
    c.add(
        new Case(
            "swml_add_pattern_hint",
            () -> {
              AgentBase a = newAgent();
              a.addPatternHint("SignalWire", "signal wire", "SignalWire", true);
              return extract(render(a), "ai.hints");
            }));

    // swml_add_hint: a plain string hint.
    c.add(
        new Case(
            "swml_add_hint",
            () -> {
              AgentBase a = newAgent();
              a.addHint("SignalWire");
              return extract(render(a), "ai.hints");
            }));

    // swml_prompt_add_section: POM sections render into ai.prompt.pom.
    c.add(
        new Case(
            "swml_prompt_add_section",
            () -> {
              AgentBase a = newAgent();
              a.promptAddSection("Role", "You are a helpful assistant.", null);
              a.promptAddSection("Rules", "", list("Be concise", "Be accurate"));
              return extract(render(a), "ai.prompt.pom");
            }));

    // swml_add_pronunciation: renders into ai.pronounce.
    c.add(
        new Case(
            "swml_add_pronunciation",
            () -> {
              AgentBase a = newAgent();
              a.addPronunciation("SW", "SignalWire", true);
              return extract(render(a), "ai.pronounce");
            }));

    return c;
  }

  public static void main(String[] args) {
    // Silence the SDK's INFO logging (which writes to stdout) so ONLY the JSON
    // artifact reaches stdout — the differ parses stdout as one JSON object.
    Logger.setGlobalLevel(Logger.Level.OFF);
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    Map<String, Object> out = new LinkedHashMap<>();
    for (Case cs : cases()) {
      out.put(cs.id(), cs.build().get());
    }
    System.out.println(gson.toJson(out));
  }
}
