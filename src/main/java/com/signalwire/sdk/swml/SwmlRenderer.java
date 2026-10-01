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
 * <p>Both helpers are static; there is nothing to instantiate.
 *
 * <p>{@link #renderSwml(RenderOptions)} has many optional inputs, so it takes a {@link
 * RenderOptions} builder object; convenience overloads cover the common minimal calls.
 */
public final class SwmlRenderer {

  /** Special hook function names that are deduped from the caller's list. */
  private static final List<String> HOOK_FUNCTIONS = List.of("startup_hook", "hangup_hook");

  /** Action verbs (in precedence order) recognised in a function response. */
  private static final List<String> RESPONSE_ACTION_VERBS =
      List.of("play", "hangup", "transfer", "ai");

  private SwmlRenderer() {}

  /**
   * Options for {@link #renderSwml(RenderOptions)}, which has too many optional inputs for a
   * positional signature. Build with {@link #of(Object, Service)} then chain setters.
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

    /**
     * The prompt run after the conversation ends, whose output becomes the call summary.
     *
     * @param v the post-prompt text.
     * @return these options, for chaining.
     */
    public RenderOptions postPrompt(String v) {
      this.postPrompt = v;
      return this;
    }

    /**
     * Where the platform POSTs the post-prompt summary. That endpoint receives conversation
     * content.
     *
     * @param v the post-prompt URL.
     * @return these options, for chaining.
     */
    public RenderOptions postPromptUrl(String v) {
      this.postPromptUrl = v;
      return this;
    }

    /**
     * The SWAIG function definitions to render into the AI verb — the tools the model may call.
     *
     * @param v the function objects as they should appear on the wire.
     * @return these options, for chaining.
     */
    public RenderOptions swaigFunctions(List<Map<String, Object>> v) {
      this.swaigFunctions = v;
      return this;
    }

    /**
     * Endpoint called when the AI session starts.
     *
     * @param v the startup hook URL.
     * @return these options, for chaining.
     */
    public RenderOptions startupHookUrl(String v) {
      this.startupHookUrl = v;
      return this;
    }

    /**
     * Endpoint called when the call hangs up.
     *
     * @param v the hangup hook URL.
     * @return these options, for chaining.
     */
    public RenderOptions hangupHookUrl(String v) {
      this.hangupHookUrl = v;
      return this;
    }

    /**
     * Whether the prompt passed to {@code of(...)} is a POM structure rather than a plain string.
     * This governs how it is rendered, so it must match what was actually supplied.
     *
     * @param v {@code true} when the prompt is a POM.
     * @return these options, for chaining.
     */
    public RenderOptions promptIsPom(boolean v) {
      this.promptIsPom = v;
      return this;
    }

    /**
     * Extra keys merged at the {@code ai} verb's TOP level — they land beside {@code prompt} and
     * {@code SWAIG}, NOT inside {@code ai.params}.
     *
     * <p>{@code AIObject} is CLOSED ({@code unevaluatedProperties: {"not": {}}}), so only keys it
     * declares are legal here — {@code prompt}, {@code post_prompt}, {@code post_prompt_url},
     * {@code SWAIG}, {@code params}, {@code global_data}, {@code hints}, {@code languages}, {@code
     * pronounce}. LLM tuning knobs such as {@code temperature} are {@code AIParams} keys: pass them
     * as {@code params(Map.of("params", Map.of("temperature", 0.4)))}. (This javadoc previously
     * claimed the map rendered into {@code ai.params}; it does not, and the mismatch shipped
     * invalid documents because this path bypassed the validator.)
     *
     * @param v the top-level ai keys.
     * @return these options, for chaining.
     */
    public RenderOptions params(Map<String, Object> v) {
      this.params = v;
      return this;
    }

    /**
     * Whether the rendered document answers the call before the AI verb runs.
     *
     * @param v whether to emit an answer verb.
     * @return these options, for chaining.
     */
    public RenderOptions addAnswer(boolean v) {
      this.addAnswer = v;
      return this;
    }

    /**
     * Whether the rendered document starts background call recording. Recording call audio carries
     * consent and retention obligations in most jurisdictions.
     *
     * @param v whether to record.
     * @return these options, for chaining.
     */
    public RenderOptions recordCall(boolean v) {
      this.recordCall = v;
      return this;
    }

    /**
     * Container format for the recording; defaults to {@code mp4}. Only meaningful when {@link
     * #recordCall(boolean)} is on.
     *
     * @param v the container format.
     * @return these options, for chaining.
     */
    public RenderOptions recordFormat(String v) {
      this.recordFormat = v;
      return this;
    }

    /**
     * Whether the recording keeps each leg on its own channel; defaults to {@code true}, which is
     * what makes per-speaker transcription possible afterwards.
     *
     * @param v whether to record in stereo.
     * @return these options, for chaining.
     */
    public RenderOptions recordStereo(boolean v) {
      this.recordStereo = v;
      return this;
    }

    /**
     * Output format for the rendered document; defaults to {@code json}.
     *
     * @param v the output format.
     * @return these options, for chaining.
     */
    public RenderOptions format(String v) {
      this.format = v;
      return this;
    }

    /**
     * Webhook URL applied to SWAIG functions that do not carry one of their own.
     *
     * @param v the default webhook URL.
     * @return these options, for chaining.
     */
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
      // Through the validating Service.addVerb choke point, as the reference does
      // (swml_renderer.py routes every verb through service.add_verb) — the raw
      // Document entry point accepts any shape, which is how invalid configs ship.
      opts.service.addVerb("record_call", rc);
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
      // Text is played via the `say:` URL scheme — the SWML `play` verb has no `text`
      // key (its config is PlayWithURL/PlayWithURLS, url matching `...|say: ?.*|...`).
      // Emitting `{"text": ...}` produced a document the SWML schema rejects, and it
      // shipped silently because this wrote through the RAW document. Route through the
      // validating Service.addVerb choke point, as the reference does.
      Map<String, Object> play = new LinkedHashMap<>();
      play.put("url", "say:" + responseText);
      service.addVerb("play", play);
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

  /**
   * Add the first recognised action verb from an action map to the document, through the validating
   * {@link Service#addVerb} choke point — a caller-supplied action config that the SWML schema
   * rejects must raise, not ship.
   */
  private static void addResponseAction(Service service, Map<String, Object> action) {
    for (String v : RESPONSE_ACTION_VERBS) {
      if (action.containsKey(v)) {
        service.addVerb(v, action.get(v));
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

  /** Render a document Map as YAML. Block style, insertion-ordered keys (not sorted). */
  private static String renderYaml(Map<String, Object> doc) {
    DumperOptions opts = new DumperOptions();
    opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    opts.setPrettyFlow(false);
    opts.setIndent(2);
    return new Yaml(opts).dump(doc);
  }
}
