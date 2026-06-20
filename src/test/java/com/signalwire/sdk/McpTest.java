package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for MCP server endpoint and addMcpServer configuration. */
class McpTest {

  private AgentBase agent;

  @BeforeEach
  void setUp() {
    agent = AgentBase.builder().name("test-mcp").authUser("u").authPassword("p").build();
  }

  private AgentBase makeAgentWithTool() {
    agent.defineTool(
        "get_weather",
        "Get the weather for a location",
        Map.of(
            "type",
            "object",
            "properties",
            Map.of("location", Map.of("type", "string", "description", "City name"))),
        (args, raw) ->
            new FunctionResult("72F sunny in " + args.getOrDefault("location", "unknown")));
    agent.enableMcpServer();
    return agent;
  }

  // ======== MCP Tool List ========

  @Test
  void testBuildMcpToolList() {
    AgentBase a = makeAgentWithTool();
    List<Map<String, Object>> tools = a.buildMcpToolList();

    assertEquals(1, tools.size());
    assertEquals("get_weather", tools.get(0).get("name"));
    assertEquals("Get the weather for a location", tools.get(0).get("description"));
    assertNotNull(tools.get(0).get("inputSchema"));
  }

  // ======== Initialize Handshake ========

  @Test
  void testInitializeHandshake() {
    AgentBase a = makeAgentWithTool();
    Map<String, Object> resp =
        a.handleMcpRequest(
            Map.of(
                "jsonrpc",
                "2.0",
                "id",
                1,
                "method",
                "initialize",
                "params",
                Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of())));

