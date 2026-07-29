package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

/** Register user-defined custom tools from configuration. */
public class CustomSkillsSkill implements SkillBase {

  private final List<ToolDefinition> customTools = new ArrayList<>();

  /**
   * The registry name this skill is loaded by: {@code custom_skills}.
   *
   * @return the skill name.
   */
  @Override
  public String getName() {
    return "custom_skills";
  }

  /**
   * Human-readable summary of what this skill adds to an agent.
   *
   * @return the description.
   */
  @Override
  public String getDescription() {
    return "Register user-defined custom tools";
  }

  /**
   * Whether an agent may load this skill more than once under different configurations.
   *
   * @return whether multiple instances are supported.
   */
  @Override
  public boolean supportsMultipleInstances() {
    return true;
  }

  /**
   * Configure the skill from its parameters.
   *
   * @param params the skill's configuration.
   * @return {@code true} when setup supplied a non-empty {@code tools} list from which at least one
   *     entry carried a name; {@code false} otherwise, which leaves the skill unloaded.
   */
  @Override
  @SuppressWarnings("unchecked")
  public boolean setup(Map<String, Object> params) {
    List<Map<String, Object>> tools = (List<Map<String, Object>>) params.get("tools");
    if (tools == null || tools.isEmpty()) {
      return false;
    }

    for (Map<String, Object> toolDef : tools) {
      String name = (String) toolDef.get("name");
      String description = (String) toolDef.getOrDefault("description", "Custom tool: " + name);
      Map<String, Object> parameters =
          (Map<String, Object>)
              toolDef.getOrDefault("parameters", Map.of("type", "object", "properties", Map.of()));

      if (name == null) continue;

      customTools.add(
          new ToolDefinition(
              name,
              description,
              parameters,
              (args, raw) ->
                  new FunctionResult("Custom tool " + name + " called with: " + args.toString())));
    }

    return !customTools.isEmpty();
  }

  /**
   * The tools this skill contributes to the agent, offered to the model alongside the agent's own.
   *
   * @return the tool definitions.
   */
  @Override
  public List<ToolDefinition> registerTools() {
    return customTools;
  }
}
