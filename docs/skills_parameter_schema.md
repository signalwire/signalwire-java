# Skills Parameter Schema System

This guide explains the parameter schema system for the SignalWire AI Agents SDK skills, which enables GUI configuration tools and programmatic skill discovery.

## Overview

The parameter schema system allows skills to declare their configurable parameters with metadata including types, descriptions, default values, and security hints. This enables:

- **GUI Configuration Tools** - Automatically generate configuration forms
- **API Documentation** - Document all available parameters
- **Validation** - Type checking and constraint validation
- **Security** - Mark sensitive parameters as hidden
- **Environment Variables** - Indicate which parameters can be sourced from environment

## Using the Schema System

### Getting All Skills Schema

Enumerate the `SkillRegistry` and call `getParameterSchema()` on each registered skill to get a complete schema of all available skills:

```java
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.skills.SkillRegistry;
import java.util.LinkedHashMap;
import java.util.Map;

// Build a complete schema for all registered skills
Map<String, Map<String, Object>> allSchemas = new LinkedHashMap<>();
var registry = new SkillRegistry();
for (Map<String, Object> meta : registry.listSkills()) {
    String name = (String) meta.get("name");
    SkillBase skill = SkillRegistry.get(name);
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("name", skill.getName());
    entry.put("description", skill.getDescription());
    entry.put("version", skill.getVersion());
    entry.put("supports_multiple_instances", skill.supportsMultipleInstances());
    entry.put("required_packages", skill.getRequiredPackages());
    entry.put("required_env_vars", skill.getRequiredEnvVars());
    entry.put("parameters", skill.getParameterSchema());
    allSchemas.put(name, entry);
}
```

Each entry has this structure (shown here as JSON):

```json
{
    "web_search": {
        "name": "web_search",
        "description": "Search the web for information using Google Custom Search API",
        "version": "1.0.0",
        "supports_multiple_instances": true,
        "required_packages": ["bs4", "requests"],
        "required_env_vars": [],
        "parameters": {
            "api_key": {
                "type": "string",
                "description": "Google Custom Search API key",
                "required": true,
                "hidden": true,
                "env_var": "GOOGLE_SEARCH_API_KEY"
            },
            "search_engine_id": {
                "type": "string",
                "description": "Google Custom Search Engine ID",
                "required": true,
                "hidden": true,
                "env_var": "GOOGLE_SEARCH_ENGINE_ID"
            },
            "num_results": {
                "type": "integer",
                "description": "Default number of search results to return",
                "default": 1,
                "required": false,
                "min": 1,
                "max": 10
            }
        }
    },
    "datetime": {
        "name": "datetime",
        "description": "Get current date, time, and timezone information",
        "version": "1.0.0",
        "supports_multiple_instances": false,
        "required_packages": ["pytz"],
        "required_env_vars": [],
        "parameters": {
            "swaig_fields": {
                "type": "object",
                "description": "Additional SWAIG function metadata to merge into tool definitions",
                "default": {},
                "required": false
            }
        }
    }
}
```

### Using Schema for GUI Configuration

Here's an example of how to use the schema to generate a configuration form:

```java
import com.signalwire.sdk.skills.SkillRegistry;
import java.util.Map;

// Get the web_search skill schema
var webSearchSchema = SkillRegistry.get("web_search").getParameterSchema();

// Generate an HTML form field based on a parameter schema
static String generateFormField(String paramName, Map<String, Object> info) {
    StringBuilder field = new StringBuilder("<div class=\"form-group\">\n");
    field.append("  <label for=\"").append(paramName).append("\">")
         .append(info.get("description")).append("</label>\n");

    // Mark required fields
    String required = Boolean.TRUE.equals(info.get("required")) ? "required" : "";

    // Hide sensitive fields
    String inputType = Boolean.TRUE.equals(info.get("hidden")) ? "password" : "text";

    String type = (String) info.get("type");
    switch (type) {
        case "string" -> {
            Object def = info.getOrDefault("default", "");
            field.append("  <input type=\"").append(inputType).append("\" id=\"")
                 .append(paramName).append("\" name=\"").append(paramName).append("\" ")
                 .append("value=\"").append(def).append("\" ").append(required).append(">\n");
        }
        case "integer" -> {
            Object def = info.getOrDefault("default", 0);
            String minVal = info.containsKey("min") ? "min=\"" + info.get("min") + "\"" : "";
            String maxVal = info.containsKey("max") ? "max=\"" + info.get("max") + "\"" : "";
            field.append("  <input type=\"number\" id=\"").append(paramName)
                 .append("\" name=\"").append(paramName).append("\" ")
                 .append("value=\"").append(def).append("\" ")
                 .append(minVal).append(" ").append(maxVal).append(" ")
                 .append(required).append(">\n");
        }
        case "boolean" -> {
            boolean def = Boolean.TRUE.equals(info.getOrDefault("default", false));
            String checked = def ? "checked" : "";
            field.append("  <input type=\"checkbox\" id=\"").append(paramName)
                 .append("\" name=\"").append(paramName).append("\" ")
                 .append(checked).append(">\n");
        }
        default -> { }
    }

    // Show environment variable hint
    if (info.containsKey("env_var")) {
        field.append("  <small>Can also be set via ").append(info.get("env_var"))
             .append(" environment variable</small>\n");
    }

    field.append("</div>\n");
    return field.toString();
}

// Generate form fields for the web_search skill
System.out.println("<form>");
@SuppressWarnings("unchecked")
var params = (Map<String, Map<String, Object>>) (Map<?, ?>) webSearchSchema;
for (var e : params.entrySet()) {
    System.out.println(generateFormField(e.getKey(), e.getValue()));
}
System.out.println("</form>");
```

### Programmatic Skill Configuration

Use the schema to validate and configure skills programmatically:

```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.skills.SkillRegistry;
import java.util.Map;

var agent = AgentBase.builder().name("my-agent").build();

// Get schema to validate configuration
@SuppressWarnings("unchecked")
var webSearchSchema =
    (Map<String, Map<String, Object>>) (Map<?, ?>)
        SkillRegistry.get("web_search").getParameterSchema();

// Configure web_search skill with validation
Map<String, Object> webSearchParams = Map.of(
    "api_key", "your-api-key",
    "search_engine_id", "your-engine-id",
    "num_results", 3,
    "max_content_length", 3000);

// Validate required parameters
for (var e : webSearchSchema.entrySet()) {
    if (Boolean.TRUE.equals(e.getValue().get("required"))
            && !webSearchParams.containsKey(e.getKey())) {
        throw new IllegalArgumentException("Missing required parameter: " + e.getKey());
    }
}

// Add skill with validated parameters
agent.addSkill("web_search", webSearchParams);
```

## Parameter Schema Reference

Each parameter in the schema can have the following properties:

| Property | Type | Description |
|----------|------|-------------|
| `type` | string | Parameter type: "string", "integer", "number", "boolean", "object", "array" |
| `description` | string | Human-readable description of the parameter |
| `default` | any | Default value if not provided |
| `required` | boolean | Whether the parameter is required (default: false) |
| `hidden` | boolean | Whether to hide this field in UIs (for secrets/API keys) |
| `env_var` | string | Environment variable that can provide this value |
| `enum` | array | List of allowed values (for string types) |
| `min` | number | Minimum value (for numeric types) |
| `max` | number | Maximum value (for numeric types) |

## Implementing Parameter Schema in Skills

To add parameter schema support to a skill, override `getParameterSchema()`:

```java
package com.example.skills;

import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MyCustomSkill implements SkillBase {

    private Map<String, Object> params;

    @Override
    public String getName() {
        return "my_custom_skill";
    }

    @Override
    public String getDescription() {
        return "My custom skill";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        // Start from the base schema (swaig_fields, skip_prompt, and tool_name
        // for multi-instance skills), then add skill-specific parameters.
        Map<String, Object> schema = SkillBase.super.getParameterSchema();
        schema = new LinkedHashMap<>(schema);

        schema.put("api_endpoint", Map.of(
            "type", "string",
            "description", "API endpoint URL",
            "required", true,
            "default", "https://api.example.com"));

        schema.put("api_key", Map.of(
            "type", "string",
            "description", "API authentication key",
            "required", true,
            "hidden", true,             // Mark as sensitive
            "env_var", "MY_API_KEY"));   // Can be set via environment

        schema.put("timeout", Map.of(
            "type", "integer",
            "description", "Request timeout in seconds",
            "default", 30,
            "required", false,
            "min", 1,
            "max", 300));

        schema.put("retry_count", Map.of(
            "type", "integer",
            "description", "Number of retries on failure",
            "default", 3,
            "required", false,
            "min", 0,
            "max", 10));

        schema.put("output_format", Map.of(
            "type", "string",
            "description", "Output format for results",
            "default", "json",
            "required", false,
            "enum", List.of("json", "xml", "text")));  // Allowed values

        schema.put("enable_cache", Map.of(
            "type", "boolean",
            "description", "Enable response caching",
            "default", true,
            "required", false));

        return schema;
    }

    @Override
    public boolean setup(Map<String, Object> params) {
        // Access parameters via the passed map
        this.params = params;
        Object apiEndpoint = params.get("api_endpoint");
        Object apiKey = params.get("api_key");
        int timeout = (int) params.getOrDefault("timeout", 30);
        // ... etc
        return true;
    }

    @Override
    public List<ToolDefinition> registerTools() {
        return List.of();
    }
}
```

## Common Parameter Patterns

### API Keys and Secrets

Always mark sensitive parameters as `hidden` and provide an `env_var` option:

```java
schema.put("api_key", Map.of(
    "type", "string",
    "description", "API key for authentication",
    "required", true,
    "hidden", true,
    "env_var", "SERVICE_API_KEY"));
```

### Numeric Parameters with Constraints

Use `min` and `max` to enforce valid ranges:

```java
schema.put("port", Map.of(
    "type", "integer",
    "description", "Server port number",
    "default", 8080,
    "required", false,
    "min", 1,
    "max", 65535));
```

### Enumerated Values

Use `enum` to restrict to specific values:

```java
schema.put("log_level", Map.of(
    "type", "string",
    "description", "Logging level",
    "default", "info",
    "required", false,
    "enum", List.of("debug", "info", "warning", "error")));
```

### Optional Features

Use boolean parameters for optional features:

```java
schema.put("enable_analytics", Map.of(
    "type", "boolean",
    "description", "Enable analytics tracking",
    "default", false,
    "required", false));
```

## Base Parameters

All skills automatically inherit these base parameters from `SkillBase`:

- **`swaig_fields`** (object) - Additional SWAIG function metadata to merge into tool definitions
- **`skip_prompt`** (boolean) - If true, the skill does not inject its default prompt section into the POM
- **`tool_name`** (string) - Custom name for skill instances (only for skills where `supportsMultipleInstances()` returns `true`)

## Examples

### Simple Skill (No Parameters)

Skills like `datetime` and `math` that don't need configuration just return the base schema:

```java
@Override
public Map<String, Object> getParameterSchema() {
    // Just return the base schema
    return SkillBase.super.getParameterSchema();
}
```

### Complex Skill (Many Parameters)

Skills like `web_search` with multiple configuration options add to the base schema:

```java
@Override
public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = new LinkedHashMap<>(SkillBase.super.getParameterSchema());

    // API credentials (hidden)
    schema.put("api_key", Map.of(/* ... */));
    schema.put("api_secret", Map.of(/* ... */));

    // Configuration options
    schema.put("timeout", Map.of(/* ... */));
    schema.put("retry_count", Map.of(/* ... */));

    // Feature flags
    schema.put("enable_cache", Map.of(/* ... */));
    schema.put("debug_mode", Map.of(/* ... */));

    return schema;
}
```

## Best Practices

1. **Always provide descriptions** - Make parameters self-documenting
2. **Set sensible defaults** - Allow skills to work with minimal configuration
3. **Mark secrets as hidden** - Protect sensitive information in UIs
4. **Use appropriate types** - Enable proper validation and UI controls
5. **Document environment variables** - Show alternative configuration methods
6. **Validate in setup()** - Ensure all required parameters are present
7. **Support backward compatibility** - Handle deprecated parameters gracefully

## Future Enhancements

The parameter schema system is designed to be extensible. Future enhancements may include:

- **Conditional parameters** - Show/hide based on other parameter values
- **Complex validation** - Cross-parameter validation rules
- **Nested schemas** - Support for complex object parameters
- **Internationalization** - Localized descriptions and error messages
- **Runtime parameter updates** - Modify configuration without restart
