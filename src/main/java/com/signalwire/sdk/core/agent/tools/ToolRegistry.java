/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core.agent.tools;

import com.signalwire.sdk.logging.Logger;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages SWAIG function registration.
 *
 * <p>Mirrors Python's {@code signalwire.core.agent.tools.registry.ToolRegistry} and the Ruby {@code
 * SignalWire::Core::Agent::Tools::ToolRegistry}. A registry holds SWAIG function definitions keyed
 * by name. Two kinds of entries are supported:
 *
 * <ul>
 *   <li>definitions created via {@link #defineTool} (carry a {@code handler}), and
 *   <li>raw SWAIG function dictionaries via {@link #registerSwaigFunction} (e.g. from a DataMap's
 *       {@code toSwaigFunction}) which execute on SignalWire's server and carry no handler.
 * </ul>
 *
 * <p>Like the Ruby port, the registry stores the built definition Map with string keys, matching
 * the wire shape (the Java SDK's AgentBase also stores plain Maps on the wire).
 */
public class ToolRegistry {

  private static final Logger log = Logger.getLogger("ToolRegistry");

  private final Object agent;
  // name => definition Map (string keys)
  private final Map<String, Map<String, Object>> swaigFunctions = new LinkedHashMap<>();

  /** Construct a standalone registry (no parent agent). */
  public ToolRegistry() {
    this(null);
  }

  /**
   * @param agent optional parent agent instance (kept as a back-reference for parity with the
   *     Python/Ruby registries; may be {@code null} for standalone use).
   */
  public ToolRegistry(Object agent) {
    this.agent = agent;
  }

  /**
   * Define a SWAIG function that the AI can call.
   *
   * <p>Python parity: {@code define_tool(name, description, parameters, handler, secure=True,
   * fillers=None, wait_file=None, wait_file_loops=None, webhook_url=None, required=None,
   * is_typed_handler=False, **swaig_fields)}.
   *
   * @param name function name (must be unique)
   * @param description LLM-facing description
   * @param parameters JSON-Schema parameters
   * @param handler handler invoked when the tool runs (may be {@code null})
   * @param secure whether to require token validation
   * @param fillers language_code =&gt; [phrases] (may be {@code null})
   * @param waitFile audio URL played while running (may be {@code null})
   * @param waitFileLoops loop count for waitFile (may be {@code null})
   * @param webhookUrl external webhook URL (may be {@code null})
   * @param required required parameter names (may be {@code null})
   * @param isTypedHandler handler uses typed params
   * @param swaigFields extra fields merged into the def (may be {@code null})
   * @return the stored definition
   * @throws IllegalArgumentException if the tool name already exists
   */
  public Map<String, Object> defineTool(
      String name,
      String description,
      Map<String, Object> parameters,
      Object handler,
      boolean secure,
      Map<String, Object> fillers,
      String waitFile,
      Integer waitFileLoops,
      String webhookUrl,
      List<String> required,
      boolean isTypedHandler,
      Map<String, Object> swaigFields) {
    if (swaigFunctions.containsKey(name)) {
      throw new IllegalArgumentException("Tool with name '" + name + "' already exists");
    }
    Map<String, Object> definition =
        buildDefinition(
            name,
            description,
            parameters,
            required,
            handler,
            secure,
            fillers,
            waitFile,
            waitFileLoops,
            webhookUrl,
            isTypedHandler,
            swaigFields);
    swaigFunctions.put(name, definition);
    log.debug("Defined tool: %s", name);
    return definition;
  }

  /**
   * Convenience overload with Python defaults ({@code secure=true}, all optionals {@code null}).
   */
  public Map<String, Object> defineTool(
      String name, String description, Map<String, Object> parameters, Object handler) {
    return defineTool(
        name, description, parameters, handler, true, null, null, null, null, null, false, null);
  }

  /**
   * Register a raw SWAIG function dictionary (e.g. from a DataMap's {@code toSwaigFunction}).
   *
   * <p>Python parity: {@code register_swaig_function(function_dict)} — requires a {@code function}
   * field and rejects duplicates. These entries carry no handler (they execute on SignalWire's
   * server).
   *
   * @param functionDict complete SWAIG function definition
   * @return the stored definition
   * @throws IllegalArgumentException if the name is missing or already exists
   */
  public Map<String, Object> registerSwaigFunction(Map<String, Object> functionDict) {
    Object fnameObj = functionDict.get("function");
    if (fnameObj == null || String.valueOf(fnameObj).isEmpty()) {
      throw new IllegalArgumentException(
          "Function dictionary must contain 'function' field with the function name");
    }
    String fname = String.valueOf(fnameObj);
    if (swaigFunctions.containsKey(fname)) {
      throw new IllegalArgumentException("Tool with name '" + fname + "' already exists");
    }
    Map<String, Object> stored = new LinkedHashMap<>(functionDict);
    swaigFunctions.put(fname, stored);
    log.debug("Registered SWAIG function: %s", fname);
    return stored;
  }

  /**
   * Register tools defined via method annotations on the parent agent's class.
   *
   * <p>Python parity: {@code register_class_decorated_tools()} scans the agent class for methods
   * decorated with {@code @AgentBase.tool} (marked with {@code _is_tool}) and registers each one.
   * Java has no method-decorator mechanism that mutates a function object, so the closest faithful
   * analog is a reflective scan of the parent agent's class for methods annotated with {@link
   * Tool}: for each such method, a tool is registered whose name/description come from the
   * annotation (falling back to the method name) and whose handler invokes the method reflectively.
   *
   * <p>Agents built imperatively via {@link #defineTool} (the common Java idiom, since the Java SDK
   * has no class-decorator tool-definition style) declare no {@link Tool}-annotated methods, so
   * this is a no-op for them — mirroring a Python class with no {@code _is_tool} methods. If there
   * is no parent agent, nothing is scanned.
   */
  public void registerClassDecoratedTools() {
    if (agent == null) {
      return;
    }
    Class<?> cls = agent.getClass();
    for (Method method : cls.getMethods()) {
      Tool tool = method.getAnnotation(Tool.class);
      if (tool == null) {
        continue;
      }
      String toolName = tool.name().isEmpty() ? method.getName() : tool.name();
      String description =
          tool.description().isEmpty() ? ("Function " + toolName) : tool.description();
      if (swaigFunctions.containsKey(toolName)) {
        continue;
      }
      final Method bound = method;
      Object handler =
          (com.signalwire.sdk.swaig.SWAIGFunction.Handler)
              (args, rawData) -> {
                try {
                  return bound.invoke(agent, args, rawData);
                } catch (ReflectiveOperationException e) {
                  throw new IllegalStateException(
                      "Failed to invoke class-decorated tool '" + toolName + "'", e);
                }
              };
      defineTool(
          toolName,
          description,
          new LinkedHashMap<>(),
          handler,
          tool.secure(),
          null,
          null,
          null,
          null,
          null,
          false,
          null);
      log.debug("Registered class-decorated tool: %s", toolName);
    }
  }

  /**
   * Get a registered function by name.
   *
   * @param name function name
   * @return the definition Map, or {@code null} if not found
   */
  public Map<String, Object> getFunction(String name) {
    return swaigFunctions.get(name);
  }

  /**
   * Get a copy of all registered functions.
   *
   * @return name =&gt; definition Map
   */
  public Map<String, Map<String, Object>> getAllFunctions() {
    return new LinkedHashMap<>(swaigFunctions);
  }

  /**
   * Check whether a function is registered.
   *
   * @param name function name
   * @return {@code true} if the function exists
   */
  public boolean hasFunction(String name) {
    return swaigFunctions.containsKey(name);
  }

  /**
   * Remove a registered function.
   *
   * @param name function name
   * @return {@code true} if removed, {@code false} if not found
   */
  public boolean removeFunction(String name) {
    if (!swaigFunctions.containsKey(name)) {
      return false;
    }
    swaigFunctions.remove(name);
    log.debug("Removed function: %s", name);
    return true;
  }

  // ---- internals ----

  /**
   * Build the wire-shape definition Map for a defined tool. Optional fields are only emitted when
   * present so the wire matches AgentBase's own tool serialisation.
   */
  private Map<String, Object> buildDefinition(
      String name,
      String description,
      Map<String, Object> parameters,
      List<String> required,
      Object handler,
      boolean secure,
      Map<String, Object> fillers,
      String waitFile,
      Integer waitFileLoops,
      String webhookUrl,
      boolean isTypedHandler,
      Map<String, Object> swaigFields) {
    Map<String, Object> definition = new LinkedHashMap<>();
    definition.put("function", name);
    definition.put("description", description);
    definition.put("parameters", normaliseParameters(parameters, required));

    if (fillers != null && !fillers.isEmpty()) {
      definition.put("fillers", fillers);
    }
    if (waitFile != null) {
      definition.put("wait_file", waitFile);
    }
    if (waitFileLoops != null) {
      definition.put("wait_file_loops", waitFileLoops);
    }
    if (webhookUrl != null) {
      definition.put("webhook_url", webhookUrl);
    }
    if (isTypedHandler) {
      definition.put("is_typed_handler", true);
    }
    if (swaigFields != null) {
      definition.putAll(swaigFields);
    }
    if (handler != null) {
      definition.put("handler", handler);
    }
    definition.put("secure", secure);
    return definition;
  }

  /** Wrap bare properties in an object schema and inject {@code required}. */
  private Map<String, Object> normaliseParameters(
      Map<String, Object> parameters, List<String> required) {
    Map<String, Object> schema = objectSchema(parameters);
    if (required == null || required.isEmpty()) {
      return schema;
    }
    @SuppressWarnings("unchecked")
    List<String> existing =
        schema.get("required") instanceof List
            ? (List<String>) schema.get("required")
            : new java.util.ArrayList<>();
    java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>(existing);
    merged.addAll(required);
    schema.put("required", new java.util.ArrayList<>(merged));
    return schema;
  }

  private Map<String, Object> objectSchema(Map<String, Object> parameters) {
    if (parameters != null && "object".equals(parameters.get("type"))) {
      return new LinkedHashMap<>(parameters);
    }
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", parameters != null ? parameters : new LinkedHashMap<>());
    return schema;
  }

  /**
   * Marker annotation — the Java analog of Python's {@code @AgentBase.tool} decorator. Annotate a
   * public method on an agent subclass so {@link #registerClassDecoratedTools()} auto-registers it.
   * The method must accept {@code (Map<String,Object> args, Map<String,Object> rawData)}.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface Tool {
    /** Tool name; defaults to the method name when empty. */
    String name() default "";

    /** LLM-facing description; defaults to {@code "Function <name>"} when empty. */
    String description() default "";

    /** Whether the tool requires token validation. */
    boolean secure() default true;
  }
}
