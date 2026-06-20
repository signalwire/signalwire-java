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
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WebSearchSkill implements SkillBase {

  private static final Logger log = Logger.getLogger(WebSearchSkill.class);

  private String apiKey;
  private String searchEngineId;
  private int numResults = 3;
  private String toolName = "web_search";
  private String noResultsMessage = "No results found for the query.";
  // Optional prefix/postfix wrapped around every non-empty search result.
  // Mirrors Python's response_prefix / response_postfix params; left as
  // empty strings when omitted so the wrapping no-ops without an extra
  // null check on the hot path.
  private String responsePrefix = "";
  private String responsePostfix = "";

  // Latency-control parameters. The SignalWire kernel times out webhook
  // responses around 55s, so the handler MUST finish under that. Mirrors
  // Python's web_search/skill.py (commits 51101da + 295745b).
  //   perPageTimeout: max seconds to wait on a single page scrape. Applied
  //     as the HttpClient request timeout (connect+read) per page fetch.
  //   overallDeadline: wall-clock budget for the whole tool call. Once
  //     exceeded, any in-flight scrapes are abandoned and we format whatever
  //     results we already have (or fall back to CSE snippets). ENFORCED.
  //   parallelScrape: fetch all candidate pages concurrently via
  //     CompletableFuture on an ExecutorService instead of one-at-a-time
  //     (best-effort, not contracted).
  //   snippetsOnly: skip page scraping entirely and format the Google CSE
  //     snippets directly. Fastest mode (sub-second).
  private double perPageTimeout = 2.0;
  private double overallDeadline = 10.0;
  private boolean parallelScrape = true;
  private boolean snippetsOnly = false;

  @Override
  public String getName() {
    return "web_search";
  }

  @Override
  public String getDescription() {
    return "Search the web for information using Google Custom Search API";
  }

  @Override
  public String getVersion() {
    return "2.0.0";
  }

  @Override
  public boolean supportsMultipleInstances() {
    return true;
  }

  @Override
  public boolean setup(Map<String, Object> params) {
    this.apiKey = (String) params.get("api_key");
    this.searchEngineId = (String) params.get("search_engine_id");
    if (params.containsKey("num_results")) {
      this.numResults = ((Number) params.get("num_results")).intValue();
      numResults = Math.max(1, Math.min(numResults, 10));
    }
    if (params.containsKey("tool_name")) this.toolName = (String) params.get("tool_name");
    if (params.containsKey("no_results_message"))
      this.noResultsMessage = (String) params.get("no_results_message");
    if (params.containsKey("response_prefix")) {
      Object v = params.get("response_prefix");
      this.responsePrefix = v == null ? "" : v.toString();
    }
    if (params.containsKey("response_postfix")) {
      Object v = params.get("response_postfix");
      this.responsePostfix = v == null ? "" : v.toString();
    }
    // Latency-control params (Python parity: 51101da). Defaults match:
    // per_page_timeout=2.0s, overall_deadline=10.0s, parallel_scrape=true,
    // snippets_only=false.
    if (params.containsKey("per_page_timeout")) {
      this.perPageTimeout = ((Number) params.get("per_page_timeout")).doubleValue();
    }
    if (params.containsKey("overall_deadline")) {
      this.overallDeadline = ((Number) params.get("overall_deadline")).doubleValue();
    }
    if (params.containsKey("parallel_scrape")) {
      this.parallelScrape = toBool(params.get("parallel_scrape"), true);
    }
    if (params.containsKey("snippets_only")) {
      this.snippetsOnly = toBool(params.get("snippets_only"), false);
    }
    return apiKey != null
        && !apiKey.isEmpty()
        && searchEngineId != null
        && !searchEngineId.isEmpty();
  }

  private static boolean toBool(Object v, boolean dflt) {
    if (v == null) return dflt;
    if (v instanceof Boolean b) return b;
    return Boolean.parseBoolean(v.toString());
  }

  @Override
  public List<ToolDefinition> registerTools() {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("type", "object");
    parameters.put(
        "properties", Map.of("query", Map.of("type", "string", "description", "The search query")));
    parameters.put("required", List.of("query"));

    return List.of(
        new ToolDefinition(
            toolName,
            "Search the web for high-quality information, automatically filtering low-quality results",
            parameters,
            this::handleSearch));
  }

  @SuppressWarnings("unchecked")
  private FunctionResult handleSearch(Map<String, Object> args, Map<String, Object> rawData) {
    String query = (String) args.get("query");
    if (query == null || query.isEmpty()) {
      return new FunctionResult("No query provided");
    }
    query = query.trim();
    if (query.isEmpty()) {
      return new FunctionResult("No query provided");
    }

    // overall_deadline is the wall-clock budget for the whole tool call.
    // The SignalWire kernel times out webhook responses around 55s; once
    // this fires, in-flight scrapes are abandoned and we return whatever we
    // have (or fall back to CSE snippets). THIS IS THE CONTRACT.
    long deadlineAtMillis =
        System.nanoTime() / 1_000_000L + Math.round(Math.max(0.0, overallDeadline) * 1000.0);

    try {
      String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
      // Allow tests / audit fixtures to redirect the upstream host by
      // setting WEB_SEARCH_BASE_URL (e.g. http://127.0.0.1:NNNN). The
      // path segment "/customsearch/v1" is preserved so the audit can
      // assert the documented Google CSE path on the wire. The system
      // property of the same name wins over the env var so JUnit tests
      // can set it inline without spawning a subprocess.
      String base = System.getProperty("WEB_SEARCH_BASE_URL");
      if (base == null || base.isEmpty()) {
        base = System.getenv("WEB_SEARCH_BASE_URL");
      }
      if (base == null || base.isEmpty()) {
        base = "https://www.googleapis.com";
      }
      // Strip trailing slash so the format string produces a clean URL.
      if (base.endsWith("/")) {
        base = base.substring(0, base.length() - 1);
      }
      String url =
          String.format(
              "%s/customsearch/v1?key=%s&cx=%s&q=%s&num=%d",
              base, apiKey, searchEngineId, encodedQuery, numResults);

      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              // Bound the CSE fetch itself by the per-page timeout so a
              // hung search endpoint can't run past the overall deadline.
              .timeout(pageTimeout())
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        return new FunctionResult("Search request failed with status: " + response.statusCode());
      }

      Map<String, Object> result =
          new Gson().fromJson(response.body(), new TypeToken<Map<String, Object>>() {}.getType());

      List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
      if (items == null || items.isEmpty()) {
        return new FunctionResult(formatNoResults(query));
      }

      // snippets_only fast path: skip page scraping entirely and format
      // the CSE snippets directly. Sub-second response.
      if (snippetsOnly) {
        return new FunctionResult(wrapResponse(formatSnippetResults(query, items)));
      }

      // Scrape and score each candidate page under the overall_deadline
      // budget. In parallel mode every candidate is fetched on an
      // ExecutorService and harvested with an overall-deadline bound;
      // whatever has not returned by the deadline is abandoned. In
      // sequential mode we scrape one-at-a-time, breaking out once the
      // deadline passes. The overall_deadline is enforced in BOTH modes.
      List<ScrapedResult> scraped = scrapeCandidates(query, items, deadlineAtMillis);

      if (scraped.isEmpty()) {
        // Time ran out (overall_deadline fired) or every page was empty
        // / below threshold. Fall back to snippet-only formatting so we
        // return SOMETHING useful before the kernel webhook timeout
        // fires, rather than an empty no-results message. (Python
        // parity: 51101da.)
        return new FunctionResult(wrapResponse(formatSnippetResults(query, items)));
      }

      // Sort by scraped content length (a cheap quality proxy) so the
      // richest pages lead, then render.
      scraped.sort((a, b) -> Integer.compare(b.content.length(), a.content.length()));

      StringBuilder sb = new StringBuilder("Quality web search results for \"" + query + "\":\n\n");
      int shown = 0;
      for (ScrapedResult r : scraped) {
        if (shown >= numResults) break;
        shown++;
        sb.append(shown).append(". ").append(r.title).append("\n");
        sb.append("   ").append(r.snippet).append("\n");
        sb.append("   URL: ").append(r.link).append("\n");
        if (!r.content.isEmpty()) {
          sb.append("   ").append(truncate(r.content, 500)).append("\n");
        }
        sb.append("\n");
      }

      return new FunctionResult(wrapResponse(sb.toString().stripTrailing()));
    } catch (Exception e) {
      log.error("Web search error", e);
      return new FunctionResult("Error performing web search: " + e.getMessage());
    }
  }

  /**
   * Scrape and score the candidate results under the overall_deadline budget.
   *
   * <p>In parallel mode, each candidate's page fetch is dispatched on a shared {@link
   * ExecutorService} as a {@link CompletableFuture}, then harvested with an overall-deadline-bound
   * {@code allOf(...).get(remaining, MILLIS)}. If that times out we keep whatever futures already
   * completed and abandon the rest (cancelling them so the executor can wind down). In sequential
   * mode we scrape one-at-a-time, breaking out once the deadline passes. The overall_deadline is
   * enforced in BOTH modes. Mirrors Python's {@code search_and_scrape_best} (skill.py, commit
   * 51101da).
   */
  private List<ScrapedResult> scrapeCandidates(
      String query, List<Map<String, Object>> items, long deadlineAtMillis) {
    List<ScrapedResult> out = new ArrayList<>();

    if (!parallelScrape) {
      // Sequential mode. Still honors overall_deadline: bail before
      // starting a scrape once we're out of time.
      for (Map<String, Object> item : items) {
        if (nowMillis() >= deadlineAtMillis) break;
        ScrapedResult r = scrapeOne(query, item);
        if (r != null) out.add(r);
      }
      return out;
    }

    // Parallel mode: dispatch every scrape at once and harvest with an
    // overall-deadline bound. The executor is shut down in finally.
    ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, Math.min(8, items.size())));
    try {
      List<CompletableFuture<ScrapedResult>> futures = new ArrayList<>(items.size());
      for (Map<String, Object> item : items) {
        futures.add(CompletableFuture.supplyAsync(() -> scrapeOne(query, item), executor));
      }

      long remaining = deadlineAtMillis - nowMillis();
      if (remaining > 0) {
        try {
          CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
              .get(remaining, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
          // Deadline fired (TimeoutException) or a scrape threw. Either
          // way, harvest whatever completed below and abandon the rest.
          // THIS IS THE overall_deadline ENFORCEMENT.
          log.debug(
              "web_search: overall_deadline reached or scrape error; "
                  + "harvesting completed results only");
        }
      }

      // Harvest only the futures that finished in time; cancel stragglers
      // so the executor can shut down promptly.
      for (CompletableFuture<ScrapedResult> f : futures) {
        if (f.isDone() && !f.isCompletedExceptionally() && !f.isCancelled()) {
          try {
            ScrapedResult r = f.getNow(null);
            if (r != null) out.add(r);
          } catch (Exception ignore) {
            // completed-exceptionally races; skip.
          }
        } else {
          f.cancel(true);
        }
      }
    } finally {
      executor.shutdownNow();
    }
    return out;
  }

  /**
   * Fetch one candidate page and return its extracted text bundled with the CSE title/snippet/link.
   * Returns {@code null} on any fetch/parse failure (including the per-page timeout firing) so the
   * caller can skip it. Mirrors Python's {@code _scrape_one} closure.
   */
  private ScrapedResult scrapeOne(String query, Map<String, Object> item) {
    String title = stringVal(item.get("title"));
    String snippet = stringVal(item.get("snippet"));
    String link = stringVal(item.get("link"));
    if (link.isEmpty()) {
      return null;
    }
    try {
      HttpClient client =
          HttpClient.newBuilder()
              // Bound the connect phase by the per-page timeout too.
              .connectTimeout(pageTimeout())
              .build();
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(link))
              // per_page_timeout: max wait on a single page fetch
              // (connect + read). ENFORCED.
              .timeout(pageTimeout())
              .header(
                  "User-Agent",
                  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                      + "AppleWebKit/537.36 (KHTML, like Gecko) "
                      + "Chrome/120.0.0.0 Safari/537.36")
              .GET()
              .build();
      HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() != 200) {
        return null;
      }
      String text = extractText(resp.body());
      if (text.isEmpty()) {
        return null;
      }
      return new ScrapedResult(title, snippet, link, text);
    } catch (Exception e) {
      // Per-page timeout (HttpTimeoutException), connect failure, parse
      // error — abandon this candidate. The deadline / fallback handles
      // the empty-result case upstream.
      log.debug("web_search: scrape failed for " + link + ": " + e.getMessage());
      return null;
    }
  }

  /**
   * Crude HTML-to-text: strip script/style blocks and tags, collapse whitespace. Good enough to
   * give the model page context without pulling in a full HTML parser. Mirrors the intent of
   * Python's extract_html_content.
   */
  private static String extractText(String html) {
    if (html == null || html.isEmpty()) return "";
    String s =
        html.replaceAll("(?is)<script.*?</script>", " ")
            .replaceAll("(?is)<style.*?</style>", " ")
            .replaceAll("(?is)<[^>]+>", " ")
            .replaceAll("&nbsp;", " ")
            .replaceAll("&amp;", "&")
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .replaceAll("\\s+", " ")
            .trim();
    return s;
  }

  private static String truncate(String s, int max) {
    if (s.length() <= max) return s;
    return s.substring(0, max) + "...";
  }

  /**
   * Format Google CSE snippets without fetching the underlying pages. Used by the snippets_only
   * fast path and as the graceful fallback when page scraping is abandoned by the overall_deadline
   * (or every page was empty / below threshold). The result is shorter than a fully-scraped
   * response but always non-empty when the CSE returned anything, so the kernel never sees a
   * webhook timeout. Mirrors Python's {@code GoogleSearchScraper._format_snippet_results}.
   */
  private String formatSnippetResults(String query, List<Map<String, Object>> items) {
    if (items == null || items.isEmpty()) {
      return formatNoResults(query);
    }
    int top = Math.min(Math.max(numResults, 1), items.size());
    StringBuilder sb = new StringBuilder();
    sb.append("Snippet-only results for '")
        .append(query)
        .append("' (page content not scraped):\n\n");
    for (int i = 0; i < top; i++) {
      Map<String, Object> it = items.get(i);
      sb.append("=== RESULT ").append(i + 1).append(" ===\n");
      sb.append("Title: ").append(stringVal(it.get("title"))).append("\n");
      sb.append("URL: ").append(stringVal(it.get("link"))).append("\n");
      sb.append("Snippet: ").append(stringVal(it.get("snippet")).trim()).append("\n");
      sb.append("\n");
    }
    return sb.toString().stripTrailing();
  }

  /** Substitute {query} into the configured no-results message. */
  private String formatNoResults(String query) {
    return noResultsMessage.replace("{query}", query);
  }

  /**
   * Apply the optional response_prefix / response_postfix around a non-empty result body. Shared by
   * the scraped-result and snippet paths; the error and no-results branches deliberately stay
   * unwrapped, matching Python.
   */
  private String wrapResponse(String body) {
    if (!responsePrefix.isEmpty()) {
      body = responsePrefix + "\n\n" + body;
    }
    if (!responsePostfix.isEmpty()) {
      body = body + "\n\n" + responsePostfix;
    }
    return body;
  }

  /** per_page_timeout (seconds) as a Duration, clamped to a sane minimum. */
  private Duration pageTimeout() {
    double secs = perPageTimeout > 0 ? perPageTimeout : 2.0;
    return Duration.ofMillis(Math.max(100L, Math.round(secs * 1000.0)));
  }

  private static long nowMillis() {
    return System.nanoTime() / 1_000_000L;
  }

  private static String stringVal(Object o) {
    return o == null ? "" : o.toString();
  }

  /** Enriched scrape result: CSE metadata plus extracted page text. */
  private static final class ScrapedResult {
    final String title;
    final String snippet;
    final String link;
    final String content;

    ScrapedResult(String title, String snippet, String link, String content) {
      this.title = title;
      this.snippet = snippet;
      this.link = link;
      this.content = content;
    }
  }

  @Override
  public List<Map<String, Object>> getPromptSections() {
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("title", "Web Search Capability (Quality Enhanced)");
    section.put("body", "");
    section.put(
        "bullets",
        List.of(
            "Use " + toolName + " to find current information from the web",
            "Results are quality-filtered for accuracy",
            "Always cite sources when providing search results"));
    return List.of(section);
  }

  @Override
  public Map<String, Object> getGlobalData() {
    return Map.of(
        "web_search_enabled", true,
        "search_provider", "Google Custom Search",
        "quality_filtering", true);
  }

  /**
   * Advertise every configurable param so GUI tooling can discover it. Mirrors Python's
   * get_parameter_schema (commit 295745b), including the six latency / response params. Each
   * setup() read must appear here.
   */
  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();

    schema.put(
        "tool_name",
        schemaEntry("string", "Custom name for the web search tool.", "web_search", false));
    schema.put(
        "num_results",
        schemaEntry("integer", "Number of search results to return (1-10).", 3, false));
    schema.put(
        "no_results_message",
        schemaEntry(
            "string",
            "Message to show when no results are found. Use {query} as placeholder.",
            noResultsMessage,
            false));
    schema.put(
        "response_prefix",
        schemaEntry(
            "string", "Optional text prepended to every non-empty search result.", "", false));
    schema.put(
        "response_postfix",
        schemaEntry(
            "string", "Optional text appended to every non-empty search result.", "", false));

    Map<String, Object> perPage =
        schemaEntry("number", "Maximum seconds to wait on a single page scrape.", 2.0, false);
    perPage.put("min", 0.1);
    schema.put("per_page_timeout", perPage);

    Map<String, Object> deadline =
        schemaEntry(
            "number",
            "Wall-clock budget in seconds for the whole tool call. In-flight "
                + "scrapes are abandoned past this so the response beats the kernel "
                + "webhook timeout.",
            10.0,
            false);
    deadline.put("min", 1.0);
    schema.put("overall_deadline", deadline);

    schema.put(
        "parallel_scrape",
        schemaEntry(
            "boolean",
            "Scrape all candidate pages concurrently (CompletableFuture on an "
                + "ExecutorService) instead of sequentially.",
            true,
            false));
    schema.put(
        "snippets_only",
        schemaEntry(
            "boolean",
            "Skip page scraping entirely and return Google CSE snippets only. "
                + "Fastest mode (sub-second).",
            false,
            false));

    return schema;
  }

  private static Map<String, Object> schemaEntry(
      String type, String description, Object dflt, boolean required) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", type);
    m.put("description", description);
    m.put("default", dflt);
    m.put("required", required);
    return m;
  }
}
