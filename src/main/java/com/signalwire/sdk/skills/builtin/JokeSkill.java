package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.datamap.DataMap;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

public class JokeSkill implements SkillBase {

  private String apiKey;
  private String toolName = "get_joke";

  @Override
  public String getName() {
    return "joke";
  }

  @Override
  public String getDescription() {
    return "Tell jokes using the API Ninjas joke API";
  }

  @Override
  public boolean setup(Map<String, Object> params) {
    this.apiKey = (String) params.get("api_key");
    if (params.containsKey("tool_name")) {
      this.toolName = (String) params.get("tool_name");
    }
    return apiKey != null && !apiKey.isEmpty();
  }

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
}
