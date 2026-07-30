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
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SECURE-DEFAULT dump program for the secure-default differ.
 *
 * <p>Defines a default (no explicit {@code secure=}) tool + an explicit {@code secure=false} tool,
 * renders the agent's SWML with the fixed corpus {@code CALL_ID}, and emits per fixture the
 * RENDERED WIRE PAYLOAD for the differ to classify:
 *
 * <pre>
 *   {"&lt;fixture id&gt;": {"secure_default_true": bool, "rendered": {&lt;functions[] entry&gt;}}}
 * </pre>
 *
 * <ul>
 *   <li>{@code secure_default_true} — the tool built WITHOUT an explicit {@code secure=} is secure
 *       (the SDK-recorded flag); false by construction for the explicit-{@code secure=false} case.
 *   <li>{@code rendered} — that tool's own {@code SWAIG.functions[]} entry, VERBATIM, with every
 *       token VALUE replaced by the corpus placeholder {@code <TOKEN>} (the values are HMACs and
 *       vary per run; the KEY PATH is the whole contract and is preserved exactly).
 * </ul>
 *
 * <p>This program deliberately makes NO judgement about whether the render is correct. An earlier
 * version emitted a self-computed {@code wire_reflects_secure} boolean, which made the gate
 * vacuous: it classified on {@code meta_data_token} (the SWML metadata SCOPING key) and passed
 * green while emitting a tokenless {@code web_hook_url}. The differ now sees the keys and decides.
 *
 * <p>Only stdout carries JSON; the SDK Logger is silenced. Run via the {@code secureDefaultDump}
 * Gradle task.
 */
final class SecureDefaultDump {

  private SecureDefaultDump() {}

  private static final Gson GSON = new GsonBuilder().create();

  // Mirror secure_default_corpus.py EXACTLY.
  private static final String DEFAULT_TOOL = "sd_default_secure";
  private static final String INSECURE_TOOL = "sd_explicit_insecure";
  private static final String TOKEN_PLACEHOLDER = "<TOKEN>";

  /**
   * Entry point: emits the SECURE-DEFAULT dump this gate compares across ports.
   *
   * @param args the command-line arguments.
   */
  @SuppressWarnings("unchecked")
  public static void main(String[] args) {
    Logger.setGlobalLevel(Logger.Level.OFF);

    AgentBase agent =
        AgentBase.builder()
            .name("secure-default-fixture")
            .route("/sd")
            .authUser("u")
            .authPassword("p")
            .build();
    agent.setPromptText("secure default fixture");

    // Default tool: NO explicit secure= → must default secure=true (A1).
    agent.defineTool(
        new ToolDefinition(
            DEFAULT_TOOL,
            "secure-default fixture tool",
            Map.of(),
            (a, r) -> new FunctionResult("ok")));
    // Explicit secure=false tool.
    agent.defineTool(
        new ToolDefinition(
                INSECURE_TOOL,
                "secure-default fixture tool",
                Map.of(),
                (a, r) -> new FunctionResult("ok"))
            .setSecure(false));

    // Render SWML. A secure tool's rendered entry carries its own web_hook_url with a __token
    // query param; an insecure one has no per-tool web_hook_url at all and falls back to
    // SWAIG.defaults. The fixed corpus CALL_ID is referenced for parity with the oracle; java
    // mints the token independent of a call_id (createToken(name, "")), so the single-arg render
    // is sufficient.
    Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
    Map<String, Object> functions = swaigFunctionsByName(swml);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put(
        "define_tool_default_is_secure",
        emit((Map<String, Object>) functions.get(DEFAULT_TOOL), true));
    out.put(
        "define_tool_explicit_insecure",
        emit((Map<String, Object>) functions.get(INSECURE_TOOL), false));
    System.out.println(GSON.toJson(out));
  }

  /**
   * Emit one fixture: the SDK-recorded secure flag plus the rendered entry with token values
   * redacted. NO classification — the differ does that.
   */
  private static Map<String, Object> emit(Map<String, Object> fn, boolean secureDefaultTrue) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("secure_default_true", secureDefaultTrue);
    m.put("rendered", redact(fn == null ? new LinkedHashMap<>() : fn));
    return m;
  }

  /**
   * Replace every nondeterministic token VALUE (an HMAC) with the corpus placeholder while
   * preserving every KEY and key path exactly — both a token-suffixed field and a token-suffixed
   * query parameter on a URL value, so re-applying the same redaction is a no-op.
   */
  private static Map<String, Object> redact(Map<String, Object> fn) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : fn.entrySet()) {
      String k = e.getKey();
      Object v = e.getValue();
      if (v instanceof String s) {
        if (k.toLowerCase(java.util.Locale.ROOT).endsWith("token")) {
          out.put(k, TOKEN_PLACEHOLDER);
          continue;
        }
        if (s.contains("://") || s.startsWith("/")) {
          out.put(k, redactUrlTokens(s));
          continue;
        }
      }
      out.put(k, v);
    }
    return out;
  }

  /** Replace the VALUE of every token-suffixed query parameter in a URL with the placeholder. */
  private static String redactUrlTokens(String url) {
    int q = url.indexOf('?');
    if (q < 0) {
      return url;
    }
    StringBuilder sb = new StringBuilder(url.substring(0, q + 1));
    String[] pairs = url.substring(q + 1).split("&", -1);
    for (int i = 0; i < pairs.length; i++) {
      if (i > 0) {
        sb.append('&');
      }
      String pair = pairs[i];
      int eq = pair.indexOf('=');
      String key = eq < 0 ? pair : pair.substring(0, eq);
      if (eq >= 0 && key.toLowerCase(java.util.Locale.ROOT).endsWith("token")) {
        sb.append(key).append('=').append(TOKEN_PLACEHOLDER);
      } else {
        sb.append(pair);
      }
    }
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> swaigFunctionsByName(Map<String, Object> swml) {
    Map<String, Object> byName = new LinkedHashMap<>();
    Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
    List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
    Map<String, Object> ai =
        main.stream()
            .filter(v -> v.containsKey("ai"))
            .findFirst()
            .map(v -> (Map<String, Object>) v.get("ai"))
            .orElseThrow(() -> new IllegalStateException("no ai verb in rendered SWML"));
    Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
    List<Map<String, Object>> functions = (List<Map<String, Object>>) swaig.get("functions");
    for (Map<String, Object> fn : functions) {
      byName.put(String.valueOf(fn.get("function")), fn);
    }
    return byName;
  }
}
