# SignalWire REST Client (Java)

Synchronous REST client for managing SignalWire resources, controlling live calls, and interacting with every SignalWire API surface from Java. No WebSocket required -- just standard HTTP requests with automatic connection pooling.

## Quick Start

```java
import com.signalwire.agents.rest.SignalWireClient;

var client = SignalWireClient.builder()
    .project("your-project-id")
    .token("your-api-token")
    .space("example.signalwire.com")
    .build();

// Create an AI agent
var agent = client.fabric().aiAgents().create(Map.of(
    "name", "Support Bot",
    "prompt", Map.of("text", "You are a helpful support agent.")
));

// Search for a phone number
var results = client.phoneNumbers().search(Map.of("area_code", "512"));

// Place a call via REST
client.calling().execute("dial", Map.of(
    "from", "+15559876543",
    "to", "+15551234567",
    "url", "https://example.com/call-handler"
));
```

## Features

- Single `SignalWireClient` with 21 namespaced sub-objects for every API
- All calling commands: dial, play, record, collect, detect, tap, stream, AI, transcribe
- Full Fabric API: resource types with CRUD + addresses, tokens, and generic resources
- Datasphere: document management and semantic search
- Video: rooms, sessions, recordings, conferences, tokens, streams
- Compatibility API: full Twilio-compatible LAML surface
- Phone number management, 10DLC registry, MFA, logs, and more
- Uses `java.net.http.HttpClient` for connection pooling across all calls
- Map returns -- raw JSON decoded to Maps, no wrapper objects to learn

## Documentation

- [Getting Started](docs/getting-started.md) -- installation, configuration, first API call
- [Client Reference](docs/client-reference.md) -- SignalWireClient constructor, namespaces, error handling
- [Fabric Resources](docs/fabric.md) -- managing AI agents, SWML scripts, subscribers, call flows
- [Calling Commands](docs/calling.md) -- REST-based call control (dial, play, record, collect, AI)
- [Compatibility API](docs/compat.md) -- Twilio-compatible LAML endpoints
- [All Namespaces](docs/namespaces.md) -- phone numbers, video, datasphere, logs, registry

## Examples

- [RestManageResources.java](examples/RestManageResources.java) -- create an AI agent, list resources, place a test call
- [RestDatasphereSearch.java](examples/RestDatasphereSearch.java) -- upload a document and run a semantic search
- [RestCallingPlayAndRecord.java](examples/RestCallingPlayAndRecord.java) -- play audio and record on a call
- [RestCallingIvrAndAi.java](examples/RestCallingIvrAndAi.java) -- IVR with AI agent handoff
- [RestCompatLaml.java](examples/RestCompatLaml.java) -- Twilio-compatible LAML API
- [RestFabricConferencesAndRouting.java](examples/RestFabricConferencesAndRouting.java) -- conference and routing management
- [RestFabricSubscribersAndSip.java](examples/RestFabricSubscribersAndSip.java) -- subscriber and SIP management
- [RestFabricSwmlAndCallflows.java](examples/RestFabricSwmlAndCallflows.java) -- SWML scripts and call flows
- [RestPhoneNumberManagement.java](examples/RestPhoneNumberManagement.java) -- search, purchase, update phone numbers
- [RestQueuesMfaAndRecordings.java](examples/RestQueuesMfaAndRecordings.java) -- queues, MFA, and recordings
- [RestVideoRooms.java](examples/RestVideoRooms.java) -- video room management
- [Rest10dlcRegistration.java](examples/Rest10dlcRegistration.java) -- 10DLC campaign registration

## Environment Variables

| Variable | Description |
|----------|-------------|
| `SIGNALWIRE_PROJECT_ID` | Project ID for authentication |
| `SIGNALWIRE_API_TOKEN` | API token for authentication |
| `SIGNALWIRE_SPACE` | Space hostname (e.g. `example.signalwire.com`) |
| `SIGNALWIRE_LOG_LEVEL` | Log level (`debug` for HTTP request details) |

## Module Structure

```
com.signalwire.agents.rest/
    SignalWireClient.java    -- Namespace wiring, env var resolution
    HttpClient.java          -- java.net.http wrapper with Basic Auth
    CrudResource.java        -- Generic CRUD operations
    SignalWireRestError.java  -- Error class
    namespaces/
        FabricNamespace.java        -- AI agents, SWML scripts, subscribers, call flows
        CallingNamespace.java       -- REST-based call control commands
        PhoneNumbersNamespace.java  -- Phone number management
        DatasphereNamespace.java    -- Document management and search
        VideoNamespace.java         -- Video rooms and sessions
        CompatNamespace.java        -- Twilio-compatible API
        ... and 15 more
```
