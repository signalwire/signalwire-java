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
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * MCP Gateway skill — bridge a running MCP (Model Context Protocol) gateway with SWAIG functions.
 *
 * <p>This is the CLIENT half of the mcp_gateway subsystem. It connects to an already-running MCP
 * Gateway service over HTTP, authenticates (bearer token OR HTTP-basic), enumerates the gateway's
 * services and their tools, and registers each MCP tool as a SWAIG function whose handler proxies
 * the tool call back through the gateway. Mirrors the Python reference {@code MCPGatewaySkill}
 * ({@code signalwire/skills/mcp_gateway/skill.py}).
 *
 * <p><b>Server-half is Python-only.</b> The gateway SERVER (the process that hosts the MCP servers
 * and exposes the {@code /services} / {@code /call} HTTP surface) is NOT ported to Java — see
 * {@code porting-sdk/PORT_PHILOSOPHY_JAVA.md} ("mcp_gateway: client-half only"). Java ships the
 * client that talks to a Python-hosted (or any spec-compatible) gateway; it does not host one.
 *
 * <p><b>TLS verification ({@code verify_ssl}).</b> Config param, default {@code true} (verification
 * ON — the secure default, matching the Python reference {@code verify=self.verify_ssl}). Setting
 * {@code verify_ssl=false} is the explicit opt-out for self-signed-cert gateways: only then is a
 * permissive {@link SSLContext} installed on the {@link HttpClient}. Every request is issued
 * through {@link #httpClient()}, so the flag governs the actual peer-certificate check, not just a
 * stored boolean.
 */
public class McpGatewaySkill implements SkillBase {

  private static final Logger log = Logger.getLogger(McpGatewaySkill.class);
  private static final Gson GSON = new Gson();

  private String gatewayUrl;
  private String authToken;
  private String authUser;
  private String authPassword;
  private List<Map<String, Object>> services = new ArrayList<>();
  private int sessionTimeout = 300;
  private String toolPrefix = "mcp_";
  private int retryAttempts = 3;
  private int requestTimeout = 30;
  private boolean verifySsl = true;

  // Session id is set from call_id when the first tool is used (mirrors the
  // Python reference's self.session_id = None at setup).
  private String sessionId;

  @Override
  public String getName() {
    return "mcp_gateway";
  }

  @Override
  public String getDescription() {
    return "Bridge MCP servers with SWAIG functions";
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public List<String> getRequiredPackages() {
    // Java resolves HTTP/JSON deps at build time; informational only.
    return Collections.emptyList();
  }

  /**
   * Validate configuration and probe the gateway.
   *
   * <p>Auth is either a bearer token ({@code auth_token}) OR HTTP-basic ({@code auth_user} + {@code
   * auth_password}); {@code gateway_url} is always required. Reads {@code verify_ssl} (default
   * {@code true}) and threads it to the HTTP client. Finally performs a {@code GET /health} to
   * confirm the gateway is reachable, exactly like the Python reference.
   */
  @Override
  @SuppressWarnings("unchecked")
  public boolean setup(Map<String, Object> params) {
    this.authToken = str(params.get("auth_token"));

    if (authToken == null || authToken.isEmpty()) {
      // No token → require basic auth credentials + gateway_url.
      List<String> missing = new ArrayList<>();
      for (String required : List.of("gateway_url", "auth_user", "auth_password")) {
        Object v = params.get(required);
        if (v == null || v.toString().isEmpty()) {
          missing.add(required);
        }
      }
      if (!missing.isEmpty()) {
        log.error("Missing required parameters: %s", missing);
        return false;
      }
      this.authUser = str(params.get("auth_user"));
      this.authPassword = str(params.get("auth_password"));
    } else {
      // Token auth → only gateway_url is required.
      Object url = params.get("gateway_url");
      if (url == null || url.toString().isEmpty()) {
        log.error("Missing required parameter: gateway_url");
        return false;
      }
    }

    String url = str(params.get("gateway_url"));
    // rstrip('/') parity.
    while (url != null && url.endsWith("/")) {
      url = url.substring(0, url.length() - 1);
    }
    this.gatewayUrl = url;

    Object svc = params.get("services");
    if (svc instanceof List) {
      this.services = new ArrayList<>();
      for (Object o : (List<Object>) svc) {
        if (o instanceof Map) {
          this.services.add((Map<String, Object>) o);
        }
      }
    }
    if (params.containsKey("session_timeout")) {
      this.sessionTimeout = ((Number) params.get("session_timeout")).intValue();
    }
    if (params.containsKey("tool_prefix")) {
      this.toolPrefix = str(params.get("tool_prefix"));
    }
    if (params.containsKey("retry_attempts")) {
      this.retryAttempts = ((Number) params.get("retry_attempts")).intValue();
    }
    if (params.containsKey("request_timeout")) {
      this.requestTimeout = ((Number) params.get("request_timeout")).intValue();
    }
    // verify_ssl: default TRUE (secure). Only an explicit false opts out.
    if (params.containsKey("verify_ssl")) {
      this.verifySsl = toBool(params.get("verify_ssl"), true);
    }

    this.sessionId = null;

    // Validate gateway connection with a health probe.
    try {
      HttpResponse<String> resp = request("GET", gatewayUrl + "/health", null);
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        log.error("Failed to connect to gateway: HTTP %d", resp.statusCode());
        return false;
      }
      log.info("Connected to MCP Gateway at %s", gatewayUrl);
    } catch (Exception e) {
      log.error("Failed to connect to gateway: %s", e.getMessage());
      return false;
    }
    return true;
  }

  /**
   * Enumerate the gateway's services + tools and register each MCP tool as a SWAIG function.
   *
   * <p>If no services were configured, GETs {@code /services} for the full list; then for each
   * service GETs {@code /services/{name}/tools} and registers every (optionally filtered) tool.
   */
  @Override
  @SuppressWarnings("unchecked")
  public List<ToolDefinition> registerTools() {
    List<ToolDefinition> tools = new ArrayList<>();

    // Discover all services when none were specified.
    if (services.isEmpty()) {
      try {
        HttpResponse<String> resp = request("GET", gatewayUrl + "/services", null);
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
          List<String> all = GSON.fromJson(resp.body(), new TypeToken<List<String>>() {}.getType());
          if (all != null) {
            for (String name : all) {
              Map<String, Object> entry = new LinkedHashMap<>();
              entry.put("name", name);
              services.add(entry);
            }
          }
        } else {
          log.error("Failed to list services: HTTP %d", resp.statusCode());
        }
      } catch (Exception e) {
        log.error("Failed to list services: %s", e.getMessage());
      }
    }

    for (Map<String, Object> serviceConfig : services) {
      String serviceName = str(serviceConfig.get("name"));
      if (serviceName == null || serviceName.isEmpty()) {
        continue;
      }
      try {
        HttpResponse<String> resp =
            request("GET", gatewayUrl + "/services/" + serviceName + "/tools", null);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
          log.error(
              "Failed to get tools for service '%s': HTTP %d", serviceName, resp.statusCode());
          continue;
        }
        Map<String, Object> toolsData =
            GSON.fromJson(resp.body(), new TypeToken<Map<String, Object>>() {}.getType());
        List<Map<String, Object>> toolList =
            toolsData == null ? null : (List<Map<String, Object>>) toolsData.get("tools");
        if (toolList == null) {
          continue;
        }

        // Filter tools if a specific list was configured ('*' = all).
        Object toolFilter = serviceConfig.getOrDefault("tools", "*");
        if (toolFilter instanceof List) {
          List<Object> allowed = (List<Object>) toolFilter;
          toolList.removeIf(t -> !allowed.contains(t.get("name")));
        }

        for (Map<String, Object> tool : toolList) {
          ToolDefinition td = buildMcpTool(serviceName, tool);
          if (td != null) {
            tools.add(td);
          }
        }
      } catch (Exception e) {
        log.error("Failed to get tools for service '%s': %s", serviceName, e.getMessage());
      }
    }

    // Register the hangup hook for session cleanup. is_hangup_hook is carried as
    // an extra field so it round-trips into the SWAIG function definition.
    Map<String, Object> emptyParams = new LinkedHashMap<>();
    emptyParams.put("type", "object");
    emptyParams.put("properties", new LinkedHashMap<String, Object>());
    ToolDefinition hangup =
        new ToolDefinition(
            "_mcp_gateway_hangup",
            "Internal cleanup function for MCP sessions",
            emptyParams,
            this::hangupHandler);
    Map<String, Object> hangupExtra = new LinkedHashMap<>();
    hangupExtra.put("is_hangup_hook", true);
    hangup.setExtraFields(hangupExtra);
    tools.add(hangup);

    return tools;
  }

  /** Build one SWAIG {@link ToolDefinition} from an MCP tool definition. */
  @SuppressWarnings("unchecked")
  private ToolDefinition buildMcpTool(String serviceName, Map<String, Object> toolDef) {
    String toolName = str(toolDef.get("name"));
    if (toolName == null || toolName.isEmpty()) {
      return null;
    }
    String swaigName = toolPrefix + serviceName + "_" + toolName;

    Map<String, Object> inputSchema =
        toolDef.get("inputSchema") instanceof Map
            ? (Map<String, Object>) toolDef.get("inputSchema")
            : Collections.emptyMap();
    Map<String, Object> properties =
        inputSchema.get("properties") instanceof Map
            ? (Map<String, Object>) inputSchema.get("properties")
            : Collections.emptyMap();
    List<Object> requiredList =
        inputSchema.get("required") instanceof List
            ? (List<Object>) inputSchema.get("required")
            : Collections.emptyList();

    Map<String, Object> swaigProps = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : properties.entrySet()) {
      Map<String, Object> propDef =
          e.getValue() instanceof Map ? (Map<String, Object>) e.getValue() : Map.of();
      Map<String, Object> paramDef = new LinkedHashMap<>();
      paramDef.put("type", propDef.getOrDefault("type", "string"));
      paramDef.put("description", propDef.getOrDefault("description", ""));
      if (propDef.containsKey("enum")) {
        paramDef.put("enum", propDef.get("enum"));
      }
      if (propDef.containsKey("default") && !requiredList.contains(e.getKey())) {
        paramDef.put("default", propDef.get("default"));
      }
      swaigProps.put(e.getKey(), paramDef);
    }

    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("type", "object");
    parameters.put("properties", swaigProps);
    if (!requiredList.isEmpty()) {
      parameters.put("required", requiredList);
    }

    String description = "[" + serviceName + "] " + toolDef.getOrDefault("description", toolName);

    ToolDefinition td =
        new ToolDefinition(
            swaigName,
            description,
            parameters,
            (args, rawData) -> callMcpTool(serviceName, toolName, args, rawData));
    log.info("Registered SWAIG function: %s", swaigName);
    return td;
  }

  /** Proxy an MCP tool call back through the gateway, with retry on 5xx. */
  @SuppressWarnings("unchecked")
  private FunctionResult callMcpTool(
      String serviceName, String toolName, Map<String, Object> args, Map<String, Object> rawData) {
    String session = resolveSessionId(rawData);

    Map<String, Object> requestData = new LinkedHashMap<>();
    requestData.put("tool", toolName);
    requestData.put("arguments", args == null ? Collections.emptyMap() : args);
    requestData.put("session_id", session);
    requestData.put("timeout", sessionTimeout);
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("timestamp", rawData == null ? null : rawData.get("timestamp"));
    metadata.put("call_id", rawData == null ? null : rawData.get("call_id"));
    requestData.put("metadata", metadata);

    String body = GSON.toJson(requestData);
    String lastError = null;

    for (int attempt = 0; attempt < Math.max(1, retryAttempts); attempt++) {
      try {
        HttpResponse<String> resp =
            request("POST", gatewayUrl + "/services/" + serviceName + "/call", body);
        if (resp.statusCode() == 200) {
          Map<String, Object> result =
              GSON.fromJson(resp.body(), new TypeToken<Map<String, Object>>() {}.getType());
          Object text = result == null ? null : result.get("result");
          return new FunctionResult(text == null ? "No response" : text.toString());
        }
        String errorMsg;
        try {
          Map<String, Object> errData =
              GSON.fromJson(resp.body(), new TypeToken<Map<String, Object>>() {}.getType());
          errorMsg =
              errData != null && errData.get("error") != null
                  ? errData.get("error").toString()
                  : "HTTP " + resp.statusCode();
        } catch (Exception parse) {
          errorMsg = "HTTP " + resp.statusCode();
        }
        lastError = errorMsg;
        if (resp.statusCode() >= 500) {
          log.warn("Gateway error (attempt %d): %s", attempt + 1, errorMsg);
          continue; // retry server errors
        }
        break; // client error: don't retry
      } catch (Exception e) {
        lastError = e.getMessage();
        log.warn("Error calling MCP tool (attempt %d): %s", attempt + 1, e.getMessage());
      }
    }

    String errorMsg = "Failed to call " + serviceName + "." + toolName + ": " + lastError;
    log.error(errorMsg);
    return new FunctionResult(errorMsg);
  }

  /** Hangup hook: delete the gateway session for this call. */
  private FunctionResult hangupHandler(Map<String, Object> args, Map<String, Object> rawData) {
    String session = resolveSessionId(rawData);
    try {
      HttpResponse<String> resp = request("DELETE", gatewayUrl + "/sessions/" + session, null);
      if (resp.statusCode() == 200 || resp.statusCode() == 404) {
        log.info("Cleaned up MCP session: %s", session);
      } else {
        log.warn("Failed to cleanup session: HTTP %d", resp.statusCode());
      }
    } catch (Exception e) {
      log.error("Error cleaning up session: %s", e.getMessage());
    }
    return new FunctionResult("Session cleanup complete");
  }

  /** mcp_call_id in global_data wins over the top-level call_id (Python parity). */
  @SuppressWarnings("unchecked")
  private String resolveSessionId(Map<String, Object> rawData) {
    if (rawData != null && rawData.get("global_data") instanceof Map) {
      Map<String, Object> gd = (Map<String, Object>) rawData.get("global_data");
      Object v = gd.get("mcp_call_id");
      if (v != null) {
        return v.toString();
      }
    }
    Object callId = rawData == null ? null : rawData.get("call_id");
    return callId == null ? "unknown" : callId.toString();
  }

  @Override
  public List<String> getHints() {
    List<String> hints = new ArrayList<>();
    hints.add("MCP");
    hints.add("gateway");
    for (Map<String, Object> service : services) {
      Object name = service.get("name");
      if (name != null) {
        hints.add(name.toString());
      }
    }
    return hints;
  }

  @Override
  public Map<String, Object> getGlobalData() {
    List<String> serviceNames = new ArrayList<>();
    for (Map<String, Object> s : services) {
      Object name = s.get("name");
      serviceNames.add(name == null ? String.valueOf(s) : name.toString());
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("mcp_gateway_url", gatewayUrl);
    data.put("mcp_session_id", sessionId);
    data.put("mcp_services", serviceNames);
    return data;
  }

  @Override
  public List<Map<String, Object>> getPromptSections() {
    List<String> descriptions = new ArrayList<>();
    for (Map<String, Object> service : services) {
      String name = str(service.getOrDefault("name", "Unknown"));
      Object tools = service.getOrDefault("tools", "*");
      if ("*".equals(tools)) {
        descriptions.add(name + " (all tools)");
      } else if (tools instanceof List) {
        descriptions.add(name + " (" + ((List<?>) tools).size() + " tools)");
      }
    }
    if (descriptions.isEmpty()) {
      return Collections.emptyList();
    }
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("title", "MCP Gateway Integration");
    section.put(
        "body",
        "You have access to external MCP (Model Context Protocol) services through a gateway.");
    section.put(
        "bullets",
        List.of(
            "Connected to gateway at " + gatewayUrl,
            "Available services: " + String.join(", ", descriptions),
            "Functions are prefixed with '" + toolPrefix + "' followed by service name",
            "Each service maintains its own session state throughout the call"));
    return List.of(section);
  }

  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("gateway_url", entry("string", "URL of the MCP Gateway service", null, true));
    schema.put(
        "auth_token",
        hidden(
            "string",
            "Bearer token for authentication (alternative to basic auth)",
            "MCP_GATEWAY_AUTH_TOKEN"));
    schema.put(
        "auth_user",
        envEntry(
            "string",
            "Username for basic authentication (required if auth_token not provided)",
            "MCP_GATEWAY_AUTH_USER"));
    schema.put(
        "auth_password",
        hidden(
            "string",
            "Password for basic authentication (required if auth_token not provided)",
            "MCP_GATEWAY_AUTH_PASSWORD"));
    schema.put(
        "services",
        entry(
            "array",
            "List of MCP services to connect to (empty for all available)",
            new ArrayList<>(),
            false));
    schema.put("session_timeout", entry("integer", "Session timeout in seconds", 300, false));
    schema.put(
        "tool_prefix",
        entry("string", "Prefix for registered SWAIG function names", "mcp_", false));
    schema.put(
        "retry_attempts",
        entry("integer", "Number of retry attempts for failed requests", 3, false));
    schema.put("request_timeout", entry("integer", "Request timeout in seconds", 30, false));
    schema.put("verify_ssl", entry("boolean", "Verify SSL certificates", true, false));
    return schema;
  }

  // ---- HTTP + auth + verify_ssl wiring -------------------------------------

  /**
   * Issue an authenticated HTTP request through a client whose TLS peer verification is governed by
   * {@link #verifySsl}. When {@code verifySsl} is false a permissive {@link SSLContext} is
   * installed so self-signed gateways can be reached; when true (the default) the standard
   * verifying client is used.
   */
  private HttpResponse<String> request(String method, String url, String jsonBody)
      throws Exception {
    HttpRequest.Builder rb =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(Math.max(1, requestTimeout)));

    // Auth: bearer token wins; otherwise HTTP-basic.
    if (authToken != null && !authToken.isEmpty()) {
      rb.header("Authorization", "Bearer " + authToken);
    } else if (authUser != null) {
      String creds = authUser + ":" + (authPassword == null ? "" : authPassword);
      String basic = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
      rb.header("Authorization", "Basic " + basic);
    }

    if (jsonBody != null) {
      rb.header("Content-Type", "application/json");
      rb.method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
    } else if ("GET".equals(method)) {
      rb.GET();
    } else {
      rb.method(method, HttpRequest.BodyPublishers.noBody());
    }

    return httpClient().send(rb.build(), HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Build the {@link HttpClient}. verify_ssl=true (default) → the standard, fully-verifying client.
   * verify_ssl=false → a client with an all-trusting {@link SSLContext}, the explicit self-signed
   * opt-out. The insecure branch is reached ONLY when the operator set {@code verify_ssl=false}.
   */
  private HttpClient httpClient() throws Exception {
    HttpClient.Builder b =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.max(1, requestTimeout)));
    if (!verifySsl) {
      // Explicit opt-out ONLY (verify_ssl defaults true = secure). Install the
      // permissive trust manager so self-signed gateways are reachable. This is
      // the mcp_gateway verify_ssl idiom the python reference endorses via
      // verify=self.verify_ssl. The trust-all construction lives inside this
      // `if (!verifySsl)` guard so the TLS-VERIFY gate recognizes the site as the
      // secure-default opt-out, not an ungated leak.
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, new TrustManager[] {new InsecureTrustManager()}, new SecureRandom());
      b.sslContext(ctx);
    }
    return b.build();
  }

  /**
   * The all-trusting {@link X509TrustManager} used ONLY on the {@code verify_ssl=false} opt-out
   * path (constructed inside the {@code if (!verifySsl)} guard in {@link #httpClient()}). A named
   * nested class rather than an inline anonymous one so the surface enumerator scopes its interface
   * methods to THIS class, not the public {@code MCPGatewaySkill} surface. This deliberately-empty
   * trust manager is the self-signed opt-out the python reference endorses via {@code
   * verify=self.verify_ssl}; it is allowlisted in {@code TLS_VERIFY_ALLOW.md} as a
   * secure-default-gated site (the only legitimate allowlist reason per the TLS-VERIFY gate).
   */
  private static final class InsecureTrustManager implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }

  // ---- helpers -------------------------------------------------------------

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }

  private static boolean toBool(Object v, boolean dflt) {
    if (v == null) {
      return dflt;
    }
    if (v instanceof Boolean b) {
      return b;
    }
    return Boolean.parseBoolean(v.toString());
  }

  private static Map<String, Object> entry(
      String type, String description, Object dflt, boolean required) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", type);
    m.put("description", description);
    if (dflt != null) {
      m.put("default", dflt);
    }
    m.put("required", required);
    return m;
  }

  private static Map<String, Object> hidden(String type, String description, String envVar) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", type);
    m.put("description", description);
    m.put("required", false);
    m.put("hidden", true);
    m.put("env_var", envVar);
    return m;
  }

  private static Map<String, Object> envEntry(String type, String description, String envVar) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", type);
    m.put("description", description);
    m.put("required", false);
    m.put("env_var", envVar);
    return m;
  }
}
