# SignalWire AI Agent Guide

## Table of Contents
- [Introduction](#introduction)
- [Architecture Overview](#architecture-overview)
- [Creating an Agent](#creating-an-agent)
- [Prompt Building](#prompt-building)
- [SWAIG Functions (SignalWire AI Gateway)](#swaig-functions)
- [Skills System](#skills-system)
- [Multilingual Support](#multilingual-support)
- [Agent Configuration](#agent-configuration)
- [Dynamic Agent Configuration](#dynamic-agent-configuration)
  - [Overview](#overview)
  - [Setting Up Dynamic Configuration](#setting-up-dynamic-configuration)
  - [Dynamic Configuration Methods](#dynamic-configuration-methods)
  - [Request Data Access](#request-data-access)
  - [Configuration Examples](#configuration-examples)
  - [Use Cases](#use-cases)
  - [Migration Guide](#migration-guide)
  - [Best Practices](#best-practices)
- [Advanced Features](#advanced-features)
  - [State Management](#state-management)
  - [SIP Routing](#sip-routing)
  - [Custom Routing](#custom-routing)
- [Prefab Agents](#prefab-agents)
- [API Reference](#api-reference)
- [Examples](#examples)

## Introduction

The `AgentBase` class provides the foundation for creating AI-powered agents using the SignalWire AI Agent SDK. It extends the `Service` class (the SWML service base), inheriting all its SWML (SignalWire Markup Language) document creation and serving capabilities, while adding AI-specific functionality. SWML is the JSON document format that tells the SignalWire platform how an agent should behave during a call.

<!-- snippet-setup -->
```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

AgentBase agent = AgentBase.builder().name("guide-agent").route("/agent").build();
```

Key features of `AgentBase` include:

- Structured prompt building with POM (Prompt Object Model)
- SWAIG (SignalWire AI Gateway) function definitions -- SWAIG is the platform's AI tool-calling system with native access to the media stack
- Multilingual support
- Agent configuration (hint handling, pronunciation rules, etc.)
- State management for conversations

This guide explains how to create and customize your own AI agents, with examples based on the SDK's sample implementations.

## Architecture Overview

The Agent SDK architecture consists of several layers:

1. **SWMLService**: The base layer for SWML document creation and serving
2. **AgentBase**: Extends SWMLService with AI agent functionality
3. **Custom Agents**: Your specific agent implementations that extend AgentBase

Here's how these components relate to each other:

```
┌─────────────┐
│ Your Agent  │ (Extends AgentBase with your specific functionality)
└─────▲───────┘
      │
┌─────┴───────┐
│  AgentBase  │ (Adds AI functionality to SWMLService)
└─────▲───────┘
      │
┌─────┴───────┐
│ SWMLService │ (Provides SWML document creation and web service)
└─────────────┘
```

## Creating an Agent

To create an agent, build an `AgentBase` with the fluent builder and configure its behavior via the instance methods:

```java
import com.signalwire.sdk.agent.AgentBase;

import java.util.List;

agent = AgentBase.builder()
        .name("my-agent")
        .route("/agent")
        .host("0.0.0.0")
        .port(3000)
        .build();

// Define agent personality and behavior
agent.promptAddSection("Personality", "You are a helpful and friendly assistant.");
agent.promptAddSection("Goal", "Help users with their questions and tasks.");
agent.promptAddSection("Instructions", "", List.of(
        "Answer questions clearly and concisely",
        "If you don't know, say so",
        "Use the provided tools when appropriate"
));

// Add a post-prompt for summary
agent.setPostPrompt("Please summarize the key points of this conversation.");
```

The Prompt Object Model (POM) is always enabled for structured prompts; `promptAddSection` builds it up section by section. For a reusable agent type, put this configuration in a factory method or a subclass of `AgentBase`.

## Running Your Agent

The SignalWire AI Agent SDK provides a `run()` method that starts the agent's HTTP server, binding to the host/port configured on the builder.

### Deployment with `run()`

```java
import com.signalwire.sdk.agent.AgentBase;

public class MyAgentMain {
    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("my-agent")
                .route("/agent")
                .port(3000)
                .build();

        System.out.println("Starting agent server...");
        agent.run();  // Starts the built-in HTTP server (blocks)
    }
}
```

The `run()` method starts the built-in HTTP server (JDK `com.sun.net.httpserver.HttpServer`, using its default executor — no external web framework). The `PORT` environment variable overrides the builder's port when set.

### Logging Configuration

The SDK includes a central logging system controlled by environment variables:

```java
// Logging is configured from environment variables; no manual setup needed.
//
// SIGNALWIRE_LOG_LEVEL=debug|info|warn|error|off  // Log verbosity
// SIGNALWIRE_LOG_MODE=off                          // Set to "off" to suppress all output
```

The logging system:
- **Server Mode**: Uses structured logging with timestamps and levels
- **Silent Mode**: `SIGNALWIRE_LOG_MODE=off` suppresses all output
- **Debug Mode**: Enhanced logging when `SIGNALWIRE_LOG_LEVEL=debug`

## Prompt Building

There are several ways to build prompts for your agent:

### 1. Using Prompt Sections (POM)

The Prompt Object Model (POM) provides a structured way to build prompts:

```java
import java.util.List;

// Add a section with just body text
agent.promptAddSection("Personality", "You are a friendly assistant.");

// Add a section with bullet points (empty body, then the bullets list)
agent.promptAddSection("Instructions", "", List.of(
        "Answer questions clearly",
        "Be helpful and polite",
        "Use functions when appropriate"
));

// Add a section with both body and bullets
agent.promptAddSection("Context",
        "The user is calling about technical support.",
        List.of("They may need help with their account",
                "Check for existing tickets"));
```

All three sections (Personality, Goal, Instructions) are built on top of the same `promptAddSection(title, body)` / `promptAddSection(title, body, bullets)` overloads — there are no `setPersonality` / `setGoal` / `setInstructions` wrappers in this SDK. Pass the section title ("Personality", "Goal", "Instructions") explicitly, as shown above.

### 2. Using Raw Text Prompts

For simpler agents, you can set the prompt directly as text:

```java
agent.setPromptText("""
        You are a helpful assistant. Your goal is to provide clear and concise information
        to the user. Answer their questions to the best of your ability.
        """);
```

### 3. Setting a Post-Prompt

The post-prompt is sent to the AI after the conversation for summary or analysis:

```java
agent.setPostPrompt("""
        Analyze the conversation and extract:
        1. Main topics discussed
        2. Action items or follow-ups needed
        3. Whether the user's questions were answered satisfactorily
        """);
```

## SWAIG Functions

SWAIG (SignalWire AI Gateway) functions allow the AI agent to perform actions and access external systems during a call. The AI decides when to call a function based on the conversation; SWAIG handles invocation, parameter passing, and delivering the result back to the AI. There are two types of SWAIG functions you can define:

### SWAIG functions ARE LLM tools — descriptions matter

Before writing your first SWAIG function, internalize this: a SWAIG function is **exactly the same concept** as a "tool" in native OpenAI / Anthropic tool calling. There is no separate "SWAIG layer" between your function and the model. Each SWAIG function is rendered into the OpenAI tool schema format on every turn:

```json
{
  "type": "function",
  "function": {
    "name":        "your_function_name",
    "description": "your description text",
    "parameters":  { /* your JSON schema */ }
  }
}
```

That schema is sent to the model as part of the same API call that produces the next assistant message. The model reads:

- the **function `description`** to decide WHEN to call this tool
- the **per-parameter `description` strings** inside `parameters` to decide HOW to fill in each argument

This means **descriptions are prompt engineering**, not developer documentation. They are not a comment for the next human reading the code — they are instructions to the LLM that directly determine whether the model picks your tool when the user's request matches it.

Compare:

| Bad (model often misses the tool) | Good (model picks it reliably) |
|---|---|
| `description: "Lookup function"` | `description: "Look up a customer's account details by their account number. Use this BEFORE quoting any account-specific information (balance, plan, status, billing date). Don't use it for general product questions."` |
| `"description": "the id"` (parameter) | `"description": "The customer's 8-digit account number, no dashes or spaces. Ask the user if they don't provide it."` |

A vague description is the #1 cause of "the model has the right tool but doesn't call it" failures. When you find yourself debugging why the model isn't picking a tool that obviously matches the user's request, the first thing to check is whether the description tells the model — in plain language — when to use it and what makes it the right choice over sibling tools.

**Tool count matters too.** LLM tool selection accuracy degrades noticeably past ~7-8 simultaneously-active tools per call. If you have many tools, partition them across steps using `Step.setFunctions()` so only the relevant subset is active at any moment. See `contexts_guide.md` for the per-step whitelist mechanism.

### 1. Local Webhook Functions (Standard)

These are the traditional SWAIG functions that are handled locally by your agent:

```java
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

agent.defineTool(
        "get_weather",
        "Get the current weather for a location",
        Map.of(
                "type", "object",
                "properties", Map.of(
                        "location", Map.of(
                                "type", "string",
                                "description", "The city or location to get weather for"
                        )
                ),
                "required", List.of("location")
        ),
        (args, rawData) -> {
            // Extract the location parameter
            String location = (String) args.getOrDefault("location", "Unknown location");

            // Here you would typically call a weather API.
            // For this example, we return mock data.
            String weatherData = "It's sunny and 72F in " + location + ".";

            // Return a FunctionResult
            return new FunctionResult(weatherData);
        });
```

The handler is a `(args, rawData) -> FunctionResult` lambda (the `ToolHandler` functional interface). Tools are secure by default; to make one public, register it as a `ToolDefinition` and call `.setSecure(false)` — see [SWAIG Function Security](#swaig-function-security).

### 2. External Webhook Functions

External webhook functions allow you to delegate function execution to external services instead of handling them locally. This is useful when you want to:
- Use existing web services or APIs directly
- Distribute function processing across multiple servers
- Integrate with third-party systems that provide their own endpoints

To create an external webhook function, build a `ToolDefinition` and attach the external URL via `setExtraFields` with the `web_hook_url` key, then register it:

```java
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;

import java.util.List;
import java.util.Map;

var externalWeather = new ToolDefinition(
        "get_weather_external",
        "Get weather from external service",
        Map.of(
                "type", "object",
                "properties", Map.of(
                        "location", Map.of(
                                "type", "string",
                                "description", "The city or location to get weather for"
                        )
                ),
                "required", List.of("location")
        ),
        // This handler is never invoked locally when web_hook_url is set;
        // the external service receives the function call instead.
        (args, rawData) -> new FunctionResult("This should not be reached for external webhooks"))
        .setExtraFields(Map.of("web_hook_url", "https://your-service.com/weather-endpoint"));

agent.defineTool(externalWeather);
```

#### How External Webhooks Work

When you specify a `web_hook_url`:

1. **Function Registration**: The function is registered with your agent as usual
2. **SWML Generation**: The generated SWML includes the external webhook URL instead of your local endpoint
3. **SignalWire Processing**: When the AI calls the function, SignalWire makes an HTTP POST request directly to your external URL
4. **Payload Format**: The external service receives a JSON payload with the function call data:

```json
{
    "function": "get_weather_external",
    "argument": {
        "parsed": [{"location": "New York"}],
        "raw": "{\"location\": \"New York\"}"
    },
    "call_id": "abc123-def456-ghi789",
    "call": { /* call information */ },
    "vars": { /* call variables */ }
}
```

5. **Response Handling**: Your external service should return a JSON response that SignalWire will process.

#### Mixing Local and External Functions

You can mix both types of functions in the same agent:

```java
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;

import java.util.Map;

agent = AgentBase.builder()
        .name("hybrid-agent")
        .route("/hybrid")
        .build();

// Local function - handled by this agent
agent.defineTool("get_help", "Get help information",
        Map.of("type", "object", "properties", Map.of()),
        (args, rawData) -> new FunctionResult("I can help you with weather and news!"));

// External function - handled by external service
agent.defineTool(new ToolDefinition("get_weather", "Get current weather",
        Map.of("type", "object", "properties", Map.of(
                "location", Map.of("type", "string", "description", "City name"))),
        (args, rawData) -> new FunctionResult(""))  // not called for external webhooks
        .setExtraFields(Map.of("web_hook_url", "https://weather-service.com/api/weather")));

// Another external function - different service
agent.defineTool(new ToolDefinition("get_news", "Get latest news",
        Map.of("type", "object", "properties", Map.of(
                "topic", Map.of("type", "string", "description", "News topic"))),
        (args, rawData) -> new FunctionResult(""))  // not called for external webhooks
        .setExtraFields(Map.of("web_hook_url", "https://news-service.com/api/news")));
```

#### Testing External Webhooks

You can test external webhook functions using the CLI tool:

```bash
# List all functions with their types (against a running agent)
bin/swaig-test --url http://user:pass@localhost:3000 --list-tools

# Test a local function
bin/swaig-test --url http://user:pass@localhost:3000 --exec get_help

# Test an external webhook function
bin/swaig-test --url http://user:pass@localhost:3000 --exec get_weather --param location="New York"
```

The CLI tool detects external webhook functions and makes HTTP requests to the external services, simulating what SignalWire does in production.

### 3. Explicit JSON-Schema Parameters

Java is statically typed, so there is no runtime type-hint inference like Python's. Define the parameter schema explicitly as a `Map` (JSON Schema). The handler is a `(args, rawData)` lambda that reads arguments from the `args` map:

```java
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

agent.defineTool("get_weather", "Get the weather forecast.",
        Map.of("type", "object",
                "properties", Map.of(
                        "city", Map.of("type", "string",
                                "description", "Name of the city to look up"),
                        "units", Map.of("type", "string",
                                "description", "Temperature units to use",
                                "enum", List.of("celsius", "fahrenheit"))
                ),
                "required", List.of("city")),
        (args, rawData) -> {
            String city = (String) args.get("city");
            String units = (String) args.getOrDefault("units", "celsius");
            return new FunctionResult("It's sunny in " + city + " (showing " + units + ")");
        });
```

Notes on the Java model:
- Map a JSON-Schema `"type"` to the Java value you cast the argument to: `"string"` → `String`, `"integer"` → `Number` (call `.intValue()`), `"number"` → `Number` (call `.doubleValue()`), `"boolean"` → `Boolean`, `"array"` → `List`, `"object"` → `Map`.
- Mark required parameters with a `"required"` list on the schema.
- Use `"enum"` for a fixed set of allowed string values.
- The handler always receives both `args` (the parsed arguments map) and `rawData` (the full raw request map — call id, call info, vars, global data). Read whatever you need from either.

### Function Parameters

The parameters for a SWAIG function are defined using JSON Schema, expressed as nested `Map`s:

<!-- snippet: no-compile illustrative JSON-schema shape (a bare Map literal, not a runnable statement) -->
```java
Map.of("type", "object",
       "properties", Map.of(
               "parameter_name", Map.of(
                       "type", "string",  // string, number, integer, boolean, array, object
                       "description", "Description of the parameter",
                       // Optional attributes:
                       "enum", List.of("option1", "option2"),  // For enumerated values
                       "minimum", 0,                            // For numeric types
                       "maximum", 100,                          // For numeric types
                       "pattern", "^[A-Z]+$"                    // For string validation
               )
       ),
       "required", List.of("parameter_name"))
```

### Function Results

To return results from a SWAIG function, use the `FunctionResult` class:

<!-- snippet: no-compile illustrative — tool-handler return-value forms (bare `return` statements, shown outside a method body) -->
```java
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

// Basic result with just text
return new FunctionResult("Here's the result");

// Result with a single action
return new FunctionResult("Here's the result with an action")
        .addAction("say", "I found the information you requested.");

// Result with multiple actions using addActions
return new FunctionResult("Multiple actions example")
        .addActions(List.of(
                Map.of("playback_bg", Map.of("file", "https://example.com/music.mp3")),
                Map.of("set_global_data", Map.of("key", "value"))
        ));

// Alternative way to add multiple actions sequentially
return new FunctionResult("Sequential actions example")
        .addAction("say", "I found the information you requested.")
        .addAction("playback_bg", Map.of("file", "https://example.com/music.mp3"));
```

In the examples above:
- `addAction(name, data)` adds a single action with the given name and data
- `addActions(actions)` adds multiple actions at once from a list of action objects

`FunctionResult` also offers typed helper methods for common actions — `say(text)`, `hangup()`, `hold(timeout)`, `connect(destination, isFinal, from)`, `updateGlobalData(data)`, `playBackgroundFile(filename)`, and more — each returning `this` so they chain fluently.

### Native Functions

The agent can use SignalWire's built-in functions:

```java
import java.util.List;

// Enable native functions
agent.setNativeFunctions(List.of(
        "check_time",
        "wait_seconds"
));
```

### Function Includes

You can include functions from remote sources:

```java
import java.util.List;
import java.util.Map;

// Include remote functions. addFunctionInclude(url, functions) records an
// include entry pointing at the remote URL; "functions" carries the remote
// function definitions to expose.
agent.addFunctionInclude(
        "https://api.example.com/functions",
        Map.of("functions", List.of("get_weather", "get_news")));
```

### SWAIG Function Security

The SDK implements an automated security mechanism for SWAIG functions to ensure that only authorized calls can be made to your functions. This is important because SWAIG functions often provide access to sensitive operations or data.

#### Token-Based Security

By default, all SWAIG functions are secure, which enables token-based security. To make security explicit, register the tool as a `ToolDefinition` and call `.setSecure(true)`:

```java
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;

import java.util.Map;

agent.defineTool(new ToolDefinition(
        "get_account_details",
        "Get customer account details",
        Map.of("type", "object", "properties", Map.of(
                "account_id", Map.of("type", "string"))),
        (args, rawData) -> {
            // Implementation
            return new FunctionResult("...");
        })
        .setSecure(true));  // This is the default, can be omitted
```

When a function is marked as secure:

1. The SDK automatically generates a secure token for each function when rendering the SWML document
2. The token is added to the function's URL as a query parameter: `?token=X2FiY2RlZmcuZ2V0X3RpbWUuMTcxOTMxNDI1...`
3. When the function is called, the token is validated before executing the function

These security tokens have important properties:
- **Completely stateless**: The system doesn't need to store tokens or track sessions
- **Self-contained**: Each token contains all information needed for validation
- **Function-specific**: A token for one function can't be used for another
- **Session-bound**: Tokens are tied to a specific call/session ID
- **Time-limited**: Tokens expire after a configurable duration (default: 60 minutes)
- **Cryptographically signed**: Tokens can't be tampered with or forged

This stateless design provides several benefits:
- **Server resilience**: Tokens remain valid even if the server restarts
- **No memory consumption**: No need to track sessions or store tokens in memory
- **High scalability**: Multiple servers can validate tokens without shared state
- **Load balancing**: Requests can be distributed across multiple servers freely

The token system secures both SWAIG functions and post-prompt endpoints:
- SWAIG function calls for interactive AI capabilities
- Post-prompt requests for receiving conversation summaries

You can disable token security for specific functions when appropriate:

```java
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;

import java.util.Map;

agent.defineTool(new ToolDefinition(
        "get_public_information",
        "Get public information that doesn't require security",
        Map.of("type", "object", "properties", Map.of()),
        (args, rawData) -> {
            // Implementation
            return new FunctionResult("...");
        })
        .setSecure(false));  // Disable token security for this function
```

#### Token Expiration

The default token expiration is 60 minutes (3600 seconds). Token lifetime is owned by the agent's `SessionManager`, which defaults to a 3600-second expiry (`new SessionManager(1800)` would set a 30-minute expiry). The signed token embeds the expiry, call id, function name, and a nonce.

The expiration timer resets each time a function is successfully called, so as long as there is activity at least once within the expiration period, the tokens will remain valid throughout the entire conversation.

#### Custom Token Validation

`AgentBase` exposes `validateToolToken(functionName, token, callId)`, which delegates to the `SessionManager`. Override it in a subclass of `AgentBase` to plug in your own validation logic.

## Skills System

The Skills System allows you to extend your agents with reusable capabilities via one-liner calls. Skills are modular, reusable components that can be easily added to any agent and configured with parameters.

### Quick Start

```java
import com.signalwire.sdk.agent.AgentBase;

import java.util.Map;

agent = AgentBase.builder()
        .name("skillful-agent")
        .route("/skillful")
        .build();

// Add skills with one-liners
agent.addSkill("web_search", Map.of());  // Web search capability
agent.addSkill("datetime", Map.of());    // Current date/time info
agent.addSkill("math", Map.of());        // Mathematical calculations

// Configure skills with parameters
agent.addSkill("web_search", Map.of(
        "num_results", 3,   // Get 3 search results instead of default 1
        "delay", 0.5        // Add delay between requests
));
```

### Available Built-in Skills

#### Web Search Skill (`web_search`)
Provides web search capabilities using Google Custom Search API with web scraping.

**Requirements:**
- HTTP/HTML parsing is handled by the SDK's built-in HTTP client (no extra dependencies)

**Parameters:**
- `api_key` (required): Google Custom Search API key
- `search_engine_id` (required): Google Custom Search Engine ID
- `num_results` (default: 1): Number of search results to return
- `delay` (default: 0): Delay in seconds between requests
- `tool_name` (default: "web_search"): Custom name for the search tool
- `no_results_message` (default: "I couldn't find any results for '{query}'. This might be due to a very specific query or temporary issues. Try rephrasing your search or asking about a different topic."): Custom message to return when no search results are found. Use `{query}` as a placeholder for the search query.

**Multiple Instance Support:**
The web_search skill supports multiple instances with different search engines and tool names, allowing you to search different data sources:

**Example:**
```java
import java.util.Map;

// Basic single instance
agent.addSkill("web_search", Map.of(
        "api_key", "your-google-api-key",
        "search_engine_id", "your-search-engine-id"
));
// Creates tool: web_search

// Fast single result (previous default)
agent.addSkill("web_search", Map.of(
        "api_key", "your-google-api-key",
        "search_engine_id", "your-search-engine-id",
        "num_results", 1,
        "delay", 0
));

// Multiple results with delay
agent.addSkill("web_search", Map.of(
        "api_key", "your-google-api-key",
        "search_engine_id", "your-search-engine-id",
        "num_results", 5,
        "delay", 1.0
));

// Multiple instances with different search engines
agent.addSkill("web_search", Map.of(
        "api_key", "your-google-api-key",
        "search_engine_id", "general-search-engine-id",
        "tool_name", "search_general",
        "num_results", 1
));
// Creates tool: search_general

agent.addSkill("web_search", Map.of(
        "api_key", "your-google-api-key",
        "search_engine_id", "news-search-engine-id",
        "tool_name", "search_news",
        "num_results", 3,
        "delay", 0.5
));
// Creates tool: search_news

// Custom no results message
agent.addSkill("web_search", Map.of(
        "api_key", "your-google-api-key",
        "search_engine_id", "your-search-engine-id",
        "no_results_message", "Sorry, I couldn't find information about '{query}'. Please try a different search term."
));
```

#### DateTime Skill (`datetime`)
Provides current date and time information with timezone support.

**Requirements:**
- Uses the JDK's built-in `java.time` API for timezones (no extra dependencies)

**Tools Added:**
- `get_current_time`: Get current time with optional timezone
- `get_current_date`: Get current date with optional timezone

**Example:**
```java
agent.addSkill("datetime", Map.of());
// Agent can now tell users the current time and date
```

#### Math Skill (`math`)
Provides safe mathematical expression evaluation.

**Requirements:**
- None (uses built-in JDK functionality)

**Tools Added:**
- `calculate`: Evaluate mathematical expressions safely

**Example:**
```java
agent.addSkill("math", Map.of());
// Agent can now perform calculations like "2 + 3 * 4"
```

#### DataSphere Skill (`datasphere`)
Provides knowledge search capabilities using SignalWire DataSphere, a cloud-hosted document search and retrieval-augmented generation (RAG) service.

**Requirements:**
- HTTP calls are handled by the SDK's built-in HTTP client (no extra dependencies)

**Parameters:**
- `space_name` (required): SignalWire space name
- `project_id` (required): SignalWire project ID 
- `token` (required): SignalWire authentication token
- `document_id` (required): DataSphere document ID to search
- `count` (default: 1): Number of search results to return
- `distance` (default: 3.0): Distance threshold for search matching
- `tags` (optional): List of tags to filter search results
- `language` (optional): Language code to limit search
- `pos_to_expand` (optional): List of parts of speech for synonym expansion (e.g., ["NOUN", "VERB"])
- `max_synonyms` (optional): Maximum number of synonyms to use for each word
- `tool_name` (default: "search_knowledge"): Custom name for the search tool
- `no_results_message` (default: "I couldn't find any relevant information for '{query}' in the knowledge base. Try rephrasing your question or asking about a different topic."): Custom message when no results found

**Multiple Instance Support:**
The DataSphere skill supports multiple instances with different tool names, allowing you to search multiple knowledge bases:

**Example:**
```java
import java.util.List;
import java.util.Map;

// Basic single instance
agent.addSkill("datasphere", Map.of(
        "space_name", "my-space",
        "project_id", "my-project",
        "token", "my-token",
        "document_id", "general-knowledge"
));
// Creates tool: search_knowledge

// Multiple instances for different knowledge bases
agent.addSkill("datasphere", Map.of(
        "space_name", "my-space",
        "project_id", "my-project",
        "token", "my-token",
        "document_id", "product-docs",
        "tool_name", "search_products",
        "tags", List.of("Products", "Features"),
        "count", 3
));
// Creates tool: search_products

agent.addSkill("datasphere", Map.of(
        "space_name", "my-space",
        "project_id", "my-project",
        "token", "my-token",
        "document_id", "support-kb",
        "tool_name", "search_support",
        "no_results_message", "I couldn't find support information about '{query}'. Try contacting our support team.",
        "distance", 5.0
));
// Creates tool: search_support
```

#### Native Vector Search Skill (`native_vector_search`)
Provides document search by querying a **remote** vector-search server over HTTP. The skill issues a `POST` to the configured `remote_url` with the query and returns the matched results. (The Java port supports remote mode only; local `.swsearch` index files are not built or read by this SDK.)

**Parameters:**
- `remote_url` (required): URL of the remote search server endpoint
- `index_name` (optional): Index name to query on the remote server
- `tool_name` (default: "search_knowledge"): Custom name for the search tool
- `description` (default: "Search the local knowledge base for information"): Tool description
- `count` (default: 3): Number of search results to return
- `hints` (optional): Additional speech hints to register for the tool

**Multiple Instance Support:**
The native vector search skill supports multiple instances with different remote endpoints and tool names.

**Example:**
```java
import java.util.Map;

// Remote mode connecting to a search server
agent.addSkill("native_vector_search", Map.of(
        "tool_name", "search_knowledge",
        "description", "Search the knowledge base",
        "remote_url", "http://localhost:8001/search",
        "index_name", "concepts",
        "count", 3
));
// Creates tool: search_knowledge
```

### Skill Management

```java
import java.util.List;

// Check what skills are loaded
List<String> loadedSkills = agent.listSkills();
System.out.println("Loaded skills: " + String.join(", ", loadedSkills));

// Check if a specific skill is loaded
if (agent.hasSkill("web_search")) {
    System.out.println("Web search is available");
}

// Remove a skill (if needed)
agent.removeSkill("math");
```

### Advanced Skill Configuration with swaig_fields

Skills support a special `swaig_fields` parameter that allows you to customize how SWAIG functions are registered. When you pass `swaig_fields` to a skill, they are automatically merged into all tool definitions created by that skill through the `SkillBase.define_tool()` wrapper method.

```java
import java.util.List;
import java.util.Map;

// Add a skill with swaig_fields to customize SWAIG function properties
agent.addSkill("math", Map.of(
        "precision", 2,  // Regular skill parameter
        "swaig_fields", Map.of(  // Special fields merged into SWAIG function automatically
                "secure", false,  // Override default security requirement
                "fillers", Map.of(
                        "en-US", List.of("Let me calculate that...", "Computing the result..."),
                        "es-ES", List.of("Déjame calcular eso...", "Calculando el resultado...")
                )
        )
));

// Add web search with custom security and fillers
agent.addSkill("web_search", Map.of(
        "num_results", 3,
        "delay", 0.5,
        "swaig_fields", Map.of(
                "secure", true,  // Require authentication
                "fillers", Map.of(
                        "en-US", List.of("Searching the web...", "Looking that up...", "Finding information...")
                )
        )
));
```

The `swaig_fields` can include any field supported by a tool definition:
- `secure`: Boolean indicating if the function requires authentication
- `fillers`: Map of language codes to lists of filler phrases
- Any other fields supported by the SWAIG function system

**Implementation Note**: The `SkillBase` class provides a `defineTool()` wrapper method that automatically injects `swaig_fields` into all tool definitions. Skills should use `this.defineTool()` (the `SkillBase` wrapper) rather than the agent's `defineTool()` directly to get automatic `swaig_fields` support without manual handling.

### Error Handling

The skills system provides detailed error messages for common issues:

```java
import java.util.Map;

try {
    agent.addSkill("web_search", Map.of());
} catch (IllegalArgumentException e) {
    System.out.println("Failed to load skill: " + e.getMessage());
    // Output: "Failed to load skill 'web_search': Missing required environment variables: [GOOGLE_SEARCH_API_KEY]"
}
```

### Creating Custom Skills

You can create your own skills by implementing the `SkillBase` interface:

```java
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;

import java.util.List;
import java.util.Map;

/** A custom skill for weather information. */
public class WeatherSkill implements SkillBase {

    private String defaultUnits = "fahrenheit";

    @Override
    public String getName() {
        return "weather";
    }

    @Override
    public String getDescription() {
        return "Get weather information for locations";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getRequiredEnvVars() {
        return List.of("WEATHER_API_KEY");
    }

    /** Setup the skill -- validate dependencies and read configuration. */
    @Override
    public boolean setup(Map<String, Object> params) {
        // Read configuration parameters
        this.defaultUnits = (String) params.getOrDefault("units", "fahrenheit");
        return true;
    }

    /** Register tools with the agent. */
    @Override
    public List<ToolDefinition> registerTools() {
        // defineTool() (the SkillBase helper) merges any configured swaig_fields.
        return List.of(defineTool(
                "get_weather",
                "Get current weather for a location",
                Map.of("type", "object",
                        "properties", Map.of(
                                "location", Map.of("type", "string",
                                        "description", "City or location name"),
                                "units", Map.of("type", "string",
                                        "description", "Temperature units (fahrenheit or celsius)",
                                        "enum", List.of("fahrenheit", "celsius"))
                        ),
                        "required", List.of("location")),
                this::getWeatherHandler));
    }

    /** Handle weather requests. */
    private FunctionResult getWeatherHandler(Map<String, Object> args, Map<String, Object> rawData) {
        String location = (String) args.getOrDefault("location", "");
        // String units = (String) args.getOrDefault("units", defaultUnits);

        if (location.isEmpty()) {
            return new FunctionResult("Please provide a location");
        }

        // Your weather API integration here
        String weatherData = "Weather for " + location + ": 72F and sunny";
        return new FunctionResult(weatherData);
    }

    /** Return speech recognition hints. */
    @Override
    public List<String> getHints() {
        return List.of("weather", "temperature", "forecast", "conditions");
    }

    /** Return prompt sections to add to the agent. */
    @Override
    public List<Map<String, Object>> getPromptSections() {
        return List.of(Map.of(
                "title", "Weather Information",
                "body", "You can provide current weather information for any location.",
                "bullets", List.of(
                        "Use get_weather tool when users ask about weather",
                        "Always specify the location clearly",
                        "Include temperature and conditions in your response"
                )
        ));
    }
}
```

**Using the custom skill:**
```java
import java.util.Map;

// Register your skill type with the SkillRegistry, then add it by name:
agent.addSkill("weather", Map.of(
        "units", "celsius"
));
```

### Skills with Dynamic Configuration

Skills work with dynamic configuration:

```java
import com.signalwire.sdk.agent.AgentBase;

import java.util.Map;

agent = AgentBase.builder()
        .name("dynamic-skill-agent")
        .build();

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    // Add different skills based on request parameters
    String tier = queryParams.getOrDefault("tier", "basic");

    // Basic skills for all users
    configAgent.addSkill("datetime", Map.of());
    configAgent.addSkill("math", Map.of());

    // Premium skills for premium users
    if ("premium".equals(tier)) {
        configAgent.addSkill("web_search", Map.of(
                "num_results", 5,
                "delay", 0.5
        ));
    } else if ("basic".equals(tier)) {
        configAgent.addSkill("web_search", Map.of(
                "num_results", 1,
                "delay", 0
        ));
    }
});
```

### Best Practices

1. **Choose appropriate parameters**: Configure skills for your use case
   ```java
   // For speed (customer service)
   agent.addSkill("web_search", Map.of("num_results", 1, "delay", 0));

   // For research (detailed analysis)
   agent.addSkill("web_search", Map.of("num_results", 5, "delay", 1.0));
   ```

2. **Handle missing dependencies gracefully**:
   ```java
   try {
       agent.addSkill("web_search", Map.of());
   } catch (IllegalArgumentException e) {
       System.out.println("Web search unavailable: " + e.getMessage());
       // Continue without web search capability
   }
   ```

3. **Document your custom skills**: Include clear descriptions and parameter documentation

4. **Test skills in isolation**: Create simple test scripts to verify skill functionality

For more detailed information about the skills system architecture and advanced customization, see the [Skills System guide](skills_system.md).

## Multilingual Support

Agents can support multiple languages:

```java
import java.util.List;

// Add English language.
// Overload: addLanguage(name, code, voice, speechFillers, functionFillers, engine, model)
agent.addLanguage(
        "English",
        "en-US",
        "en-US-Neural2-F",
        List.of("Let me think...", "One moment please..."),
        List.of("I'm looking that up...", "Let me check that..."),
        null,   // engine
        null    // model
);

// Add Spanish language (speech fillers only; function fillers null)
agent.addLanguage(
        "Spanish",
        "es",
        "rime.spore:multilingual",
        List.of("Un momento por favor...", "Estoy pensando..."),
        null,   // functionFillers
        null,   // engine
        null    // model
);
```

### Voice Formats

There are different ways to specify voices:

```java
// Simple format (name, code, voice)
agent.addLanguage("English", "en-US", "en-US-Neural2-F");

// Explicit parameters with engine and model
agent.addLanguage(
        "British English",
        "en-GB",
        "spore",
        null,          // speechFillers
        null,          // functionFillers
        "rime",        // engine
        "multilingual" // model
);

// Combined string format ("engine.voice:model" is parsed apart)
agent.addLanguage("Spanish", "es", "rime.spore:multilingual");
```

## Agent Configuration

### Adding Hints

Hints help the AI understand certain terms better:

```java
import java.util.List;

// Simple hints (list of words)
agent.addHints(List.of("SignalWire", "SWML", "SWAIG"));

// Pattern hint with replacement
agent.addPatternHint(
        "AI Agent",     // hint
        "AI\\s+Agent",  // pattern
        "A.I. Agent",   // replace
        true            // ignoreCase
);
```

### Adding Pronunciation Rules

Pronunciation rules help the AI speak certain terms correctly:

```java
// Add pronunciation rule: addPronunciation(replace, with, ignoreCase)
agent.addPronunciation("API", "A P I", false);
agent.addPronunciation("SIP", "sip", true);
```

### Setting AI Parameters

Configure various AI behavior parameters:

```java
import java.util.Map;

// Set AI parameters
agent.setParams(Map.of(
        "wait_for_user", false,
        "end_of_speech_timeout", 1000,
        "ai_volume", 5,
        "languages_enabled", true,
        "local_tz", "America/Los_Angeles"
));
```

### Setting Global Data

Provide global data for the AI to reference:

```java
import java.util.List;
import java.util.Map;

// Set global data
agent.setGlobalData(Map.of(
        "company_name", "SignalWire",
        "product", "AI Agent SDK",
        "supported_features", List.of(
                "Voice AI",
                "Telephone integration",
                "SWAIG functions"
        )
));
```

### Customizing LLM Parameters

The SDK provides methods to fine-tune the Language Model parameters for both the main prompt and post-prompt, giving you precise control over the AI's behavior:

```java
import java.util.Map;

// Set LLM parameters for the main prompt.
// These parameters are passed to the server which validates them based on the model.
agent.setPromptLlmParams(Map.of(
        "temperature", 0.7,        // Controls randomness
        "top_p", 0.9,              // Nucleus sampling threshold
        "barge_confidence", 0.6,   // ASR confidence to interrupt
        "presence_penalty", 0.0,   // Penalizes token repetition
        "frequency_penalty", 0.0   // Penalizes frequent word usage
));

// Set different parameters for the post-prompt
agent.setPostPromptLlmParams(Map.of(
        "temperature", 0.3,        // Lower temperature for consistent summaries
        "top_p", 0.95              // Slightly wider token selection
));
```

**Common Use Cases:**

- **Customer Service**: Low temperature (0.2-0.4) for consistent, professional responses
- **Creative Tasks**: Higher temperature (0.7-0.9) for varied, creative outputs
- **Technical Support**: Very low temperature (0.1-0.3) with high confidence for accuracy
- **General Assistant**: Medium temperature (0.5-0.7) for balanced interaction

For detailed information about each parameter and advanced tuning strategies, see [LLM Parameters Guide](llm_parameters.md).

## Dynamic Agent Configuration

Dynamic agent configuration allows you to configure agents per-request based on parameters from the HTTP request (query parameters, body data, headers). This enables patterns like multi-tenant applications, A/B testing, personalization, and localization.

### Overview

There are two main approaches to agent configuration:

#### Static Configuration (Traditional)
```java
import com.signalwire.sdk.agent.AgentBase;

import java.util.Map;

agent = AgentBase.builder()
        .name("static-agent")
        .build();

// Configuration happens once at startup
agent.addLanguage("English", "en-US", "rime.spore:mistv2");
agent.setParams(Map.of("end_of_speech_timeout", 500));
agent.promptAddSection("Role", "You are a customer service agent.");
agent.setGlobalData(Map.of("service_level", "standard"));
```

**Pros**: Simple, fast, predictable
**Cons**: Same behavior for all users, requires separate agents for different configurations

#### Dynamic Configuration (New)
```java
import com.signalwire.sdk.agent.AgentBase;

import java.util.Map;

agent = AgentBase.builder()
        .name("dynamic-agent")
        .build();

// No static configuration - set up dynamic callback instead
agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    // Configuration happens fresh for each request
    String tier = queryParams.getOrDefault("tier", "standard");

    if ("premium".equals(tier)) {
        configAgent.addLanguage("English", "en-US", "rime.spore:mistv2");
        configAgent.setParams(Map.of("end_of_speech_timeout", 300));  // Faster
        configAgent.promptAddSection("Role", "You are a premium customer service agent.");
        configAgent.setGlobalData(Map.of("service_level", "premium"));
    } else {
        configAgent.addLanguage("English", "en-US", "rime.spore:mistv2");
        configAgent.setParams(Map.of("end_of_speech_timeout", 500));  // Standard
        configAgent.promptAddSection("Role", "You are a customer service agent.");
        configAgent.setGlobalData(Map.of("service_level", "standard"));
    }
});
```

**Pros**: Highly flexible, single agent serves multiple configurations, enables advanced use cases
**Cons**: Slightly more complex, configuration overhead per request

### Setting Up Dynamic Configuration

Use the `setDynamicConfigCallback()` method to register a callback that will be called for each request. The callback is a `DynamicConfigCallback` functional interface — a lambda receiving `(queryParams, bodyParams, headers, configAgent)`:

```java
import com.signalwire.sdk.agent.AgentBase;

agent = AgentBase.builder()
        .name("my-agent")
        .route("/agent")
        .build();

// Register the dynamic configuration callback.
// Called for every request to configure the agent:
//   queryParams  (Map<String,String>):        query string parameters from the URL
//   bodyParams   (Map<String,Object>):        parsed JSON body from POST requests
//   headers      (Map<String,String>):        HTTP headers from the request
//   configAgent  (AgentBase):                 the agent instance to configure
agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    // Your dynamic configuration logic here
});
```

The callback receives four parameters:
- **queryParams**: Map of URL query parameters
- **bodyParams**: Map of parsed JSON body (empty for GET requests)
- **headers**: Map of HTTP headers
- **configAgent**: The agent instance to configure dynamically

### Dynamic Configuration Methods

The `agent` parameter in your callback is the actual agent instance, allowing you to use all the same configuration methods you would use during initialization:

#### Language Configuration
```java
// Add languages with voice configuration
agent.addLanguage("English", "en-US", "rime.spore:mistv2");
agent.addLanguage("Spanish", "es-ES", "rime.spore:mistv2");
```

#### Prompt Building
```java
import java.util.List;

// Add prompt sections
agent.promptAddSection("Role", "You are a helpful assistant.");
agent.promptAddSection("Guidelines", "", List.of(
        "Be professional and courteous",
        "Provide accurate information",
        "Ask clarifying questions when needed"
));

// Set raw prompt text
agent.setPromptText("You are a specialized AI assistant...");

// Set post-prompt for summary
agent.setPostPrompt("Summarize the key points of this conversation.");
```

#### AI Parameters
```java
import java.util.Map;

// Configure AI behavior
agent.setParams(Map.of(
        "end_of_speech_timeout", 300,
        "attention_timeout", 20000,
        "background_file_volume", -30
));
```

#### Global Data
```java
import java.util.List;
import java.util.Map;

// Set data available to the AI
agent.setGlobalData(Map.of(
        "customer_tier", "premium",
        "features_enabled", List.of("advanced_support", "priority_queue"),
        "session_info", Map.of("start_time", "2024-01-01T00:00:00Z")
));

// Update existing global data
agent.updateGlobalData(Map.of("additional_info", "value"));
```

#### Speech Recognition Hints
```java
import java.util.List;

// Add hints for better speech recognition
agent.addHints(List.of("SignalWire", "SWML", "API", "technical"));
agent.addPronunciation("API", "A P I", false);
```

#### Function Configuration
```java
import java.util.List;
import java.util.Map;

// Set native functions
agent.setNativeFunctions(List.of("transfer", "hangup"));

// Add function includes
agent.addFunctionInclude(
        "https://api.example.com/functions",
        Map.of("functions", List.of("get_account_info", "update_profile")));
```

### Request Data Access

Your callback function receives detailed information about the incoming request:

#### Query Parameters
```java
import java.util.Map;

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    // Extract query parameters
    String tier = queryParams.getOrDefault("tier", "standard");
    String language = queryParams.getOrDefault("language", "en");
    String customerId = queryParams.get("customer_id");
    boolean debug = "true".equalsIgnoreCase(queryParams.getOrDefault("debug", ""));

    // Use parameters for configuration
    if ("premium".equals(tier)) {
        configAgent.setParams(Map.of("end_of_speech_timeout", 300));
    }

    if (customerId != null) {
        configAgent.setGlobalData(Map.of("customer_id", customerId));
    }
});

// Request: GET /agent?tier=premium&language=es&customer_id=12345&debug=true
```

#### POST Body Parameters
```java
import java.util.Map;

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    // Extract from POST body
    @SuppressWarnings("unchecked")
    Map<String, Object> userProfile =
            (Map<String, Object>) bodyParams.getOrDefault("user_profile", Map.of());
    @SuppressWarnings("unchecked")
    Map<String, Object> preferences =
            (Map<String, Object>) bodyParams.getOrDefault("preferences", Map.of());

    // Configure based on profile
    if ("es".equals(userProfile.get("language"))) {
        configAgent.addLanguage("Spanish", "es-ES", "rime.spore:mistv2");
    }

    if ("fast".equals(preferences.get("voice_speed"))) {
        configAgent.setParams(Map.of("end_of_speech_timeout", 200));
    }
});

// Request: POST /agent with JSON body:
// {
//   "user_profile": {"language": "es", "region": "mx"},
//   "preferences": {"voice_speed": "fast", "tone": "formal"}
// }
```

#### HTTP Headers
```java
import java.util.Map;

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    // Extract headers (each header maps to a List<String> of values)
    String userAgent = headers.getOrDefault("user-agent", List.of("")).get(0);
    String authToken = headers.getOrDefault("authorization", List.of("")).get(0);
    String locale = headers.getOrDefault("accept-language", List.of("en-US")).get(0);

    // Configure based on headers
    if (userAgent.toLowerCase().contains("mobile")) {
        configAgent.setParams(Map.of("end_of_speech_timeout", 400));  // Longer for mobile
    }

    if (locale.startsWith("es")) {
        configAgent.addLanguage("Spanish", "es-ES", "rime.spore:mistv2");
    }
});
```

### Configuration Examples

#### Simple Multi-Tenant Configuration
```java
import java.util.Map;

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    String tenant = queryParams.getOrDefault("tenant", "default");

    // Tenant-specific configuration
    if ("healthcare".equals(tenant)) {
        configAgent.addLanguage("English", "en-US", "rime.spore:mistv2");
        configAgent.promptAddSection("Compliance",
                "Follow HIPAA guidelines and maintain patient confidentiality.");
        configAgent.setGlobalData(Map.of(
                "industry", "healthcare",
                "compliance_level", "hipaa"
        ));
    } else if ("finance".equals(tenant)) {
        configAgent.addLanguage("English", "en-US", "rime.spore:mistv2");
        configAgent.promptAddSection("Compliance",
                "Follow financial regulations and protect sensitive data.");
        configAgent.setGlobalData(Map.of(
                "industry", "finance",
                "compliance_level", "pci"
        ));
    }
});
```

#### Language and Localization
```java
import java.util.Map;

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    String language = queryParams.getOrDefault("language", "en");
    String region = queryParams.getOrDefault("region", "us");

    // Configure language and voice
    if ("es".equals(language)) {
        if ("mx".equals(region)) {
            configAgent.addLanguage("Spanish (Mexico)", "es-MX", "rime.spore:mistv2");
        } else {
            configAgent.addLanguage("Spanish", "es-ES", "rime.spore:mistv2");
        }
        configAgent.promptAddSection("Language", "Respond in Spanish.");
    } else if ("fr".equals(language)) {
        configAgent.addLanguage("French", "fr-FR", "rime.alois");
        configAgent.promptAddSection("Language", "Respond in French.");
    } else {
        configAgent.addLanguage("English", "en-US", "rime.spore:mistv2");
    }

    // Regional customization
    String currency = "us".equals(region) ? "USD" : "eu".equals(region) ? "EUR" : "MXN";
    configAgent.setGlobalData(Map.of(
            "language", language,
            "region", region,
            "currency", currency
    ));
});
```

#### A/B Testing Configuration
```java
import java.util.List;
import java.util.Map;

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    // Determine test group (could be from query param, user ID hash, etc.)
    String testGroup = queryParams.getOrDefault("test_group", "A");

    if ("A".equals(testGroup)) {
        // Control group - standard configuration
        configAgent.setParams(Map.of("end_of_speech_timeout", 500));
        configAgent.promptAddSection("Style", "Use a standard conversational approach.");
        configAgent.setGlobalData(Map.of("test_group", "A", "features", List.of("basic")));
    } else {
        // Test group B - experimental features
        configAgent.setParams(Map.of("end_of_speech_timeout", 300));
        configAgent.promptAddSection("Style",
                "Use an enhanced, more interactive conversational approach.");
        configAgent.setGlobalData(Map.of("test_group", "B", "features", List.of("basic", "enhanced")));
    }
});
```

#### Customer Tier-Based Configuration
```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    String customerId = queryParams.get("customer_id");
    String tier = queryParams.getOrDefault("tier", "standard");

    // Base configuration
    configAgent.addLanguage("English", "en-US", "rime.spore:mistv2");

    // Tier-specific configuration
    List<String> features;
    if ("enterprise".equals(tier)) {
        configAgent.setParams(Map.of(
                "end_of_speech_timeout", 200,  // Fastest response
                "attention_timeout", 30000     // Longest attention span
        ));
        configAgent.promptAddSection("Service Level",
                "You provide white-glove enterprise support with priority handling.");
        features = List.of("all_features", "dedicated_support", "custom_integration");
    } else if ("premium".equals(tier)) {
        configAgent.setParams(Map.of(
                "end_of_speech_timeout", 300,
                "attention_timeout", 20000
        ));
        configAgent.promptAddSection("Service Level",
                "You provide premium support with enhanced features.");
        features = List.of("premium_features", "priority_support");
    } else {
        configAgent.setParams(Map.of(
                "end_of_speech_timeout", 500,
                "attention_timeout", 15000
        ));
        configAgent.promptAddSection("Service Level",
                "You provide standard customer support.");
        features = List.of("basic_features");
    }

    // Set global data
    Map<String, Object> globalData = new LinkedHashMap<>();
    globalData.put("tier", tier);
    globalData.put("features", features);
    if (customerId != null) {
        globalData.put("customer_id", customerId);
    }
    configAgent.setGlobalData(globalData);
});
```

### Use Cases

#### Multi-Tenant SaaS Applications
Perfect for SaaS platforms where each customer needs different agent behavior:

```text
# Different tenants get different capabilities
# /agent?tenant=acme&industry=healthcare
# /agent?tenant=globex&industry=finance
```

Benefits:
- Single agent deployment serves all customers
- Tenant-specific branding and behavior
- Industry-specific compliance and terminology
- Custom feature sets per subscription level

#### A/B Testing and Experimentation
Test different agent configurations with real users:

```text
# Split traffic between different configurations
# /agent?test_group=A  (control)
# /agent?test_group=B  (experimental)
```

Benefits:
- Compare agent performance metrics
- Test new features with subset of users
- Gradual rollout of improvements
- Data-driven optimization

#### Personalization and User Preferences
Adapt agent behavior to individual user preferences:

```text
# Personalized based on user profile
# /agent?user_id=123&voice_speed=fast&formality=casual
```

Benefits:
- Improved user experience
- Accessibility support (voice speed, etc.)
- Cultural and linguistic adaptation
- Learning from user interactions

#### Geographic and Cultural Localization
Adapt to different regions and cultures:

```text
# Location-based configuration
# /agent?country=mx&language=es&timezone=America/Mexico_City
```

Benefits:
- Local language and dialect support
- Cultural appropriateness
- Regional business practices
- Time zone aware responses

### Migration Guide

#### Converting Static Agents to Dynamic

**Step 1: Move Configuration to Callback**

Before (Static):
```java
import com.signalwire.sdk.agent.AgentBase;

import java.util.Map;

agent = AgentBase.builder()
        .name("my-agent")
        .build();

// Static configuration
agent.addLanguage("English", "en-US", "rime.spore:mistv2");
agent.setParams(Map.of("end_of_speech_timeout", 500));
agent.promptAddSection("Role", "You are a helpful assistant.");
agent.setGlobalData(Map.of("version", "1.0"));
```

After (Dynamic):
```java
import com.signalwire.sdk.agent.AgentBase;

import java.util.Map;

agent = AgentBase.builder()
        .name("my-agent")
        .build();

// Set up dynamic configuration
agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    // Same configuration, but now dynamic
    configAgent.addLanguage("English", "en-US", "rime.spore:mistv2");
    configAgent.setParams(Map.of("end_of_speech_timeout", 500));
    configAgent.promptAddSection("Role", "You are a helpful assistant.");
    configAgent.setGlobalData(Map.of("version", "1.0"));
});
```

**Step 2: Add Parameter-Based Logic**

```java
import java.util.Map;

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    // Start with base configuration
    configAgent.addLanguage("English", "en-US", "rime.spore:mistv2");
    configAgent.promptAddSection("Role", "You are a helpful assistant.");

    // Add parameter-based customization
    int timeout = Integer.parseInt(queryParams.getOrDefault("timeout", "500"));
    configAgent.setParams(Map.of("end_of_speech_timeout", timeout));

    String version = queryParams.getOrDefault("version", "1.0");
    configAgent.setGlobalData(Map.of("version", version));
});
```

**Step 3: Test Both Approaches**

You can support both static and dynamic patterns during migration:

```java
import com.signalwire.sdk.agent.AgentBase;

agent = AgentBase.builder()
        .name("my-agent")
        .build();

boolean useDynamic = false;

if (useDynamic) {
    agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
        // New dynamic configuration
        // ... dynamic config logic
    });
} else {
    // Keep static configuration for backward compatibility
    agent.addLanguage("English", "en-US", "rime.spore:mistv2");
    // ... rest of static config
}
```

### Best Practices

#### Performance Considerations

1. **Keep Callbacks Lightweight**
<!-- snippet: no-compile illustrative — references an application-defined TIER_CONFIGS map -->
```java
import java.util.Map;

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    // Good: Simple parameter extraction and configuration
    String tier = queryParams.getOrDefault("tier", "standard");
    configAgent.setParams(TIER_CONFIGS.get(tier));

    // Avoid: Heavy computation or external API calls
    // Map<String,Object> customerData = expensiveApiCall(customerId);  // Don't do this
});
```

2. **Cache Configuration Data**
```java
import java.util.Map;

// Pre-compute configuration templates once
Map<String, Map<String, Object>> tierConfigs = Map.of(
        "basic", Map.of("end_of_speech_timeout", 500),
        "premium", Map.of("end_of_speech_timeout", 300),
        "enterprise", Map.of("end_of_speech_timeout", 200)
);

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    String tier = queryParams.getOrDefault("tier", "basic");
    configAgent.setParams(tierConfigs.get(tier));
});
```

3. **Use Default Values**
```java
import java.util.List;

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    // Always provide defaults
    String language = queryParams.getOrDefault("language", "en");
    String tier = queryParams.getOrDefault("tier", "standard");

    // Handle invalid values gracefully
    if (!List.of("en", "es", "fr").contains(language)) {
        language = "en";
    }
});
```

#### Security Considerations

1. **Validate Input Parameters**
```java
import java.util.List;

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    // Validate and sanitize inputs
    String tier = queryParams.getOrDefault("tier", "standard");
    if (!List.of("basic", "premium", "enterprise").contains(tier)) {
        tier = "basic";  // Safe default
    }

    // Validate numeric parameters
    int timeout;
    try {
        timeout = Integer.parseInt(queryParams.getOrDefault("timeout", "500"));
        timeout = Math.max(100, Math.min(timeout, 2000));  // Clamp to reasonable range
    } catch (NumberFormatException e) {
        timeout = 500;  // Safe default
    }
});
```

2. **Protect Sensitive Configuration**
<!-- snippet: no-compile illustrative — references application helpers isValidCustomer/getCustomerTier -->
```java
import java.util.Map;

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    // Don't expose internal configuration via parameters
    // Bad: configAgent.setGlobalData(Map.of("api_key", queryParams.get("api_key")));

    // Good: Use internal mapping for call-related data only
    String customerId = queryParams.get("customer_id");
    if (customerId != null && isValidCustomer(customerId)) {
        // Store call-related customer info, NOT sensitive credentials
        configAgent.setGlobalData(Map.of(
                "customer_id", customerId,
                "customer_tier", getCustomerTier(customerId),
                "account_type", "premium"
        ));
    }
});
```

3. **Rate Limiting for Complex Configurations**
<!-- snippet: no-compile illustrative — references an application-defined `database` handle -->
```java
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Cache expensive lookups (bounded, thread-safe)
Map<String, Map<String, Object>> customerConfigCache = new ConcurrentHashMap<>();

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    String customerId = queryParams.get("customer_id");
    if (customerId != null) {
        Map<String, Object> config = customerConfigCache.computeIfAbsent(
                customerId, id -> database.getCustomerSettings(id));
        configAgent.setGlobalData(config);
    }
});
```

#### Error Handling

1. **Graceful Degradation**
<!-- snippet: no-compile illustrative — references application helpers applyCustomConfig/applyDefaultConfig -->
```java
agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    try {
        // Try custom configuration
        applyCustomConfig(queryParams, configAgent);
    } catch (Exception e) {
        // Log error but don't fail the request
        System.err.println("config_error: " + e.getMessage());

        // Fall back to default configuration
        applyDefaultConfig(configAgent);
    }
});
```

2. **Configuration Validation**
```java
import java.util.Map;

agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
    // Validate required parameters
    if (queryParams.get("tenant") == null) {
        configAgent.setGlobalData(Map.of("error", "Missing tenant parameter"));
        return;
    }

    // Validate configuration makes sense
    String language = queryParams.getOrDefault("language", "en");
    String region = queryParams.getOrDefault("region", "us");

    if ("es".equals(language) && "us".equals(region)) {
        // Adjust for Spanish speakers in US
        configAgent.addLanguage("Spanish (US)", "es-US", "rime.spore:mistv2");
    }
});
```

Dynamic agent configuration enables sophisticated, multi-tenant AI applications while maintaining the familiar AgentBase API. Start with simple parameter-based configuration and gradually add more complex logic as your use cases evolve.

## Advanced Features

### Debug Events

The debug events system provides real-time visibility into what the AI module is doing during a call. When enabled, the module POSTs structured JSON events to your agent throughout the call lifecycle — session start/end, barge interruptions, LLM errors, step changes, and more.

#### Basic Setup

```java
import com.signalwire.sdk.agent.AgentBase;

agent = AgentBase.builder()
        .name("my_agent")
        .build();
agent.enableDebugEvents();  // That's it — events are auto-logged
agent.run();
```

With just `enableDebugEvents()`, every debug event is logged through the agent's structured logger. No other configuration is needed — the SDK automatically:
- Registers a `/debug_events` endpoint on the agent
- Sets `debug_webhook_url` and `debug_webhook_level` in the SWML params
- Logs each incoming event with its type and payload

#### Custom Event Handler

To act on specific events (alerting, metrics, custom logging), register a handler:

```java
import com.signalwire.sdk.agent.AgentBase;

agent = AgentBase.builder()
        .name("my_agent")
        .build();
agent.enableDebugEvents();

// The callback receives the full event map (event type under "type",
// plus call_id and event-specific fields).
agent.onDebugEvent(event -> {
    String eventType = (String) event.get("type");
    String callId = (String) event.get("call_id");

    if ("barge".equals(eventType)) {
        System.out.println("[" + callId + "] Caller interrupted after "
                + event.get("barge_elapsed_ms") + "ms");
    } else if ("llm_error".equals(eventType)) {
        System.out.println("[" + callId + "] LLM error: " + event.get("event"));
        // alertOpsTeam(event);  // application-defined incident hook
    } else if ("session_end".equals(eventType)) {
        double durationMs = ((Number) event.getOrDefault("duration_ms", 0)).doubleValue();
        System.out.printf("[%s] Call ended after %.1fs — reason: %s%n",
                callId, durationMs / 1000, event.get("reason"));
    }
});

agent.run();
```

The handler is called for every event in addition to the default structured logging.

#### Verbosity Levels

- **Level 1** (default): High-level events — session start/end, barge, errors, step changes, hold, filler, gather flow, action processing
- **Level 2+**: Adds high-volume events — every LLM request/response, conversation history additions

```java
agent.enableDebugEvents();  // Enable debug events (level is set on the AI params)
```

For the complete list of event types and their payloads, see the [API Reference](api_reference.md#debug-events).

### Session Lifecycle Hooks

SignalWire provides special SWAIG functions that are automatically called at specific points during a voice session's lifecycle. These hooks enable you to perform initialization tasks when a call starts and cleanup tasks when a call ends.

#### Overview

Session lifecycle hooks are special SWAIG functions that SignalWire calls automatically:
- `startup_hook`: Called immediately when a new voice session begins
- `hangup_hook`: Called when a voice session ends (regardless of how it ended)

These hooks are particularly useful for:
- Initializing session state or resources
- Loading user preferences or history
- Logging session start/end events
- Cleaning up temporary resources
- Saving session data for analytics

#### Implementation

To implement lifecycle hooks, define them as regular SWAIG functions with these specific names:

```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.time.Instant;
import java.util.Map;

agent = AgentBase.builder()
        .name("my-agent")
        .build();

// startup_hook: called when the voice session starts.
agent.defineTool("startup_hook", "Called when the voice session starts",
        Map.of("type", "object", "properties", Map.of()),
        (args, rawData) -> {
            // Extract session information from the raw request
            String callId = (String) rawData.get("call_id");
            Object fromNumber = rawData.get("from_number");
            Object toNumber = rawData.get("to_number");

            // Initialize session state carried in global data (survives the call).
            System.out.println("Session started: " + callId + " from " + fromNumber);

            // Persist session start details via global data on the result.
            return new FunctionResult("Session initialized successfully")
                    .updateGlobalData(Map.of(
                            "session_start", Instant.now().toString(),
                            "from", fromNumber,
                            "to", toNumber,
                            "interaction_count", 0
                    ));
        });

// hangup_hook: called when the voice session ends.
agent.defineTool("hangup_hook", "Called when the voice session ends",
        Map.of("type", "object", "properties", Map.of()),
        (args, rawData) -> {
            String callId = (String) rawData.get("call_id");

            // Retrieve session state from the request's global_data
            @SuppressWarnings("unchecked")
            Map<String, Object> state = (Map<String, Object>) rawData.get("global_data");

            if (state != null && state.get("session_start") != null) {
                // Calculate session duration
                Instant startTime = Instant.parse((String) state.get("session_start"));
                long durationSeconds = java.time.Duration.between(startTime, Instant.now()).getSeconds();

                // Log session metrics
                System.out.println("Session ended: " + callId);
                System.out.println("Duration: " + durationSeconds + " seconds");
                System.out.println("Interactions: " + state.getOrDefault("interaction_count", 0));
            }

            return new FunctionResult("Session cleanup completed");
        });
```

#### Common Use Cases

##### 1. User Preference Loading
```java
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.Map;

agent.defineTool("startup_hook", "Called when the voice session starts",
        Map.of("type", "object", "properties", Map.of()),
        (args, rawData) -> {
            String callerId = (String) rawData.get("from_number");

            // Load user preferences from a database (application-defined helper)
            Map<String, Object> preferences = Map.of();  // loadUserPreferences(callerId);

            // Store in global data for quick access during the call
            return new FunctionResult("User preferences loaded")
                    .updateGlobalData(Map.of(
                            "user_preferences", preferences,
                            "language", preferences.getOrDefault("language", "en-US"),
                            "previous_orders", preferences.getOrDefault("recent_orders", java.util.List.of())
                    ));
        });
```

##### 2. Analytics and Logging
```java
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

agent.defineTool("hangup_hook", "Called when the voice session ends",
        Map.of("type", "object", "properties", Map.of()),
        (args, rawData) -> {
            String callId = (String) rawData.get("call_id");
            @SuppressWarnings("unchecked")
            Map<String, Object> state = (Map<String, Object>) rawData.get("global_data");
            if (state == null) {
                state = Map.of();
            }

            // Assemble analytics data
            Map<String, Object> analyticsData = Map.of(
                    "call_id", callId,
                    "duration", state.getOrDefault("duration", 0),
                    "functions_called", state.getOrDefault("functions_called", List.of()),
                    "outcome", state.getOrDefault("outcome", "unknown")
            );

            // Post to analytics service (application-defined helper)
            // sendToAnalytics(analyticsData);

            return new FunctionResult("Analytics data sent: " + analyticsData.size() + " fields");
        });
```

#### Important Notes

1. **Function Names**: The hooks must be named exactly `startup_hook` and `hangup_hook` for SignalWire to call them
2. **Error Handling**: Always implement proper error handling in hooks - failures shouldn't crash the voice session
3. **Timing**: `startup_hook` is called before the AI starts speaking to the caller
4. **Session Data**: Any data you need to persist across the session should be stored in external storage (Redis, database, etc.)
5. **Return Values**: Both hooks must return a `FunctionResult` object

### SIP Routing

SIP routing allows your agents to receive voice calls via SIP addresses. The SDK supports both individual agent-level routing and centralized server-level routing.

#### Individual Agent SIP Routing

Enable SIP routing on a single agent:

```java
// Enable SIP routing with automatic username mapping based on agent name
agent.enableSipRouting();

// Register additional SIP usernames for this agent
agent.registerSipUsername("support_agent");
agent.registerSipUsername("help_desk");
```

When SIP routing is enabled, the agent automatically registers SIP usernames based on:
- The agent's name (e.g., `support@domain`)
- The agent's route path (e.g., `/support` becomes `support@domain`)
- Common variations (e.g., removing vowels for shorter dialing)

#### Server-Level SIP Routing (Multi-Agent)

For multi-agent setups, centralized routing is more efficient:

<!-- snippet: no-compile illustrative — references example agent instances (registrationAgent/supportAgent) constructed elsewhere -->
```java
import com.signalwire.sdk.server.AgentServer;

// Create an AgentServer
var server = new AgentServer("0.0.0.0", 3000);

// Register multiple agents
server.register(registrationAgent);  // Route: /register
server.register(supportAgent);       // Route: /support

// Set up central SIP routing
server.setupSipRouting("/sip", true);

// Register additional SIP username mappings
server.registerSipUsername("signup", "/register");    // signup@domain -> registration agent
server.registerSipUsername("help", "/support");       // help@domain -> support agent
```

With server-level routing:
- Each agent is reachable via its name (when auto-map is enabled)
- Additional SIP usernames can be mapped to specific agent routes
- All SIP routing is handled at a single endpoint (`/sip` by default)

#### How SIP Routing Works

1. A SIP call comes in with a username (e.g., `support@yourdomain`)
2. The SDK extracts the username part (`support`)
3. The system checks if this username is registered:
   - In individual routing: The current agent checks its own username list
   - In server routing: The server checks its central mapping table
4. If a match is found, the call is routed to the appropriate agent

### Custom Routing

You can dynamically handle requests to different paths using routing callbacks:

```java
import java.util.List;
import java.util.Map;

// Register routing callbacks for specific paths.
// A callback is (body, headers) -> route-or-null: return a URL to redirect to,
// or null to process the request normally via onSwmlRequest.
agent.registerRoutingCallback((body, headers) -> {
    // Extract any relevant data
    Object customerId = body.get("customer_id");

    // You can redirect to another agent/service if needed
    if (customerId instanceof String s && s.startsWith("vip-")) {
        return "/vip-handler/" + s;
    }

    // Return null to process the request with onSwmlRequest
    return null;
}, "/customer");
```

To customize the SWML document based on the route, override `onSwmlRequest(requestData, callbackPath)` in a subclass of `AgentBase`:

```java
import com.signalwire.sdk.agent.AgentBase;

import java.util.List;
import java.util.Map;

public class MyRoutedAgent extends AgentBase {

    // Subclasses call the protected AgentBase(name, route, host, port, authUser, authPassword)
    // constructor with explicit fields (see BedrockAgent for a full example).
    public MyRoutedAgent(String name, String route) {
        super(name, route, "0.0.0.0", 3000, null, null);
    }

    @Override
    public Map<String, Object> onSwmlRequest(Map<String, Object> requestData, String callbackPath) {
        if ("/customer".equals(callbackPath)) {
            // Serve customer-specific content
            return Map.of("sections", Map.of("main", List.of(
                    Map.of("answer", Map.of()),
                    Map.of("play", Map.of("url", "say:Welcome to customer service!"))
            )));
        }
        // Other path handling...
        return null;
    }
}
```

### Customizing SWML Requests

You can modify the SWML document based on request data by overriding the `onSwmlRequest` method in a subclass:

<!-- snippet: no-compile illustrative — a single overridden method (@Override onSwmlRequest), shown outside its enclosing class -->
```java
import java.util.List;
import java.util.Map;

@Override
public Map<String, Object> onSwmlRequest(Map<String, Object> requestData, String callbackPath) {
    // requestData: the request data (body for POST or query params for GET)
    // callbackPath: the path that triggered the routing callback
    // Returns: a modifications map to apply to the document, or null.

    if (requestData != null && "vip".equals(requestData.get("caller_type"))) {
        // Return modifications to change the AI behavior based on caller type
        return Map.of("sections", Map.of("main", List.of(
                // Modify the AI verb parameters
                Map.of("ai", Map.of("params", Map.of(
                        "wait_for_user", false,
                        "end_of_speech_timeout", 500  // More responsive
                )))
        )));
    }

    // You can also use the callbackPath to serve different content based on the route
    if ("/customer".equals(callbackPath)) {
        return Map.of("sections", Map.of("main", List.of(
                Map.of("answer", Map.of()),
                Map.of("play", Map.of("url", "say:Welcome to our customer service line."))
        )));
    }

    // Return null to use the default document
    return null;
}
```

### Conversation Summary Handling

Process conversation summaries:

```java
// Register a summary callback: (summary, rawPayload) -> void.
// summary is the summary map (may be empty if none was produced);
// rawPayload is the complete raw POST data from the request.
agent.onSummary((summary, rawPayload) -> {
    if (summary != null && !summary.isEmpty()) {
        // Log the summary
        System.out.println("conversation_summary: " + summary);

        // Save the summary to a database, send notifications, etc.
        // ...
    }
});
```

### Custom Webhook URLs

You can override the default webhook URLs for SWAIG functions and post-prompt delivery:

```java
// In your agent initialization or setup code:

// Override the webhook URL for all SWAIG functions
agent.setWebHookUrl("https://external-service.example.com/handle-swaig");

// Override the post-prompt delivery URL
agent.setPostPromptUrl("https://analytics.example.com/conversation-summaries");

// These methods allow you to:
// 1. Send function calls to external services instead of handling them locally
// 2. Send conversation summaries to analytics services or other systems
// 3. Use special URLs with pre-configured authentication
```

### External Input Checking

The SDK provides a check-for-input endpoint that allows agents to check for new input from external systems:

<!-- snippet: no-compile illustrative — a standalone helper method definition (checkForNewInput), not a runnable statement block -->
```java
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;

// Example client code that checks for new input.
// Returns new messages if any, or null.
static List<Object> checkForNewInput(String agentUrl, String conversationId,
                                     String user, String pass) throws Exception {
    Gson gson = new Gson();
    String basic = Base64.getEncoder()
            .encodeToString((user + ":" + pass).getBytes());

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(agentUrl + "/check_for_input"))
            .header("Authorization", "Basic " + basic)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                    gson.toJson(Map.of("conversation_id", conversationId))))
            .build();

    HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == 200) {
        Map<String, Object> data = gson.fromJson(
                response.body(), new TypeToken<Map<String, Object>>() {}.getType());
        if (Boolean.TRUE.equals(data.get("new_input"))) {
            @SuppressWarnings("unchecked")
            List<Object> messages = (List<Object>) data.getOrDefault("messages", List.of());
            return messages;
        }
    }
    return null;
}
```

By default, the `/check_for_input` endpoint returns an empty response (`{"new_input": false, "messages": []}`) after enforcing basic authentication. It is served automatically by the agent's built-in HTTP server. A successful response has this shape:

```json
{
    "status": "success",
    "conversation_id": "conv-123",
    "new_input": true,
    "messages": [ /* new messages, if any */ ]
}
```

To supply your own messages (for example, from a database or an external API), front the agent with a small handler on the `/check_for_input` route that performs the lookup and returns the JSON above. This endpoint is useful for implementing asynchronous conversations where users might send messages through different channels that need to be incorporated into the agent conversation.

## Prefab Agents

Prefab agents are pre-configured agent implementations designed for specific use cases. They provide ready-to-use functionality with customization options, saving development time and ensuring consistent patterns.

### Built-in Prefabs

The SDK includes several built-in prefab agents:

#### InfoGathererAgent

Collects structured information from users:

```java
import com.signalwire.sdk.prefabs.InfoGathererAgent;

import java.util.List;

// InfoGathererAgent(name, questions[, route, port]).
// Build each question with the InfoGathererAgent.question(keyName, questionText) factory.
var prefab = new InfoGathererAgent(
        "info-gatherer",
        List.of(
                InfoGathererAgent.question("full_name", "What is your full name?"),
                InfoGathererAgent.question("email", "What is your email address?"),
                InfoGathererAgent.question("reason", "How can I help you today?")
        )
);

prefab.run();
```

#### FAQBotAgent

Answers questions based on a knowledge base:

```java
import com.signalwire.sdk.prefabs.FAQBotAgent;

import java.util.List;

// FAQBotAgent(name, faqs[, route, port]).
// Each FAQ is built with FAQBotAgent.faq(question, answer, keywords).
var prefab = new FAQBotAgent(
        "knowledge-base",
        List.of(
                FAQBotAgent.faq(
                        "What is SignalWire?",
                        "SignalWire is a communications platform with APIs for voice, video, and messaging.",
                        List.of("signalwire", "platform", "api")),
                FAQBotAgent.faq(
                        "How do I create an AI Agent?",
                        "Use the SignalWire AI Agent SDK to build and deploy conversational AI agents.",
                        List.of("agent", "sdk", "create"))
        )
);

prefab.run();
```

#### ConciergeAgent

Acts as a venue concierge providing amenity information and availability:

```java
import com.signalwire.sdk.prefabs.ConciergeAgent;

import java.util.List;

// ConciergeAgent(name, venueName, amenities[, route, port]).
// Each amenity is built with ConciergeAgent.amenity(name, description, hours, location, price).
var prefab = new ConciergeAgent(
        "concierge",
        "Oceanview Resort",
        List.of(
                ConciergeAgent.amenity("Infinity Pool",
                        "Heated infinity pool overlooking the ocean.",
                        "7:00 AM - 10:00 PM", "Main Level", null),
                ConciergeAgent.amenity("Spa",
                        "Full-service luxury spa with massages and treatments.",
                        "9:00 AM - 8:00 PM", "Lower Level", "$150+")
        )
);

prefab.run();
```

#### SurveyAgent

Conducts structured surveys with different question types:

```java
import com.signalwire.sdk.prefabs.SurveyAgent;

import java.util.List;

// SurveyAgent(name, questions[, completionMessage, route, port]).
// Build questions with the typed factory methods: ratingQuestion, multipleChoiceQuestion,
// yesNoQuestion, openEndedQuestion.
var prefab = new SurveyAgent(
        "satisfaction-survey",
        List.of(
                SurveyAgent.ratingQuestion(
                        "How satisfied are you with our product?", 1, 5),
                SurveyAgent.openEndedQuestion(
                        "Do you have any specific feedback about how we can improve?")
        )
);

prefab.run();
```

#### ReceptionistAgent

Handles call routing and department transfers:

```java
import com.signalwire.sdk.prefabs.ReceptionistAgent;

import java.util.LinkedHashMap;
import java.util.Map;

// ReceptionistAgent(name, greeting, departments[, route, port]).
// departments maps a display name to a config built with ReceptionistAgent.phoneDepartment(description, number).
Map<String, Map<String, Object>> departments = new LinkedHashMap<>();
departments.put("Sales",
        ReceptionistAgent.phoneDepartment("For product inquiries and pricing", "+15551235555"));
departments.put("Support",
        ReceptionistAgent.phoneDepartment("For technical assistance", "+15551236666"));
departments.put("Billing",
        ReceptionistAgent.phoneDepartment("For payment and invoice questions", "+15551237777"));

var prefab = new ReceptionistAgent(
        "acme-receptionist",
        "Thank you for calling ACME Corp. How may I direct your call?",
        departments
);

prefab.run();
```

### Creating Your Own Prefabs

You can create your own prefab agents by wrapping an `AgentBase` and applying configuration in the constructor. The SDK's own prefabs use this composition pattern: hold an `AgentBase`, configure it, and expose `run()` (and `getAgent()`) for consumers. Custom prefabs can live in your project or be packaged as reusable libraries.

#### Basic Prefab Structure

A well-designed prefab should:

1. Wrap an `AgentBase` (or another prefab)
2. Take configuration parameters in the constructor
3. Apply configuration to set up the agent
4. Provide appropriate default values
5. Include domain-specific tools

Example of a custom support agent prefab:

```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CustomerSupportAgent {

    private final AgentBase agent;

    public CustomerSupportAgent(String name, String route, String productName,
                                String escalationPath) {
        // Build the underlying agent
        this.agent = AgentBase.builder()
                .name(name)
                .route(route)
                .build();

        // Configure prompt
        agent.promptAddSection("Personality",
                "I am a customer support agent for " + productName + ".");
        agent.promptAddSection("Goal",
                "Help customers solve their problems effectively.");

        // Standard instructions (conditional on configuration)
        List<String> instructions = new ArrayList<>(List.of(
                "Be professional but friendly.",
                "Verify the customer's identity before sharing account details."));
        if (escalationPath != null) {
            instructions.add("For complex issues, offer to escalate to " + escalationPath + ".");
        }
        agent.promptAddSection("Instructions", "", instructions);

        // Register default tools
        registerDefaultTools();
    }

    private void registerDefaultTools() {
        agent.defineTool("escalate_issue",
                "Escalate a customer issue to a human agent",
                Map.of("type", "object", "properties", Map.of(
                        "issue_summary", Map.of("type", "string",
                                "description", "Brief summary of the issue"),
                        "customer_email", Map.of("type", "string",
                                "description", "Customer's email address")
                )),
                (args, rawData) -> new FunctionResult("Issue escalated successfully."));

        agent.defineTool("send_support_email",
                "Send a follow-up email to the customer",
                Map.of("type", "object", "properties", Map.of(
                        "customer_email", Map.of("type", "string"),
                        "issue_summary", Map.of("type", "string"),
                        "resolution_steps", Map.of("type", "string")
                )),
                (args, rawData) -> new FunctionResult("Follow-up email sent successfully."));
    }

    /** Expose the underlying agent for further customization. */
    public AgentBase getAgent() {
        return agent;
    }

    /** Start the agent's HTTP server. */
    public void run() throws Exception {
        agent.run();
    }
}
```

#### Using the Custom Prefab

<!-- snippet: no-compile references CustomerSupportAgent, the custom prefab class defined in the preceding block (cross-block reference) -->
```java
// Create an instance of the custom prefab
var supportAgent = new CustomerSupportAgent(
        "voice-support",
        "/voice-support",
        "SignalWire Voice API",
        "tier 2 support"
);

// Start the agent
supportAgent.run();
```

#### Customizing Existing Prefabs

You can also start from a built-in prefab and customize its underlying agent via `getAgent()`:

```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.prefabs.InfoGathererAgent;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

var gatherer = new InfoGathererAgent(
        "enhanced-gatherer",
        List.of(
                InfoGathererAgent.question("full_name", "What is your full name?"),
                InfoGathererAgent.question("email", "What is your email address?")
        )
);

// Reach into the underlying agent to add customizations
agent = gatherer.getAgent();

// Add an additional instruction
agent.promptAddSection("Instructions", "", List.of(
        "Verify all information carefully."
));

// Add an additional custom tool
agent.defineTool("check_customer", "Check customer status in database",
        Map.of("type", "object", "properties", Map.of(
                "email", Map.of("type", "string"))),
        (args, rawData) -> new FunctionResult("Customer status: Active"));

gatherer.run();
```

### Best Practices for Prefab Design

1. **Clear Documentation**: Document the purpose, parameters, and extension points
2. **Sensible Defaults**: Provide working defaults that make sense for the use case
3. **Error Handling**: Implement robust error handling with helpful messages
4. **Modular Design**: Keep prefabs focused on a specific use case
5. **Consistent Interface**: Maintain consistent patterns across related prefabs
6. **Extension Points**: Provide clear ways for others to extend your prefab
7. **Configuration Options**: Make all key behaviors configurable

### Making Prefabs Distributable

To create distributable prefabs that can be used across multiple projects:

1. **Package Structure**: Create a proper Java library (its own Gradle/Maven module)
2. **Documentation**: Include clear usage examples
3. **Configuration**: Support both code and file-based configuration
4. **Testing**: Include tests for your prefab
5. **Publishing**: Publish to Maven Central or share via GitHub

Example project structure:

```
my-prefab-agents/
├── README.md
├── build.gradle
└── src/
    ├── main/java/com/example/prefabs/
    │   ├── SupportAgent.java
    │   ├── RetailAgent.java
    │   └── util/
    │       └── KnowledgeBase.java
    └── test/java/com/example/prefabs/
        └── SupportAgentTest.java
```

## API Reference

### Builder Parameters

Agents are constructed via `AgentBase.builder()...build()`:

- `.name(String)`: Agent name/identifier (default: "agent")
- `.route(String)`: HTTP route path (default: "/")
- `.host(String)`: Host to bind to (default: "0.0.0.0")
- `.port(int)`: Port to bind to (default: 3000)
- `.authUser(String)` / `.authPassword(String)`: Optional basic-auth credentials (auto-generated if unset)
- `.autoAnswer(boolean)`: Auto-answer calls (default: true)
- `.maxDuration(int)`: Maximum call duration in seconds (default: 3600)
- `.recordCall(boolean)`: Record calls (default: false)
- `.recordFormat(String)` / `.recordStereo(boolean)`: Recording format and stereo flag

The Prompt Object Model (POM) is always enabled. Logging is controlled by the `SIGNALWIRE_LOG_LEVEL` / `SIGNALWIRE_LOG_MODE` environment variables.

### Prompt Methods

- `promptAddSection(String title, String body)` and `promptAddSection(String title, String body, List<String> bullets)`
- `promptAddSubsection(String parentTitle, String title, String body)`
- `promptAddToSection(String title, List<String> bullets)`
- `setPromptText(String)`
- `setPostPrompt(String)`

### SWAIG Methods

- `defineTool(String name, String description, Map<String,Object> parameters, ToolHandler handler)`
- `defineTool(ToolDefinition toolDef)` — use `new ToolDefinition(...).setSecure(boolean).setExtraFields(Map)` for security and external webhooks
- `registerSwaigFunction(Map<String,Object> swaigFunc)`
- `setNativeFunctions(List<String> functionNames)`
- `addFunctionInclude(String url, Map<String,Object> functions)`

### Configuration Methods

- `addHint(String)` and `addHints(List<String>)`
- `addPatternHint(String hint, String pattern, String replace, boolean ignoreCase)`
- `addPronunciation(String replace, String with, boolean ignoreCase)`
- `addLanguage(String name, String code, String voice)` and the full overload `addLanguage(name, code, voice, speechFillers, functionFillers, engine, model)`
- `setParam(String key, Object value)` and `setParams(Map<String,Object>)`
- `setGlobalData(Map<String,Object>)` and `updateGlobalData(Map<String,Object>)`

### State

Per-conversation state is carried in the SWML `global_data` object rather than a separate state store. Read the current global data from a handler's `rawData` map (`rawData.get("global_data")`) and mutate it from a tool result via `FunctionResult.updateGlobalData(Map)` / `removeGlobalData(...)`.

### SIP Routing Methods

- `enableSipRouting()`: Enable SIP routing for an agent (auto-maps usernames from the agent name/route)
- `registerSipUsername(String sipUsername)`: Register a SIP username for an agent

#### AgentServer SIP Methods

- `setupSipRouting(String route, boolean autoMap)`: Set up central SIP routing for a server
- `registerSipUsername(String username, String route)`: Map a SIP username to an agent route

### Service Methods

- `run()`: Start the built-in HTTP server (blocks)
- `onSwmlRequest(Map<String,Object> requestData, String callbackPath)`: Override to customize SWML based on request data and path
- `onSummary(BiConsumer<Map,Map> callback)`: Register a post-prompt summary handler
- `onFunctionCall(String name, Map args, Map rawData)`: Process SWAIG function calls
- `registerRoutingCallback(BiFunction<Map,Map,String> callback, String path)`: Register a callback for custom path routing
- `setWebHookUrl(String url)`: Override the default `web_hook_url`
- `setPostPromptUrl(String url)`: Override the default `post_prompt_url`

### Endpoint Methods

The SDK provides several endpoints for different purposes:

- Root endpoint (`/`): Serves the main SWML document
- SWAIG endpoint (`/swaig`): Handles SWAIG function calls
- Post-prompt endpoint (`/post_prompt`): Processes conversation summaries
- Check-for-input endpoint (`/check_for_input`): Supports checking for new input from external systems
- Debug endpoint (`/debug`): Serves the SWML document with debug headers
- Debug events endpoint (`/debug_events`): Receives real-time debug events from the AI module (see [Debug Events](#debug-events))
- SIP routing endpoint (configurable, default `/sip`): Handles SIP routing requests

## Testing

The SignalWire AI Agent SDK provides testing capabilities through the `bin/swaig-test` CLI tool, which drives a running agent over HTTP (or loads an agent class in-process) to list tools, render SWML, and execute SWAIG functions.

### Local Agent Testing

Start your agent (e.g. `java SimpleAgent`), then point the CLI at its URL. The URL includes the basic-auth credentials the agent prints on startup:

```bash
# List available functions
bin/swaig-test --url http://user:pass@localhost:3000 --list-tools

# Test a SWAIG function (pass arguments as --param key=value)
bin/swaig-test --url http://user:pass@localhost:3000 --exec get_weather --param city=Austin

# Generate the SWML document
bin/swaig-test --url http://user:pass@localhost:3000 --dump-swml
```

### In-Process Class Loading

To introspect an agent or SWML service without starting an HTTP server, load its class directly. This is handy for sidecar / standalone SWAIG hosts whose tools do not appear via `--list-tools --url`:

```bash
# Load a Service subclass in-process and list its registered tools
bin/swaig-test --class examples.SwmlServiceSwaigStandalone --list-tools
```

The `--class` FQCN must extend `com.signalwire.sdk.swml.Service` and have a public no-arg constructor. No socket is bound.

### Serverless (AWS Lambda) Simulation

You can route an agent class through the serverless adapter to verify it behaves correctly when deployed to AWS Lambda. Pass the agent's fully-qualified class name and `--simulate-serverless lambda`:

```bash
# Render SWML through the Lambda adapter
bin/swaig-test MyAgent --simulate-serverless lambda --dump-swml

# Execute a SWAIG function through the Lambda adapter
bin/swaig-test MyAgent --simulate-serverless lambda --exec get_weather --param city=Miami
```

The agent class must expose a public static factory method returning an `AgentBase` — `createAgent(EnvProvider)` / `buildAgent(EnvProvider)` / `newAgent(EnvProvider)` / `getAgent(EnvProvider)` (or their no-arg equivalents). The `EnvProvider`-aware signature is recommended so the agent's build-time env reads (`SWML_BASIC_AUTH_USER`, `SWML_BASIC_AUTH_PASSWORD`, `SWML_PROXY_URL_BASE`) see the simulated values. Supported platform: `lambda`.

### Testing Best Practices

1. **List tools first**: Confirm every expected tool is registered with the right description before executing anything.
2. **Render SWML**: Use `--dump-swml` to verify the generated document (prompt, params, SWAIG defaults) matches your intent.
3. **Exercise functions**: Run `--exec` with representative `--param` values to confirm handler logic and results.
4. **Simulate Lambda**: If you deploy to AWS Lambda, verify with `--simulate-serverless lambda` so build-time env reads and webhook URLs resolve correctly.
5. **Use verbose mode**: Add `--verbose` for debugging setup and execution; `--raw` prints unformatted JSON.

For more detailed testing documentation, see the [CLI Guide](cli_guide.md).

## Examples

### Simple Question-Answering Agent

```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class SimpleAgentMain {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("simple")
                .route("/simple")
                .build();

        // Configure agent personality
        agent.promptAddSection("Personality", "You are a friendly and helpful assistant.");
        agent.promptAddSection("Goal", "Help users with basic tasks and answer questions.");
        agent.promptAddSection("Instructions", "", List.of(
                "Be concise and direct in your responses.",
                "If you don't know something, say so clearly.",
                "Use the get_time function when asked about the current time."
        ));

        agent.defineTool("get_time", "Get the current time",
                Map.of("type", "object", "properties", Map.of()),
                (toolArgs, rawData) -> {
                    String time = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    return new FunctionResult("The current time is " + time);
                });

        System.out.println("Starting agent server...");
        agent.run();
    }
}
```

### Multi-Language Customer Service Agent

```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

public class CustomerServiceAgentMain {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("customer-service")
                .route("/support")
                .build();

        // Configure agent personality
        agent.promptAddSection("Personality",
                "You are a helpful customer service representative for SignalWire.");
        agent.promptAddSection("Knowledge",
                "You can answer questions about SignalWire products and services.");
        agent.promptAddSection("Instructions", "", List.of(
                "Greet customers politely",
                "Answer questions about SignalWire products",
                "Use check_account_status when customer asks about their account",
                "Use create_support_ticket for unresolved issues"
        ));

        // Add language support
        agent.addLanguage(
                "English", "en-US", "en-US-Neural2-F",
                List.of("Let me think...", "One moment please..."),
                List.of("I'm looking that up...", "Let me check that..."),
                null, null);

        agent.addLanguage(
                "Spanish", "es", "rime.spore:multilingual",
                List.of("Un momento por favor...", "Estoy pensando..."),
                null, null, null);

        // Enable languages
        agent.setParams(Map.of("languages_enabled", true));

        // Add company information
        agent.setGlobalData(Map.of(
                "company_name", "SignalWire",
                "support_hours", "9am-5pm ET, Monday through Friday",
                "support_email", "support@signalwire.com"
        ));

        agent.defineTool("check_account_status",
                "Check the status of a customer's account",
                Map.of("type", "object", "properties", Map.of(
                        "account_id", Map.of("type", "string",
                                "description", "The customer's account ID")
                ), "required", List.of("account_id")),
                (toolArgs, rawData) -> {
                    String accountId = (String) toolArgs.get("account_id");
                    // In a real implementation, this would query a database
                    return new FunctionResult("Account " + accountId + " is in good standing.");
                });

        agent.defineTool("create_support_ticket",
                "Create a support ticket for an unresolved issue",
                Map.of("type", "object", "properties", Map.of(
                        "issue", Map.of("type", "string",
                                "description", "Brief description of the issue"),
                        "priority", Map.of("type", "string",
                                "description", "Ticket priority",
                                "enum", List.of("low", "medium", "high", "critical"))
                ), "required", List.of("issue")),
                (toolArgs, rawData) -> {
                    String issue = (String) toolArgs.getOrDefault("issue", "");
                    String priority = (String) toolArgs.getOrDefault("priority", "medium");

                    // Generate a ticket ID (in a real system, this would create a DB entry)
                    String ticketId = String.format("TICKET-%04d", Math.abs(issue.hashCode()) % 10000);

                    return new FunctionResult(
                            "Support ticket " + ticketId + " has been created with "
                                    + priority + " priority. "
                                    + "A support representative will contact you shortly.");
                });

        System.out.println("Starting customer service agent...");
        agent.run();
    }
}
```

### Dynamic Agent Configuration Examples

For working examples of dynamic agent configuration, see these files in the `examples` directory:

- **`SimpleStaticAgent.java`**: Traditional static configuration approach
- **`SimpleDynamicAgent.java`**: Same agent but using dynamic configuration
- **`SimpleDynamicEnhanced.java`**: Enhanced version that actually uses request parameters
- **`ComprehensiveDynamicAgent.java`**: Advanced multi-tier, multi-industry dynamic agent
- **`CustomPathAgent.java`**: Dynamic agent with custom routing path
- **`MultiAgentServer.java`**: Multiple specialized dynamic agents on one server

These examples demonstrate the progression from static to dynamic configuration and show real-world use cases like multi-tenant applications, A/B testing, and personalization.

For more examples, see the `examples` directory in the SignalWire AI Agent SDK repository.