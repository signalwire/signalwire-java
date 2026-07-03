package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class SpiderSkill implements SkillBase {

  private static final Logger log = Logger.getLogger(SpiderSkill.class);

  private int timeout = 30;
  private int maxTextLength = 5000;
  private String userAgent = "SignalWire-Spider/1.0";
  // Python parity: get_instance_key defaults tool_name to SKILL_NAME (spider/skill.py).
  private String toolName = "spider";

  @Override
  public String getName() {
    return "spider";
  }

  @Override
  public String getDescription() {
    return "Fast web scraping and crawling capabilities";
  }

  @Override
  public boolean supportsMultipleInstances() {
    return true;
  }

  @Override
  public boolean setup(Map<String, Object> params) {
    if (params.containsKey("timeout")) this.timeout = ((Number) params.get("timeout")).intValue();
    if (params.containsKey("max_text_length"))
      this.maxTextLength = ((Number) params.get("max_text_length")).intValue();
    if (params.containsKey("user_agent")) this.userAgent = (String) params.get("user_agent");
    if (params.containsKey("tool_name")) this.toolName = (String) params.get("tool_name");
    return true;
  }

  @Override
  public List<ToolDefinition> registerTools() {
    Map<String, Object> urlParams = new LinkedHashMap<>();
    urlParams.put("type", "object");
    urlParams.put(
        "properties", Map.of("url", Map.of("type", "string", "description", "URL to scrape")));
    urlParams.put("required", List.of("url"));

    ToolDefinition scrape =
        new ToolDefinition(
            "scrape_url",
            "Scrape content from a web page URL",
            urlParams,
            (args, raw) -> {
              String url = (String) args.get("url");
              // Allow tests / audit fixtures to redirect every fetch
              // through a loopback host by setting SPIDER_BASE_URL. The
              // path component of the requested URL is preserved so the
              // audit can still assert it on the wire (the audit feeds
              // a synthetic upstream like "https://audit.example/page"
              // and expects to see "/page" hit on the fixture).
              String spiderBase = System.getenv("SPIDER_BASE_URL");
              if (spiderBase != null && !spiderBase.isEmpty()) {
                if (spiderBase.endsWith("/")) {
                  spiderBase = spiderBase.substring(0, spiderBase.length() - 1);
                }
                try {
                  URI parsed = URI.create(url);
                  String path = parsed.getRawPath();
                  String queryPart = parsed.getRawQuery();
                  String suffix = (path == null || path.isEmpty()) ? "/" : path;
                  if (queryPart != null && !queryPart.isEmpty()) {
                    suffix += "?" + queryPart;
                  }
                  url = spiderBase + suffix;
                } catch (IllegalArgumentException ignored) {
                  // Malformed input — leave url untouched.
                }
              }
              try {
                HttpClient client =
                    HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(timeout))
                        .build();
                HttpRequest request =
                    HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", userAgent)
                        .GET()
                        .build();
                HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                // Basic HTML stripping
                String text =
                    body.replaceAll("<script[^>]*>[\\s\\S]*?</script>", "")
                        .replaceAll("<style[^>]*>[\\s\\S]*?</style>", "")
                        .replaceAll("<[^>]+>", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
                if (text.length() > maxTextLength) {
                  text = text.substring(0, maxTextLength) + "...";
                }
                return new FunctionResult("Content from " + url + ":\n" + text);
              } catch (Exception e) {
                return new FunctionResult("Error scraping URL: " + e.getMessage());
              }
            });

    ToolDefinition crawl =
        new ToolDefinition(
            "crawl_site",
            "Crawl a website starting from a URL",
            Map.of(
                "type",
                "object",
                "properties",
                Map.of("start_url", Map.of("type", "string", "description", "Starting URL")),
                "required",
                List.of("start_url")),
            (args, raw) -> new FunctionResult("Crawl initiated from: " + args.get("start_url")));

    ToolDefinition extract =
        new ToolDefinition(
            "extract_structured_data",
            "Extract structured data from a web page",
            Map.of(
                "type",
                "object",
                "properties",
                Map.of("url", Map.of("type", "string", "description", "URL to extract from")),
                "required",
                List.of("url")),
            (args, raw) -> new FunctionResult("Extracting data from: " + args.get("url")));

    return List.of(scrape, crawl, extract);
  }

  @Override
  public List<String> getHints() {
    return List.of("scrape", "crawl", "extract", "web page", "website", "spider");
  }

  /**
   * Python parity: get_instance_key uses the tool name (default {@code SKILL_NAME}) — {@code
   * f"{SKILL_NAME}_{tool_name}"} (spider/skill.py).
   */
  @Override
  public String getInstanceKey() {
    return getName() + "_" + toolName;
  }

  /**
   * Python parity: cleanup closes the HTTP session, clears the page cache, and logs
   * (spider/skill.py). This port opens a fresh {@link HttpClient} per fetch and holds no cache, so
   * there is nothing to release; we log for symmetry.
   */
  @Override
  public void cleanup() {
    log.info("Spider skill cleaned up");
  }

  /** Python parity: get_parameter_schema (spider/skill.py) — base params plus custom fields. */
  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = SkillParams.base(true, getName());

    Map<String, Object> delay = numberEntry("number", "Delay between requests in seconds", 0.1);
    delay.put("minimum", 0.0);
    schema.put("delay", delay);

    Map<String, Object> concurrent =
        numberEntry("integer", "Number of concurrent requests allowed", 5);
    concurrent.put("minimum", 1);
    concurrent.put("maximum", 20);
    schema.put("concurrent_requests", concurrent);

    Map<String, Object> timeoutParam = numberEntry("integer", "Request timeout in seconds", 5);
    timeoutParam.put("minimum", 1);
    timeoutParam.put("maximum", 60);
    schema.put("timeout", timeoutParam);

    Map<String, Object> maxPages = numberEntry("integer", "Maximum number of pages to scrape", 1);
    maxPages.put("minimum", 1);
    maxPages.put("maximum", 100);
    schema.put("max_pages", maxPages);

    Map<String, Object> maxDepth =
        numberEntry("integer", "Maximum crawl depth (0 = single page only)", 0);
    maxDepth.put("minimum", 0);
    maxDepth.put("maximum", 5);
    schema.put("max_depth", maxDepth);

    Map<String, Object> extractType =
        numberEntry("string", "Content extraction method", "fast_text");
    extractType.put("enum", List.of("fast_text", "clean_text", "full_text", "html", "custom"));
    schema.put("extract_type", extractType);

    Map<String, Object> maxTextLen = numberEntry("integer", "Maximum text length to return", 10000);
    maxTextLen.put("minimum", 100);
    maxTextLen.put("maximum", 100000);
    schema.put("max_text_length", maxTextLen);

    schema.put("clean_text", numberEntry("boolean", "Whether to clean extracted text", true));

    Map<String, Object> selectors = new LinkedHashMap<>();
    selectors.put("type", "object");
    selectors.put("description", "Custom CSS/XPath selectors for extraction");
    selectors.put("default", new LinkedHashMap<>());
    selectors.put("required", false);
    selectors.put("additionalProperties", Map.of("type", "string"));
    schema.put("selectors", selectors);

    Map<String, Object> followPatterns = new LinkedHashMap<>();
    followPatterns.put("type", "array");
    followPatterns.put("description", "URL patterns to follow when crawling");
    followPatterns.put("default", new ArrayList<>());
    followPatterns.put("required", false);
    followPatterns.put("items", Map.of("type", "string"));
    schema.put("follow_patterns", followPatterns);

    schema.put(
        "user_agent",
        numberEntry(
            "string",
            "User agent string for requests",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"));

    Map<String, Object> headers = new LinkedHashMap<>();
    headers.put("type", "object");
    headers.put("description", "Additional HTTP headers");
    headers.put("default", new LinkedHashMap<>());
    headers.put("required", false);
    headers.put("additionalProperties", Map.of("type", "string"));
    schema.put("headers", headers);

    schema.put("follow_robots_txt", numberEntry("boolean", "Whether to respect robots.txt", true));
    schema.put("cache_enabled", numberEntry("boolean", "Whether to cache scraped pages", true));

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
