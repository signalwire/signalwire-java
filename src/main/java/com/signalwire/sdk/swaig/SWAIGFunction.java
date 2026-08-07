/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swaig;

import com.signalwire.sdk.logging.Logger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Represents a SWAIG function — a tool the AI model can call.
 *
 * <p>A SWAIGFunction is the same concept as a "tool" in native OpenAI / Anthropic tool calling: it
 * holds a name/description/parameters/handler and renders into the tool schema sent to the model.
 *
 * <p>Construct one via {@link #builder()} — the {@code name}, {@code description}, and {@code
 * handler} are required; everything else is optional.
 */
public class SWAIGFunction {

  /**
   * Handler signature: {@code (args, rawData) -> result}. The result may be a {@link
   * FunctionResult}, a {@code Map} already containing a {@code "response"} key, any other {@code
   * Map}, or a plain value coerced via {@code toString()} — see the coercion in {@link #execute}.
   */
  @FunctionalInterface
  public interface Handler extends BiFunction<Map<String, Object>, Map<String, Object>, Object> {}

  /** Generic, non-leaking message returned when a handler raises. */
  public static final String EXECUTE_ERROR_RESPONSE =
      "Sorry, I couldn't complete that action. Please try again or contact support if the issue"
          + " persists.";

  private final String name;
  private final Handler handler;
  private final String description;
  private final Map<String, Object> parameters;
  private final boolean secure;
  private final Map<String, Object> fillers;
  private final String waitFile;
  private final Integer waitFileLoops;
  private final String webhookUrl;
  private final List<String> required;
  private final boolean isTypedHandler;
  private final Map<String, Object> extraSwaigFields;
  private final boolean isExternal;

  private SWAIGFunction(Builder b) {
    this.name = b.name;
    this.handler = b.handler;
    this.description = b.description;
    this.parameters = b.parameters != null ? b.parameters : new LinkedHashMap<>();
    this.secure = b.secure;
    this.fillers = b.fillers;
    this.waitFile = b.waitFile;
    this.waitFileLoops = b.waitFileLoops;
    this.webhookUrl = b.webhookUrl;
    this.required = b.required != null ? b.required : new ArrayList<>();
    this.isTypedHandler = b.isTypedHandler;
    this.extraSwaigFields = b.extraSwaigFields != null ? b.extraSwaigFields : new LinkedHashMap<>();
    this.isExternal = b.webhookUrl != null; // external when a webhook_url is provided
  }

  // ---- Accessors (parity with Ruby attr_reader) ----

  /**
   * The function name the model calls this tool by, and the key it is registered under.
   *
   * @return the function name.
   */
  public String getName() {
    return name;
  }

  /**
   * The Java callable invoked when the model calls this tool.
   *
   * @return the handler.
   */
  public Handler getHandler() {
    return handler;
  }

