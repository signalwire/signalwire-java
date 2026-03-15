package com.signalwire.agents;

import com.signalwire.agents.agent.AgentBase;
import com.signalwire.agents.swaig.FunctionResult;
import com.signalwire.agents.swaig.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AgentBaseTest {

    private AgentBase agent;

    @BeforeEach
    void setUp() {
        agent = AgentBase.builder()
                .name("test-agent")
                .route("/test")
                .port(4000)
                .authUser("testuser")
                .authPassword("testpass")
                .build();
    }

    // ======== Builder Tests ========

    @Test
    void testBuilderCreatesAgent() {
        assertNotNull(agent);
        assertEquals("test-agent", agent.getName());
        assertEquals("/test", agent.getRoute());
        assertEquals(4000, agent.getPort());
        assertEquals("testuser", agent.getAuthUser());
        assertEquals("testpass", agent.getAuthPassword());
    }

    @Test
    void testBuilderDefaults() {
        AgentBase defaultAgent = AgentBase.builder()
                .name("default-agent")
                .authUser("user")
                .authPassword("pass")
                .build();
        assertEquals("default-agent", defaultAgent.getName());
        assertEquals("/", defaultAgent.getRoute());
        assertEquals("0.0.0.0", defaultAgent.getHost());
    }

    @Test
    void testRouteTrailingSlashRemoved() {
        AgentBase a = AgentBase.builder()
                .name("agent")
                .route("/api/")
                .authUser("u")
                .authPassword("p")
                .build();
        assertEquals("/api", a.getRoute());
    }

    // ======== Prompt Tests ========

    @Test
    void testSetPromptText() {
        agent.setPromptText("You are a helpful assistant.");
        Object prompt = agent.getPrompt();
        assertInstanceOf(Map.class, prompt);
        @SuppressWarnings("unchecked")
        Map<String, Object> promptMap = (Map<String, Object>) prompt;
        assertEquals("You are a helpful assistant.", promptMap.get("text"));
    }

    @Test
    void testPromptAddSection() {
        agent.promptAddSection("Personality", "Be friendly.",
                List.of("Be helpful", "Be concise"));
        assertTrue(agent.promptHasSection("Personality"));
        assertFalse(agent.promptHasSection("Nonexistent"));

        Object prompt = agent.getPrompt();
        assertInstanceOf(Map.class, prompt);
        @SuppressWarnings("unchecked")
        Map<String, Object> promptMap = (Map<String, Object>) prompt;
        assertNotNull(promptMap.get("pom"));
    }

    @Test
    void testPromptAddSubsection() {
        agent.promptAddSection("Main", "Main body");
        agent.promptAddSubsection("Main", "Sub", "Subsection body");

        Object prompt = agent.getPrompt();
        @SuppressWarnings("unchecked")
        Map<String, Object> promptMap = (Map<String, Object>) prompt;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pom = (List<Map<String, Object>>) promptMap.get("pom");
        assertNotNull(pom);
        assertEquals(1, pom.size());
        assertNotNull(pom.get(0).get("subsections"));
    }

    @Test
    void testPromptAddToSection() {
        agent.promptAddSection("Rules", "Follow these:", List.of("Rule 1"));
        agent.promptAddToSection("Rules", List.of("Rule 2", "Rule 3"));

        Object prompt = agent.getPrompt();
        @SuppressWarnings("unchecked")
        Map<String, Object> promptMap = (Map<String, Object>) prompt;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pom = (List<Map<String, Object>>) promptMap.get("pom");
        @SuppressWarnings("unchecked")
        List<String> bullets = (List<String>) pom.get(0).get("bullets");
        assertEquals(3, bullets.size());
    }

    // ======== Tool Tests ========

    @Test
    void testDefineTool() {
        agent.defineTool("greet", "Say hello",
                Map.of("type", "object", "properties", Map.of(
                        "name", Map.of("type", "string")
                )),
                (args, raw) -> new FunctionResult("Hello, " + args.get("name") + "!"));

        assertTrue(agent.hasTool("greet"));
        assertFalse(agent.hasTool("nonexistent"));
    }

    @Test
    void testToolExecution() {
        agent.defineTool("echo", "Echo input",
                Map.of("type", "object", "properties", Map.of(
                        "message", Map.of("type", "string")
                )),
                (args, raw) -> new FunctionResult("Echo: " + args.get("message")));

        FunctionResult result = agent.onFunctionCall("echo",
                Map.of("message", "test"),
                Map.of());
        assertNotNull(result);
        assertEquals("Echo: test", result.getResponse());
    }

    @Test
    void testToolNotFound() {
        FunctionResult result = agent.onFunctionCall("nonexistent", Map.of(), Map.of());
        assertTrue(result.getResponse().contains("not found"));
    }

    @Test
    void testDefineToolDefinition() {
        ToolDefinition td = new ToolDefinition("calc", "Calculate",
                Map.of("type", "object", "properties", Map.of()),
                (args, raw) -> new FunctionResult("42"));
        agent.defineTool(td);
        assertTrue(agent.hasTool("calc"));
    }

    @Test
    void testDefineMultipleTools() {
        ToolDefinition td1 = new ToolDefinition("t1", "Tool 1", Map.of(), (a, r) -> new FunctionResult("1"));
        ToolDefinition td2 = new ToolDefinition("t2", "Tool 2", Map.of(), (a, r) -> new FunctionResult("2"));
        agent.defineTools(List.of(td1, td2));
        assertTrue(agent.hasTool("t1"));
        assertTrue(agent.hasTool("t2"));
    }

    // ======== AI Config Tests ========

    @Test
    void testAddHints() {
        agent.addHint("SignalWire");
        agent.addHints(List.of("SWML", "agent"));
        // Verify via SWML rendering
        Map<String, Object> swml = agent.renderSwml("http://localhost:4000");
        assertNotNull(swml);
    }

    @Test
    void testAddLanguage() {
        agent.addLanguage("English", "en-US", "rachel");
        Map<String, Object> swml = agent.renderSwml("http://localhost:4000");
        assertNotNull(swml);
    }

    @Test
    void testAddPronunciation() {
        agent.addPronunciation("SW", "SignalWire", true);
        Map<String, Object> swml = agent.renderSwml("http://localhost:4000");
        assertNotNull(swml);
    }

    @Test
    void testSetParams() {
        agent.setParam("temperature", 0.7);
        agent.setParams(Map.of("top_p", 0.9));
        Map<String, Object> swml = agent.renderSwml("http://localhost:4000");
        assertNotNull(swml);
    }

    @Test
    void testGlobalData() {
        agent.setGlobalData(Map.of("key1", "value1"));
        agent.updateGlobalData(Map.of("key2", "value2"));
        Map<String, Object> gd = agent.getGlobalData();
        assertEquals("value1", gd.get("key1"));
        assertEquals("value2", gd.get("key2"));
    }

    @Test
    void testSetNativeFunctions() {
        agent.setNativeFunctions(List.of("check_for_input"));
        Map<String, Object> swml = agent.renderSwml("http://localhost:4000");
        assertNotNull(swml);
    }

    // ======== SWML Rendering Tests ========

    @Test
    @SuppressWarnings("unchecked")
    void testRenderSwml() {
        agent.setPromptText("You are helpful.");

        Map<String, Object> swml = agent.renderSwml("http://localhost:4000");
        assertNotNull(swml);
        assertEquals("1.0.0", swml.get("version"));

        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        assertNotNull(sections);

        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
        assertNotNull(main);
        assertFalse(main.isEmpty());

        // Should contain answer verb
        boolean hasAnswer = main.stream().anyMatch(v -> v.containsKey("answer"));
        assertTrue(hasAnswer);

        // Should contain ai verb
        boolean hasAi = main.stream().anyMatch(v -> v.containsKey("ai"));
        assertTrue(hasAi);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRenderSwmlWithTools() {
        agent.setPromptText("Test");
        agent.defineTool("my_tool", "My tool", Map.of(), (a, r) -> new FunctionResult("ok"));

        Map<String, Object> swml = agent.renderSwml("http://localhost:4000");
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");

        Map<String, Object> aiVerb = main.stream()
                .filter(v -> v.containsKey("ai"))
                .findFirst()
                .map(v -> (Map<String, Object>) v.get("ai"))
                .orElseThrow();

        Map<String, Object> swaig = (Map<String, Object>) aiVerb.get("SWAIG");
        assertNotNull(swaig);
        List<Map<String, Object>> functions = (List<Map<String, Object>>) swaig.get("functions");
        assertNotNull(functions);
        assertFalse(functions.isEmpty());
        assertEquals("my_tool", functions.get(0).get("function"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRenderSwmlWithGlobalData() {
        agent.setPromptText("Test");
        agent.updateGlobalData(Map.of("testKey", "testValue"));

        Map<String, Object> swml = agent.renderSwml("http://localhost:4000");
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");

        Map<String, Object> aiVerb = main.stream()
                .filter(v -> v.containsKey("ai"))
                .findFirst()
                .map(v -> (Map<String, Object>) v.get("ai"))
                .orElseThrow();

        Map<String, Object> globalData = (Map<String, Object>) aiVerb.get("global_data");
        assertNotNull(globalData);
        assertEquals("testValue", globalData.get("testKey"));
    }

    // ======== Verb Tests ========

    @Test
    @SuppressWarnings("unchecked")
    void testPreAnswerVerbs() {
        agent.setPromptText("Test");
        agent.addPreAnswerVerb("play", Map.of("url", "ringback.wav"));

        Map<String, Object> swml = agent.renderSwml("http://localhost:4000");
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");

        // First verb should be the pre-answer play
        assertTrue(main.get(0).containsKey("play"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testPostAiVerbs() {
        agent.setPromptText("Test");
        agent.addPostAiVerb("hangup", Map.of());

        Map<String, Object> swml = agent.renderSwml("http://localhost:4000");
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");

        // Last verb should be hangup
        assertTrue(main.get(main.size() - 1).containsKey("hangup"));
    }

    @Test
    void testClearVerbs() {
        agent.addPreAnswerVerb("play", Map.of());
        agent.addPostAnswerVerb("play", Map.of());
        agent.addPostAiVerb("hangup", Map.of());

        agent.clearPreAnswerVerbs();
        agent.clearPostAnswerVerbs();
        agent.clearPostAiVerbs();

        // Should still render without errors
        agent.setPromptText("Test");
        Map<String, Object> swml = agent.renderSwml("http://localhost:4000");
        assertNotNull(swml);
    }

    // ======== Clone Tests ========

    @Test
    void testClone() {
        agent.setPromptText("Original prompt");
        agent.addHint("original");
        agent.updateGlobalData(Map.of("key", "original"));

        AgentBase clone = agent.clone();

        // Verify clone has same values
        assertEquals(agent.getName(), clone.getName());

        // Modify clone should not affect original
        clone.updateGlobalData(Map.of("key", "modified"));
        assertEquals("original", agent.getGlobalData().get("key"));
    }

    // ======== SIP Tests ========

    @Test
    void testSipRouting() {
        agent.enableSipRouting();
        agent.registerSipUsername("valid_user.123");
        assertTrue(agent.isSipRoutingEnabled());
        assertTrue(agent.getSipUsernames().contains("valid_user.123"));
    }

    @Test
    void testInvalidSipUsername() {
        agent.registerSipUsername("invalid user!@#");
        assertFalse(agent.getSipUsernames().contains("invalid user!@#"));
    }

    // ======== Web Config Tests ========

    @Test
    void testQueryParams() {
        agent.addSwaigQueryParams(Map.of("key1", "val1", "key2", "val2"));
        agent.clearSwaigQueryParams();
        // Should not crash
        agent.setPromptText("Test");
        Map<String, Object> swml = agent.renderSwml("http://localhost:4000");
        assertNotNull(swml);
    }

    // ======== Method Chaining ========

    @Test
    void testMethodChaining() {
        AgentBase result = agent
                .setPromptText("Test")
                .setPostPrompt("Summarize")
                .addHint("test")
                .addHints(List.of("a", "b"))
                .addLanguage("English", "en-US", "rachel")
                .addPronunciation("SW", "SignalWire", true)
                .setParam("temperature", 0.5)
                .addPreAnswerVerb("play", Map.of())
                .enableDebugEvents()
                .enableSipRouting();

        assertSame(agent, result);
    }

    // ======== JSON Rendering ========

    @Test
    void testRenderSwmlJson() {
        agent.setPromptText("Test");
        String json = agent.renderSwmlJson("http://localhost:4000");
        assertNotNull(json);
        assertTrue(json.contains("version"));
        assertTrue(json.contains("1.0.0"));
    }

    // ======== RegisterSwaigFunction (DataMap) ========

    @Test
    @SuppressWarnings("unchecked")
    void testRegisterSwaigFunction() {
        agent.setPromptText("Test");
        Map<String, Object> dataMapFunc = new LinkedHashMap<>();
        dataMapFunc.put("function", "weather");
        dataMapFunc.put("purpose", "Get weather");
        dataMapFunc.put("data_map", Map.of("webhooks", List.of()));
        agent.registerSwaigFunction(dataMapFunc);

        Map<String, Object> swml = agent.renderSwml("http://localhost:4000");
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
        Map<String, Object> aiVerb = main.stream()
                .filter(v -> v.containsKey("ai"))
                .findFirst()
                .map(v -> (Map<String, Object>) v.get("ai"))
                .orElseThrow();
        Map<String, Object> swaig = (Map<String, Object>) aiVerb.get("SWAIG");
        assertNotNull(swaig);
        List<Map<String, Object>> functions = (List<Map<String, Object>>) swaig.get("functions");
        assertTrue(functions.stream().anyMatch(f -> "weather".equals(f.get("function"))));
    }
}
