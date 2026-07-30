/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.skills.SkillRegistry;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EmitSkills — this SDK's dump program for the SKILL-CONTRACT gate's differ.
 *
 * <p>The sibling of {@link EmitCorpus}, for built-in SKILLS rather than {@code FunctionResult}. For
 * each covered skill it looks up the skill's factory in the {@link SkillRegistry}, instantiates it,
 * runs {@code setup(config)} with the canonical config from the shared skill-contract corpus (the
 * single source of truth for both the config and the covered id set), collects the tool contracts
 * the skill registers, and prints ONE JSON object mapping
 *
 * <pre>
 *   skill-id -&gt; [ { "name": ..., "parameters": {...}, "required"?: [...] }, ... ]
 * </pre>
 *
 * to stdout. The differ runs this program, parses that object, and structurally compares each
 * skill's tool contract against the shared golden. The differ normalises both sides (flat vs
 * wrapped params, required list, enum order); this program emits each tool's name + parameters
 * verbatim. DESCRIPTIONS are not part of the compared contract.
 *
 * <p>A skill registers tools in one of two shapes, and a skill may use either or both:
 *
 * <ul>
 *   <li><b>handler tools</b> — {@link SkillBase#registerTools()} returns {@link ToolDefinition}s,
 *       each carrying a {@code name} and a {@code parameters} JSON-Schema map. When that map
 *       carries a top-level {@code required} array it is forwarded so the differ sees it.
 *   <li><b>DataMap tools</b> — {@link SkillBase#getSwaigFunctions()} returns the wrapped SWAIG
 *       function maps (name under {@code "function"}, params under {@code "parameters"} with their
 *       own {@code required} array). 6 covered skills are DataMap-based (weather_api,
 *       datasphere_serverless, swml_transfer, joke, api_ninjas_trivia, play_background_file).
 * </ul>
 *
 * <p>CONTRACT (the differ's {@code --help} states the same requirements):
 *
 * <ul>
 *   <li>The id set MUST equal {@code corpus_ids()} (the differ rejects a mismatch).
 *   <li>Only stdout carries the JSON object; all logs/errors go to stderr.
 * </ul>
 *
 * <p>Run from the built JAR (the {@code emitSkills} Gradle task wires the classpath):
 *
 * <pre>
 *   ./gradlew --no-daemon -q emitSkills
 * </pre>
 */
// Package-private: this is the skill-dump tool the SKILL-CONTRACT gate runs via
// the `emitSkills` Gradle task (a main() entry point — no `public` needed to
// launch it), NOT a public SDK API. Keeping it non-public excludes it from the
// enumerated public surface, matching EmitCorpus and every other port's emitter.
final class EmitSkills {

  private EmitSkills() {}

  /** One entry of the shared skill-contract CORPUS: {id, skill, config}. */
  private record CorpusEntry(String id, String skill, Map<String, Object> config) {}

  /**
   * Locate the shared skill-contract corpus script under {@code $PORTING_SDK} / {@code
   * $PORTING_SDK_PATH}, else the sibling {@code ../porting-sdk} checkout (the adjacency
   * convention), run it, and return its CORPUS entries. Fails loudly if no candidate base contains
   * the script.
   */
  private static List<CorpusEntry> loadCorpus() throws Exception {
    List<String> bases = new ArrayList<>();
    for (String env :
        new String[] {System.getenv("PORTING_SDK"), System.getenv("PORTING_SDK_PATH")}) {
      if (env != null && !env.isEmpty()) {
        bases.add(env);
      }
    }
    bases.add(new File(System.getProperty("user.dir"), "../porting-sdk").getPath());

    for (String base : bases) {
      File script = new File(base, "scripts/skill_contract_corpus.py");
      if (!script.exists()) {
        continue;
      }
      Process p =
          new ProcessBuilder("python3", script.getAbsolutePath())
              .redirectError(ProcessBuilder.Redirect.INHERIT)
              .start();
      String out;
      try (InputStream in = p.getInputStream()) {
        out = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      }
      int rc = p.waitFor();
      if (rc != 0) {
        throw new IllegalStateException("running " + script + " failed (exit " + rc + ")");
      }
      Gson gson = new Gson();
      Type t = new TypeToken<Map<String, Object>>() {}.getType();
      Map<String, Object> parsed = gson.fromJson(out, t);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> corpus = (List<Map<String, Object>>) parsed.get("corpus");
      List<CorpusEntry> entries = new ArrayList<>();
      for (Map<String, Object> e : corpus) {
        @SuppressWarnings("unchecked")
        Map<String, Object> cfg =
            e.get("config") != null ? (Map<String, Object>) e.get("config") : Map.of();
        entries.add(new CorpusEntry((String) e.get("id"), (String) e.get("skill"), cfg));
      }
      return entries;
    }
    throw new IllegalStateException(
        "cannot locate porting-sdk/scripts/skill_contract_corpus.py "
            + "(set PORTING_SDK / PORTING_SDK_PATH or clone porting-sdk adjacent)");
  }

  /**
   * Instantiate one covered skill with the corpus config, run setup, and collect the {name,
   * parameters, required?} contracts it registers from BOTH registerTools() (handler tools) and
   * getSwaigFunctions() (DataMap tools).
   */
  private static List<Map<String, Object>> contractsFor(CorpusEntry entry) {
    SkillBase skill = SkillRegistry.get(entry.skill());
    if (skill == null) {
      throw new IllegalStateException(
          "no registered factory for covered skill \"" + entry.skill() + "\"");
    }
    // NOTE: in the Java port, config is passed to setup(), not the constructor.
    if (!skill.setup(entry.config())) {
      throw new IllegalStateException(
          "skill \""
              + entry.skill()
              + "\" setup returned false with the corpus config — "
              + "config drift between the corpus and the port.");
    }

    List<Map<String, Object>> contracts = new ArrayList<>();

    // Handler tools: name + parameters (JSON-Schema map). Forward a top-level
    // `required` array if the parameters map carries one (the differ reads it
    // either from the wrapped schema or from this top-level key).
    for (ToolDefinition tool : skill.registerTools()) {
      Map<String, Object> c = new LinkedHashMap<>();
      c.put("name", tool.getName());
      Map<String, Object> params = tool.getParameters();
      c.put("parameters", params != null ? params : Map.of());
      Object required = params != null ? params.get("required") : null;
      if (required instanceof List) {
        c.put("required", required);
      }
      contracts.add(c);
    }

    // DataMap tools: the wrapped SWAIG function map — name under "function",
    // params (with their own embedded `required`) under "parameters".
    for (Map<String, Object> fn : skill.getSwaigFunctions()) {
      Map<String, Object> c = new LinkedHashMap<>();
      c.put("name", fn.get("function"));
      Object params = fn.get("parameters");
      c.put("parameters", params != null ? params : Map.of());
      contracts.add(c);
    }

    return contracts;
  }

  /**
   * Entry point: emits the SKILL-DUMP contract corpus this gate compares across ports.
   *
   * @param args the command-line arguments.
   */
  public static void main(String[] args) {
    try {
      List<CorpusEntry> corpus = loadCorpus();
      Map<String, Object> result = new LinkedHashMap<>();
      for (CorpusEntry entry : corpus) {
        result.put(entry.id(), contractsFor(entry));
      }
      Gson gson = new GsonBuilder().disableHtmlEscaping().create();
      System.out.println(gson.toJson(result));
    } catch (Exception e) {
      System.err.println("emit-skills: " + e.getMessage());
      System.exit(1);
    }
  }
}
