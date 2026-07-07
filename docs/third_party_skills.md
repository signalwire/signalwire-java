# Third-Party Skills Integration Guide

This guide explains how to create and integrate third-party skills with the SignalWire AI Agents SDK for Java. The SDK supports multiple methods for registering external skills, making it easy to extend agent capabilities without modifying the core SDK.

<!-- snippet-setup -->
```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.skills.SkillRegistry;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;

AgentBase agent = AgentBase.builder().name("skills-agent").route("/agent").build();
```

## Overview

Third-party skills can be integrated using these methods:

1. **Direct Registration** - Register a skill class or factory with `SkillRegistry`
2. **Directory Registration** - Point the registry at directories containing skill collections
3. **Environment Variables** - Configure skill paths via `SIGNALWIRE_SKILL_PATHS`

Java has no Python-entry-point mechanism; where Python auto-discovers packaged skills via `setuptools` entry points, Java registers a `Supplier<SkillBase>` factory (or a `Class<? extends SkillBase>`) with `SkillRegistry`. All registered skills are enumerated the same way as built-in skills through `SkillRegistry.listSkills()` / `getAllSkillsSchema()`.

## Creating a Third-Party Skill

Third-party skills follow the same structure as built-in skills: implement the `SkillBase` interface. Here's a minimal example:

<!-- snippet: no-compile complete example compilation unit in the reader's own `com.example.skills` package (declared for the narrative, not part of the SDK) -->
```java
package com.example.skills;

import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

/** Custom weather information skill. */
public class WeatherSkill implements SkillBase {

  private String apiKey;
  private String units = "celsius";
  private int cacheTimeout = 300;

  @Override
  public String getName() {
    return "weather";
  }

  @Override
  public String getDescription() {
    return "Get weather information for any location";
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public List<String> getRequiredPackages() {
    // Informational only. Java resolves dependencies at build time (Gradle/Maven).
    return List.of("com.google.code.gson:gson");
  }

  /** Define configuration parameters (for GUI tooling). */
  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();

    Map<String, Object> apiKeyField = new LinkedHashMap<>();
    apiKeyField.put("type", "string");
    apiKeyField.put("description", "Weather API key");
    apiKeyField.put("required", true);
    apiKeyField.put("hidden", true);
    apiKeyField.put("env_var", "WEATHER_API_KEY");
    schema.put("api_key", apiKeyField);

    Map<String, Object> unitsField = new LinkedHashMap<>();
    unitsField.put("type", "string");
    unitsField.put("description", "Temperature units");
    unitsField.put("default", "celsius");
    unitsField.put("required", false);
    unitsField.put("enum", List.of("celsius", "fahrenheit", "kelvin"));
    schema.put("units", unitsField);

    Map<String, Object> cacheField = new LinkedHashMap<>();
    cacheField.put("type", "integer");
    cacheField.put("description", "Cache timeout in seconds");
    cacheField.put("default", 300);
    cacheField.put("required", false);
    cacheField.put("min", 0);
    cacheField.put("max", 3600);
    schema.put("cache_timeout", cacheField);

    return schema;
  }

  /** Initialize the skill. Return false to abort loading. */
  @Override
  public boolean setup(Map<String, Object> params) {
    this.apiKey = (String) params.get("api_key");
    if (apiKey == null || apiKey.isEmpty()) {
      return false; // API key is required
    }
    if (params.containsKey("units")) {
      this.units = (String) params.get("units");
    }
    if (params.containsKey("cache_timeout")) {
      this.cacheTimeout = ((Number) params.get("cache_timeout")).intValue();
    }
    return true;
  }

  /** Register weather tools with the agent. */
  @Override
  public List<ToolDefinition> registerTools() {
    ToolDefinition tool =
        defineTool(
            "get_weather",
            "Get current weather for a location",
            Map.of(
                "type", "object",
                "properties",
                    Map.of(
                        "location",
                        Map.of("type", "string", "description", "City name or coordinates"))),
            (args, rawData) -> {
              String location = String.valueOf(args.getOrDefault("location", "")).trim();
              if (location.isEmpty()) {
                return new FunctionResult("Please provide a location");
              }
              // Implementation would call the weather API here. This is just an example.
              String unit = units.substring(0, 1).toUpperCase();
              return new FunctionResult(
                  "The weather in " + location + " is sunny and 22°" + unit);
            });
    return List.of(tool);
  }
}
```

