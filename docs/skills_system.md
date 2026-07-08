# SignalWire Agents Skills System

The SignalWire Agents SDK includes a modular skills system that lets you add capabilities to your agents with simple one-liner calls and configurable parameters.

<!-- snippet-setup -->
```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
```

## What's New

Instead of manually implementing every agent capability, you can now:

```java
import com.signalwire.sdk.agent.AgentBase;
import java.util.Map;

// Create an agent
var agent = AgentBase.builder().name("My Assistant").build();

// Add skills with one-liners!
agent.addSkill("web_search", Map.of());   // Web search capability with default settings
agent.addSkill("datetime", Map.of());     // Current date/time info
agent.addSkill("math", Map.of());         // Mathematical calculations

// Add skills with custom parameters!
agent.addSkill("web_search", Map.of(
    "num_results", 3,   // Get 3 search results instead of default 1
    "delay", 0.5        // Add 0.5s delay between requests instead of default 0
));

// Your agent now has all these capabilities automatically
```

## Architecture

The skills system consists of:

### Core Infrastructure
- **`SkillBase`** - Interface for all skills with parameter support
- **`SkillManager`** - Handles loading/unloading and lifecycle management with parameters
- **`AgentBase.addSkill()`** - Simple method to add skills to agents with optional parameters

### Discovery & Registry
- **`SkillRegistry`** - Registry of all built-in skills, keyed by name
- **Static registration** - Skills are registered eagerly in the registry
- **Validation** - Checks dependencies and environment variables

### Built-in Skills
- **`web_search`** - Google Custom Search API integration with web scraping
- **`datetime`** - Current date/time information with timezone support
- **`math`** - Basic mathematical calculations

## Available Skills

### Web Search (`web_search`)
Search the internet and extract content from web pages.

**Requirements:**
- Environment variables: `GOOGLE_SEARCH_API_KEY`, `GOOGLE_SEARCH_ENGINE_ID`

**Parameters:**
- `num_results` (default: 1) - Number of search results to retrieve (1-10)
- `delay` (default: 0) - Delay in seconds between web requests

**Tools provided:**
- `web_search(query, num_results)` - Search and scrape web content

**Usage examples:**
```java
var agent = AgentBase.builder().name("assistant").route("/").build();

// Default: fast single result
agent.addSkill("web_search", Map.of());

// Custom: multiple results with delay
agent.addSkill("web_search", Map.of(
    "num_results", 3,
    "delay", 0.5
));

// Speed optimized: single result, no delay
agent.addSkill("web_search", Map.of(
    "num_results", 1,
    "delay", 0
));
```

### Date/Time (`datetime`)
Get current date and time information.

**Parameters:** None (no configurable parameters)

**Tools provided:**
- `get_current_time(timezone)` - Current time in any timezone
- `get_current_date(timezone)` - Current date in any timezone

### Math (`math`)
Perform mathematical calculations.

**Requirements:** None

**Parameters:** None (no configurable parameters)

**Tools provided:**
- `calculate(expression)` - Evaluate mathematical expressions safely

### Native Vector Search (`native_vector_search`)
Search documents by querying a **remote** vector-search server over HTTP. (The Java port supports remote mode only; it does not build or read local index files.)

**Parameters:**
- `remote_url` (required) - URL of the remote search server endpoint
- `index_name` (optional) - Index name to query on the remote server
- `tool_name` (default: "search_knowledge") - Custom name for the search tool
- `description` (default: "Search the local knowledge base for information") - Tool description
- `count` (default: 3) - Number of search results to return
- `hints` (optional) - Additional speech hints to register

**Tools provided:**
- `search_knowledge(query, count)` - Search the remote index and return matched results

**Usage examples:**
```java
var agent = AgentBase.builder().name("assistant").route("/").build();

// Remote mode
agent.addSkill("native_vector_search", Map.of(
    "remote_url", "http://localhost:8001/search",
    "index_name", "knowledge"
));

// Multiple instances for different remote endpoints
agent.addSkill("native_vector_search", Map.of(
    "tool_name", "search_examples",
    "remote_url", "http://localhost:8001/examples",
    "index_name", "examples"
));
```

### SWML Transfer (`swml_transfer`)
Transfer calls between agents using pattern matching.

**Requirements:** None (no additional packages or environment variables required)

**Parameters:**
- `tool_name` (default: "transfer_call") - Custom name for the transfer function
- `description` (default: "Transfer call based on pattern matching") - Tool description
- `parameter_name` (default: "transfer_type") - Name of the parameter for the transfer function
- `parameter_description` (default: "The type of transfer to perform") - Parameter description
- `transfers` (required) - Map of regex patterns to transfer configurations:
  - Pattern (key): Regex pattern to match (e.g., "/sales/i")
  - Configuration (value): Map with:
    - `url` (required): Transfer destination URL
    - `message` (optional): Pre-transfer message
    - `return_message` (optional): Post-transfer message
    - `post_process` (optional, default: true): Enable post-processing
