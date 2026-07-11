# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

<!-- snippet-setup -->
```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.datamap.DataMap;
```

## Project Overview

This is the **SignalWire AI Agents SDK for Java** -- a Java 21+ framework for building, deploying, and managing AI agents as microservices. The SDK provides tools for creating self-contained web applications that expose HTTP endpoints to interact with the SignalWire platform.

The Java SDK mirrors the Python SignalWire Agents SDK, offering the same agent patterns, SWML rendering, SWAIG tool system, skills framework, contexts/steps workflow, DataMap integration, and RELAY/REST clients.

## Development Commands

Test / lint / format go through the canonical `scripts/run-*.sh` entry points.
They self-bootstrap their tool environment (resolve the repo root from the
script's own path, locate the Gradle **wrapper** `./gradlew`, and resolve
`JAVA_HOME`) and run correctly from ANY directory — call them instead of raw
`gradle test` / `spotless*` / `checkstyle*` directly (see
porting-sdk/RUN_LINT_FORMAT_SPEC.md). `scripts/run-ci.sh` invokes these same
scripts for its TEST/FMT/LINT gates, so local == CI. The shared bootstrap lives
in `scripts/_env.sh`.

### Build & Test
```bash
# Run the full test suite (canonical entry point; self-bootstraps, any CWD)
bash scripts/run-tests.sh

# Run a subset — pass a test class or method as the filter
# (forwarded as gradle `--tests <filter>`)
bash scripts/run-tests.sh "com.signalwire.sdk.AgentBaseTest"
bash scripts/run-tests.sh "com.signalwire.sdk.rest.*"
```

### Formatting and Linting
```bash
# Format (spotless / google-java-format): APPLY in place (default)
bash scripts/run-format.sh
# Format VERIFY-ONLY (what CI runs): fails if anything is unformatted
bash scripts/run-format.sh --check

# Lint (Error Prone warnings-as-errors + Checkstyle, zero findings):
# report-only — Java's linters have no safe autofix
bash scripts/run-lint.sh
```

### Raw Gradle (when you need a task the scripts don't wrap)
```bash
# The scripts use the Gradle WRAPPER; use it directly for other tasks:
./gradlew build -x test        # build only (skip tests)
./gradlew jar                  # build the jar
```

### CLI Tool
```bash
# Test SWAIG functions against a running agent
bin/swaig-test --url http://user:pass@localhost:3000 --list-tools
bin/swaig-test --url http://user:pass@localhost:3000 --dump-swml
bin/swaig-test --url http://user:pass@localhost:3000 --exec tool_name --param key=value
```

### Installation
```bash
# Build the jar
$GRADLE jar

# The jar is produced at build/libs/signalwire-sdk-2.0.0.jar
```

## Architecture Overview

### Core Components
1. **AgentBase** (`agent/AgentBase.java`) -- Base class for all AI agents with builder pattern
2. **Service** (`swml/Service.java`) -- Foundation SWML service with 38 schema-driven verb methods
3. **Document** (`swml/Document.java`) -- SWML document builder
4. **AgentServer** (`server/AgentServer.java`) -- Multi-agent hosting server with SIP routing
5. **SkillBase/SkillManager** (`skills/`) -- Modular capabilities framework with 17 built-in skills
6. **ContextBuilder** (`contexts/`) -- Structured workflow management (contexts + steps)
7. **DataMap** (`datamap/DataMap.java`) -- Server-side API integration without webhooks
8. **FunctionResult** (`swaig/FunctionResult.java`) -- 40+ action methods for tool responses
9. **RelayClient** (`relay/RelayClient.java`) -- Real-time call control via WebSocket
10. **RestClient** (`rest/RestClient.java`) -- REST API client with 20 namespaces

### Key Patterns

#### Agent Creation (Builder Pattern)
```java
var agent = AgentBase.builder()
    .name("my-agent")
    .route("/")
    .port(3000)
    .build();

agent.promptAddSection("Role", "You are a helpful assistant.");
agent.defineTool("greet", "Greet the user",
    Map.of("type", "object", "properties", Map.of(
        "name", Map.of("type", "string", "description", "User name")
    )),
    (args, raw) -> new FunctionResult("Hello, " + args.get("name") + "!"));
agent.run();
```

