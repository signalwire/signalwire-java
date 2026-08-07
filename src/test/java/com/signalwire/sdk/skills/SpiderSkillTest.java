package com.signalwire.sdk.skills;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.skills.builtin.SpiderSkill;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class SpiderSkillTest {

  @Test
  void testSkillProperties() {
    SpiderSkill skill = new SpiderSkill();
    assertEquals("spider", skill.getName());
    assertNotNull(skill.getDescription());
    assertTrue(skill.supportsMultipleInstances());
  }

  @Test
  void testSetupSucceeds() {
    assertTrue(new SpiderSkill().setup(Map.of()));
  }

  @Test
  void testRegistersThreeTools() {
    SpiderSkill skill = new SpiderSkill();
    skill.setup(Map.of());
    List<ToolDefinition> tools = skill.registerTools();
    assertEquals(3, tools.size());
    assertEquals("scrape_url", tools.get(0).getName());
    assertEquals("crawl_site", tools.get(1).getName());
    assertEquals("extract_structured_data", tools.get(2).getName());
  }

  @Test
  void testToolsHaveHandlers() {
    SpiderSkill skill = new SpiderSkill();
    skill.setup(Map.of());
    var tools = skill.registerTools();
    for (ToolDefinition td : tools) {
      assertTrue(td.hasHandler());
    }
  }

  @Test
  void testToolsHaveDescriptions() {
    SpiderSkill skill = new SpiderSkill();
    skill.setup(Map.of());
    var tools = skill.registerTools();
    for (ToolDefinition td : tools) {
      assertNotNull(td.getDescription());
      assertFalse(td.getDescription().isEmpty());
    }
  }

  // ======== remove_xpaths (reference spider/skill.py:191-199, :313-319) ========
  // The reference drops SEVEN element subtrees via lxml drop_tree before
  // calling text_content(). Anything it does not drop reaches the LLM as
  // "scraped content", so the set is a behavioural contract, not decoration.

  private static final List<String> REFERENCE_REMOVE_XPATHS =
      List.of("//script", "//style", "//nav", "//header", "//footer", "//aside", "//noscript");

  @Test
  void testRemoveXpathsIsPrefilledWithTheReferenceDefaults() {
    assertEquals(REFERENCE_REMOVE_XPATHS, new SpiderSkill().getRemoveXpaths());
  }

  @Test
  void testSetupOverridesRemoveXpaths() {
    SpiderSkill skill = new SpiderSkill();
    skill.setup(Map.of("remove_xpaths", List.of("//aside")));
    assertEquals(List.of("//aside"), skill.getRemoveXpaths());
  }

  /**
   * The load-bearing test. Every element the reference drops must have its TEXT CONTENT absent from
   * the scraped output — not just its tag stripped. A port that only flattens tags leaks
   * nav/header/footer/aside/noscript prose into the model's context.
   */
  @Test
  void testScrapeDropsEveryRemovedElementSubtree() throws Exception {
    String html =
        "<html><head><style>.x{color:red}STYLETEXT</style>"
            + "<script>alert(1);SCRIPTTEXT</script></head><body>"
            + "<header>HEADERTEXT</header>"
            + "<nav>NAVTEXT</nav>"
            + "<aside>ASIDETEXT</aside>"
            // Deliberately NOT "NOSCRIPTTEXT": that string CONTAINS "SCRIPTTEXT",
            // so a noscript leak would masquerade as a script leak and the two
            // element types could not be told apart.
            + "<noscript>NOSCRIPTONLYTEXT</noscript>"
            + "<p>KEEPTEXT</p>"
            + "<footer>FOOTERTEXT</footer>"
            + "</body></html>";
    try (PageServer page = PageServer.serving(html)) {
      String out = scrape(new SpiderSkill(), page.url());

      assertTrue(out.contains("KEEPTEXT"), "real page prose must survive: " + out);
      for (String leaked :
          List.of(
              "SCRIPTTEXT",
              "STYLETEXT",
              "NAVTEXT",
              "HEADERTEXT",
              "FOOTERTEXT",
              "ASIDETEXT",
              "NOSCRIPTONLYTEXT")) {
        assertFalse(
            out.contains(leaked),
            "removed element's text content leaked into scraped output: " + leaked + " in " + out);
      }
      // alert(1)'s body is inside <script>; assert the executable text too.
      assertFalse(out.contains("alert(1)"), "script body leaked: " + out);
    }
  }

  /**
   * The strip is driven by the {@link SpiderSkill#getRemoveXpaths()} field, not by a hardcoded
   * list: clearing an entry lets that element's text through, and adding one takes a new element
   * out.
   */
  @Test
  void testStripIsDrivenByTheRemoveXpathsField() throws Exception {
    String html =
        "<html><body><nav>NAVTEXT</nav><p>KEEPTEXT</p><main>MAINTEXT</main></body></html>";

    try (PageServer page = PageServer.serving(html)) {
      // Narrowed: //nav no longer listed, so NAVTEXT is now expected content.
      SpiderSkill narrowed = new SpiderSkill();
      narrowed.setup(Map.of("remove_xpaths", List.of("//style")));
      String out = scrape(narrowed, page.url());
      assertTrue(out.contains("NAVTEXT"), "un-listed element must NOT be stripped: " + out);

      // Widened: //main added, so MAINTEXT is dropped even though it is not
      // one of the reference defaults.
      SpiderSkill widened = new SpiderSkill();
      widened.setup(Map.of("remove_xpaths", List.of("//main")));
      String widenedOut = scrape(widened, page.url());
      assertFalse(widenedOut.contains("MAINTEXT"), "added element must be stripped: " + widenedOut);
      assertTrue(widenedOut.contains("KEEPTEXT"));
    }
  }

  /**
   * Only the plain {@code //tag} form compiles to a matcher. A more expressive XPath has no engine
   * behind it here, so it is SKIPPED rather than mis-applied — it must not silently strip some
   * wrong element.
   */
  @Test
  void testNonSimpleXpathIsSkippedNotMisapplied() throws Exception {
    String html = "<html><body><div class='ad'>ADTEXT</div><p>KEEPTEXT</p></body></html>";
    try (PageServer page = PageServer.serving(html)) {
      SpiderSkill skill = new SpiderSkill();
      skill.setup(Map.of("remove_xpaths", List.of("//div[@class='ad']")));
      String out = scrape(skill, page.url());
      assertTrue(out.contains("ADTEXT"), "unsupported xpath must be skipped, not guessed: " + out);
      assertTrue(out.contains("KEEPTEXT"), out);
    }
  }

  // ---- helpers ----

  private static String scrape(SpiderSkill skill, String url) {
    for (ToolDefinition td : skill.registerTools()) {
      if ("scrape_url".equals(td.getName())) {
        FunctionResult r = td.getHandler().handle(Map.of("url", url), Map.of());
        return r.getResponse();
      }
    }
    throw new AssertionError("scrape_url tool not registered");
  }

  /**
   * Serves one fixed HTML body on loopback, so the scrape path runs offline and deterministically.
   */
  private static class PageServer implements AutoCloseable {
    private final HttpServer server;

    private PageServer(HttpServer server) {
      this.server = server;
    }

    static PageServer serving(String html) throws IOException {
      HttpServer s =
          HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
      s.createContext(
          "/page",
          (HttpExchange ex) -> {
            ex.getResponseHeaders().add("Content-Type", "text/html");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
          });
      s.start();
      return new PageServer(s);
    }

    String url() {
      return "http://127.0.0.1:" + server.getAddress().getPort() + "/page";
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
