package com.signalwire.sdk.swaig;

import java.util.*;

/** Defines a SWAIG tool with its name, description, parameters, and handler. */
public class ToolDefinition {
  private final String name;
  private final String description;
  private final Map<String, Object> parameters;
  private final ToolHandler handler;
  private boolean secure = false;
  private Map<String, Object> extraFields;

  public ToolDefinition(
      String name, String description, Map<String, Object> parameters, ToolHandler handler) {
    this.name = name;
    this.description = description;
    this.parameters = parameters != null ? new LinkedHashMap<>(parameters) : new LinkedHashMap<>();
    this.handler = handler;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Map<String, Object> getParameters() {
    return Collections.unmodifiableMap(parameters);
  }

  public ToolHandler getHandler() {
    return handler;
  }

  public boolean isSecure() {
    return secure;
  }

  public ToolDefinition setSecure(boolean secure) {
    this.secure = secure;
    return this;
  }

  public ToolDefinition setExtraFields(Map<String, Object> extraFields) {
    this.extraFields = extraFields;
    return this;
  }

  public Map<String, Object> getExtraFields() {
    return extraFields;
  }

  /**
   * Serialize to SWAIG function format for SWML. Mirrors the Python reference ({@code
   * SWAIGFunction.to_swaig} / {@code AgentBase._render_swaig_functions}): the function definition
   * carries {@code function} (name), {@code description}, and {@code parameters} — the latter
   * passed through {@link #ensureParameterStructure()} so a complete {@code {type,properties}}
   * schema renders FLAT (not double-wrapped) and a bare property map is wrapped in {@code
   * {type:object,properties:…}}.
   */
  public Map<String, Object> toSwaigFunction(String webhookUrl, String metaDataToken) {
    Map<String, Object> func = new LinkedHashMap<>();
    func.put("function", name);
    func.put("description", description);
    func.put("parameters", ensureParameterStructure());
    if (webhookUrl != null) {
      func.put("web_hook_url", webhookUrl);
    }
    if (metaDataToken != null) {
      func.put("meta_data_token", metaDataToken);
    }
    if (extraFields != null) {
      func.putAll(extraFields);
    }
    return func;
  }

  /**
   * Structure the parameters for the SWML wire — the Java mirror of Python's {@code
   * SWAIGFunction._ensure_parameter_structure}: an empty map becomes {@code
   * {type:object,properties:{}}}; a map already carrying {@code type} + {@code properties} passes
   * through unchanged (a complete schema is NOT double-wrapped); otherwise the map is treated as a
   * bare property set and wrapped in {@code {type:object,properties:…}}.
   */
  private Map<String, Object> ensureParameterStructure() {
    Map<String, Object> result = new LinkedHashMap<>();
    if (parameters.isEmpty()) {
      result.put("type", "object");
      result.put("properties", new LinkedHashMap<>());
      return result;
    }
    if (parameters.containsKey("type") && parameters.containsKey("properties")) {
      return parameters;
    }
    result.put("type", "object");
    result.put("properties", parameters);
    return result;
  }

  /** Check if this tool has a handler (as opposed to DataMap tools which don't). */
  public boolean hasHandler() {
    return handler != null;
  }
}
