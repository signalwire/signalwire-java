package com.signalwire.agents;

import com.signalwire.agents.agent.AgentBase;
import com.signalwire.agents.datamap.DataMap;
import com.signalwire.agents.swaig.FunctionResult;
import com.signalwire.agents.swaig.ToolDefinition;
import com.signalwire.agents.swaig.ToolHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for tool registration, dispatch, ToolDefinition, and DataMap tool integration.
 */
class ToolTest {

    private AgentBase agent;

    @BeforeEach
    void setUp() {
        agent = AgentBase.builder()
                .name("tool-test")
                .authUser("u")
                .authPassword("p")
                .build();
    }

    // ======== defineTool (name, description, parameters, handler) ========

    @Test
    void testDefineToolByArgs() {
        agent.defineTool("greet", "Greet a user",
                Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"))),
                (args, raw) -> new FunctionResult("Hello, " + args.get("name")));
        assertTrue(agent.hasTool("greet"));
    }

    @Test
    void testDefineToolByDefinition() {
        ToolDefinition td = new ToolDefinition("calc", "Calculate",
                Map.of("type", "object"), (a, r) -> new FunctionResult("42"));
        agent.defineTool(td);
        assertTrue(agent.hasTool("calc"));
    }

    @Test
    void testDefineToolsListRegistersAll() {
        List<ToolDefinition> tools = List.of(
                new ToolDefinition("t1", "T1", Map.of(), (a, r) -> new FunctionResult("1")),
                new ToolDefinition("t2", "T2", Map.of(), (a, r) -> new FunctionResult("2")),
                new ToolDefinition("t3", "T3", Map.of(), (a, r) -> new FunctionResult("3"))
        );
        agent.defineTools(tools);
        assertTrue(agent.hasTool("t1"));
        assertTrue(agent.hasTool("t2"));
        assertTrue(agent.hasTool("t3"));
    }

    // ======== Tool execution ========

    @Test
    void testToolExecution() {
        agent.defineTool("echo", "Echo input",
                Map.of("type", "object", "properties", Map.of("msg", Map.of("type", "string"))),
                (args, raw) -> new FunctionResult("Echo: " + args.get("msg")));
        FunctionResult result = agent.onFunctionCall("echo", Map.of("msg", "hello"), Map.of());
        assertEquals("Echo: hello", result.getResponse());
    }

    @Test
    void testToolNotFoundReturnsErrorResult() {
        FunctionResult result = agent.onFunctionCall("missing_tool", Map.of(), Map.of());
        assertNotNull(result);
        assertTrue(result.getResponse().contains("not found"));
    }

    @Test
    void testToolExceptionReturnsErrorResult() {
        agent.defineTool("failing", "Fails", Map.of(),
                (args, raw) -> { throw new RuntimeException("Boom!"); });
        FunctionResult result = agent.onFunctionCall("failing", Map.of(), Map.of());
        assertTrue(result.getResponse().contains("Error"));
    }

    @Test
    void testToolWithMultipleArgs() {
        agent.defineTool("concat", "Concatenate", Map.of(
                "type", "object",
                "properties", Map.of(
                        "a", Map.of("type", "string"),
                        "b", Map.of("type", "string")
                )),
                (args, raw) -> new FunctionResult(args.get("a") + " " + args.get("b")));
        FunctionResult result = agent.onFunctionCall("concat",
                Map.of("a", "foo", "b", "bar"), Map.of());
        assertEquals("foo bar", result.getResponse());
    }

    // ======== hasTool ========

    @Test
    void testHasToolFalse() {
        assertFalse(agent.hasTool("nonexistent"));
    }

    @Test
    void testHasToolAfterDefine() {
        agent.defineTool("exists", "desc", Map.of(), (a, r) -> new FunctionResult("ok"));
        assertTrue(agent.hasTool("exists"));
    }

    // ======== getTools ========

    @Test
    void testGetToolsIsUnmodifiable() {
        agent.defineTool("t", "d", Map.of(), (a, r) -> new FunctionResult("ok"));
        Map<String, ToolDefinition> tools = agent.getTools();
        assertThrows(UnsupportedOperationException.class, () -> tools.put("x", null));
    }

    @Test
    void testGetToolsContainsRegisteredTools() {
        agent.defineTool("a", "A", Map.of(), (a, r) -> new FunctionResult("a"));
        agent.defineTool("b", "B", Map.of(), (a, r) -> new FunctionResult("b"));
        assertEquals(2, agent.getTools().size());
        assertTrue(agent.getTools().containsKey("a"));
        assertTrue(agent.getTools().containsKey("b"));
    }

    // ======== ToolDefinition ========

    @Test
    void testToolDefinitionProperties() {
        ToolDefinition td = new ToolDefinition("name", "desc",
                Map.of("type", "object"), (a, r) -> new FunctionResult("ok"));
        assertEquals("name", td.getName());
        assertEquals("desc", td.getDescription());
        assertNotNull(td.getParameters());
        assertTrue(td.hasHandler());
    }

