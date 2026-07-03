/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.agents;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.logging.Logger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent implementation for the Amazon Bedrock voice-to-voice model.
 *
 * <p>Mirrors Python's {@code signalwire.agents.bedrock.BedrockAgent} and the Ruby {@code
 * SignalWire::Agents::BedrockAgent}. It extends {@link AgentBase} to keep full compatibility with
 * all SignalWire agent features (skills, POM, SWAIG functions, post-prompt) and renders the same
 * base SWML as {@link AgentBase}, then transforms the {@code ai} verb into an {@code
 * amazon_bedrock} verb whose object carries voice + inference parameters inside its prompt config,
 * per the SWML {@code amazon_bedrock} schema (keys: {@code prompt}, {@code SWAIG}, {@code params},
 * {@code global_data}, {@code post_prompt}, {@code post_prompt_url}).
 */
public class BedrockAgent extends AgentBase {

  /**
   * Prompt keys that apply to text models but not to Bedrock's voice-to-voice model; stripped from
   * the prompt config.
   */
  static final List<String> TEXT_MODEL_ONLY_PROMPT_KEYS =
      Arrays.asList("barge_confidence", "presence_penalty", "frequency_penalty");

  private static final Logger log = Logger.getLogger("bedrock_agent");

  private String voiceId;
  private double temperature;
  private double topP;
  private int maxTokens;

  /**
   * Full constructor. Python parity: {@code __init__(name="bedrock_agent", route="/bedrock",
   * system_prompt=None, voice_id="matthew", temperature=0.7, top_p=0.9, max_tokens=1024)}.
   *
   * @param name agent name
   * @param route HTTP route for the agent
   * @param systemPrompt initial prompt (may be {@code null}; overridable later via {@code
   *     setPromptText})
   * @param voiceId Bedrock voice id (default "matthew")
   * @param temperature generation temperature (0-1)
   * @param topP nucleus sampling parameter (0-1)
   * @param maxTokens maximum tokens to generate
   */
  public BedrockAgent(
      String name,
      String route,
      String systemPrompt,
      String voiceId,
      double temperature,
      double topP,
      int maxTokens) {
    super(name, route, "0.0.0.0", resolvePort(), null, null);
    this.voiceId = voiceId;
    this.temperature = temperature;
    this.topP = topP;
    this.maxTokens = maxTokens;

    if (systemPrompt != null) {
      setPromptText(systemPrompt);
    }
    log.info("BedrockAgent initialized: %s on route %s", name, route);
  }

  /** Convenience constructor using all Python defaults. */
  public BedrockAgent() {
    this("bedrock_agent", "/bedrock", null, "matthew", 0.7, 0.9, 1024);
  }

  /** Convenience constructor overriding only name + route; other defaults preserved. */
  public BedrockAgent(String name, String route) {
    this(name, route, null, "matthew", 0.7, 0.9, 1024);
  }

  /**
   * Render the SWML document, transforming the {@code ai} verb into an {@code amazon_bedrock} verb.
   *
   * <p>Python parity: {@code _render_swml} overrides the base render to swap the {@code ai} verb
   * structure for {@code amazon_bedrock}. The base render builds a Map (not a JSON string), so this
   * operates on that Map directly.
   */
  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> renderSwml(String baseUrl) {
    Map<String, Object> swml = super.renderSwml(baseUrl);
    Object sectionsObj = swml.get("sections");
    if (!(sectionsObj instanceof Map)) {
      return swml;
    }
    Object mainObj = ((Map<String, Object>) sectionsObj).get("main");
    if (!(mainObj instanceof List)) {
      return swml;
    }
    List<Map<String, Object>> main = (List<Map<String, Object>>) mainObj;
    for (int i = 0; i < main.size(); i++) {
      Map<String, Object> verb = main.get(i);
      if (verb != null && verb.containsKey("ai")) {
        Map<String, Object> aiConfig = (Map<String, Object>) verb.get("ai");
        Map<String, Object> bedrockVerb = new LinkedHashMap<>();
        bedrockVerb.put("amazon_bedrock", buildBedrockObject(aiConfig));
        main.set(i, bedrockVerb);
        break;
      }
    }
    return swml;
  }

