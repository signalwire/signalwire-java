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
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Renders SWML documents for SignalWire AI Agents with AI and SWAIG components, built on top of the
 * {@link Service} document model.
 *
 * <p>Mirrors the Python reference {@code signalwire.core.swml_renderer.SwmlRenderer} (two
 * static-method helpers) and the Ruby {@code SignalWire::SWML::SwmlRenderer}. Both helpers are
 * static.
 *
 * <p>{@link #renderSwml(RenderOptions)} has many optional inputs, so it takes a {@link
 * RenderOptions} builder object (the Java named-parameter idiom); convenience overloads cover the
 * common minimal calls.
 */
public final class SwmlRenderer {

  /** Special hook function names that are deduped from the caller's list. */
  private static final List<String> HOOK_FUNCTIONS = List.of("startup_hook", "hangup_hook");

  /** Action verbs (in precedence order) recognised in a function response. */
  private static final List<String> RESPONSE_ACTION_VERBS =
      List.of("play", "hangup", "transfer", "ai");

  private SwmlRenderer() {}

  /**
   * Options for {@link #renderSwml(RenderOptions)} — the Java named-parameter idiom for the many
   * optional inputs of the reference {@code render_swml} static method. Build with {@link
   * #of(Object, Service)} then chain setters.
   */
  public static final class RenderOptions {
    private Object prompt;
    private Service service;
    private String postPrompt;
    private String postPromptUrl;
    private List<Map<String, Object>> swaigFunctions;
    private String startupHookUrl;
    private String hangupHookUrl;
    private boolean promptIsPom;
    private Map<String, Object> params;
    private boolean addAnswer;
    private boolean recordCall;
    private String recordFormat = "mp4";
    private boolean recordStereo = true;
    private String format = "json";
    private String defaultWebhookUrl;

    private RenderOptions() {}

    /**
     * Create render options with the two required inputs.
     *
     * @param prompt AI prompt text (a {@code String}), or a POM structure ({@code List<Map>}) when
     *     {@link #promptIsPom(boolean)} is set
     * @param service the service to build the document with
     * @return a new options object
     */
    public static RenderOptions of(Object prompt, Service service) {
      RenderOptions o = new RenderOptions();
      o.prompt = prompt;
      o.service = service;
      return o;
    }

    public RenderOptions postPrompt(String v) {
      this.postPrompt = v;
      return this;
    }

    public RenderOptions postPromptUrl(String v) {
      this.postPromptUrl = v;
      return this;
    }

    public RenderOptions swaigFunctions(List<Map<String, Object>> v) {
      this.swaigFunctions = v;
      return this;
    }

    public RenderOptions startupHookUrl(String v) {
      this.startupHookUrl = v;
      return this;
    }

    public RenderOptions hangupHookUrl(String v) {
      this.hangupHookUrl = v;
      return this;
    }

    public RenderOptions promptIsPom(boolean v) {
      this.promptIsPom = v;
      return this;
    }

    public RenderOptions params(Map<String, Object> v) {
      this.params = v;
      return this;
    }

    public RenderOptions addAnswer(boolean v) {
      this.addAnswer = v;
      return this;
    }

    public RenderOptions recordCall(boolean v) {
      this.recordCall = v;
      return this;
    }

    public RenderOptions recordFormat(String v) {
      this.recordFormat = v;
      return this;
    }

    public RenderOptions recordStereo(boolean v) {
      this.recordStereo = v;
      return this;
    }

    public RenderOptions format(String v) {
      this.format = v;
      return this;
    }

    public RenderOptions defaultWebhookUrl(String v) {
      this.defaultWebhookUrl = v;
      return this;
    }
  }

  /**
   * Generate a complete SWML document with an AI configuration (minimal form).
   *
   * @param prompt AI prompt text
   * @param service the service to build with
   * @return SWML document as a string
   */
  public static String renderSwml(String prompt, Service service) {
    return renderSwml(RenderOptions.of(prompt, service));
  }

  /**
   * Generate a complete SWML document with an AI configuration.
   *
   * @param opts the render options (prompt + service required; the rest optional)
   * @return SWML document as a string
   */
  @SuppressWarnings("unchecked")
  public static String renderSwml(RenderOptions opts) {
    SWMLBuilder builder = new SWMLBuilder(opts.service);
    builder.reset();
    if (opts.addAnswer) {
      builder.answer();
    }
    if (opts.recordCall) {
      Map<String, Object> rc = new LinkedHashMap<>();
      rc.put("format", opts.recordFormat);
      rc.put("stereo", opts.recordStereo);
      opts.service.getDocument().addVerb("record_call", rc);
    }

    List<Map<String, Object>> functions =
        buildFunctions(opts.swaigFunctions, opts.startupHookUrl, opts.hangupHookUrl);
    Map<String, Object> swaigConfig = buildSwaigConfig(functions, opts.defaultWebhookUrl);

    String promptText = opts.promptIsPom ? null : (String) opts.prompt;
    List<Map<String, Object>> promptPom =
        opts.promptIsPom ? (List<Map<String, Object>>) opts.prompt : null;

    builder.ai(
        promptText,
        promptPom,
        opts.postPrompt,
        opts.postPromptUrl,
        swaigConfig.isEmpty() ? null : swaigConfig,
        opts.params);

    if ("yaml".equalsIgnoreCase(opts.format)) {
      return renderYaml(builder.build());
    }
    return builder.render();
  }