`defineTool(name, description, parameters, handler)` is a helper on `SkillBase` that builds a `ToolDefinition` and merges the skill's `getExtraFields()` (swaig_fields). Skills return their tools from `registerTools()`; the `SkillManager` registers them with the agent.

## Integration Methods

### Method 1: Direct Registration

Register a skill factory or class with `SkillRegistry`, then add it to any agent by name:

<!-- snippet: no-compile references the reader's own `com.example.skills.WeatherSkill` from the example above (not an SDK type) -->
```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.skills.SkillRegistry;
import com.example.skills.WeatherSkill;
import java.util.Map;

// Register the skill globally (factory form)
SkillRegistry.register("weather", WeatherSkill::new);

// ...or register by class (name is read from getName())
new SkillRegistry().registerSkill(WeatherSkill.class);

// Now use it in any agent
var agent = AgentBase.builder().name("my-agent").route("/").port(3000).build();
agent.addSkill("weather", Map.of(
    "api_key", "your-api-key",
    "units", "fahrenheit"));
```

### Method 2: Directory Registration

Register directories containing multiple skills. A directory must exist and be a directory, or `addSkillDirectory` throws `IllegalArgumentException`:

```java
import com.signalwire.sdk.skills.SkillRegistry;

var registry = new SkillRegistry();

// Add a directory of custom skills
registry.addSkillDirectory("/opt/custom_skills");

// Directory structure should be:
// /opt/custom_skills/
//   weather/
//     Skill.java      # Contains a WeatherSkill class
//   stock_market/
//     Skill.java      # Contains a StockMarketSkill class
//   translation/
//     Skill.java      # Contains a TranslationSkill class

// External paths are reported alongside built-ins
System.out.println(registry.getExternalPaths());
```

Java resolves classes on the classpath rather than at runtime from source files, so the directory paths are recorded (and surfaced via `getExternalPaths()` / `listAllSkillSources()`) for tooling; the skill classes themselves must be on the application classpath and registered as in Method 1.

### Method 3: Programmatic Registration in Place of Python Entry Points

Python packages advertise skills via `setuptools` entry points. Java has no equivalent auto-discovery, so a distributable skill library registers its skills programmatically — typically from a small bootstrap method that callers invoke once:

<!-- snippet: no-compile complete example compilation unit in the reader's own `com.example.skills` package (declared for the narrative, not part of the SDK) -->
```java
package com.example.skills;

import com.signalwire.sdk.skills.SkillRegistry;

/** Call once at startup to register every skill this library provides. */
public final class SkillsBootstrap {
  private SkillsBootstrap() {}

  public static void registerAll() {
    SkillRegistry.register("weather", WeatherSkill::new);
    SkillRegistry.register("stock", StockMarketSkill::new);
    SkillRegistry.register("translate", TranslationSkill::new);
  }
}
```

Consumers add the library as a Gradle/Maven dependency:

```groovy
// build.gradle
dependencies {
    implementation 'com.example:my-signalwire-skills:1.0.0'
    implementation 'com.signalwire:signalwire-sdk:2.0.2'
}
```

Then bootstrap and use:

<!-- snippet: no-compile calls the reader's own `com.example.skills.SkillsBootstrap` from the example above (not an SDK type) -->
```java
com.example.skills.SkillsBootstrap.registerAll();
agent.addSkill("weather", Map.of("api_key", "..."));
```

### Method 4: Environment Variable

Set the `SIGNALWIRE_SKILL_PATHS` environment variable to seed external skill directories:

```bash
# Single directory
export SIGNALWIRE_SKILL_PATHS=/opt/my_skills

# Multiple directories (colon-separated)
export SIGNALWIRE_SKILL_PATHS=/opt/my_skills:/home/user/custom_skills
```

Read the paths and register them with the registry at startup:

```java
String paths = System.getenv("SIGNALWIRE_SKILL_PATHS");
if (paths != null && !paths.isEmpty()) {
  var registry = new SkillRegistry();
  for (String path : paths.split(":")) {
    registry.addSkillDirectory(path);
  }
}
agent.addSkill("weather", Map.of("api_key", "..."));
```

