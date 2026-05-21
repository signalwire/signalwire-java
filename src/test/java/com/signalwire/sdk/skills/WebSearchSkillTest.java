package com.signalwire.sdk.skills;

import com.signalwire.sdk.skills.builtin.WebSearchSkill;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

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
        assertTrue(new WebSearchSkill().setup(Map.of(
                "api_key", "test-key",
                "search_engine_id", "test-cx"
        )));
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
        skill.setup(Map.of(
                "api_key", "k",
                "search_engine_id", "cx",
                "response_prefix", "PREFIX",
                "response_postfix", "POSTFIX"
        ));
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
        skill.setup(Map.of(
                "api_key", "k",
                "search_engine_id", "cx",
                "response_prefix", "PRE",
                "response_postfix", "POST"
        ));
        var tools = skill.registerTools();
        var result = tools.get(0).getHandler().handle(Map.of("query", ""), Map.of());
        // Error / no-query path unchanged: it does not get wrapped.
        assertFalse(result.getResponse().contains("PRE"));
        assertFalse(result.getResponse().contains("POST"));
    }

    @Test
    void testSuccessfulResponseWrappedWithPrefixAndPostfix() throws Exception {
        try (StubServer stub = StubServer.startWithItem(
                "Title One", "Snippet here", "https://example.com/one")) {
            WebSearchSkill skill = new WebSearchSkill();
            skill.setup(Map.of(
                    "api_key", "k",
                    "search_engine_id", "cx",
                    "response_prefix", "[FROM PUBLIC WEB]",
                    "response_postfix", "[END]"
            ));
            String response = invokeHandler(skill, "kittens");
            assertTrue(response.startsWith("[FROM PUBLIC WEB]\n\n"),
                    "prefix must lead the response: " + response);
            assertTrue(response.endsWith("\n\n[END]"),
                    "postfix must trail the response: " + response);
            assertTrue(response.contains("Title One"),
                    "body still rendered: " + response);
        }
    }

    @Test
    void testSuccessfulResponseWithoutPrefixPostfixIsUnwrapped() throws Exception {
        try (StubServer stub = StubServer.startWithItem(
                "T", "S", "https://example.com/")) {
            WebSearchSkill skill = new WebSearchSkill();
            skill.setup(Map.of("api_key", "k", "search_engine_id", "cx"));
            String response = invokeHandler(skill, "q");
            assertTrue(response.startsWith("Search results for"),
                    "no prefix leading: " + response);
            assertTrue(response.contains("T"));
        }
    }

    @Test
    void testNoResultsPathIsNotWrappedByPrefixPostfix() throws Exception {
        try (StubServer stub = StubServer.startEmptyItems()) {
            WebSearchSkill skill = new WebSearchSkill();
            skill.setup(Map.of(
                    "api_key", "k",
                    "search_engine_id", "cx",
                    "response_prefix", "PRE",
                    "response_postfix", "POST",
                    "no_results_message", "Nothing found."
            ));
            String response = invokeHandler(skill, "q");
            // No-results path returns the plain no_results_message untouched.
            assertEquals("Nothing found.", response);
        }
    }

    // ---- helpers ----

    private static String invokeHandler(WebSearchSkill skill, String query) {
        var tools = skill.registerTools();
        FunctionResult r = tools.get(0).getHandler().handle(
                Map.of("query", query), Map.of());
        return r.getResponse();
    }

    private static Object readPrivate(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    /**
     * Minimal in-process HTTP server that mimics Google Custom Search
     * responses. Sets the WEB_SEARCH_BASE_URL system property to point
     * the skill at this server, and restores it on close.
     */
    private static class StubServer implements AutoCloseable {
        private final HttpServer server;
        private final String previousProp;

        private StubServer(HttpServer server, String previousProp) {
            this.server = server;
            this.previousProp = previousProp;
        }

        static StubServer startWithItem(String title, String snippet, String link) throws IOException {
            String body = String.format(
                    "{\"items\":[{\"title\":\"%s\",\"snippet\":\"%s\",\"link\":\"%s\"}]}",
                    title, snippet, link);
            return start(body);
        }

        static StubServer startEmptyItems() throws IOException {
            return start("{\"items\":[]}");
        }

        static StubServer start(String body) throws IOException {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/customsearch/v1", (HttpExchange ex) -> {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.close();
            });
            s.start();
            String prev = System.getProperty("WEB_SEARCH_BASE_URL");
            System.setProperty("WEB_SEARCH_BASE_URL",
                    "http://127.0.0.1:" + s.getAddress().getPort());
            return new StubServer(s, prev);
        }

        @Override
        public void close() {
            if (previousProp == null) {
                System.clearProperty("WEB_SEARCH_BASE_URL");
            } else {
                System.setProperty("WEB_SEARCH_BASE_URL", previousProp);
            }
            server.stop(0);
        }
    }
}