  /**
   * The LLM-facing description of when to call this tool. This is prompt text the model reasons
   * over, not a developer comment — a vague one is the usual cause of a registered tool never being
   * called.
   *
   * @return the description.
   */
  public String getDescription() {
    return description;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  /**
   * Whether this tool's rendered webhook URL carries a signed {@code __token} the SDK's HTTP {@code
   * /swaig} handler validates on callback. Defaults to {@code true}; opting out publishes a webhook
   * anyone who sees the SWML can invoke.
   *
   * @return whether the tool is secure.
   */
  public boolean isSecure() {
    return secure;
  }

  public Map<String, Object> getFillers() {
    return fillers;
  }

  /**
   * Audio played to the caller while this tool runs, so a slow handler is not dead air.
   *
   * @return the wait-file URL, or {@code null} when none is set.
   */
  public String getWaitFile() {
    return waitFile;
  }

  /**
   * How many times the wait file repeats while the tool runs.
   *
   * @return the loop count, or {@code null} for the platform default.
   */
  public Integer getWaitFileLoops() {
    return waitFileLoops;
  }

  /**
   * The endpoint the platform calls for this tool instead of this agent. Setting it is what makes
   * the function EXTERNAL — see {@link #isExternal()}.
   *
   * @return the webhook URL, or {@code null} when the tool runs on this agent.
   */
  public String getWebhookUrl() {
    return webhookUrl;
  }

  /**
   * Names of the parameters the model must supply. Rendered into the JSON-schema {@code required}
   * array, and omitted from it entirely when empty.
   *
   * @return the required parameter names, never {@code null}.
   */
  public List<String> getRequired() {
    return required;
  }

  /**
   * Whether the handler expects typed arguments rather than the raw argument map.
   *
   * @return whether the handler is typed.
   */
  public boolean isTypedHandler() {
    return isTypedHandler;
  }

  public Map<String, Object> getExtraSwaigFields() {
    return extraSwaigFields;
  }

  /**
   * Whether the platform calls someone else's endpoint for this tool rather than this agent.
   * Derived, not configured: it is {@code true} exactly when a {@link #getWebhookUrl() webhook URL}
   * was supplied.
   *
   * @return whether the function is external.
   */
  public boolean isExternal() {
    return isExternal;
  }

  /**
   * Call the underlying handler function.
   *
   * <p>{@code function.call(args, rawData)} invokes the handler and returns its raw (uncoerced)
   * return value. Use {@link #execute} instead when you want the result coerced to a {@link
   * FunctionResult} map and handler errors caught.
   *
   * @param args parsed arguments for the function
   * @param rawData optional raw request data
   * @return the handler's return value
   */
  public Object call(Map<String, Object> args, Map<String, Object> rawData) {
    return handler.apply(args, rawData);
  }

  /**
   * Execute the function with no raw request data — equivalent to passing {@code null} for {@code
   * rawData}.
   *
   * @param args parsed arguments for the function
   * @return function result as a Map (from {@link FunctionResult#toMap()})
   */
  public Map<String, Object> execute(Map<String, Object> args) {
    return execute(args, null);
  }

  /**
   * Execute the function with the given arguments.
   *
   * <p>Everything must end up as a {@link FunctionResult} Map. On any error a generic error message
   * is returned (details are logged, not exposed to the AI).
   *
   * @param args parsed arguments for the function
   * @param rawData optional raw request data (may be {@code null})
   * @return function result as a Map (from {@link FunctionResult#toMap()})
   */
  public Map<String, Object> execute(Map<String, Object> args, Map<String, Object> rawData) {
    try {
      Map<String, Object> raw = rawData != null ? rawData : new LinkedHashMap<>();
      return coerceResult(handler.apply(args, raw));
    } catch (RuntimeException e) {
      Logger.getLogger("SWAIG::" + name).error("Error executing SWAIG function " + name + ": " + e);
      return new FunctionResult(EXECUTE_ERROR_RESPONSE).toMap();
    }
  }

  /**
   * Validate the arguments against the parameter schema.
   *
   * <p>Performs a lightweight built-in check of the {@code required} list and each property's
   * {@code type}. This SDK bundles no JSON-Schema validator dependency, so this built-in check is
   * the whole of the validation — constructs beyond {@code required} and {@code type} (patterns,
   * ranges, nested schemas) are not enforced. If no properties are declared, validation passes.
   *
   * @param args arguments to validate
   * @return a {@link ValidationResult} of {@code (valid, errors)}
   */
  public ValidationResult validateArgs(Map<String, Object> args) {
    Map<String, Object> schema = ensureParameterStructure();
    Object props = schema.get("properties");
    if (!(props instanceof Map) || ((Map<?, ?>) props).isEmpty()) {
      return new ValidationResult(true, new ArrayList<>());
    }
    return validateArgsBuiltin(schema, args);
  }

  /**
   * Convert this function to a SWAIG-compatible Map for SWML.
   *
   * @param baseUrl base URL for the webhook
   * @param token optional auth token to include in the URL
   * @param callId optional call ID for session tracking
   * @param includeAuth whether to include auth credentials in the URL (reserved; currently unused)
   * @return representation for the SWAIG array in SWML
   */
  public Map<String, Object> toSwaig(
      String baseUrl, String token, String callId, boolean includeAuth) {
    // All functions use a single /swaig endpoint.
    String url = baseUrl + "/swaig";
    if (token != null && callId != null) {
      url = url + "?token=" + token + "&call_id=" + callId;
    }

    Map<String, Object> functionDef = new LinkedHashMap<>();
    functionDef.put("function", name);
    functionDef.put("description", description);
    functionDef.put("parameters", ensureParameterStructure());
    if (!url.isEmpty()) {
      functionDef.put("web_hook_url", url);
    }
    if (fillers != null && !fillers.isEmpty()) {
      functionDef.put("fillers", fillers);
    }
    functionDef.putAll(extraSwaigFields);
    return functionDef;
  }

  /** Convenience overload using defaults ({@code token=null, callId=null, includeAuth=true}). */
  public Map<String, Object> toSwaig(String baseUrl) {
    return toSwaig(baseUrl, null, null, true);
  }

  // ---- internals ----

  /** Coerce a handler return value into a {@link FunctionResult} Map. */
  @SuppressWarnings("unchecked")
  private Map<String, Object> coerceResult(Object result) {
    if (result instanceof FunctionResult) {
      return ((FunctionResult) result).toMap();
    }
    if (result instanceof Map) {
      Map<String, Object> m = (Map<String, Object>) result;
      if (m.containsKey("response")) {
        return m;
      }
      return new FunctionResult("Function completed successfully").toMap();
    }
    return new FunctionResult(String.valueOf(result)).toMap();
  }

  /**
   * Minimal built-in argument validation: enforces the schema's {@code required} list and each
   * declared property's JSON {@code type}.
   */
  @SuppressWarnings("unchecked")
  private ValidationResult validateArgsBuiltin(
      Map<String, Object> schema, Map<String, Object> args) {
    Map<String, Object> a = args != null ? args : new LinkedHashMap<>();
    List<String> errors = new ArrayList<>();

    Object req = schema.get("required");
    if (req instanceof List) {
      for (Object nameObj : (List<Object>) req) {
        String propName = String.valueOf(nameObj);
        if (!a.containsKey(propName)) {
          errors.add("missing required property '" + propName + "'");
        }
      }
    }

    Object propsObj = schema.get("properties");
    if (propsObj instanceof Map) {
      Map<String, Object> props = (Map<String, Object>) propsObj;
      for (Map.Entry<String, Object> entry : props.entrySet()) {
        String propName = entry.getKey();
        if (!(entry.getValue() instanceof Map) || !a.containsKey(propName)) {
          continue;
        }
        Object typeObj = ((Map<String, Object>) entry.getValue()).get("type");
        if (typeObj instanceof String) {
          String type = (String) typeObj;
          if (!typeMatches(type, a.get(propName))) {
            errors.add("property '" + propName + "' must be of type " + type);
          }
        }
      }
    }
    return new ValidationResult(errors.isEmpty(), errors);
  }

  /** JSON-Schema type predicate used by the built-in validator fallback. */
  private boolean typeMatches(String type, Object value) {
    switch (type) {
      case "string":
        return value instanceof String;
      case "integer":
        // Java has no bare Python int; accept integral Number types.
        return value instanceof Integer || value instanceof Long || value instanceof Short;
      case "number":
        return value instanceof Number;
      case "boolean":
        return value instanceof Boolean;
      case "array":
        return value instanceof List;
      case "object":
        return value instanceof Map;
      default:
        // Unknown type: nothing to enforce.
        return true;
    }
  }

  /**
   * Ensure the parameters are correctly structured for SWML — wrap loose property maps in the
   * {@code {type, properties[, required]}} envelope.
   */
  private Map<String, Object> ensureParameterStructure() {
    if (parameters == null || parameters.isEmpty()) {
      Map<String, Object> empty = new LinkedHashMap<>();
      empty.put("type", "object");
      empty.put("properties", new LinkedHashMap<>());
      return empty;
    }
    if (parameters.containsKey("type") && parameters.containsKey("properties")) {
      return parameters;
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("type", "object");
    result.put("properties", parameters);
    if (!required.isEmpty()) {
      result.put("required", required);
    }
    return result;
  }

  // ---- Builder (the single __init__) ----

  /**
   * Start defining a SWAIG function.
   *
   * @return a fresh {@link Builder}, with {@code secure} already defaulted to {@code true}.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link SWAIGFunction}; mirrors the Python/Ruby keyword-argument constructor. */
  public static final class Builder {
    private String name;
    private Handler handler;
    private String description;
    private Map<String, Object> parameters;
    // A1 (secure-default): default secure=true, matching the Python reference define_tool
    // (secure=True). Call secure(false) to opt out.
    private boolean secure = true;
    private Map<String, Object> fillers;
    private String waitFile;
    private Integer waitFileLoops;
    private String webhookUrl;
    private List<String> required;
    private boolean isTypedHandler = false;
    private Map<String, Object> extraSwaigFields;

    /**
     * The function name the model calls this tool by. Required.
     *
     * @param name the function name; a snake_case verb reads best to the model.
     * @return this builder.
     */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * The Java callable invoked when the model calls this tool. Required.
     *
     * @param handler the handler.
     * @return this builder.
     */
    public Builder handler(Handler handler) {
      this.handler = handler;
      return this;
    }

    /**
     * The LLM-facing description of when to call this tool. Required, and load-bearing — the model
     * reads it to decide whether this tool applies.
     *
     * @param description the description.
     * @return this builder.
     */
    public Builder description(String description) {
      this.description = description;
      return this;
    }

    /**
     * JSON-schema properties for the tool's arguments. Each property's own {@code description} is
     * what the model uses to fill that argument from what the caller said, so write them for the
     * model.
     *
     * @param parameters the properties map.
     * @return this builder.
     */
    public Builder parameters(Map<String, Object> parameters) {
      this.parameters = parameters;
      return this;
    }

    /**
     * Whether the rendered webhook URL carries a signed {@code __token}. Defaults to {@code true};
     * passing {@code false} publishes a webhook anyone who can see the SWML can invoke.
     *
     * @param secure whether the tool is secure.
     * @return this builder.
     */
    public Builder secure(boolean secure) {
      this.secure = secure;
      return this;
    }

    /**
     * Per-language phrases the agent speaks while this tool runs, keyed by language code, so the
     * caller hears something during a slow handler.
     *
     * @param fillers the filler phrases.
     * @return this builder.
     */
    public Builder fillers(Map<String, Object> fillers) {
      this.fillers = fillers;
      return this;
    }

    /**
     * Audio to play while this tool runs, as an alternative to spoken {@link #fillers(Map)}.
     *
     * @param waitFile the audio file URL.
     * @return this builder.
     */
    public Builder waitFile(String waitFile) {
      this.waitFile = waitFile;
      return this;
    }

    /**
     * How many times the wait file repeats.
     *
     * @param waitFileLoops the loop count, or {@code null} for the platform default.
     * @return this builder.
     */
    public Builder waitFileLoops(Integer waitFileLoops) {
      this.waitFileLoops = waitFileLoops;
      return this;
    }

    /**
     * Have the platform call this endpoint for the tool instead of this agent. Supplying it also
     * makes the function {@linkplain SWAIGFunction#isExternal() external}.
     *
     * @param webhookUrl the external endpoint, or {@code null} to run on this agent.
     * @return this builder.
     */
    public Builder webhookUrl(String webhookUrl) {
      this.webhookUrl = webhookUrl;
      return this;
    }

    /**
     * Which parameters the model must supply. Rendered into the JSON-schema {@code required} array.
     *
     * @param required the required parameter names.
     * @return this builder.
     */
    public Builder required(List<String> required) {
      this.required = required;
      return this;
    }

    /**
     * Whether the handler expects typed arguments rather than the raw argument map.
     *
     * @param isTypedHandler whether the handler is typed.
     * @return this builder.
     */
    public Builder isTypedHandler(boolean isTypedHandler) {
      this.isTypedHandler = isTypedHandler;
      return this;
    }

    /** Additional SWAIG-only fields (meta_data_token, web_hook_auth_*, etc.). */
    public Builder extraSwaigFields(Map<String, Object> extraSwaigFields) {
      this.extraSwaigFields = extraSwaigFields;
      return this;
    }

    /**
     * Construct the function, checking the three required fields.
     *
     * @return the constructed function.
     * @throws IllegalArgumentException if {@code name}, {@code handler}, or {@code description} was
     *     not set.
     */
    public SWAIGFunction build() {
      if (name == null) {
        throw new IllegalArgumentException("SWAIGFunction requires a name");
      }
      if (handler == null) {
        throw new IllegalArgumentException("SWAIGFunction requires a handler");
      }
      if (description == null) {
        throw new IllegalArgumentException("SWAIGFunction requires a description");
      }
      return new SWAIGFunction(this);
    }
  }

  /** Result of {@link #validateArgs}: {@code (valid, errors)}. */
  public static final class ValidationResult {
    private final boolean valid;
    private final List<String> errors;

    public ValidationResult(boolean valid, List<String> errors) {
      this.valid = valid;
      this.errors = errors;
    }

    /**
     * Whether the validated arguments satisfied the function's schema.
     *
     * @return {@code true} when there were no errors.
     */
    public boolean isValid() {
      return valid;
    }

    /**
     * The validation failures, empty when {@link #isValid()} is {@code true}.
     *
     * @return the error messages.
     */
    public List<String> getErrors() {
      return errors;
    }
  }
}
