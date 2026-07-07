# SignalWire AI Agents SDK: Why the SDK, Not Raw SWML

<!-- snippet-setup -->
```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.contexts.ContextBuilder;
import com.signalwire.sdk.contexts.Context;
import com.signalwire.sdk.contexts.Step;

AgentBase agent = AgentBase.builder().name("features-agent").route("/agent").build();
AgentBase salesAgent = AgentBase.builder().name("sales").route("/sales").build();
AgentBase supportAgent = AgentBase.builder().name("support").route("/support").build();
AgentBase triageAgent = AgentBase.builder().name("triage").route("/triage").build();
```

## The Problem with Raw SWML

SWML (SignalWire Markup Language) is a JSON document format that defines how an agent behaves during a call -- 30+ verbs, an AI verb with dozens of parameters, SWAIG (SignalWire AI Gateway) function definitions with JSON Schema, post-prompt URLs, webhook authentication, language arrays, pronunciation rules, hints, global data, contexts, steps, gather configs. Writing it by hand means constructing deeply nested JSON, manually building authenticated webhook URLs, hand-coding parameter schemas, and deploying separate webhook servers for your tools. Every agent becomes a bespoke JSON engineering project.

The SDK eliminates all of this. You write Java. The SDK generates correct SWML, serves it over HTTP, and handles its own webhook callbacks -- all in one process, deployable to any platform.

---

## The Self-Referencing Pipeline

The SDK's core architectural insight is that the agent is both the **SWML generator** and the **SWAIG webhook handler** in a single stateless microservice.

```
SignalWire requests SWML → Agent generates document
  ↓
SWML contains webhook URLs → URLs point back to the agent itself
  ↓
AI calls a function → SignalWire POSTs to agent's /swaig/ endpoint
  ↓
Agent executes function locally → Returns result to AI
  ↓
Call ends → SignalWire POSTs analytics to agent's /post_prompt/ endpoint
```

The agent auto-detects its own public URL -- including behind ngrok, load balancers, API Gateway, or any reverse proxy (via `X-Forwarded-Host`, `Forwarded` header, or `SWML_PROXY_URL_BASE` env var). It embeds Basic Auth credentials directly into the webhook URLs. It generates per-call security tokens for each function. The developer writes none of this:

```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import java.util.List;
import java.util.Map;

var weatherAgent = AgentBase.builder()
    .name("weather")
    .route("/weather")
    .port(3000)
    .build();

weatherAgent.promptAddSection("Role", "You help with weather.");

weatherAgent.defineTool(
    "get_weather",
    "Get weather",
    Map.of(
        "type", "object",
        "properties", Map.of("city", Map.of("type", "string")),
        "required", List.of("city")),
    (args, rawData) -> {
      String city = (String) args.get("city");
      // ... fetch weather ...
      return new FunctionResult("72°F and sunny in " + city);
    });

weatherAgent.run();
```

That's a complete agent: HTTP server, SWML generation, authenticated webhook routing, function execution, and response formatting. The generated SWML contains the full AI configuration, function schemas, and webhook URLs pointing back to the running process -- all computed automatically.

---

## Prompt Object Model (POM)

Raw SWML prompts are flat strings. The SDK provides structured prompt building:

```java
agent.promptAddSection("Role", "You are a travel booking assistant.");
agent.promptAddSection("Rules", "", List.of(
    "Never make up flight information",
    "Always confirm before booking",
    "Use the search tool for real data"));
agent.promptAddSection("Personality", "Friendly but professional.");
```

POM sections are rendered by the platform into a format the LLM understands with proper hierarchy. You can add subsections, append to existing sections, check if sections exist, and compose prompts programmatically -- including from skills that inject their own sections.

---

## Tools: Three Ways

### 1. Defined Functions (Local Execution)

