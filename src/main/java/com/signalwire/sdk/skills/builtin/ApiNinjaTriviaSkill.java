package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.datamap.DataMap;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

/**
 * Trivia questions from the API Ninjas service.
 *
 * <p>Registered under the name {@code api_ninjas_trivia}; load it with {@code
 * agent.addSkill("api_ninjas_trivia", params)}.
 */
public class ApiNinjaTriviaSkill implements SkillBase {

  /**
   * Ordered map of category key to human-readable description. Used both as the valid-category list
   * and to build the {@code categories} parameter-schema description.
   */
  private static final Map<String, String> VALID_CATEGORIES;

  static {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("artliterature", "Art and Literature");
    m.put("language", "Language");
    m.put("sciencenature", "Science and Nature");
    m.put("general", "General Knowledge");
    m.put("fooddrink", "Food and Drink");
    m.put("peopleplaces", "People and Places");
    m.put("geography", "Geography");
    m.put("historyholidays", "History and Holidays");
    m.put("entertainment", "Entertainment");
    m.put("toysgames", "Toys and Games");
    m.put("music", "Music");
    m.put("mathematics", "Mathematics");
    m.put("religionmythology", "Religion and Mythology");
    m.put("sportsleisure", "Sports and Leisure");
    VALID_CATEGORIES = Collections.unmodifiableMap(m);
  }

  private static final List<String> ALL_CATEGORIES = List.copyOf(VALID_CATEGORIES.keySet());

  private String apiKey;
  private String toolName = "get_trivia";
  private List<String> categories = new ArrayList<>(ALL_CATEGORIES);

  /**
   * The registry name this skill is loaded by: {@code api_ninjas_trivia}.
   *
   * @return the skill name.
   */
  @Override
  public String getName() {
    return "api_ninjas_trivia";
  }

  /**
   * Human-readable summary of what this skill adds to an agent.
   *
   * @return the description.
   */
  @Override
  public String getDescription() {
    return "Get trivia questions from API Ninjas";
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
   * @return {@code true} when setup supplied a non-empty {@code api_key}; {@code false} otherwise,
   *     which leaves the skill unloaded.
   */
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

  /**
   * The tools this skill contributes to the agent, offered to the model alongside the agent's own.
   *
   * @return the tool definitions.
   */
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

  /** Instance key: the skill name plus the tool name, joined as {@code <skill>_<tool>}. */
  @Override
  public String getInstanceKey() {
    return getName() + "_" + toolName;
  }

  /**
   * Returns this skill's tools. The DataMap tool is built in {@link #getSwaigFunctions()}, so
   * {@code getTools()} returns exactly that list (the DataMap tool list).
   */
  public List<Map<String, Object>> getTools() {
    return getSwaigFunctions();
  }

  /**
   * Parameter schema: base schema plus {@code api_key} (hidden, required, env_var API_NINJAS_KEY)
   * and {@code categories} (array of category keys; description enumerates VALID_CATEGORIES).
   */
  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = SkillParams.base(true, getName());

    SkillParams.addString(schema, "api_key", "API Ninjas API key", true, true, "API_NINJAS_KEY");

    StringBuilder options = new StringBuilder("List of trivia categories to enable. Available: ");
    boolean first = true;
    for (Map.Entry<String, String> e : VALID_CATEGORIES.entrySet()) {
      if (!first) {
        options.append(", ");
      }
      options.append(e.getKey()).append(" (").append(e.getValue()).append(")");
      first = false;
    }

    Map<String, Object> categoriesField = new LinkedHashMap<>();
    categoriesField.put("type", "array");
    categoriesField.put("description", options.toString());
    categoriesField.put("default", new ArrayList<>(VALID_CATEGORIES.keySet()));
    categoriesField.put("required", false);
    categoriesField.put(
        "items", Map.of("type", "string", "enum", new ArrayList<>(VALID_CATEGORIES.keySet())));
    schema.put("categories", categoriesField);

    return schema;
  }
}
