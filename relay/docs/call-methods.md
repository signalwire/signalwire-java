# Call Methods Reference

A `Call` object represents a live phone call. You get one from `client.onCall(handler)` (inbound) or `client.dial(devices)` (outbound).

<!-- snippet-setup -->
```java
import com.signalwire.sdk.relay.*;

Call call = new Call("call-id", "node-id");
```

## Properties

`Call` exposes its state through accessor methods. Nullable scalars are returned as `Optional<String>` (empty until the server pushes the relevant event).

| Accessor | Type | Description |
|----------|------|-------------|
| `getCallId()` | `String` | Unique call identifier |
| `getNodeId()` | `Optional<String>` | Server node handling the call |
| `getState()` | `String` | Current state: `created`, `ringing`, `answered`, `ending`, `ended` |
| `getCallState()` | `Optional<CallState>` | Typed view of `getState()` |
| `getDirection()` | `Optional<String>` | `inbound` or `outbound` |
| `getTag()` | `Optional<String>` | Dial-correlation tag |
| `getDevice()` | `Map<String, Object>` | Device info (type, params) |
| `getEndReason()` | `Optional<String>` | End reason once the call has ended |
| `isEnded()` | `boolean` | `true` when the call has ended |

## Actions: Blocking vs Fire-and-Forget

Methods like `play()`, `record()`, `detect()`, etc. return **`Action`** objects. The call itself only waits for the server to accept the command — the actual operation runs asynchronously on the server. You choose how to handle completion:

### Wait inline (blocking)

```java
Action.PlayAction action = call.play(List.of(
    Map.of("type", "tts", "params", Map.of("text", "Hello"))));
action.waitForCompletion(); // blocks until playback finishes
// execution continues only after play is done
```

### Fire and forget (background)

```java
Action.PlayAction action = call.play(List.of(
    Map.of("type", "tts", "params", Map.of("text", "Hello"))));
// don't call waitForCompletion() — continue immediately while audio plays
call.sendDigits("1234");

// check later if needed
if (action.isDone()) {
    System.out.println("Play result: " + action.getResult());
}
```

### Fire with callback

```java
// Callback fires on terminal state; continues immediately.
Action.PlayAction action = call.play(List.of(
    Map.of("type", "tts", "params", Map.of("text", "Hello"))));
action.setOnCompleted(completed ->
    System.out.println("Done: " + completed.getResult().getParams()));

// The same pattern works for record, etc.
Action.RecordAction recording = call.record(
    Map.of("audio", Map.of("format", "wav")), null);
recording.setOnCompleted(completed -> {
    RelayEvent event = completed.getResult();
    System.out.println("Recording URL: " + event.getStringParam("url"));
    call.hangup();
});
```

The `setOnCompleted` callback is available on every `Action` subclass returned by `play`, `record`, `playAndCollect`, `collect`, `detect`, `pay`, `sendFax`, `receiveFax`, `tap`, `stream`, `transcribe`, and `ai`. Exceptions thrown in the callback are caught and logged, never crash the event loop. The callback also fires when the call is gone (404/410).

### Action methods summary

| Method | Returns |
|--------|---------|
| `action.waitForCompletion()` / `action.await()` | Blocks until the action completes, returns the terminal `RelayEvent` |
| `action.waitForCompletion(long timeoutMs)` | Same, with a timeout (`null` on timeout) |
| `action.isDone()` | `true` if the action has completed |
| `action.getResult()` | The terminal `RelayEvent` (or `null` if not done) |
| `action.getState()` | The last observed action state |
| `action.stop()` | Stop the operation on the server |

Some actions also have `pause()`, `resume()`, and `volume(double)`.

## Lifecycle

### `answer() -> Map<String, Object>`

Answer an inbound call.

```java
call.answer();
```

### `hangup() -> Map<String, Object>` / `hangup(String reason)`

End the call.

```java
call.hangup();
call.hangup("busy");
```

### `pass() -> Map<String, Object>`

Decline control, returning the call to routing.

```java
call.pass();
```

## Audio Playback

### `play(List<Map<String, Object>> media, Map<String, Object> options) -> Action.PlayAction`

Play audio. Returns a `PlayAction` with `stop()`, `pause()`, `resume()`, `volume(double)`, and `waitForCompletion()`. An overload `play(media)` uses default options.

```java
// TTS
Action.PlayAction action = call.play(List.of(
    Map.of("type", "tts", "params", Map.of("text", "Hello!"))));
action.waitForCompletion();

// Audio file
call.play(List.of(
    Map.of("type", "audio", "params", Map.of("url", "https://example.com/sound.mp3"))));

// Silence
call.play(List.of(
    Map.of("type", "silence", "params", Map.of("duration", 2))));

// Ringtone
call.play(List.of(
    Map.of("type", "ringtone", "params", Map.of("name", "us"))));

// Control playback
action.pause();
action.resume();
action.volume(-3.0);
action.stop();
```

