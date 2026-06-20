package com.signalwire.sdk.skills;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.skills.builtin.WebSearchSkill;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WebSearchSkillTest {

  @Test
  void testSkillProperties() {
    WebSearchSkill skill = new WebSearchSkill();
    assertEquals("web_search", skill.getName());
    assertNotNull(skill.getDescription());
    assertTrue(skill.supportsMultipleInstances());
  }

  @Test
  void testSetupFailsWithoutCredentials() {
    assertFalse(new WebSearchSkill().setup(Map.of()));
  }

  @Test
  void testSetupSucceeds() {
    assertTrue(
        new WebSearchSkill()
            .setup(
                Map.of(
                    "api_key", "test-key",
                    "search_engine_id", "test-cx")));
  }

  @Test
  void testRegistersSearchTool() {
    WebSearchSkill skill = new WebSearchSkill();
    skill.setup(Map.of("api_key", "key", "search_engine_id", "cx"));
    List<ToolDefinition> tools = skill.registerTools();
    assertEquals(1, tools.size());
    assertEquals("web_search", tools.get(0).getName());
  }

  @Test
  void testCustomToolName() {
    WebSearchSkill skill = new WebSearchSkill();
    skill.setup(Map.of("api_key", "key", "search_engine_id", "cx", "tool_name", "search_web"));
    List<ToolDefinition> tools = skill.registerTools();
    assertEquals("search_web", tools.get(0).getName());
  }

  @Test
  void testPromptSections() {
    WebSearchSkill skill = new WebSearchSkill();
    skill.setup(Map.of("api_key", "key", "search_engine_id", "cx"));
    var sections = skill.getPromptSections();
    assertFalse(sections.isEmpty());
  }

  @Test
  void testGlobalData() {
    WebSearchSkill skill = new WebSearchSkill();
    skill.setup(Map.of("api_key", "key", "search_engine_id", "cx"));
    var gd = skill.getGlobalData();
    assertTrue((Boolean) gd.get("web_search_enabled"));
  }

  @Test
  void testEmptyQueryReturnsError() {
    WebSearchSkill skill = new WebSearchSkill();
    skill.setup(Map.of("api_key", "key", "search_engine_id", "cx"));
    var tools = skill.registerTools();
    var result = tools.get(0).getHandler().handle(Map.of("query", ""), Map.of());
    assertTrue(result.getResponse().contains("No query"));
  }

  // ======== response_prefix / response_postfix (Python 8aad242) ========
  // Mirrors the Python addition: optional prefix/postfix wrapped around
  // successful (non-empty) search results. Error and no-results paths
  // are unchanged.

  @Test
  void testSetupCapturesResponsePrefixAndPostfix() throws Exception {
    WebSearchSkill skill = new WebSearchSkill();
    skill.setup(
        Map.of(
            "api_key", "k",
            "search_engine_id", "cx",
            "response_prefix", "PREFIX",
            "response_postfix", "POSTFIX"));
    assertEquals("PREFIX", readPrivate(skill, "responsePrefix"));
    assertEquals("POSTFIX", readPrivate(skill, "responsePostfix"));
  }

  @Test
  void testSetupDefaultsResponsePrefixPostfixToEmpty() throws Exception {
    WebSearchSkill skill = new WebSearchSkill();
    skill.setup(Map.of("api_key", "k", "search_engine_id", "cx"));
    assertEquals("", readPrivate(skill, "responsePrefix"));
    assertEquals("", readPrivate(skill, "responsePostfix"));
  }

  @Test
  void testEmptyQueryUnaffectedByPrefixPostfix() {
    WebSearchSkill skill = new WebSearchSkill();
    skill.setup(
        Map.of(
            "api_key", "k",
            "search_engine_id", "cx",
            "response_prefix", "PRE",
            "response_postfix", "POST"));
    var tools = skill.registerTools();
    var result = tools.get(0).getHandler().handle(Map.of("query", ""), Map.of());
    // Error / no-query path unchanged: it does not get wrapped.
    assertFalse(result.getResponse().contains("PRE"));
    assertFalse(result.getResponse().contains("POST"));
  }

  @Test
  void testSuccessfulResponseWrappedWithPrefixAndPostfix() throws Exception {
    // Scrape succeeds (content page served fast by the stub), so the
    // wrapped body is a fully-scraped result. snippets_only=false (default).
    try (StubServer stub =
        StubServer.startWithScrapableItem(
            "Title One",
            "Snippet here",
            "page-one",
            "<html><body>Real page body content about kittens here.</body></html>")) {
      WebSearchSkill skill = new WebSearchSkill();
      skill.setup(
          Map.of(
              "api_key", "k",
              "search_engine_id", "cx",
              "response_prefix", "[FROM PUBLIC WEB]",
              "response_postfix", "[END]"));
      String response = invokeHandler(skill, "kittens");
      assertTrue(
          response.startsWith("[FROM PUBLIC WEB]\n\n"),
          "prefix must lead the response: " + response);
      assertTrue(response.endsWith("\n\n[END]"), "postfix must trail the response: " + response);
      assertTrue(response.contains("Title One"), "body still rendered: " + response);
    }
  }

  @Test
  void testSuccessfulResponseWithoutPrefixPostfixIsUnwrapped() throws Exception {
    try (StubServer stub =
        StubServer.startWithScrapableItem(
            "T", "S", "page", "<html><body>Body text for the page T here.</body></html>")) {
      WebSearchSkill skill = new WebSearchSkill();
      skill.setup(Map.of("api_key", "k", "search_engine_id", "cx"));
      String response = invokeHandler(skill, "q");
      assertTrue(
          response.startsWith("Quality web search results for"),
          "scraped-result header expected: " + response);
      assertTrue(response.contains("T"));
    }
  }

  @Test
  void testNoResultsPathIsNotWrappedByPrefixPostfix() throws Exception {
    try (StubServer stub = StubServer.startEmptyItems()) {
      WebSearchSkill skill = new WebSearchSkill();
      skill.setup(
          Map.of(
              "api_key", "k",
              "search_engine_id", "cx",
              "response_prefix", "PRE",
              "response_postfix", "POST",
              "no_results_message", "Nothing found."));
      String response = invokeHandler(skill, "q");
      // No-results path returns the plain no_results_message untouched.
      assertEquals("Nothing found.", response);
    }
  }

  // ======== Latency-control params (Python 51101da + 295745b) ========
  // overall_deadline + per_page_timeout are the CONTRACT: a slow site must
  // not blow past the kernel webhook timeout (~55s). snippets_only fast path
  // and snippet fallback keep the response non-empty. parallel_scrape via
  // CompletableFuture is best-effort.

  @Test
  void testSetupDefaultsForLatencyParams() throws Exception {
    WebSearchSkill skill = new WebSearchSkill();
    skill.setup(Map.of("api_key", "k", "search_engine_id", "cx"));
    assertEquals(2.0, (double) readPrivate(skill, "perPageTimeout"), 1e-9);
    assertEquals(10.0, (double) readPrivate(skill, "overallDeadline"), 1e-9);
    assertEquals(Boolean.TRUE, readPrivate(skill, "parallelScrape"));
    assertEquals(Boolean.FALSE, readPrivate(skill, "snippetsOnly"));
  }

  @Test
  void testSetupCapturesLatencyParamOverrides() throws Exception {
    WebSearchSkill skill = new WebSearchSkill();
    skill.setup(
        Map.of(
            "api_key",
            "k",
            "search_engine_id",
            "cx",
            "per_page_timeout",
            3.5,
            "overall_deadline",
            12.0,
            "parallel_scrape",
            false,
            "snippets_only",
            true));
    assertEquals(3.5, (double) readPrivate(skill, "perPageTimeout"), 1e-9);
    assertEquals(12.0, (double) readPrivate(skill, "overallDeadline"), 1e-9);
    assertEquals(Boolean.FALSE, readPrivate(skill, "parallelScrape"));
    assertEquals(Boolean.TRUE, readPrivate(skill, "snippetsOnly"));
  }

  @Test
  void testSnippetsOnlySkipsPageFetch() throws Exception {
    // snippets_only short-circuits before any content fetch. The stub
    // counts content-page hits; assert zero, and assert the response is
    // the snippet-only block (not a scraped result).
    try (StubServer stub =
        StubServer.startWithScrapableItem(
            "SnipTitle",
            "snippet body text",
            "should-not-be-fetched",
            "<html><body>SHOULD NOT BE SCRAPED</body></html>")) {
      WebSearchSkill skill = new WebSearchSkill();
      skill.setup(
          Map.of(
              "api_key", "k",
              "search_engine_id", "cx",
              "snippets_only", true));
      String response = invokeHandler(skill, "anything");
      assertEquals(
          0,
          stub.contentHits.get(),
          "snippets_only must not fetch any page; hits=" + stub.contentHits.get());
      assertTrue(
          response.contains("Snippet-only results"),
          "snippets_only must format snippet block: " + response);
      assertTrue(
          response.contains("snippet body text"), "snippet text must be present: " + response);
      assertFalse(
          response.contains("SHOULD NOT BE SCRAPED"),
          "page body must never appear in snippets_only mode: " + response);
    }
  }

  @Test
  void testDeadlineTruncatesToSnippetFallback() throws Exception {
    // CONTRACT TEST. The content page sleeps far longer than the overall
    // deadline. The handler MUST abandon the in-flight scrape, fall back to
    // the snippet block, return within ~deadline + slack, and NOT emit the
    // empty no-results message.
    long sleepMs = 8_000; // page is much slower than the deadline
    double deadlineSecs = 1.0;
    try (StubServer stub =
        StubServer.startWithSlowItem("SlowTitle", "slow snippet body", "slow-page", sleepMs)) {
      WebSearchSkill skill = new WebSearchSkill();
      skill.setup(
          Map.of(
              "api_key", "k",
              "search_engine_id", "cx",
              "overall_deadline", deadlineSecs,
              // Per-page timeout LONGER than the deadline, so the deadline
              // (not the per-page timeout) is what truncates the scrape.
              "per_page_timeout", 30.0,
              "no_results_message", "EMPTY-NO-RESULTS-SHOULD-NOT-APPEAR"));

      long start = System.nanoTime();
      String response = invokeHandler(skill, "slow query");
      long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

      // Returned roughly within the deadline, well before the page sleep
      // would have finished. Generous slack for executor teardown + CI.
      assertTrue(
          elapsedMs < sleepMs - 2_000,
          "must return before the slow page completes; elapsed=" + elapsedMs + "ms");
      assertTrue(
          elapsedMs >= (long) (deadlineSecs * 1000) - 200,
          "should have waited about the deadline; elapsed=" + elapsedMs + "ms");

      // Snippet fallback, NOT the empty no-results message.
      assertTrue(
          response.contains("Snippet-only results"),
          "deadline must fall back to snippet block: " + response);
      assertTrue(
          response.contains("slow snippet body"),
          "snippet text must be present in fallback: " + response);
      assertFalse(
          response.contains("EMPTY-NO-RESULTS-SHOULD-NOT-APPEAR"),
          "deadline path must NOT return the empty no-results message: " + response);
    }
  }

  @Test
  void testSequentialModeBoundsLatencyAndFallsBack() throws Exception {
    // parallel_scrape=false. Sequential mode bounds a single in-flight
    // scrape by per_page_timeout (an already-started scrape is what the
    // per-page timeout exists to truncate), then — having no scraped
    // content — falls back to the snippet block rather than the empty
    // no-results message. Deterministic: the page sleeps far longer than
    // the per-page timeout.
    long sleepMs = 8_000;
    double perPageSecs = 0.6;
    try (StubServer stub =
        StubServer.startWithSlowItem("SeqTitle", "seq snippet body", "seq-slow", sleepMs)) {
      WebSearchSkill skill = new WebSearchSkill();
      skill.setup(
          Map.of(
              "api_key",
              "k",
              "search_engine_id",
              "cx",
              "overall_deadline",
              20.0,
              "per_page_timeout",
              perPageSecs,
              "parallel_scrape",
              false));
      long start = System.nanoTime();
      String response = invokeHandler(skill, "seq query");
      long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
      assertTrue(
          elapsedMs < sleepMs - 1_000,
          "sequential scrape must be bounded by per_page_timeout; elapsed=" + elapsedMs);
      assertTrue(
          response.contains("Snippet-only results"),
          "sequential path falls back to snippets: " + response);
      assertTrue(response.contains("seq snippet body"));
    }
  }

  @Test
  void testPerPageTimeoutAbandonsSlowPage() throws Exception {
    // per_page_timeout fires before the page responds; with no scraped
    // content we fall back to the snippet block. Returns near the per-page
    // timeout, well before the page sleep finishes.
    long sleepMs = 6_000;
    double perPageSecs = 0.5;
    try (StubServer stub =
        StubServer.startWithSlowItem("PptTitle", "ppt snippet body", "ppt-slow", sleepMs)) {
      WebSearchSkill skill = new WebSearchSkill();
      skill.setup(
          Map.of(
              "api_key",
              "k",
              "search_engine_id",
              "cx",
              // Generous overall deadline so the per-page timeout is the
              // thing that abandons the scrape.
              "overall_deadline",
              20.0,
              "per_page_timeout",
              perPageSecs));
      long start = System.nanoTime();
      String response = invokeHandler(skill, "ppt query");
      long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
      assertTrue(
          elapsedMs < sleepMs - 1_000,
          "per_page_timeout must abandon the slow page; elapsed=" + elapsedMs);
      assertTrue(
          response.contains("Snippet-only results"),
          "per_page_timeout path falls back to snippets: " + response);
      assertTrue(response.contains("ppt snippet body"));
    }
  }

  @Test
  void testSnippetFallbackWhenScrapeYieldsEmpty() throws Exception {
    // Content page returns 404 → scrape yields nothing → snippet fallback
    // (not the no-results message). Fast and deterministic, no sleeps.
    try (StubServer stub =
        StubServer.startWith404Content("FbTitle", "fallback snippet text", "missing-page")) {
      WebSearchSkill skill = new WebSearchSkill();
      skill.setup(
          Map.of(
              "api_key", "k",
              "search_engine_id", "cx",
              "no_results_message", "EMPTY-SHOULD-NOT-APPEAR"));
      String response = invokeHandler(skill, "fb query");
      assertTrue(
          response.contains("Snippet-only results"),
          "empty-scrape path falls back to snippets: " + response);
      assertTrue(response.contains("fallback snippet text"));
      assertFalse(
          response.contains("EMPTY-SHOULD-NOT-APPEAR"),
          "must not emit the empty no-results message: " + response);
    }
  }

  // ======== getParameterSchema (Python 295745b) ========

  @Test
  void testParameterSchemaAdvertisesAllSixParams() {
    WebSearchSkill skill = new WebSearchSkill();
    Map<String, Object> schema = skill.getParameterSchema();
    for (String key :
        List.of(
            "response_prefix",
            "response_postfix",
            "per_page_timeout",
            "overall_deadline",
            "parallel_scrape",
            "snippets_only")) {
      assertTrue(schema.containsKey(key), "schema must advertise " + key);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void testParameterSchemaDefaultsAndTypes() {
    WebSearchSkill skill = new WebSearchSkill();
    Map<String, Object> schema = skill.getParameterSchema();

    Map<String, Object> ppt = (Map<String, Object>) schema.get("per_page_timeout");
    assertEquals("number", ppt.get("type"));
    assertEquals(2.0, ((Number) ppt.get("default")).doubleValue(), 1e-9);
    assertEquals(Boolean.FALSE, ppt.get("required"));

    Map<String, Object> dl = (Map<String, Object>) schema.get("overall_deadline");
    assertEquals("number", dl.get("type"));
    assertEquals(10.0, ((Number) dl.get("default")).doubleValue(), 1e-9);
    assertEquals(Boolean.FALSE, dl.get("required"));

    Map<String, Object> ps = (Map<String, Object>) schema.get("parallel_scrape");
    assertEquals("boolean", ps.get("type"));
    assertEquals(Boolean.TRUE, ps.get("default"));

    Map<String, Object> so = (Map<String, Object>) schema.get("snippets_only");
    assertEquals("boolean", so.get("type"));
    assertEquals(Boolean.FALSE, so.get("default"));

    Map<String, Object> rp = (Map<String, Object>) schema.get("response_prefix");
    assertEquals("string", rp.get("type"));
    assertEquals("", rp.get("default"));
    Map<String, Object> rpost = (Map<String, Object>) schema.get("response_postfix");
    assertEquals("", rpost.get("default"));
  }

  @Test
  void testEverySetupParamIsAdvertised() {
    // Guard against the recurring "added a setup() read but forgot the
    // schema entry" drift (Python test_every_setup_param_is_advertised).
    WebSearchSkill skill = new WebSearchSkill();
    Map<String, Object> schema = skill.getParameterSchema();
    for (String key :
        List.of(
            "tool_name",
            "num_results",
            "no_results_message",
            "response_prefix",
            "response_postfix",
            "per_page_timeout",
            "overall_deadline",
            "parallel_scrape",
            "snippets_only")) {
      assertTrue(schema.containsKey(key), "setup() reads '" + key + "' but schema omits it");
    }
  }

  // ---- helpers ----

  private static String invokeHandler(WebSearchSkill skill, String query) {
    var tools = skill.registerTools();
    FunctionResult r = tools.get(0).getHandler().handle(Map.of("query", query), Map.of());
    return r.getResponse();
  }

  private static Object readPrivate(Object o, String name) throws Exception {
    Field f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(o);
  }

  /**
   * Minimal in-process HTTP server that mimics Google Custom Search responses AND serves the
   * candidate content pages, so page scrapes are deterministic and offline. The CSE item's {@code
   * link} points back at this server's own {@code /content/<id>} path. Sets WEB_SEARCH_BASE_URL to
   * redirect the CSE call here; restores the prior value on close. A {@link #contentHits} counter
   * records how many content fetches actually started, so a test can assert scraping was (or was
   * not) attempted.
   */
  private static class StubServer implements AutoCloseable {
    private final HttpServer server;
    private final String previousProp;
    final AtomicInteger contentHits = new AtomicInteger(0);
    // Set on close() so a content handler still sleeping (because the
    // skill abandoned its fetch) wakes promptly instead of holding the
    // server's request thread for the full sleep — keeps test teardown
    // fast without weakening the deadline assertion.
    private volatile boolean shuttingDown = false;

    private StubServer(HttpServer server, String previousProp) {
      this.server = server;
      this.previousProp = previousProp;
    }

    /** CSE returns one item whose link is served fast with the given body. */
    static StubServer startWithScrapableItem(
        String title, String snippet, String contentId, String contentBody) throws IOException {
      return build(title, snippet, contentId, contentBody, 0L, 200);
    }

    /** CSE returns one item whose content page sleeps {@code sleepMs}. */
    static StubServer startWithSlowItem(
        String title, String snippet, String contentId, long sleepMs) throws IOException {
      return build(
          title,
          snippet,
          contentId,
          "<html><body>slow body, should be abandoned</body></html>",
          sleepMs,
          200);
    }

    /** CSE returns one item whose content page 404s (empty scrape). */
    static StubServer startWith404Content(String title, String snippet, String contentId)
        throws IOException {
      return build(title, snippet, contentId, "not found", 0L, 404);
    }

    /** CSE returns no items (no-results path). */
    static StubServer startEmptyItems() throws IOException {
      HttpServer s =
          HttpServer.create(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
      String prev = System.getProperty("WEB_SEARCH_BASE_URL");
      StubServer stub = new StubServer(s, prev);
      registerCse(s, "{\"items\":[]}");
      s.start();
      System.setProperty("WEB_SEARCH_BASE_URL", "http://127.0.0.1:" + s.getAddress().getPort());
      return stub;
    }

    /**
     * Build a stub serving (1) the CSE endpoint with a single item whose link points back at this
     * server's {@code /content/<id>} path, and (2) that content page (optionally sleeping {@code
     * sleepMs} and/or returning {@code contentStatus}).
     */
    private static StubServer build(
        String title,
        String snippet,
        String contentId,
        String contentBody,
        long sleepMs,
        int contentStatus)
        throws IOException {
      HttpServer s =
          HttpServer.create(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
      int port = s.getAddress().getPort();
      String prev = System.getProperty("WEB_SEARCH_BASE_URL");
      StubServer stub = new StubServer(s, prev);

      String contentPath = "/content/" + contentId;
      String link = "http://127.0.0.1:" + port + contentPath;
      String cseBody =
          String.format(
              "{\"items\":[{\"title\":\"%s\",\"snippet\":\"%s\",\"link\":\"%s\"}]}",
              title, snippet, link);
      registerCse(s, cseBody);

      s.createContext(
          contentPath,
          (HttpExchange ex) -> {
            stub.contentHits.incrementAndGet();
            if (sleepMs > 0) {
              // Sleep in short slices so close() (or a thread interrupt)
              // can break us out promptly once the skill has abandoned
              // the fetch. The skill never reads our late body anyway.
              long deadline = System.nanoTime() + sleepMs * 1_000_000L;
              try {
                while (System.nanoTime() < deadline && !stub.shuttingDown) {
                  Thread.sleep(25);
                }
              } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                ex.close();
                return;
              }
              if (stub.shuttingDown) {
                ex.close();
                return;
              }
            }
            byte[] bytes = contentBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html");
            ex.sendResponseHeaders(contentStatus, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
          });

      s.start();
      System.setProperty("WEB_SEARCH_BASE_URL", "http://127.0.0.1:" + port);
      return stub;
    }

    private static void registerCse(HttpServer s, String cseBody) {
      s.createContext(
          "/customsearch/v1",
          (HttpExchange ex) -> {
            byte[] bytes = cseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
          });
    }

    @Override
    public void close() {
      shuttingDown = true;
      if (previousProp == null) {
        System.clearProperty("WEB_SEARCH_BASE_URL");
      } else {
        System.setProperty("WEB_SEARCH_BASE_URL", previousProp);
      }
      server.stop(0);
    }
  }
}
