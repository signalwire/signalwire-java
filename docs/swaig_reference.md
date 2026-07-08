# FunctionResult Methods Reference

SWAIG (SignalWire AI Gateway) is the platform's AI tool-calling system -- it connects the AI's decisions to actions like call transfers, SMS, recordings, and API calls, with native access to the media stack. This document provides a complete reference for all methods available in the `FunctionResult` class (`com.signalwire.sdk.swaig.FunctionResult`). These methods provide convenient abstractions for SWAIG actions, eliminating the need to manually construct action JSON objects.

> Java note: helper methods use camelCase (`updateGlobalData`, `sendSms`, `joinConference`),
> `FunctionResult` is the Java class name (the Python reference calls it `SwaigFunctionResult`),
> and there are no keyword arguments -- Java exposes overloads (a convenience form plus a
> full-arity positional form). The examples below assume `import com.signalwire.sdk.swaig.FunctionResult;`
> and `import java.util.*;`.

<!-- snippet-setup -->
```java
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.agent.AgentBase;

FunctionResult result = new FunctionResult("...");
AgentBase agent = AgentBase.builder().name("ref").build();
```

## Core Methods

### Basic Construction & Control

#### `FunctionResult(String response)` / `FunctionResult(String response, boolean postProcess)`
Creates a new result object with optional response text and post-processing behavior.

```java
result = new FunctionResult("Hello, I'll help you with that");
var result2 = new FunctionResult("Processing request...", true);
```

#### `setResponse(String response)`
Sets or updates the response text that the AI will speak.

```java
result.setResponse("I've updated your information");
```

#### `setPostProcess(boolean postProcess)`
Controls whether AI gets one more turn before executing actions.

```java
result.setPostProcess(true);   // AI speaks response before executing actions
result.setPostProcess(false);  // Actions execute immediately
```

---

## Action Methods

### Call Control Actions

#### `executeSwml(Object swmlContent)` / `executeSwml(Object swmlContent, boolean transfer)`
Execute SWML content with flexible input support and optional transfer behavior. The content may be a raw JSON string, a `Map` (SWML document), or an SWML SDK object.

```java
// Raw SWML string
result.executeSwml("{\"version\":\"1.0.0\",\"sections\":{\"main\":[{\"say\":\"Hello\"}]}}");

// SWML as a Map
Map<String, Object> swmlDoc = Map.of(
        "version", "1.0.0",
        "sections", Map.of("main", List.of(Map.of("say", "Hello"))));
result.executeSwml(swmlDoc, true);
```

#### `connect(String destination, boolean isFinal)` / `connect(String destination, boolean isFinal, String from)`
Transfer/connect call to another destination using SWML.

```java
result.connect("+15551234567", true);                            // Permanent transfer
result.connect("support@company.com", false, "+15559876543");    // Temporary transfer
```

#### `sendSms(String toNumber, String fromNumber, String body, List<String> media, List<String> tags, String region)`
Send SMS message to a PSTN phone number using SWML. A convenience overload drops the trailing `region`.

```java
// Simple text message
result.sendSms(
        "+15551234567",
        "+15559876543",
        "Your order has been confirmed!",
        null, null);

// Media message with images (no body)
result.sendSms(
        "+15551234567",
        "+15559876543",
        null,
        List.of("https://example.com/receipt.jpg", "https://example.com/map.png"),
        null);

// Full featured message with tags and region
result.sendSms(
        "+15551234567",
        "+15559876543",
        "Order update with receipt attached",
        List.of("https://example.com/receipt.pdf"),
        List.of("order", "confirmation", "customer"),
        "us");
```

**Parameters:**
- `toNumber` (required): Phone number in E.164 format to send to
- `fromNumber` (required): Phone number in E.164 format to send from
- `body` (optional): Message text (required if no media) -- pass `null` to omit
- `media` (optional): List of URLs to send (required if no body) -- pass `null` to omit
- `tags` (optional): List of tags for UI searching -- pass `null` to omit
- `region` (optional): Region to originate message from

