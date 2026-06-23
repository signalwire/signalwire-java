# Getting Started with RELAY

The RELAY client connects to SignalWire via WebSocket and gives you real-time, imperative control over phone calls.

## Installation

The RELAY client ships in the single `com.signalwire:signalwire-sdk` artifact (requires Java 21+):

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

Alternatively, you can authenticate with a JWT token:

| Builder method | Env Var | Description |
|----------------|---------|-------------|
| `.jwtToken(...)` | `SIGNALWIRE_JWT_TOKEN` | A SignalWire JWT auth token |

## Minimal Example

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

Or use environment variables and skip the constructor args:

```bash
export SIGNALWIRE_PROJECT_ID=your-project-id
export SIGNALWIRE_API_TOKEN=your-api-token
export SIGNALWIRE_SPACE=example.signalwire.com
```

```python
from signalwire_agents.relay import RelayClient

client = RelayClient(contexts=["default"])

@client.on_call
async def handle(call):
    await call.answer()
    await call.hangup()

client.run()
```

## Contexts

Contexts are topics your client subscribes to for receiving inbound calls. When a call arrives on a context you're subscribed to, your `@client.on_call` handler is invoked.

```python
# Subscribe at connect time
client = RelayClient(contexts=["sales", "support"])

# Or dynamically after connecting
await client.receive(["billing"])
await client.unreceive(["sales"])
```

## Making Outbound Calls

Use `client.dial()` to place an outbound call:

```python
call = await client.dial([
    [{"type": "phone", "params": {"to_number": "+15551234567", "from_number": "+15559876543"}}]
])
# call is now a live Call object
action = await call.play([{"type": "tts", "params": {"text": "This is an outbound call."}}])
await action.wait()
await call.hangup()
```

The outer list represents serial attempts; the inner list represents parallel attempts. For example, to try two numbers simultaneously:

```python
call = await client.dial([
    [
        {"type": "phone", "params": {"to_number": "+15551111111", "from_number": "+15559876543"}},
        {"type": "phone", "params": {"to_number": "+15552222222", "from_number": "+15559876543"}},
    ]
])
```

## Debug Logging

Set the log level to see WebSocket traffic:

```bash
export SIGNALWIRE_LOG_LEVEL=debug
```

## Async Context Manager

For use within an existing async application:

```python
async with RelayClient(contexts=["default"]) as client:
    call = await client.dial([...])
    await call.answer()
```

## Next Steps

- [Call Methods Reference](call-methods.md) -- all methods available on a Call object
- [Events](events.md) -- handling real-time call events
- [Client Reference](client-reference.md) -- RelayClient configuration and methods