<!-- snippet: no-compile illustrative handler using a reader-supplied `db` handle and `order` domain type (not defined here) -->
```java
agent.defineTool(
    "lookup_order",
    "Look up an order",
    Map.of(
        "type", "object",
        "properties", Map.of("order_id", Map.of("type", "string")),
        "required", List.of("order_id")),
    (args, rawData) -> {
      var order = db.get((String) args.get("order_id"));
      FunctionResult result = new FunctionResult("Order " + order.id + ": " + order.status);
      result.addAction("set_global_data", Map.of("current_order", order.toMap()));
      return result;
    });
```

The SDK converts this into a SWAIG function definition with JSON Schema parameters, creates a secure webhook URL, routes inbound POST requests to the handler, parses arguments, and formats the response -- including the 40+ SWAIG actions (transfer, hold, context_switch, toggle_functions, etc.) that tools can return via `FunctionResult`.

The handler is a `ToolHandler` lambda receiving `(Map<String, Object> args, Map<String, Object> rawData)`. In Java the parameter schema is always supplied explicitly to `defineTool` (there is no reflective inference from a method signature); the closed `Map`-based JSON-Schema shape is the parity surface for Python's `parameters=`.

### 2. DataMap (Server-Side Execution)

```java
import com.signalwire.sdk.datamap.DataMap;

DataMap dataMap = new DataMap("check_stock")
    .purpose("Check product stock levels")
    .parameter("sku", "string", "Product SKU", true)
    .webhook("GET", "https://api.warehouse.com/stock/${args.sku}")
    .output(new FunctionResult("Stock for ${args.sku}: ${response.quantity} units"))
    .fallbackOutput(new FunctionResult("Could not check stock right now"));

agent.registerSwaigFunction(dataMap.toSwaigFunction());
```

DataMap tools execute on SignalWire's servers -- no webhook needed. The SDK generates the `data_map` structure in the SWML with variable expansion (`${args.*}`, `${response.*}`, `${global_data.*}`), foreach iteration, expression matching, and error handling. Your agent never receives the callback; SignalWire handles the entire API call.

### 3. Skills (Packaged Integrations)

```java
agent.addSkill("web_search", Map.of("api_key", "...", "engine_id", "..."));
agent.addSkill("datetime", Map.of());
agent.addSkill("math", Map.of());
```

One line. The skill auto-registers its tools, injects prompt sections, adds speech hints, and validates dependencies. No manual wiring.

---

## The Skills System

Skills are self-contained modules that package tools, prompts, hints, and configuration into a single `addSkill()` call. Each skill:

- Implements the `SkillBase` interface with required `setup(params)` and `registerTools()` methods
- Declares required packages and env vars (via `getRequiredPackages()` / `getRequiredEnvVars()`) for dependency validation
- Builds SWAIG functions in `registerTools()` (or `getSwaigFunctions()` for DataMap-based skills)
- Can inject prompt sections via `getPromptSections()`
- Can provide speech hints via `getHints()`
- Can contribute global data via `getGlobalData()`
- Supports multiple instances with different configs (e.g., two `web_search` skills with different engines) via `supportsMultipleInstances()` / `getInstanceKey()`

**Built-in skills:** `datetime`, `math`, `web_search`, `wikipedia_search`, `weather_api`, `google_maps`, `datasphere`, `datasphere_serverless`, `native_vector_search`, `spider`, `swml_transfer`, `play_background_file`, `info_gatherer`, `api_ninjas_trivia`, `joke`, `claude_skills`, `custom_skills`.

The elegance is composability: skills don't know about each other, but they all register cleanly into the same agent. A single agent can combine web search, datetime, a custom booking tool, and a DataMap stock checker -- all declared at construction, all generating correct SWML with proper function definitions, all routed to the right handler.

---

## Contexts and Steps: Priming the State Machine

The contexts/steps system lets you define structured workflows declaratively. Instead of hoping the LLM follows instructions about conversation flow, you mechanically enforce it:

```java
import com.signalwire.sdk.contexts.ContextBuilder;
import com.signalwire.sdk.contexts.Context;
import com.signalwire.sdk.contexts.Step;

ContextBuilder ctx = agent.defineContexts();

Context greeting = ctx.addContext("default");

Step step1 = greeting.addStep("welcome");
step1.setText("Greet the user and ask how you can help.");
step1.setValidSteps(List.of("collect_info"));
step1.setFunctions(List.of("check_hours")); // Only this tool available here

Step step2 = greeting.addStep("collect_info");
step2.setText("Collect the user's name and email.");
step2.setStepCriteria("User has provided both name and email");
step2.setGatherInfo("user_profile", null, null);
step2.addGatherQuestion("name", "What is your name?", "string", false, null, null);
step2.addGatherQuestion("email", "What is your email?", "string", true, null, null);
step2.setValidSteps(List.of("confirm"));

Step step3 = greeting.addStep("confirm");
step3.setText("Confirm the information and say goodbye.");
step3.setFunctions("none"); // No tools -- just confirm and end
```

This generates SWML with a complete contexts/steps structure. The platform enforces navigation rules, restricts which functions are available at each step, collects structured data with typed questions and confirmation, and tracks transitions with trigger attribution in the enriched call_log. The LLM can't skip steps, can't call restricted tools, and can't navigate to disallowed contexts -- not because it was told not to, but because the mechanisms don't exist in its world. This is PGI (Programmatically Governed Inference) in practice.

**Multi-context** agents can define separate conversation modes (e.g., "sales" and "support") with isolated function sets, and use `setValidContexts()` to control switching. Context transitions support 4-mode reset (consolidate x full_reset) with conversation history summarization or archival.

---

## Programmatically Governed Inference (PGI)

The contexts/steps system is the SDK's implementation of a broader architectural discipline: **Programmatically Governed Inference**. PGI starts from a single design rule: *do not tell the AI anything it does not need to know.*

Current AI models are extraordinarily good at language -- understanding loosely phrased human input, mapping intent onto structured actions, and rendering system decisions back into natural speech. They are also inconsistent, non-deterministic, and prone to confident error. These are not bugs that will be fixed in the next model generation. They are properties of probabilistic inference itself. The industry's dominant response -- prompt harder and hope ("prompt and pray") -- treats the model as the brain of the system. PGI rejects this entirely. The model is not the brain. It is a controlled participant inside a deterministic system that was always in charge.

### The Four Layers

PGI is enforced through four layers of constraint, each operating independently. Only the first depends on the model's cooperation. The remaining three are mechanical.

**Layer 1: Semantic Constraints** -- The model receives a prompt describing its role and instructions for how to behave. This is the weakest layer; it depends on probabilistic compliance. PGI treats it as guidance, not enforcement. The remaining layers are the law.

**Layer 2: Schema Constraints** -- At each step, the model sees only the tools registered for that step. Tools belonging to other steps do not exist in its function schema. The model cannot call them, reference them, or reason about them. This is the difference between telling someone not to open a door and removing the door from the building.

**Layer 3: Transition Constraints** -- Each step defines which steps it can transition to. The platform validates every transition against this whitelist. The model cannot skip phases, loop back to completed steps, or jump to unreachable states. The conversational flow is governed by the same deterministic logic as any well-designed state machine.

**Layer 4: Execution Authority** -- When the model calls a tool, it is making a request, not issuing a command. The tool handler accesses authoritative state, applies business logic, and returns both a response for the model to speak and a set of actions for the platform to execute. The model does not update state. The model does not decide what happens next. The platform does.

### PGI in Practice: Blackjack

```java
ContextBuilder ctx = agent.defineContexts();
Context game = ctx.addContext("blackjack");

Step betting = game.addStep("betting");
betting.setFunctions(List.of("place_bet"));
betting.setValidSteps(List.of("playing"));

Step playing = game.addStep("playing");
playing.setFunctions(List.of("hit", "stand", "double_down"));
playing.setValidSteps(List.of("hand_complete"));

Step lost = game.addStep("you_lost");
lost.setFunctions(List.of());
lost.setValidSteps(List.of());
```