**Variables Set:**
- `send_sms_result`: "success" or "failed"

#### `pay(...)`
Process payments using SWML pay action. The convenience overload `pay(connectorUrl, inputMethod, statusUrl, timeout, maxAttempts)` uses reference defaults for everything else; the full-arity overload exposes every option positionally.

```java
// Simple payment setup (convenience overload)
result.pay(
        "https://api.example.com/accept-payment",  // connectorUrl
        "dtmf",                                     // inputMethod
        null,                                       // statusUrl
        5,                                          // timeout
        1);                                         // maxAttempts

// Advanced payment with custom prompts (full-arity overload).
// Custom prompts are built with the static helper methods:
List<Map<String, String>> welcomeActions = List.of(
        FunctionResult.createPaymentAction("Say", "Welcome to our payment system"),
        FunctionResult.createPaymentAction("Say", "Please enter your credit card number"));
Map<String, Object> cardPrompt =
        FunctionResult.createPaymentPrompt("payment-card-number", welcomeActions, null, null);

List<Map<String, String>> errorActions = List.of(
        FunctionResult.createPaymentAction("Say", "Invalid card number, please try again"));
Map<String, Object> errorPrompt = FunctionResult.createPaymentPrompt(
        "payment-card-number", errorActions, null, "invalid-card-number timeout");

// Create payment parameters
List<Map<String, String>> params = List.of(
        FunctionResult.createPaymentParameter("customer_id", "12345"),
        FunctionResult.createPaymentParameter("order_id", "ORD-789"));

// Full payment configuration
result.pay(
        "https://api.example.com/accept-payment",   // connectorUrl
        "dtmf",                                      // inputMethod
        "https://api.example.com/payment-status",    // statusUrl
        "credit-card",                               // paymentMethod
        10,                                          // timeout
        3,                                           // maxAttempts
        true,                                        // securityCode
        Boolean.FALSE,                               // postalCode (bool or literal string)
        0,                                           // minPostalCodeLength
        "one-time",                                  // tokenType
        "25.50",                                     // chargeAmount
        "usd",                                       // currency
        "en-US",                                     // language
        "polly.Sally",                               // voice
        "Premium service upgrade",                   // description
        "visa mastercard amex",                      // validCardTypes
        params,                                      // parameters
        List.of(cardPrompt, errorPrompt),            // prompts
        null);                                       // aiResponse (null = default)
```

**Core Parameters:**
- `connectorUrl` (required): URL to process payment requests
- `inputMethod`: "dtmf" or "voice" (reference default: "dtmf")
- `paymentMethod`: "credit-card" (default: "credit-card")
- `timeout`: Seconds to wait for input (convenience default: 5)
- `maxAttempts`: Number of retry attempts (convenience default: 1)

**Security & Validation:**
- `securityCode`: Prompt for CVV (default: true)
- `postalCode`: Prompt for postal code -- `Boolean` or a literal postcode string (default: true)
- `minPostalCodeLength`: Minimum postal code digits (default: 0)
- `validCardTypes`: Space-separated card types (default: "visa mastercard amex")

**Payment Configuration:**
- `tokenType`: "one-time" or "reusable" (default: "reusable")
- `chargeAmount`: Amount as decimal string
- `currency`: Currency code (default: "usd")
- `description`: Payment description

**Customization:**
- `language`: Prompt language (default: "en-US")
- `voice`: TTS voice (default: "woman")
- `statusUrl`: URL for status notifications
- `parameters`: Additional name/value pairs for the connector
- `prompts`: Custom prompt configurations
- `aiResponse`: Override the AI response set before the pay verb (`null` = default)

**Helper Methods for Payment Setup:**
```java
// Create payment action
Map<String, String> action = FunctionResult.createPaymentAction("Say", "Enter card number");

// Create payment prompt (forSituation, payActions, cardType, errorType)
Map<String, Object> prompt = FunctionResult.createPaymentPrompt(
        "payment-card-number", List.of(action), null, "invalid-card-number");

// Create payment parameter
Map<String, String> param = FunctionResult.createPaymentParameter("customer_id", "12345");
```

