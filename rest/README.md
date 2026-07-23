# SignalWire REST Client (Java)

Synchronous REST client for managing SignalWire resources, controlling live calls, and interacting with every SignalWire API surface from Java. No WebSocket required -- just standard HTTP requests with automatic connection pooling.

## Quick Start

```java
import com.signalwire.sdk.rest.RestClient;

var client = RestClient.builder()
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
var results = client.phoneNumbers().search(Map.of("areacode", "512"));

// Place a call via REST
client.calling().dial(
    com.signalwire.sdk.rest.namespaces.generated.Calling.DialRequest.builder()
        .from("+15559876543")
        .to("+15551234567")
        .url("https://example.com/call-handler")
        .build());
```

## Features

- Single `RestClient` with 22 namespaced sub-objects for every API
- All calling commands: dial, play, record, collect, detect, tap, stream, AI, transcribe
- Full Fabric API: resource types with CRUD + addresses, tokens, and generic resources
- Datasphere: document management and semantic search
- Video: rooms, sessions, recordings, conferences, tokens, streams
- Phone number management, 10DLC registry, MFA, logs, and more
- Uses `java.net.http.HttpClient` for connection pooling across all calls
- Map returns -- raw JSON decoded to Maps, no wrapper objects to learn

## Request Options (per-call timeout & retries)

Every request's transport behavior -- timeout, retries, retry backoff, retry-on-status,
and an abort signal -- is controlled by an immutable `RequestOptions`. Build one with
`RequestOptions.builder()` (all fields optional) and apply it two ways:

```java
import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RequestOptions;

var opts = RequestOptions.builder()
    .timeout(5.0)                 // seconds; caps the whole request
    .retries(2)                   // retry a failed request up to N times
    .retryOnStatus(java.util.Set.of(429, 503))
    .build();

// Client default -- applied to every request unless overridden per-call:
var client = RestClient.builder()
    .project("your-project-id").token("your-api-token").space("example.signalwire.com")
    .requestOptions(opts)
    .build();

// Per-call override -- pass RequestOptions as the trailing argument on any
// CRUD/list/get/create/update/delete method (merged over the client default):
var agent = client.fabric().aiAgents().get("agent-id",
    RequestOptions.builder().timeout(2.0).build());
```

A per-call `RequestOptions` is merged over the client default, so you can set a global
timeout on the client and tighten it for a single latency-sensitive call.

## Documentation

- [Getting Started](docs/getting-started.md) -- installation, configuration, first API call
- [Client Reference](docs/client-reference.md) -- RestClient constructor, namespaces, error handling
- [Fabric Resources](docs/fabric.md) -- managing AI agents, SWML scripts, subscribers, call flows
- [Calling Commands](docs/calling.md) -- REST-based call control (dial, play, record, collect, AI)
- [All Namespaces](docs/namespaces.md) -- phone numbers, video, datasphere, logs, registry

## Examples

- [RestManageResources.java](examples/RestManageResources.java) -- create an AI agent, list resources, place a test call
- [RestDatasphereSearch.java](examples/RestDatasphereSearch.java) -- upload a document and run a semantic search
- [RestCallingPlayAndRecord.java](examples/RestCallingPlayAndRecord.java) -- play audio and record on a call
- [RestCallingIvrAndAi.java](examples/RestCallingIvrAndAi.java) -- IVR with AI agent handoff
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
| `SIGNALWIRE_REST_CA_FILE` | Path to a custom CA bundle for the REST transport (private/self-signed platform cert) |
| `SIGNALWIRE_LOG_LEVEL` | Log level (`debug` for HTTP request details) |

## Module Structure

```
com.signalwire.sdk.rest/
    RestClient.java    -- Namespace wiring, env var resolution
    HttpClient.java          -- java.net.http wrapper with Basic Auth
    CrudResource.java        -- Generic CRUD operations
    RestError.java  -- Error class
    namespaces/
        FabricNamespace.java        -- AI agents, SWML scripts, subscribers, call flows
        CallingNamespace.java       -- REST-based call control commands
        PhoneNumbersNamespace.java  -- Phone number management
        DatasphereNamespace.java    -- Document management and search
        VideoNamespace.java         -- Video rooms and sessions
        ... and 17 more
```