During the betting step, the model can only call `place_bet`. It cannot deal cards, draw cards, or resolve hands because those functions are not in its schema. When the tool handler transitions to the playing step, `place_bet` disappears and `hit`, `stand`, `double_down` appear. The model's capabilities change not because it was told to behave differently, but because the available operations were mechanically replaced.

The `you_lost` step has zero functions and zero valid transitions. The game is over. A user can beg, negotiate, or attempt social engineering. None of it works, because the mechanism for continuing does not exist. There is nothing for the model to comply with or resist. The interaction is structurally complete.

The tool handler demonstrates execution authority -- the model has no idea a step change is about to happen:

<!-- snippet: no-compile illustrative handler using reader-supplied game helpers (deckPop/playerHand/calculateHand/formatCard) not defined here -->
```java
agent.defineTool(
    "hit",
    "Draw a card",
    Map.of("type", "object", "properties", Map.of()),
    (args, rawData) -> {
      @SuppressWarnings("unchecked")
      Map<String, Object> game =
          (Map<String, Object>)
              ((Map<String, Object>) rawData.get("global_data")).get("game_state");
      var card = deckPop(game);
      playerHand(game).add(card);
      int score = calculateHand(playerHand(game));

      FunctionResult result =
          new FunctionResult("You drew " + formatCard(card) + ". Your total is " + score + ".");
      result.updateGlobalData(Map.of("game_state", game));

      if (score > 21) {
        result.swmlChangeStep("you_lost");
      }

      return result;
    });
```

The model speaks the result. The platform changes the step. The model's world changes without its participation.

### Data Isolation

PGI extends to how data flows through the system. The model operates on a projection of reality, not the full truth. Authoritative state lives in structured data (`global_data`) that the model sees only in curated subsets. In a blackjack game, the model knows the player's chip count and visible cards. It does not know the deck composition, the dealer's hidden card, or the internal scoring calculations. In an ordering system, the model knows which items have been added. It does not know the internal pricing logic, tax calculations, or inventory state.

The model cannot hallucinate a price it has never seen. It cannot promise availability it has no knowledge of. It can only report what the system tells it to report.

### Why PGI, Not Guardrails

PGI produces a property that makes it fundamentally different from guardrails, output filtering, or any other containment strategy: **the model does not know it is being governed.** It does not know that other tools exist elsewhere in the system. It does not know that a state machine is managing the interaction. It sees its current world -- a prompt, a set of functions, a conversation history -- and operates within it. There is nothing to reason around, nothing to game, nothing to circumvent.

The strongest test of any PGI system: replace the model with a rigid scripted menu ("press 1 for tacos, press 2 for drinks") and the system would still produce correct outcomes. The tool handlers would still validate input, enforce business rules, and manage state. The experience would be worse, but every order would be accurate and every transition would follow the rules. The model makes the interaction natural. The software makes it correct. In a PGI system, those are independent properties.

The SDK's contexts/steps/function restrictions are the primitives that make PGI mechanical rather than aspirational. The developer defines steps, scopes tools to steps, declares transitions, and writes tool handlers that return structured results with platform actions. The platform enforces all of it. The developer brings domain expertise. The SDK provides the governance infrastructure.

---

## Deployment: One `run()` Call

```java
agent.run();
```

That single call starts the built-in HTTP server (JDK `com.sun.net.httpserver.HttpServer` backed by virtual threads -- no external web framework), generates SWML, and routes its own SWAIG and post-prompt callbacks. The port defaults to 3000 and is configurable via the `PORT` env var or the builder's `.port(...)`.

