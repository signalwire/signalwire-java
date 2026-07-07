# RestClient Reference

## Constructor

`RestClient` is created with a builder. Each value falls back to its corresponding environment variable when not supplied:

<!-- snippet-setup -->
```java
import com.signalwire.sdk.rest.RestClient;

var client = RestClient.builder()
        .project("...")   // SIGNALWIRE_PROJECT_ID
        .token("...")     // SIGNALWIRE_API_TOKEN
        .space("...")     // SIGNALWIRE_SPACE
        .build();
```

Authentication uses HTTP Basic Auth (`project:token`).

## Namespaces

Every API surface is exposed as a namespace accessor **method** on the client.

### Fabric API

Reached via `client.fabric()`:

| Accessor | Description |
|----------|-------------|
| `client.fabric().swmlScripts()` | SWML script resources (CRUD + addresses) |
| `client.fabric().swmlWebhooks()` | SWML webhook resources |
| `client.fabric().aiAgents()` | AI agent resources |
| `client.fabric().relayApplications()` | Relay application resources |
| `client.fabric().callFlows()` | Call flow resources (+ versions) |
| `client.fabric().conferenceRooms()` | Conference room resources |
| `client.fabric().freeswitchConnectors()` | FreeSWITCH connector resources |
| `client.fabric().subscribers()` | Subscriber resources (+ SIP endpoints) |
| `client.fabric().sipEndpoints()` | SIP endpoint resources |
| `client.fabric().sipGateways()` | SIP gateway resources |
| `client.fabric().cxmlScripts()` | cXML script resources |
| `client.fabric().cxmlWebhooks()` | cXML webhook resources |
| `client.fabric().cxmlApplications()` | cXML application resources (no create) |
| `client.fabric().resources()` | Generic resource operations |
| `client.fabric().addresses()` | Fabric addresses (list/get only) |
| `client.fabric().tokens()` | Subscriber/guest/invite/embed token creation |

### Calling API

| Accessor | Description |
|----------|-------------|
| `client.calling()` | REST call control -- 37 commands via POST |

### Relay REST Resources

| Accessor | Description |
|----------|-------------|
| `client.phoneNumbers()` | Phone number management (+ search) |
| `client.addresses()` | Address management |
| `client.queues()` | Queue management (+ members) |
| `client.recordings()` | Recording management |
| `client.numberGroups()` | Number group management (+ memberships) |
| `client.verifiedCallers()` | Verified caller ID management (+ verification flow) |
| `client.sipProfile()` | Project SIP profile (get/update) |
| `client.numberLookup()` | Phone number lookup |
| `client.shortCodes()` | Short code management |
| `client.importedNumbers()` | Import external phone numbers |
| `client.mfa()` | Multi-factor authentication (SMS/call/verify) |
| `client.registry()` | 10DLC brand/campaign registry |

### Other APIs

| Accessor | Description |
|----------|-------------|
| `client.datasphere()` | Datasphere document management and semantic search |
| `client.video()` | Video rooms, sessions, recordings, conferences |
| `client.logs()` | Message, voice, fax, and conference logs |
| `client.project()` | API token management |
| `client.pubSub()` | PubSub token creation |
| `client.chat()` | Chat token creation |

## Error Handling

`RestError` is thrown on any non-2xx HTTP response:

```java
import com.signalwire.sdk.rest.RestError;

try {
    client.fabric().aiAgents().get("bad-id");
} catch (RestError e) {
    e.getStatusCode();    // 404
    e.getResponseBody();  // {"error": "not found"}
    e.getPath();          // "/api/fabric/resources/ai_agents/bad-id"
    e.getMethod();        // "GET"
}
```

### Error Attributes

| Accessor | Type | Description |
|----------|------|-------------|
| `getStatusCode()` | `int` | HTTP status code |
| `getResponseBody()` | `String` | Response body (raw text) |
| `getPath()` | `String` | Request path |
| `getMethod()` | `String` | HTTP method |

## Session Behavior

- Content-Type is always `application/json`.
- DELETE requests returning 204 yield an empty result.