**Variables Set:**
- `pay_result`: "success", "too-many-failed-attempts", "payment-connector-error", etc.
- `pay_payment_results`: JSON with payment details including tokens and card info

#### `recordCall(...)`
Start background call recording using SWML. `recordCall()` records with defaults; the full-arity overload exposes every option positionally.

Unlike foreground recording, the script continues executing while recording happens in the background.

```java
// Simple background recording
result.recordCall();

// Recording with custom settings (controlId, stereo, format, direction,
// terminators, beep, inputSensitivity, initialTimeout, endSilenceTimeout,
// maxLength, statusUrl)
result.recordCall(
        "support_call_001",  // controlId
        true,                // stereo
        "mp3",               // format
        "both",              // direction
        null,                // terminators
        false,               // beep
        44.0,                // inputSensitivity
        null,                // initialTimeout
        null,                // endSilenceTimeout
        300.0,               // maxLength (5 minutes)
        null);               // statusUrl

// Recording with terminator and status webhook
result.recordCall(
        "customer_voicemail",
        false,
        "wav",
        "speak",             // Only capture customer voice
        "#",                 // Stop on '#' press
        true,                // Play beep before recording
        44.0,
        4.0,                 // Wait 4 seconds for speech
        3.0,                 // Stop after 3 seconds of silence
        null,
        "https://api.example.com/recording-status");
```

**Core Parameters:**
- `controlId` (optional): Identifier for this recording (for use with `stopRecordCall`)
- `stereo`: Record in stereo (default: false)
- `format`: "wav", "mp3", or "mp4" (default: "wav")
- `direction`: "speak", "listen", or "both" (default: "both")

**Control Options:**
- `terminators`: Digits that stop recording when pressed
- `beep`: Play beep before recording (default: false)
- `maxLength`: Maximum recording length in seconds

**Timing Options:**
- `inputSensitivity`: Input sensitivity (default: 44.0)
- `initialTimeout`: Time to wait for speech start
- `endSilenceTimeout`: Time to wait in silence before ending

**Webhook Options:**
- `statusUrl`: URL to send recording status events to

**Variables Set:**
- `record_call_result`: "success" or "failed"
- `record_call_url`: URL of recorded file (when recording completes)

#### `stopRecordCall(String controlId)` / `stopRecordCall()`
Stop an active background call recording using SWML.

```java
// Stop the most recent recording
result.stopRecordCall();

// Stop specific recording by ID
result.stopRecordCall("support_call_001");

// Chain to stop recording and provide feedback
result.stopRecordCall("customer_voicemail")
      .say("Thank you, your message has been recorded");
```

**Parameters:**
- `controlId` (optional): Identifier for recording to stop. If not provided, stops the most recent recording.

**Variables Set:**
- `stop_record_call_result`: "success" or "failed"

#### `joinRoom(String name)`
Join a RELAY room using SWML.

RELAY rooms enable multi-party communication and collaboration features.

```java
// Join a conference room
result.joinRoom("support_team_room");

// Join customer meeting room
result.joinRoom("customer_meeting_001")
      .say("Welcome to the customer meeting room");

// Join room and set metadata
result.joinRoom("sales_conference")
      .setMetadata(Map.of(
              "participant_role", "moderator",
              "join_time", "2024-01-01T12:00:00Z"));
```

**Parameters:**
- `name` (required): The name of the room to join

**Variables Set:**
- `join_room_result`: "success" or "failed"

#### `sipRefer(String toUri)`
Send SIP REFER for call transfer using SWML.

SIP REFER is used for call transfer in SIP environments, allowing one endpoint to request another to initiate a new connection.

```java
// Basic SIP refer to transfer call
result.sipRefer("sip:support@company.com");

// Transfer to specific SIP address with domain
result.sipRefer("sip:agent123@pbx.company.com:5060");

// Chain with announcement
result.say("Transferring your call to our specialist")
      .sipRefer("sip:specialist@company.com");
```

