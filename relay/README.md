# SignalWire RELAY Client (Java)

Real-time call control and messaging over WebSocket using Java's virtual threads. The RELAY client connects to SignalWire via the Blade protocol (JSON-RPC 2.0 over WebSocket) and gives you imperative control over live phone calls and SMS/MMS messaging.

## Quick Start

```java
import com.signalwire.agents.relay.RelayClient;

var client = RelayClient.builder()
    .project("your-project-id")
    .token("your-api-token")
    .space("example.signalwire.com")
    .contexts(List.of("default"))
    .build();

client.onCall(call -> {
    call.answer();
    var action = call.play(List.of(Map.of(
        "type", "tts",
        "params", Map.of("text", "Welcome to SignalWire!")
    )));
    action.waitForCompletion();
    call.hangup();
});

client.run();
```

## Features

- Virtual-thread based with auto-reconnect and exponential backoff
- All calling methods: play, record, collect, connect, detect, fax, tap, stream, AI, conferencing, queues
- SMS/MMS messaging: send outbound messages, receive inbound messages, track delivery state
- Action objects with `waitForCompletion()`, `stop()`, `pause()`, `resume()`
- Typed event classes for all call events
- JWT and legacy authentication
- Dynamic context subscription/unsubscription

## Documentation

- [Getting Started](docs/getting-started.md) -- installation, configuration, first call
- [Call Methods Reference](docs/call-methods.md) -- every method available on a Call object
- [Events](docs/events.md) -- event types, typed event classes, call states
- [Messaging](docs/messaging.md) -- sending and receiving SMS/MMS messages
- [Client Reference](docs/client-reference.md) -- RelayClient configuration, methods, connection behavior
- [Implementation Guide](RELAY_IMPLEMENTATION_GUIDE.md) -- internal architecture and protocol details

## Examples

- [RelayAnswerAndWelcome.java](examples/RelayAnswerAndWelcome.java) -- answer an inbound call and play a TTS greeting
- [RelayDialAndPlay.java](examples/RelayDialAndPlay.java) -- dial a number and play a TTS message
- [RelayIvrConnect.java](examples/RelayIvrConnect.java) -- IVR menu with DTMF, playback, and call connect

## Environment Variables

| Variable | Description |
|----------|-------------|
| `SIGNALWIRE_PROJECT_ID` | Project ID for authentication |
| `SIGNALWIRE_API_TOKEN` | API token for authentication |
| `SIGNALWIRE_JWT_TOKEN` | JWT token (alternative to project/token) |
| `SIGNALWIRE_SPACE` | Space hostname (default: `relay.signalwire.com`) |
| `RELAY_MAX_ACTIVE_CALLS` | Max concurrent calls per client (default: 1000) |
| `SIGNALWIRE_LOG_LEVEL` | Log level (`debug` for WebSocket traffic) |

## Module Structure

```
com.signalwire.agents.relay/
    RelayClient.java   -- WebSocket connection, auth, event dispatch
    Call.java           -- Call object with all calling methods and Action classes
    Action.java         -- Action wrapper for controllable operations
    Message.java        -- SMS/MMS message tracking
    RelayEvent.java     -- Typed event classes
    Constants.java      -- Protocol constants, call states, event types
```
