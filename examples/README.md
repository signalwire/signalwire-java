# SignalWire AI Agents SDK for Java -- Examples

This directory contains example agents demonstrating the key features of the Java SDK.

## Examples

### Core Agent Patterns

| Example | Description |
|---------|-------------|
| [SimpleAgent.java](SimpleAgent.java) | Minimal agent with one tool and POM-based prompts |
| [SimpleStaticAgent.java](SimpleStaticAgent.java) | Static agent with voice, params, hints, and global data |
| [SimpleDynamicAgent.java](SimpleDynamicAgent.java) | Agent with per-request dynamic configuration |
| [SimpleDynamicEnhanced.java](SimpleDynamicEnhanced.java) | Enhanced dynamic config: VIP, department, customer ID, language |
| [ComprehensiveDynamicAgent.java](ComprehensiveDynamicAgent.java) | Multi-tenant dynamic config (tier, industry, A/B testing) |
| [DeclarativeAgent.java](DeclarativeAgent.java) | Declarative prompt sections with tools and post-prompt |
| [CustomPathAgent.java](CustomPathAgent.java) | Agent on a non-root path (/chat) with query-param personalization |
| [MultiAgentServer.java](MultiAgentServer.java) | Host multiple agents on different routes |
| [MultiEndpointAgent.java](MultiEndpointAgent.java) | Agent alongside /health, /ready using AgentServer |

### Contexts, Steps, and Gather Info

| Example | Description |
|---------|-------------|
| [ContextsDemo.java](ContextsDemo.java) | Structured workflows with contexts and steps |
| [GatherInfoDemo.java](GatherInfoDemo.java) | Gather-info mode for structured data collection (patient intake) |

### DataMap (Server-Side Tools)

| Example | Description |
|---------|-------------|
| [DataMapDemo.java](DataMapDemo.java) | Server-side API integration without webhooks |
| [AdvancedDatamapDemo.java](AdvancedDatamapDemo.java) | Expressions, foreach, fallback chains, and error keys |

### Skills

| Example | Description |
|---------|-------------|
| [SkillsDemo.java](SkillsDemo.java) | Adding built-in skills (datetime, math, web_search, joke) |
| [JokeSkillDemo.java](JokeSkillDemo.java) | Joke skill via the modular skills system with DataMap |
| [WebSearchAgent.java](WebSearchAgent.java) | Web search skill with Google Custom Search |
| [WebSearchMultiInstanceDemo.java](WebSearchMultiInstanceDemo.java) | Multiple web search instances (general, news, quick) |
| [WikipediaDemo.java](WikipediaDemo.java) | Wikipedia search skill for factual lookups |
| [DatasphereAgent.java](DatasphereAgent.java) | Multiple DataSphere skill instances for separate knowledge bases |
| [DatasphereMultiInstanceDemo.java](DatasphereMultiInstanceDemo.java) | DataSphere multi-instance with custom tool names |
| [DatasphereServerlessEnv.java](DatasphereServerlessEnv.java) | DataSphere serverless from environment variables |
| [DatasphereWebhookEnvDemo.java](DatasphereWebhookEnvDemo.java) | Webhook-based DataSphere from environment variables |
| [McpGatewayDemo.java](McpGatewayDemo.java) | MCP Gateway skill for Model Context Protocol tools |

### SWAIG Features and FunctionResult Actions

| Example | Description |
|---------|-------------|
| [SwaigFeaturesAgent.java](SwaigFeaturesAgent.java) | Tool parameter patterns, hints, and post-prompt |
| [JokeAgent.java](JokeAgent.java) | Raw data_map configuration (API Ninjas jokes) |
| [RecordCallExample.java](RecordCallExample.java) | Start/stop background call recording via FunctionResult |
| [RoomAndSipExample.java](RoomAndSipExample.java) | Join rooms, SIP REFER transfers, and conferences |
| [TapExample.java](TapExample.java) | WebSocket/RTP tap for call monitoring and compliance |

### Call Flow and AI Configuration

| Example | Description |
|---------|-------------|
| [CallFlowAndActionsDemo.java](CallFlowAndActionsDemo.java) | 5-phase call flow with pre/post answer verbs |
| [LlmParamsDemo.java](LlmParamsDemo.java) | LLM parameter tuning (precise, creative, customer service) |
| [SessionAndStateDemo.java](SessionAndStateDemo.java) | Stateful agent with global data and session tracking |

### Prefab Agents