## Directory Structure

Skills organized on disk mirror the built-in layout — one directory per skill, matching `getName()`:

```
my_skills_directory/
├── weather/                 # Skill directory (matches getName())
│   ├── WeatherSkill.java    # Contains the skill class
│   └── README.md            # Optional: documentation
├── translation/
│   ├── TranslationSkill.java
│   └── resources/           # Optional: additional files
│       └── languages.json
└── stock_market/
    └── StockMarketSkill.java
```

## Skill Discovery and Schema

Registered skills are enumerated through the registry:

```java
var registry = new SkillRegistry();

// Metadata for every registered skill (name, description, version, packages, env vars)
List<Map<String, Object>> allSkills = registry.listSkills();

// Full parameter schema keyed by skill name
Map<String, Map<String, Object>> schema = registry.getAllSkillsSchema();
System.out.println(schema.get("weather"));
// Output shape:
// {
//   "name": "weather",
//   "description": "Get weather information for any location",
//   "version": "1.0.0",
//   "parameters": { ... }
// }
```

## Best Practices

### 1. Skill Naming

- Use lowercase, underscore-separated names (returned from `getName()`)
- Choose unique names to avoid conflicts with built-in skills
- Match the directory name to `getName()` for directory-based organization

### 2. Parameter Design

- Implement `getParameterSchema()` for GUI compatibility
- Mark sensitive parameters as `hidden`
- Provide sensible defaults
- Use `env_var` for parameters that can come from the environment

### 3. Error Handling

`setup` returns `false` to abort loading. Validate required params and connectivity there:

<!-- snippet: no-compile method-override body shown outside its enclosing SkillBase class for illustration -->
```java
@Override
public boolean setup(Map<String, Object> params) {
  // Validate required parameters
  this.apiKey = (String) params.get("api_key");
  if (apiKey == null || apiKey.isEmpty()) {
    return false; // API key is required
  }

  // Test connectivity
  try {
    testApiConnection();
  } catch (Exception e) {
    return false;
  }

  return true;
}
```

### 4. Documentation

Include a README.md in your skill directory:

```markdown
# Weather Skill

Provides weather information for any location.

## Configuration

- `api_key` (required): Your weather API key
- `units` (optional): Temperature units (celsius, fahrenheit, kelvin)
- `cache_timeout` (optional): Cache timeout in seconds

## Usage

    agent.addSkill("weather", Map.of(
        "api_key", "your-api-key",
        "units", "fahrenheit"));
```

## Advanced Features

### Multiple Instances

Support multiple instances of your skill by overriding `supportsMultipleInstances()` and `getInstanceKey()`:

<!-- snippet: no-compile illustrative excerpt showing only the multi-instance overrides (getDescription/setup/registerTools omitted for brevity) -->
```java
public class WeatherSkill implements SkillBase {
  private Map<String, Object> params;

  @Override
  public String getName() {
    return "weather";
  }

  @Override
  public boolean supportsMultipleInstances() {
    return true; // Enable multiple instances
  }

  @Override
  public String getInstanceKey() {
    Object service = params.getOrDefault("service", "default");
    return getName() + "_" + service;
  }
}
```

Usage:

```java
// Add multiple weather services
agent.addSkill("weather", Map.of(
    "tool_name", "openweather",
    "service", "openweathermap",
    "api_key", "key1"));

agent.addSkill("weather", Map.of(
    "tool_name", "weatherapi",
    "service", "weatherapi",
    "api_key", "key2"));
```

### Dynamic Tool Names

Customize tool names for better agent prompts. Read the name in `setup` and use it in `registerTools`:

<!-- snippet: no-compile method-override body shown outside its enclosing SkillBase class for illustration -->
```java
@Override
public List<ToolDefinition> registerTools() {
  String toolName = (String) params.getOrDefault("tool_name", "get_weather");
  String service = (String) params.getOrDefault("service", "default");

  return List.of(
      defineTool(
          toolName,
          "Get weather using " + service,
          Map.of("type", "object", "properties", Map.of()),
          this::weatherHandler));
}
```

