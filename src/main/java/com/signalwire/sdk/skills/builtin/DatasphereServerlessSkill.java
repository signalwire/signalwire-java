package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.datamap.DataMap;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DatasphereServerlessSkill implements SkillBase {

  private String spaceName;
  private String projectId;
  private String token;
  private String documentId;
  private int count = 1;
  private double distance = 3.0;
  private String toolName = "search_knowledge";

  @Override
  public String getName() {
    return "datasphere_serverless";
  }

  @Override
  public String getDescription() {
    return "Search knowledge using SignalWire DataSphere with serverless DataMap execution";
  }

  @Override
  public boolean supportsMultipleInstances() {
    return true;
  }

  @Override
  public boolean setup(Map<String, Object> params) {
    this.spaceName = (String) params.get("space_name");
    this.projectId = (String) params.get("project_id");
    this.token = (String) params.get("token");
    this.documentId = (String) params.get("document_id");
    if (params.containsKey("count")) this.count = ((Number) params.get("count")).intValue();
    if (params.containsKey("distance"))
      this.distance = ((Number) params.get("distance")).doubleValue();
    if (params.containsKey("tool_name")) this.toolName = (String) params.get("tool_name");
    return spaceName != null && projectId != null && token != null && documentId != null;
  }

  @Override
  public List<ToolDefinition> registerTools() {
    return Collections.emptyList();
  }

  @Override
  public List<Map<String, Object>> getSwaigFunctions() {
    String url = "https://" + spaceName + "/api/datasphere/documents/search";
    String authStr = projectId + ":" + token;
    String authEncoded =
        Base64.getEncoder().encodeToString(authStr.getBytes(StandardCharsets.UTF_8));

    DataMap dm =
        new DataMap(toolName)
            .purpose(
                "Search the knowledge base for information on any topic and return relevant results")
            .parameter("query", "string", "The search query", true)
            .webhook(
                "POST",
                url,
                Map.of("Content-Type", "application/json", "Authorization", "Basic " + authEncoded))
            .body(
                Map.of(
                    "query_string", "${args.query}",
                    "document_id", documentId,
                    "count", count,
                    "distance", distance))
            .output(
                new FunctionResult(
                    "I found results for \"${args.query}\":\n\n${formatted_results}"));

    return List.of(dm.toSwaigFunction());
  }

  @Override
  public List<Map<String, Object>> getPromptSections() {
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("title", "Knowledge Search Capability (Serverless)");
    section.put("body", "You have access to a serverless knowledge base search.");
    section.put(
        "bullets",
        List.of(
            "Use " + toolName + " to search the knowledge base without a webhook",
            "Results are processed directly on SignalWire servers"));
    return List.of(section);
  }

  @Override
  public Map<String, Object> getGlobalData() {
    return Map.of(
        "datasphere_serverless_enabled",
        true,
        "document_id",
        documentId,
        "knowledge_provider",
        "SignalWire DataSphere (Serverless)");
  }

  /** Returns an empty hint list. */
  @Override
  public List<String> getHints() {
    return Collections.emptyList();
  }

  /**
   * Instance key: the skill name plus the tool name (default {@code "search_knowledge"}), joined as
   * {@code <skill>_<tool>}.
   */
  @Override
  public String getInstanceKey() {
    return getName() + "_" + toolName;
  }

  /** Parameter schema: base params plus custom fields. */
  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = SkillParams.base(true, getName());

    SkillParams.addString(
        schema,
        "space_name",
        "SignalWire space name (e.g., 'mycompany' from mycompany.signalwire.com)",
        true,
        false,
        null);
    SkillParams.addString(
        schema, "project_id", "SignalWire project ID", true, false, "SIGNALWIRE_PROJECT_ID");
    SkillParams.addString(schema, "token", "SignalWire API token", true, true, "SIGNALWIRE_TOKEN");
    SkillParams.addString(
        schema, "document_id", "DataSphere document ID to search within", true, false, null);

    Map<String, Object> count = numberEntry("integer", "Number of search results to return", 1);
    count.put("minimum", 1);
    count.put("maximum", 10);
    schema.put("count", count);

    Map<String, Object> distance =
        numberEntry(
            "number", "Maximum distance threshold for results (lower is more relevant)", 3.0);
    distance.put("minimum", 0.0);
    distance.put("maximum", 10.0);
    schema.put("distance", distance);

    Map<String, Object> tags = new LinkedHashMap<>();
    tags.put("type", "array");
    tags.put("description", "Tags to filter search results");
    tags.put("required", false);
    tags.put("items", Map.of("type", "string"));
    schema.put("tags", tags);

    Map<String, Object> language = new LinkedHashMap<>();
    language.put("type", "string");
    language.put("description", "Language code for query expansion (e.g., 'en', 'es')");
    language.put("required", false);
    schema.put("language", language);

    Map<String, Object> posToExpand = new LinkedHashMap<>();
    posToExpand.put("type", "array");
    posToExpand.put("description", "Parts of speech to expand with synonyms");
    posToExpand.put("required", false);
    posToExpand.put(
        "items", Map.of("type", "string", "enum", List.of("NOUN", "VERB", "ADJ", "ADV")));
    schema.put("pos_to_expand", posToExpand);

    Map<String, Object> maxSynonyms = new LinkedHashMap<>();
    maxSynonyms.put("type", "integer");
    maxSynonyms.put("description", "Maximum number of synonyms to use for query expansion");
    maxSynonyms.put("required", false);
    maxSynonyms.put("minimum", 1);
    maxSynonyms.put("maximum", 10);
    schema.put("max_synonyms", maxSynonyms);

    Map<String, Object> noResults = new LinkedHashMap<>();
    noResults.put("type", "string");
    noResults.put("description", "Message to return when no results are found");
    noResults.put(
        "default",
        "I couldn't find any relevant information for '{query}' in the knowledge base. Try"
            + " rephrasing your question or asking about a different topic.");
    noResults.put("required", false);
    schema.put("no_results_message", noResults);

    return schema;
  }

  private static Map<String, Object> numberEntry(String type, String description, Object dflt) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", type);
    m.put("description", description);
    m.put("default", dflt);
    m.put("required", false);
    return m;
  }
}
