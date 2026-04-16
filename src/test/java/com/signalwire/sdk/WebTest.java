package com.signalwire.sdk;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for web/HTTP configuration: webhook URLs, proxy, query params,
 * dynamic config callback, post-prompt URL, debug routes.
 */
class WebTest {

    private AgentBase agent;

    @BeforeEach
    void setUp() {
        agent = AgentBase.builder()
                .name("web-test")
                .authUser("u")
                .authPassword("p")
                .build();
        agent.setPromptText("Test");
    }

    // ======== Webhook URL ========

    @Test
    @SuppressWarnings("unchecked")
    void testDefaultWebhookUrl() {
        agent.defineTool("t", "d", Map.of(), (a, r) -> new FunctionResult("ok"));
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        Map<String, Object> ai = extractAi(swml);
        Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
        List<Map<String, Object>> fns = (List<Map<String, Object>>) swaig.get("functions");
        String url = (String) fns.get(0).get("web_hook_url");
        assertEquals("http://localhost:3000/swaig", url);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSetWebHookUrl() {
        agent.setWebHookUrl("https://custom.example.com/webhook");
        agent.defineTool("t", "d", Map.of(), (a, r) -> new FunctionResult("ok"));
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        Map<String, Object> ai = extractAi(swml);
        Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
        List<Map<String, Object>> fns = (List<Map<String, Object>>) swaig.get("functions");
        assertEquals("https://custom.example.com/webhook", fns.get(0).get("web_hook_url"));
    }

    // ======== Query Params ========

    @Test
    @SuppressWarnings("unchecked")
    void testSwaigQueryParams() {
        agent.addSwaigQueryParams(Map.of("tenant", "abc", "lang", "en"));
        agent.defineTool("t", "d", Map.of(), (a, r) -> new FunctionResult("ok"));
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        Map<String, Object> ai = extractAi(swml);
        Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
        List<Map<String, Object>> fns = (List<Map<String, Object>>) swaig.get("functions");
        String url = (String) fns.get(0).get("web_hook_url");
        assertTrue(url.contains("?"));
        assertTrue(url.contains("tenant=abc"));
        assertTrue(url.contains("lang=en"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearSwaigQueryParams() {
        agent.addSwaigQueryParams(Map.of("key", "val"));
        agent.clearSwaigQueryParams();
        agent.defineTool("t", "d", Map.of(), (a, r) -> new FunctionResult("ok"));
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        Map<String, Object> ai = extractAi(swml);
        Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
        List<Map<String, Object>> fns = (List<Map<String, Object>>) swaig.get("functions");
        String url = (String) fns.get(0).get("web_hook_url");
        assertFalse(url.contains("?"));
    }

    // ======== Proxy URL ========

    @Test
    void testManualSetProxyUrl() {
        agent.manualSetProxyUrl("https://proxy.example.com");
        // Just verify no crash -- proxy affects how base URL is resolved at runtime
        assertNotNull(agent);
    }

    // ======== Post-Prompt URL ========

    @Test
    @SuppressWarnings("unchecked")
    void testDefaultPostPromptUrl() {
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        Map<String, Object> ai = extractAi(swml);
        String ppUrl = (String) ai.get("post_prompt_url");
        assertNotNull(ppUrl);
        assertTrue(ppUrl.contains("/post_prompt"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSetPostPromptUrl() {
        agent.setPostPromptUrl("https://custom.example.com/post_prompt");
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        Map<String, Object> ai = extractAi(swml);
        assertEquals("https://custom.example.com/post_prompt", ai.get("post_prompt_url"));
    }

    // ======== Dynamic Config Callback ========

    @Test
    void testSetDynamicConfigCallback() {
        agent.setDynamicConfigCallback((queryParams, bodyParams, headers, agentRef) -> {
            // No-op callback
        });
        assertNotNull(agent);
    }

    // ======== Debug Routes ========

    @Test
    void testEnableDebugRoutes() {
        agent.enableDebugRoutes();
        assertNotNull(agent);
    }

    // ======== Route with path ========

    @Test
    @SuppressWarnings("unchecked")
    void testRouteIncludedInWebhookUrl() {
        AgentBase routeAgent = AgentBase.builder()
                .name("routed")
                .route("/myagent")
                .authUser("u")
                .authPassword("p")
                .build();
        routeAgent.setPromptText("Test");
        routeAgent.defineTool("t", "d", Map.of(), (a, r) -> new FunctionResult("ok"));
        Map<String, Object> swml = routeAgent.renderSwml("http://localhost:3000");
        Map<String, Object> ai = extractAi(swml);
        Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
        List<Map<String, Object>> fns = (List<Map<String, Object>>) swaig.get("functions");
        String url = (String) fns.get(0).get("web_hook_url");
        assertTrue(url.contains("/myagent/swaig"));
    }

    // ======== Route preservation invariant (Lambda + proxy) ========

    /**
     * Regression test mirroring the bug fixed in Python and TypeScript
     * SDKs: when a non-root route is combined with a proxy base URL
     * (such as a Lambda Function URL), the rendered webhook URL must
     * include the agent's route before {@code /swaig}. The proxy base
     * must NOT be used bare.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testRoutePreservedWithProxyBase() {
        AgentBase routeAgent = AgentBase.builder()
                .name("routed")
                .route("/my-agent")
                .authUser("u")
                .authPassword("p")
                .build();
        routeAgent.setPromptText("Test");
        routeAgent.manualSetProxyUrl("https://xyz.lambda-url.us-east-1.on.aws");
        routeAgent.defineTool("t", "d", Map.of(), (a, r) -> new FunctionResult("ok"));

        // renderSwml is passed the base URL that the HTTP server / Lambda
        // adapter would have resolved — simulate that here.
        Map<String, Object> swml = routeAgent.renderSwml(
                "https://xyz.lambda-url.us-east-1.on.aws");
        Map<String, Object> ai = extractAi(swml);
        Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
        List<Map<String, Object>> fns = (List<Map<String, Object>>) swaig.get("functions");
        String url = (String) fns.get(0).get("web_hook_url");
        assertEquals("https://xyz.lambda-url.us-east-1.on.aws/my-agent/swaig", url);
        assertFalse(url.endsWith(".on.aws/swaig"),
                "BUG REGRESSION: webhook URL lost the agent's route: " + url);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRoutePreservedInPostPromptUrl() {
        AgentBase routeAgent = AgentBase.builder()
                .name("routed")
                .route("/my-agent")
                .authUser("u")
                .authPassword("p")
                .build();
        routeAgent.setPromptText("Test");
        routeAgent.setPostPrompt("Summarise");
        Map<String, Object> swml = routeAgent.renderSwml(
                "https://xyz.lambda-url.us-east-1.on.aws");
        Map<String, Object> ai = extractAi(swml);
        assertEquals("https://xyz.lambda-url.us-east-1.on.aws/my-agent/post_prompt",
                ai.get("post_prompt_url"));
    }

    // ======== Method chaining ========

    @Test
    void testWebMethodChaining() {
        AgentBase result = agent
                .setWebHookUrl("http://example.com")
                .setPostPromptUrl("http://example.com/pp")
                .manualSetProxyUrl("http://proxy.com")
                .addSwaigQueryParams(Map.of("k", "v"))
                .clearSwaigQueryParams()
                .enableDebugRoutes();
        assertSame(agent, result);
    }

    // ======== Helper ========

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAi(Map<String, Object> swml) {
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
        return main.stream()
                .filter(v -> v.containsKey("ai"))
                .findFirst()
                .map(v -> (Map<String, Object>) v.get("ai"))
                .orElseThrow();
    }
}
