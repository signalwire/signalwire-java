/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.contexts;

import java.util.*;

/**
 * Configuration for gathering information in a step via the C-side gather_info system.
 *
 * <p>This produces zero tool_call/tool_result entries in LLM-visible history, instead using dynamic
 * step instruction re-injection to present one question at a time.
 */
public class GatherInfo {

  private final List<GatherQuestion> questions;
  private final String outputKey;
  private final String completionAction;
  private final String prompt;
  // Gather-level default for every question; a question's own isolated overrides it.
  private final boolean isolated;

  public GatherInfo(String outputKey, String completionAction, String prompt, boolean isolated) {
    this.questions = new ArrayList<>();
    this.outputKey = outputKey;
    this.completionAction = completionAction;
    this.prompt = prompt;
    this.isolated = isolated;
  }

  public GatherInfo(String outputKey, String completionAction, String prompt) {
    this(outputKey, completionAction, prompt, false);
  }

  public GatherInfo() {
    this(null, null, null, false);
  }

  /** Add a question to gather. */
  public GatherInfo addQuestion(
      String key,
      String question,
      String type,
      boolean confirm,
      String prompt,
      List<String> functions,
      Boolean isolated) {
    questions.add(new GatherQuestion(key, question, type, confirm, prompt, functions, isolated));
    return this;
  }

  public GatherInfo addQuestion(
      String key,
      String question,
      String type,
      boolean confirm,
      String prompt,
      List<String> functions) {
    return addQuestion(key, question, type, confirm, prompt, functions, null);
  }

  public GatherInfo addQuestion(String key, String question) {
    return addQuestion(key, question, "string", false, null, null, null);
  }

  public List<GatherQuestion> getQuestions() {
    return Collections.unmodifiableList(questions);
  }

  public String getCompletionAction() {
    return completionAction;
  }

  public Map<String, Object> toMap() {
    if (questions.isEmpty()) {
      throw new IllegalStateException("gather_info must have at least one question");
    }
    Map<String, Object> map = new LinkedHashMap<>();
    List<Map<String, Object>> questionMaps = new ArrayList<>();
    for (GatherQuestion q : questions) {
      questionMaps.add(q.toMap());
    }
    map.put("questions", questionMaps);
    if (prompt != null) {
      map.put("prompt", prompt);
    }
    if (outputKey != null) {
      map.put("output_key", outputKey);
    }
    if (completionAction != null) {
      map.put("completion_action", completionAction);
    }
    // Gather-level default: emitted only when truthy (a false default is omitted).
    if (isolated) {
      map.put("isolated", true);
    }
    return map;
  }
}