**Parameters:**
- `toUri` (required): The SIP URI to send the REFER to

**Variables Set:**
- `sip_refer_result`: "success" or "failed"

#### `joinConference(...)`
Join an ad-hoc audio conference with RELAY and CXML calls using SWML. `joinConference(name)` joins with defaults; the full-arity overload exposes every option positionally.

```java
// Simple conference join
result.joinConference("my_conference");

// Advanced conference with callbacks and coaching (full-arity overload:
// name, muted, beep, startOnEnter, endOnExit, waitUrl, maxParticipants,
// record, region, trim, coach, statusCallbackEvent, statusCallback,
// statusCallbackMethod, recordingStatusCallback,
// recordingStatusCallbackMethod, recordingStatusCallbackEvent, result)
result.joinConference(
        "customer_support_conf",                      // name
        false,                                        // muted
        "onEnter",                                    // beep
        true,                                         // startOnEnter
        false,                                        // endOnExit
        null,                                         // waitUrl
        50,                                           // maxParticipants
        "record-from-start",                          // record
        "us-east",                                    // region
        "trim-silence",                               // trim
        null,                                         // coach
        "start end join leave",                       // statusCallbackEvent
        "https://api.company.com/conference-events",  // statusCallback
        "POST",                                       // statusCallbackMethod
        "https://api.company.com/recording-events",   // recordingStatusCallback
        "POST",                                       // recordingStatusCallbackMethod
        "completed",                                  // recordingStatusCallbackEvent
        null);                                        // result

// Chain with other actions
result.say("Joining you to the team conference")
      .joinConference("team_meeting")
      .setMetadata(Map.of("meeting_type", "team_sync", "participant_role", "attendee"));
```

**Core Parameters:**
- `name` (required): Name of conference to join
- `muted`: Join muted (default: false)
- `beep`: Beep configuration -- "true", "false", "onEnter", "onExit"
- `startOnEnter`: Conference starts when this participant enters
- `endOnExit`: Conference ends when this participant exits

**Capacity & Region:**
- `maxParticipants`: Maximum participants <= 250
- `region`: Conference region for optimization
- `waitUrl`: SWML URL for custom hold music

**Recording Options:**
- `record`: "do-not-record" or "record-from-start"
- `trim`: "trim-silence" or "do-not-trim"
- `recordingStatusCallback`: URL for recording status events
- `recordingStatusCallbackMethod`: "GET" or "POST"
- `recordingStatusCallbackEvent`: "in-progress completed absent"

**Status & Coaching:**
- `coach`: SWML Call ID or CXML CallSid for coaching features
- `statusCallback`: URL for conference status events
- `statusCallbackMethod`: "GET" or "POST"
- `statusCallbackEvent`: Events to report

**Control Flow:**
- `result`: Switch on return_value (`Map` or `List` for conditional logic)

**Variables Set:**
- `join_conference_result`: "completed", "answered", "no-answer", "failed", or "canceled"
- `return_value`: Same as `join_conference_result`

#### `tap(...)`
Start background call tap using SWML.

Media is streamed over Websocket or RTP to a customer-controlled URI for real-time monitoring and analysis.

```java
// Simple WebSocket tap (uri, controlId, direction, codec)
result.tap("wss://example.com/tap", null, "both", "PCMU");

// RTP tap with custom settings (uri, controlId, direction, codec, rtpPtime, statusUrl)
result.tap(
        "rtp://192.168.1.100:5004",  // uri
        "monitoring_tap_001",        // controlId
        "both",                      // direction
        "PCMA",                      // codec
        30,                          // rtpPtime
        null);                       // statusUrl

// Advanced tap with status callbacks
result.tap(
        "wss://monitoring.company.com/audio-stream",
        "compliance_tap",
        "speak",   // Only what the party says
        "PCMU",
        20,
        "https://api.company.com/tap-status")
      .setMetadata(Map.of("tap_purpose", "compliance", "session_id", "sess_123"));
```

