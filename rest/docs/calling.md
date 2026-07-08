# Calling Commands

The Calling API provides REST-based call control. All commands are dispatched via a single `POST /calling/calls` endpoint with a `command` field. No WebSocket connection is needed.

Access the namespace through `client.calling()`. Every method takes a typed, closed request object built with a fluent builder. Optional/unmodeled wire keys go through the builder's `extras(Map)` door. Each method returns a `Map<String, Object>` decoded from the JSON response.

<!-- snippet-setup -->
```java
import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.namespaces.generated.Calling;

RestClient client = RestClient.builder().build();
String callId = "call-uuid";
```

## How It Works

Every method on `client.calling()` sends a POST request with this structure:

```json
{
    "command": "calling.play",
    "id": "<call-uuid>",
    "params": { ... }
}
```

For `dial` and `update`, the call details are inside `params` (no top-level `id`). For all other commands, `id` is the call UUID passed as the first argument.

## Call Lifecycle

### `dial(DialRequest request) -> Map<String, Object>`

Initiate an outbound call.

```java
var result = client.calling().dial(
    Calling.DialRequest.builder()
        .from("+15559876543")
        .to("+15551234567")
        .url("https://example.com/call-handler")
        .build());
callId = (String) result.get("id");
```

### `update(UpdateRequest request) -> Map<String, Object>`

Update an active call's dialplan mid-call.

```java
client.calling().update(
    Calling.UpdateRequest.builder()
        .id(callId)
        .url("https://example.com/new-handler")
        .build());
```

### `end(String callId, EndRequest request) -> Map<String, Object>`

Terminate a call.

```java
client.calling().end(callId,
    Calling.EndRequest.builder().reason("hangup").build());
```

### `transfer(String callId, TransferRequest request) -> Map<String, Object>`

Transfer a call to a new destination.

```java
client.calling().transfer(callId,
    Calling.TransferRequest.builder()
        .dest(Map.of("to", "sip:agent@example.com"))
        .build());
```

### `disconnect(String callId, DisconnectRequest request) -> Map<String, Object>`

Disconnect bridged calls without hanging up either leg.

```java
client.calling().disconnect(callId,
    Calling.DisconnectRequest.builder().build());
```

## Audio Playback

### `play(String callId, PlayRequest request) -> Map<String, Object>`

Play audio, TTS, silence, or ringtone.

```java
client.calling().play(callId,
    Calling.PlayRequest.builder()
        .play(List.of(Map.of("type", "tts", "text", "Hello!")))
        .volume(5.0)
        .build());
```

### `playPause` / `playResume`

Pause or resume active playback.

```java
client.calling().playPause(callId,
    Calling.PlayPauseRequest.builder().controlId("ctrl-1").build());
client.calling().playResume(callId,
    Calling.PlayResumeRequest.builder().controlId("ctrl-1").build());
```

### `playStop`

Stop active playback.

```java
client.calling().playStop(callId,
    Calling.PlayStopRequest.builder().controlId("ctrl-1").build());
```

### `playVolume`

Adjust playback volume.

```java
client.calling().playVolume(callId,
    Calling.PlayVolumeRequest.builder().controlId("ctrl-1").volume(-3.0).build());
```

## Recording

### `record` / `recordPause` / `recordResume` / `recordStop`

```java
client.calling().record(callId,
    Calling.RecordRequest.builder()
        .controlId("rec-1")
        .audio(Map.of("beep", true, "format", "wav", "stereo", true))
        .build());
client.calling().recordPause(callId,
    Calling.RecordPauseRequest.builder().controlId("rec-1").build());
client.calling().recordResume(callId,
    Calling.RecordResumeRequest.builder().controlId("rec-1").build());
client.calling().recordStop(callId,
    Calling.RecordStopRequest.builder().controlId("rec-1").build());
```

## Input Collection

### `collect` / `collectStop` / `collectStartInputTimers`

```java
client.calling().collect(callId,
    Calling.CollectRequest.builder()
        .controlId("coll-1")
        .digits(Map.of("max", 4, "terminators", "#"))
        .speech(Map.of("end_silence_timeout", 2.0))
        .build());
client.calling().collectStop(callId,
    Calling.CollectStopRequest.builder().controlId("coll-1").build());
client.calling().collectStartInputTimers(callId,
    Calling.CollectStartInputTimersRequest.builder().controlId("coll-1").build());
```

## Detection

### `detect` / `detectStop`

```java
client.calling().detect(callId,
    Calling.DetectRequest.builder()
        .controlId("det-1")
        .detect(Map.of("type", "machine", "params", Map.of("initial_timeout", 4.5)))
        .build());
client.calling().detectStop(callId,
    Calling.DetectStopRequest.builder().controlId("det-1").build());
```

## Tap & Stream

### `tap` / `tapStop`

```java
client.calling().tap(callId,
    Calling.TapRequest.builder()
        .controlId("tap-1")
        .tap(Map.of("type", "audio", "params", Map.of("direction", "both")))
        .device(Map.of("type", "rtp", "params", Map.of("addr", "192.168.1.1", "port", 1234)))
        .build());
client.calling().tapStop(callId,
    Calling.TapStopRequest.builder().controlId("tap-1").build());
```

### `stream` / `streamStop`

```java
client.calling().stream(callId,
    Calling.StreamRequest.builder()
        .controlId("str-1")
        .url("wss://example.com/audio-stream")
        .codec("PCMU")
        .build());
client.calling().streamStop(callId,
    Calling.StreamStopRequest.builder().controlId("str-1").build());
```

## Denoise

### `denoise` / `denoiseStop`

