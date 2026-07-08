# MCP Integration

<!-- snippet-setup -->
```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
```

The SDK supports the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) in two ways:

1. **MCP Client** -- Connect to external MCP servers and use their tools in your agent
2. **MCP Server** -- Expose your agent's tools as an MCP endpoint for other clients

These features are independent and can be used separately or together.

## Adding External MCP Servers

Use `addMcpServer()` to connect your agent to remote MCP servers. Tools are discovered at call start via the MCP protocol and added to the AI's tool list alongside your defined tools.

```java
var agent = AgentBase.builder()
        .name("my-agent")
        .route("/agent")
        .build();

agent.addMcpServer(
    "https://mcp.example.com/tools",
    Map.of("Authorization", "Bearer sk-xxx")
);
```

### Parameters

| Parameter | Type | Description |
|---|---|---|
| `url` | String | MCP server HTTP endpoint URL |
| `headers` | Map<String, String> | Optional HTTP headers for authentication |
| `resources` | boolean | Fetch resources into `global_data` (default: false) |
| `resourceVars` | Map<String, String> | Variables for URI template substitution |

### With Resources

MCP servers can expose read-only data as resources. When enabled, resources are fetched at session start and merged into `global_data`:

```java
var agent = AgentBase.builder().name("my-agent").route("/agent").build();

agent.addMcpServer(
    "https://mcp.example.com/crm",
    Map.of("Authorization", "Bearer sk-xxx"),
    true,
    Map.of("caller_id", "${caller_id_number}")
);
```

Resource data is available in prompts via `${global_data.key}` and included in every webhook call.

### Multiple Servers

```java
var agent = AgentBase.builder().name("my-agent").route("/agent").build();

agent.addMcpServer("https://mcp-search.example.com/tools",
    Map.of("Authorization", "Bearer search-key"));
agent.addMcpServer("https://mcp-crm.example.com/tools",
    Map.of("Authorization", "Bearer crm-key"));
```

Tools from all servers are merged into one list.

## Exposing Tools as MCP Server

Use `enableMcpServer()` to add an MCP endpoint at `/mcp` on your agent's server. Any MCP client can connect and use your tools.

```java
var agent = AgentBase.builder()
        .name("my-agent")
        .route("/agent")
        .build();
agent.enableMcpServer();

agent.defineTool("get_weather", "Get weather for a location",
    Map.of("type", "object", "properties",
        Map.of("location", Map.of("type", "string", "description", "City name"))),
    (args, raw) -> new FunctionResult("72F sunny in " + args.get("location")));
```

The `/mcp` endpoint handles the full MCP protocol:
- `initialize` -- protocol version and capability negotiation
- `notifications/initialized` -- ready signal
- `tools/list` -- returns all tools in MCP format
- `tools/call` -- invokes the handler and returns the result
- `ping` -- keepalive

### Connecting from Claude Desktop

Add your agent as an MCP server in Claude Desktop's config:

```json
{
    "mcpServers": {
        "my-agent": {
            "url": "https://your-server.com/agent/mcp"
        }
    }
}
```

## Using Both Together

The two features are independent:

```java
var agent = AgentBase.builder().name("my-agent").route("/agent").build();

agent.enableMcpServer();
agent.addMcpServer("https://mcp.example.com/crm",
    Map.of("Authorization", "Bearer sk-xxx"),
    true, null);
```

In this setup:
- Voice calls use local tools via SWAIG webhook + CRM tools via MCP
- Claude Desktop uses local tools via MCP endpoint
- The same tool code serves both protocols

## MCP vs SWAIG Webhooks

| | SWAIG Webhooks | MCP Tools |
|---|---|---|
| Response format | JSON with `response`, `action`, `SWML` | Text content only |
| Call control | Can trigger hold, transfer, SWML | Response only |
| Discovery | Defined in SWML config | Auto-discovered via protocol |
| Auth | `web_hook_auth_user/password` | `headers` dict |

MCP tools are best for data retrieval. Use tool handlers with SWAIG webhooks when you need call control actions.
