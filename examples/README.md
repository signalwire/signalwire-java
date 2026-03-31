# SignalWire AI Agents SDK for Java -- Examples

This directory contains example agents demonstrating the key features of the Java SDK.

## Examples

### Core Agent Patterns

| Example | Description |
|---------|-------------|
| [SimpleAgent.java](SimpleAgent.java) | Minimal agent with one tool and POM-based prompts |
| [SimpleStatic.java](SimpleStatic.java) | Static agent with voice, params, hints, and global data |
| [SimpleDynamicAgent.java](SimpleDynamicAgent.java) | Agent with per-request dynamic configuration |
| [SimpleDynamicEnhanced.java](SimpleDynamicEnhanced.java) | Enhanced dynamic config: VIP, department, customer ID, language |
| [ComprehensiveDynamic.java](ComprehensiveDynamic.java) | Multi-tenant dynamic config (tier, industry, A/B testing) |
| [DeclarativeAgent.java](DeclarativeAgent.java) | Declarative prompt sections with tools and post-prompt |
| [CustomPath.java](CustomPath.java) | Agent on a non-root path (/chat) with query-param personalization |
| [MultiAgentServer.java](MultiAgentServer.java) | Host multiple agents on different routes |
| [MultiEndpoint.java](MultiEndpoint.java) | Agent alongside /health, /ready using AgentServer |

### Contexts, Steps, and Gather Info

| Example | Description |
|---------|-------------|
| [ContextsDemo.java](ContextsDemo.java) | Structured workflows with contexts and steps |
| [GatherInfo.java](GatherInfo.java) | Gather-info mode for structured data collection (patient intake) |

### DataMap (Server-Side Tools)

| Example | Description |
|---------|-------------|
| [DataMapDemo.java](DataMapDemo.java) | Server-side API integration without webhooks |
| [AdvancedDataMap.java](AdvancedDataMap.java) | Expressions, foreach, fallback chains, and error keys |

### Skills

| Example | Description |
|---------|-------------|
| [SkillsDemo.java](SkillsDemo.java) | Adding built-in skills (datetime, math, web_search, joke) |
| [JokeSkillDemo.java](JokeSkillDemo.java) | Joke skill via the modular skills system with DataMap |
| [WebSearchAgent.java](WebSearchAgent.java) | Web search skill with Google Custom Search |
| [WebSearchMultiInstance.java](WebSearchMultiInstance.java) | Multiple web search instances (general, news, quick) |
| [WikipediaAgent.java](WikipediaAgent.java) | Wikipedia search skill for factual lookups |
| [DatasphereAgent.java](DatasphereAgent.java) | Multiple DataSphere skill instances for separate knowledge bases |
| [DatasphereMultiInstance.java](DatasphereMultiInstance.java) | DataSphere multi-instance with custom tool names |
| [DatasphereServerlessEnv.java](DatasphereServerlessEnv.java) | DataSphere serverless from environment variables |
| [DatasphereWebhookEnv.java](DatasphereWebhookEnv.java) | Webhook-based DataSphere from environment variables |
| [McpGateway.java](McpGateway.java) | MCP Gateway skill for Model Context Protocol tools |

### SWAIG Features and FunctionResult Actions

| Example | Description |
|---------|-------------|
| [SwaigFeatures.java](SwaigFeatures.java) | Tool parameter patterns, hints, and post-prompt |
| [JokeAgent.java](JokeAgent.java) | Raw data_map configuration (API Ninjas jokes) |
| [RecordCallExample.java](RecordCallExample.java) | Start/stop background call recording via FunctionResult |
| [RoomAndSip.java](RoomAndSip.java) | Join rooms, SIP REFER transfers, and conferences |
| [TapExample.java](TapExample.java) | WebSocket/RTP tap for call monitoring and compliance |

### Call Flow and AI Configuration

| Example | Description |
|---------|-------------|
| [CallFlow.java](CallFlow.java) | 5-phase call flow with pre/post answer verbs |
| [LlmParams.java](LlmParams.java) | LLM parameter tuning (precise, creative, customer service) |
| [SessionState.java](SessionState.java) | Stateful agent with global data and session tracking |

### Prefab Agents

| Example | Description |
|---------|-------------|
| [DynamicInfoGatherer.java](DynamicInfoGatherer.java) | Dynamic InfoGatherer with callback-based question selection |
| [PrefabInfoGatherer.java](PrefabInfoGatherer.java) | Pre-built info-gathering agent with sequential questions |
| [PrefabSurvey.java](PrefabSurvey.java) | Pre-built survey agent with typed questions |
| [ConciergeExample.java](ConciergeExample.java) | Pre-built concierge agent for venues with amenities |
| [ReceptionistExample.java](ReceptionistExample.java) | Pre-built receptionist with department routing |
| [FaqBotExample.java](FaqBotExample.java) | Pre-built FAQ bot with keyword-based lookup |

### SWML Service (No AI)

| Example | Description |
|---------|-------------|
| [AutoVivifiedExample.java](AutoVivifiedExample.java) | Auto-vivified verb methods on SWMLService |
| [SwmlService.java](SwmlService.java) | Raw SWML documents: voicemail, IVR, transfer, recording |
| [DynamicSwmlService.java](DynamicSwmlService.java) | Dynamic SWML generation based on request data |
| [SwmlServiceRouting.java](SwmlServiceRouting.java) | Multiple SWML sections with path-based routing |

### Deployment

| Example | Description |
|---------|-------------|
| [KubernetesAgent.java](KubernetesAgent.java) | Production K8s agent with /health, /ready endpoints |
| [LambdaAgent.java](LambdaAgent.java) | Agent designed for AWS Lambda serverless deployment |

### RELAY and REST Clients

| Example | Description |
|---------|-------------|
| [RelayDemo.java](RelayDemo.java) | Using the RELAY WebSocket client for real-time call control |
| [RestDemo.java](RestDemo.java) | Using the REST client to manage SignalWire resources |

## Running Examples

```bash
export JAVA_HOME=/home/devuser/jdk-21
GRADLE=/home/devuser/gradle/gradle-8.5/bin/gradle

# Build the SDK first
cd /home/devuser/src/signalwire-agents-java
$GRADLE jar

# Run an example (using the SDK jar + dependencies)
CP="build/libs/signalwire-agents-1.1.0.jar:$(find build -name '*.jar' | tr '\n' ':')"
$JAVA_HOME/bin/javac -cp "$CP" examples/SimpleAgent.java
$JAVA_HOME/bin/java -cp "$CP:examples" SimpleAgent
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