  /**
   * Build the {@code amazon_bedrock} verb object from the base {@code ai} config. Voice + inference
   * params live inside the prompt config; only non-null keys are emitted (matches the Python
   * reference and the {@code amazon_bedrock} schema).
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> buildBedrockObject(Map<String, Object> aiConfig) {
    Object promptObj = aiConfig.get("prompt");
    Map<String, Object> promptConfig =
        promptObj instanceof Map ? (Map<String, Object>) promptObj : new LinkedHashMap<>();

    Map<String, Object> object = new LinkedHashMap<>();
    object.put("prompt", addVoiceToPrompt(promptConfig));
    putIfNotNull(object, "SWAIG", aiConfig.get("SWAIG"));
    putIfNotNull(object, "params", aiConfig.get("params"));
    putIfNotNull(object, "global_data", aiConfig.get("global_data"));
    putIfNotNull(object, "post_prompt", aiConfig.get("post_prompt"));
    putIfNotNull(object, "post_prompt_url", aiConfig.get("post_prompt_url"));
    return object;
  }

  /** Add voice + inference params to the prompt object, stripping text-model-only keys. */
  private Map<String, Object> addVoiceToPrompt(Map<String, Object> promptConfig) {
    Map<String, Object> filtered = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : promptConfig.entrySet()) {
      if (!TEXT_MODEL_ONLY_PROMPT_KEYS.contains(entry.getKey())) {
        filtered.put(entry.getKey(), entry.getValue());
      }
    }
    filtered.put("voice_id", voiceId);
    filtered.put("temperature", temperature);
    filtered.put("top_p", topP);
    return filtered;
  }

  private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  /**
   * Set the Bedrock voice id.
   *
   * @param voiceId Bedrock voice identifier (e.g. "matthew")
   * @return this
   */
  public BedrockAgent setVoice(String voiceId) {
    this.voiceId = voiceId;
    log.debug("Voice set to: %s", voiceId);
    return this;
  }

  /**
   * Update Bedrock inference parameters. Only non-null values are applied.
   *
   * <p>Python parity: {@code set_inference_params(temperature=None, top_p=None, max_tokens=None)}.
   *
   * @param temperature generation temperature (0-1), or {@code null} to leave unchanged
   * @param topP nucleus sampling parameter (0-1), or {@code null} to leave unchanged
   * @param maxTokens maximum tokens, or {@code null} to leave unchanged
   * @return this
   */
  public BedrockAgent setInferenceParams(Double temperature, Double topP, Integer maxTokens) {
    if (temperature != null) {
      this.temperature = temperature;
    }
    if (topP != null) {
      this.topP = topP;
    }
    if (maxTokens != null) {
      this.maxTokens = maxTokens;
    }
    log.debug(
        "Inference params updated: temp=%s, top_p=%s, max_tokens=%s",
        this.temperature, this.topP, this.maxTokens);
    return this;
  }

  /**
   * Set LLM model — not applicable for Bedrock (fixed voice-to-voice model). Logs a warning and
   * does nothing.
   *
   * @param model model name (ignored)
   * @return this
   */
  public BedrockAgent setLlmModel(String model) {
    log.warn("set_llm_model('%s') called but Bedrock uses a fixed voice-to-voice model", model);
    return this;
  }

  /**
   * Set LLM temperature — redirects to {@link #setInferenceParams}.
   *
   * @param temperature temperature value
   * @return this
   */
  public BedrockAgent setLlmTemperature(double temperature) {
    return setInferenceParams(temperature, null, null);
  }

  /**
   * Set post-prompt LLM parameters — not applicable for Bedrock (the post-prompt uses OpenAI
   * configured server-side). Warns and no-ops.
   *
   * @param params ignored
   * @return this
   */
  public BedrockAgent setPostPromptLlmParams(Map<String, Object> params) {
    log.warn(
        "set_post_prompt_llm_params() called but Bedrock post-prompt uses OpenAI configured in C"
            + " code");
    return this;
  }

  /**
   * Set prompt LLM parameters — use {@link #setInferenceParams} instead for Bedrock. Warns and
   * no-ops.
   *
   * @param params ignored
   * @return this
   */
  public BedrockAgent setPromptLlmParams(Map<String, Object> params) {
    log.warn("set_prompt_llm_params() called - use set_inference_params() for Bedrock");
    return this;
  }

  public String getVoiceId() {
    return voiceId;
  }

  public double getTemperature() {
    return temperature;
  }

  public double getTopP() {
    return topP;
  }

  public int getMaxTokens() {
    return maxTokens;
  }

  /**
   * String representation of the agent.
   *
   * <p>Python parity: {@code __repr__}. The enumerator maps Java {@code toString()} to {@code
   * __repr__}.
   */
  @Override
  public String toString() {
    return "BedrockAgent(name='"
        + getName()
        + "', route='"
        + getRoute()
        + "', voice='"
        + voiceId
        + "')";
  }
}
