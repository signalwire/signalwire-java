package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.datamap.DataMap;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

public class ApiNinjaTriviaSkill implements SkillBase {

  private static final List<String> ALL_CATEGORIES =
      List.of(
          "artliterature",
          "language",
          "sciencenature",
          "general",
          "fooddrink",
          "peopleplaces",
          "geography",
          "historyholidays",
          "entertainment",
          "toysgames",
          "music",
          "mathematics",
          "religionmythology",
          "sportsleisure");

  private String apiKey;
  private String toolName = "get_trivia";
  private List<String> categories = new ArrayList<>(ALL_CATEGORIES);

  @Override
  public String getName() {
    return "api_ninjas_trivia";
  }

  @Override
  public String getDescription() {
    return "Get trivia questions from API Ninjas";
  }

  @Override
  public boolean supportsMultipleInstances() {
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean setup(Map<String, Object> params) {
    this.apiKey = (String) params.get("api_key");
    if (params.containsKey("tool_name")) this.toolName = (String) params.get("tool_name");
    if (params.containsKey("categories")) {
      this.categories = (List<String>) params.get("categories");
    }
    return apiKey != null && !apiKey.isEmpty();
  }

  @Override
  public List<ToolDefinition> registerTools() {
    return Collections.emptyList();
  }

  @Override
  public List<Map<String, Object>> getSwaigFunctions() {
    DataMap dm =
        new DataMap(toolName)
            .purpose("Get trivia questions for " + toolName)
            .parameter("category", "string", "Trivia category", true, categories)
            .webhook(
                "GET",
                "https://api.api-ninjas.com/v1/trivia?category=${args.category}",
                Map.of("X-Api-Key", apiKey))
            .output(
                new FunctionResult(
                    "Category ${array[0].category} question: ${array[0].question} "
                        + "Answer: ${array[0].answer}, be sure to give the user time to answer before saying the answer."));

    return List.of(dm.toSwaigFunction());
  }
}
