/**
 * MCP Gateway Demo.
 *
 * Connects a SignalWire AI agent to MCP (Model Context Protocol) servers
 * through the mcp_gateway skill. The gateway bridges MCP tools so the
 * agent can use them as SWAIG functions.
 *
 * Prerequisites:
 *   Start an MCP gateway server: mcp-gateway -c config.json
 *
 * Env vars:
 *   MCP_GATEWAY_URL          - URL of the running gateway (default: http://localhost:8080)
 *   MCP_GATEWAY_AUTH_USER    - Basic auth username
 *   MCP_GATEWAY_AUTH_PASSWORD - Basic auth password
 */

import com.signalwire.sdk.agent.AgentBase;

import java.util.List;
import java.util.Map;

public class McpGatewayDemo {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("mcp-gateway-agent")
                .route("/mcp-gateway")
                .port(3000)
                .build();

        agent.addLanguage("English", "en-US", "en-US-Standard-C");

        agent.promptAddSection("Role",
                "You are a helpful assistant with access to external tools " +
                "provided through MCP servers. Use the available tools to help " +
                "users accomplish their tasks.");

        // Connect to MCP gateway -- tools are discovered automatically
        agent.addSkill("mcp_gateway", Map.of(
                "gateway_url", envOr("MCP_GATEWAY_URL", "http://localhost:8080"),
                "auth_user", envOr("MCP_GATEWAY_AUTH_USER", "admin"),
                "auth_password", envOr("MCP_GATEWAY_AUTH_PASSWORD", "changeme"),
                "services", List.of(Map.of("name", "todo"))
        ));

        System.out.println("Starting MCP Gateway agent on port 3000...");
        agent.run();
    }

    private static String envOr(String key, String fallback) {
        String val = System.getenv(key);
        return (val != null && !val.isEmpty()) ? val : fallback;
    }
}