```java
client.calling().denoise(callId, Calling.DenoiseRequest.builder().build());
client.calling().denoiseStop(callId, Calling.DenoiseStopRequest.builder().build());
```

## Transcription

### `transcribe` / `transcribeStop`

```java
client.calling().transcribe(callId,
    Calling.TranscribeRequest.builder()
        .controlId("tx-1")
        .statusUrl("https://example.com/hook")
        .build());
client.calling().transcribeStop(callId,
    Calling.TranscribeStopRequest.builder().controlId("tx-1").build());
```

## AI

### `aiMessage`

Inject a message into an active AI session.

```java
client.calling().aiMessage(callId,
    Calling.AiMessageRequest.builder()
        .role("user")
        .messageText("Transfer me to billing")
        .build());
```

### `aiHold` / `aiUnhold`

```java
client.calling().aiHold(callId,
    Calling.AiHoldRequest.builder()
        .timeout(60L)
        .prompt("Please wait while I transfer you.")
        .build());
client.calling().aiUnhold(callId,
    Calling.AiUnholdRequest.builder()
        .prompt("I'm back, how can I help?")
        .build());
```

### `aiStop`

```java
client.calling().aiStop(callId,
    Calling.AiStopRequest.builder().controlId("ai-1").build());
```

## Live Transcribe & Translate

The `action` field is a structured object passed as a `Map`.

```java
client.calling().liveTranscribe(callId,
    Calling.LiveTranscribeRequest.builder()
        .action(Map.of("start", Map.of("lang", "en")))
        .build());
client.calling().liveTranslate(callId,
    Calling.LiveTranslateRequest.builder()
        .action(Map.of("start", Map.of("from_lang", "en", "to_lang", "es")))
        .build());
```

## Fax

```java
client.calling().sendFaxStop(callId,
    Calling.SendFaxStopRequest.builder().controlId("fax-1").build());
client.calling().receiveFaxStop(callId,
    Calling.ReceiveFaxStopRequest.builder().controlId("fax-1").build());
```

## SIP & Custom Events

```java
// SIP REFER transfer
client.calling().refer(callId,
    Calling.ReferRequest.builder()
        .device(Map.of("to", "sip:agent@example.com"))
        .build());

// Custom event
client.calling().userEvent(callId,
    Calling.UserEventRequest.builder()
        .event(Map.of("type", "custom", "data", Map.of("key", "value")))
        .build());
```

## Complete Method List

| Method | Command | Requires callId |
|--------|---------|:-:|
| `dial(DialRequest)` | `dial` | No |
| `update(UpdateRequest)` | `update` | No |
| `end(callId, EndRequest)` | `calling.end` | Yes |
| `transfer(callId, TransferRequest)` | `calling.transfer` | Yes |
| `disconnect(callId, DisconnectRequest)` | `calling.disconnect` | Yes |
| `play(callId, PlayRequest)` | `calling.play` | Yes |
| `playPause(callId, PlayPauseRequest)` | `calling.play.pause` | Yes |
| `playResume(callId, PlayResumeRequest)` | `calling.play.resume` | Yes |
| `playStop(callId, PlayStopRequest)` | `calling.play.stop` | Yes |
| `playVolume(callId, PlayVolumeRequest)` | `calling.play.volume` | Yes |
| `record(callId, RecordRequest)` | `calling.record` | Yes |
| `recordPause(callId, RecordPauseRequest)` | `calling.record.pause` | Yes |
| `recordResume(callId, RecordResumeRequest)` | `calling.record.resume` | Yes |
| `recordStop(callId, RecordStopRequest)` | `calling.record.stop` | Yes |
| `collect(callId, CollectRequest)` | `calling.collect` | Yes |
| `collectStop(callId, CollectStopRequest)` | `calling.collect.stop` | Yes |
| `collectStartInputTimers(callId, CollectStartInputTimersRequest)` | `calling.collect.start_input_timers` | Yes |
| `detect(callId, DetectRequest)` | `calling.detect` | Yes |
| `detectStop(callId, DetectStopRequest)` | `calling.detect.stop` | Yes |
| `tap(callId, TapRequest)` | `calling.tap` | Yes |
| `tapStop(callId, TapStopRequest)` | `calling.tap.stop` | Yes |
| `stream(callId, StreamRequest)` | `calling.stream` | Yes |
| `streamStop(callId, StreamStopRequest)` | `calling.stream.stop` | Yes |
| `denoise(callId, DenoiseRequest)` | `calling.denoise` | Yes |
| `denoiseStop(callId, DenoiseStopRequest)` | `calling.denoise.stop` | Yes |
| `transcribe(callId, TranscribeRequest)` | `calling.transcribe` | Yes |
| `transcribeStop(callId, TranscribeStopRequest)` | `calling.transcribe.stop` | Yes |
| `aiMessage(callId, AiMessageRequest)` | `calling.ai_message` | Yes |
| `aiHold(callId, AiHoldRequest)` | `calling.ai_hold` | Yes |
| `aiUnhold(callId, AiUnholdRequest)` | `calling.ai_unhold` | Yes |
| `aiStop(callId, AiStopRequest)` | `calling.ai.stop` | Yes |
| `liveTranscribe(callId, LiveTranscribeRequest)` | `calling.live_transcribe` | Yes |
| `liveTranslate(callId, LiveTranslateRequest)` | `calling.live_translate` | Yes |
| `sendFaxStop(callId, SendFaxStopRequest)` | `calling.send_fax.stop` | Yes |
| `receiveFaxStop(callId, ReceiveFaxStopRequest)` | `calling.receive_fax.stop` | Yes |
| `refer(callId, ReferRequest)` | `calling.refer` | Yes |
| `userEvent(callId, UserEventRequest)` | `calling.user_event` | Yes |