**Core Parameters:**
- `uri` (required): Destination of tap media stream
  - WebSocket: `ws://example.com` or `wss://example.com`
  - RTP: `rtp://IP:port`
- `controlId`: Identifier for this tap to use with `stopTap` (pass `null` to auto-generate)

**Audio Configuration:**
- `direction`: Audio direction to tap (default: "both")
  - `"speak"`: What party says
  - `"hear"`: What party hears
  - `"both"`: What party hears and says
- `codec`: Codec for tap stream -- "PCMU" or "PCMA" (default: "PCMU")
- `rtpPtime`: RTP packetization time in milliseconds (default: 20)

**Status & Monitoring:**
- `statusUrl`: URL for tap status change requests

**Variables Set:**
- `tap_uri`: Destination URI of the newly started tap
- `tap_result`: "success" or "failed"
- `tap_control_id`: Control ID of this tap
- `tap_rtp_src_addr`: If RTP, source address of the tap stream
- `tap_rtp_src_port`: If RTP, source port of the tap stream
- `tap_ptime`: Packetization time of the tap stream
- `tap_codec`: Codec in the tap stream
- `tap_rate`: Sample rate in the tap stream

#### `stopTap(String controlId)` / `stopTap()`
Stop an active tap stream using SWML.

```java
// Stop the most recent tap
result.stopTap();

// Stop specific tap by ID
result.stopTap("monitoring_tap_001");

// Chain to stop tap and provide feedback
result.stopTap("compliance_tap")
      .say("Audio monitoring has been stopped")
      .updateGlobalData(Map.of("tap_active", false));
```

**Parameters:**
- `controlId` (optional): ID of the tap to stop. If not set, the last tap started will be stopped.

**Variables Set:**
- `stop_tap_result`: "success" or "failed"

#### `hangup()`
Terminate the call immediately.

```java
result.hangup();
```

---

### Call Flow Control

#### `hold(int timeout)`
Put call on hold with timeout (max 900 seconds).

```java
result.hold(60);    // Hold for 1 minute
result.hold(600);   // Hold for 10 minutes
```

#### `waitForUser(Boolean enabled, Integer timeout, boolean answerFirst)` / `waitForUser()`
Control how the agent waits for user input.

```java
result.waitForUser(true, null, false);    // Wait indefinitely
result.waitForUser(null, 30, false);      // Wait 30 seconds
result.waitForUser(null, null, true);     // Special answer-first mode
result.waitForUser(false, null, false);   // Disable waiting
```

#### `stop()`
Stop agent execution completely.

```java
result.stop();
```

---

### Speech & Audio Control

#### `say(String text)`
Make the agent speak specific text immediately.

```java
result.say("Please hold while I look that up for you");
```

#### `playBackgroundFile(String filename)` / `playBackgroundFile(String filename, boolean wait)`
Play audio file in background with attention control.

```java
result.playBackgroundFile("hold_music.wav");                // AI tries to get attention
result.playBackgroundFile("announcement.mp3", true);        // AI suppresses attention
```

#### `stopBackgroundFile()`
Stop currently playing background audio.

```java
result.stopBackgroundFile();
```

---

### Speech Recognition Settings

#### `setEndOfSpeechTimeout(int milliseconds)`
Set silence timeout after speech detection for finalizing recognition.

```java
result.setEndOfSpeechTimeout(2000);  // 2 seconds of silence
```

#### `setSpeechEventTimeout(int milliseconds)`
Set timeout since last speech event -- better for noisy environments.

```java
result.setSpeechEventTimeout(3000);  // 3 seconds since last speech event
```

---

### Data Management

#### `updateGlobalData(Map<String, Object> data)`
Update global agent data variables.

```java
result.updateGlobalData(Map.of("user_name", "John", "step", 2));
```

#### `removeGlobalData(Object keys)`
Remove global data variables by key(s) -- a single `String` or a `List<String>`.

