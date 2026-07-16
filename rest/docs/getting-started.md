# Getting Started with the REST Client

The REST client provides synchronous access to all SignalWire APIs using standard HTTP requests. No WebSocket connection required.

## Installation

The REST client ships in the single `com.signalwire:signalwire-sdk` artifact (requires Java 21+):

```groovy
implementation 'com.signalwire:signalwire-sdk:4.0.0'
```

## Configuration

You need three things to connect:

| Builder method | Env Var | Description |
|----------------|---------|-------------|
| `.project(...)` | `SIGNALWIRE_PROJECT_ID` | Your SignalWire project ID |
| `.token(...)` | `SIGNALWIRE_API_TOKEN` | Your SignalWire API token |
| `.space(...)` | `SIGNALWIRE_SPACE` | Your space hostname (e.g. `example.signalwire.com`) |

## Minimal Example

<!-- snippet-setup -->
```java
import com.signalwire.sdk.rest.RestClient;

var client = RestClient.builder()
        .project("your-project-id")
        .token("your-api-token")
        .space("example.signalwire.com")
        .build();

// List your AI agents
var agents = client.fabric().aiAgents().list();
System.out.println(agents);
```

Or use environment variables and skip the credential setters (the builder falls back to them):

```bash
export SIGNALWIRE_PROJECT_ID=your-project-id
export SIGNALWIRE_API_TOKEN=your-api-token
export SIGNALWIRE_SPACE=example.signalwire.com
```

```java
// Environment-only construction: reads SIGNALWIRE_PROJECT_ID / _API_TOKEN / _SPACE.
// var client = RestClient.builder().build();
var envAgents = client.fabric().aiAgents().list();
```

## CRUD Pattern

Most resources follow the same CRUD pattern. Requests and responses are
`Map<String, Object>`:

```java
import java.util.Map;

// List
var items = client.fabric().aiAgents().list();

// Create
var agent = client.fabric().aiAgents().create(Map.of(
    "name", "Support",
    "prompt", Map.of("text", "Be helpful")));

// Get by ID
var fetched = client.fabric().aiAgents().get("agent-uuid");

// Update
client.fabric().aiAgents().update("agent-uuid", Map.of("name", "Updated Name"));

// Delete
client.fabric().aiAgents().delete("agent-uuid");
```

Fabric resources also support listing addresses:

```java
var addresses = client.fabric().aiAgents().listAddresses("agent-uuid", Map.of());
```

## Error Handling

```java
import com.signalwire.sdk.rest.RestError;

try {
    var agent = client.fabric().aiAgents().get("nonexistent-id");
} catch (RestError e) {
    System.out.println("HTTP " + e.getStatusCode() + ": " + e.getResponseBody());
    // HTTP 404: {"error": "not found"}
}
```

## Debug Logging

Set the log level to see HTTP request details:

```bash
export SIGNALWIRE_LOG_LEVEL=debug
```

## Next Steps

- [Client Reference](client-reference.md) -- all namespaces and constructor options
- [Fabric Resources](fabric.md) -- managing AI agents, SWML scripts, and more
- [Calling Commands](calling.md) -- REST-based call control
- [All Namespaces](namespaces.md) -- phone numbers, video, datasphere, and more
