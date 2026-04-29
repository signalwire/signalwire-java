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

    @Override public String getName() { return "datasphere"; }
    @Override public String getDescription() { return "Search knowledge using SignalWire DataSphere RAG stack"; }
    @Override public boolean supportsMultipleInstances() { return true; }

    @Override
    public boolean setup(Map<String, Object> params) {
        this.spaceName = (String) params.get("space_name");
        this.projectId = (String) params.get("project_id");
        this.token = (String) params.get("token");
        this.documentId = (String) params.get("document_id");
        if (params.containsKey("count")) this.count = ((Number) params.get("count")).intValue();
        if (params.containsKey("distance")) this.distance = ((Number) params.get("distance")).doubleValue();
        if (params.containsKey("tool_name")) this.toolName = (String) params.get("tool_name");
        if (params.containsKey("no_results_message")) this.noResultsMessage = (String) params.get("no_results_message");

        return spaceName != null && projectId != null && token != null && documentId != null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ToolDefinition> registerTools() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", Map.of(
                "query", Map.of("type", "string", "description", "The search query")
        ));
        parameters.put("required", List.of("query"));

        return List.of(new ToolDefinition(toolName,
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
                        String authHeader = "Basic " + Base64.getEncoder()
                                .encodeToString(authStr.getBytes(StandardCharsets.UTF_8));

                        HttpClient client = HttpClient.newHttpClient();
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header("Content-Type", "application/json")
                                .header("Authorization", authHeader)
                                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                                .build();
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                        if (response.statusCode() != 200) {
                            return new FunctionResult("DataSphere search failed: " + response.statusCode());
                        }

                        Map<String, Object> result = new Gson().fromJson(response.body(),
                                new TypeToken<Map<String, Object>>() {}.getType());
                        // Real DataSphere API returns the array under
                        // `chunks`; the porting-sdk audit fixture uses
                        // `results`. Both are real-shape upstream responses
                        // — accept either so the skill round-trips against
                        // the live API and the offline audit alike.
                        List<Map<String, Object>> chunks =
                                (List<Map<String, Object>>) result.get("chunks");
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
                }
        ));
    }

    @Override
    public List<Map<String, Object>> getPromptSections() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", "Knowledge Search Capability");
        section.put("body", "You have access to a knowledge base that can be searched for relevant information.");
        section.put("bullets", List.of(
                "Use " + toolName + " to find information in the knowledge base",
                "Always search the knowledge base before saying you don't know something",
                "Cite the knowledge base when providing information from it"
        ));
        return List.of(section);
    }

    @Override
    public Map<String, Object> getGlobalData() {
        return Map.of(
                "datasphere_enabled", true,
                "document_id", documentId,
                "knowledge_provider", "SignalWire DataSphere"
        );
    }
}
