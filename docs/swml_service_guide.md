# SignalWire SWML Service Guide

<!-- snippet-setup -->
```java
import com.signalwire.sdk.swml.Service;
import com.signalwire.sdk.logging.Logger;
```

## Table of Contents
- [Introduction](#introduction)
- [Installation](#installation)
- [Basic Usage](#basic-usage)
- [Centralized Logging System](#centralized-logging-system)
- [SWML Document Creation](#swml-document-creation)
- [Verb Handling](#verb-handling)
- [Web Service Features](#web-service-features)
- [Custom Routing Callbacks](#custom-routing-callbacks)
- [Advanced Usage](#advanced-usage)
- [API Reference](#api-reference)
- [Examples](#examples)

## Introduction

The `Service` class (`com.signalwire.sdk.swml.Service`) provides a foundation for creating and serving SignalWire Markup Language (SWML) documents. It serves as the base class for all SignalWire services, including AI Agents (`AgentBase` extends `Service`), and handles common tasks such as:

- SWML document creation and manipulation
- Schema-driven verb validation
- Web service functionality (JDK `HttpServer`, default executor)
- Authentication
- Centralized logging

The class is designed to be used directly or extended for specific use cases, while providing a full set of capabilities out of the box.

## Installation

The `Service` class is part of the SignalWire AI Agents SDK for Java. Add the dependency to your build:

```groovy
// build.gradle
dependencies {
    implementation 'com.signalwire:signalwire-sdk:2.0.2'
}
```

```xml
<!-- Maven pom.xml -->
<dependency>
  <groupId>com.signalwire</groupId>
  <artifactId>signalwire-sdk</artifactId>
  <version>2.0.2</version>
</dependency>
```

## Basic Usage

Here's a simple example of creating an SWML service. Construct a `Service` directly, add verbs with the shortcut methods, and call `serve()`:

```java
import com.signalwire.sdk.swml.Service;
import java.util.Map;

var service = new Service("voice-service", "/voice");

// Build the SWML document
service.answer(null);
service.play(Map.of("url", "say:Hello, thank you for calling our service."));
service.hangup();

// Start the service (blocks, serving GET/POST /voice)
service.serve();
```

You can also subclass `Service` and generate the document per request by overriding `onSwmlRequest` (see [Dynamic SWML Generation](#dynamic-swml-generation)).

## Centralized Logging System

The SDK includes a centralized logging system (`com.signalwire.sdk.logging.Logger`) that provides level-controlled, environment-driven logging. It is configured automatically; you don't need to set it up in each service.

### How It Works

1. `Logger` reads its level once from the environment when first used
2. Each class obtains a logger via `Logger.getLogger(MyClass.class)`
3. Log calls below the configured level are dropped
4. Output honors `SIGNALWIRE_LOG_LEVEL` and `SIGNALWIRE_LOG_MODE`

### Using the Logger

Obtain a logger and use its level methods:

<!-- snippet: no-compile class-field declaration excerpt referencing the reader's own `MyService` class (shown as a field, not a runnable statement) -->
```java
import com.signalwire.sdk.logging.Logger;

private static final Logger log = Logger.getLogger(MyService.class);

// Basic logging
log.info("service_started");

// Logging with context (printf-style formatting)
log.debug("document_created size=%d", document.length());

// Error logging
try {
  // Some operation
} catch (Exception e) {
  log.error("operation_failed error=%s", e.getMessage());
}
```

### Log Levels

The following log levels are available (in increasing order of severity):
- `debug`: Detailed information for debugging
- `info`: General information about operation
- `warn`: Warning about potential issues
- `error`: Error information when operations fail

Levels map to the `Logger.Level` enum (`DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF`).

### Suppressing Logs

Control the level with environment variables:

```bash
# Only show warnings and above
export SIGNALWIRE_LOG_LEVEL=warn

# Suppress all output
export SIGNALWIRE_LOG_MODE=off
```

## SWML Document Creation

The `Service` class provides methods for creating and manipulating SWML documents.

### Document Structure

SWML documents have the following basic structure:

```json
{
  "version": "1.0.0",
  "sections": {
    "main": [
      { "verb1": { /* configuration */ } },
      { "verb2": { /* configuration */ } }
    ],
    "section1": [
      { "verb3": { /* configuration */ } }
    ]
  }
}
```

### Document Methods

- `resetDocument()`: Reset the document to an empty state
- `addVerb(verbName, config)`: Add a verb to the main section
- `addSection(sectionName)`: Add a new section
- `addVerbToSection(sectionName, verbName, config)`: Add a verb to a specific section
- `getDocument()`: Get the current document (`Document`)
- `renderDocument()`: Get the current document as a JSON string

### Common Verb Shortcuts

In addition to the generic `addVerb(verbName, config)`, `Service` exposes schema-driven shortcut methods for common verbs — e.g. `answer(...)`, `play(...)`, `record(...)`, `recordCall(...)`, `connect(...)`, `prompt(...)`, `sleep(...)`, `hangup(...)`.

## Verb Handling

The `Service` class validates SWML verbs against the SignalWire schema.

### Verb Validation

When adding a verb, the service validates it against the schema to ensure it has the correct structure and parameters.

```java
var service = new Service("demo-service", "/");

// This validates the configuration against the schema
service.addVerb("play", Map.of(
    "url", "say:Hello, world!",
    "volume", 5));

// This would fail validation (invalid parameter)
service.addVerb("play", Map.of(
    "invalid_param", "value"));
```

### Custom Verb Handlers

You can register custom verb handlers for specialized verb processing by extending `SWMLVerbHandler`:

<!-- snippet: no-compile illustrative unit combining a full handler class with a trailing `service.registerVerbHandler(...)` usage statement (not a single compilable form) -->
```java
import com.signalwire.sdk.swml.SWMLVerbHandler;
import java.util.List;
import java.util.Map;

class CustomPlayHandler extends SWMLVerbHandler {
  @Override
  public String getVerbName() {
    return "play";
  }

  @Override
  public ValidationResult validateConfig(Map<String, Object> config) {
    // Custom validation logic
    return new ValidationResult(true, List.of());
  }

  @Override
  public Map<String, Object> buildConfig(Map<String, Object> kwargs) {
    // Custom configuration building
    return kwargs;
  }
}

service.registerVerbHandler(new CustomPlayHandler());
```

## Web Service Features

The `Service` class includes built-in web service capabilities for serving SWML documents (JDK `com.sun.net.httpserver.HttpServer`, no external web framework).

### Endpoints

By default, a service provides the following endpoints:

- `GET /route`: Return the SWML document
- `POST /route`: Process request data and return the SWML document
- `GET /route/`: Same as above but with trailing slash
- `POST /route/`: Same as above but with trailing slash

Where `route` is the route path specified when creating the service.

### Authentication

Basic authentication is automatically set up for all endpoints. Credentials are generated if not provided (via `SecureRandom`), or can be specified through the full constructor:

```java
var service = new Service(
    "my-service", "/", "0.0.0.0", 3000, "username", "password");
```

You can also set credentials using environment variables:
- `SWML_BASIC_AUTH_USER`
- `SWML_BASIC_AUTH_PASSWORD`

Credential comparison uses `MessageDigest.isEqual()` for a timing-safe check.

### Dynamic SWML Generation

You can override the `onSwmlRequest` method to customize SWML documents based on request data. Return a map of overrides, or `null` to serve the document as built:

```java
import com.signalwire.sdk.swml.Service;
import java.util.Map;

class VipGreetingService extends Service {
  VipGreetingService() {
    super("greeting", "/greeting");
    answer(null);
    play(Map.of("url", "say:Welcome caller!"));
    hangup();
  }

  @Override
  public Map<String, Object> onSwmlRequest(
      Map<String, Object> requestData, String callbackPath) {
    if (requestData == null) {
      return null;
    }

    // Customize the document based on requestData
    resetDocument();
    answer(null);

    if ("vip".equals(requestData.get("caller_type"))) {
      addVerb("play", Map.of("url", "say:Welcome VIP caller!"));
    } else {
      addVerb("play", Map.of("url", "say:Welcome caller!"));
    }

    // Return modifications to the document,
    // or null to use the document we've built without modifications
    return null;
  }
}
```

## Custom Routing Callbacks

The `Service` class allows you to register custom routing callbacks that examine incoming requests and determine where they should be routed.

### Registering a Routing Callback

Use `registerRoutingCallback` to register a function called for requests to a specific path. The callback is a `BiFunction<Map<String,Object> body, Map<String,String> headers, String>` — return a URL string to redirect, or `null` to process normally:

```java
import java.util.Map;

var service = new Service("router-service", "/");

// Route based on a field in the request body
service.registerRoutingCallback(
    (body, headers) -> {
      if (body.containsKey("customer_id")) {
        return "/customer/" + body.get("customer_id");
      }
      // Process request normally
      return null;
    },
    "/customer"); // path this callback handles
```

### How Routing Works

1. When a request is received at the registered path, the routing callback is executed
2. The callback inspects the request and can decide whether to redirect it
3. If the callback returns a URL string, the request is redirected with HTTP 307 (temporary redirect)
4. If the callback returns `null`, the request is processed normally by `onRequest`

### Serving Different Content for Different Paths

You can use the `callbackPath` parameter passed to `onRequest` / `onSwmlRequest` to serve different content for different paths:

<!-- snippet: no-compile method-override body shown outside its enclosing Service subclass for illustration -->
```java
@Override
public Map<String, Object> onRequest(
    Map<String, Object> requestData, String callbackPath) {
  // Serve different content based on the callback path
  if ("/customer".equals(callbackPath)) {
    return Map.of("sections", Map.of("main", List.of(
        Map.of("answer", Map.of()),
        Map.of("play", Map.of("url", "say:Welcome to customer service!")))));
  } else if ("/product".equals(callbackPath)) {
    return Map.of("sections", Map.of("main", List.of(
        Map.of("answer", Map.of()),
        Map.of("play", Map.of("url", "say:Welcome to product support!")))));
  }

  // Default content
  return null;
}
```

### Example: Multi-Section Service

Here's a service that uses routing callbacks to handle different types of requests:

```java
import com.signalwire.sdk.swml.Service;
import java.util.List;
import java.util.Map;

class MultiSectionService extends Service {

  MultiSectionService() {
    super("multi-section", "/main");

    // Create the main document
    resetDocument();
    addVerb("answer", Map.of());
    addVerb("play", Map.of("url", "say:Hello from the main service!"));
    addVerb("hangup", Map.of());

    // Register customer and product routes
    registerCustomerRoute();
    registerProductRoute();
  }

  private void registerCustomerRoute() {
    registerRoutingCallback(
        (body, headers) -> {
          if (body.containsKey("customer_id")) {
            // In a real implementation you might redirect to another service.
            // Here we just log it and process normally.
            System.out.println("Processing request for customer ID: " + body.get("customer_id"));
          }
          return null;
        },
        "/customer");

    // Create the customer SWML section
    addSection("customer_section");
    addVerbToSection("customer_section", "answer", Map.of());
    addVerbToSection("customer_section", "play",
        Map.of("url", "say:Welcome to customer service!"));
    addVerbToSection("customer_section", "hangup", Map.of());
  }

  private void registerProductRoute() {
    registerRoutingCallback(
        (body, headers) -> {
          if (body.containsKey("product_id")) {
            System.out.println("Processing request for product ID: " + body.get("product_id"));
          }
          return null;
        },
        "/product");

    // Create the product SWML section
    addSection("product_section");
    addVerbToSection("product_section", "answer", Map.of());
    addVerbToSection("product_section", "play",
        Map.of("url", "say:Welcome to product support!"));
    addVerbToSection("product_section", "hangup", Map.of());
  }

  @Override
  public Map<String, Object> onRequest(
      Map<String, Object> requestData, String callbackPath) {
    // Serve different content based on the callback path
    if ("/customer".equals(callbackPath)) {
      return Map.of("sections", Map.of(
          "main", getDocument().getSectionVerbs("customer_section")));
    } else if ("/product".equals(callbackPath)) {
      return Map.of("sections", Map.of(
          "main", getDocument().getSectionVerbs("product_section")));
    }
    return null;
  }
}
```

In this example:
1. The service registers two custom route paths: `/customer` and `/product`
2. Each path has its own callback to handle routing decisions
3. `onRequest` uses the `callbackPath` to determine which content to serve
4. Different SWML sections are served for different paths

## Advanced Usage

### Hosting Multiple Services

Java uses the built-in JDK HTTP server rather than a FastAPI router. To host several agents in one process, register them by route with `AgentServer`. `AgentServer.register` accepts `AgentBase` agents (which extend `Service`); host a plain `Service` on its own by calling its own `run()`:

```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.server.AgentServer;

var voiceAgent = AgentBase.builder().name("voice").route("/voice").build();
var routerAgent = AgentBase.builder().name("router").route("/router").build();

AgentServer server = new AgentServer("0.0.0.0", 3000);
server.register(voiceAgent, "/voice");
server.register(routerAgent, "/router");
server.run();
```

### Custom Verb Schema

Verb validation is driven by the bundled SWML schema and the registered `SWMLVerbHandler`s. To customize validation for a verb, register a custom handler (see [Custom Verb Handlers](#custom-verb-handlers)) rather than swapping a schema file.

## API Reference

### Constructor Parameters

- `new Service(name)` — service name, default route `/`, host `0.0.0.0`, port from `PORT` env or 3000
- `new Service(name, route)` — as above with an explicit route
- `new Service(name, route, host, port, authUser, authPassword)` — full form; pass `null` for `authUser`/`authPassword` to auto-resolve from env or generate

### Document Methods

- `resetDocument()`
- `addVerb(verbName, config)`
- `addSection(sectionName)`
- `addVerbToSection(sectionName, verbName, config)`
- `getDocument()`
- `renderDocument()`

### Service Methods

- `serve()`: Start the service (binds the HTTP server; throws `IOException`)
- `stop()`: Stop the service
- `getBasicAuthCredentials()`: Get the basic auth credentials as a `String[2]`
- `validateBasicAuth(user, password)`: Timing-safe credential check
- `onSwmlRequest(requestData, callbackPath)`: Called when SWML is requested
- `registerRoutingCallback(callback)` / `registerRoutingCallback(callback, path)`: Register a routing callback (default path `/sip`)
- `registerVerbHandler(handler)`: Register a custom `SWMLVerbHandler`

### Verb Helper Methods

- `addVerb(verbName, config)`: Add any SWML verb with configuration
- Shortcut methods for common verbs: `answer`, `play`, `record`, `recordCall`, `connect`, `prompt`, `sleep`, `hangup`, and more

## Examples

### Basic Voicemail Service

```java
import com.signalwire.sdk.swml.Service;
import java.util.Map;

var service = new Service("voicemail", "/voicemail");

// Add answer verb
service.answer(null);

// Greeting
service.play(Map.of("url",
    "say:Hello, you've reached the voicemail service. Please leave a message after the beep."));

// Play a beep
service.play(Map.of("url", "https://example.com/beep.wav"));

// Record the message (2 minutes max)
service.record(Map.of(
    "format", "mp3",
    "stereo", false,
    "max_length", 120,
    "terminators", "#"));

// Thank the caller
service.play(Map.of("url", "say:Thank you for your message. Goodbye!"));

// Hang up
service.hangup();

service.serve();
```

### Dynamic Call Routing Service

```java
import com.signalwire.sdk.swml.Service;
import com.signalwire.sdk.logging.Logger;
import java.util.Map;

class CallRouterService extends Service {
  private static final Logger log = Logger.getLogger(CallRouterService.class);

  CallRouterService() {
    super("call-router", "/router");
  }

  @Override
  public Map<String, Object> onSwmlRequest(
      Map<String, Object> requestData, String callbackPath) {
    // If there's no request data, use default routing
    if (requestData == null) {
      log.debug("no_request_data_using_default");
      return null;
    }

    // Create a new document
    resetDocument();
    answer(null);

    // Get routing parameters
    String department =
        String.valueOf(requestData.getOrDefault("department", "")).toLowerCase();

    // Greeting
    play(Map.of("url",
        "say:Thank you for calling our " + department + " department. Please hold."));

    // Route based on department
    Map<String, String> phoneNumbers = Map.of(
        "sales", "+15551112222",
        "support", "+15553334444",
        "billing", "+15555556666");
    String toNumber = phoneNumbers.getOrDefault(department, "+15559990000");

    // Connect to the department
    connect(Map.of(
        "to", toNumber,
        "timeout", 30,
        "answer_on_bridge", true));

    // Fallback message and hangup
    play(Map.of("url",
        "say:We're sorry, but all of our agents are currently busy. Please try again later."));
    hangup();

    return null; // Use the document we've built
  }
}
```

For more examples, see the `examples/` directory in the SignalWire AI Agents SDK for Java repository (`SwmlServiceExample.java`, `DynamicSwmlService.java`, `BasicSwmlService.java`, `AutoVivifiedExample.java`).
