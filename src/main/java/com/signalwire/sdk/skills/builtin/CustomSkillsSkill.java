package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

/** Register user-defined custom tools from configuration. */
public class CustomSkillsSkill implements SkillBase {

  private final List<ToolDefinition> customTools = new ArrayList<>();

  @Override
  public String getName() {
    return "custom_skills";
  }

  @Override
  public String getDescription() {
    return "Register user-defined custom tools";
  }

  @Override
  public boolean supportsMultipleInstances() {
    return true;
  }

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

  @Override
  public List<ToolDefinition> registerTools() {
    return customTools;
  }
}
