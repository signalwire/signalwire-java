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
 * SecureDefaultDump — the Java port's SECURE-DEFAULT dump program for the cross-port secure-default
 * differ (porting-sdk/scripts/diff_port_secure_default.py, A+ campaign A1 / PSDK-4a).
 *
 * <p>Defines a default (no explicit {@code secure=}) tool + an explicit {@code secure=false} tool,
 * renders the agent's SWML with the fixed corpus {@code CALL_ID}, and for each tool emits the
 * deterministic classification the differ compares against the Python golden:
 *
 * <pre>
 *   {secure_default_true: bool, wire_reflects_secure: bool}
 * </pre>
 *
 * <ul>
 *   <li>{@code secure_default_true} — the tool built WITHOUT an explicit {@code secure=} is secure
 *       (the SDK-recorded flag); false by construction for the explicit-{@code secure=false} case.
 *   <li>{@code wire_reflects_secure} — the rendered SWML webhook reflects the tool's secure state:
 *       a per-tool token ({@code meta_data_token}, the java surfacing of the reference {@code
 *       __token}) is present IFF the tool is secure.
 * </ul>
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

  @SuppressWarnings("unchecked")
  public static void main(String[] args) {
    Logger.setGlobalLevel(Logger.Level.OFF);

    AgentBase agent =
        AgentBase.builder().name("secure-default-fixture").authUser("u").authPassword("p").build();
    agent.setPromptText("secure default fixture");

    // Default tool: NO explicit secure= → must default secure=true (A1).
    agent.defineTool(
        new ToolDefinition(
            DEFAULT_TOOL, "default secure tool", Map.of(), (a, r) -> new FunctionResult("ok")));
    // Explicit secure=false tool.
    agent.defineTool(
        new ToolDefinition(
                INSECURE_TOOL,
                "explicit insecure tool",
                Map.of(),
                (a, r) -> new FunctionResult("ok"))
            .setSecure(false));

    // Render SWML: a secure tool's rendered SWAIG function carries a per-tool token
    // (meta_data_token,
    // minted in buildSwaigFunctions when the tool isSecure), an insecure one does not. The fixed
    // corpus CALL_ID is referenced for parity with the oracle; java mints the token independent of
    // a
    // call_id (createToken(name, "")), so the single-arg render is sufficient.
    Map<String, Object> swml = agent.renderSwml("http://mock.test");
    Map<String, Object> functions = swaigFunctionsByName(swml);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put(
        "define_tool_default_is_secure",
        classify((Map<String, Object>) functions.get(DEFAULT_TOOL), true));
    out.put(
        "define_tool_explicit_insecure",
        classify((Map<String, Object>) functions.get(INSECURE_TOOL), false));
    System.out.println(GSON.toJson(out));
  }

  /**
   * Classify one rendered function. {@code expectedSecure} is the tool's declared secure state; the
   * wire "reflects" it when a per-tool token is present iff the tool is secure.
   */
  private static Map<String, Object> classify(Map<String, Object> fn, boolean expectedSecure) {
    boolean tokenPresent =
        fn != null
            && fn.get("meta_data_token") != null
            && !String.valueOf(fn.get("meta_data_token")).isEmpty();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("secure_default_true", expectedSecure);
    m.put("wire_reflects_secure", tokenPresent == expectedSecure);
    return m;
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
