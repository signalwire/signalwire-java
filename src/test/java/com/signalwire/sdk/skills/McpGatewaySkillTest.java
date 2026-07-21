package com.signalwire.sdk.skills;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.skills.builtin.McpGatewaySkill;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * Bidirectional tests for the mcp_gateway CLIENT skill. A JDK {@link HttpServer} stands in for a
 * running MCP Gateway; the test asserts the skill enumerates services/tools over HTTP and registers
 * each MCP tool as a SWAIG function, that calling a tool proxies through the gateway, and that
 * {@code verify_ssl} defaults to true and controls the HttpClient's peer verification when flipped.
 */
class McpGatewaySkillTest {

  /** A minimal in-process MCP-Gateway mock: /health, /services, /services/{n}/tools, /call. */
  private static final class MockGateway implements AutoCloseable {
    final HttpServer server;
    final String baseUrl;
    final List<String> paths = new CopyOnWriteArrayList<>();
    final List<String> authHeaders = new CopyOnWriteArrayList<>();

    MockGateway() throws IOException {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      server.createContext("/", this::handle);
      server.setExecutor(null);
      server.start();
      baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange ex) throws IOException {
      String path = ex.getRequestURI().getPath();
      paths.add(path);
      String auth = ex.getRequestHeaders().getFirst("Authorization");
      authHeaders.add(auth == null ? "" : auth);
      // Drain the request body.
      try (InputStream in = ex.getRequestBody()) {
        in.readAllBytes();
      }
      String body;
      if ("/health".equals(path)) {
        body = "{\"status\":\"ok\"}";
      } else if ("/services".equals(path)) {
        body = "[\"calc\"]";
      } else if ("/services/calc/tools".equals(path)) {
        body =
            "{\"tools\":[{\"name\":\"add\",\"description\":\"Add two numbers\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{"
                + "\"a\":{\"type\":\"integer\",\"description\":\"first\"},"
                + "\"b\":{\"type\":\"integer\",\"description\":\"second\"}},"
                + "\"required\":[\"a\",\"b\"]}}]}";
      } else if ("/services/calc/call".equals(path)) {
        body = "{\"result\":\"42\"}";
      } else {
        body = "{}";
      }
      byte[] out = body.getBytes(StandardCharsets.UTF_8);
      ex.sendResponseHeaders(200, out.length);
      ex.getResponseBody().write(out);
      ex.close();
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }

  @Test
  void skillIdentity() {
    McpGatewaySkill skill = new McpGatewaySkill();
    assertEquals("mcp_gateway", skill.getName());
    assertEquals("Bridge MCP servers with SWAIG functions", skill.getDescription());
  }

  @Test
  void setupRequiresGatewayUrl() {
    // No gateway_url, no token → fails.
    assertFalse(new McpGatewaySkill().setup(Map.of("auth_token", "t")));
  }

  @Test
  void setupRequiresBasicCredentialsWithoutToken() throws Exception {
    try (MockGateway gw = new MockGateway()) {
      // gateway_url present but no token and no basic creds → fails.
      assertFalse(new McpGatewaySkill().setup(Map.of("gateway_url", gw.baseUrl)));
    }
  }

  @Test
  void setupWithBearerTokenProbesHealth() throws Exception {
    try (MockGateway gw = new MockGateway()) {
      McpGatewaySkill skill = new McpGatewaySkill();
      assertTrue(skill.setup(Map.of("gateway_url", gw.baseUrl, "auth_token", "secret")));
      assertTrue(gw.paths.contains("/health"));
      assertEquals("Bearer secret", gw.authHeaders.get(0));
    }
  }

  @Test
  void setupWithBasicAuthSendsBasicHeader() throws Exception {
    try (MockGateway gw = new MockGateway()) {
      McpGatewaySkill skill = new McpGatewaySkill();
      assertTrue(
          skill.setup(
              Map.of(
                  "gateway_url", gw.baseUrl,
                  "auth_user", "u",
                  "auth_password", "p")));
      String expected =
          "Basic " + Base64.getEncoder().encodeToString("u:p".getBytes(StandardCharsets.UTF_8));
      assertEquals(expected, gw.authHeaders.get(0));
    }
  }