#### Skills System
Skills are self-contained modules in `skills/builtin/`. Each implements `SkillBase`:
- `setup(params)` for initialization
- `registerTools()` returning `List<ToolDefinition>`
- Optional: `getHints()`, `getGlobalData()`, `getPromptSections()`, `getSwaigFunctions()`

#### DataMap Tools
```java
var agent = AgentBase.builder().name("my-agent").route("/").build();
var dm = new DataMap("weather")
    .purpose("Get current weather")
    .parameter("city", "string", "City name", true)
    .webhook("GET", "https://api.weather.com/${args.city}")
    .output(new FunctionResult("Weather: ${response.temp}F"));
agent.registerSwaigFunction(dm.toSwaigFunction());
```

### Package Layout
```
com.signalwire.sdk/
    agent/        AgentBase (builder, prompts, tools, AI config, HTTP server)
    server/       AgentServer (multi-agent hosting)
    swml/         Document, Schema, Service (SWML rendering)
    swaig/        FunctionResult, ToolDefinition, ToolHandler
    datamap/      DataMap (server-side tool builder)
    contexts/     ContextBuilder, Context, Step, GatherInfo, GatherQuestion
    skills/       SkillBase, SkillManager, SkillRegistry, builtin/*
    prefabs/      InfoGathererAgent, SurveyAgent, ConciergeAgent, etc.
    security/     SessionManager (HMAC-SHA256 tokens)
    logging/      Logger (level-controlled, env-var driven)
    relay/        RelayClient, Call, Action, Message, RelayEvent, Constants
    rest/         RestClient, HttpClient, CrudResource, 20 namespaces
```

### Testing
- Unit tests in `src/test/java/com/signalwire/sdk/`
- JUnit 5 with `@Test` annotations
- All tests run via `gradle test`
- Test classes: AgentBaseTest, ContextsTest, DataMapTest, FunctionResultTest,
  PrefabsTest, SecurityTest, ServerTest, SkillsTest, SwmlTest, RelayTest, RestTest

## Important Implementation Notes

### Security
- Basic auth uses `MessageDigest.isEqual()` for timing-safe comparison (all three: AgentBase, AgentServer, Service)
- Passwords auto-generated via `SecureRandom` (32 bytes, URL-safe Base64) -- never falls back to weak defaults
- Session tokens use HMAC-SHA256 signing
- All authenticated endpoints include security headers: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-store`
- Body size limited to 1 MB on all request handlers
- No passwords are logged anywhere

### HTTP Server
- Uses JDK built-in `com.sun.net.httpserver.HttpServer` with its default executor
- No external web framework dependency
- Port defaults to 3000, configurable via `PORT` env var or builder

### Dependencies
- `com.google.code.gson:gson:2.10.1` -- JSON serialization
- `org.java-websocket:Java-WebSocket:1.5.6` -- WebSocket for RELAY client
- JUnit 5 for testing

### Environment Variables
| Variable | Description |
|----------|-------------|
| `PORT` | HTTP server port (default: 3000) |
| `SWML_BASIC_AUTH_USER` | Override auth username |
| `SWML_BASIC_AUTH_PASSWORD` | Override auth password |
| `SWML_PROXY_URL_BASE` | Proxy URL base for webhook URLs |
| `SWML_SSL_ENABLED` | Enable SSL/HTTPS |
| `SIGNALWIRE_LOG_LEVEL` | Log level: debug/info/warn/error/off |
| `SIGNALWIRE_LOG_MODE` | Set to "off" to suppress all output |
| `SIGNALWIRE_PROJECT_ID` | Project ID for RELAY/REST clients |
| `SIGNALWIRE_API_TOKEN` | API token for RELAY/REST clients |
| `SIGNALWIRE_JWT_TOKEN` | JWT token for RELAY client auth (alternative to `SIGNALWIRE_API_TOKEN`) |
| `SIGNALWIRE_SPACE` | Space hostname for RELAY/REST clients |
