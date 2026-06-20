package com.signalwire.sdk.skills;

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
}