  @Test
  void registersGatewayToolsAsSwaigFunctions() throws Exception {
    try (MockGateway gw = new MockGateway()) {
      McpGatewaySkill skill = new McpGatewaySkill();
      assertTrue(skill.setup(Map.of("gateway_url", gw.baseUrl, "auth_token", "t")));

      List<ToolDefinition> tools = skill.registerTools();
      // The MCP "add" tool plus the internal hangup hook.
      List<String> names = new ArrayList<>();
      for (ToolDefinition t : tools) {
        names.add(t.getName());
      }
      assertTrue(names.contains("mcp_calc_add"), "expected registered tool, got " + names);
      assertTrue(names.contains("_mcp_gateway_hangup"));

      // The gateway was enumerated over HTTP.
      assertTrue(gw.paths.contains("/services"));
      assertTrue(gw.paths.contains("/services/calc/tools"));

      // The registered SWAIG tool carries the MCP schema (properties + required).
      ToolDefinition add =
          tools.stream().filter(t -> t.getName().equals("mcp_calc_add")).findFirst().orElseThrow();
      @SuppressWarnings("unchecked")
      Map<String, Object> props = (Map<String, Object>) add.getParameters().get("properties");
      assertTrue(props.containsKey("a"));
      assertTrue(props.containsKey("b"));
      assertEquals(List.of("a", "b"), add.getParameters().get("required"));

      // Invoking the handler proxies the call through the gateway.
      FunctionResult result = add.getHandler().handle(Map.of("a", 1, "b", 2), Map.of());
      assertTrue(gw.paths.contains("/services/calc/call"));
      String json = result.toJson();
      assertTrue(
          json.contains("42"), "gateway result should surface in the FunctionResult: " + json);
    }
  }

  @Test
  void verifySslDefaultsTrue() throws Exception {
    try (MockGateway gw = new MockGateway()) {
      McpGatewaySkill skill = new McpGatewaySkill();
      skill.setup(Map.of("gateway_url", gw.baseUrl, "auth_token", "t"));
      assertTrue(readVerifySsl(skill), "verify_ssl must default to true (secure)");
    }
  }

  @Test
  void verifySslCanBeDisabled() throws Exception {
    try (MockGateway gw = new MockGateway()) {
      McpGatewaySkill skill = new McpGatewaySkill();
      skill.setup(Map.of("gateway_url", gw.baseUrl, "auth_token", "t", "verify_ssl", false));
      assertFalse(readVerifySsl(skill), "verify_ssl=false must flip peer verification off");
    }
  }

  @Test
  void verifySslPresentInParameterSchemaWithSecureDefault() {
    Map<String, Object> schema = new McpGatewaySkill().getParameterSchema();
    assertTrue(schema.containsKey("verify_ssl"));
    @SuppressWarnings("unchecked")
    Map<String, Object> vs = (Map<String, Object>) schema.get("verify_ssl");
    assertEquals("boolean", vs.get("type"));
    assertEquals(Boolean.TRUE, vs.get("default"));
  }

  @Test
  void globalDataAndHintsReflectServices() throws Exception {
    try (MockGateway gw = new MockGateway()) {
      McpGatewaySkill skill = new McpGatewaySkill();
      Map<String, Object> service = new LinkedHashMap<>();
      service.put("name", "calc");
      skill.setup(
          Map.of("gateway_url", gw.baseUrl, "auth_token", "t", "services", List.of(service)));
      assertEquals(gw.baseUrl, skill.getGlobalData().get("mcp_gateway_url"));
      assertTrue(skill.getHints().contains("MCP"));
      assertTrue(skill.getHints().contains("calc"));
      assertFalse(skill.getPromptSections().isEmpty());
    }
  }

  private static boolean readVerifySsl(McpGatewaySkill skill) throws Exception {
    Field f = McpGatewaySkill.class.getDeclaredField("verifySsl");
    f.setAccessible(true);
    return f.getBoolean(skill);
  }
}