Typed convenience wrappers build the media shape for you: `playTts(String text)`, `playAudio(String url)`, `playSilence(double duration)`, `playRingtone(String name)` (each with an optional trailing options map).

```java
call.playTts("Hello!");
call.playAudio("https://example.com/sound.mp3");
```

## Recording

### `record(Map<String, Object> recordConfig, Map<String, Object> options) -> Action.RecordAction`

Record the call. Returns a `RecordAction` with `stop()`, `pause()`, `resume()`, and `waitForCompletion()`.

```java
Action.RecordAction action = call.record(
    Map.of("audio", Map.of("format", "wav", "stereo", true, "direction", "both")),
    null);
// ... later ...
action.stop();
RelayEvent event = action.waitForCompletion();
System.out.println("Recording URL: " + event.getStringParam("url"));
```

## Input Collection

### `playAndCollect(List<Map<String, Object>> media, Map<String, Object> collectConfig, Map<String, Object> options) -> Action.PlayAndCollectAction`

Play audio and collect DTMF or speech input. Returns a `PlayAndCollectAction`.

```java
Action.PlayAndCollectAction action = call.playAndCollect(
    List.of(Map.of("type", "tts",
        "params", Map.of("text", "Press 1 for sales, 2 for support."))),
    Map.of("digits", Map.of("max", 1, "digit_timeout", 5.0)),
    Map.of());
RelayEvent event = action.waitForCompletion();
@SuppressWarnings("unchecked")
Map<String, Object> result = (Map<String, Object>) event.getParams().get("result");
```

### `collect(Map<String, Object> collectConfig, Map<String, Object> options) -> Action.CollectAction`

Collect input without playing audio.

```java
Action.CollectAction action = call.collect(
    Map.of(
        "digits", Map.of("max", 4, "terminators", "#"),
        "speech", Map.of("language", "en-US"),
        "partial_results", true),
    null);
RelayEvent event = action.waitForCompletion();
```

## Bridging

### `connect(List<List<Map<String, Object>>> devices, Map<String, Object> options) -> Map<String, Object>`

Bridge the call to another destination. The devices matrix is `outer = sequential, inner = parallel`. There is no ring-back media parameter — play any ring-back TTS separately beforehand.

```java
call.connect(
    List.of(List.of(Map.of(
        "type", "phone",
        "params", Map.of("to_number", "+15551234567", "from_number", "+15559876543")))),
    Map.of());
```

### `disconnect() -> Map<String, Object>`

Unbridge a connected call.

```java
call.disconnect();
```

## DTMF

### `sendDigits(String digits) -> Map<String, Object>`

Send DTMF tones.

```java
call.sendDigits("1234#");
```

## Detection

### `detect(Map<String, Object> detectConfig, Map<String, Object> options) -> Action.DetectAction`

Detect machine, fax, or digits.

```java
Action.DetectAction action = call.detect(
    Map.of("type", "machine"),
    Map.of("timeout", 30.0));
RelayEvent event = action.waitForCompletion();
```

Typed convenience wrappers: `detectDigit()`, `detectAnsweringMachine()`, `detectFax()` (each with an optional options map).

## SIP Refer

### `refer(Map<String, Object> device, Map<String, Object> options) -> Map<String, Object>`

Transfer via SIP REFER.

```java
call.refer(
    Map.of("type", "sip", "params", Map.of("to", "sip:user@example.com")),
    null);
```

## Transfer

### `transfer(String dest) -> Map<String, Object>`

Transfer call control to another RELAY app or SWML script.

```java
call.transfer("https://example.com/swml-endpoint");
```

## Fax

### `sendFax(String documentUrl, Map<String, Object> options) -> Action.SendFaxAction`

```java
Action.SendFaxAction action = call.sendFax(
    "https://example.com/document.pdf",
    Map.of("identity", "+15551234567"));
RelayEvent event = action.waitForCompletion();
```

### `receiveFax(Map<String, Object> options) -> Action.ReceiveFaxAction`

```java
Action.ReceiveFaxAction action = call.receiveFax(Map.of());
RelayEvent event = action.waitForCompletion();
```

## Tap (Media Interception)

### `tap(Map<String, Object> tapConfig, Map<String, Object> tapDevice, Map<String, Object> options) -> Action.TapAction`

Intercept call media and stream to an RTP endpoint.

```java
Action.TapAction action = call.tap(
    Map.of("type", "audio", "params", Map.of("direction", "both")),
    Map.of("type", "rtp", "params", Map.of("addr", "192.168.1.100", "port", 5000)),
    Map.of());
```

## Streaming

### `stream(String url, Map<String, Object> options) -> Action.StreamAction`

Stream call audio to a WebSocket endpoint.

```java
Action.StreamAction action = call.stream(
    "wss://example.com/audio",
    Map.of("name", "my_stream", "codec", "PCMU", "track", "inbound_track"));
// Stop streaming
action.stop();
```

