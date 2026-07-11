# Contexts and Steps Guide

## Table of Contents

- [Overview](#overview)
- [Core Concepts](#core-concepts)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Navigation and Flow Control](#navigation-and-flow-control)
- [Function Restrictions](#function-restrictions)
- [Real-World Examples](#real-world-examples)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)
- [Migration from POM](#migration-from-pom)

## Overview

<!-- snippet-setup -->
```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.contexts.ContextBuilder;
import com.signalwire.sdk.contexts.Context;
import com.signalwire.sdk.contexts.Step;
import com.signalwire.sdk.skills.SkillName;

AgentBase agent = AgentBase.builder().name("ctx-guide").route("/agent").build();
ContextBuilder contexts = agent.defineContexts();
Context ctx = contexts.addContext("default");
Context context = ctx;
Step step = ctx.addStep("step");
```

The **Contexts and Steps** system enhances traditional Prompt Object Model (POM) prompts in SignalWire AI agents by adding structured workflows on top of your base prompt. Instead of just defining a single prompt, you create workflows with explicit steps, navigation rules, and completion criteria. Steps can restrict which SWAIG (SignalWire AI Gateway) functions are available at each stage of the conversation.

### Key Benefits

- **Structured Workflows**: Define clear, step-by-step processes
- **Explicit Navigation**: Control exactly where users can go next
- **Function Restrictions**: Limit AI tool access per step
- **Completion Criteria**: Define clear progression requirements
- **Context Isolation**: Separate different conversation flows
- **Debugging**: Easier to trace and debug complex interactions

### When to Use Contexts vs Traditional Prompts

**Use Contexts and Steps when:**
- Building multi-step workflows (onboarding, support tickets, applications)
- Need explicit navigation control between conversation states
- Want to restrict function access based on conversation stage
- Building complex customer service or troubleshooting flows
- Creating guided experiences with clear progression

**Use Traditional Prompts when:**
- Building simple, freeform conversational agents
- Want maximum flexibility in conversation flow
- Creating general-purpose assistants
- Prototyping or building simple proof-of-concepts

## Core Concepts

### Contexts

A **Context** represents a conversation state or workflow area. Contexts can be:

- **Workflow Container**: Simple step organization without state changes
- **Context Switch**: Triggers conversation state changes when entered

Each context can define:

- **Steps**: Individual workflow stages within the context
- **Context Prompts**: Guidance that applies to all steps in the context  
- **Entry Parameters**: Control conversation state when context is entered
- **Navigation Rules**: Which other contexts can be accessed

### Context Entry Parameters

When entering a context, these parameters control conversation behavior:

- **`post_prompt`**: Override the agent's post prompt for this context
- **`system_prompt`**: Trigger conversation reset with new instructions
- **`consolidate`**: Summarize previous conversation in new prompt
- **`full_reset`**: Complete system prompt replacement vs injection
- **`user_prompt`**: Inject user message for context establishment

**Important**: If `system_prompt` is present, the context becomes a "Context Switch Context" that processes entry parameters like a `context_switch` SWAIG action. Without `system_prompt`, it's a "Workflow Container Context" that only organizes steps.

### Context Prompts

Contexts can have their own prompts (separate from entry parameters):

```java
// Simple string prompt
context.setPrompt("Context-specific guidance");

// POM-style sections
context.addSection("Department", "Billing Department");
context.addBullets("Services", List.of("Payments", "Refunds", "Account inquiries"));
```

Context prompts provide guidance that applies to all steps within that context, creating a prompt hierarchy: Base Agent Prompt → Context Prompt → Step Prompt.

### Steps

A **Step** is a specific stage within a context. Each step defines:

- **Prompt Content**: What the AI says/does (text or POM sections)
- **Completion Criteria**: When the step is considered complete
- **Navigation Rules**: Where the user can go next
- **Function Access**: Which AI tools are available

### Navigation Control

The system provides fine-grained control over conversation flow:

- **Valid Steps**: Control movement within a context
- **Valid Contexts**: Control switching between contexts  
- **Implicit Navigation**: Automatic "next" step progression
- **Explicit Navigation**: User must explicitly choose next step

## Getting Started

### Basic Single-Context Workflow

```java
import com.signalwire.sdk.agent.AgentBase;

import java.util.List;

agent = AgentBase.builder()
        .name("Onboarding Assistant")
        .route("/onboarding")
        .build();

// Define contexts (replaces traditional prompt setup)
contexts = agent.defineContexts();

// Single context must be named "default"
var workflow = contexts.addContext("default");

// Step 1: Welcome
workflow.addStep("welcome")
        .setText("Welcome to our service! Let's get you set up. What's your name?")
        .setStepCriteria("User has provided their name")
        .setValidSteps(List.of("collect_email"));

// Step 2: Collect Email
workflow.addStep("collect_email")
        .setText("Thanks! Now I need your email address to create your account.")
        .setStepCriteria("Valid email address has been provided")
        .setValidSteps(List.of("confirm_details"));

// Step 3: Confirmation
workflow.addStep("confirm_details")
        .setText("Perfect! Let me confirm your details before we proceed.")
        .setStepCriteria("User has confirmed their information")
        .setValidSteps(List.of("complete"));

// Step 4: Completion
workflow.addStep("complete")
        .setText("All set! Your account has been created successfully.");
        // No setValidSteps() = end of workflow

agent.run();
```

### Multi-Context Workflow

```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.skills.SkillName;

import java.util.List;
import java.util.Map;

agent = AgentBase.builder()
        .name("Customer Service")
        .route("/service")
        .build();

// Add skills for enhanced capabilities
agent.addSkill(SkillName.DATETIME, Map.of());
agent.addSkill(SkillName.WEB_SEARCH, Map.of(
        "api_key", "your-api-key",
        "search_engine_id", "your-engine-id"));

contexts = agent.defineContexts();

// Main triage context
var triage = contexts.addContext("triage");
triage.addStep("greeting")
        .addSection("Current Task", "Understand the customer's need and route appropriately")
        .addBullets("Required Information", List.of(
                "Type of issue they're experiencing",
                "Urgency level of the problem",
                "Previous troubleshooting attempts"))
        .setStepCriteria("Customer's need has been identified")
        .setValidContexts(List.of("technical", "billing", "general"));

// Technical support context
var tech = contexts.addContext("technical");
tech.addStep("technical_help")
        .addSection("Current Task", "Help diagnose and resolve technical issues")
        .addSection("Available Tools", "Use web search and datetime functions for technical solutions")
        .setFunctions(List.of("web_search", "datetime"))
        .setStepCriteria("Issue is resolved or escalated")
        .setValidContexts(List.of("triage"));

// Billing context (restricted functions for security)
var billing = contexts.addContext("billing");
billing.addStep("billing_help")
        .setText("I'll help with your billing question. For security, please provide your account verification.")
        .setFunctions("none")
        .setStepCriteria("Billing issue is addressed")
        .setValidContexts(List.of("triage"));

// General inquiries context
var general = contexts.addContext("general");
general.addStep("general_help")
        .setText("I'm here to help with general questions. What can I assist you with?")
        .setFunctions(List.of("web_search", "datetime"))
        .setStepCriteria("Question has been answered")
        .setValidContexts(List.of("triage"));

agent.run();
```

## API Reference

### ContextBuilder

The main entry point for defining contexts and steps.

```java
// Get the builder
contexts = agent.defineContexts();

// Create contexts
context = contexts.addContext("name");
```

### Context

Represents a conversation context or workflow state.

<!-- snippet: no-compile class Context interface-style signature listing (methods without bodies) — API reference, not runnable -->
```java
class Context {
    Step addStep(String name);                          // Create a new step in this context
    Context setValidContexts(List<String> contexts);    // Which contexts can be accessed from this context

    // Context entry parameters
    Context setPostPrompt(String postPrompt);           // Override post prompt for this context
    Context setSystemPrompt(String systemPrompt);       // Trigger context switch with new system prompt
    Context setConsolidate(boolean consolidate);        // Consolidate conversation history when entering
    Context setFullReset(boolean fullReset);            // Full system prompt replacement vs injection
    Context setUserPrompt(String userPrompt);           // Inject user message for context

    // Context prompts
    Context setPrompt(String prompt);                   // Simple string prompt for context
    Context addSection(String title, String body);      // Add POM section to context prompt
    Context addBullets(String title, List<String> bullets); // Add POM bullet section to context prompt

    // Context isolation and fillers
    Context setIsolated(boolean isolated);              // Mark context as isolated (independent state)
    Context setEnterFillers(Map<String, List<String>> fillers); // Fillers spoken when entering
    Context setExitFillers(Map<String, List<String>> fillers);  // Fillers spoken when exiting
    Context addEnterFiller(String languageCode, List<String> fillers); // Enter fillers for one language
    Context addExitFiller(String languageCode, List<String> fillers);  // Exit fillers for one language
}
```

#### Methods

- `addStep(name)`: Create and return a new Step
- `setValidContexts(contexts)`: Allow navigation to specified contexts
- `setPostPrompt(prompt)`: Override agent's post prompt for this context
- `setSystemPrompt(prompt)`: Trigger context switch behavior (makes this a Context Switch Context)
- `setConsolidate(bool)`: Whether to consolidate conversation when entering
- `setFullReset(bool)`: Complete vs partial context reset
- `setUserPrompt(prompt)`: User message to inject when entering context
- `setPrompt(text)`: Simple string prompt for context
- `addSection(title, body)`: Add POM section to context prompt
- `addBullets(title, list)`: Add POM bullet section to context prompt
- `setIsolated(bool)`: Mark context as isolated (independent conversation state)
- `setEnterFillers(map)`: Set all enter fillers by language code
- `setExitFillers(map)`: Set all exit fillers by language code
- `addEnterFiller(lang, list)`: Add enter fillers for a specific language
- `addExitFiller(lang, list)`: Add exit fillers for a specific language

### Step

Represents a single step within a context workflow.

<!-- snippet: no-compile class Step interface-style signature listing (methods without bodies) — API reference, not runnable -->
```java
class Step {
    // Content definition (choose one approach)
    Step setText(String text);                          // Direct text prompt (mutually exclusive with sections)
    Step addSection(String title, String body);         // Add a POM-style section (mutually exclusive with setText)
    Step addBullets(String title, List<String> bullets);// Add a titled bullet section

    // Flow control
    Step setStepCriteria(String criteria);              // Completion criteria for this step
    Step setValidSteps(List<String> steps);             // Which steps can be accessed next in same context
    Step setValidContexts(List<String> contexts);       // Which contexts can be accessed from this step

    // Function restrictions ("none" or a List<String> of function names)
    Step setFunctions(Object functions);

    // Reset behavior when entering step
    Step setResetSystemPrompt(String systemPrompt);     // Reset system prompt when entering this step
    Step setResetUserPrompt(String userPrompt);         // Reset user prompt when entering this step
    Step setResetConsolidate(boolean consolidate);      // Consolidate conversation when entering this step
    Step setResetFullReset(boolean fullReset);          // Full conversation reset when entering this step
}
```

#### Content Methods

**Option 1: Direct Text**
```java
step.setText("Direct prompt text for the AI");
```

**Option 2: POM-Style Sections**
```java
step.addSection("Role", "You are a helpful assistant")
    .addSection("Instructions", "Help users with their questions")
    .addBullets("Guidelines", List.of("Be friendly", "Ask clarifying questions"));
```

**Note**: You cannot mix `setText()` with `addSection()` in the same step.

#### Navigation Methods

```java
// Control step progression within context
step.setValidSteps(List.of("step1", "step2"));  // Can go to step1 or step2
step.setValidSteps(List.of());                  // Cannot progress (dead end)
// No setValidSteps() call = implicit "next" step

// Control context switching
step.setValidContexts(List.of("context1", "context2"));  // Can switch contexts
step.setValidContexts(List.of());                        // Trapped in current context
// No setValidContexts() call = inherit from context level
```

#### Function Restriction Methods

```java
// Allow specific functions only
step.setFunctions(List.of("datetime", "math"));

// Block all functions
step.setFunctions("none");

// No restriction (default - all agent functions available)
// step.setFunctions(...)  // Don't call this method
```

## Navigation and Flow Control

### Step Navigation Rules

The `setValidSteps()` method controls movement within a context:

```java
// Explicit step list - can only go to these steps
step.setValidSteps(List.of("review", "edit", "cancel"));

// Empty list - dead end, cannot progress
step.setValidSteps(List.of());

// Not called - implicit "next" step progression
// (will go to the next step defined in the context)
```

### Context Navigation Rules

The `setValidContexts()` method controls switching between contexts:

```java
// Can switch to these contexts
step.setValidContexts(List.of("billing", "technical", "general"));

// Trapped in current context
step.setValidContexts(List.of());

// Not called - inherit from context-level settings
```

### Navigation Inheritance

Context-level navigation settings are inherited by steps:

```java
// Set at context level
context.setValidContexts(List.of("main", "help"));

// All steps in this context can access main and help contexts
// unless overridden at step level
step.setValidContexts(List.of("main"));  // Override - only main allowed
```

### Complete Navigation Example

```java
contexts = agent.defineContexts();

// Main context
var main = contexts.addContext("main");
main.setValidContexts(List.of("help", "settings"));  // Context-level setting

main.addStep("welcome")
    .setText("Welcome! How can I help you?")
    .setValidSteps(List.of("menu"));  // Must go to menu
    // Inherits context-level validContexts

main.addStep("menu")
    .setText("Choose an option: 1) Help 2) Settings 3) Continue")
    .setValidContexts(List.of("help", "settings", "main"));  // Override context setting
    // No setValidSteps() = this is a branching point

// Help context
var helpCtx = contexts.addContext("help");
helpCtx.addStep("help_info")
    .setText("Here's how to use the system...")
    .setValidContexts(List.of("main"));  // Can return to main

// Settings context
var settings = contexts.addContext("settings");
settings.addStep("settings_menu")
    .setText("Choose a setting to modify...")
    .setValidContexts(List.of("main"));  // Can return to main
```

## Function Restrictions

Control which AI tools/functions are available in each step for enhanced security and user experience.

### Function Restriction Levels

```java
// No restrictions (default) - all agent functions available:
// simply don't call step.setFunctions()

// Allow specific functions only
step.setFunctions(List.of("datetime", "math", "web_search"));

// Block all functions
step.setFunctions("none");
```

### Security-Focused Example

```java
agent = AgentBase.builder()
        .name("Banking Assistant")
        .route("/banking")
        .build();

// Add potentially sensitive functions
agent.addSkill(SkillName.WEB_SEARCH, Map.of("api_key", "key", "search_engine_id", "id"));
agent.addSkill(SkillName.DATETIME, Map.of());

contexts = agent.defineContexts();

// Public context - full access
var public_ = contexts.addContext("public");
public_.addStep("welcome")
        .setText("Welcome to banking support. Are you an existing customer?")
        .setFunctions(List.of("datetime", "web_search"))  // Safe functions only
        .setValidContexts(List.of("authenticated", "public"));

// Authenticated context - restricted for security
var auth = contexts.addContext("authenticated");
auth.addStep("account_access")
        .setText("I can help with your account. What do you need assistance with?")
        .setFunctions("none")  // No external functions for account data
        .setValidContexts(List.of("public"));  // Can log out
```

### Function Access Patterns

```java
// Progressive function access based on trust level
contexts = agent.defineContexts();

// Low trust - limited functions
var public_ = contexts.addContext("public");
public_.addStep("initial_contact")
        .setFunctions(List.of("datetime"));  // Only safe functions

// Medium trust - more functions
var verified = contexts.addContext("verified");
verified.addStep("verified_user")
        .setFunctions(List.of("datetime", "web_search"));  // Add search capability

// High trust - full access
var authenticated = contexts.addContext("authenticated");
authenticated.addStep("full_access");
        // No setFunctions() call = all functions available
```

## Step Modes

Steps can operate in two modes:

- **Normal Mode**: The step's text is injected as instructions. The AI follows those instructions, and the step completes based on criteria you define or by navigating to the next step.
- **Gather Info Mode**: The step collects structured information from the caller one question at a time, with zero tool artifacts in the LLM conversation history. Once all questions are answered, the step either auto-advances or returns to normal mode.

### Normal Mode

In normal mode, the step's text is injected as a system message with this structure:

```
[context prompt if any]

## Instructions to complete the Current Step
[your step text]

Do not mention to the user that you are following steps, or the names of the steps.
Do not ask the user any questions not explicitly related to these instructions.
Do not end the conversation when this step is complete.
[step criteria if any]
```

The step text supports `${variable}` expansion from `global_data` and prompt variables.

Step criteria tell the AI when a step is done. The AI evaluates the criteria and calls `next_step` when they're met:

```java
ctx.addStep("verify")
    .setText("Verify the caller's identity.")
    .setStepCriteria(
        "The caller has provided their account number "
        + "AND confirmed their date of birth.")
    .setValidSteps(List.of("handle_request"));
```

### Gather Info Mode

When an AI agent needs to collect structured information (name, address, account number, etc.), the traditional approach uses SWAIG functions -- the AI calls a function for each piece of data, which creates `tool_call` and `tool_result` entries in the conversation history. These tool artifacts confuse some models (especially reasoning models at low effort settings), waste tokens, and can cause the model to lose track of where it is in the collection flow.

Gather info mode solves this by using **dynamic step instruction re-injection**. Questions are presented one at a time by swapping out the system instruction, and answers are recorded via an internal function that routes through the system-log path -- producing **zero** tool_call/tool_result entries in the LLM-visible conversation history.

#### How It Works Internally

1. **Step entry**: When the AI enters a step with `gather_info`, the system switches to gather questioning mode.
2. **Preamble injection** (first question only): If the gather has a `prompt`, it's injected as a **persistent** system message for the entire gather sequence.
3. **Question injection**: A minimal system instruction is injected as a **clearable** message containing the question text, type hint, confirmation instructions, and any per-question prompt text.
4. **Tool lockdown**: During gather mode, **all normal functions are hidden** -- only `gather_submit` (an internal function) and any per-question `functions` are visible.
5. **Answer submission**: When the AI calls `gather_submit`, the answer is written to `global_data` and the next question's instruction is re-injected. The `gather_submit` call routes through the system-log path, so the LLM never sees tool_call/tool_result for it.
6. **Completion**: When all questions are answered, either:
   - The step auto-advances to the next sequential step (`completion_action="next_step"`)
   - The step jumps to a specific named step (`completion_action="step_name"`)
   - The step returns to normal mode with the regular step text, plus a note that gathered data is available (when `completion_action` is None)

Here's what the LLM conversation history looks like during gather mode:

```
[system] You are a travel assistant. You need to collect some details.    <- persistent preamble
[system] Ask the user: "What is your first name?"                        <- clearable, changes per question
         When you have the answer, call the gather_submit function.
         Do not ask the user any other questions.

[assistant] Hi there! I'm your travel assistant. What's your first name?
[user] Tony.
                                                        <- gather_submit recorded via system-log (invisible)
[system] Ask the user: "What is your last name?"        <- previous question instruction replaced
         ...

[assistant] Great, Tony! And your last name?
[user] Smith.
```

No tool_call/tool_result entries anywhere. Clean conversation history.

#### Basic Gather Example

```java
ctx.addStep("collect_info")
    .setText("Help the caller with their request.")
    .setGatherInfo("caller_info", null, null)
    .addGatherQuestion("first_name", "What is your first name?")
    .addGatherQuestion("last_name", "What is your last name?")
    .addGatherQuestion("email", "What is your email address?");
```

This collects three pieces of information, stores them under `caller_info` in global_data, then returns to normal step mode with the step text "Help the caller with their request."

#### The Gather Prompt (Preamble)

The gather `prompt` is injected once as a persistent message when the first question begins:

```java
ctx.addStep("collect_profile")
    .setText("Use the profile to recommend products.")
    .setGatherInfo(
        "profile",  // outputKey
        null,       // completionAction
        "Welcome the caller and introduce yourself as a product specialist. "
        + "Explain that you need to ask a few quick questions to find the "
        + "best products for them. Be friendly and conversational.")
    .addGatherQuestion("name", "What is your name?")
    .addGatherQuestion("budget", "What is your budget?", "number", false, null, null);
```

Without a gather `prompt`, the AI jumps straight into asking the first question with no introduction.

#### Question Types

Each question has a `type` that controls the JSON schema of the `answer` parameter in `gather_submit`:

<!-- snippet: no-compile illustrative — dangling `.addGatherQuestion(...)` method-variant calls with no receiver -->
```java
// String (default) - free text (the 2-arg overload defaults type to "string")
.addGatherQuestion("name", "What is your name?")

// Integer - whole numbers
.addGatherQuestion("age", "How old are you?", "integer", false, null, null)

// Number - decimal values
.addGatherQuestion("budget", "What is your budget in dollars?", "number", false, null, null)

// Boolean - yes/no questions
.addGatherQuestion("has_passport", "Do you have a valid passport?", "boolean", false, null, null)
```

#### Confirmation Flow

When `confirm=True`, the AI must read the answer back to the caller and get explicit confirmation before submitting:

<!-- snippet: no-compile illustrative — dangling `.addGatherQuestion(...)` call with no receiver -->
```java
.addGatherQuestion(
    "last_name",
    "What is your last name?",
    "string",  // type
    true,      // confirm
    null,      // prompt
    null)      // functions
```

How it works:

1. The question instruction includes: "You MUST confirm the answer with the user before submitting."
2. The `gather_submit` function schema includes a required `confirmed_by_user` enum parameter.
3. If the AI calls `gather_submit` with `confirmed_by_user` set to `"false"`, the function rejects the submission and tells the AI to confirm with the user first.
4. The AI must read back the answer, get the user's "yes", then call `gather_submit` again with `confirmed_by_user: "true"`.

#### Per-Question Instructions and Functions

Each question can have additional instructions and specific functions made available:

<!-- snippet: no-compile illustrative — dangling `.addGatherQuestion(...)` call with no receiver -->
```java
.addGatherQuestion(
    "home_airport",
    "What is your home airport or nearest major city for departure?",
    "string",  // type
    true,      // confirm
    "Use the resolve_airport function to validate the airport code "
    + "before submitting. If the airport is ambiguous, clarify with the user.",  // prompt
    List.of("resolve_airport"))  // functions
```

The `resolve_airport` function must already be registered on the agent. The `functions` array activates those functions for this question only, alongside `gather_submit`. When the next question begins, they're deactivated again.

#### Output Storage

Answers are stored in `global_data`, which is available in prompt variable expansion via `${key}`:

<!-- snippet: no-compile illustrative — dangling `.addGatherQuestion(...)`/data-map calls with no receiver -->
```java
// Store under a namespace
.setGatherInfo("profile", null, null)
// Results in: global_data.profile.first_name, global_data.profile.last_name, etc.
// Accessible in prompts as: ${profile}

// Store at top level (null outputKey)
.setGatherInfo(null, null, null)
// Results in: global_data.first_name, global_data.last_name, etc.
```

After gathering, `global_data` is refreshed so subsequent step prompts can reference the collected values:

```java
ctx.addStep("plan_trip")
    .setText(
        "The caller's travel profile is: ${profile}. "
        + "Use their name, budget, and preferences to suggest destinations.");
```

#### Auto-Advancing After Gather

With `completion_action`, the step automatically advances when the last question is answered. You can advance to the next sequential step or jump to a specific named step:

```java
// Advance to the next sequential step
ctx.addStep("collect_profile")
    .setText("Collect the caller's profile.")
    .setGatherInfo(
        "profile",      // outputKey
        "next_step",    // completionAction
        "Welcome the caller. You need to collect a few details.")
    .addGatherQuestion("name", "What is your name?")
    .addGatherQuestion("email", "What is your email?");

// This step runs immediately after the last question is answered
ctx.addStep("process")
    .setText("You have the caller's profile in ${profile}. Help them with their request.");
```

You can also jump to a specific step by name:

```java
ctx.addStep("collect_info")
    .setText("Collect caller info.")
    .setGatherInfo(
        "info",      // outputKey
        "review",    // completionAction — jump directly to "review" step
        null)
    .addGatherQuestion("name", "What is your name?")
    .addGatherQuestion("issue", "What is your issue?");

ctx.addStep("other_step")
    .setText("This step is skipped when coming from collect_info.");

ctx.addStep("review")
    .setText("Review the collected info in ${info} and help the caller.");
```

> **Note**: The target step is validated at build time. Using `"next_step"` on the last step in a context, or naming a step that doesn't exist, will throw an `IllegalStateException`.

#### Combining Gather with Normal Step Mode

Without `completion_action` (or when set to None), the step returns to normal mode after all questions are answered:

```java
ctx.addStep("intake")
    .setText(
        "Review the caller's information in ${intake_data}. "
        + "Confirm everything looks correct, then proceed to scheduling.")
    .setGatherInfo("intake_data", null, null)
    .addGatherQuestion("name", "What is your name?")
    .addGatherQuestion("dob", "What is your date of birth?")
    .addGatherQuestion("reason", "What is the reason for your visit?")
    .setValidSteps(List.of("schedule"));
```

Flow:
1. Gather mode: Questions are asked one at a time
2. All questions answered -> step switches to normal mode
3. Step text is injected with `valid_steps` and `step_criteria` restored
4. The AI follows the normal step instructions using the gathered data
5. Navigation to `schedule` becomes available

#### Gather Info API Reference

**`setGatherInfo(String outputKey, String completionAction, String prompt)` Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `outputKey` | `String` | Key in global_data to store answers under. If `null`, answers stored at top level. |
| `completionAction` | `String` | Where to go when all questions are answered: `"next_step"` to advance sequentially, or a specific step name (e.g. `"process_results"`) to jump to that step. If `null`, returns to normal step mode. The target is validated — `"next_step"` requires a following step, and named steps must exist in the context. |
| `prompt` | `String` | Preamble text injected once as a persistent message when entering the gather step. `null` to omit. |

**`addGatherQuestion(...)` Parameters:** the 2-arg overload `addGatherQuestion(key, question)` defaults `type` to `"string"`, `confirm` to `false`, and `prompt`/`functions` to `null`; the 6-arg overload exposes all of them.

| Parameter | Type | Default (2-arg overload) | Description |
|-----------|------|---------|-------------|
| `key` | `String` | required | Key name for storing the answer in global_data |
| `question` | `String` | required | The question text presented to the AI |
| `type` | `String` | `"string"` | JSON schema type: `"string"`, `"integer"`, `"number"`, `"boolean"` |
| `confirm` | `boolean` | `false` | If true, AI must confirm answer with user before submitting |
| `prompt` | `String` | `null` | Additional instruction text for this question |
| `functions` | `List<String>` | `null` | Function names to make visible for this question only |

## Real-World Examples

### Example 1: Technical Support Troubleshooting

```java
agent = AgentBase.builder()
        .name("Tech Support")
        .route("/tech-support")
        .build();

// Add diagnostic tools
agent.addSkill(SkillName.WEB_SEARCH, Map.of("api_key", "key", "search_engine_id", "id"));
agent.addSkill(SkillName.DATETIME, Map.of());

contexts = agent.defineContexts();

// Initial triage
var triage = contexts.addContext("triage");
triage.addStep("problem_identification")
        .addSection("Current Task", "Identify the type of technical issue")
        .addBullets("Information to Gather", List.of(
                "Description of the specific problem",
                "When did the issue start occurring?",
                "What steps has the customer already tried?",
                "Rate the severity level (critical/high/medium/low)"))
        .setStepCriteria("Issue type and severity determined")
        .setValidContexts(List.of("hardware", "software", "network"));

// Hardware troubleshooting
var hardware = contexts.addContext("hardware");
hardware.addStep("hardware_diagnosis")
        .addSection("Current Task", "Guide user through hardware diagnostics")
        .addSection("Available Tools", "Use web search to find hardware specifications and troubleshooting guides")
        .setFunctions(List.of("web_search"))  // Can search for hardware info
        .setStepCriteria("Hardware issue diagnosed")
        .setValidSteps(List.of("hardware_solution"));

hardware.addStep("hardware_solution")
        .setText("Based on the diagnosis, here's how to resolve the hardware issue...")
        .setStepCriteria("Solution provided and tested")
        .setValidContexts(List.of("triage"));  // Can start over if needed

// Software troubleshooting
var software = contexts.addContext("software");
software.addStep("software_diagnosis")
        .addSection("Current Task", "Diagnose software-related issues")
        .addSection("Available Tools", "Use web search for software updates and datetime to check for recent changes")
        .setFunctions(List.of("web_search", "datetime"))  // Can check for updates
        .setStepCriteria("Software issue identified")
        .setValidSteps(List.of("software_fix", "escalation"));

software.addStep("software_fix")
        .setText("Let's try these software troubleshooting steps...")
        .setStepCriteria("Fix attempted and result confirmed")
        .setValidSteps(List.of("escalation", "resolution"));

software.addStep("escalation")
        .setText("I'll escalate this to our specialist team.")
        .setFunctions("none")  // No tools needed for escalation
        .setStepCriteria("Escalation ticket created");

software.addStep("resolution")
        .setText("Great! The issue has been resolved.")
        .setStepCriteria("Customer confirms resolution")
        .setValidContexts(List.of("triage"));

// Network troubleshooting
var network = contexts.addContext("network");
network.addStep("network_diagnosis")
        .addSection("Current Task", "Diagnose network and connectivity issues")
        .addSection("Available Tools", "Use web search to check service status and datetime for outage windows")
        .setFunctions(List.of("web_search", "datetime"))  // Check service status
        .setStepCriteria("Network issue diagnosed")
        .setValidSteps(List.of("network_fix"));

network.addStep("network_fix")
        .setText("Let's resolve your connectivity issue with these steps...")
        .setStepCriteria("Network connectivity restored")
        .setValidContexts(List.of("triage"));

agent.run();
```

### Example 2: Multi-Step Application Process

```java
agent = AgentBase.builder()
        .name("Loan Application")
        .route("/loan-app")
        .build();

// Add verification tools
agent.addSkill(SkillName.DATETIME, Map.of());  // For date validation

contexts = agent.defineContexts();

// Single workflow context
var application = contexts.addContext("default");

// Step 1: Introduction and eligibility
application.addStep("introduction")
        .addSection("Current Task", "Guide customers through the loan application process")
        .addBullets("Information to Provide", List.of(
                "Explain the process clearly",
                "Outline what information will be needed",
                "Set expectations for timeline and next steps"))
        .setStepCriteria("Customer understands process and wants to continue")
        .setValidSteps(List.of("personal_info"));

// Step 2: Personal information
application.addStep("personal_info")
        .addSection("Instructions", "Collect personal information")
        .addBullets("Information to Collect", List.of(
                "Full legal name",
                "Date of birth",
                "Social Security Number",
                "Phone number and email"))
        .setFunctions(List.of("datetime"))  // Can validate dates
        .setStepCriteria("All personal information collected and verified")
        .setValidSteps(List.of("employment_info", "personal_info"));  // Can review/edit

// Step 3: Employment information
application.addStep("employment_info")
        .setText("Now I need information about your employment and income.")
        .setStepCriteria("Employment and income information complete")
        .setValidSteps(List.of("financial_info", "personal_info"));  // Can go back

// Step 4: Financial information
application.addStep("financial_info")
        .setText("Let's review your financial situation including assets and debts.")
        .setStepCriteria("Financial information complete")
        .setValidSteps(List.of("review", "employment_info"));  // Can go back

// Step 5: Review all information
application.addStep("review")
        .addSection("Instructions", "Review all collected information")
        .addBullets("Review Checklist", List.of(
                "Confirm personal details",
                "Verify employment information",
                "Review financial data",
                "Ensure accuracy before submission"))
        .setStepCriteria("Customer has reviewed and confirmed all information")
        .setValidSteps(List.of("submit", "personal_info", "employment_info", "financial_info"));

// Step 6: Submission
application.addStep("submit")
        .setText("Thank you! Your loan application has been submitted successfully. You'll receive a decision within 2-3 business days.")
        .setFunctions("none")  // No tools needed for final message
        .setStepCriteria("Application submitted and confirmation provided");
        // No setValidSteps() = end of process

agent.run();
```

### Example 3: E-commerce Customer Service

```java
agent = AgentBase.builder()
        .name("E-commerce Support")
        .route("/ecommerce")
        .build();

// Add tools for order management
agent.addSkill(SkillName.WEB_SEARCH, Map.of("api_key", "key", "search_engine_id", "id"));
agent.addSkill(SkillName.DATETIME, Map.of());

contexts = agent.defineContexts();

// Main service menu
var main = contexts.addContext("main");
main.addStep("service_menu")
        .addSection("Current Task", "Help customers with their orders and questions")
        .addBullets("Service Areas Available", List.of(
                "Order status, modifications, and tracking",
                "Returns and refunds",
                "Product information and specifications",
                "Account-related questions"))
        .setStepCriteria("Customer's need has been identified")
        .setValidContexts(List.of("orders", "returns", "products", "account"));

// Order management context
var orders = contexts.addContext("orders");
orders.addStep("order_assistance")
        .addSection("Current Task", "Help with order status, modifications, and tracking")
        .addSection("Available Tools", "Use datetime to check delivery dates and processing times")
        .setFunctions(List.of("datetime"))  // Can check delivery dates
        .setStepCriteria("Order issue resolved or escalated")
        .setValidContexts(List.of("main"));

// Returns and refunds context
var returns = contexts.addContext("returns");
returns.addStep("return_process")
        .addSection("Current Task", "Guide customers through return process")
        .addBullets("Return Process Steps", List.of(
                "Verify return eligibility",
                "Explain return policy",
                "Provide return instructions",
                "Process refund if applicable"))
        .setFunctions("none")  // Sensitive financial operations
        .setStepCriteria("Return request processed")
        .setValidContexts(List.of("main"));

// Product information context
var products = contexts.addContext("products");
products.addStep("product_help")
        .addSection("Current Task", "Help customers with product questions")
        .addSection("Available Tools", "Use web search to find detailed product information and specifications")
        .setFunctions(List.of("web_search"))  // Can search for product info
        .setStepCriteria("Product question answered")
        .setValidContexts(List.of("main"));

// Account management context
var account = contexts.addContext("account");
account.addStep("account_help")
        .setText("I can help with account-related questions. Please verify your identity first.")
        .setFunctions("none")  // Security-sensitive context
        .setStepCriteria("Account issue resolved")
        .setValidContexts(List.of("main"));

agent.run();
```

## Best Practices

### 1. Clear Step Naming

Use descriptive step names that indicate purpose:

<!-- snippet: no-compile illustrative — dangling `.addStep(...)` naming examples with no receiver -->
```java
// Good
.addStep("collect_shipping_address")
.addStep("verify_payment_method")
.addStep("confirm_order_details")

// Avoid
.addStep("step1")
.addStep("next")
.addStep("continue")
```

### 2. Meaningful Completion Criteria

Define clear, testable completion criteria:

<!-- snippet: no-compile illustrative — dangling `.setStepCriteria(...)` examples with no receiver -->
```java
// Good - specific and measurable
.setStepCriteria("User has provided valid email address and confirmed subscription preferences")
.setStepCriteria("All required fields completed and payment method verified")

// Avoid - vague or subjective
.setStepCriteria("User is ready")
.setStepCriteria("Everything is good")
```

### 3. Logical Navigation Flow

Design intuitive navigation that matches user expectations:

<!-- snippet: no-compile illustrative — dangling `.setValidSteps(...)` examples with no receiver -->
```java
// Allow users to go back and review
.setValidSteps(List.of("review_info", "edit_details", "confirm_submission"))

// Provide escape routes
.setValidContexts(List.of("main_menu", "help"))

// Consider dead ends carefully
.setValidSteps(List.of())  // Only if this is truly the end
```

### 4. Progressive Function Access

Restrict functions based on security and context needs:

<!-- snippet: no-compile illustrative — references example step handles publicStep/authStep declared elsewhere -->
```java
// Public areas - limited functions
publicStep.setFunctions(List.of("datetime", "web_search"));

// Authenticated areas - more functions allowed
authStep.setFunctions(List.of("datetime", "web_search", "user_profile"));

// Sensitive operations - minimal functions
billingStep.setFunctions("none");
```

### 5. Context Organization

Organize contexts by functional area or user journey:

```java
// By functional area
List<String> byArea = List.of("triage", "technical_support", "billing", "account_management");

// By user journey stage
List<String> byStage = List.of("onboarding", "verification", "configuration", "completion");

// By security level
List<String> bySecurity = List.of("public", "authenticated", "admin");
```

### 6. Error Handling and Recovery

Provide recovery paths for common issues:

<!-- snippet: no-compile illustrative — dangling `.setValidSteps(...)`/`.setValidContexts(...)` examples with no receiver -->
```java
// Allow users to retry failed steps
.setValidSteps(List.of("retry_payment", "choose_different_method", "contact_support"))

// Provide help context access
.setValidContexts(List.of("help", "main"))

// Include validation steps
verificationCtx.addStep("validation")
    .setStepCriteria("Data validation passed")
    .setValidSteps(List.of("proceed", "edit_data"));
```

### 7. Content Strategy

Choose the right content approach for each step:

```java
// Use setText() for simple, direct instructions
step.setText("Please provide your email address");

// Use POM sections for complex, structured content
step.addSection("Role", "You are a technical specialist")
    .addSection("Context", "Customer is experiencing network issues")
    .addSection("Instructions", "Follow diagnostic protocol")
    .addBullets("Diagnostic Steps", List.of("Check connectivity", "Test speed", "Verify settings"));
```

## Troubleshooting

### Common Issues

#### 1. "Single context must be named 'default'"

**Error**: When using a single context with a name other than "default"

<!-- snippet: no-compile illustrative — deliberately shows a duplicate `var context` (Wrong vs Correct), which cannot compile -->
```java
// Wrong
var context = contexts.addContext("main");  // Error!

// Correct
var context = contexts.addContext("default");
```

#### 2. "Cannot mix setText with addSection"

**Error**: Using both direct text and POM sections in the same step

```java
// Wrong
step.setText("Welcome!")
    .addSection("Role", "Assistant");  // Error!

// Correct - choose one approach
step.setText("Welcome! I'm your assistant.");
// OR
step.addSection("Role", "Assistant")
    .addSection("Message", "Welcome!");
```

#### 3. Navigation Issues

**Problem**: Users getting stuck or unable to navigate

```java
// Check your navigation rules
step.setValidSteps(List.of());  // Dead end - is this intended?
step.setValidContexts(List.of());  // Trapped in context - is this intended?

// Add appropriate navigation
step.setValidSteps(List.of("next_step", "previous_step"));
step.setValidContexts(List.of("main", "help"));
```

#### 4. Function Access Problems

**Problem**: Functions not available when expected

```java
// Check function restrictions
step.setFunctions("none");  // All functions blocked
step.setFunctions(List.of("datetime"));  // Only datetime allowed

// Verify function names match your agent's functions
agent.addSkill(SkillName.WEB_SEARCH, Map.of());  // Function name is "web_search"
step.setFunctions(List.of("web_search"));  // Must match exactly
```

### Debugging Tips

#### 1. Trace Navigation Flow

Add logging to understand flow:

<!-- snippet: no-compile illustrative — a standalone helper method definition (createStepWithLogging), not a runnable statement block -->
```java
Step createStepWithLogging(Context context, String name) {
    Step step = context.addStep(name);
    System.out.println("Created step: " + name);
    return step;
}
```

#### 2. Validate Navigation Rules

Check that all referenced steps/contexts exist:

<!-- snippet: no-compile illustrative — dangling `.setValidSteps(...)`/`.setValidContexts(...)` examples with no receiver -->
```java
// Ensure referenced steps exist
.setValidSteps(List.of("review", "edit"))  // Both "review" and "edit" steps must exist

// Ensure referenced contexts exist
.setValidContexts(List.of("main", "help"))  // Both "main" and "help" contexts must exist
```

#### 3. Test Function Restrictions

Verify functions are properly restricted:

```java
// Test with all functions
// step  // No setFunctions() call

// Test with restrictions
step.setFunctions(List.of("datetime"));

// Test with no functions
step.setFunctions("none");
```

## Migration from POM

### Converting Traditional Prompts

**Before (Traditional POM):**
```java
agent = AgentBase.builder()
        .name("assistant")
        .route("/assistant")
        .build();

agent.promptAddSection("Role", "You are a helpful assistant");
agent.promptAddSection("Instructions", "Help users with questions");
agent.promptAddSection("Guidelines", "", List.of(
        "Be friendly",
        "Ask clarifying questions",
        "Provide accurate information"));
```

**After (Contexts and Steps):**
```java
agent = AgentBase.builder()
        .name("assistant")
        .route("/assistant")
        .build();

contexts = agent.defineContexts();
var main = contexts.addContext("default");

main.addStep("assistance")
        .addSection("Role", "You are a helpful assistant")
        .addSection("Instructions", "Help users with questions")
        .addBullets("Guidelines", List.of(
                "Be friendly",
                "Ask clarifying questions",
                "Provide accurate information"))
        .setStepCriteria("User's question has been answered");
```

### Hybrid Approach

You can use both traditional prompts and contexts in the same agent:

```java
agent = AgentBase.builder()
        .name("hybrid")
        .route("/hybrid")
        .build();

// Traditional prompt sections (from skills, global settings, etc.)
// These will coexist with contexts

// Define contexts for structured workflows
contexts = agent.defineContexts();
var workflow = contexts.addContext("default");

workflow.addStep("structured_process")
        .setText("Following the structured workflow...")
        .setStepCriteria("Workflow complete");
```

### Migration Strategy

1. **Start Simple**: Convert one workflow at a time
2. **Preserve Existing**: Keep traditional prompts for simple interactions
3. **Add Structure**: Use contexts for complex, multi-step processes
4. **Test Thoroughly**: Verify navigation and function access work as expected
5. **Iterate**: Refine step criteria and navigation based on testing

---

## Conclusion

The Contexts and Steps system provides structured workflow control for building sophisticated AI agents. By combining structured navigation, function restrictions, and clear completion criteria, you can create predictable, user-friendly agent experiences that guide users through complex processes while maintaining security and control.

Start with simple single-context workflows and gradually build more complex multi-context systems as your requirements grow. The system is designed to be flexible and scalable, supporting both simple linear workflows and complex branching conversation trees.

### Dynamic Context Switching

To switch contexts dynamically during a conversation, return a `FunctionResult` with the `swmlChangeContext()` method from a tool handler:

```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

agent = AgentBase.builder()
        .name("multi-context")
        .route("/multi")
        .build();

// Define contexts using the ContextBuilder pattern
contexts = agent.defineContexts();

// Sales context
var sales = contexts.addContext("sales");
sales.addSection("Role", "You are a helpful sales representative.");
sales.addStep("greeting").setText("Welcome customers and understand their needs.");

// Support context
var support = contexts.addContext("support");
support.addSection("Role", "You are a technical support specialist.");
support.addStep("diagnose").setText("Help diagnose and resolve technical issues.");

// Tools that switch contexts via swmlChangeContext
agent.defineTool("transfer_to_support",
        "Transfer the customer to technical support",
        Map.of("type", "object", "properties", Map.of()),
        (args, raw) -> new FunctionResult("Transferring you to technical support...")
                .swmlChangeContext("support"));

agent.defineTool("transfer_to_sales",
        "Transfer the customer to sales",
        Map.of("type", "object", "properties", Map.of()),
        (args, raw) -> new FunctionResult("Transferring you to sales...")
                .swmlChangeContext("sales"));
```

For a complete example of multi-context agents with different personas, see `examples/ContextsDemo.java`.

---

### Example 4: Travel Profile Agent (Gather Info Mode)

Collects a travel profile with typed questions and confirmation, then recommends destinations:

```java
import com.signalwire.sdk.agent.AgentBase;

import java.util.List;

agent = AgentBase.builder()
        .name("Travel Agent")
        .route("/travel")
        .build();

agent.promptAddSection("Role", "You are a friendly travel booking assistant.");

contexts = agent.defineContexts();
ctx = contexts.addContext("default");

// Step 1: Collect profile (gather mode, auto-advance)
ctx.addStep("collect_profile")
        .setText("Collect the caller's travel profile.")
        .setGatherInfo(
                "profile",      // outputKey
                "next_step",    // completionAction
                "Welcome the caller and introduce yourself as a travel "
                + "booking assistant. You need to collect a few details "
                + "to build their travel profile. Be warm and conversational.")
        .addGatherQuestion("first_name", "What is your first name?")
        .addGatherQuestion("last_name", "What is your last name?", "string", true, null, null)
        .addGatherQuestion("party_size", "How many people are traveling?", "integer", false, null, null)
        .addGatherQuestion("budget_per_person", "What is your budget per person?", "number", false, null, null)
        .addGatherQuestion("has_passport", "Do you have a valid passport?", "boolean", false, null, null)
        .addGatherQuestion("home_airport", "What is your home airport?", "string", true, null, null);

// Step 2: Recommend destinations (normal mode)
ctx.addStep("plan_trip")
        .setText(
                "You now have the caller's travel profile in ${profile}. "
                + "Use their name, party size, budget, passport status, and "
                + "home airport to suggest three vacation destinations. "
                + "If they don't have a passport, only suggest domestic destinations.");

agent.addLanguage("English", "en-US", "rime.spore");
```

### Example 5: Support Ticket Agent (Gather + Triage)

Gathers issue details, then routes to the right team using normal mode navigation:

```java
import com.signalwire.sdk.agent.AgentBase;

import java.util.List;

agent = AgentBase.builder()
        .name("Support Agent")
        .route("/support")
        .build();

agent.promptAddSection("Role", "You are a technical support agent.");

contexts = agent.defineContexts();
ctx = contexts.addContext("default");

// Collect ticket info, then return to normal mode for triage
ctx.addStep("intake")
        .setText(
                "You have the caller's issue details in ${ticket}. "
                + "Based on the category and description, route them to "
                + "the appropriate team.")
        .setGatherInfo(
                "ticket",  // outputKey
                null,      // completionAction (null = return to normal mode)
                "Thank the caller for contacting support. "
                + "You need to collect some details about their issue.")
        .addGatherQuestion("name", "What is your name?")
        .addGatherQuestion("account_id", "What is your account ID?", "string", true, null, null)
        .addGatherQuestion("category", "Is this about billing, a technical issue, or something else?")
        .addGatherQuestion("description", "Please describe the issue in detail.")
        .setValidSteps(List.of("billing_support", "tech_support", "general_support"));

ctx.addStep("billing_support")
        .setText("Help the caller with their billing issue. Details: ${ticket}.");

ctx.addStep("tech_support")
        .setText("Help the caller with their technical issue. Details: ${ticket}.")
        .setFunctions(List.of("run_diagnostics", "check_service_status"));

ctx.addStep("general_support")
        .setText("Help the caller with their general inquiry. Details: ${ticket}.");

agent.addLanguage("English", "en-US", "rime.spore");
```

Note: This example uses gather **without** `completionAction`. After all questions are answered, the step returns to normal mode with `validSteps` restored. The AI uses the gathered data to decide which support step to route to.

## Related Documentation

- **[API Reference](api_reference.md)** - Complete AgentBase class reference
- **[SWAIG Reference](swaig_reference.md)** - All available result methods including `swmlChangeContext()` and `swmlChangeStep()`
- **[Agent Guide](agent_guide.md)** - General agent development guide
- **[DataMap Guide](datamap_guide.md)** - Serverless function integration

### Example Files

- `examples/ContextsDemo.java` - Multi-context onboarding agent
- `examples/GatherInfoDemo.java` - Structured data collection using `setGatherInfo()` and `addGatherQuestion()`
- `examples/StepFunctionInheritanceDemo.java` - Per-step function whitelist inheritance
