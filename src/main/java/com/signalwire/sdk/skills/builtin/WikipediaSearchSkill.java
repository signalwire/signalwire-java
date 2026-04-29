package com.signalwire.sdk.skills.builtin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WikipediaSearchSkill implements SkillBase {

    private static final Logger log = Logger.getLogger(WikipediaSearchSkill.class);

    private int numResults = 1;
    private String noResultsMessage = "No Wikipedia articles found for the query.";

    @Override public String getName() { return "wikipedia_search"; }
    @Override public String getDescription() { return "Search Wikipedia for information about a topic and get article summaries"; }

    @Override
    public boolean setup(Map<String, Object> params) {
        if (params.containsKey("num_results")) {
            this.numResults = ((Number) params.get("num_results")).intValue();
            numResults = Math.max(1, Math.min(numResults, 5));
        }
        if (params.containsKey("no_results_message")) {
            this.noResultsMessage = (String) params.get("no_results_message");
        }
        return true;
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

        return List.of(new ToolDefinition(
                "search_wiki",
                "Search Wikipedia for information about a topic and get article summaries",
                parameters,
                (args, raw) -> {
                    String query = (String) args.get("query");
                    if (query == null || query.isEmpty()) {
                        return new FunctionResult("No query provided");
                    }
                    try {
                        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                        // Allow tests / audit fixtures to redirect the upstream
                        // by setting WIKIPEDIA_BASE_URL. The path /w/api.php is
                        // preserved so the audit can assert the documented
                        // Wikipedia API path on the wire.
                        String wikiBase = System.getenv("WIKIPEDIA_BASE_URL");
                        if (wikiBase == null || wikiBase.isEmpty()) {
                            wikiBase = "https://en.wikipedia.org";
                        }
                        if (wikiBase.endsWith("/")) {
                            wikiBase = wikiBase.substring(0, wikiBase.length() - 1);
                        }
                        String searchUrl = wikiBase + "/w/api.php?action=query&list=search" +
                                "&srsearch=" + encoded + "&srlimit=" + numResults +
                                "&format=json&utf8=1";

                        HttpClient client = HttpClient.newHttpClient();
                        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(searchUrl)).GET().build();
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                        Map<String, Object> result = new Gson().fromJson(response.body(),
                                new TypeToken<Map<String, Object>>() {}.getType());
                        Map<String, Object> queryResult = (Map<String, Object>) result.get("query");
                        List<Map<String, Object>> searchResults = (List<Map<String, Object>>) queryResult.get("search");

                        if (searchResults == null || searchResults.isEmpty()) {
                            return new FunctionResult(noResultsMessage);
                        }

                        StringBuilder sb = new StringBuilder();
                        for (Map<String, Object> item : searchResults) {
                            String title = (String) item.get("title");
                            String snippet = ((String) item.get("snippet")).replaceAll("<[^>]+>", "");
                            sb.append("Title: ").append(title).append("\n");
                            sb.append("Summary: ").append(snippet).append("\n\n");
                        }

                        return new FunctionResult(sb.toString());
                    } catch (Exception e) {
                        log.error("Wikipedia search error", e);
                        return new FunctionResult("Error searching Wikipedia: " + e.getMessage());
                    }
                }
        ));
    }

    @Override
    public List<Map<String, Object>> getPromptSections() {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", "Wikipedia Search");
        section.put("body", "You can search Wikipedia for factual information.");
        section.put("bullets", List.of(
                "Use search_wiki to look up topics on Wikipedia",
                "Summarize and cite Wikipedia as the source"
        ));
        return List.of(section);
    }
}