```java
result.removeGlobalData("temporary_data");                 // Single key
result.removeGlobalData(List.of("step", "temp_value"));    // Multiple keys
```

#### `setMetadata(Map<String, Object> data)`
Set metadata scoped to current function's meta_data_token.

```java
result.setMetadata(Map.of("session_id", "abc123", "user_tier", "premium"));
```

#### `removeMetadata(Object keys)`
Remove metadata from current function's scope -- a single `String` or a `List<String>`.

```java
result.removeMetadata("temp_session_data");                // Single key
result.removeMetadata(List.of("cache_key", "temp_flag"));  // Multiple keys
```

---

### Function & Behavior Control

#### `toggleFunctions(List<Map<String, Object>> toggles)`
Enable/disable specific SWAIG functions dynamically.

```java
result.toggleFunctions(List.of(
        Map.of("function", "transfer_call", "active", false),
        Map.of("function", "lookup_info", "active", true)));
```

#### `enableFunctionsOnTimeout(boolean enabled)`
Control whether functions can be called on speaker timeout.

```java
result.enableFunctionsOnTimeout(true);
result.enableFunctionsOnTimeout(false);
```

#### `enableExtensiveData(boolean enabled)`
Send full data to LLM for this turn only, then use smaller replacement.

```java
result.enableExtensiveData(true);   // Send extensive data this turn
result.enableExtensiveData(false);  // Use normal data
```

#### `replaceInHistory(String text)` / `replaceInHistory(boolean summary)`
Remove or replace the tool_call + tool_result pair from the LLM's conversation history after the first send. This is useful when a function call is an implementation detail that would confuse the model if it remained visible in context.

When called with a `String`, the tool_call/tool_result pair is replaced with an assistant message containing that text. When called with `true`, the pair is removed entirely -- the LLM will never see that the function was called.

```java
// Remove entirely — LLM won't see this function was called
result = new FunctionResult("Done.");
result.replaceInHistory(true);

// Replace with a friendly assistant message instead of tool artifacts
var saved = new FunctionResult("Profile saved.");
saved.replaceInHistory("I've saved your profile information.");

// Practical example: data collection tool that shouldn't clutter history
agent.defineTool("save_answer", "Save the user's answer",
        Map.of("type", "object", "properties", Map.of(
                "answer", Map.of("type", "string"))),
        (args, raw) -> {
            String answer = (String) args.get("answer");
            return new FunctionResult("Answer recorded: " + answer)
                    .replaceInHistory(true);  // Keep history clean
        });
```

**When to use:**
- Functions that are implementation details (saving data, logging, internal state changes)
- Functions called frequently that would bloat conversation history
- Situations where tool artifacts confuse the model's reasoning (especially with reasoning models at low effort settings)

