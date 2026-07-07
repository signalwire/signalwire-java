/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handler for the SWML 'ai' verb.
 *
 * <p>The 'ai' verb is complex and requires specialized handling, particularly for managing prompts,
 * SWAIG functions, and AI configurations. Mirrors the Python reference {@code
 * signalwire.core.swml_handler.AIVerbHandler} and the Ruby {@code SignalWire::SWML::AIVerbHandler}.
 */
public class AIVerbHandler extends SWMLVerbHandler {

  /** Top-level AI keys that live outside the params object. */
  private static final Set<String> TOP_LEVEL_AI_KEYS =
      Set.of("languages", "hints", "pronounce", "global_data");

  @Override
  public String getVerbName() {
    return "ai";
  }

  /**
   * Validate the configuration for the AI verb.
   *
   * <p>Checks that {@code prompt} is present and an object, contains exactly one of {@code text} /
   * {@code pom} (mutually exclusive), that {@code prompt.contexts} (if present) is an object, and
   * that {@code SWAIG} (if present) is an object.
   *
   * @param config the AI verb configuration
   * @return a {@link ValidationResult} of (isValid, errorMessages)
   */
  @Override
  @SuppressWarnings("unchecked")
  public ValidationResult validateConfig(Map<String, Object> config) {
    List<String> errors = new ArrayList<>();

    if (config == null || !config.containsKey("prompt")) {
      errors.add("Missing required field 'prompt'");
      return new ValidationResult(false, errors);
    }

    Object promptObj = config.get("prompt");
    if (!(promptObj instanceof Map)) {
      errors.add("'prompt' must be an object");
      return new ValidationResult(false, errors);
    }
    Map<String, Object> prompt = (Map<String, Object>) promptObj;

    boolean hasText = prompt.containsKey("text");
    boolean hasPom = prompt.containsKey("pom");
    int baseCount = (hasText ? 1 : 0) + (hasPom ? 1 : 0);
    if (baseCount == 0) {
      errors.add("'prompt' must contain either 'text' or 'pom' as base prompt");
    } else if (baseCount > 1) {
      errors.add("'prompt' can only contain one of: 'text' or 'pom' (mutually exclusive)");
    }

    if (prompt.containsKey("contexts") && !(prompt.get("contexts") instanceof Map)) {
      errors.add("'prompt.contexts' must be an object");
    }

    if (config.containsKey("SWAIG") && !(config.get("SWAIG") instanceof Map)) {
      errors.add("'SWAIG' must be an object");
    }

    return new ValidationResult(errors.isEmpty(), errors);
  }

  /**
   * Build a configuration for the AI verb (map-based catch-all form).
   *
   * <p>Convenience: extracts the recognised keys ({@code prompt_text}, {@code prompt_pom}, {@code
   * contexts}, {@code post_prompt}, {@code post_prompt_url}, {@code swaig}) from {@code kwargs} and
   * treats the rest as extra AI parameters. Prefer {@link #buildConfig(String, List, Map, String,
   * String, Map, Map)} for a typed call.
   *
   * @param kwargs all arguments as a map (may be {@code null})
   * @return the AI verb configuration map
   */
  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> buildConfig(Map<String, Object> kwargs) {
    Map<String, Object> args = kwargs != null ? new LinkedHashMap<>(kwargs) : new LinkedHashMap<>();
    String promptText = (String) args.remove("prompt_text");
    List<Map<String, Object>> promptPom = (List<Map<String, Object>>) args.remove("prompt_pom");
    Map<String, Object> contexts = (Map<String, Object>) args.remove("contexts");
    String postPrompt = (String) args.remove("post_prompt");
    String postPromptUrl = (String) args.remove("post_prompt_url");
    Map<String, Object> swaig = (Map<String, Object>) args.remove("swaig");
    return buildConfig(promptText, promptPom, contexts, postPrompt, postPromptUrl, swaig, args);
  }

  /**
   * Build a configuration for the AI verb.
   *
   * <p>Requires exactly one of {@code promptText} / {@code promptPom} (mutually exclusive). {@code
   * languages}, {@code hints}, {@code pronounce} and {@code global_data} are placed at the top
   * level; every other extra keyword is placed into {@code config['params']}.
   *
   * @param promptText base text prompt (mutually exclusive with promptPom), or {@code null}
   * @param promptPom POM structure prompt (mutually exclusive with promptText), or {@code null}
   * @param contexts optional contexts/steps configuration, or {@code null}
   * @param postPrompt optional post-prompt text, or {@code null}
   * @param postPromptUrl optional post-prompt URL, or {@code null}
   * @param swaig optional SWAIG configuration, or {@code null}
   * @param kwargs additional AI parameters, or {@code null}
   * @return the AI verb configuration map
   * @throws IllegalArgumentException if neither or both of promptText/promptPom are provided
   */
  public Map<String, Object> buildConfig(
      String promptText,
      List<Map<String, Object>> promptPom,
      Map<String, Object> contexts,
      String postPrompt,
      String postPromptUrl,
      Map<String, Object> swaig,
      Map<String, Object> kwargs) {
    int baseCount = (promptText != null ? 1 : 0) + (promptPom != null ? 1 : 0);
    if (baseCount == 0) {
      throw new IllegalArgumentException(
          "Either prompt_text or prompt_pom must be provided as base prompt");
    }
    if (baseCount > 1) {
      throw new IllegalArgumentException("prompt_text and prompt_pom are mutually exclusive");
    }

    Map<String, Object> config = new LinkedHashMap<>();

    Map<String, Object> promptConfig = new LinkedHashMap<>();
    if (promptText != null) {
      promptConfig.put("text", promptText);
    } else {
      promptConfig.put("pom", promptPom);
    }
    if (contexts != null) {
      promptConfig.put("contexts", contexts);
    }
    config.put("prompt", promptConfig);

    if (postPrompt != null) {
      Map<String, Object> pp = new LinkedHashMap<>();
      pp.put("text", postPrompt);
      config.put("post_prompt", pp);
    }
    if (postPromptUrl != null) {
      config.put("post_prompt_url", postPromptUrl);
    }
    if (swaig != null) {
      config.put("SWAIG", swaig);
    }

    // Match Python behaviour: always initialise the params map.
    Map<String, Object> params = new LinkedHashMap<>();
    config.put("params", params);

    if (kwargs != null) {
      for (Map.Entry<String, Object> e : kwargs.entrySet()) {
        if (TOP_LEVEL_AI_KEYS.contains(e.getKey())) {
          config.put(e.getKey(), e.getValue());
        } else {
          params.put(e.getKey(), e.getValue());
        }
      }
    }

    return config;
  }
}
