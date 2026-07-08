# Migrating to SignalWire SDK 2.0

## Dependency Change

Update your `build.gradle`:
```groovy
// Before
implementation 'com.signalwire:signalwire-agents:1.x.x'

// After
implementation 'com.signalwire:signalwire-sdk:2.0.2'
```

Or in `pom.xml`:
```xml
<!-- Before -->
<dependency>
    <groupId>com.signalwire</groupId>
    <artifactId>signalwire-agents</artifactId>
    <version>1.x.x</version>
</dependency>

<!-- After -->
<dependency>
    <groupId>com.signalwire</groupId>
    <artifactId>signalwire-sdk</artifactId>
    <version>2.0.2</version>
</dependency>
```

## Import Changes

<!-- snippet: no-compile before/after migration illustration; the "Before" half references the pre-2.0 `com.signalwire.agents.*` packages that no longer exist -->
```java
// Before
import com.signalwire.agents.AgentBase;
import com.signalwire.agents.core.FunctionResult;
import com.signalwire.agents.rest.SignalWireClient;
import com.signalwire.agents.rest.SignalWireRestError;

SignalWireClient client = new SignalWireClient(projectId, token, spaceUrl);

// After
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;

RestClient client = new RestClient(projectId, token, spaceUrl);
```

## Class Renames

| Before | After |
|--------|-------|
| `SignalWireClient` | `RestClient` |
| `SignalWireRestError` | `RestError` |
| `com.signalwire.agents.*` | `com.signalwire.sdk.*` |

## Quick Migration

Find and replace in your project:
```bash
# Update package imports
find . -name '*.java' -exec sed -i 's/com\.signalwire\.agents/com.signalwire.sdk/g' {} +

# Rename client class
find . -name '*.java' -exec sed -i 's/SignalWireClient/RestClient/g' {} +

# Rename error class
find . -name '*.java' -exec sed -i 's/SignalWireRestError/RestError/g' {} +

# Update build.gradle
sed -i "s/signalwire-agents/signalwire-sdk/g" build.gradle
```

## What Didn't Change

- All method signatures (setPromptText, defineTool, addSkill, etc.)
- All parameter classes
- SWML output format
- RELAY protocol
- REST API paths
- Skills, contexts, DataMap -- all the same