- `default_message` (default: "Please specify a valid transfer type.") - Message when no pattern matches
- `default_post_process` (default: false) - Post-processing flag for default case
- `required_fields` (default: {}) - Map of field names to descriptions for data collection before transfer

**Tools provided:**
- `transfer_call(transfer_type, ...required_fields)` (or custom tool_name) - Transfer based on pattern matching with optional required fields

**Usage examples:**
```java
var agent = AgentBase.builder().name("assistant").route("/").build();

// Simple transfer between departments
agent.addSkill("swml_transfer", Map.of(
    "tool_name", "transfer_to_department",
    "transfers", Map.of(
        "/sales/i", Map.of(
            "url", "https://example.com/sales",
            "message", "Transferring to sales...",
            "return_message", "Sales transfer complete."
        ),
        "/support/i", Map.of(
            "url", "https://example.com/support",
            "message", "Transferring to support...",
            "return_message", "Support transfer complete."
        )
    )
));

// Multiple instances for different transfer types
agent.addSkill("swml_transfer", Map.of(
    "tool_name", "route_call",
    "parameter_name", "department",
    "transfers", Map.of(
        "/sales|billing/i", Map.of(
            "url", "https://api.company.com/sales",
            "message", "Connecting to sales team...",
            "post_process", true
        ),
        "/technical|support/i", Map.of(
            "url", "https://api.company.com/support",
            "message", "Connecting to support team...",
            "post_process", true
        )
    ),
    "default_message", "Would you like sales or support?"
));
```

## Usage Examples

### Basic Usage
```java
import com.signalwire.sdk.agent.AgentBase;
import java.util.Map;

// Create agent and add skills
var agent = AgentBase.builder().name("Assistant").route("/assistant").build();
agent.addSkill("datetime", Map.of());
agent.addSkill("math", Map.of());
agent.addSkill("web_search", Map.of());  // Uses defaults: 1 result, no delay

// Start the agent
agent.run();
```

### Skills with Custom Parameters
```java
import com.signalwire.sdk.agent.AgentBase;
import java.util.Map;

// Create agent
var agent = AgentBase.builder().name("Research Assistant").route("/research").build();

// Add web search optimized for research (more results)
agent.addSkill("web_search", Map.of(
    "num_results", 5,   // Get more comprehensive results
    "delay", 1.0        // Be respectful to websites
));

// Add other skills without parameters
agent.addSkill("datetime", Map.of());
agent.addSkill("math", Map.of());

// Start the agent
agent.run();
```

### Different Parameter Configurations
```java
var agent = AgentBase.builder().name("assistant").route("/").build();

// Speed-optimized for quick responses
agent.addSkill("web_search", Map.of(
    "num_results", 1,
    "delay", 0
));

// Comprehensive research mode
agent.addSkill("web_search", Map.of(
    "num_results", 5,
    "delay", 1.0
));

// Balanced approach
agent.addSkill("web_search", Map.of(
    "num_results", 3,
    "delay", 0.5
));
```

### Check Available Skills
```java
import com.signalwire.sdk.skills.SkillRegistry;
import java.util.Map;

// List all registered skills
var registry = new SkillRegistry();
for (Map<String, Object> skill : registry.listSkills()) {
    System.out.println("- " + skill.get("name") + ": " + skill.get("description"));
    var envVars = skill.get("required_env_vars");
    if (envVars != null && !((java.util.List<?>) envVars).isEmpty()) {
        System.out.println("  Requires: " + envVars);
    }
}
```

### Runtime Skill Management
```java
var agent = AgentBase.builder().name("Dynamic Agent").build();

// Add skills with different configurations
agent.addSkill("math", Map.of());
agent.addSkill("datetime", Map.of());
agent.addSkill("web_search", Map.of("num_results", 2, "delay", 0.3));

// Check what's loaded
System.out.println("Loaded skills: " + agent.listSkills());

// Remove a skill
agent.removeSkill("math");

// Check if specific skill is loaded
if (agent.hasSkill("datetime")) {
    System.out.println("Date/time capabilities available");
}
```

## Creating Custom Skills

Create a new skill by implementing `SkillBase` with parameter support:

<!-- snippet: no-compile complete example compilation unit in the reader's own `com.example.skills` package (declared for the narrative, not part of the SDK) -->
```java
package com.example.skills;

import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.List;
import java.util.Map;

public class MyCustomSkill implements SkillBase {

    private int maxItems;
    private int timeout;
    private int retryCount;

    @Override
    public String getName() {
        return "my_skill";
    }

    @Override
    public String getDescription() {
        return "Does something awesome with configurable parameters";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getRequiredPackages() {
        return List.of();  // Optional
    }

    @Override
    public List<String> getRequiredEnvVars() {
        return List.of("API_KEY");  // Optional
    }

    @Override
    public boolean setup(Map<String, Object> params) {
        // Initialize the skill with parameters
        if (!validateEnvVars() || !validatePackages()) {
            return false;
        }

        // Use parameters with defaults
        this.maxItems = (int) params.getOrDefault("max_items", 10);
        this.timeout = (int) params.getOrDefault("timeout", 30);
        this.retryCount = (int) params.getOrDefault("retry_count", 3);

        return true;
    }

    @Override
    public List<ToolDefinition> registerTools() {
        // Register SWAIG tools with the agent
        Map<String, Object> parameters = Map.of(
            "type", "object",
            "properties", Map.of(
                "input", Map.of(
                    "type", "string",
                    "description", "Input parameter")));

        ToolDefinition tool = new ToolDefinition(
            "my_function",
            "Does something cool (max " + maxItems + " items)",
            parameters,
            this::myHandler);

        return List.of(tool);
    }

    private FunctionResult myHandler(Map<String, Object> args, Map<String, Object> rawData) {
        // Use maxItems, timeout, retryCount in your logic
        return new FunctionResult("Processed with max_items=" + maxItems);
    }

    @Override
    public List<String> getHints() {
        // Speech recognition hints
        return List.of("custom", "skill", "awesome");
    }

    @Override
    public List<Map<String, Object>> getPromptSections() {
        // Prompt sections to add to agent
        return List.of(Map.of(
            "title", "Custom Capability",
            "body", "You can do custom things with my_skill (configured for "
                + maxItems + " items)."));
    }
}
```

Register the skill in the `SkillRegistry`, then it is available as:
<!-- snippet: no-compile references the reader's own `MyCustomSkill` from the example above (not an SDK type) -->
```java
import com.signalwire.sdk.skills.SkillRegistry;
import java.util.Map;

// Register once at startup
SkillRegistry.register("my_skill", MyCustomSkill::new);

// Use defaults
agent.addSkill("my_skill", Map.of());

// Use custom parameters
agent.addSkill("my_skill", Map.of(
    "max_items", 20,
    "timeout", 60,
    "retry_count", 5
));
```

## Quick Start

1. **Add the dependency** to your build:
   ```groovy
   // build.gradle
   dependencies {
       implementation 'com.signalwire:signalwire-sdk:2.0.2'
   }
   ```

2. **Run the demo:**
   ```bash
   ./gradlew run -PmainClass=SkillsDemo
   ```

3. **For web search, set environment variables:**
   ```bash
   export GOOGLE_SEARCH_API_KEY="your_api_key"
   export GOOGLE_SEARCH_ENGINE_ID="your_engine_id"
   ```

## Testing

Test the skills system with parameters (`src/test/java/com/signalwire/sdk/SkillsTest.java`):

```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.skills.SkillRegistry;
import java.util.Map;

// Show registered skills
var registry = new SkillRegistry();
System.out.println("Available skills: "
    + registry.listSkills().stream().map(s -> s.get("name")).toList());

// Create agent and load skills with parameters
var agent = AgentBase.builder().name("Test").route("/test").build();
agent.addSkill("datetime", Map.of());
agent.addSkill("math", Map.of());
agent.addSkill("web_search", Map.of("num_results", 2, "delay", 0.5));

System.out.println("Loaded skills: " + agent.listSkills());
System.out.println("Skills system with parameters working!");
```

## Benefits

- **One-liner integration** - `agent.addSkill("skill_name", Map.of())`
- **Configurable parameters** - `agent.addSkill("skill_name", Map.of("param", "value"))`
- **Static registration** - Register skills in the registry and they're available
- **Dependency validation** - Checks packages and environment variables
- **Modular architecture** - Skills are self-contained and reusable
- **Extensible** - Easy to create custom skills with parameters
- **Clean separation** - Skills don't interfere with each other
- **Performance tuning** - Configure skills for speed vs. comprehensiveness

## Migration Guide

**Before (manual implementation):**
<!-- snippet: no-compile illustrative "before" pseudo-code (elided `/* ... */` method argument), shown for contrast -->
```java
// Had to manually implement every capability
public class WebSearchAgent extends AgentBase {
    public WebSearchAgent() {
        // configure Google search, register tools by hand...
        defineTool("web_search", /* ... lots of manual code ... */);
    }
}
```

**After (skills system with parameters):**
```java
// Simple one-liner with custom configuration
var agent = AgentBase.builder().name("WebSearchAgent").build();
agent.addSkill("web_search", Map.of(
    "num_results", 3,   // Get more results
    "delay", 0.5        // Be respectful to servers
));
// Done! Full web search capability with custom settings.
```

The skills system makes SignalWire agents more modular, maintainable, and configurable.
