# Events

RELAY events are server-pushed notifications about call state changes and operation results. Events arrive over the WebSocket as `signalwire.event` JSON-RPC messages and are automatically routed to the correct `Call` object.

## Listening for Events

### On a Call

```java
import java.util.List;
import java.util.Map;

client.onCall(call -> {
    // Register a listener (receives every event routed to this call)
    call.on(event -> System.out.println("Event: " + event.getEventType()
        + " " + event.getParams()));

    // Or wait for the call to reach a specific state
    var event = call.waitFor("ended", 60_000);  // targetState, timeoutMs
});
```

### Via Actions

Actions returned by `play()`, `record()`, etc. have a `waitForCompletion()` method that resolves when the operation completes:

```java
import java.util.List;
import java.util.Map;

var action = call.play(List.of(Map.of("type", "tts", "params", Map.of("text", "Hello"))));
var event = action.waitForCompletion(30_000);
// event is a RelayEvent with the terminal state
```

## Event Types

All event-type constants are available on `com.signalwire.sdk.relay.Constants`:

| Constant | Value | Description |
|----------|-------|-------------|
| `EVENT_CALL_STATE` | `calling.call.state` | Call state changes (created, ringing, answered, ending, ended) |
| `EVENT_CALL_RECEIVE` | `calling.call.receive` | Inbound call notification |
| `EVENT_CALL_PLAY` | `calling.call.play` | Play operation state changes |
| `EVENT_CALL_RECORD` | `calling.call.record` | Record operation state changes |
| `EVENT_CALL_COLLECT` | `calling.call.collect` | Input collection results |
| `EVENT_CALL_CONNECT` | `calling.call.connect` | Bridge/connect state changes |
| `EVENT_CALL_DETECT` | `calling.call.detect` | Detection results |
| `EVENT_CALL_FAX` | `calling.call.fax` | Fax operation state changes |
| `EVENT_CALL_TAP` | `calling.call.tap` | Tap operation state changes |
| `EVENT_CALL_STREAM` | `calling.call.stream` | Stream operation state changes |
| `EVENT_CALL_SEND_DIGITS` | `calling.call.send_digits` | DTMF send completion |
| `EVENT_CALL_DIAL` | `calling.call.dial` | Outbound dial progress |
| `EVENT_CALL_REFER` | `calling.call.refer` | SIP REFER results |
| `EVENT_CALL_DENOISE` | `calling.call.denoise` | Denoise state changes |
| `EVENT_CALL_PAY` | `calling.call.pay` | Payment state changes |
| `EVENT_CALL_QUEUE` | `calling.call.queue` | Queue state changes |
| `EVENT_CALL_ECHO` | `calling.call.echo` | Echo state changes |
| `EVENT_CALL_TRANSCRIBE` | `calling.call.transcribe` | Transcription state changes |
| `EVENT_CONFERENCE` | `calling.conference` | Conference state changes |
| `EVENT_CALLING_ERROR` | `calling.error` | Error events |
| `EVENT_MESSAGING_RECEIVE` | `messaging.receive` | Inbound message received |
| `EVENT_MESSAGING_STATE` | `messaging.state` | Outbound message state change |

## Typed Event Classes

Raw events are always `RelayEvent` with a `getParams()` map. For convenience, typed event classes (nested under `RelayEvent`) provide named accessors:

```java
import com.signalwire.sdk.relay.Constants;
import com.signalwire.sdk.relay.RelayEvent;

// Automatic parsing
RelayEvent event = RelayEvent.parseEvent(rawPayload);

// Or construct directly
if (Constants.EVENT_CALL_STATE.equals(event.getEventType())) {
    var stateEvent = RelayEvent.CallStateEvent.fromPayload(rawPayload);
    System.out.println(stateEvent.getCallState());   // "answered"
    System.out.println(stateEvent.getEndReason());   // "hangup" (only on ended)
}
```

### Available Typed Events

All are nested classes of `RelayEvent` (e.g. `RelayEvent.CallStateEvent`):

