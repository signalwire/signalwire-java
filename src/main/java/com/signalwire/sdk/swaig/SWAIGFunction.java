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
 * <p>Mirrors the Python reference {@code signalwire.core.swaig_function.SWAIGFunction} and the Ruby
 * {@code SignalWire::Swaig::SWAIGFunction}. A SWAIGFunction is exactly the same concept as a "tool"
 * in native OpenAI / Anthropic tool calling: it holds a name/description/parameters/handler and
 * renders into the tool schema sent to the model.
 *
 * <p>Construct one via {@link #builder()} (the {@code name}, {@code description}, and {@code
 * handler} are required; everything else is optional) — the enumerator records this as the single
 * {@code __init__}.
 */
public class SWAIGFunction {

  /**
   * Handler signature: {@code (args, rawData) -> result}. The result may be a {@link
   * FunctionResult}, a {@code Map} already containing a {@code "response"} key, any other {@code
   * Map}, or a plain value coerced via {@code toString()} — matching the Python reference's
   * coercion in {@link #execute}.
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

  public String getName() {
    return name;
  }

  public Handler getHandler() {
    return handler;
  }

  public String getDescription() {
    return description;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  public boolean isSecure() {
    return secure;
  }

  public Map<String, Object> getFillers() {
    return fillers;
  }

  public String getWaitFile() {
    return waitFile;
  }

  public Integer getWaitFileLoops() {
    return waitFileLoops;
  }

  public String getWebhookUrl() {
    return webhookUrl;
  }

  public List<String> getRequired() {
    return required;
  }

  public boolean isTypedHandler() {
    return isTypedHandler;
  }

  public Map<String, Object> getExtraSwaigFields() {
    return extraSwaigFields;
  }

  public boolean isExternal() {
    return isExternal;
  }

  /**
   * Call the underlying handler function.
   *
   * <p>Java analog of the Python reference's {@code __call__} (which makes the object callable).
   * {@code function.call(args, rawData)} invokes the handler and returns its raw (uncoerced) return
   * value.
   *
   * @param args parsed arguments for the function
   * @param rawData optional raw request data
   * @return the handler's return value
   */
  public Object call(Map<String, Object> args, Map<String, Object> rawData) {
    return handler.apply(args, rawData);
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
   * {@code type}. This is the Python reference's optional-validator FALLBACK behaviour: the
   * reference tries {@code jsonschema_rs} / {@code jsonschema} and, when neither is installed,
   * skips validation. Java has no bundled JSON-Schema validator dependency, so only the
   * always-available built-in check is ported here (a real dependency limit — see the class docs).
   * If no properties are declared, validation passes.
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

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for {@link SWAIGFunction}; mirrors the Python/Ruby keyword-argument constructor. */
  public static final class Builder {
    private String name;
    private Handler handler;
    private String description;
    private Map<String, Object> parameters;
    private boolean secure = false;
    private Map<String, Object> fillers;
    private String waitFile;
    private Integer waitFileLoops;
    private String webhookUrl;
    private List<String> required;
    private boolean isTypedHandler = false;
    private Map<String, Object> extraSwaigFields;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder handler(Handler handler) {
      this.handler = handler;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder parameters(Map<String, Object> parameters) {
      this.parameters = parameters;
      return this;
    }

    public Builder secure(boolean secure) {
      this.secure = secure;
      return this;
    }

    public Builder fillers(Map<String, Object> fillers) {
      this.fillers = fillers;
      return this;
    }

    public Builder waitFile(String waitFile) {
      this.waitFile = waitFile;
      return this;
    }

    public Builder waitFileLoops(Integer waitFileLoops) {
      this.waitFileLoops = waitFileLoops;
      return this;
    }

    public Builder webhookUrl(String webhookUrl) {
      this.webhookUrl = webhookUrl;
      return this;
    }

    public Builder required(List<String> required) {
      this.required = required;
      return this;
    }

    public Builder isTypedHandler(boolean isTypedHandler) {
      this.isTypedHandler = isTypedHandler;
      return this;
    }

    /** Additional SWAIG-only fields (meta_data_token, web_hook_auth_*, etc.). */
    public Builder extraSwaigFields(Map<String, Object> extraSwaigFields) {
      this.extraSwaigFields = extraSwaigFields;
      return this;
    }

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

    public boolean isValid() {
      return valid;
    }

    public List<String> getErrors() {
      return errors;
    }
  }
}
