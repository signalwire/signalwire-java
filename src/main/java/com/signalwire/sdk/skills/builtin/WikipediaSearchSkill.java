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

  @Override
  public String getName() {
    return "wikipedia_search";
  }

  @Override
  public String getDescription() {
    return "Search Wikipedia for information about a topic and get article summaries";
  }

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
  public List<ToolDefinition> registerTools() {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("type", "object");
    parameters.put(
        "properties", Map.of("query", Map.of("type", "string", "description", "The search query")));
    // No `required` — Python's wikipedia_search passes none (wikipedia_search/
    // skill.py:87); the handler guards an empty query. Matches the reference.

    return List.of(
        new ToolDefinition(
            "search_wiki",
            "Search Wikipedia for information about a topic and get article summaries",
            parameters,
            (args, raw) -> {
              String query = (String) args.get("query");
              if (query == null || query.isEmpty()) {
                return new FunctionResult("No query provided");
              }
              return new FunctionResult(searchWiki(query));
            }));
  }

  /**
   * Search Wikipedia for articles matching {@code query} and return the joined article extracts, or
   * an error / no-results message string.
   *
   * <p>Two-step against the MediaWiki API: ({@code action=query&list=search}) to find matching
   * titles, then ({@code action=query&prop=extracts&exintro&explaintext}) to fetch each article's
   * intro extract. Formats each as {@code **Title**\n\n<extract>}, joining multiple with a 50-'='
   * separator, and returns {@code no_results_message} (with {query} substituted) when nothing is
   * found.
   *
   * @param query the search term to look up
   * @return the Wikipedia article content or an error/no-results message
   */
  @SuppressWarnings("unchecked")
  public String searchWiki(String query) {
    try {
      // Allow tests / audit fixtures to redirect the upstream by setting
      // WIKIPEDIA_BASE_URL. The path /w/api.php is preserved so the audit can
      // assert the documented Wikipedia API path on the wire.
      String wikiBase = System.getenv("WIKIPEDIA_BASE_URL");
      if (wikiBase == null || wikiBase.isEmpty()) {
        wikiBase = "https://en.wikipedia.org";
      }
      if (wikiBase.endsWith("/")) {
        wikiBase = wikiBase.substring(0, wikiBase.length() - 1);
      }

      HttpClient client = HttpClient.newHttpClient();

      // Step 1: search for articles matching the query.
      String searchUrl =
          wikiBase
              + "/w/api.php?action=query&list=search&format=json"
              + "&srsearch="
              + URLEncoder.encode(query, StandardCharsets.UTF_8)
              + "&srlimit="
              + numResults;
      HttpResponse<String> searchResponse =
          client.send(
              HttpRequest.newBuilder().uri(URI.create(searchUrl)).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      Map<String, Object> searchData =
          new Gson()
              .fromJson(searchResponse.body(), new TypeToken<Map<String, Object>>() {}.getType());

      Map<String, Object> queryResult = (Map<String, Object>) searchData.get("query");
      List<Map<String, Object>> searchResults =
          queryResult == null ? null : (List<Map<String, Object>>) queryResult.get("search");
      if (searchResults == null || searchResults.isEmpty()) {
        return formatNoResults(query);
      }

      // Step 2: get article extracts for each result (up to numResults).
      List<String> articles = new ArrayList<>();
      int limit = Math.min(numResults, searchResults.size());
      for (int i = 0; i < limit; i++) {
        String title = (String) searchResults.get(i).get("title");

        String extractUrl =
            wikiBase
                + "/w/api.php?action=query&prop=extracts&exintro&explaintext&format=json"
                + "&titles="
                + URLEncoder.encode(title, StandardCharsets.UTF_8);
        HttpResponse<String> extractResponse =
            client.send(
                HttpRequest.newBuilder().uri(URI.create(extractUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Map<String, Object> extractData =
            new Gson()
                .fromJson(
                    extractResponse.body(), new TypeToken<Map<String, Object>>() {}.getType());

        Map<String, Object> extractQuery = (Map<String, Object>) extractData.get("query");
        Map<String, Object> pages =
            extractQuery == null ? null : (Map<String, Object>) extractQuery.get("pages");
        if (pages != null && !pages.isEmpty()) {
          Map<String, Object> page = (Map<String, Object>) pages.values().iterator().next();
          Object extractObj = page.get("extract");
          String extract = extractObj == null ? "" : extractObj.toString().strip();
          if (!extract.isEmpty()) {
            articles.add("**" + title + "**\n\n" + extract);
          } else {
            articles.add("**" + title + "**\n\nNo summary available for this article.");
          }
        }
      }

      if (articles.isEmpty()) {
        return formatNoResults(query);
      }

      if (articles.size() == 1) {
        return articles.get(0);
      }
      return String.join("\n\n" + "=".repeat(50) + "\n\n", articles);
    } catch (Exception e) {
      log.error("Wikipedia search error", e);
      return "Error searching Wikipedia: " + e.getMessage();
    }
  }

  /** Substitute {query} into the configured no-results message. */
  private String formatNoResults(String query) {
    return noResultsMessage.replace("{query}", query);
  }

  /** Returns an empty hint list. */
  @Override
  public List<String> getHints() {
    return Collections.emptyList();
  }

  /**
   * Parameter schema: base schema plus {@code num_results} (integer, default 1, min 1 / max 5) and
   * {@code no_results_message} (string).
   */
  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = SkillParams.base(supportsMultipleInstances(), getName());

    Map<String, Object> numResultsParam = new LinkedHashMap<>();
    numResultsParam.put("type", "integer");
    numResultsParam.put("description", "Maximum number of Wikipedia articles to return");
    numResultsParam.put("default", 1);
    numResultsParam.put("required", false);
    numResultsParam.put("minimum", 1);
    numResultsParam.put("maximum", 5);
    schema.put("num_results", numResultsParam);

    Map<String, Object> noResultsParam = new LinkedHashMap<>();
    noResultsParam.put("type", "string");
    noResultsParam.put("description", "Custom message when no Wikipedia articles are found");
    noResultsParam.put(
        "default",
        "I couldn't find any Wikipedia articles for '{query}'. Try rephrasing your"
            + " search or using different keywords.");
    noResultsParam.put("required", false);
    schema.put("no_results_message", noResultsParam);

    return schema;
  }

  @Override
  public List<Map<String, Object>> getPromptSections() {
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("title", "Wikipedia Search");
    section.put("body", "You can search Wikipedia for factual information.");
    section.put(
        "bullets",
        List.of(
            "Use search_wiki to look up topics on Wikipedia",
            "Summarize and cite Wikipedia as the source"));
    return List.of(section);
  }
}
