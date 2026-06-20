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
}
