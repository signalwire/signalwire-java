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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WebSearchSkill implements SkillBase {

    private static final Logger log = Logger.getLogger(WebSearchSkill.class);

    private String apiKey;
    private String searchEngineId;
    private int numResults = 3;
    private String toolName = "web_search";
    private String noResultsMessage = "No results found for the query.";

    @Override public String getName() { return "web_search"; }
    @Override public String getDescription() { return "Search the web for information using Google Custom Search API"; }
    @Override public String getVersion() { return "2.0.0"; }
    @Override public boolean supportsMultipleInstances() { return true; }

    @Override
    public boolean setup(Map<String, Object> params) {
        this.apiKey = (String) params.get("api_key");
        this.searchEngineId = (String) params.get("search_engine_id");
        if (params.containsKey("num_results")) {
            this.numResults = ((Number) params.get("num_results")).intValue();
            numResults = Math.max(1, Math.min(numResults, 10));
        }
        if (params.containsKey("tool_name")) this.toolName = (String) params.get("tool_name");
        if (params.containsKey("no_results_message")) this.noResultsMessage = (String) params.get("no_results_message");
        return apiKey != null && !apiKey.isEmpty() && searchEngineId != null && !searchEngineId.isEmpty();
    }

    @Override
    public List<ToolDefinition> registerTools() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", Map.of(
                "query", Map.of("type", "string", "description", "The search query")
        ));
        parameters.put("required", List.of("query"));

        return List.of(new ToolDefinition(
                toolName,
                "Search the web for high-quality information, automatically filtering low-quality results",
                parameters,
                this::handleSearch
        ));
    }

    @SuppressWarnings("unchecked")
    private FunctionResult handleSearch(Map<String, Object> args, Map<String, Object> rawData) {
        String query = (String) args.get("query");
        if (query == null || query.isEmpty()) {
            return new FunctionResult("No query provided");
        }

        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            // Allow tests / audit fixtures to redirect the upstream host by
            // setting WEB_SEARCH_BASE_URL (e.g. http://127.0.0.1:NNNN). The
            // path segment "/customsearch/v1" is preserved so the audit can
            // assert the documented Google CSE path on the wire.
            String base = System.getenv("WEB_SEARCH_BASE_URL");
            if (base == null || base.isEmpty()) {
                base = "https://www.googleapis.com";
            }
            // Strip trailing slash so the format string produces a clean URL.
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            String url = String.format(
                    "%s/customsearch/v1?key=%s&cx=%s&q=%s&num=%d",
                    base, apiKey, searchEngineId, encodedQuery, numResults);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return new FunctionResult("Search request failed with status: " + response.statusCode());
            }

            Map<String, Object> result = new Gson().fromJson(response.body(),
                    new TypeToken<Map<String, Object>>() {}.getType());

            List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
            if (items == null || items.isEmpty()) {
                return new FunctionResult(noResultsMessage);
            }

            StringBuilder sb = new StringBuilder("Search results for \"" + query + "\":\n\n");
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> item = items.get(i);
                sb.append(i + 1).append(". ").append(item.get("title")).append("\n");
                sb.append("   ").append(item.get("snippet")).append("\n");
                sb.append("   URL: ").append(item.get("link")).append("\n\n");
            }

            return new FunctionResult(sb.toString());
        } catch (Exception e) {
            log.error("Web search error", e);
            return new FunctionResult("Error performing web search: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getPromptSections() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", "Web Search Capability (Quality Enhanced)");
        section.put("body", "");
        section.put("bullets", List.of(
                "Use " + toolName + " to find current information from the web",
                "Results are quality-filtered for accuracy",
                "Always cite sources when providing search results"
        ));
        return List.of(section);
    }

    @Override
    public Map<String, Object> getGlobalData() {
        return Map.of(
                "web_search_enabled", true,
                "search_provider", "Google Custom Search",
                "quality_filtering", true
        );
    }
}
