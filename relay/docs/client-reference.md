# RelayClient Reference

## Construction (Builder)

`RelayClient` is created via its builder:

```java
import com.signalwire.sdk.relay.RelayClient;
import java.util.List;

var client = RelayClient.builder()
    .project("...")                  // SIGNALWIRE_PROJECT_ID
    .token("...")                    // SIGNALWIRE_API_TOKEN
    .jwtToken("...")                 // SIGNALWIRE_JWT_TOKEN
    .space("example.signalwire.com") // SIGNALWIRE_SPACE (or .host(...))
    .contexts(List.of("default"))    // Topics to subscribe to
    .build();
```

Authentication requires either `project` + `token` (legacy) or `jwtToken` (faster, no server roundtrip). When the credential setters are omitted, the client falls back to the corresponding environment variables (`SIGNALWIRE_PROJECT_ID`, `SIGNALWIRE_API_TOKEN`, `SIGNALWIRE_JWT_TOKEN`, `SIGNALWIRE_SPACE`).

## Methods

### `run()`

Blocking entry point. Connects, authenticates, and runs the event loop with auto-reconnect until interrupted.

```java
client.run();
```

### `connect()` / `disconnect()`

Manual lifecycle control.

```java
client.connect();       // or client.connect(timeoutMs)
// ... use client ...
client.disconnect();
```

`RelayClient` also implements `AutoCloseable`, so it works with try-with-resources:

```java
try (var client = RelayClient.builder().contexts(List.of("default")).build()) {
    client.connect();
    // ...
}
```

### `onCall(handler)`

Register the inbound call handler. The handler receives a `Call` object.

```java
client.onCall(call -> {
    call.answer();
});
```

### `dial(devices, options, timeout) -> Call`

Place an outbound call. Returns a `Call` once the remote party answers.

- `devices` -- nested list of device objects (outer = sequential, inner = parallel dial)
- `options` -- optional parameters map (e.g. `tag`, `region`, `max_price_per_minute`); `tag` is auto-generated if omitted
- `timeout` -- milliseconds to wait before a `RelayError` is thrown

A convenience overload `dial(devices)` uses a default timeout of 120 seconds.

```java
import java.util.List;
import java.util.Map;

var call = client.dial(List.of(
    List.of(Map.of("type", "phone",
        "params", Map.of("to_number", "+15551234567", "from_number", "+15559876543")))
));
```

### `onMessage(handler)`

Register the inbound message handler. The handler receives a `Message` object.

```java
client.onMessage(message -> {
    System.out.println("SMS from " + message.getFromNumber().orElse("")
        + ": " + message.getBody().orElse(""));
});
```

### `sendMessage(context, fromNumber, toNumber, body, mediaUrls) -> Message`

Send an outbound SMS/MMS. Returns a `Message` that tracks delivery state. An overload accepts an additional `tags` list. Pass `null` (or empty) for `context` to use the connected protocol context.

```java
import java.util.List;

var message = client.sendMessage(
    null,                 // context (null → protocol/default)
    "+15551111111",       // fromNumber
    "+15552222222",       // toNumber
    "Hello!",             // body
    List.of());           // mediaUrls (empty for SMS)

var event = message.waitForCompletion();  // block until delivered/failed
```

See [Messaging](messaging.md) for full details.

### `execute(method, params) -> Map<String, Object>`

Send a raw JSON-RPC request. Used internally by Call methods, but available for custom commands.

### `receive(newContexts)` / `unreceive(removeContexts)`

Dynamically subscribe to or unsubscribe from contexts after connecting.

```java
client.receive(List.of("new-context"));
client.unreceive(List.of("old-context"));
```

## Properties (accessors)

| Accessor | Type | Description |
|----------|------|-------------|
| `getRelayProtocol()` | `String` | Server-assigned protocol string from connect response |
| `getProject()` | `String` | Project ID |
| `getSpace()` | `String` | Relay host/space |
| `getContexts()` | `List<String>` | Initial contexts |
| `isConnected()` | `boolean` | Whether the WebSocket is currently connected |

## Connection Behavior

- **Auto-reconnect**: On connection loss, the client reconnects with exponential backoff (1s to 30s).
- **Ping/pong**: Client sends periodic pings and monitors server pings. After consecutive failures, the connection is force-closed and reconnected.
- **Request queueing**: Requests made while disconnected are queued and sent after re-authentication.
- **Authorization state**: The server sends encrypted auth state via events (see `getAuthorizationState()`). On reconnect, this is sent back for fast re-authentication without a full auth roundtrip.
- **Server disconnect**: The server can request a graceful disconnect (e.g. during deployment). The client auto-reconnects afterward.

## Concurrency

Each inbound call handler runs on the JDK virtual-thread executor, so multiple calls are handled concurrently without blocking the event loop.

## Error Handling

```java
import com.signalwire.sdk.relay.RelayError;

try {
    call.play(List.of(/* ... */));
} catch (RelayError e) {
    System.out.println("Relay error: " + e.getMessage());
}
```

`RelayError` (a `RuntimeException`) is thrown when the server returns a non-2xx response code, or on dial timeout/failure. Errors 404 and 410 (call gone) are silently swallowed by Call methods since the call no longer exists.
