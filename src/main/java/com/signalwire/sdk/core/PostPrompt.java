/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Post-prompt normalization: one conversation can run over voice and over text chat, and both
 * engines produce "the post-prompt" in different shapes. These functions absorb that divergence so
 * an application sees one artifact ({@link NormalizedPostPrompt}) regardless of which engine
 * finished the conversation. Parsing is schema-agnostic: the summary dict is returned as found.
 *
 * <p>Static-only: the reference exposes these as module-level functions of {@code
 * signalwire.core.post_prompt}. Nothing here throws on malformed input — the conversation that
 * produced it is already over.
 */
public final class PostPrompt {

  /**
   * Roles that are actual dialogue; everything else in a call log ({@code system}, {@code
   * system-log}, {@code tool}, {@code assistant-manual}) is machinery.
   */
  public static final List<String> DIALOGUE_ROLES = List.of("user", "assistant");

  private static final Pattern FENCE_OPEN = Pattern.compile("^```[a-zA-Z]*\\s*");
  private static final Pattern FENCE_CLOSE = Pattern.compile("\\s*```$");
  private static final Gson GSON = new Gson();

  private PostPrompt() {}

  /**
   * Unwrap {@code ```json ... ```} fencing (the chat engine hands the model's answer back verbatim,
   * fence and all).
   *
   * @param text the possibly-fenced text ({@code null} reads as empty)
   * @return the unfenced, trimmed text
   */
  public static String stripJsonFence(String text) {
    String stripped = (text == null ? "" : text).strip();
    if (stripped.startsWith("```")) {
      stripped = FENCE_OPEN.matcher(stripped).replaceFirst("");
      stripped = FENCE_CLOSE.matcher(stripped).replaceFirst("");
    }
    return stripped.strip();
  }

  /**
   * Return {@code post_prompt_data} as a plain map, whichever shape it arrived in: flat keys
   * (voice), a fenced JSON {@code raw} string (chat), or the object wrapped in a list under {@code
   * parsed}. Prose instead of JSON yields {@code {"summary": "<the prose>"}}.
   *
   * @param data the {@code post_prompt_data} value
   * @return the summary object, or an empty map when there is nothing usable
   */
  public static Map<String, Object> parsePostPromptData(Object data) {
    if (!(data instanceof Map<?, ?> map)) {
      return new LinkedHashMap<>();
    }
    Map<String, Object> unwrapped = unwrapParsed(map);
    if (unwrapped != null && !unwrapped.isEmpty()) {
      return unwrapped;
    }
    Map<String, Object> flat = new LinkedHashMap<>();
    map.forEach(
        (k, v) -> {
          String key = String.valueOf(k);
          if (!"raw".equals(key) && !"parsed".equals(key)) {
            flat.put(key, v);
          }
        });
    if (!flat.isEmpty()) {
      return flat;
    }
    Object raw = map.get("raw");
    if (!(raw instanceof String rawStr) || rawStr.isBlank()) {
      return new LinkedHashMap<>();
    }
    String unfenced = stripJsonFence(rawStr);
    JsonElement loaded;
    try {
      loaded = JsonParser.parseString(unfenced);
    } catch (RuntimeException e) {
      return singleSummary(unfenced); // prose instead of JSON: still a summary
    }
    if (loaded.isJsonObject()) {
      return GSON.fromJson(loaded, new TypeToken<Map<String, Object>>() {}.getType());
    }
    if (loaded.isJsonPrimitive()) {
      return singleSummary(loaded.getAsString());
    }
    return singleSummary(loaded.toString());
  }

