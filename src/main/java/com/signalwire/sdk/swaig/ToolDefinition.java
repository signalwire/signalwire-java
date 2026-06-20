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

  /** Serialize to SWAIG function format for SWML. */
  public Map<String, Object> toSwaigFunction(String webhookUrl, String metaDataToken) {
    Map<String, Object> func = new LinkedHashMap<>();
    func.put("function", name);
    func.put("purpose", description);
    if (!parameters.isEmpty()) {
      func.put("argument", parameters);
    }
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

  /** Check if this tool has a handler (as opposed to DataMap tools which don't). */
  public boolean hasHandler() {
    return handler != null;
  }
}