## Payment

### `pay(String paymentConnectorUrl, Map<String, Object> options) -> Action.PayAction`

Collect a payment via DTMF.

```java
Action.PayAction action = call.pay(
    "https://pay.example.com",
    Map.of("charge_amount", "25.99", "currency", "usd", "input_method", "dtmf"));
RelayEvent event = action.waitForCompletion();
```

## Conference

### `joinConference(String name, Map<String, Object> options) -> Map<String, Object>`

```java
call.joinConference("my_conference", Map.of("muted", false, "beep", "onEnter"));
```

### `leaveConference(String conferenceId) -> Map<String, Object>`

```java
call.leaveConference("conf-123");
```

## Hold

### `hold() -> Map<String, Object>` / `unhold() -> Map<String, Object>`

```java
call.hold();
// ... later ...
call.unhold();
```

## Denoise

### `denoise() -> Map<String, Object>` / `denoiseStop() -> Map<String, Object>`

```java
call.denoise();
// ... later ...
call.denoiseStop();
```

## Transcription

### `transcribe(Map<String, Object> options) -> Action.TranscribeAction`

```java
Action.TranscribeAction action = call.transcribe(
    Map.of("status_url", "https://example.com/transcription"));
// ... later ...
action.stop();
```

## Live Transcribe / Translate

### `liveTranscribe(Map<String, Object> action) -> Map<String, Object>`

```java
call.liveTranscribe(Map.of("start", Map.of("language", "en-US")));
```

### `liveTranslate(Map<String, Object> action, Map<String, Object> options) -> Map<String, Object>`

```java
call.liveTranslate(Map.of("start", Map.of("source", "en-US", "target", "es")), null);
```

## Echo

### `echo(Map<String, Object> options) -> Map<String, Object>`

Echo audio back to the caller (useful for testing).

```java
call.echo(Map.of("timeout", 30.0));
```

## AI Agent

### `ai(Map<String, Object> aiConfig) -> Action.AiAction`

Start an AI agent session on the call.

```java
Action.AiAction action = call.ai(Map.of(
    "prompt", Map.of("text", "You are a helpful support agent."),
    "SWAIG", Map.of("functions", List.of()),
    "params", Map.of("end_of_speech_timeout", 3000)));
RelayEvent event = action.waitForCompletion();
```

### `amazonBedrock(Map<String, Object> config) -> Map<String, Object>`

Connect to an Amazon Bedrock AI agent.

### `aiMessage(Map<String, Object> messageConfig) -> Map<String, Object>`

Send a message to an active AI session.

### `aiHold(Map<String, Object> options) -> Map<String, Object>` / `aiUnhold(Map<String, Object> options) -> Map<String, Object>`

Put an AI session on/off hold.

## Rooms

### `joinRoom(String name, Map<String, Object> options) -> Map<String, Object>`

```java
call.joinRoom("my_room", null);
```

### `leaveRoom() -> Map<String, Object>`

```java
call.leaveRoom();
```

## Queue

### `queueEnter(String queueName, Map<String, Object> options) -> Map<String, Object>`

```java
call.queueEnter("support", null);
```

### `queueLeave(String queueName, Map<String, Object> options) -> Map<String, Object>`

```java
call.queueLeave("support", Map.of("queue_id", "q-123"));
```

## Digit Bindings

### `bindDigit(String digits, String bindMethod, Map<String, Object> options) -> Map<String, Object>`

Bind a DTMF sequence to trigger a RELAY method.

```java
call.bindDigit(
    "*1",
    "calling.play",
    Map.of("bind_params", Map.of(
        "play", List.of(Map.of("type", "tts",
            "params", Map.of("text", "You pressed star-1"))))));
```

### `clearDigitBindings() -> Map<String, Object>` / `clearDigitBindings(String realm)`

```java
call.clearDigitBindings();
```

## User Events

### `userEvent(String event) -> Map<String, Object>`

Send a custom event.

```java
call.userEvent("order_placed");
```

## Event Handling

### `on(Consumer<RelayEvent> listener)`

Register an event listener on this call. The listener receives every event routed to the call; branch on `event.getEventType()`.

```java
call.on(event -> {
    if (Constants.EVENT_CALL_PLAY.equals(event.getEventType())) {
        System.out.println("Play state: " + event.getStringParam("state"));
    }
});
```

### `waitFor(String targetState) -> RelayEvent` / `waitFor(String targetState, long timeoutMs)`

Block until the call reaches a target state (one of the `Constants.CALL_STATE_*` values). Returns immediately if the call is already at or past that state.

```java
RelayEvent event = call.waitFor(Constants.CALL_STATE_ANSWERED, 30_000);
```

Convenience wrappers: `waitForRinging()`, `waitForAnswered()`, `waitForEnding()`, `waitForEnded()`.

```java
RelayEvent event = call.waitForEnded();
System.out.println("End reason: " + call.getEndReason().orElse(""));
```
