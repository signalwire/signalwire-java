# SignalWire AI Agents SDK for Java -- Examples

This directory contains example agents demonstrating the key features of the Java SDK.

## Examples

| Example | Description |
|---------|-------------|
| [SimpleAgent.java](SimpleAgent.java) | Minimal agent with one tool and POM-based prompts |
| [SimpleDynamicAgent.java](SimpleDynamicAgent.java) | Agent with per-request dynamic configuration |
| [MultiAgentServer.java](MultiAgentServer.java) | Host multiple agents on different routes |
| [ContextsDemo.java](ContextsDemo.java) | Structured workflows with contexts and steps |
| [DataMapDemo.java](DataMapDemo.java) | Server-side API integration without webhooks |
| [SkillsDemo.java](SkillsDemo.java) | Adding built-in skills (datetime, math, web_search) |
| [SessionState.java](SessionState.java) | Stateful agent with global data and session tracking |
| [CallFlow.java](CallFlow.java) | 5-phase call flow with pre/post answer verbs |
| [RelayDemo.java](RelayDemo.java) | Using the RELAY WebSocket client for real-time call control |
| [RestDemo.java](RestDemo.java) | Using the REST client to manage SignalWire resources |
| [PrefabInfoGatherer.java](PrefabInfoGatherer.java) | Pre-built info-gathering agent with sequential questions |
| [PrefabSurvey.java](PrefabSurvey.java) | Pre-built survey agent with typed questions |

## Running Examples

```bash
export JAVA_HOME=/home/devuser/jdk-21
GRADLE=/home/devuser/gradle/gradle-8.5/bin/gradle

# Build the SDK first
cd /home/devuser/src/signalwire-agents-java
$GRADLE jar

# Run an example (using the SDK jar + dependencies)
CP="build/libs/signalwire-agents-1.0.0.jar:$(find build -name '*.jar' | tr '\n' ':')"
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