For standalone mode, the SDK provides:
- Kubernetes health (`/health`) and readiness (`/ready`) probes
- SSL/TLS support via `SWML_SSL_ENABLED`, `SWML_SSL_CERT`, `SWML_SSL_KEY`
- Security headers on all authenticated endpoints (`X-Content-Type-Options`, `X-Frame-Options`, `Cache-Control`)
- Proxy-aware public URL detection (`X-Forwarded-Host`, `Forwarded`, `SWML_PROXY_URL_BASE`)

---

## Multi-Agent Hosting

```java
import com.signalwire.sdk.server.AgentServer;

AgentServer server = new AgentServer("0.0.0.0", 3000);
server.register(salesAgent, "/sales");
server.register(supportAgent, "/support");
server.register(triageAgent, "/triage");
server.run();
```

One process, multiple agents, route-based dispatch. Each agent gets its own SWML endpoint and SWAIG callback routing. SIP routing can map usernames to specific agents.

---

## Dynamic Configuration and Multi-Tenancy

<!-- snippet: no-compile illustrative callback using a reader-supplied `loadTenantConfig(tenant)` helper (not defined here) -->
```java
agent.setDynamicConfigCallback((queryParams, bodyParams, headers, ephemeralAgent) -> {
  List<String> tenantHeader = headers.getOrDefault("X-Tenant-ID", List.of("default"));
  String tenant = tenantHeader.get(0);
  Map<String, Object> config = loadTenantConfig(tenant);
  ephemeralAgent.promptAddSection("Company", (String) config.get("company_info"));
  ephemeralAgent.setGlobalData(Map.of("tenant_id", tenant, "tier", config.get("tier")));
  if ("premium".equals(config.get("tier"))) {
    ephemeralAgent.addSkill("advanced_search", Map.of());
  }
});
```

Each inbound request creates an **ephemeral copy** of the agent. The callback (`DynamicConfigCallback.configure(queryParams, bodyParams, headers, agent)`) customizes it per-request -- different prompts, skills, global data, languages, tools. The original agent is unchanged. This enables multi-tenancy from a single deployment: one agent instance serves hundreds of tenants with tailored behavior.

---

## Search System

The `native_vector_search` skill adds document search by querying a **remote** vector-search server over HTTP. (The Java port supports remote mode only; it does not build or read local index files.)

**In agents:**
```java
agent.addSkill("native_vector_search", Map.of(
    "remote_url", "http://localhost:8001/search",
    "index_name", "docs",
    "tool_name", "search_docs",
    "description", "Search product documentation"));
```

The skill POSTs the user's query (and optional `index_name`) to the configured `remote_url` and surfaces the returned results to the agent. Configure the number of results with `count` and add speech `hints` as needed.

---

## Prefab Agents

Production-ready patterns for common use cases:

```java
import com.signalwire.sdk.prefabs.InfoGathererAgent;
import com.signalwire.sdk.prefabs.ReceptionistAgent;

// Collect structured data
var gatherer = new InfoGathererAgent("intake", List.of(
    Map.of("key_name", "name", "question_text", "What is your name?"),
    Map.of("key_name", "issue", "question_text", "Describe your issue", "confirm", true)));

// Route calls to departments
var receptionist = new ReceptionistAgent(
    "front-desk",
    "Welcome to SignalWire.",
    Map.of(
        "Sales", ReceptionistAgent.phoneDepartment("Product inquiries", "+15551234567"),
        "Support", ReceptionistAgent.phoneDepartment("Technical help", "+15559876543")));
```

Five prefabs: **InfoGatherer**, **Survey**, **Receptionist**, **FAQBot**, **Concierge**. Each generates complete SWML with appropriate prompts, tools, and workflows. You instantiate, customize, deploy.

---

## AI Configuration

Everything the platform supports, the SDK exposes as methods:

```java
// LLM tuning
agent.setPromptLlmParams(Map.of("temperature", 0.3, "top_p", 0.9, "barge_confidence", 0.7));

// Multi-language (name, code, voice, speechFillers, functionFillers, engine, model)
agent.addLanguage("Spanish", "es", "google.es-ES-Neural2-A",
    List.of("Un momento..."), List.of("Buscando..."), null, null);

// Speech recognition
agent.addHints(List.of("SignalWire", "SWML", "SWAIG"));
agent.addPronunciation("SignalWire", "Signal Wire", false);

// Vision, thinking, inner dialog
agent.setParams(Map.of("enable_vision", true, "vision_model", "gpt-4o"));
agent.setParams(Map.of("enable_thinking", true, "thinking_model", "o4-mini"));

// Interruption control
agent.setParams(Map.of(
    "barge_match_string", "^(stop|cancel|nevermind)$",
    "barge_min_words", 2,
    "barge_confidence", 0.8));

// Native functions with custom fillers
agent.setNativeFunctions(List.of("check_time", "wait_for_user"));
agent.addInternalFiller("check_time", "en", List.of("Let me check the time..."));

// Call recording (enabled via the builder)
var recordingAgent = AgentBase.builder().name("rec").recordCall(true).build();

// Call flow verbs
agent.addPreAnswerVerb("play", Map.of("url", "ringback.wav"));
agent.addPostAiVerb("hangup", Map.of());
```

Each of these would require understanding and manually constructing the correct SWML JSON structure. The SDK provides named methods with proper defaults.

---

## swaig-test CLI

Test without deploying. Point it at a running agent's HTTP endpoint:

```bash
# List available tools
bin/swaig-test --url http://user:pass@localhost:3000 --list-tools

# Execute a specific tool
bin/swaig-test --url http://user:pass@localhost:3000 --exec get_weather --param city="San Francisco"

# Dump generated SWML for inspection
bin/swaig-test --url http://user:pass@localhost:3000 --dump-swml
```

---

## Authentication

The SDK handles auth automatically:

- **Auto-generated credentials:** If no env vars are set, a `SecureRandom` password is generated (never a weak default) and printed to the console
- **Environment variables:** `SWML_BASIC_AUTH_USER` / `SWML_BASIC_AUTH_PASSWORD`
- **Embedded in URLs:** Webhook URLs include `user:pass@host` automatically
- **Per-function tokens:** Secure functions get `__token=...` query params with expiration, HMAC-SHA256 signed
- **Timing-safe comparison:** Basic auth uses `MessageDigest.isEqual()` to avoid timing attacks

---

## What You'd Have to Build Without the SDK

| Capability | Without SDK | With SDK |
|-----------|-------------|----------|
| SWML document | Hand-craft JSON | Auto-generated from Java |
| Webhook server | Build and deploy separately | Built into the agent process |
| URL routing | Manual HTTP server setup | Automatic route registration |
| Auth tokens | Manual JWT/token system | Auto-generated per call/function |
| Proxy detection | Parse headers yourself | Automatic (ngrok, LB, CDN) |
| Tool schemas | Write JSON Schema by hand | `defineTool()` / `DataMap` |
| Multi-language | Manually construct language arrays | `addLanguage()` one-liner |
| State machine | Manually build contexts JSON | Fluent `defineContexts()` API |
| Structured data collection | Build gather configs by hand | `addGatherQuestion()` chain |
| Search/RAG | Build entire pipeline | `addSkill("native_vector_search")` |
| Multi-agent | Separate deployments + router | `AgentServer` with route registration |
| Dynamic config | Custom middleware | `setDynamicConfigCallback()` |
| Post-call analytics | Parse raw webhook payload | `onSummary()` callback |
| Health checks | Manual endpoints | Built-in `/health` and `/ready` |
| Call recording | Manual SWML verb insertion | `.recordCall(true)` on the builder |
| SSL/TLS | Manual cert configuration | Env var driven |

The SDK turns what would be a multi-file infrastructure project into a single Java class. The SWML is correct by construction. The webhooks route themselves. The auth is automatic. The developer focuses on what the agent should *do*, not how to wire it together.
