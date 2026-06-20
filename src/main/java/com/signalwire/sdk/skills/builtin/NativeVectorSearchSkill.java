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
import java.util.*;

/**
 * Native vector search skill - supports remote mode only (network API). Local .swsearch file
 * support is skipped per porting guide.
 */
public class NativeVectorSearchSkill implements SkillBase {

  private static final Logger log = Logger.getLogger(NativeVectorSearchSkill.class);

  private String remoteUrl;
  private String indexName;
  private String toolName = "search_knowledge";
  private String description = "Search the local knowledge base for information";
  private int count = 3;
  private List<String> customHints = new ArrayList<>();

  @Override
  public String getName() {
    return "native_vector_search";
  }

  @Override
  public String getDescription() {
    return "Search document indexes using vector similarity and keyword search (local or remote)";
  }

  @Override
  public boolean supportsMultipleInstances() {
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean setup(Map<String, Object> params) {
    this.remoteUrl = (String) params.get("remote_url");
    this.indexName = (String) params.get("index_name");
    if (params.containsKey("tool_name")) this.toolName = (String) params.get("tool_name");
    if (params.containsKey("description")) this.description = (String) params.get("description");
    if (params.containsKey("count")) this.count = ((Number) params.get("count")).intValue();
    if (params.containsKey("hints")) this.customHints = (List<String>) params.get("hints");
    return remoteUrl != null && !remoteUrl.isEmpty();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ToolDefinition> registerTools() {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("type", "object");
    parameters.put(
        "properties",
        Map.of(
            "query", Map.of("type", "string", "description", "Search query"),
            "count", Map.of("type", "integer", "description", "Number of results to return")));
    parameters.put("required", List.of("query"));

    return List.of(
        new ToolDefinition(
            toolName,
            description,
            parameters,
            (args, raw) -> {
              String query = (String) args.get("query");
              int resultCount =
                  args.containsKey("count") ? ((Number) args.get("count")).intValue() : count;

              try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("query", query);
                body.put("count", resultCount);
                if (indexName != null) body.put("index_name", indexName);

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request =
                    HttpRequest.newBuilder()
                        .uri(URI.create(remoteUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                        .build();
                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                  return new FunctionResult("Search failed: " + response.statusCode());
                }

                Map<String, Object> result =
                    new Gson()
                        .fromJson(
                            response.body(), new TypeToken<Map<String, Object>>() {}.getType());
                List<Map<String, Object>> results =
                    (List<Map<String, Object>>) result.get("results");

                if (results == null || results.isEmpty()) {
                  return new FunctionResult("No results found for: " + query);
                }

                StringBuilder sb = new StringBuilder("Search results:\n\n");
                for (Map<String, Object> r : results) {
                  sb.append(r.getOrDefault("text", r.getOrDefault("content", ""))).append("\n\n");
                }
                return new FunctionResult(sb.toString());
              } catch (Exception e) {
                log.error("Vector search error", e);
                return new FunctionResult("Error performing search: " + e.getMessage());
              }
            }));
  }

  @Override
  public List<String> getHints() {
    List<String> hints =
        new ArrayList<>(List.of("search", "find", "look up", "documentation", "knowledge base"));
    hints.addAll(customHints);
    return hints;
  }
}