    @Test
    void testToolDefinitionNullHandler() {
        ToolDefinition td = new ToolDefinition("name", "desc", Map.of(), null);
        assertFalse(td.hasHandler());
    }

    @Test
    void testToolDefinitionSecure() {
        ToolDefinition td = new ToolDefinition("name", "desc", Map.of(), (a, r) -> new FunctionResult("ok"));
        assertFalse(td.isSecure());
        td.setSecure(true);
        assertTrue(td.isSecure());
    }

    @Test
    void testToolDefinitionExtraFields() {
        ToolDefinition td = new ToolDefinition("name", "desc", Map.of(), (a, r) -> new FunctionResult("ok"));
        assertNull(td.getExtraFields());
        td.setExtraFields(Map.of("fillers", "test"));
        assertNotNull(td.getExtraFields());
        assertEquals("test", td.getExtraFields().get("fillers"));
    }

    @Test
    void testToolDefinitionToSwaigFunction() {
        ToolDefinition td = new ToolDefinition("my_tool", "My tool description",
                Map.of("type", "object", "properties", Map.of("x", Map.of("type", "string"))),
                (a, r) -> new FunctionResult("ok"));
        Map<String, Object> func = td.toSwaigFunction("http://localhost/swaig", null);
        assertEquals("my_tool", func.get("function"));
        assertEquals("My tool description", func.get("purpose"));
        assertNotNull(func.get("argument"));
        assertEquals("http://localhost/swaig", func.get("web_hook_url"));
        assertFalse(func.containsKey("meta_data_token"));
    }

    @Test
    void testToolDefinitionToSwaigFunctionWithToken() {
        ToolDefinition td = new ToolDefinition("secure_tool", "Secure", Map.of(),
                (a, r) -> new FunctionResult("ok"));
        Map<String, Object> func = td.toSwaigFunction("http://localhost/swaig", "token-123");
        assertEquals("token-123", func.get("meta_data_token"));
    }

    @Test
    void testToolDefinitionExtraFieldsMergedIntoSwaig() {
        ToolDefinition td = new ToolDefinition("tool", "desc", Map.of(),
                (a, r) -> new FunctionResult("ok"));
        td.setExtraFields(Map.of("fillers", Map.of("en-US", List.of("Hold on"))));
        Map<String, Object> func = td.toSwaigFunction("http://localhost", null);
        assertTrue(func.containsKey("fillers"));
    }

    // ======== registerSwaigFunction (DataMap) ========

    @Test
    @SuppressWarnings("unchecked")
    void testRegisterSwaigFunction() {
        agent.setPromptText("Test");
        DataMap dm = new DataMap("weather")
                .purpose("Get weather")
                .webhook("GET", "https://api.weather.com")
                .output(new FunctionResult("Sunny"));
        agent.registerSwaigFunction(dm.toSwaigFunction());

        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
        Map<String, Object> aiVerb = main.stream()
                .filter(v -> v.containsKey("ai"))
                .findFirst()
                .map(v -> (Map<String, Object>) v.get("ai"))
                .orElseThrow();
        Map<String, Object> swaig = (Map<String, Object>) aiVerb.get("SWAIG");
        List<Map<String, Object>> functions = (List<Map<String, Object>>) swaig.get("functions");
        assertTrue(functions.stream().anyMatch(f -> "weather".equals(f.get("function"))));
    }

    // ======== Tools rendered in SWML ========

    @Test
    @SuppressWarnings("unchecked")
    void testToolRenderedWithWebhookUrl() {
        agent.setPromptText("Test");
        agent.defineTool("my_tool", "desc", Map.of(), (a, r) -> new FunctionResult("ok"));
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
        Map<String, Object> aiVerb = main.stream()
                .filter(v -> v.containsKey("ai"))
                .findFirst()
                .map(v -> (Map<String, Object>) v.get("ai"))
                .orElseThrow();
        Map<String, Object> swaig = (Map<String, Object>) aiVerb.get("SWAIG");
        List<Map<String, Object>> functions = (List<Map<String, Object>>) swaig.get("functions");
        assertFalse(functions.isEmpty());
        Map<String, Object> func = functions.get(0);
        assertEquals("my_tool", func.get("function"));
        String webhookUrl = (String) func.get("web_hook_url");
        assertNotNull(webhookUrl);
        assertTrue(webhookUrl.contains("/swaig"));
    }

    // ======== Tool overwrite ========

    @Test
    void testDefineToolOverwritesPrevious() {
        agent.defineTool("tool", "first", Map.of(), (a, r) -> new FunctionResult("first"));
        agent.defineTool("tool", "second", Map.of(), (a, r) -> new FunctionResult("second"));
        FunctionResult result = agent.onFunctionCall("tool", Map.of(), Map.of());
        assertEquals("second", result.getResponse());
    }

    // ======== Method chaining ========

    @Test
    void testDefineToolReturnsAgent() {
        AgentBase result = agent.defineTool("t", "d", Map.of(), (a, r) -> new FunctionResult("ok"));
        assertSame(agent, result);
    }
}