| Class | Key Accessors |
|-------|---------------|
| `CallStateEvent` | `getCallId()`, `getCallState()`, `getEndReason()`, `getDirection()`, `getDevice()` |
| `CallReceiveEvent` | `getCallId()`, `getCallState()`, `getContext()`, `getDevice()`, `getNodeId()` |
| `CallPlayEvent` | `getCallId()`, `getControlId()`, `getState()` |
| `CallRecordEvent` | `getCallId()`, `getControlId()`, `getState()`, `getUrl()`, `getDuration()`, `getSize()` |
| `CallCollectEvent` | `getCallId()`, `getControlId()`, `getState()`, `getResult()`, `getResultType()`, `getFinal()` |
| `CallConnectEvent` | `getCallId()`, `getConnectState()` |
| `CallDetectEvent` | `getCallId()`, `getControlId()`, `getDetect()`, `getDetectEvent()` |
| `CallFaxEvent` | `getCallId()`, `getControlId()`, `getState()` |
| `CallTapEvent` | `getCallId()`, `getControlId()`, `getState()` |
| `CallStreamEvent` | `getCallId()`, `getControlId()`, `getState()` |
| `CallSendDigitsEvent` | `getCallId()`, `getState()` |
| `CallDialEvent` | `getTag()`, `getNodeId()`, `getDialState()`, `getDialStateEnum()`, `getCallInfo()`, `getCallId()` |
| `CallReferEvent` | `getCallId()`, `getReferState()` |
| `CallDenoiseEvent` | `getCallId()`, `getEventType()`, `getParams()` |
| `CallPayEvent` | `getCallId()`, `getControlId()`, `getState()` |
| `QueueEvent` | `getCallId()`, `getControlId()`, `getStatus()`, `getQueueId()`, `getQueueName()`, `getPosition()`, `getSize()` |
| `EchoEvent` | `getCallId()`, `getState()` |
| `CallTranscribeEvent` | `getCallId()`, `getControlId()`, `getState()` |
| `HoldEvent` | `getCallId()`, `getState()` |
| `ConferenceEvent` | `getConferenceId()`, `getCallId()` |
| `CallingErrorEvent` | `getCallId()`, `getCode()`, `getMessage()` |
| `MessagingReceiveEvent` | `getMessageId()`, `getContext()`, `getDirection()`, `getFromNumber()`, `getToNumber()`, `getBody()`, `getMedia()`, `getSegments()`, `getMessageState()`, `getTags()` |
| `MessagingStateEvent` | `getMessageId()`, `getContext()`, `getDirection()`, `getFromNumber()`, `getToNumber()`, `getBody()`, `getMedia()`, `getSegments()`, `getMessageState()`, `getReason()`, `getTags()` |

## Call States

```
created -> ringing -> answered -> ending -> ended
```

Constants: `CALL_STATE_CREATED`, `CALL_STATE_RINGING`, `CALL_STATE_ANSWERED`, `CALL_STATE_ENDING`, `CALL_STATE_ENDED`

## End Reasons

When a call reaches the `ended` state, the `end_reason` field indicates why:

| Reason | Description |
|--------|-------------|
| `hangup` | Normal hangup |
| `cancel` | Caller cancelled |
| `busy` | Destination busy |
| `noAnswer` | No answer |
| `decline` | Call declined |
| `error` | Error occurred |
| `abandoned` | Call abandoned |
| `max_duration` | Max duration reached |
| `not_found` | Destination not found |

## Message States

Outbound messages progress through: `queued` → `initiated` → `sent` → `delivered` (or `undelivered`/`failed`).

Constants: `MESSAGE_STATE_QUEUED`, `MESSAGE_STATE_INITIATED`, `MESSAGE_STATE_SENT`, `MESSAGE_STATE_DELIVERED`, `MESSAGE_STATE_UNDELIVERED`, `MESSAGE_STATE_FAILED`, `MESSAGE_STATE_RECEIVED`
