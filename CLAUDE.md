# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the **SignalWire AI Agents SDK for Java** -- a Java 21+ framework for building, deploying, and managing AI agents as microservices. The SDK provides tools for creating self-contained web applications that expose HTTP endpoints to interact with the SignalWire platform.

The Java SDK mirrors the Python SignalWire Agents SDK, offering the same agent patterns, SWML rendering, SWAIG tool system, skills framework, contexts/steps workflow, DataMap integration, and RELAY/REST clients.

## Development Commands

### Build & Test
```bash
# Set environment
export JAVA_HOME=/home/devuser/jdk-21
GRADLE=/home/devuser/gradle/gradle-8.5/bin/gradle

# Full build with tests
$GRADLE clean test

# Build only (skip tests)
$GRADLE build -x test

# Run a specific test class
$GRADLE test --tests "com.signalwire.agents.AgentBaseTest"

# Run with verbose output
$GRADLE test --info
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

# The jar is produced at build/libs/signalwire-agents-1.0.0.jar
```

## Architecture Overview

### Core Components
1. **AgentBase** (`agent/AgentBase.java`) -- Base class for all AI agents with builder pattern
2. **Service** (`swml/Service.java`) -- Foundation SWML service with 38 schema-driven verb methods
3. **Document** (`swml/Document.java`) -- SWML document builder
4. **AgentServer** (`server/AgentServer.java`) -- Multi-agent hosting server with SIP routing
5. **SkillBase/SkillManager** (`skills/`) -- Modular capabilities framework with 18 built-in skills
6. **ContextBuilder** (`contexts/`) -- Structured workflow management (contexts + steps)
7. **DataMap** (`datamap/DataMap.java`) -- Server-side API integration without webhooks
8. **FunctionResult** (`swaig/FunctionResult.java`) -- 40+ action methods for tool responses
9. **RelayClient** (`relay/RelayClient.java`) -- Real-time call control via WebSocket
10. **SignalWireClient** (`rest/SignalWireClient.java`) -- REST API client with 21 namespaces

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
var dm = new DataMap("weather")
    .purpose("Get current weather")
    .parameter("city", "string", "City name", true)
    .webhook("GET", "https://api.weather.com/${args.city}")
    .output(new FunctionResult("Weather: ${response.temp}F"));
agent.registerSwaigFunction(dm.toSwaigFunction());
```

### Package Layout
```
com.signalwire.agents/
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
    rest/         SignalWireClient, HttpClient, CrudResource, 21 namespaces
```

### Testing
- Unit tests in `src/test/java/com/signalwire/agents/`
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
- Uses JDK built-in `com.sun.net.httpserver.HttpServer` with virtual threads
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
| `SIGNALWIRE_SPACE` | Space hostname for RELAY/REST clients |
