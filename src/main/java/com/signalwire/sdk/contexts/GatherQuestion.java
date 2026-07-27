/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.contexts;

import java.util.*;

/** Represents a single question in a gather_info configuration. */
public class GatherQuestion {

  private final String key;
  private final String question;
  private final String type;
  private final boolean confirm;
  private final String prompt;
  private final List<String> functions;
  // Tri-state: null means "inherit the gather_info default".
  private final Boolean isolated;

  public GatherQuestion(
      String key,
      String question,
      String type,
      boolean confirm,
      String prompt,
      List<String> functions,
      Boolean isolated) {
    this.key = key;
    this.question = question;
    this.type = type != null ? type : "string";
    this.confirm = confirm;
    this.prompt = prompt;
    this.functions = functions;
    this.isolated = isolated;
  }

  public GatherQuestion(
      String key,
      String question,
      String type,
      boolean confirm,
      String prompt,
      List<String> functions) {
    this(key, question, type, confirm, prompt, functions, null);
  }

  public GatherQuestion(String key, String question) {
    this(key, question, "string", false, null, null, null);
  }

  public String getKey() {
    return key;
  }

  // Read side of the remaining construction params. The reference stores each as a
  // public instance attribute (contexts.py GatherQuestion.__init__), so a caller that
  // supplies the value at construction can read it back.

  /** The question text (the {@code question} construction param). */
  public String getQuestion() {
    return question;
  }

  /** The answer type (the {@code type} construction param); defaults to {@code "string"}. */
  public String getType() {
    return type;
  }

  /** Whether the answer is confirmed back to the caller (the {@code confirm} param). */
  public boolean isConfirm() {
    return confirm;
  }

  /** Optional per-question prompt override (the {@code prompt} param). */
  public String getPrompt() {
    return prompt;
  }

  /** Functions callable while gathering this answer (the {@code functions} param). */
  public List<String> getFunctions() {
    return functions;
  }

  /** Whether the question runs in an isolated context (the {@code isolated} param). */
  public Boolean getIsolated() {
    return isolated;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("key", key);
    map.put("question", question);
    if (!"string".equals(type)) {
      map.put("type", type);
    }
    if (confirm) {
      map.put("confirm", true);
    }
    if (prompt != null) {
      map.put("prompt", prompt);
    }
    if (functions != null && !functions.isEmpty()) {
      map.put("functions", functions);
    }
    // Emitted even when False, so it can override an isolated gather. Omit when null.
    if (isolated != null) {
      map.put("isolated", isolated);
    }
    return map;
  }
}
