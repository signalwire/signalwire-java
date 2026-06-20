package com.signalwire.sdk.skills.builtin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Bridge MCP (Model Context Protocol) servers with SWAIG functions. */
public class McpGatewaySkill implements SkillBase {

  private static final Logger log = Logger.getLogger(McpGatewaySkill.class);

  private String gatewayUrl;
  private String authToken;
  private String authUser;
  private String authPassword;
  private String toolPrefix = "mcp_";
  private int requestTimeout = 30;
  private List<Map<String, Object>> services = new ArrayList<>();
  private final List<ToolDefinition> discoveredTools = new ArrayList<>();
  private final List<String> serviceNames = new ArrayList<>();

  @Override
  public String getName() {
    return "mcp_gateway";
  }

  @Override
  public String getDescription() {
    return "Bridge MCP servers with SWAIG functions";
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean setup(Map<String, Object> params) {
    this.gatewayUrl = (String) params.get("gateway_url");
    if (params.containsKey("auth_token")) this.authToken = (String) params.get("auth_token");
    if (params.containsKey("auth_user")) this.authUser = (String) params.get("auth_user");
    if (params.containsKey("auth_password"))
      this.authPassword = (String) params.get("auth_password");
    if (params.containsKey("tool_prefix")) this.toolPrefix = (String) params.get("tool_prefix");
    if (params.containsKey("request_timeout"))
      this.requestTimeout = ((Number) params.get("request_timeout")).intValue();
    if (params.containsKey("services"))
      this.services = (List<Map<String, Object>>) params.get("services");

    if (gatewayUrl == null || gatewayUrl.isEmpty()) {
      log.error("mcp_gateway requires 'gateway_url' parameter");
      return false;
    }

    // Discover tools from services
    for (Map<String, Object> service : services) {
      String serviceName = (String) service.get("name");
      if (serviceName == null) continue;
      serviceNames.add(serviceName);

      List<Map<String, Object>> serviceTools = (List<Map<String, Object>>) service.get("tools");
      if (serviceTools == null) continue;

      for (Map<String, Object> tool : serviceTools) {
        String toolName = toolPrefix + serviceName + "_" + tool.get("name");
        String toolDesc = "[" + serviceName + "] " + tool.getOrDefault("description", "MCP tool");

        Map<String, Object> toolParams =
            (Map<String, Object>)
                tool.getOrDefault("parameters", Map.of("type", "object", "properties", Map.of()));

        final String svcName = serviceName;
        final String origToolName = (String) tool.get("name");

        discoveredTools.add(
            new ToolDefinition(
                toolName,
                toolDesc.toString(),
                toolParams,
                (args, raw) -> callMcpTool(svcName, origToolName, args)));
      }
    }

    return true;
  }

  @SuppressWarnings("unchecked")
  private FunctionResult callMcpTool(
      String serviceName, String toolName, Map<String, Object> args) {
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("service", serviceName);
      body.put("tool", toolName);
      body.put("arguments", args);

      HttpRequest.Builder reqBuilder =
          HttpRequest.newBuilder()
              .uri(URI.create(gatewayUrl + "/execute"))
              .header("Content-Type", "application/json")
              .timeout(Duration.ofSeconds(requestTimeout))
              .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)));

      if (authToken != null) {
        reqBuilder.header("Authorization", "Bearer " + authToken);
      } else if (authUser != null && authPassword != null) {
        String auth =
            Base64.getEncoder()
                .encodeToString((authUser + ":" + authPassword).getBytes(StandardCharsets.UTF_8));
        reqBuilder.header("Authorization", "Basic " + auth);
      }

      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> response =
          client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        return new FunctionResult("MCP tool call failed: " + response.statusCode());
      }

      Map<String, Object> result =
          new Gson().fromJson(response.body(), new TypeToken<Map<String, Object>>() {}.getType());
      String resultText = result.getOrDefault("result", result.toString()).toString();
      return new FunctionResult(resultText);

    } catch (Exception e) {
      log.error("MCP gateway error", e);
      return new FunctionResult("Error calling MCP tool: " + e.getMessage());
    }
  }

  @Override
  public List<ToolDefinition> registerTools() {
    return discoveredTools;
  }

  @Override
  public List<String> getHints() {
    List<String> hints = new ArrayList<>(List.of("MCP", "gateway"));
    hints.addAll(serviceNames);
    return hints;
  }

  @Override
  public List<Map<String, Object>> getPromptSections() {
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("title", "MCP Gateway Integration");
    section.put("body", "Connected MCP services: " + String.join(", ", serviceNames));
    return List.of(section);
  }

  @Override
  public Map<String, Object> getGlobalData() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("mcp_gateway_url", gatewayUrl);
    data.put("mcp_session_id", null);
    data.put("mcp_services", serviceNames);
    return data;
  }
}