    assertEquals("2.0", resp.get("jsonrpc"));
    assertEquals(1, resp.get("id"));
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    assertNotNull(result);
    assertEquals("2025-06-18", result.get("protocolVersion"));
    assertNotNull(result.get("capabilities"));
  }

  // ======== Initialized Notification ========

  @Test
  void testInitializedNotification() {
    AgentBase a = makeAgentWithTool();
    Map<String, Object> resp =
        a.handleMcpRequest(
            Map.of(
                "jsonrpc", "2.0",
                "method", "notifications/initialized"));

    assertNotNull(resp.get("result"));
  }

  // ======== Tools List ========

  @Test
  void testToolsList() {
    AgentBase a = makeAgentWithTool();
    Map<String, Object> resp =
        a.handleMcpRequest(
            Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/list", "params", Map.of()));

    assertEquals(2, resp.get("id"));
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
    assertEquals(1, tools.size());
    assertEquals("get_weather", tools.get(0).get("name"));
  }

  // ======== Tools Call ========

  @Test
  void testToolsCall() {
    AgentBase a = makeAgentWithTool();
    Map<String, Object> resp =
        a.handleMcpRequest(
            Map.of(
                "jsonrpc",
                "2.0",
                "id",
                3,
                "method",
                "tools/call",
                "params",
                Map.of("name", "get_weather", "arguments", Map.of("location", "Orlando"))));

    assertEquals(3, resp.get("id"));
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) resp.get("result");
    assertEquals(false, result.get("isError"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
    assertEquals(1, content.size());
    assertEquals("text", content.get(0).get("type"));
    assertTrue(((String) content.get(0).get("text")).contains("Orlando"));
  }

  // ======== Tools Call Unknown ========

  @Test
  void testToolsCallUnknown() {
    AgentBase a = makeAgentWithTool();
    Map<String, Object> resp =
        a.handleMcpRequest(
            Map.of(
                "jsonrpc",
                "2.0",
                "id",
                4,
                "method",
                "tools/call",
                "params",
                Map.of("name", "nonexistent", "arguments", Map.of())));

    assertNotNull(resp.get("error"));
    @SuppressWarnings("unchecked")
    Map<String, Object> error = (Map<String, Object>) resp.get("error");
    assertEquals(-32602, ((Number) error.get("code")).intValue());
    assertTrue(((String) error.get("message")).contains("nonexistent"));
  }

  // ======== Unknown Method ========

  @Test
  void testUnknownMethod() {
    AgentBase a = makeAgentWithTool();
    Map<String, Object> resp =
        a.handleMcpRequest(
            Map.of("jsonrpc", "2.0", "id", 5, "method", "resources/list", "params", Map.of()));

    assertNotNull(resp.get("error"));
    @SuppressWarnings("unchecked")
    Map<String, Object> error = (Map<String, Object>) resp.get("error");
    assertEquals(-32601, ((Number) error.get("code")).intValue());
  }

  // ======== Ping ========

  @Test
  void testPing() {
    AgentBase a = makeAgentWithTool();
    Map<String, Object> resp =
        a.handleMcpRequest(
            Map.of(
                "jsonrpc", "2.0",
                "id", 6,
                "method", "ping"));

    assertNotNull(resp.get("result"));
  }

  // ======== Invalid JSON-RPC Version ========

  @Test
  void testInvalidJsonrpcVersion() {
    AgentBase a = makeAgentWithTool();
    Map<String, Object> resp =
        a.handleMcpRequest(
            Map.of(
                "jsonrpc", "1.0",
                "id", 7,
                "method", "initialize"));

    assertNotNull(resp.get("error"));
    @SuppressWarnings("unchecked")
    Map<String, Object> error = (Map<String, Object>) resp.get("error");
    assertEquals(-32600, ((Number) error.get("code")).intValue());
  }

  // ======== addMcpServer Tests ========

  @Test
  void testAddMcpServerBasic() {
    agent.addMcpServer("https://mcp.example.com/tools");
    assertEquals(1, agent.getMcpServers().size());
    assertEquals("https://mcp.example.com/tools", agent.getMcpServers().get(0).get("url"));
  }

  @Test
  void testAddMcpServerWithHeaders() {
    agent.addMcpServer("https://mcp.example.com/tools", Map.of("Authorization", "Bearer sk-xxx"));
    @SuppressWarnings("unchecked")
    Map<String, String> headers = (Map<String, String>) agent.getMcpServers().get(0).get("headers");
    assertEquals("Bearer sk-xxx", headers.get("Authorization"));
  }

  @Test
  void testAddMcpServerWithResources() {
    agent.addMcpServer(
        "https://mcp.example.com/crm", null, true, Map.of("caller_id", "${caller_id_number}"));
    assertEquals(true, agent.getMcpServers().get(0).get("resources"));
    @SuppressWarnings("unchecked")
    Map<String, String> vars =
        (Map<String, String>) agent.getMcpServers().get(0).get("resource_vars");
    assertEquals("${caller_id_number}", vars.get("caller_id"));
  }

  @Test
  void testAddMultipleMcpServers() {
    agent.addMcpServer("https://mcp1.example.com");
    agent.addMcpServer("https://mcp2.example.com");
    assertEquals(2, agent.getMcpServers().size());
  }

  @Test
  void testAddMcpServerMethodChaining() {
    AgentBase result = agent.addMcpServer("https://mcp.example.com");
    assertSame(agent, result);
  }

  @Test
  void testEnableMcpServer() {
    assertFalse(agent.isMcpServerEnabled());
    AgentBase result = agent.enableMcpServer();
    assertTrue(agent.isMcpServerEnabled());
    assertSame(agent, result);
  }

  // ======== MCP Servers in SWML ========

  @Test
  @SuppressWarnings("unchecked")
  void testMcpServersRenderedInSwml() {
    agent.setPromptText("Test");
    agent.addMcpServer("https://mcp.example.com/tools", Map.of("Authorization", "Bearer sk-xxx"));
    Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
    Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
    List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
    Map<String, Object> aiVerb =
        main.stream()
            .filter(v -> v.containsKey("ai"))
            .findFirst()
            .map(v -> (Map<String, Object>) v.get("ai"))
            .orElseThrow();
    Map<String, Object> swaig = (Map<String, Object>) aiVerb.get("SWAIG");
    assertNotNull(swaig);
    List<Map<String, Object>> mcpServers = (List<Map<String, Object>>) swaig.get("mcp_servers");
    assertNotNull(mcpServers);
    assertEquals(1, mcpServers.size());
    assertEquals("https://mcp.example.com/tools", mcpServers.get(0).get("url"));
  }
}