  /**
   * Generate a SWML document for a function response — a {@code play} of the response text followed
   * by any provided actions (JSON format).
   *
   * @param responseText text response to include in the document
   * @param service the service to build with
   * @return SWML document as a string
   */
  public static String renderFunctionResponseSwml(String responseText, Service service) {
    return renderFunctionResponseSwml(responseText, service, null, "json");
  }

  /**
   * Generate a SWML document for a function response — a {@code play} of the response text followed
   * by any provided actions.
   *
   * @param responseText text response to include in the document
   * @param service the service to build with
   * @param actions optional list of actions to perform, or {@code null}
   * @param format output format ("json" or "yaml")
   * @return SWML document as a string
   */
  public static String renderFunctionResponseSwml(
      String responseText, Service service, List<Map<String, Object>> actions, String format) {
    service.getDocument().reset();
    if (responseText != null && !responseText.isEmpty()) {
      Map<String, Object> play = new LinkedHashMap<>();
      play.put("text", responseText);
      service.getDocument().addVerb("play", play);
    }
    if (actions != null) {
      for (Map<String, Object> action : actions) {
        addResponseAction(service, action);
      }
    }

    if ("yaml".equalsIgnoreCase(format)) {
      return renderYaml(service.getDocument().toMap());
    }
    return service.getDocument().render();
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  /** Add the first recognised action verb from an action map to the document. */
  private static void addResponseAction(Service service, Map<String, Object> action) {
    for (String v : RESPONSE_ACTION_VERBS) {
      if (action.containsKey(v)) {
        service.getDocument().addVerb(v, action.get(v));
        return;
      }
    }
  }

  /**
   * Build the SWAIG function list, prepending startup/hangup hooks and skipping any duplicate hooks
   * in the caller-supplied list.
   */
  private static List<Map<String, Object>> buildFunctions(
      List<Map<String, Object>> swaigFunctions, String startupHookUrl, String hangupHookUrl) {
    List<Map<String, Object>> functions = new ArrayList<>();
    if (startupHookUrl != null && !startupHookUrl.isEmpty()) {
      functions.add(hookFunction("startup_hook", "Called when the call starts", startupHookUrl));
    }
    if (hangupHookUrl != null && !hangupHookUrl.isEmpty()) {
      functions.add(hookFunction("hangup_hook", "Called when the call ends", hangupHookUrl));
    }
    if (swaigFunctions != null) {
      for (Map<String, Object> func : swaigFunctions) {
        Object fn = func.get("function");
        if (!HOOK_FUNCTIONS.contains(fn)) {
          functions.add(func);
        }
      }
    }
    return functions;
  }

  /** Build a single startup/hangup hook function definition. */
  private static Map<String, Object> hookFunction(String name, String description, String url) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("type", "object");
    params.put("properties", new LinkedHashMap<String, Object>());

    Map<String, Object> fn = new LinkedHashMap<>();
    fn.put("function", name);
    fn.put("description", description);
    fn.put("parameters", params);
    fn.put("web_hook_url", url);
    return fn;
  }

  /** Build the SWAIG config object from the function list + default URL. */
  private static Map<String, Object> buildSwaigConfig(
      List<Map<String, Object>> functions, String defaultWebhookUrl) {
    Map<String, Object> swaigConfig = new LinkedHashMap<>();
    boolean hasDefault = defaultWebhookUrl != null && !defaultWebhookUrl.isEmpty();
    if (functions.isEmpty() && !hasDefault) {
      return swaigConfig;
    }
    if (hasDefault) {
      Map<String, Object> defaults = new LinkedHashMap<>();
      defaults.put("web_hook_url", defaultWebhookUrl);
      swaigConfig.put("defaults", defaults);
    }
    if (!functions.isEmpty()) {
      swaigConfig.put("functions", functions);
    }
    return swaigConfig;
  }

  /**
   * Render a document Map as YAML (parity with the reference's optional yaml branch, which calls
   * {@code yaml.dump(doc, sort_keys=False)}). Block style, insertion-ordered keys.
   */
  private static String renderYaml(Map<String, Object> doc) {
    DumperOptions opts = new DumperOptions();
    opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    opts.setPrettyFlow(false);
    opts.setIndent(2);
    return new Yaml(opts).dump(doc);
  }
}