| Example | Description |
|---------|-------------|
| [DynamicInfoGathererExample.java](DynamicInfoGathererExample.java) | Dynamic InfoGatherer with callback-based question selection |
| [InfoGathererExample.java](InfoGathererExample.java) | Pre-built info-gathering agent with sequential questions |
| [SurveyAgentExample.java](SurveyAgentExample.java) | Pre-built survey agent with typed questions |
| [ConciergeAgentExample.java](ConciergeAgentExample.java) | Pre-built concierge agent for venues with amenities |
| [ReceptionistAgentExample.java](ReceptionistAgentExample.java) | Pre-built receptionist with department routing |
| [FaqBotAgent.java](FaqBotAgent.java) | Pre-built FAQ bot with keyword-based lookup |

### SWML Service (No AI)

| Example | Description |
|---------|-------------|
| [AutoVivifiedExample.java](AutoVivifiedExample.java) | Auto-vivified verb methods on SWMLService |
| [SwmlServiceExample.java](SwmlServiceExample.java) | Raw SWML documents: voicemail, IVR, transfer, recording |
| [DynamicSwmlService.java](DynamicSwmlService.java) | Dynamic SWML generation based on request data |
| [SwmlServiceRoutingExample.java](SwmlServiceRoutingExample.java) | Multiple SWML sections with path-based routing |

### Deployment

| Example | Description |
|---------|-------------|
| [KubernetesReadyAgent.java](KubernetesReadyAgent.java) | Production K8s agent with /health, /ready endpoints |
| [LambdaAgent.java](LambdaAgent.java) | Agent deployed on AWS Lambda via `LambdaAgentHandler` adapter |

### RELAY and REST Clients

| Example | Description |
|---------|-------------|
| [RelayDemo.java](RelayDemo.java) | Using the RELAY WebSocket client for real-time call control |
| [RestDemo.java](RestDemo.java) | Using the REST client to manage SignalWire resources |

## Running Examples

```bash
# JAVA_HOME must point at a JDK 21+ install (the scripts auto-resolve it if unset).
# Build the SDK jar first, from the repo root, using the Gradle wrapper:
./gradlew jar

# Run an example (using the SDK jar + dependencies on the classpath)
CP="$(find build/libs -name 'signalwire-sdk-*.jar' | grep -Ev -- '-(sources|javadoc)\.jar$' | head -1):$(find build -name '*.jar' | tr '\n' ':')"
javac -cp "$CP" examples/SimpleAgent.java
java -cp "$CP:examples" SimpleAgent
```

## Environment Variables

All examples respect these environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `PORT` | HTTP server port | 3000 |
| `SWML_BASIC_AUTH_USER` | Override auth username | agent name |
| `SWML_BASIC_AUTH_PASSWORD` | Override auth password | auto-generated |
| `SWML_PROXY_URL_BASE` | Proxy URL for webhooks | auto-detected |
| `SIGNALWIRE_PROJECT_ID` | Project ID (RELAY/REST) | - |
| `SIGNALWIRE_API_TOKEN` | API token (RELAY/REST) | - |
| `SIGNALWIRE_SPACE` | Space hostname (RELAY/REST) | - |

Some examples require additional environment variables:

| Variable | Used By | Description |
|----------|---------|-------------|
| `API_NINJAS_KEY` | JokeAgent | API Ninjas API key |
| `GOOGLE_SEARCH_API_KEY` | WebSearchAgent | Google Custom Search API key |
| `GOOGLE_SEARCH_ENGINE_ID` | WebSearchAgent | Google Custom Search Engine ID |
| `MCP_GATEWAY_URL` | McpGateway | MCP gateway service URL |
| `MCP_GATEWAY_AUTH_USER` | McpGateway | MCP gateway basic auth user |
| `MCP_GATEWAY_AUTH_PASSWORD` | McpGateway | MCP gateway basic auth password |

## Deploying to AWS Lambda

The SDK ships a `LambdaAgentHandler` that translates API Gateway (v1 or
v2) and Lambda Function URL events into the same dispatch logic the
in-process HTTP server uses. See [LambdaAgent.java](LambdaAgent.java).

Package a shaded JAR (e.g. with the Gradle Shadow plugin) that contains
the SDK and its transitive dependencies, upload it to Lambda, and set
the handler to `LambdaAgent::handleLambdaRequest`.

Lambda auto-populates the environment variables the SDK reads
(`AWS_LAMBDA_FUNCTION_URL`, `AWS_LAMBDA_FUNCTION_NAME`, `AWS_REGION`).
Set `SWML_BASIC_AUTH_USER` / `SWML_BASIC_AUTH_PASSWORD` in the Lambda
configuration so external callers can authenticate. Optionally set
`SWML_PROXY_URL_BASE` if you front the Lambda with a custom domain.