**Note:** For structured data collection, consider using [gather_info mode](contexts_guide.md#gather-info-mode) instead, which produces zero tool artifacts by design and doesn't require `replaceInHistory`.

---

### Agent Settings & Configuration

#### `updateSettings(Map<String, Object> settings)`
Update agent runtime settings with validation.

```java
// AI model settings
result.updateSettings(Map.of(
        "temperature", 0.7,
        "max-tokens", 2048,
        "frequency-penalty", -0.5));

// Speech recognition settings
result.updateSettings(Map.of(
        "confidence", 0.8,
        "barge-confidence", 0.7));
```

**Supported Settings:**
- `frequency-penalty`: Float (-2.0 to 2.0)
- `presence-penalty`: Float (-2.0 to 2.0)
- `max-tokens`: Integer (0 to 4096)
- `top-p`: Float (0.0 to 1.0)
- `confidence`: Float (0.0 to 1.0)
- `barge-confidence`: Float (0.0 to 1.0)
- `temperature`: Float (0.0 to 2.0, clamped to 1.5)

#### `switchContext(String systemPrompt)` / `switchContext(String systemPrompt, String userPrompt, boolean consolidate, boolean fullReset)`
Change agent context/prompt during conversation.

```java
// Simple context switch
result.switchContext("You are now a technical support agent");

// Advanced context switch
result.switchContext(
        "You are a billing specialist",           // systemPrompt
        "The user needs help with their invoice", // userPrompt
        true,                                     // consolidate
        false);                                   // fullReset
```

#### `simulateUserInput(String text)`
Queue simulated user input for testing or flow control.

```java
result.simulateUserInput("Yes, I'd like to speak to billing");
```

---

## Low-Level Methods

### Manual Action Construction

#### `addAction(String name, Object data)`
Add a single action manually (for custom actions not covered by helper methods).

```java
result.addAction("custom_action", Map.of("param", "value"));
```

#### `addActions(List<Map<String, Object>> actionList)`
Add multiple actions at once.

```java
result.addActions(List.of(
        Map.of("say", "Hello"),
        Map.of("hold", 300)));
```

### Output Generation

#### `toMap()` / `toJson()`
Convert result to a `Map` (or JSON string) for serialization.

```java
Map<String, Object> resultMap = result.toMap();
// Returns: {"response": "...", "action": [...], "post_process": true/false}
String json = result.toJson();
```

---

## Method Chaining

All methods return `this` to enable fluent method chaining:

```java
result = new FunctionResult("Processing your request", true)
        .updateGlobalData(Map.of("status", "processing"))
        .playBackgroundFile("processing.wav", true)
        .setEndOfSpeechTimeout(2500);

// Complex chaining example
var transfer = new FunctionResult("Let me transfer you to billing")
        .setMetadata(Map.of("transfer_reason", "billing_inquiry"))
        .updateGlobalData(Map.of("last_action", "transfer_to_billing"))
        .connect("+15551234567", true);
```

---

## Implementation Status

- **[IMPLEMENTED]**: `connect()`, `updateGlobalData()`, and all methods listed above
- **[HELPER METHODS]**: `sendSms()`, `pay()`, `recordCall()`, `stopRecordCall()`, `joinRoom()`, `sipRefer()`, `joinConference()`, `tap()`, `stopTap()` -- additional convenience methods that generate SWML
- **[UTILITY METHODS]**: `createPaymentPrompt()`, `createPaymentAction()`, `createPaymentParameter()` (static)
- **[EXTENSIBLE]**: Additional convenience methods for common SWML patterns

## Best Practices

1. **Use `postProcess = true`** when you want the AI to speak before executing actions
2. **Chain methods** for cleaner, more readable code
3. **Use specific methods** instead of manual action construction when available
4. **Handle errors gracefully** -- methods may throw `IllegalArgumentException` for invalid inputs
5. **Validate settings** -- `updateSettings()` relies on server-side validation

### Final State
The framework includes **10 virtual helpers total**:
1. `connect()` -- Call transfer/connect
2. `sendSms()` -- SMS messaging
3. `pay()` -- Payment processing
4. `recordCall()` -- Start background recording
5. `stopRecordCall()` -- Stop background recording
6. `joinRoom()` -- Join RELAY room
7. `sipRefer()` -- SIP REFER transfer
8. `joinConference()` -- Join audio conference with extensive options
9. `tap()` -- Start background call tap for monitoring
10. `stopTap()` -- Stop background call tap

---

## Post Data Reference

The `post_data` object is the JSON payload sent to SWAIG function handlers. Its structure differs between webhook functions and DataMap functions.

### Base Keys (All Functions)

| Key | Type | Description |
|-----|------|-------------|
| `app_name` | string | Name of the AI application |
| `function` | string | Name of the SWAIG function being called |
| `call_id` | string | Unique UUID of the current call session |
| `ai_session_id` | string | Unique UUID of the AI session |
| `caller_id_name` | string | Caller ID name (if available) |
| `caller_id_num` | string | Caller ID number (if available) |
| `channel_active` | boolean | Whether the channel is currently up |
| `channel_offhook` | boolean | Whether the channel is off-hook |
| `channel_ready` | boolean | Whether the AI session is ready |
| `argument` | object | Parsed function arguments |
| `argument_desc` | object | Function argument schema/description |
| `purpose` | string | Description of what the function does |
| `content_type` | string | Always `"text/swaig"` |
| `version` | string | SWAIG protocol version |
| `global_data` | object | Application-level global data (when set) |
| `conversation_id` | string | Conversation identifier (when tracking enabled) |
| `project_id` | string | SignalWire project ID |
| `space_id` | string | SignalWire space ID |

### Webhook-Only Keys

These keys are only present for traditional webhook SWAIG functions:

| Key | Type | Description | Present When |
|-----|------|-------------|--------------|
| `meta_data_token` | string | Token for metadata access | Function has metadata token |
| `meta_data` | object | Function-level metadata | Function has metadata token |
| `SWMLVars` | object | SWML variables | `swaig_post_swml_vars` parameter set |
| `SWMLCall` | object | SWML call state | `swaig_post_swml_vars` parameter set |
| `call_log` | array | Processed conversation history | `swaig_post_conversation` is true |
| `raw_call_log` | array | Raw conversation history | `swaig_post_conversation` is true |

**Metadata scoping**: Functions sharing the same `meta_data_token` share access to the same metadata. If no token is specified, scope defaults to function name/URL.

**Conversation history**: `call_log` may shrink after conversation resets (consolidation), while `raw_call_log` preserves full history. Both include timing data (latency, utterance_latency, audio_latency).

### DataMap-Specific Keys

| Key | Type | Description |
|-----|------|-------------|
| `prompt_vars` | object | Template variables built from call context, SWML vars, and global_data |
| `args` | object | First parsed argument object for easy template access |
| `input` | object | Copy of entire post_data for variable expansion |

### prompt_vars Contents

| Key | Source | Description |
|-----|--------|-------------|
| `call_direction` | Call direction | `"inbound"` or `"outbound"` |
| `caller_id_name` | Channel variable | Caller's name |
| `caller_id_number` | Channel variable | Caller's number |
| `local_date` | System time | Current date in local timezone |
| `local_time` | System time | Current time with timezone |
| `time_of_day` | Derived from hour | `"morning"`, `"afternoon"`, or `"evening"` |
| `supported_languages` | App config | Available languages |
| `default_language` | App config | Primary language |

All keys from `global_data` are also merged into `prompt_vars`, with global_data taking precedence.

### SWML Parameters Controlling post_data

| Parameter | Type | Default | Purpose |
|-----------|------|---------|---------|
| `swaig_allow_swml` | boolean | true | Allow functions to execute SWML actions |
| `swaig_allow_settings` | boolean | true | Allow functions to modify AI settings |
| `swaig_post_conversation` | boolean | false | Include conversation history in post_data |
| `swaig_set_global_data` | boolean | true | Allow functions to modify global_data |
| `swaig_post_swml_vars` | boolean/array | false | Include SWML variables in post_data |

### Variable Expansion in DataMap

DataMap processing supports template expansion with access to:

- Nested object access via dot notation: `${user.name}`
- Array access: `${items[0].value}`
- Encoding functions: `${enc:url:variable}`
- Built-in functions: `@{strftime %Y-%m-%d}`, `@{expr 2+2}`

---

## Related Documentation

- **[API Reference](api_reference.md)** - Complete AgentBase and FunctionResult API reference
- **[Contexts Guide](contexts_guide.md)** - Using `swmlChangeContext()` and `swmlChangeStep()`
- **[DataMap Guide](datamap_guide.md)** - Using FunctionResult with DataMap outputs
- **[Agent Guide](agent_guide.md)** - General agent development guide

### Example Files

- `examples/SwaigFeaturesAgent.java` - Advanced SWAIG features
- `examples/DataMapDemo.java` - Basic DataMap usage
- `examples/AdvancedDatamapDemo.java` - Expressions, foreach, fallback outputs
