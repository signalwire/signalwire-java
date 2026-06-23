# Getting Started with the REST Client

The REST client provides synchronous access to all SignalWire APIs using standard HTTP requests. No WebSocket connection required.

## Installation

The REST client ships in the single `com.signalwire:signalwire-sdk` artifact (requires Java 21+):

```groovy
implementation 'com.signalwire:signalwire-sdk:2.0.2'
```

## Configuration

You need three things to connect:

| Builder method | Env Var | Description |
|----------------|---------|-------------|
| `.project(...)` | `SIGNALWIRE_PROJECT_ID` | Your SignalWire project ID |
| `.token(...)` | `SIGNALWIRE_API_TOKEN` | Your SignalWire API token |
| `.space(...)` | `SIGNALWIRE_SPACE` | Your space hostname (e.g. `example.signalwire.com`) |

## Minimal Example

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

Or use environment variables and skip the constructor args:

```bash
export SIGNALWIRE_PROJECT_ID=your-project-id
export SIGNALWIRE_API_TOKEN=your-api-token
export SIGNALWIRE_SPACE=example.signalwire.com
```

```python
from signalwire_agents.rest import RestClient

client = RestClient()
agents = client.fabric.ai_agents.list()
```

## CRUD Pattern

Most resources follow the same CRUD pattern:

```python
# List
items = client.fabric.ai_agents.list()

# Create
agent = client.fabric.ai_agents.create(name="Support", prompt={"text": "Be helpful"})

# Get by ID
agent = client.fabric.ai_agents.get("agent-uuid")

# Update
client.fabric.ai_agents.update("agent-uuid", name="Updated Name")

# Delete
client.fabric.ai_agents.delete("agent-uuid")
```

Fabric resources also support listing addresses:

```python
addresses = client.fabric.ai_agents.list_addresses("agent-uuid")
```

## Error Handling

```python
from signalwire_agents.rest import RestClient, RestError

client = RestClient()

try:
    agent = client.fabric.ai_agents.get("nonexistent-id")
except RestError as e:
    print(f"HTTP {e.status_code}: {e.body}")
    # HTTP 404: {'error': 'not found'}
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
