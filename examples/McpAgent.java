/**
 * Example: MCP Integration -- Client and Server
 *
 * <p>This agent demonstrates both MCP features:
 *
 * <p>1. MCP Server: Exposes tools at /mcp so external MCP clients (Claude Desktop, other agents)
 * can discover and invoke them.
 *
 * <p>2. MCP Client: Connects to an external MCP server to pull in additional tools for voice calls.
 *
 * <p>Run: java McpAgent
 *
 * <p>Then: - Point a SignalWire phone number at http://your-server:3000/agent - Connect Claude
 * Desktop to http://your-server:3000/agent/mcp
 */
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import java.util.Map;

public class McpAgent {

  public static void main(String[] args) throws Exception {
    var agent = AgentBase.builder().name("mcp-agent").route("/agent").port(3000).build();

    // -- MCP Server --
    // Adds a /mcp endpoint that speaks JSON-RPC 2.0 (MCP protocol).
    agent.enableMcpServer();

    // -- MCP Client --
    // Connect to an external MCP server. Tools are discovered
    // automatically at call start.
    agent.addMcpServer(
        "https://mcp.example.com/tools", Map.of("Authorization", "Bearer sk-your-mcp-api-key"));

    // -- MCP Client with Resources --
    agent.addMcpServer(
        "https://mcp.example.com/crm",
        Map.of("Authorization", "Bearer sk-your-crm-key"),
        true,
        Map.of("caller_id", "${caller_id_number}", "tenant", "acme-corp"));

    // -- Agent Configuration --
    agent.promptAddSection(
        "Role",
        "You are a helpful customer support agent. "
            + "You have access to the customer's profile via global_data.");

    agent.setParams(Map.of("attention_timeout", 15000));

    // -- Local Tools --
    agent.defineTool(
        "get_weather",
        "Get the current weather for a location",
        Map.of(
            "type",
            "object",
            "properties",
            Map.of("location", Map.of("type", "string", "description", "City name or zip code"))),
        (toolArgs, rawData) -> {
          String location = (String) toolArgs.getOrDefault("location", "unknown");
          return new FunctionResult("Currently 72F and sunny in " + location + ".");
        });

    agent.defineTool(
        "create_ticket",
        "Create a support ticket",
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "subject", Map.of("type", "string", "description", "Ticket subject"),
                "description", Map.of("type", "string", "description", "Issue description"))),
        (toolArgs, rawData) -> {
          String subject = (String) toolArgs.getOrDefault("subject", "No subject");
          return new FunctionResult("Ticket created: '" + subject + "'. Reference: TK-12345.");
        });

    agent.run();
  }
}
