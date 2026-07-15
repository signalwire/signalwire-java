# Getting Started with RELAY

The RELAY client connects to SignalWire via WebSocket and gives you real-time, imperative control over phone calls.

## Installation

The RELAY client ships in the single `com.signalwire:signalwire-sdk` artifact (requires Java 21+):

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

Alternatively, you can authenticate with a JWT token:

| Builder method | Env Var | Description |
|----------------|---------|-------------|
| `.jwtToken(...)` | `SIGNALWIRE_JWT_TOKEN` | A SignalWire JWT auth token |

## Minimal Example

<!-- snippet-setup -->
```java
import com.signalwire.sdk.relay.RelayClient;
import com.signalwire.sdk.relay.Call;
import java.util.List;
import java.util.Map;
```

```java
import com.signalwire.sdk.relay.RelayClient;
import java.util.List;

var client = RelayClient.builder()
        .project("your-project-id")
        .token("your-api-token")
        .space("example.signalwire.com")
        .contexts(List.of("default"))
        .build();

client.onCall(call -> {
    call.answer();
    var action = call.playTts("Hello!");
    action.waitForCompletion();
    call.hangup();
});

client.run();
```

Or use environment variables and skip the credential setters (the builder falls back to them):

```bash
export SIGNALWIRE_PROJECT_ID=your-project-id
export SIGNALWIRE_API_TOKEN=your-api-token
export SIGNALWIRE_SPACE=example.signalwire.com
```

```java
import com.signalwire.sdk.relay.RelayClient;
import java.util.List;

var client = RelayClient.builder()
        .contexts(List.of("default"))
        .build();

client.onCall(call -> {
    call.answer();
    call.hangup();
});

client.run();
```

## Contexts

Contexts are topics your client subscribes to for receiving inbound calls. When a call arrives on a context you're subscribed to, your `onCall(...)` handler is invoked.

```java
// Subscribe at connect time
var client = RelayClient.builder()
        .contexts(List.of("sales", "support"))
        .build();

// Or dynamically after connecting
client.receive(List.of("billing"));
client.unreceive(List.of("sales"));
```

## Making Outbound Calls

Use `client.dial()` to place an outbound call:

```java
import java.util.List;
import java.util.Map;

var client = RelayClient.builder().build();
var call = client.dial(List.of(
    List.of(Map.of("type", "phone",
        "params", Map.of("to_number", "+15551234567", "from_number", "+15559876543")))
));
// call is now a live Call object
var action = call.play(List.of(Map.of("type", "tts",
    "params", Map.of("text", "This is an outbound call."))));
action.waitForCompletion();
call.hangup();
```

The outer list represents serial attempts; the inner list represents parallel attempts. For example, to try two numbers simultaneously:

```java
import java.util.List;
import java.util.Map;

var client = RelayClient.builder().build();
var call = client.dial(List.of(
    List.of(
        Map.of("type", "phone",
            "params", Map.of("to_number", "+15551111111", "from_number", "+15559876543")),
        Map.of("type", "phone",
            "params", Map.of("to_number", "+15552222222", "from_number", "+15559876543"))
    )
));
```

## Debug Logging

Set the log level to see WebSocket traffic:

```bash
export SIGNALWIRE_LOG_LEVEL=debug
```

## Try-With-Resources

`RelayClient` implements `AutoCloseable`, so you can manage its lifecycle with try-with-resources:

```java
try (var client = RelayClient.builder().contexts(List.of("default")).build()) {
    var call = client.dial(List.of(/* ... */));
    call.answer();
}
```

## Next Steps

- [Call Methods Reference](call-methods.md) -- all methods available on a Call object
- [Events](events.md) -- handling real-time call events
- [Client Reference](client-reference.md) -- RelayClient configuration and methods
