# Messaging

Send and receive SMS/MMS messages through the RELAY client.

<!-- snippet-setup -->
```java
import com.signalwire.sdk.relay.RelayClient;

RelayClient client = RelayClient.builder().build();
```

## Sending Messages

Use `client.sendMessage()` to send an outbound SMS or MMS. The parameter order is
`(context, fromNumber, toNumber, body, mediaUrls)` with an overload adding `tags`.
Pass `null` for `context` to use the connected protocol context.

```java
import java.util.List;

var message = client.sendMessage(
    null,               // context (null → protocol/default)
    "+15551111111",     // fromNumber
    "+15552222222",     // toNumber
    "Hello from SignalWire!",
    List.of());         // mediaUrls (empty for SMS)
```

### Wait for delivery

```java
var message = client.sendMessage(null, "+15551111111", "+15552222222", "Hello!", List.of());

var event = message.waitForCompletion();  // blocks until delivered/failed
System.out.println("Final state: " + message.getState());
message.getReason().ifPresent(r -> System.out.println("Reason: " + r));
```

### Fire and forget

```java
var message = client.sendMessage(null, "+15551111111", "+15552222222", "Hello!", List.of());
// don't call message.waitForCompletion() — continue immediately
```

### Callback on completion

```java
var message = client.sendMessage(null, "+15551111111", "+15552222222", "Hello!", List.of());
message.setOnCompleted(m ->
    System.out.println("Delivery: " + m.getState()));
```

### MMS (media messages)

```java
import java.util.List;

var message = client.sendMessage(
    null,
    "+15551111111",
    "+15552222222",
    "Check this out!",
    List.of("https://example.com/image.jpg"));  // media URLs
```

### All parameters

The `tags` overload exposes every field:

```java
import java.util.List;

var message = client.sendMessage(
    "my_context",                     // context for state events (null → relay protocol)
    "+15551111111",                   // fromNumber — required, E.164 format
    "+15552222222",                   // toNumber — required, E.164 format
    "Message text",                   // body — required if no media
    List.of("https://..."),           // mediaUrls — required if no body
    List.of("vip", "support"));       // tags — optional, for searching in UI

// Register a completion callback if desired:
message.setOnCompleted(m -> System.out.println("done: " + m.getState()));
```

## Receiving Messages

Register a handler with `client.onMessage(...)` to receive inbound SMS/MMS.

```java
import java.util.List;

// client = RelayClient.builder().contexts(List.of("default")).build();
client.onMessage(message -> {
    System.out.println("From: " + message.getFromNumber().orElse(""));
    System.out.println("To: " + message.getToNumber().orElse(""));
    System.out.println("Body: " + message.getBody().orElse(""));
    if (!message.getMedia().isEmpty()) {
        System.out.println("Media: " + message.getMedia());
    }

    // Reply back
    client.sendMessage(
        null,
        message.getToNumber().orElse(""),    // from = the number that received it
        message.getFromNumber().orElse(""),  // to = the original sender
        "You said: " + message.getBody().orElse(""),
        List.of());
});

client.run();
```

## Message Object

### Accessors

| Accessor | Type | Description |
|----------|------|-------------|
| `getMessageId()` | `String` | Unique message identifier |
| `getContext()` | `Optional<String>` | Context the message belongs to |
| `getDirection()` | `Optional<String>` | `inbound` or `outbound` |
| `getFromNumber()` | `Optional<String>` | Sender phone number (E.164) |
| `getToNumber()` | `Optional<String>` | Recipient phone number (E.164) |
| `getBody()` | `Optional<String>` | Text body of the message |
| `getMedia()` | `List<String>` | Media URLs (MMS) |
| `getSegments()` | `int` | Number of message segments |
| `getState()` | `String` | Current message state |
| `getReason()` | `Optional<String>` | Failure reason (on `undelivered` or `failed`) |
| `getTags()` | `List<String>` | Tags attached to the message |
| `isDone()` | `boolean` | `true` if message reached a terminal state |
| `getResult()` | `Optional<RelayEvent>` | Terminal event (empty if not done) |

### Methods

| Method | Description |
|--------|-------------|
| `waitForCompletion()` / `waitForCompletion(timeoutMs)` | Block until terminal state. Returns the terminal `RelayEvent`. |
| `on(listener)` | Register a `Consumer<RelayEvent>` for state change events. |
| `setOnCompleted(callback)` | Register a `Consumer<Message>` invoked once when the message reaches a terminal state. |

### Message States

Outbound messages progress through these states:

| State | Description |
|-------|-------------|
| `queued` | Message accepted and queued for sending |
| `initiated` | Sending has started |
| `sent` | Message sent to carrier |
| `delivered` | Message delivered to recipient (terminal) |
| `undelivered` | Delivery failed (terminal) — check `getReason()` |
| `failed` | Message failed to send (terminal) — check `getReason()` |

Inbound messages always arrive with state `received`.

## Event Types

| Event | Description |
|-------|-------------|
| `RelayEvent.MessagingReceiveEvent` | Inbound message received |
| `RelayEvent.MessagingStateEvent` | Outbound message state change |

```java
import com.signalwire.sdk.relay.RelayEvent;
// RelayEvent.MessagingReceiveEvent, RelayEvent.MessagingStateEvent
```

## Combining Calls and Messages

The same `RelayClient` handles both calls and messages:

```java
import java.util.List;
import java.util.Map;

// client = RelayClient.builder().contexts(List.of("default")).build();
client.onCall(call -> {
    call.answer();
    call.play(List.of(Map.of("type", "tts", "params", Map.of("text", "Hello!"))))
        .waitForCompletion();
    call.hangup();
});

client.onMessage(message ->
    System.out.println("SMS from " + message.getFromNumber().orElse("")
        + ": " + message.getBody().orElse("")));

client.run();
```