  private static Map<String, Object> singleSummary(String text) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("summary", text);
    return out;
  }

  /** The object out of a {@code {"parsed": [...]}} / {@code {"parsed": {...}}} wrapper, if any. */
  private static Map<String, Object> unwrapParsed(Map<?, ?> data) {
    Object parsed = data.get("parsed");
    if (parsed instanceof Map<?, ?> m) {
      return stringKeyed(m);
    }
    if (parsed instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> m && !m.isEmpty()) {
          return stringKeyed(m);
        }
      }
    }
    return null;
  }

  private static Map<String, Object> stringKeyed(Map<?, ?> m) {
    Map<String, Object> out = new LinkedHashMap<>();
    m.forEach((k, v) -> out.put(String.valueOf(k), v));
    return out;
  }

  /**
   * Extract the real dialogue from a call log, keeping only {@link #DIALOGUE_ROLES}.
   *
   * @param callLog the log ({@code call_log} / {@code raw_call_log} / {@code raw_messages})
   * @return {@code [{"role": ..., "content": ...}, ...]} in order
   */
  public static List<Map<String, String>> dialogueTurns(Object callLog) {
    return dialogueTurns(callLog, DIALOGUE_ROLES, null);
  }

  /**
   * Extract the real dialogue from a call log: drops non-dialogue roles, entries carrying {@code
   * tool_calls}, empty content, and — when {@code dropEcho} is given — the chat engine's summary
   * echo (a bare {@code assistant} turn byte-identical to {@code post_prompt_data.raw}).
   *
   * @param callLog the log ({@code call_log} / {@code raw_call_log} / {@code raw_messages})
   * @param roles roles to keep
   * @param dropEcho exact content to treat as the summary echo and drop (may be {@code null})
   * @return {@code [{"role": ..., "content": ...}, ...]} in order
   */
  public static List<Map<String, String>> dialogueTurns(
      Object callLog, List<String> roles, String dropEcho) {
    if (!(callLog instanceof List<?> log)) {
      return new ArrayList<>();
    }
    List<String> keep = roles == null ? DIALOGUE_ROLES : roles;
    String echo = dropEcho == null ? "" : dropEcho.strip();
    List<Map<String, String>> out = new ArrayList<>();
    for (Object entry : log) {
      if (!(entry instanceof Map<?, ?> e)) {
        continue;
      }
      Object role = e.get("role");
      if (!(role instanceof String roleStr) || !keep.contains(roleStr)) {
        continue;
      }
      if (truthy(e.get("tool_calls"))) {
        continue;
      }
      Object content = e.get("content");
      if (!(content instanceof String text) || text.isBlank()) {
        continue;
      }
      if (!echo.isEmpty() && text.strip().equals(echo)) {
        continue;
      }
      Map<String, String> turn = new LinkedHashMap<>();
      turn.put("role", roleStr);
      turn.put("content", text);
      out.add(turn);
    }
    return out;
  }

  /**
   * Normalize a post-prompt body from either engine.
   *
   * @param body the complete post-prompt request body
   * @return the normalized leg; a body that cannot be read yields one with empty fields
   */
  public static NormalizedPostPrompt normalizePostPrompt(Object body) {
    if (!(body instanceof Map<?, ?> map)) {
      return new NormalizedPostPrompt();
    }
    Object ppd = map.get("post_prompt_data");
    Map<String, Object> summary = parsePostPromptData(ppd);
    String rawSummary = "";
    if (ppd instanceof Map<?, ?> ppdMap && ppdMap.get("raw") instanceof String s) {
      rawSummary = s;
    }
    Object log = firstTruthy(map.get("call_log"), map.get("raw_call_log"), map.get("raw_messages"));
    Object medium = map.get("conversation_type");
    @SuppressWarnings("unchecked")
    Map<String, Object> raw = (Map<String, Object>) map;
    return new NormalizedPostPrompt(
        truthy(medium) ? String.valueOf(medium) : "",
        truthyString(map.get("conversation_id")),
        summary,
        dialogueTurns(log, DIALOGUE_ROLES, rawSummary.isEmpty() ? null : rawSummary),
        truthyString(map.get("call_id")),
        raw);
  }

  private static Object firstTruthy(Object... values) {
    for (Object v : values) {
      if (truthy(v)) {
        return v;
      }
    }
    return Collections.emptyList();
  }

  private static String truthyString(Object v) {
    return truthy(v) ? String.valueOf(v) : null;
  }

  private static boolean truthy(Object v) {
    if (v == null) {
      return false;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    if (v instanceof CharSequence s) {
      return s.length() > 0;
    }
    if (v instanceof Map<?, ?> m) {
      return !m.isEmpty();
    }
    if (v instanceof java.util.Collection<?> c) {
      return !c.isEmpty();
    }
    if (v instanceof Number n) {
      return n.doubleValue() != 0.0;
    }
    return true;
  }
}
