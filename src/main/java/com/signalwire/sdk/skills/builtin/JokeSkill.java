package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.datamap.DataMap;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

/**
 * Jokes from the API Ninjas service.
 *
 * <p>Registered under the name {@code joke}; load it with {@code agent.addSkill("joke", params)}.
 */
public class JokeSkill implements SkillBase {

  private String apiKey;
  private String toolName = "get_joke";

  /**
   * The registry name this skill is loaded by: {@code joke}.
   *
   * @return the skill name.
   */
  @Override
  public String getName() {
    return "joke";
  }

  /**
   * Human-readable summary of what this skill adds to an agent.
   *
   * @return the description.
   */
  @Override
  public String getDescription() {
    return "Tell jokes using the API Ninjas joke API";
  }

  /**
   * Configure the skill from its parameters.
   *
   * @param params the skill's configuration.
   * @return {@code true} when setup supplied a non-empty {@code api_key}; {@code false} otherwise,
   *     which leaves the skill unloaded.
   */
  @Override
  public boolean setup(Map<String, Object> params) {
    this.apiKey = (String) params.get("api_key");
    if (params.containsKey("tool_name")) {
      this.toolName = (String) params.get("tool_name");
    }
    return apiKey != null && !apiKey.isEmpty();
  }

  /**
   * The tools this skill contributes to the agent, offered to the model alongside the agent's own.
   *
   * @return the tool definitions.
   */
  @Override
  public List<ToolDefinition> registerTools() {
    // This is a DataMap skill - tools come from getSwaigFunctions
    return Collections.emptyList();
  }

  @Override
  public List<Map<String, Object>> getSwaigFunctions() {
    DataMap dm =
        new DataMap(toolName)
            .purpose("Get a random joke from API Ninjas")
            .parameter("type", "string", "Type of joke", true, List.of("jokes", "dadjokes"))
            .webhook(
                "GET", "https://api.api-ninjas.com/v1/${args.type}", Map.of("X-Api-Key", apiKey))
            .output(new FunctionResult("Here's a joke: ${array[0].joke}"))
            .fallbackOutput(
                new FunctionResult("Why did the programmer quit? Because they didn't get arrays!"));

    return List.of(dm.toSwaigFunction());
  }

  @Override
  public List<Map<String, Object>> getPromptSections() {
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("title", "Joke Telling");
    section.put("body", "You can tell jokes to users on request.");
    section.put(
        "bullets",
        List.of(
            "Use " + toolName + " to get a random joke",
            "You can choose between regular jokes and dad jokes"));
    return List.of(section);
  }

  @Override
  public Map<String, Object> getGlobalData() {
    return Map.of("joke_skill_enabled", true);
  }

  /** Returns an empty hint list. */
  @Override
  public List<String> getHints() {
    return Collections.emptyList();
  }

  /**
   * Parameter schema: base schema plus {@code api_key} (required, hidden, env_var API_NINJAS_KEY)
   * and {@code tool_name} (default "get_joke", optional).
   */
  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = SkillParams.base(supportsMultipleInstances(), getName());
    SkillParams.addString(
        schema, "api_key", "API Ninjas API key for joke service", true, true, "API_NINJAS_KEY");
    Map<String, Object> toolNameParam = new LinkedHashMap<>();
    toolNameParam.put("type", "string");
    toolNameParam.put("description", "Custom name for the joke tool");
    toolNameParam.put("default", "get_joke");
    toolNameParam.put("required", false);
    schema.put("tool_name", toolNameParam);
    return schema;
  }
}
