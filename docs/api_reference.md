# SignalWire AI Agents SDK - Complete API Reference

This document provides a comprehensive reference for all public APIs in the SignalWire AI Agents SDK for Java.

## Installation

Add the SDK as a dependency (Maven Central coordinates `com.signalwire:signalwire-sdk:2.0.2`).

**Gradle (`build.gradle`):**
```groovy
dependencies {
    implementation 'com.signalwire:signalwire-sdk:2.0.2'
}
```

**Maven (`pom.xml`):**
```xml
<dependency>
    <groupId>com.signalwire</groupId>
    <artifactId>signalwire-sdk</artifactId>
    <version>2.0.2</version>
</dependency>
```

## Table of Contents

1. [AgentBase Class](#agentbase-class) - Core agent functionality
2. [FunctionResult Class](#functionresult-class) - SWAIG (SignalWire AI Gateway) function response handling
3. [DataMap Class](#datamap-class) - Serverless API tools that execute on SignalWire's servers
4. [Context System](#context-system) - Structured workflows
5. [State Management](#state-management) - Persistent state
6. [Skills System](#skills-system) - Modular capabilities
7. [Utility Classes](#utility-classes) - Supporting classes

---

## AgentBase Class

The `AgentBase` class is the foundation for creating AI agents. It extends `Service` (the base class for generating SWML -- SignalWire Markup Language -- documents) and provides comprehensive functionality for building conversational AI agents.

`import com.signalwire.sdk.agent.AgentBase;`

### Construction (Builder)

Agents are constructed through the fluent `AgentBase.builder()` API, not a public constructor. Call `build()` to obtain the agent, then configure it with the instance methods below.

```java
var agent = AgentBase.builder()
    .name("my-agent")          // Human-readable name
    .route("/")                // HTTP route path (default: "/")
    .host("0.0.0.0")           // Host address to bind to (default: "0.0.0.0")
    .port(3000)                // Port number to listen on (default: 3000)
    .authUser("admin")         // HTTP basic auth username (optional)
    .authPassword("secret")    // HTTP basic auth password (optional)
    .autoAnswer(true)          // Automatically answer incoming calls (default: true)
    .maxDuration(3600)         // Max call duration in seconds
    .recordCall(false)         // Record calls by default (default: false)
    .recordFormat("mp4")       // Recording format: "mp4", "wav", "mp3"
    .recordStereo(true)        // Record in stereo (default: true)
    .signingKey("...")         // Webhook-signature signing key (optional)
    .build();
```

**Builder methods:**
- `name(String)`: Human-readable name for the agent
- `route(String)`: HTTP route path for the agent (default: `"/"`)
- `host(String)`: Host address to bind to (default: `"0.0.0.0"`)
- `port(int)`: Port number to listen on (default: `3000`)
- `authUser(String)` / `authPassword(String)`: Username/password for HTTP basic auth
- `autoAnswer(boolean)`: Automatically answer incoming calls (default: `true`)
- `maxDuration(int)`: Maximum call duration in seconds
- `recordCall(boolean)`: Record calls by default (default: `false`)
- `recordFormat(String)` / `recordFormat(RecordFormat)`: Recording format: `"mp4"`, `"wav"`, `"mp3"` (default: `"mp4"`)
- `recordStereo(boolean)`: Record in stereo (default: `true`)
- `signingKey(String)`: Key for inbound webhook signature validation
- `trustProxyForSignature(boolean)`: Trust proxy headers when validating signatures
- `envProvider(EnvProvider)`: Override the environment-variable source (useful for testing)

### Core Methods

#### Deployment and Execution

##### `run()`
Start the agent's HTTP server. Delegates to `serve()`. In serverless deployments (e.g. AWS Lambda) the base URL is auto-detected via `detectServerlessBaseUrl()`.

**Usage:**
```java
var agent = AgentBase.builder().name("my-agent").port(3000).build();
agent.run();   // blocks serving on the configured host/port
```

The port can also be overridden at runtime with the `PORT` environment variable, and host/port defaults come from the builder.

### Prompt Configuration

#### Text-Based Prompts

##### `setPromptText(String text)`
Set the agent's prompt as raw text. Returns the agent for chaining.

**Usage:**
```java
agent.setPromptText("You are a helpful customer service agent.");
```

##### `setPostPrompt(String text)`
Set additional text to append after the main prompt.

**Usage:**
```java
agent.setPostPrompt("Always be polite and professional.");
```

#### LLM Parameter Configuration

##### `setPromptLlmParams(Map<String, Object> llmParams)`
Set Language Model parameters for the main prompt. Parameters are MERGED with any previously set (successive calls accumulate; a repeated key overwrites). Values are passed through to the SignalWire server, which validates and applies them based on the target model's capabilities.

**Common Parameters:**
- `temperature`: Controls randomness. Lower = more focused
- `top_p`: Nucleus sampling threshold
- `barge_confidence`: ASR confidence to interrupt
- `presence_penalty`: Topic diversity control
- `frequency_penalty`: Repetition control

Note: No defaults are sent unless explicitly set. Invalid parameters for the selected model are handled/ignored by the server.

**Usage:**
```java
// Configure for consistent, professional responses
agent.setPromptLlmParams(Map.of(
    "temperature", 0.3,
    "top_p", 0.9,
    "barge_confidence", 0.7,
    "presence_penalty", 0.1,
    "frequency_penalty", 0.2
));
```

##### `setPostPromptLlmParams(Map<String, Object> llmParams)`
Set Language Model parameters for the post-prompt. Also merges with previous values. Passed through to the SignalWire server.

**Common Parameters:**
- `temperature`: Controls randomness. Lower = more focused
- `top_p`: Nucleus sampling threshold
- `presence_penalty`: Topic diversity control
- `frequency_penalty`: Repetition control

Note: `barge_confidence` is not applicable to post-prompt. No defaults are sent unless explicitly set.

**Usage:**
```java
// Configure for focused summaries
agent.setPostPromptLlmParams(Map.of(
    "temperature", 0.2,
    "top_p", 0.9
));
```

#### Structured Prompts (POM)

##### `promptAddSection(String title, String body, List<String> bullets)`
Add a structured section to the prompt using the Prompt Object Model. Overloads: `promptAddSection(title, body)` (no bullets).

**Parameters:**
- `title`: Section title/heading
- `body`: Main section content (pass `""` for none)
- `bullets`: List of bullet points

**Usage:**
```java
// Simple section
agent.promptAddSection("Role", "You are a customer service representative.");

// Section with bullets
agent.promptAddSection(
    "Guidelines",
    "Follow these principles:",
    List.of("Be helpful", "Stay professional", "Listen carefully"));
```

##### `promptAddToSection(String title, List<String> bullets)`
Add bullet content to an existing prompt section.

**Parameters:**
- `title`: Title of existing section to modify
- `bullets`: Bullet points to add

**Usage:**
```java
// Add bullets to an existing section
agent.promptAddToSection("Process", List.of("Follow up", "Close ticket"));
```

##### `promptAddSubsection(String parentTitle, String title, String body)`
Add a subsection to an existing prompt section.

**Parameters:**
- `parentTitle`: Title of parent section
- `title`: Subsection title
- `body`: Subsection content

**Usage:**
```java
agent.promptAddSubsection(
    "Guidelines",
    "Escalation Rules",
    "Escalate when the customer is angry or the issue is beyond scope.");
```

### Voice and Language Configuration

##### `addLanguage(String name, String code, String voice, ...)`
Configure voice and language settings for the agent. Overloads:
- `addLanguage(String name, String code, String voice)`
- `addLanguage(String name, String code, String voice, List<String> speechFillers, List<String> functionFillers, String engine, String model)`
- `addLanguage(String name, String code, String voice, List<String> speechFillers, List<String> functionFillers, String engine, String model, Map<String, Object> params)`

**Parameters:**
- `name`: Human-readable language name
- `code`: Language code (e.g., `"en-US"`, `"es-ES"`)
- `voice`: Voice identifier (e.g., `"rime.spore"`, `"nova.luna"`). A combined `"engine.voice:model"` string is parsed automatically when `engine`/`model` are not given.
- `speechFillers`: Filler phrases during speech processing
- `functionFillers`: Filler phrases during function execution
- `engine`: TTS engine to use
- `model`: AI model to use

**Usage:**
```java
// Basic language setup
agent.addLanguage("English", "en-US", "rime.spore");

// With custom fillers
agent.addLanguage(
    "English",
    "en-US",
    "nova.luna",
    List.of("Let me think...", "One moment..."),
    List.of("Processing...", "Looking that up..."),
    null,   // engine
    null);  // model
```

##### `setLanguages(List<Map<String, Object>> langs)`
Set multiple language configurations at once.

**Usage:**
```java
agent.setLanguages(List.of(
    Map.of("name", "English", "code", "en-US", "voice", "rime.spore"),
    Map.of("name", "Spanish", "code", "es-ES", "voice", "nova.luna")));
```

### Speech Recognition Configuration

##### `addHint(String hint)`
Add a single speech recognition hint.

**Usage:**
```java
agent.addHint("SignalWire");
```

##### `addHints(List<String> hints)`
Add multiple speech recognition hints.

**Usage:**
```java
agent.addHints(List.of("SignalWire", "SWML", "API", "webhook", "SIP"));
```

##### `addPatternHint(String hint, String pattern, String replace, boolean ignoreCase)`
Add a pattern-based hint for speech recognition. Overload: `addPatternHint(hint, pattern, replace)` (case-sensitive).

**Parameters:**
- `hint`: The hint phrase
- `pattern`: Regex pattern to match
- `replace`: Replacement text
- `ignoreCase`: Case-insensitive matching

**Usage:**
```java
agent.addPatternHint(
    "phone number",
    "(\\d{3})-(\\d{3})-(\\d{4})",
    "($1) $2-$3",
    false);
```

##### `addPronunciation(String replace, String with, boolean ignoreCase)`
Add a pronunciation rule for text-to-speech.

**Parameters:**
- `replace`: Text to replace
- `with`: Replacement pronunciation
- `ignoreCase`: Case-insensitive replacement

**Usage:**
```java
agent.addPronunciation("API", "A P I", false);
agent.addPronunciation("SWML", "swim-el", false);
```

##### `setPronunciations(List<Map<String, Object>> prons)`
Set multiple pronunciation rules at once.

**Usage:**
```java
agent.setPronunciations(List.of(
    Map.of("replace", "API", "with", "A P I"),
    Map.of("replace", "SWML", "with", "swim-el", "ignore_case", true)));
```

### AI Parameters Configuration

##### `setParam(String key, Object value)`
Set a single AI parameter.

**Usage:**
```java
agent.setParam("ai_model", "gpt-4.1-nano");
agent.setParam("end_of_speech_timeout", 500);
```

##### `setParams(Map<String, Object> params)`
Set multiple AI parameters at once.

**Common Parameters:**
- `ai_model`: AI model to use (`"gpt-4.1-nano"`, `"gpt-4.1-mini"`, etc.)
- `end_of_speech_timeout`: Milliseconds to wait for speech end (default: 1000)
- `attention_timeout`: Milliseconds before attention timeout (default: 30000)
- `background_file_volume`: Volume for background audio (-60 to 0 dB)
- `temperature`: AI creativity/randomness (0.0 to 2.0)
- `max_tokens`: Maximum response length
- `top_p`: Nucleus sampling parameter (0.0 to 1.0)

**Usage:**
```java
agent.setParams(Map.of(
    "ai_model", "gpt-4.1-nano",
    "end_of_speech_timeout", 500,
    "attention_timeout", 15000,
    "background_file_volume", -20,
    "temperature", 0.7));
```

### Global Data Management

##### `setGlobalData(Map<String, Object> data)`
Set global data available to the AI and functions.

**Usage:**
```java
agent.setGlobalData(Map.of(
    "company_name", "Acme Corp",
    "support_hours", "9 AM - 5 PM EST",
    "escalation_number", "+1-555-0123"));
```

##### `updateGlobalData(Map<String, Object> data)`
Update existing global data (merge with existing).

**Usage:**
```java
agent.updateGlobalData(Map.of(
    "current_promotion", "20% off all services",
    "promotion_expires", "2024-12-31"));
```

### Function Definition

##### `defineTool(String name, String description, Map<String, Object> parameters, ToolHandler handler)`
Define a custom SWAIG function/tool. The handler is a `ToolHandler` functional interface — a lambda `(args, raw) -> FunctionResult`, where `args` is a `Map<String, Object>` of parsed function arguments and `raw` is the raw request data. There is also `defineTool(ToolDefinition)` for a pre-built definition and `defineTools(List<ToolDefinition>)`.

**Parameters:**
- `name`: Function name
- `description`: Function description for the AI
- `parameters`: JSON schema for function parameters (an `object` schema with `properties`/`required`)
- `handler`: `ToolHandler` lambda executed when the tool is called

**Usage:**
```java
import com.signalwire.sdk.swaig.FunctionResult;

agent.defineTool(
    "get_weather",
    "Get current weather for a location",
    Map.of(
        "type", "object",
        "properties", Map.of(
            "location", Map.of("type", "string", "description", "City name")),
        "required", List.of("location")),
    (args, raw) -> {
        String location = (String) args.getOrDefault("location", "Unknown");
        return new FunctionResult("The weather in " + location + " is sunny and 75F");
    });
```

For a definition that needs extra SWAIG fields (secure flag, per-language fillers), build a `ToolDefinition` and pass it to `defineTool(ToolDefinition)`:
```java
import com.signalwire.sdk.swaig.ToolDefinition;

var def = new ToolDefinition(
    "get_weather",
    "Get current weather for a location",
    Map.of("type", "object", "properties", Map.of(
        "location", Map.of("type", "string", "description", "City name")),
        "required", List.of("location")),
    (args, raw) -> new FunctionResult("Sunny and 75F"));
def.setSecure(true);
agent.defineTool(def);
```

##### `registerSwaigFunction(Map<String, Object> swaigFunc)`
Register a pre-built SWAIG function dictionary (for example the output of a `DataMap`).

**Usage:**
```java
// Register a DataMap tool
var weatherTool = new DataMap("get_weather")
    .webhook("GET", "https://api.weather.com/...");
agent.registerSwaigFunction(weatherTool.toSwaigFunction());
```

### Session Lifecycle Hooks

SignalWire AI agents support special SWAIG functions that are automatically called at specific points in the conversation lifecycle. In Java, register them like any other tool via `defineTool` using the reserved names `startup_hook` and `hangup_hook`.

##### `startup_hook`
Called when a new conversation/call begins.

**Implementation:**
```java
agent.defineTool(
    "startup_hook",
    "Called when a new conversation starts to initialize state",
    Map.of("type", "object", "properties", Map.of()),
    (args, raw) -> {
        Object callId = raw.get("call_id");
        // Initialize session resources, load user data, etc.
        return new FunctionResult("Session initialized");
    });
```

##### `hangup_hook`
Called when a conversation/call ends.

**Implementation:**
```java
agent.defineTool(
    "hangup_hook",
    "Called when conversation ends to clean up resources",
    Map.of("type", "object", "properties", Map.of()),
    (args, raw) -> {
        Object callId = raw.get("call_id");
        // Clean up resources, save session data, etc.
        return new FunctionResult("Session ended");
    });
```

**Common Use Cases:**
- Loading user preferences at session start
- Initializing session-specific resources
- Logging conversation metrics
- Cleaning up temporary data
- Saving conversation summaries

### Skills System

##### `addSkill(String skillName, Map<String, Object> params)`
Add a modular skill to the agent. Overloads: `addSkill(SkillName, Map)` (enum form). Pass `null` (or an empty map) for skills needing no configuration.

**Parameters:**
- `skillName`: Name of the skill to add
- `params`: Skill configuration parameters

**Available Skills:**
- `datetime`: Current date/time information
- `math`: Mathematical calculations
- `web_search`: Google Custom Search integration
- `datasphere`: SignalWire DataSphere search
- `native_vector_search`: Remote document search

**Usage:**
```java
// Simple skill (no config)
agent.addSkill("datetime", null);
agent.addSkill("math", null);

// Skill with configuration
agent.addSkill("web_search", Map.of(
    "api_key", "your-google-api-key",
    "search_engine_id", "your-search-engine-id",
    "num_results", 3));

// Multiple instances with different tool names
agent.addSkill("web_search", Map.of(
    "api_key", "your-api-key",
    "search_engine_id", "general-engine",
    "tool_name", "search_general"));

agent.addSkill("web_search", Map.of(
    "api_key", "your-api-key",
    "search_engine_id", "news-engine",
    "tool_name", "search_news"));
```

##### `removeSkill(String skillName)`
Remove a skill from the agent. Overload: `removeSkill(SkillName)`.

**Usage:**
```java
agent.removeSkill("web_search");
```

##### `listSkills()`
Get the list of currently added skills.

**Returns:** `List<String>` — names of active skills.

**Usage:**
```java
List<String> activeSkills = agent.listSkills();
System.out.println("Active skills: " + activeSkills);
```

##### `hasSkill(String skillName)`
Check if a skill is currently added. Overload: `hasSkill(SkillName)`.

**Returns:** `boolean` — `true` if the skill is active.

**Usage:**
```java
if (agent.hasSkill("web_search")) {
    System.out.println("Web search is available");
}
```

### Native Functions

##### `setNativeFunctions(List<String> funcs)`
Enable specific native SWML functions.

**Available Native Functions:**
- `transfer`: Transfer calls
- `hangup`: End calls
- `play`: Play audio files
- `record`: Record audio
- `send_sms`: Send SMS messages

**Usage:**
```java
agent.setNativeFunctions(List.of("transfer", "hangup", "send_sms"));
```

##### `setInternalFillersMap(Map<String, Map<String, List<String>>> fillers)`
Set custom filler phrases for internal/native SWAIG functions, keyed by function name → language code → filler phrases. (There is also `setInternalFillers(List<Map<String, Object>>)` for a list-of-maps shape.)

**Available Internal Functions:**
- `next_step`: Moving between workflow steps (contexts system)
- `change_context`: Switching contexts in workflows
- `check_time`: Getting current time
- `wait_for_user`: Waiting for user input
- `wait_seconds`: Pausing for specified duration
- `get_visual_input`: Processing visual data

**Usage:**
```java
agent.setInternalFillersMap(Map.of(
    "next_step", Map.of(
        "en-US", List.of("Moving to the next step...", "Let's continue..."),
        "es", List.of("Pasando al siguiente paso...", "Continuemos...")),
    "check_time", Map.of(
        "en-US", List.of("Let me check the time...", "Getting current time..."))));
```

##### `addInternalFiller(String functionName, String languageCode, List<String> fillers)`
Add internal fillers for a specific function and language.

**Usage:**
```java
agent.addInternalFiller("next_step", "en-US", List.of(
    "Great! Let's move to the next step...",
    "Perfect! Moving forward..."));
```

### Function Includes

##### `addFunctionInclude(String url, Map<String, Object> functions)`
Include external SWAIG functions from another service.

**Parameters:**
- `url`: URL of the external SWAIG service
- `functions`: The function-include configuration (function names and optional metadata)

**Usage:**
```java
agent.addFunctionInclude(
    "https://external-service.com/swaig",
    Map.of(
        "functions", List.of("external_function1", "external_function2"),
        "meta_data", Map.of("service", "external", "version", "1.0")));
```

##### `setFunctionIncludes(List<Map<String, Object>> includes)`
Set multiple function includes at once.

**Usage:**
```java
agent.setFunctionIncludes(List.of(
    Map.of(
        "url", "https://service1.com/swaig",
        "functions", List.of("func1", "func2")),
    Map.of(
        "url", "https://service2.com/swaig",
        "functions", List.of("func3"),
        "meta_data", Map.of("priority", "high"))));
```

### Webhook Configuration

##### `setWebHookUrl(String url)`
Set the default webhook URL for SWAIG functions.

**Usage:**
```java
agent.setWebHookUrl("https://myserver.com/webhook");
```

##### `setPostPromptUrl(String url)`
Set the URL for post-prompt processing.

**Usage:**
```java
agent.setPostPromptUrl("https://myserver.com/post-prompt");
```

##### `addSwaigQueryParams(Map<String, String> params)`
Add query parameters to be included in all SWAIG webhook URLs. Useful for preserving dynamic configuration state across SWAIG callbacks — e.g. if your dynamic config adds skills based on query parameters, pass those same parameters through to the SWAIG webhook so the same configuration is applied.

**Usage:**
```java
// In a dynamic config callback, preserve configuration parameters
agent.setDynamicConfigCallback((queryParams, headers, body, cfg) -> {
    String customerId = queryParams.get("customer_id");
    if (customerId != null) {
        cfg.addSwaigQueryParams(Map.of("customer_id", customerId));
        cfg.addSkill("customer_lookup", Map.of("customer_id", customerId));
    }
});
```

##### `clearSwaigQueryParams()`
Clear all SWAIG query parameters.

**Usage:**
```java
agent.clearSwaigQueryParams();
```

### Debug Events

##### `enableDebugEvents()`
Enable the debug event webhook for this agent. When enabled, the AI module POSTs real-time debug events to a `/debug_events` endpoint on this agent during calls. Events are automatically logged via the agent's structured logger and can optionally be handled with a custom callback via `onDebugEvent()`.

**Usage:**
```java
agent.enableDebugEvents();
```

**How it works:**
- Registers a `/debug_events` POST endpoint on the agent's HTTP server
- Auto-sets `debug_webhook_url` and `debug_webhook_level` in the SWML `params` during rendering
- The URL is built automatically using the same auth/proxy logic as other webhook URLs
- No manual URL configuration needed

**Event types (level 1):**

| Event label | Description |
|-------------|-------------|
| `session_start` | AI session started (model, TTS engine, voice, language) |
| `session_end` | AI session ended (reason, duration, token counts) |
| `barge` | User interrupted AI speech (barge type, elapsed ms) |
| `step_change` | Conversation step changed |
| `context_change` | Conversation context changed |
| `llm_error` | LLM error (fatal, retry, max_retries) |
| `voice_error` | TTS voice configuration or runtime error |
| `hold` | Call placed on hold or taken off hold |
| `filler` | Filler phrase spoken (thinking or function filler) |
| `consolidation` | Token consolidation triggered |
| `process_action` | Webhook action being processed |
| `gather_start` | Gather flow started |
| `gather_complete` | Gather flow completed |

**Additional high-volume events:**

| Event label | Description |
|-------------|-------------|
| `llm_request` | LLM API request initiated (input tokens) |
| `llm_response` | LLM API response received (duration, output tokens) |
| `conversation_add` | Entry added to conversation history |

### Call Flow Verb Insertion

These methods allow you to customize the SWML call flow by inserting verbs at different stages of the call lifecycle. Verb data is passed as an `Object` (typically a `Map`).

##### `addPreAnswerVerb(String verbName, Object verbData)`
Add a verb to run before the call is answered (while still ringing).

**Safe pre-answer verbs:** `transfer`, `execute`, `return`, `label`, `goto`, `request`, `switch`, `cond`, `if`, `eval`, `set`, `unset`, `hangup`, `send_sms`, `sleep`, `stop_record_call`, `stop_denoise`, `stop_tap`

**Usage:**
```java
// Send SMS before answering
agent.addPreAnswerVerb("send_sms", Map.of(
    "to", "+15551234567",
    "from", "+15559876543",
    "body", "Incoming call from AI agent"));

// Set variables before answer
agent.addPreAnswerVerb("set", Map.of("call_start", "${system.timestamp}"));
```

##### `addAnswerVerb(String verbName, Object verbData)`
Configure the answer verb that connects the call.

**Usage:**
```java
// Set maximum call duration to 1 hour
agent.addAnswerVerb("answer", Map.of("max_duration", 3600));
```

##### `addPostAnswerVerb(String verbName, Object verbData)`
Add a verb to run after the call is answered but before the AI starts.

**Usage:**
```java
// Play welcome message before AI starts
agent.addPostAnswerVerb("play", Map.of(
    "url", "say:Welcome to our AI assistant. This call may be recorded."));

// Add a brief pause
agent.addPostAnswerVerb("sleep", Map.of("duration", 1));
```

##### `addPostAiVerb(String verbName, Object verbData)`
Add a verb to run after the AI conversation ends.

**Usage:**
```java
// Clean hangup after AI ends
agent.addPostAiVerb("hangup", Map.of());

// Transfer to human after AI conversation
agent.addPostAiVerb("transfer", Map.of("to", "+15551234567"));

// Log call completion
agent.addPostAiVerb("request", Map.of(
    "url", "https://myserver.com/call-complete",
    "method", "POST"));
```

##### `clearPreAnswerVerbs()` / `clearPostAnswerVerbs()` / `clearPostAiVerbs()`
Remove all pre-answer / post-answer / post-AI verbs respectively.

**Method Chaining Example:**
```java
agent.addPreAnswerVerb("set", Map.of("source", "ai_agent"))
     .addAnswerVerb("answer", Map.of("max_duration", 1800))
     .addPostAnswerVerb("play", Map.of("url", "say:Hello!"))
     .addPostAiVerb("hangup", Map.of());
```

### Dynamic Configuration

##### `setDynamicConfigCallback(DynamicConfigCallback callback)`
Set a callback for per-request dynamic configuration. The `DynamicConfigCallback` functional interface receives `(queryParams, headers, body, agent)` and mutates the agent for that request.

**Usage:**
```java
agent.setDynamicConfigCallback((queryParams, headers, body, cfg) -> {
    // Configure based on request
    if ("spanish".equals(queryParams.get("language"))) {
        cfg.addLanguage("Spanish", "es-ES", "nova.luna");
    }

    // Set customer-specific data
    String customerId = headers.get("X-Customer-ID");
    if (customerId != null) {
        cfg.setGlobalData(Map.of("customer_id", customerId));
    }
});
```

### SIP Integration

##### `enableSipRouting()`
Enable SIP-based routing for voice calls.

**Usage:**
```java
agent.enableSipRouting();
```

##### `registerSipUsername(String username)`
Register a specific SIP username for this agent.

**Usage:**
```java
agent.registerSipUsername("support");
agent.registerSipUsername("sales");
```

##### `getSipUsernames()` / `isSipRoutingEnabled()`
Inspect registered SIP usernames (`Set<String>`) and whether SIP routing is enabled (`boolean`).

**Usage:**
```java
if (agent.isSipRoutingEnabled()) {
    System.out.println("SIP usernames: " + agent.getSipUsernames());
}
```

### Utility Methods

##### `getName()`
Get the agent's name. Returns `String`.

##### `getApp()`
Get the underlying JDK HTTP server instance (`com.sun.net.httpserver.HttpServer`) for advanced embedding.

##### `renderSwml(String baseUrl)` / `renderSwmlJson(String baseUrl)`
Render the agent's complete SWML document as a `Map<String, Object>` or a JSON `String`.

**Usage:**
```java
String swmlJson = agent.renderSwmlJson("https://myserver.com");
System.out.println(swmlJson);
```

### Event Handlers

##### `onSummary(BiConsumer<Map<String, Object>, Map<String, Object>> callback)`
Register a handler for conversation summaries. The callback is invoked when the AI generates a summary based on your post-prompt configuration. It receives `(summary, rawData)`, where `summary` is the parsed summary map and `rawData` is the complete raw POST data.

**Usage:**
```java
var agent = AgentBase.builder().name("summary-agent").route("/agent").build();

// Configure post-prompt to request a JSON summary
agent.setPostPrompt(
    "Return a JSON summary of the conversation: "
    + "{\"topic\":\"...\",\"satisfied\":true,\"follow_up_needed\":false,\"key_points\":[]}");

agent.onSummary((summary, rawData) -> {
    if (summary != null) {
        Object topic = summary.getOrDefault("topic", "Unknown");
        Object satisfied = summary.getOrDefault("satisfied", false);
        System.out.println("Call about: " + topic + ", Customer satisfied: " + satisfied);

        // Save to database, send to CRM, trigger follow-up, etc.
        if (Boolean.TRUE.equals(summary.get("follow_up_needed"))) {
            // scheduleFollowUp(summary);
        }
    }
});
```

##### `onDebugEvent(Consumer<Map<String, Object>> callback)`
Register a handler for debug webhook events. Requires `enableDebugEvents()` to be called first. The callback receives the full event payload map, which includes `call_id`, `label`, and event-specific fields.

**Usage:**
```java
var agent = AgentBase.builder().name("debug-agent").route("/agent").build();
agent.enableDebugEvents();

agent.onDebugEvent(data -> {
    Object callId = data.get("call_id");
    String label = String.valueOf(data.get("label"));
    switch (label) {
        case "llm_error" ->
            System.out.println("LLM error on call " + callId + ": " + data.get("event"));
        case "barge" ->
            System.out.println("Barge after " + data.get("barge_elapsed_ms") + "ms");
        case "session_end" ->
            System.out.println("Call ended: " + data.get("reason")
                + ", duration: " + data.get("duration_ms") + "ms");
        default -> { }
    }
});
```

> **Note:** Even without registering a handler, all debug events are automatically logged via the agent's structured logger when `enableDebugEvents()` is called.

##### `onFunctionCall(String name, Map<String, Object> args, Map<String, Object> rawData)`
The base dispatch entry point for tool calls. It routes to the registered `ToolHandler` for `name` and returns its `FunctionResult`. You typically define tools with `defineTool` rather than overriding this, but it can be called or wrapped for custom dispatch logic.

**Returns:** `FunctionResult`.

### Authentication

Basic-auth credentials come from the builder (`authUser`/`authPassword`) or environment variables (`SWML_BASIC_AUTH_USER` / `SWML_BASIC_AUTH_PASSWORD`). Comparison is timing-safe (`MessageDigest.isEqual()`); when no password is supplied, one is generated with `SecureRandom` rather than falling back to a weak default.

##### `getAuthUser()` / `getAuthPassword()`
Retrieve the effective basic-auth credentials (from the builder or environment).

**Usage:**
```java
String user = agent.getAuthUser();
String pass = agent.getAuthPassword();
```

##### `validateWebhook(HttpExchange exchange, String rawBody)`
Validate an inbound webhook's signature against the configured signing key. Returns `boolean`.

### Context System

##### `defineContexts()`
Define structured workflow contexts for the agent. Returns a `ContextBuilder`.

**Returns:** `ContextBuilder` — builder for creating contexts and steps.

**Usage:**
```java
var contexts = agent.defineContexts();
contexts.addContext("greeting")
    .addStep("welcome")
    .setText("Welcome! How can I help?")
    .setStepCriteria("User has stated their need")
    .setValidSteps(List.of("next"));

contexts.addContext("main_menu")
    .addStep("menu")
    .setText("Choose: 1) Support 2) Sales 3) Billing")
    .setFunctions(List.of("transfer_to_support", "transfer_to_sales"));
```

This concludes the AgentBase class reference. The document continues with FunctionResult, DataMap, and other components.

---

## FunctionResult Class

The `FunctionResult` class is used to create structured responses from SWAIG functions. It handles both natural language responses and structured actions that the agent should execute. (This is the Java equivalent of Python's `SwaigFunctionResult`.)

`import com.signalwire.sdk.swaig.FunctionResult;`

### Construction

```java
new FunctionResult()                        // empty (actions only)
new FunctionResult(String response)         // with a spoken response
new FunctionResult(String response, boolean postProcess)  // with post-processing
```

**Post-processing Behavior:**
- `postProcess = false` (default): Execute actions immediately after AI response
- `postProcess = true`: Let AI respond to the user one more time, then execute actions

**Usage:**
```java
// Simple response
var result = new FunctionResult("The weather is sunny and 75F");

// Response with post-processing enabled
var result2 = new FunctionResult("I'll transfer you now", true);

// Empty response (actions only)
var result3 = new FunctionResult();
```

### Core Methods

#### Response Configuration

##### `setResponse(String response)`
Set or update the natural language response text.

**Usage:**
```java
var result = new FunctionResult();
result.setResponse("I found your order information");
```

##### `setPostProcess(boolean postProcess)`
Enable or disable post-processing for this result.

**Usage:**
```java
var result = new FunctionResult("I'll help you with that");
result.setPostProcess(true);  // Let AI handle follow-up questions first
```

#### Action Management

##### `addAction(String name, Object data)`
Add a structured action to execute.

**Parameters:**
- `name`: Action name/type (e.g., `"play"`, `"transfer"`, `"set_global_data"`)
- `data`: Action data — a string, boolean, `Map`, or `List`

**Usage:**
```java
// Simple action with boolean
result.addAction("hangup", true);

// Action with string data
result.addAction("play", "welcome.mp3");

// Action with object data
result.addAction("set_global_data", Map.of("customer_id", "12345", "status", "verified"));

// Action with array data
result.addAction("send_sms", List.of("+15551234567", "Your order is ready!"));
```

##### `addActions(List<Map<String, Object>> actionList)`
Add multiple actions at once.

**Usage:**
```java
result.addActions(List.of(
    Map.of("play", "hold_music.mp3"),
    Map.of("set_global_data", Map.of("status", "on_hold")),
    Map.of("wait", 5000)));
```

### Call Control Actions

#### Call Transfer and Connection

##### `connect(String destination, boolean isFinal, String from)`
Transfer or connect the call to another destination. Overload: `connect(destination, isFinal)`.

**Parameters:**
- `destination`: Phone number, SIP address, or other destination
- `isFinal`: Permanent transfer (`true`) vs temporary transfer (`false`)
- `from`: Override caller ID (nullable)

**Transfer Types:**
- `isFinal = true`: Permanent transfer — call exits agent completely
- `isFinal = false`: Temporary transfer — call returns to agent if the far end hangs up

**Usage:**
```java
// Permanent transfer to a phone number
result.connect("+15551234567", true);

// Temporary transfer to a SIP address with custom caller ID
result.connect("support@company.com", false, "+15559876543");

// Transfer with a response
var r = new FunctionResult("Transferring you to our sales team");
r.connect("sales@company.com", true);
```

##### `swmlTransfer(String dest, String aiResponse, boolean isFinal)`
Create a SWML-based transfer with AI response setup. `aiResponse` is spoken when the transfer returns.

**Usage:**
```java
result.swmlTransfer(
    "+15551234567",
    "You've been transferred back to me. How else can I help?",
    false);
```

##### `sipRefer(String toUri)`
Perform a SIP REFER transfer.

**Usage:**
```java
result.sipRefer("sip:support@company.com");
```

#### Call Management

##### `hangup()`
End the call immediately.

**Usage:**
```java
var result = new FunctionResult("Thank you for calling. Goodbye!");
result.hangup();
```

##### `hold(int timeout)`
Put the call on hold. `timeout` is in seconds.

**Usage:**
```java
var result = new FunctionResult("Please hold while I look that up");
result.hold(60);
```

##### `stop()`
Stop current audio playback or recording.

**Usage:**
```java
result.stop();
```

#### Audio Control

##### `say(String text)`
Add text for the AI to speak.

**Usage:**
```java
result.say("Please wait while I process your request");
```

##### `playBackgroundFile(String filename, boolean wait)`
Play an audio file in the background. Overload: `playBackgroundFile(filename)`.

**Parameters:**
- `filename`: Audio file path or URL
- `wait`: Wait for the file to finish before continuing

**Usage:**
```java
// Play hold music in the background
result.playBackgroundFile("hold_music.mp3");

// Play an announcement and wait for completion
result.playBackgroundFile("important_announcement.wav", true);
```

##### `stopBackgroundFile()`
Stop background audio playback.

**Usage:**
```java
result.stopBackgroundFile();
```

### Data Management Actions

##### `updateGlobalData(Map<String, Object> data)`
Update existing global data (merge with existing).

**Usage:**
```java
result.updateGlobalData(Map.of(
    "last_interaction", "2024-01-15T10:30:00Z",
    "agent_notes", "Customer satisfied with resolution"));
```

##### `removeGlobalData(Object keys)`
Remove specific keys from global data. `keys` may be a single `String` or a `List<String>`.

**Usage:**
```java
// Remove a single key
result.removeGlobalData("temporary_data");

// Remove multiple keys
result.removeGlobalData(List.of("temp1", "temp2", "cache_data"));
```

##### `setMetadata(Map<String, Object> data)`
Set metadata for the conversation.

**Usage:**
```java
result.setMetadata(Map.of(
    "call_type", "support",
    "priority", "high",
    "department", "technical"));
```

##### `removeMetadata(Object keys)`
Remove specific metadata keys (single `String` or `List<String>`).

**Usage:**
```java
result.removeMetadata(List.of("temporary_flag", "debug_info"));
```

### AI Behavior Control

##### `setEndOfSpeechTimeout(int milliseconds)`
Adjust how long to wait for speech to end.

**Usage:**
```java
result.setEndOfSpeechTimeout(300);   // Shorter — quick responses
result.setEndOfSpeechTimeout(2000);  // Longer — thoughtful responses
```

##### `setSpeechEventTimeout(int milliseconds)`
Set the timeout for speech events.

**Usage:**
```java
result.setSpeechEventTimeout(5000);
```

##### `waitForUser(Boolean enabled, Integer timeout, boolean answerFirst)`
Control whether to wait for user input. Overload: `waitForUser()`.

**Parameters:**
- `enabled`: Enable/disable waiting for the user (nullable)
- `timeout`: Timeout in milliseconds (nullable)
- `answerFirst`: Answer the call before waiting

**Usage:**
```java
// Wait for user input with a 10-second timeout
result.waitForUser(true, 10000, false);

// Don't wait for the user (immediate response)
result.waitForUser(false, null, false);
```

##### `toggleFunctions(List<Map<String, Object>> toggles)`
Enable or disable specific functions.

**Usage:**
```java
result.toggleFunctions(List.of(
    Map.of("name", "transfer_to_sales", "enabled", true),
    Map.of("name", "end_call", "enabled", false),
    Map.of("name", "escalate", "enabled", true, "timeout", 30000)));
```

##### `enableFunctionsOnTimeout(boolean enabled)`
Control whether functions are enabled when a timeout occurs.

**Usage:**
```java
result.enableFunctionsOnTimeout(false);  // Disable functions on timeout
```

##### `enableExtensiveData(boolean enabled)`
Enable extensive data collection.

**Usage:**
```java
result.enableExtensiveData(true);
```

##### `updateSettings(Map<String, Object> settings)`
Update various AI settings.

**Usage:**
```java
result.updateSettings(Map.of(
    "temperature", 0.8,
    "max_tokens", 150,
    "end_of_speech_timeout", 800));
```

### Context and Conversation Control

##### `switchContext(String systemPrompt, String userPrompt, boolean consolidate, boolean fullReset)`
Switch conversation context or reset the conversation. Overload: `switchContext(String systemPrompt)`.

**Parameters:**
- `systemPrompt`: New system prompt (nullable)
- `userPrompt`: New user prompt (nullable)
- `consolidate`: Consolidate conversation history
- `fullReset`: Completely reset the conversation

**Usage:**
```java
// Switch to a technical support context
result.switchContext(
    "You are now a technical support specialist",
    "The customer needs technical help",
    false,
    false);

// Reset conversation completely
result.switchContext(null, null, false, true);

// Consolidate conversation history
result.switchContext(null, null, true, false);
```

##### `simulateUserInput(String text)`
Simulate user input for testing or automation.

**Usage:**
```java
result.simulateUserInput("I need help with my order");
```

### Communication Actions

##### `sendSms(String toNumber, String fromNumber, String body, List<String> media, List<String> tags, String region)`
Send an SMS message. Overload: `sendSms(toNumber, fromNumber, body, media, tags)` (no region). Either `body` or `media` must be provided.

**Parameters:**
- `toNumber`: Recipient phone number
- `fromNumber`: Sender phone number
- `body`: SMS message text (nullable if media is provided)
- `media`: List of media URLs (nullable)
- `tags`: Message tags (nullable)
- `region`: SignalWire region (nullable)

**Usage:**
```java
// Simple text message
result.sendSms(
    "+15551234567",
    "+15559876543",
    "Your order #12345 has shipped!",
    null,
    null,
    null);

// Message with media and tags
result.sendSms(
    "+15551234567",
    "+15559876543",
    "Here's your receipt",
    List.of("https://example.com/receipt.pdf"),
    List.of("receipt", "order_12345"),
    null);
```

### Recording and Media

##### `recordCall(...)`
Start call recording. Several overloads exist, from `recordCall()` (all defaults) up to the full-arity form:
`recordCall(String controlId, boolean stereo, String format, String direction, String terminators, boolean beep, double inputSensitivity, double initialTimeout, double endSilenceTimeout, Double maxLength, String statusUrl)`.

**Parameters:**
- `controlId`: Unique identifier for this recording (nullable)
- `stereo`: Record in stereo
- `format`: Recording format: `"wav"`, `"mp3"`, `"mp4"`
- `direction`: Recording direction: `"both"`, `"inbound"`, `"outbound"`
- `terminators`: DTMF keys to stop recording (nullable)
- `beep`: Play a beep before recording
- `inputSensitivity`: Input sensitivity level
- `initialTimeout` / `endSilenceTimeout`: Timeouts in seconds
- `maxLength`: Maximum recording length in seconds (nullable)
- `statusUrl`: Webhook URL for recording status (nullable)

**Usage:**
```java
// Basic recording (defaults)
result.recordCall();

// Recording with control ID and settings
result.recordCall(
    "customer_call_001",  // controlId
    true,                  // stereo
    "wav",                 // format
    "both",                // direction
    "#*",                  // terminators
    true,                  // beep
    44.0,                  // inputSensitivity
    0.0,                   // initialTimeout
    0.0,                   // endSilenceTimeout
    300.0,                 // maxLength
    null);                 // statusUrl
```

##### `stopRecordCall(String controlId)`
Stop call recording. Overload: `stopRecordCall()`.

**Usage:**
```java
result.stopRecordCall();
result.stopRecordCall("customer_call_001");
```

### Conference and Room Management

##### `joinRoom(String name)`
Join a SignalWire room.

**Usage:**
```java
result.joinRoom("support_room_1");
```

##### `joinConference(...)`
Join a conference call. Use `joinConference(String name)` for the common case, or the full-arity overload for detailed settings (`muted`, `beep`, `startOnEnter`, `endOnExit`, `waitUrl`, `maxParticipants`, `record`, `region`, `trim`, `coach`, status-callback settings, etc.).

**Usage:**
```java
// Basic conference join
result.joinConference("sales_meeting");
```

### Payment Processing

##### `pay(...)`
Process a payment through the call. The full-arity form takes positional arguments:
`pay(String connectorUrl, String inputMethod, String statusUrl, String paymentMethod, int timeout, int maxAttempts, boolean securityCode, Object postalCode, int minPostalCodeLength, String tokenType, String chargeAmount, String currency, String language, String voice, String description, String validCardTypes, List<Map<String,String>> parameters, List<Map<String,Object>> prompts)`. A shorter overload is available for the common case.

**Key parameters:**
- `connectorUrl`: Payment processor webhook URL
- `inputMethod`: `"dtmf"` or `"speech"`
- `chargeAmount`: Amount to charge (as a string, e.g. `"29.99"`)
- `currency`: Currency code (e.g. `"usd"`)
- `description`: Payment description

**Usage:**
```java
// Basic payment processing (short overload)
result.pay("https://payment-processor.com/webhook", "29.99", "Monthly subscription");
```

### Call Monitoring

##### `tap(String uri, String controlId, String direction, String codec)`
Start call tapping/monitoring. Additional overloads accept `TapDirection`/`Codec` enums and the full-arity `tap(uri, controlId, direction, codec, rtpPtime, statusUrl)`.

**Parameters:**
- `uri`: URI to send tapped audio to
- `controlId`: Unique identifier for this tap (nullable)
- `direction`: Tap direction: `"speak"`, `"hear"`, `"both"`
- `codec`: Audio codec: `"PCMU"`, `"PCMA"`, `"G722"`

**Usage:**
```java
// Tap with specific settings
result.tap("sip:quality@company.com", "quality_monitor_001", "both", "G722");
```

##### `stopTap(String controlId)`
Stop call tapping. Overload: `stopTap()`.

**Usage:**
```java
result.stopTap();
result.stopTap("quality_monitor_001");
```

### Advanced SWML Execution

##### `executeSwml(Object swmlContent, boolean transfer)`
Execute custom SWML content. Overload: `executeSwml(swmlContent)`.

**Parameters:**
- `swmlContent`: SWML document or content to execute (a `Map`/`Document`)
- `transfer`: Whether this is a transfer operation

**Usage:**
```java
// Execute custom SWML
Map<String, Object> customSwml = Map.of(
    "version", "1.0.0",
    "sections", Map.of(
        "main", List.of(
            Map.of("play", Map.of("url", "https://example.com/custom.mp3")),
            Map.of("say", Map.of("text", "Custom SWML execution")))));
result.executeSwml(customSwml, false);
```

### Utility Methods

##### `toMap()`
Convert the result to a `Map<String, Object>` for serialization. (`toJson()` returns the JSON string form.)

**Usage:**
```java
var result = new FunctionResult("Hello world");
result.addAction("play", "music.mp3");
Map<String, Object> map = result.toMap();
System.out.println(map);
// {response=Hello world, action=[{play=music.mp3}]}
```

##### `getResponse()` / `getActions()` / `isPostProcess()`
Accessors for the response text (`String`), the accumulated actions (`List<Map<String, Object>>`), and the post-process flag (`boolean`).

### Static Helper Methods

##### `createPaymentPrompt(...)`
Create a payment prompt configuration (`Map<String, Object>`).

**Usage:**
```java
Map<String, Object> prompt = FunctionResult.createPaymentPrompt(
    "card_number",
    List.of(FunctionResult.createPaymentAction("say", "Please enter your card number")),
    null,   // cardType
    null);  // errorType
```

##### `createPaymentAction(String actionType, String phrase)`
Create a payment action configuration (`Map<String, String>`).

**Usage:**
```java
Map<String, String> action = FunctionResult.createPaymentAction("say", "Enter your card number");
```

##### `createPaymentParameter(String name, String value)`
Create a payment parameter configuration (`Map<String, String>`).

**Usage:**
```java
Map<String, String> param = FunctionResult.createPaymentParameter("merchant_id", "12345");
```

### Method Chaining

All configuration methods return `this`, enabling fluent method chaining:

```java
var result = new FunctionResult("I'll help you with that")
    .setPostProcess(true)
    .updateGlobalData(Map.of("status", "helping"))
    .setEndOfSpeechTimeout(800)
    .addAction("play", "thinking.mp3");

// Complex workflow
var payment = new FunctionResult("Processing your payment")
    .setPostProcess(true)
    .updateGlobalData(Map.of("payment_status", "processing"))
    .pay("https://payments.com/webhook", "99.99", "Service payment")
    .sendSms(
        "+15551234567",
        "+15559876543",
        "Payment confirmation will be sent shortly",
        null,
        null);
```

This concludes the FunctionResult class reference. The document continues with DataMap and other components.

---

## DataMap Class

The `DataMap` class provides a declarative approach to creating SWAIG tools that integrate with REST APIs without requiring webhook infrastructure. DataMap tools execute on SignalWire's server infrastructure, eliminating the need to expose your own webhook endpoints.

`import com.signalwire.sdk.datamap.DataMap;`

### Construction

```java
new DataMap(String functionName)
```

**Parameters:**
- `functionName`: Name of the SWAIG function this DataMap will create

**Usage:**
```java
// Create a new DataMap tool
var weatherMap = new DataMap("get_weather");
var searchMap = new DataMap("search_docs");
```

### Core Configuration Methods

#### Function Metadata

##### `purpose(String description)`
Set the function description/purpose. Returns the DataMap for chaining.

**Usage:**
```java
var dataMap = new DataMap("get_weather").purpose("Get current weather information for any city");
```

##### `description(String description)`
Alias for `purpose()` — set the function description.

**Usage:**
```java
var dataMap = new DataMap("search_api").description("Search our knowledge base for information");
```

#### Parameter Definition

##### `parameter(String name, String paramType, String description, boolean required, List<String> enumValues)`
Add a function parameter with JSON schema validation. Overloads: `parameter(name, paramType, description, required)` and `parameter(name, paramType, description)`.

**Parameters:**
- `name`: Parameter name
- `paramType`: JSON schema type: `"string"`, `"number"`, `"boolean"`, `"array"`, `"object"`
- `description`: Parameter description for the AI
- `required`: Whether the parameter is required
- `enumValues`: List of allowed values for validation (nullable)

**Usage:**
```java
// Required string parameter
dataMap.parameter("location", "string", "City name or ZIP code", true);

// Optional number parameter
dataMap.parameter("days", "number", "Number of forecast days", false);

// Enum parameter with allowed values
dataMap.parameter("units", "string", "Temperature units", false,
    List.of("celsius", "fahrenheit"));

// Array parameter
dataMap.parameter("categories", "array", "Search categories to include");
```

### API Integration Methods

#### HTTP Webhook Configuration

##### `webhook(String method, String url, Map<String, String> headers)`
Configure an HTTP API call. Overload: `webhook(method, url)` (no headers).

**Parameters:**
- `method`: HTTP method: `"GET"`, `"POST"`, `"PUT"`, `"DELETE"`, `"PATCH"`
- `url`: API endpoint URL (supports `${variable}` substitution)
- `headers`: HTTP headers to send (nullable)

**Variable Substitution in URLs:**
- `${args.parameter_name}`: Function argument values
- `${global_data.key}`: Call-wide data store (user info, call state — NOT credentials)
- `${meta_data.call_id}`: Call and function metadata

**Usage:**
```java
// Simple GET request with parameter substitution
dataMap.webhook("GET",
    "https://api.weather.com/v1/current?key=API_KEY&q=${args.location}");

// POST request with authentication headers
dataMap.webhook("POST",
    "https://api.company.com/search",
    Map.of(
        "Authorization", "Bearer YOUR_TOKEN",
        "Content-Type", "application/json"));
```

##### `body(Map<String, Object> data)`
Set the JSON body for POST/PUT requests (supports `${variable}` substitution).

**Usage:**
```java
dataMap.body(Map.of(
    "query", "${args.search_term}",
    "limit", 5,
    "filters", Map.of(
        "category", "${args.category}",
        "active", true)));
```

##### `params(Map<String, Object> data)`
Set URL query parameters (supports `${variable}` substitution).

**Usage:**
```java
dataMap.params(Map.of(
    "api_key", "YOUR_API_KEY",
    "q", "${args.location}",
    "units", "${args.units}",
    "lang", "en"));
```

#### Multiple Webhooks and Fallbacks

DataMap supports multiple webhook configurations for fallback scenarios:

```java
var dataMap = new DataMap("search_with_fallback")
    .purpose("Search with multiple API fallbacks")
    .parameter("query", "string", "Search query", true)

    // Primary API
    .webhook("GET", "https://api.primary.com/search?q=${args.query}")
    .output(new FunctionResult("Primary result: ${response.title}"))

    // Fallback API
    .webhook("GET", "https://api.fallback.com/search?q=${args.query}")
    .output(new FunctionResult("Fallback result: ${response.title}"))

    // Final fallback if all APIs fail
    .fallbackOutput(new FunctionResult("Sorry, all search services are currently unavailable"));
```

### Response Processing

#### Basic Output

##### `output(FunctionResult result)`
Set the response template for successful API calls.

**Variable Substitution in Outputs:**
- `${response.field}`: API response fields
- `${response.nested.field}`: Nested response fields
- `${response.array[0].field}`: Array element fields
- `${args.parameter}`: Original function arguments
- `${global_data.key}`: Call-wide data store (user info, call state)

**Usage:**
```java
// Simple response template
dataMap.output(new FunctionResult(
    "Weather in ${args.location}: ${response.current.condition.text}, ${response.current.temp_f}F"));

// Response with actions
dataMap.output(
    new FunctionResult("Found ${response.total_results} results")
        .updateGlobalData(Map.of("last_search", "${args.query}"))
        .addAction("play", "search_complete.mp3"));
```

##### `fallbackOutput(FunctionResult result)`
Set the response when all webhooks fail.

**Usage:**
```java
dataMap.fallbackOutput(
    new FunctionResult("Sorry, the service is temporarily unavailable. Please try again later.")
        .addAction("play", "service_unavailable.mp3"));
```

#### Array Processing

##### `foreach(Map<String, Object> foreachConfig)`
Process array responses by iterating over elements.

**Usage:**
```java
var dataMap = new DataMap("search_docs")
    .webhook("GET", "https://api.docs.com/search?q=${args.query}")
    .foreach(Map.of("array", "${response.results}"))
    .output(new FunctionResult("Found: ${foreach.title} - ${foreach.summary}"));

// Advanced foreach configuration
dataMap.foreach(Map.of(
    "array", "${response.items}",
    "limit", 3,
    "filter", Map.of("field", "status", "value", "active")));
```

**Foreach Variable Access:**
- `${foreach.field}`: Current array element field
- `${foreach.nested.field}`: Nested fields in the current element
- `${foreach_index}`: Current iteration index (0-based)
- `${foreach_count}`: Total number of items being processed

### Pattern-Based Processing

#### Expression Matching

##### `expression(String testValue, String pattern, FunctionResult output, FunctionResult nomatchOutput)`
Add a pattern-based response without an API call. Overload: `expression(testValue, pattern, output)`.

**Parameters:**
- `testValue`: Template string to test against the pattern
- `pattern`: Regex pattern string
- `output`: Response when the pattern matches
- `nomatchOutput`: Response when the pattern doesn't match (nullable)

**Usage:**
```java
var controlMap = new DataMap("file_control")
    .purpose("Control file playback")
    .parameter("command", "string", "Playback command", true)
    .parameter("filename", "string", "File to control")

    // Start commands
    .expression("${args.command}", "start|play|begin",
        new FunctionResult("Starting playback")
            .addAction("start_playback", Map.of("file", "${args.filename}")))

    // Stop commands
    .expression("${args.command}", "stop|pause|halt",
        new FunctionResult("Stopping playback")
            .addAction("stop_playback", true))

    // Volume commands
    .expression("${args.command}", "volume (\\d+)",
        new FunctionResult("Setting volume to ${match.1}")
            .addAction("set_volume", "${match.1}"));
```

**Pattern Matching Variables:**
- `${match.0}`: Full match
- `${match.1}`, `${match.2}`, etc.: Capture groups
- `${match.group_name}`: Named capture groups

### Error Handling

##### `errorKeys(List<String> keys)`
Specify response fields that indicate errors.

**Usage:**
```java
// Treat these response fields as errors
dataMap.errorKeys(List.of("error", "error_message", "status_code"));
```

##### `globalErrorKeys(List<String> keys)`
Set global error keys for all webhooks in this DataMap.

**Usage:**
```java
dataMap.globalErrorKeys(List.of("error", "message", "code"));
```

### Advanced Configuration

##### `webhookExpressions(List<Map<String, Object>> expressions)`
Add expression-based webhook selection.

**Usage:**
```java
dataMap.webhookExpressions(List.of(
    Map.of(
        "test", "${args.type}",
        "pattern", "weather",
        "webhook", Map.of(
            "method", "GET",
            "url", "https://weather-api.com/current?q=${args.location}")),
    Map.of(
        "test", "${args.type}",
        "pattern", "news",
        "webhook", Map.of(
            "method", "GET",
            "url", "https://news-api.com/search?q=${args.query}"))));
```

### Complete DataMap Examples

#### Simple Weather API

```java
var weatherTool = new DataMap("get_weather")
    .purpose("Get current weather information")
    .parameter("location", "string", "City name or ZIP code", true)
    .parameter("units", "string", "Temperature units", false, List.of("celsius", "fahrenheit"))
    .webhook("GET",
        "https://api.weather.com/v1/current?key=API_KEY&q=${args.location}&units=${args.units}")
    .output(new FunctionResult(
        "Weather in ${args.location}: ${response.current.condition.text}, ${response.current.temp_f}F"))
    .errorKeys(List.of("error"));

// Register with the agent
agent.registerSwaigFunction(weatherTool.toSwaigFunction());
```

#### Search with Array Processing

```java
var searchTool = new DataMap("search_knowledge")
    .purpose("Search company knowledge base")
    .parameter("query", "string", "Search query", true)
    .parameter("category", "string", "Search category", false, List.of("docs", "faq", "policies"))
    .webhook("POST", "https://api.company.com/search", Map.of("Authorization", "Bearer TOKEN"))
    .body(Map.of(
        "query", "${args.query}",
        "category", "${args.category}",
        "limit", 5))
    .foreach(Map.of("array", "${response.results}"))
    .output(new FunctionResult("Found: ${foreach.title} - ${foreach.summary}"))
    .fallbackOutput(new FunctionResult("Search service is temporarily unavailable"));
```

#### Command Processing (No API)

```java
var controlTool = new DataMap("system_control")
    .purpose("Control system functions")
    .parameter("action", "string", "Action to perform", true)
    .parameter("target", "string", "Target for the action")

    // Restart commands
    .expression("${args.action}", "restart|reboot",
        new FunctionResult("Restarting ${args.target}")
            .addAction("restart_service", Map.of("service", "${args.target}")))

    // Status commands
    .expression("${args.action}", "status|check",
        new FunctionResult("Checking status of ${args.target}")
            .addAction("check_status", Map.of("service", "${args.target}")))

    // Default for unrecognized commands
    .expression("${args.action}", ".*",
        new FunctionResult("Unknown command: ${args.action}"),
        new FunctionResult("Please specify a valid action"));
```

### Conversion and Registration

##### `toSwaigFunction()`
Convert the DataMap to a SWAIG function map for registration.

**Returns:** `Map<String, Object>` — the complete SWAIG function definition.

**Usage:**
```java
// Build the DataMap
var weatherMap = new DataMap("get_weather")
    .purpose("Get weather")
    .parameter("location", "string", "City", true);

// Convert to a SWAIG function and register
Map<String, Object> swaigFunction = weatherMap.toSwaigFunction();
agent.registerSwaigFunction(swaigFunction);
```

### Method Chaining

All DataMap methods return `this`, enabling fluent method chaining:

```java
var completeTool = new DataMap("comprehensive_search")
    .purpose("Comprehensive search with fallbacks")
    .parameter("query", "string", "Search query", true)
    .parameter("category", "string", "Search category", false, List.of("all", "docs", "faq"))
    .webhook("GET", "https://primary-api.com/search?q=${args.query}&cat=${args.category}")
    .output(new FunctionResult("Primary: ${response.title}"))
    .webhook("GET", "https://backup-api.com/search?q=${args.query}")
    .output(new FunctionResult("Backup: ${response.title}"))
    .fallbackOutput(new FunctionResult("All search services unavailable"))
    .errorKeys(List.of("error", "message"));
```

This concludes the DataMap class reference. The document continues with the Context System and other components.

---

## Context System

The Context System enhances traditional prompt-based agents by adding structured workflows with sequential steps on top of a base prompt. Each step contains its own guidance, completion criteria, and function restrictions while building upon the agent's foundational prompt.

`import com.signalwire.sdk.contexts.ContextBuilder;`

### ContextBuilder Class

The `ContextBuilder` is accessed via `agent.defineContexts()` and provides the main interface for creating structured workflows.

#### Getting Started

```java
// Access the context builder
var contexts = agent.defineContexts();

// Create contexts and steps
contexts.addContext("greeting")
    .addStep("welcome")
    .setText("Welcome! How can I help you today?")
    .setStepCriteria("User has stated their need")
    .setValidSteps(List.of("next"));
```

##### `addContext(String name)`
Create a new context in the workflow.

**Returns:** `Context` — the context object for method chaining.

**Usage:**
```java
// Create multiple contexts
var greetingContext = contexts.addContext("greeting");
var mainMenuContext = contexts.addContext("main_menu");
var supportContext = contexts.addContext("support");
```

### Context Class

The `Context` class represents a conversation context containing multiple steps. Key methods (all return `Context` for chaining unless noted):

```java
Step addStep(String name)                       // Create a new step (returns the Step)
Context setValidContexts(List<String> contexts) // Which contexts are reachable from here
Context setValidSteps(List<String> steps)       // Valid next steps

// Context entry parameters (context-switch behavior)
Context setPostPrompt(String postPrompt)        // Override agent's post prompt here
Context setSystemPrompt(String systemPrompt)    // Trigger a context switch (makes this a switch context)
Context setConsolidate(boolean consolidate)     // Consolidate history on entry
Context setFullReset(boolean fullReset)         // Full system-prompt replacement vs injection
Context setUserPrompt(String userPrompt)        // User message injected on entry

// Context prompts (guidance for all steps in this context)
Context setPrompt(String prompt)                // Simple string prompt for all steps
Context addSection(String title, String body)   // POM-style section
Context addBullets(String title, List<String> bullets) // POM-style bullet section
```

**Context Types:**

1. **Workflow Container Context** (no system prompt): Organizes steps without conversation state changes
2. **Context Switch Context** (has system prompt): Triggers conversation state changes when entered, processing entry parameters like a `context_switch` SWAIG action

**Prompt Hierarchy:** Base Agent Prompt → Context Prompt → Step Prompt

#### Usage Examples

```java
// Workflow container context (just organizes steps)
var mainContext = contexts.addContext("main");
mainContext.setPrompt("Follow standard customer service protocols");

// Context switch context (changes AI behavior)
var billingContext = contexts.addContext("billing");
billingContext.setSystemPrompt("You are now a billing specialist")
    .setConsolidate(true)
    .setUserPrompt("Customer needs billing assistance")
    .addSection("Department", "Billing Department")
    .addBullets("Services", List.of("Account inquiries", "Payments", "Refunds"));

// Full reset context (complete conversation reset)
var managerContext = contexts.addContext("manager");
managerContext.setSystemPrompt("You are a senior manager")
    .setFullReset(true)
    .setConsolidate(true);
```

### Step Class

Steps are created via `Context.addStep(name)`. Key methods (all return `Step` for chaining):
- `setText(String text)`: The step's spoken/instruction text
- `setStepCriteria(String criteria)`: Completion criteria for advancing
- `setFunctions(Object functions)`: Restrict which functions are callable (a `List<String>`, or `"none"`)
- `setValidSteps(List<String> steps)` / `setValidContexts(List<String> contexts)`: Allowed transitions
- `addSection(String title, String body)` / `addBullets(String title, List<String> bullets)`: POM-style prompt content
- `setEnd(boolean end)`, `setSkipUserTurn(boolean)`, `setSkipToNextStep(boolean)`: Step flow flags

**Usage:**
```java
var greeting = contexts.addContext("greeting");
greeting.addStep("welcome")
    .setText("Hello! Welcome to Acme Corp support. How can I help you today?")
    .setStepCriteria("Customer has explained their issue")
    .setValidSteps(List.of("categorize"));

greeting.addStep("categorize")
    .addSection("Current Task", "Categorize the customer's request")
    .addBullets("Categories", List.of(
        "Technical issue - use diagnostic tools",
        "Billing question - transfer to billing",
        "General inquiry - handle directly"))
    .setFunctions(List.of("transfer_to_billing", "run_diagnostics"))
    .setStepCriteria("Request categorized and action taken");
```

---

## Skills System

The Skills System provides modular, reusable capabilities that can be easily added to any agent via `agent.addSkill(name, params)`.

### Available Built-in Skills

#### `datetime` Skill
Provides current date and time information.

**Parameters:**
- `timezone` (optional): Timezone for date/time (default: system timezone)
- `format` (optional): Custom date/time format string

**Usage:**
```java
// Basic datetime skill
agent.addSkill("datetime", null);

// With timezone
agent.addSkill("datetime", Map.of("timezone", "America/New_York"));

// With custom format
agent.addSkill("datetime", Map.of(
    "timezone", "UTC",
    "format", "yyyy-MM-dd HH:mm:ss z"));
```

#### `math` Skill
Safe mathematical expression evaluation.

**Parameters:**
- `precision` (optional): Decimal precision for results (default: 2)
- `max_expression_length` (optional): Maximum expression length (default: 100)

**Usage:**
```java
// Basic math skill
agent.addSkill("math", null);

// With custom precision
agent.addSkill("math", Map.of("precision", 4));
```

#### `web_search` Skill
Google Custom Search API integration with web scraping.

**Parameters:**
- `api_key` (required): Google Custom Search API key
- `search_engine_id` (required): Google Custom Search Engine ID
- `num_results` (optional): Number of results to return (default: 3)
- `tool_name` (optional): Custom tool name for multiple instances
- `delay` (optional): Delay between requests in seconds
- `no_results_message` (optional): Custom message when no results found

**Usage:**
```java
// Basic web search
agent.addSkill("web_search", Map.of(
    "api_key", "your-google-api-key",
    "search_engine_id", "your-search-engine-id"));

// Multiple search instances
agent.addSkill("web_search", Map.of(
    "api_key", "your-api-key",
    "search_engine_id", "general-engine-id",
    "tool_name", "search_general",
    "num_results", 5));

agent.addSkill("web_search", Map.of(
    "api_key", "your-api-key",
    "search_engine_id", "news-engine-id",
    "tool_name", "search_news",
    "num_results", 3,
    "delay", 0.5));
```

#### `datasphere` Skill
SignalWire DataSphere knowledge search integration.

**Parameters:**
- `space_name` (required): DataSphere space name
- `project_id` (required): DataSphere project ID
- `token` (required): DataSphere access token
- `document_id` (optional): Specific document to search
- `tool_name` (optional): Custom tool name for multiple instances
- `count` (optional): Number of results to return (default: 3)
- `tags` (optional): Filter by document tags

**Usage:**
```java
// Basic DataSphere search
agent.addSkill("datasphere", Map.of(
    "space_name", "my-space",
    "project_id", "my-project",
    "token", "my-token"));

// Multiple DataSphere instances
agent.addSkill("datasphere", Map.of(
    "space_name", "my-space",
    "project_id", "my-project",
    "token", "my-token",
    "document_id", "drinks-menu",
    "tool_name", "search_drinks",
    "count", 5));

agent.addSkill("datasphere", Map.of(
    "space_name", "my-space",
    "project_id", "my-project",
    "token", "my-token",
    "tool_name", "search_policies",
    "tags", List.of("HR", "Policies")));
```

#### `native_vector_search` Skill
Document search against a **remote** vector-search server over HTTP. (The Java port supports remote mode only; it does not build or read local index files.)

**Parameters:**
- `remote_url` (required): URL of the remote search server endpoint
- `index_name` (optional): Index name to query on the remote server
- `tool_name` (optional): Custom tool name (default: `"search_knowledge"`)
- `count` (optional): Maximum results to return (default: 3)
- `hints` (optional): Additional speech hints to register

**Usage:**
```java
// Remote search
agent.addSkill("native_vector_search", Map.of(
    "remote_url", "http://localhost:8001/search",
    "index_name", "knowledge"));

// With custom settings
agent.addSkill("native_vector_search", Map.of(
    "remote_url", "http://localhost:8001/search",
    "index_name", "docs",
    "tool_name", "search_docs",
    "count", 10));
```

### Creating Custom Skills

#### Skill Structure

Create a new skill by implementing the `SkillBase` interface. Required methods are `getName()`, `getDescription()`, `setup(params)`, and `registerTools()`; `getVersion()`, `getRequiredEnvVars()`, `getHints()`, `getGlobalData()`, `getPromptSections()`, and `getSwaigFunctions()` have sensible defaults you can override. `registerTools()` returns the `ToolDefinition`s (and `getSwaigFunctions()` returns DataMap-style function maps) that the `SkillManager` registers with the agent — skills return their tools rather than registering directly.

`import com.signalwire.sdk.skills.SkillBase;`

```java
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.ToolDefinition;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.datamap.DataMap;
import java.util.List;
import java.util.Map;

public class CustomSkill implements SkillBase {

    private String apiKey;

    @Override
    public String getName() {
        return "custom_skill";
    }

    @Override
    public String getDescription() {
        return "Description of what this skill does";
    }

    @Override
    public List<String> getRequiredEnvVars() {
        return List.of("API_KEY");
    }

    @Override
    public boolean setup(Map<String, Object> params) {
        // Validate and store configuration
        if (params.get("api_key") == null) {
            return false;
        }
        this.apiKey = (String) params.get("api_key");
        return true;
    }

    @Override
    public List<ToolDefinition> registerTools() {
        // Handler-based tools go here; DataMap tools go in getSwaigFunctions().
        return List.of();
    }

    @Override
    public List<Map<String, Object>> getSwaigFunctions() {
        // DataMap-based tool (bypasses handlers, executes server-side)
        var tool = new DataMap("custom_function")
            .description("Custom API integration")
            .parameter("query", "string", "Search query", true)
            .webhook("GET",
                "https://api.example.com/search?key=" + apiKey + "&q=${args.query}")
            .output(new FunctionResult("Found: ${response.title}"));
        return List.of(tool.toSwaigFunction());
    }

    @Override
    public List<String> getHints() {
        return List.of("custom search", "find information");
    }

    @Override
    public Map<String, Object> getGlobalData() {
        return Map.of("skill_version", getVersion());
    }

    @Override
    public List<Map<String, Object>> getPromptSections() {
        return List.of(Map.of(
            "title", "Custom Search Capability",
            "body", "You can search our custom database for information.",
            "bullets", List.of("Use the custom_function to search", "Results are real-time")));
    }
}
```

#### Skill Registration

Built-in skills live in the `skills/builtin/` package and are discovered by the `SkillManager` / `SkillRegistry`. Once a skill class is registered with the registry, add it to an agent by its name via `agent.addSkill(name, params)`.

---

## Utility Classes

### ToolDefinition Class

Represents a SWAIG function definition with metadata. Used with `defineTool(ToolDefinition)` when you need extra SWAIG fields beyond the `defineTool(name, description, parameters, handler)` shortcut.

`import com.signalwire.sdk.swaig.ToolDefinition;`

#### Construction

```java
new ToolDefinition(String name, String description, Map<String, Object> parameters, ToolHandler handler)
```

**Parameters:**
- `name`: Function name
- `description`: Function description
- `parameters`: JSON schema for parameters
- `handler`: `ToolHandler` lambda `(args, raw) -> FunctionResult`

#### Usage

```java
import com.signalwire.sdk.swaig.ToolDefinition;
import com.signalwire.sdk.swaig.FunctionResult;

var def = new ToolDefinition(
    "get_weather",
    "Get current weather",
    Map.of(
        "type", "object",
        "properties", Map.of("location", Map.of("type", "string", "description", "City name")),
        "required", List.of("location")),
    (args, raw) -> new FunctionResult("Sunny and 75F"));
def.setSecure(true);
def.setExtraFields(Map.of("fillers", Map.of("en-US", List.of("Checking weather..."))));

// Register with the agent
agent.defineTool(def);
```

### Service Class

`Service` (package `com.signalwire.sdk.swml`) is the base class providing SWML document generation and HTTP service capabilities. `AgentBase` extends `Service`. It exposes 38 schema-driven SWML verb methods plus SWML rendering.

#### Key Methods

##### `renderSwml(String baseUrl)`
Generate the complete SWML document (`Map<String, Object>`) for the service.

##### `handleRequest(...)`
Handle incoming HTTP requests and generate appropriate responses (returns an `HttpResult`).

### Dynamic Configuration

The dynamic configuration callback receives the agent instance directly, allowing you to configure it based on request data. Register it with `setDynamicConfigCallback`; the `DynamicConfigCallback` receives `(queryParams, headers, body, agent)`.

**Usage:**
```java
agent.setDynamicConfigCallback((queryParams, headers, body, cfg) -> {
    // Configure based on request
    if ("es".equals(queryParams.get("lang"))) {
        cfg.addLanguage("Spanish", "es-ES", "nova.luna");
    }

    // Customer-specific configuration
    String customerId = headers.get("X-Customer-ID");
    if (customerId != null) {
        cfg.setGlobalData(Map.of("customer_id", customerId));
        cfg.promptAddSection("Customer Context", "You are helping customer " + customerId);
    }

    // Add skills dynamically
    if ("true".equals(queryParams.get("enable_search"))) {
        cfg.addSkill("web_search", Map.of("provider", "google"));
    }
});
```

---

## Environment Variables

The SDK supports various environment variables for configuration:

### Authentication
- `SWML_BASIC_AUTH_USER`: Basic auth username
- `SWML_BASIC_AUTH_PASSWORD`: Basic auth password

### SSL/HTTPS
- `SWML_SSL_ENABLED`: Enable SSL (true/false)
- `SWML_SSL_CERT_PATH`: Path to SSL certificate
- `SWML_SSL_KEY_PATH`: Path to SSL private key
- `SWML_DOMAIN`: Domain name for SSL

### Proxy Support
- `SWML_PROXY_URL_BASE`: Base URL for the proxy server

### Skills Configuration
- `GOOGLE_SEARCH_API_KEY`: Google Custom Search API key
- `GOOGLE_SEARCH_ENGINE_ID`: Google Custom Search Engine ID
- `DATASPHERE_SPACE_NAME`: DataSphere space name
- `DATASPHERE_PROJECT_ID`: DataSphere project ID
- `DATASPHERE_TOKEN`: DataSphere access token

### Usage

Environment variables are read automatically by the agent (basic-auth credentials, proxy base, skill credentials). To supply a value programmatically for testing, use the builder's `envProvider(EnvProvider)` hook; otherwise the process environment is used.

```java
// The agent reads SWML_BASIC_AUTH_USER / SWML_BASIC_AUTH_PASSWORD /
// GOOGLE_SEARCH_API_KEY from the environment automatically.
var agent = AgentBase.builder().name("My Agent").build();
agent.addSkill("web_search", Map.of(
    "search_engine_id", "your-engine-id"
    // api_key is read from GOOGLE_SEARCH_API_KEY in the environment
));
```

---

## Complete Example

Here's a comprehensive example using multiple SDK components:

```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.datamap.DataMap;

import java.util.List;
import java.util.Map;

public class ComprehensiveAgent {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
            .name("Comprehensive Agent")
            .route("/agent")
            .port(3000)
            .autoAnswer(true)
            .recordCall(true)
            .build();

        // Configure voice and language
        agent.addLanguage("English", "en-US", "rime.spore",
            List.of("Let me check...", "One moment..."),
            null, null, null);

        // Add speech recognition hints
        agent.addHints(List.of("SignalWire", "customer service", "technical support"));

        // Configure AI parameters
        agent.setParams(Map.of(
            "ai_model", "gpt-4.1-nano",
            "end_of_speech_timeout", 800,
            "temperature", 0.7));

        // Add skills
        agent.addSkill("datetime", null);
        agent.addSkill("math", null);
        agent.addSkill("web_search", Map.of(
            "api_key", "your-google-api-key",
            "search_engine_id", "your-engine-id",
            "num_results", 3));

        // Set up a structured workflow
        var contexts = agent.defineContexts();

        var greeting = contexts.addContext("greeting");
        greeting.addStep("welcome")
            .setText("Hello! Welcome to Acme Corp support. How can I help you today?")
            .setStepCriteria("Customer has explained their issue")
            .setValidSteps(List.of("categorize"));
        greeting.addStep("categorize")
            .addSection("Current Task", "Categorize the customer's request")
            .addBullets("Categories", List.of(
                "Technical issue - use diagnostic tools",
                "Billing question - transfer to billing",
                "General inquiry - handle directly"))
            .setFunctions(List.of("transfer_to_billing", "run_diagnostics"))
            .setStepCriteria("Request categorized and action taken");

        var tech = contexts.addContext("technical_support");
        tech.addStep("diagnose")
            .setText("Let me run some diagnostics to identify the issue.")
            .setFunctions(List.of("run_diagnostics", "check_system_status"))
            .setStepCriteria("Diagnostics completed")
            .setValidSteps(List.of("resolve"));
        tech.addStep("resolve")
            .setText("Based on the diagnostics, here's how we'll fix this.")
            .setFunctions(List.of("apply_fix", "schedule_technician"))
            .setStepCriteria("Issue resolved or escalated");

        // Register custom DataMap tools
        var lookupTool = new DataMap("lookup_customer")
            .description("Look up customer information")
            .parameter("customer_id", "string", "Customer ID", true)
            .webhook("GET", "https://api.company.com/customers/${args.customer_id}",
                Map.of("Authorization", "Bearer YOUR_TOKEN"))
            .output(new FunctionResult("Customer: ${response.name}, Status: ${response.status}"))
            .errorKeys(List.of("error"));
        agent.registerSwaigFunction(lookupTool.toSwaigFunction());

        var controlTool = new DataMap("system_control")
            .description("Control system functions")
            .parameter("action", "string", "Action to perform", true)
            .parameter("target", "string", "Target system")
            .expression("${args.action}", "restart|reboot",
                new FunctionResult("Restarting ${args.target}")
                    .addAction("restart_system", Map.of("target", "${args.target}")))
            .expression("${args.action}", "status|check",
                new FunctionResult("Checking ${args.target} status")
                    .addAction("check_status", Map.of("target", "${args.target}")));
        agent.registerSwaigFunction(controlTool.toSwaigFunction());

        // A code-defined tool with state tracking
        agent.defineTool(
            "transfer_to_billing",
            "Transfer call to billing department",
            Map.of("type", "object", "properties", Map.of()),
            (toolArgs, raw) -> new FunctionResult("Transferring you to our billing department")
                .updateGlobalData(Map.of("last_action", "transfer_to_billing"))
                .connect("billing@company.com", false));

        // Set global data
        agent.setGlobalData(Map.of(
            "company_name", "Acme Corp",
            "support_hours", "9 AM - 5 PM EST",
            "version", "2.0"));

        // Handle conversation summaries
        agent.onSummary((summary, raw) ->
            System.out.println("Conversation completed: " + summary));

        // Run the agent
        agent.run();
    }
}
```
