package com.signalwire.sdk.skills.builtin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DatasphereSkill implements SkillBase {

  private static final Logger log = Logger.getLogger(DatasphereSkill.class);

  private String spaceName;
  private String projectId;
  private String token;
  private String documentId;
  private int count = 1;
  private double distance = 3.0;
  private String toolName = "search_knowledge";
  private String noResultsMessage = "No results found in the knowledge base.";

  @Override
  public String getName() {
    return "datasphere";
  }

  @Override
  public String getDescription() {
    return "Search knowledge using SignalWire DataSphere RAG stack";
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
    if (params.containsKey("no_results_message"))
      this.noResultsMessage = (String) params.get("no_results_message");

    return spaceName != null && projectId != null && token != null && documentId != null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ToolDefinition> registerTools() {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("type", "object");
    parameters.put(
        "properties", Map.of("query", Map.of("type", "string", "description", "The search query")));
    // No `required` — Python's datasphere passes none (datasphere/skill.py:162);
    // the handler guards an empty query. Matches the reference contract.

    return List.of(
        new ToolDefinition(
            toolName,
            "Search the knowledge base for information on any topic and return relevant results",
            parameters,
            (args, raw) -> {
              String query = (String) args.get("query");
              try {
                // Allow tests / audit fixtures to redirect the upstream
                // host by setting DATASPHERE_BASE_URL (e.g.
                // http://127.0.0.1:NNNN). The path
                // "/api/datasphere/documents/search" is preserved so
                // the audit can assert the documented DataSphere POST
                // path on the wire.
                String dsBase = System.getenv("DATASPHERE_BASE_URL");
                if (dsBase == null || dsBase.isEmpty()) {
                  dsBase = "https://" + spaceName;
                }
                if (dsBase.endsWith("/")) {
                  dsBase = dsBase.substring(0, dsBase.length() - 1);
                }
                String url = dsBase + "/api/datasphere/documents/search";
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("query_string", query);
                body.put("document_id", documentId);
                body.put("count", count);
                body.put("distance", distance);

                String authStr = projectId + ":" + token;
                String authHeader =
                    "Basic "
                        + Base64.getEncoder()
                            .encodeToString(authStr.getBytes(StandardCharsets.UTF_8));

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request =
                    HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("Authorization", authHeader)
                        .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                        .build();
                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                  return new FunctionResult("DataSphere search failed: " + response.statusCode());
                }

                Map<String, Object> result =
                    new Gson()
                        .fromJson(
                            response.body(), new TypeToken<Map<String, Object>>() {}.getType());
                // Real DataSphere API returns the array under
                // `chunks`; the porting-sdk audit fixture uses
                // `results`. Both are real-shape upstream responses
                // — accept either so the skill round-trips against
                // the live API and the offline audit alike.
                List<Map<String, Object>> chunks = (List<Map<String, Object>>) result.get("chunks");
                if (chunks == null) {
                  chunks = (List<Map<String, Object>>) result.get("results");
                }

                if (chunks == null || chunks.isEmpty()) {
                  return new FunctionResult(noResultsMessage);
                }

                StringBuilder sb = new StringBuilder("Knowledge base results:\n\n");
                for (Map<String, Object> chunk : chunks) {
                  sb.append(chunk.get("text")).append("\n\n");
                }
                return new FunctionResult(sb.toString());
              } catch (Exception e) {
                log.error("DataSphere search error", e);
                return new FunctionResult("Error searching knowledge base: " + e.getMessage());
              }
            }));
  }

  @Override
  public List<Map<String, Object>> getPromptSections() {
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("title", "Knowledge Search Capability");
    section.put(
        "body",
        "You have access to a knowledge base that can be searched for relevant information.");
    section.put(
        "bullets",
        List.of(
            "Use " + toolName + " to find information in the knowledge base",
            "Always search the knowledge base before saying you don't know something",
            "Cite the knowledge base when providing information from it"));
    return List.of(section);
  }

  @Override
  public Map<String, Object> getGlobalData() {
    return Map.of(
        "datasphere_enabled",
        true,
        "document_id",
        documentId,
        "knowledge_provider",
        "SignalWire DataSphere");
  }

  /** Returns an empty hint list. */
  @Override
  public List<String> getHints() {
    return Collections.emptyList();
  }

  /**
   * Instance key: the skill name plus the tool name (default {@code "search_knowledge"}), joined as
   * {@code <skill>_<tool>}, so multiple instances are differentiated.
   */
  @Override
  public String getInstanceKey() {
    return getName() + "_" + toolName;
  }

  /**
   * Releases the skill's resources. A fresh {@link HttpClient} is opened per request rather than
   * holding a session, so there is nothing to close; this logs and leaves no dangling resource.
   */
  @Override
  public void cleanup() {
    log.debug("DataSphere skill cleaned up");
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
    SkillParams.addString(
        schema, "token", "SignalWire API token", true, true, "SIGNALWIRE_API_TOKEN");
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