### Skill Dependencies

Load skills that depend on other skills by checking the agent's skill manager in `setup`:

<!-- snippet: no-compile method-override body shown outside its enclosing SkillBase class for illustration -->
```java
@Override
public boolean setup(Map<String, Object> params) {
  // Check if the required skill is available
  if (!agent.getSkillManager().hasSkill("translation")) {
    return false; // This skill requires the translation skill
  }
  return true;
}
```

## Testing Third-Party Skills

Test your skills before distribution with JUnit 5:

<!-- snippet: no-compile JUnit test referencing the reader's own `com.example.skills.WeatherSkill`; JUnit is a test-scope dependency not on the doc-compile classpath -->
```java
import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.skills.SkillRegistry;
import com.example.skills.WeatherSkill;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeatherSkillTest {

  @Test
  void skillRegistration() {
    // Register and add the skill
    SkillRegistry.register("weather", WeatherSkill::new);

    var agent = AgentBase.builder().name("test-agent").build();
    agent.addSkill("weather", Map.of("api_key", "test-key"));

    assertTrue(agent.listSkills().contains("weather"));
  }

  @Test
  void parameterSchema() {
    Map<String, Object> schema = new WeatherSkill().getParameterSchema();
    assertTrue(schema.containsKey("api_key"));

    @SuppressWarnings("unchecked")
    Map<String, Object> apiKey = (Map<String, Object>) schema.get("api_key");
    assertEquals(Boolean.TRUE, apiKey.get("required"));
    assertEquals(Boolean.TRUE, apiKey.get("hidden"));
  }
}
```

## Troubleshooting

### Skill Not Found

If your skill isn't being discovered:

1. Verify the skill class is on the application classpath
2. Verify it was registered (`SkillRegistry.has("weather")` returns true)
3. Confirm `getName()` returns the exact name you pass to `addSkill`
4. Check logs for setup failures (a `setup` returning `false` aborts loading)

### Registration Errors

`registerSkill(Class)` requires a public no-arg constructor and a non-empty `getName()`; otherwise it throws `IllegalArgumentException`:

<!-- snippet: no-compile references the reader's own `com.example.skills.WeatherSkill` class (not an SDK type) -->
```java
try {
  new SkillRegistry().registerSkill(WeatherSkill.class);
} catch (IllegalArgumentException e) {
  System.err.println("Cannot register skill: " + e.getMessage());
}
```

### Environment Variables

Debug environment variable loading:

```java
System.out.println("Skill paths: "
    + System.getenv().getOrDefault("SIGNALWIRE_SKILL_PATHS", "Not set"));

var registry = new SkillRegistry();
Map<String, List<String>> sources = registry.listAllSkillSources();
System.out.println("External skills: " + sources.get("external_paths"));
```

## Example: Complete Third-Party Skill Library

Here's a complete example of a distributable skill library (a standard Gradle module):

```
my-signalwire-skills/
├── build.gradle
├── README.md
└── src/main/java/com/example/skills/
    ├── SkillsBootstrap.java
    ├── weather/
    │   ├── WeatherSkill.java
    │   └── WeatherUtils.java
    └── translation/
        └── TranslationSkill.java
```

```groovy
// build.gradle
plugins {
    id 'java-library'
    id 'maven-publish'
}

group = 'com.example'
version = '1.0.0'

dependencies {
    api 'com.signalwire:signalwire-sdk:2.0.2'
    implementation 'com.google.code.gson:gson:2.10.1'

    testImplementation platform('org.junit:junit-bom:5.10.0')
    testImplementation 'org.junit.jupiter:junit-jupiter'
}
```

Add the library as a dependency and use it:

<!-- snippet: no-compile calls the reader's own `com.example.skills.SkillsBootstrap` from the example above (not an SDK type) -->
```java
import com.signalwire.sdk.agent.AgentBase;
import java.util.Map;

// One-time: register every skill the library provides
com.example.skills.SkillsBootstrap.registerAll();

var agent = AgentBase.builder().name("my-agent").route("/").port(3000).build();
agent.addSkill("weather", Map.of("api_key", "..."));
agent.addSkill("translate", Map.of("api_key", "..."));
agent.run();
```
