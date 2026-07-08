package com.signalwire.sdk.skills;

import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

/**
 * Interface for all skills. Skills are modular capabilities that can be added to agents to provide
 * tools, prompts, hints, and global data.
 */
public interface SkillBase {

  /** Unique skill name. */
  String getName();

  /** Human-readable description. */
  String getDescription();

  /** Semantic version string. */
  default String getVersion() {
    return "1.0.0";
  }

  /** Whether multiple instances can be loaded with different configs. */
  default boolean supportsMultipleInstances() {
    return false;
  }

  /** Required environment variables. */
  default List<String> getRequiredEnvVars() {
    return Collections.emptyList();
  }

  /** Required packages (informational). */
  default List<String> getRequiredPackages() {
    return Collections.emptyList();
  }

  /**
   * Initialize the skill with parameters.
   *
   * @param params Configuration parameters
   * @return true if setup succeeded
   */
  boolean setup(Map<String, Object> params);

  /**
   * Register tools with the agent.
   *
   * @return List of tool definitions to register
   */
  List<ToolDefinition> registerTools();

  /** Get speech recognition hints for this skill. */
  default List<String> getHints() {
    return Collections.emptyList();
  }

  /** Get global data to merge into the agent. */
  default Map<String, Object> getGlobalData() {
    return Collections.emptyMap();
  }

  /**
   * Get prompt sections to inject into the agent. Each section is a map with: title, body, bullets
   * (optional)
   */
  default List<Map<String, Object>> getPromptSections() {
    return Collections.emptyList();
  }

  /** Get SWAIG functions (for DataMap-based skills that bypass handlers). */
  default List<Map<String, Object>> getSwaigFunctions() {
    return Collections.emptyList();
  }

  /** Cleanup resources. */
  default void cleanup() {}

  /** Get parameter schema for GUI tools. */
  default Map<String, Object> getParameterSchema() {
    return Collections.emptyMap();
  }

  /** Get unique instance key (for multi-instance skills). */
  default String getInstanceKey() {
    return getName();
  }

  /** Get extra fields to merge into all tool definitions (e.g., fillers). */
  default Map<String, Object> getExtraFields() {
    return null;
  }

  /**
   * Build a {@link ToolDefinition}, automatically merging this skill's {@link #getExtraFields()}
   * (swaig_fields) into the definition. Skills should use this helper instead of constructing a
   * {@code ToolDefinition} directly so the configured swaig_fields are always applied.
   *
   * <p>Mirrors Python {@code SkillBase.define_tool(**kwargs)}, which delegates to {@code
   * agent.define_tool} after merging {@code self.swaig_fields}. Java skills build their tools in
   * {@link #registerTools()} and return them for the {@link SkillManager} to register with the
   * agent, so this helper returns the merged definition rather than registering it directly.
   *
   * @param name tool name
   * @param description tool description
   * @param parameters JSON-schema parameters map
   * @param handler tool handler
   * @return a tool definition with the skill's extra fields merged in
   */
  default ToolDefinition defineTool(
      String name,
      String description,
      Map<String, Object> parameters,
      com.signalwire.sdk.swaig.ToolHandler handler) {
    ToolDefinition td = new ToolDefinition(name, description, parameters, handler);
    Map<String, Object> extra = getExtraFields();
    if (extra != null && !extra.isEmpty()) {
      td.setExtraFields(new LinkedHashMap<>(extra));
    }
    return td;
  }

  /**
   * The namespaced key under which this skill instance stores state in the agent's global_data.
   *
   * <p>Mirrors Python {@code SkillBase._get_skill_namespace}: {@code "skill:" + getInstanceKey()}.
   * Kept private (not part of the public surface) so {@link #getSkillData} / {@link
   * #updateSkillData} agree on the key.
   *
   * @return the namespace key, e.g. {@code "skill:math"}
   */
  private String getSkillNamespace() {
    return "skill:" + getInstanceKey();
  }

  /**
   * Read this skill instance's namespaced state out of the raw_data passed to a SWAIG handler.
   *
   * <p>Mirrors Python {@code SkillBase.get_skill_data(raw_data)}: reads {@code
   * raw_data["global_data"][namespace]} and returns it, or an empty map when absent.
   *
   * @param rawData the raw_data map a SWAIG function handler receives
   * @return the skill's namespaced state, or an empty map if not present
   */
  @SuppressWarnings("unchecked")
  default Map<String, Object> getSkillData(Map<String, Object> rawData) {
    if (rawData == null) {
      return Collections.emptyMap();
    }
    Object globalData = rawData.get("global_data");
    if (!(globalData instanceof Map)) {
      return Collections.emptyMap();
    }
    Object slice = ((Map<String, Object>) globalData).get(getSkillNamespace());
    if (slice instanceof Map) {
      return (Map<String, Object>) slice;
    }
    return Collections.emptyMap();
  }

  /**
   * Write this skill instance's namespaced state into a {@link FunctionResult}.
   *
   * <p>Mirrors Python {@code SkillBase.update_skill_data(result, data)}: wraps {@code data} under
   * the skill namespace and calls {@code result.update_global_data}. Returns {@code result} for
   * chaining.
   *
   * @param result the FunctionResult to add the global_data update to
   * @param data the skill state to persist under the namespace
   * @return {@code result}, for method chaining
   */
  default FunctionResult updateSkillData(FunctionResult result, Map<String, Object> data) {
    Map<String, Object> wrapped = new LinkedHashMap<>();
    wrapped.put(getSkillNamespace(), data);
    result.updateGlobalData(wrapped);
    return result;
  }

  /**
   * Check that every required environment variable ({@link #getRequiredEnvVars()}) is set.
   *
   * <p>Mirrors Python {@code SkillBase.validate_env_vars}: returns {@code false} when any required
   * variable is unset (or empty), otherwise {@code true}.
   *
   * @return true if all required env vars are present
   */
  default boolean validateEnvVars() {
    for (String var : getRequiredEnvVars()) {
      String value = System.getenv(var);
      if (value == null || value.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Check that every required package ({@link #getRequiredPackages()}) is available on the
   * classpath.
   *
   * <p>Mirrors Python {@code SkillBase.validate_packages}, which imports each required module. Java
   * dependencies are resolved at build time, so a listed package name is treated as a classpath
   * probe: if it resolves to a loadable class it is present. Package names that are not fully
   * qualified class names (the common informational case) are treated as satisfied.
   *
   * @return true if all required packages are available
   */
  default boolean validatePackages() {
    for (String pkg : getRequiredPackages()) {
      if (pkg == null || pkg.isEmpty()) {
        continue;
      }
      // A bare package/module name is informational in Java (no runtime import);
      // only a fully-qualified class name can be probed. Treat an unresolvable
      // dotted class name as missing; treat non-class names as satisfied.
      int lastDot = pkg.lastIndexOf('.');
      boolean looksLikeClass =
          lastDot >= 0
              && lastDot + 1 < pkg.length()
              && Character.isUpperCase(pkg.charAt(lastDot + 1));
      if (looksLikeClass) {
        try {
          Class.forName(pkg, false, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
          return false;
        }
      }
    }
    return true;
  }
}
