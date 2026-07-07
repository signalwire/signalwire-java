# SignalWire AI Agents - Cloud Functions Deployment Guide

This guide covers deploying SignalWire AI Agents to Google Cloud Functions and Azure Functions.

## Overview

SignalWire AI Agents now support deployment to major cloud function platforms:

- **Google Cloud Functions** - Serverless compute platform on Google Cloud
- **Azure Functions** - Serverless compute service on Microsoft Azure
- **AWS Lambda** - Already supported (see existing documentation)

## Google Cloud Functions

### Environment Detection

The agent automatically detects Google Cloud Functions environment using these variables:
- `FUNCTION_TARGET` - The function entry point
- `K_SERVICE` - Knative service name (Cloud Run/Functions)
- `GOOGLE_CLOUD_PROJECT` - Google Cloud project ID

### Deployment Steps

1. **Create your Cloud Function handler.** The SDK provides
   `ServerlessAdapter.handleGcf(...)`, which dispatches a request through the
   agent and returns a `(status, headers, body)` envelope:
```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.runtime.ServerlessAdapter;
import com.signalwire.sdk.swaig.FunctionResult;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AgentFunction implements HttpFunction {

    // Built once on cold start and reused across warm invocations.
    private static final AgentBase AGENT = buildAgent();

    private static AgentBase buildAgent() {
        var agent = AgentBase.builder().name("my-agent").route("/").build();
        agent.promptAddSection("Role", "You are a helpful assistant.");
        return agent;
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        // Collect headers into a plain map for the adapter.
        Map<String, String> headers = new LinkedHashMap<>();
        request.getHeaders().forEach((k, v) -> headers.put(k, String.join(",", v)));

        String body;
        try (BufferedReader reader = request.getReader()) {
            body = reader.lines().collect(Collectors.joining("\n"));
        }

        ServerlessAdapter.Response result = ServerlessAdapter.handleGcf(
            AGENT, request.getMethod(), request.getPath(), headers, body);

        response.setStatusCode(result.status());
        result.headers().forEach(response::appendHeader);
        try (PrintWriter writer = response.getWriter()) {
            writer.write(result.body());
        }
    }
}
```

2. **Declare the dependency** (`build.gradle`):
```groovy
dependencies {
    implementation 'com.signalwire:signalwire-sdk:2.0.2'
    // GCF Java runtime API (provided at deploy time):
    compileOnly 'com.google.cloud.functions:functions-framework-api:1.1.0'
}
```

3. **Deploy using gcloud** (build a shaded/fat JAR first):
```bash
gcloud functions deploy my-agent \
    --runtime java21 \
    --trigger-http \
    --entry-point com.example.AgentFunction \
    --allow-unauthenticated
```

### Environment Variables

Set these environment variables for your function:

```bash
# SignalWire credentials
export SIGNALWIRE_PROJECT_ID="your-project-id"
export SIGNALWIRE_TOKEN="your-token"

# Agent configuration
export AGENT_USERNAME="your-username"
export AGENT_PASSWORD="your-password"

# Optional: Custom region/project settings
export FUNCTION_REGION="us-central1"
export GOOGLE_CLOUD_PROJECT="your-project-id"
```

### URL Format

Google Cloud Functions URLs follow this pattern:
```
https://{region}-{project-id}.cloudfunctions.net/{function-name}
```

With authentication:
```
https://username:password@{region}-{project-id}.cloudfunctions.net/{function-name}
```

## Azure Functions

### Environment Detection

The agent automatically detects Azure Functions environment using these variables:
- `AZURE_FUNCTIONS_ENVIRONMENT` - Azure Functions runtime environment
- `FUNCTIONS_WORKER_RUNTIME` - Runtime language (python, node, etc.)
- `AzureWebJobsStorage` - Azure storage connection string

### Deployment Steps

1. **Create your function app structure**:
```
my-agent-function/
├── __init__.py
├── function.json
└── requirements.txt
```

2. **Create your Azure Function handler.** Azure's Java runtime hands you an
   `HttpRequestMessage`; forward its method/path/headers/body through the same
   `ServerlessAdapter.handleGcf(...)` dispatch (the SDK's serverless dispatch is
   platform-neutral):
```java
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.runtime.ServerlessAdapter;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class AgentFunction {

    // Built once on cold start and reused across warm invocations.
    private static final AgentBase AGENT = buildAgent();

    private static AgentBase buildAgent() {
        var agent = AgentBase.builder().name("my-agent").route("/").build();
        agent.promptAddSection("Role", "You are a helpful assistant.");
        return agent;
    }

    @FunctionName("agent")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req",
                    methods = {HttpMethod.GET, HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS)
                    HttpRequestMessage<Optional<String>> request,
            ExecutionContext context) {

        Map<String, String> headers = new LinkedHashMap<>(request.getHeaders());
        String body = request.getBody().orElse(null);

        ServerlessAdapter.Response result = ServerlessAdapter.handleGcf(
            AGENT, request.getHttpMethod().name(), request.getUri().getPath(), headers, body);

        var builder = request.createResponseBuilder(HttpStatus.valueOf(result.status()));
        result.headers().forEach(builder::header);
        return builder.body(result.body()).build();
    }
}
```

3. **Create `function.json`** (only needed for the script-based model; the
   annotation model above generates it during the Maven/Gradle build):
```json
{
  "scriptFile": "__init__.py",
  "bindings": [
    {
      "authLevel": "anonymous",
      "type": "httpTrigger",
      "direction": "in",
      "name": "req",
      "methods": ["get", "post"]
    },
    {
      "type": "http",
      "direction": "out",
      "name": "$return"
    }
  ]
}
```

4. **Declare the dependency** (`build.gradle`):
```groovy
dependencies {
    implementation 'com.signalwire:signalwire-sdk:2.0.2'
    // Azure Functions Java library (provided by the runtime):
    compileOnly 'com.microsoft.azure.functions:azure-functions-java-library:3.1.0'
}
```

5. **Deploy using Azure CLI**:
```bash
# Create function app
az functionapp create \
    --resource-group myResourceGroup \
    --consumption-plan-location westus \
    --runtime java \
    --runtime-version 21 \
    --functions-version 4 \
    --name my-agent-function \
    --storage-account mystorageaccount

# Deploy code (Maven plugin, or the Gradle azurefunctions plugin)
mvn azure-functions:deploy
```

### Environment Variables

Set these in your Azure Function App settings:

```bash
# SignalWire credentials
SIGNALWIRE_PROJECT_ID="your-project-id"
SIGNALWIRE_TOKEN="your-token"

# Agent configuration
AGENT_USERNAME="your-username"
AGENT_PASSWORD="your-password"

# Azure-specific (usually auto-set)
AZURE_FUNCTIONS_ENVIRONMENT="Development"
WEBSITE_SITE_NAME="my-agent-function"
```

### URL Format

Azure Functions URLs follow this pattern:
```
https://{function-app-name}.azurewebsites.net/api/{function-name}
```

With authentication:
```
https://username:password@{function-app-name}.azurewebsites.net/api/{function-name}
```

## Authentication

Both platforms support HTTP Basic Authentication:

### Automatic Authentication
The agent automatically validates credentials in cloud function environments. Set
them on the builder, or via the `SWML_BASIC_AUTH_USER` / `SWML_BASIC_AUTH_PASSWORD`
environment variables:

```java
var agent = AgentBase.builder()
    .name("my-agent")
    .authUser("your-username")
    .authPassword("your-password")
    .build();
```

### Authentication Flow
1. Client sends request with `Authorization: Basic <credentials>` header
2. Agent validates credentials against configured username/password
3. If invalid, returns 401 with `WWW-Authenticate` header
4. If valid, processes the request normally

## Testing

### SignalWire Agent Testing Tool

The SignalWire AI Agents SDK includes a testing tool (`swaig-test`) that can simulate cloud function environments for comprehensive testing before deployment.

#### Cloud Function Environment Simulation

**Google Cloud Functions:**
```bash
# Test SWML generation in GCP environment
swaig-test examples/my_agent.py --simulate-serverless cloud_function --gcp-project my-project --dump-swml

# Test function execution
swaig-test examples/my_agent.py --simulate-serverless cloud_function --gcp-project my-project --exec my_function --param value

# With custom region and service
swaig-test examples/my_agent.py --simulate-serverless cloud_function \
  --gcp-project my-project \
  --gcp-region us-west1 \
  --gcp-service my-service \
  --dump-swml
```

**Azure Functions:**
```bash
# Test SWML generation in Azure environment
swaig-test examples/my_agent.py --simulate-serverless azure_function --dump-swml

# Test function execution
swaig-test examples/my_agent.py --simulate-serverless azure_function --exec my_function --param value

# With custom environment and URL
swaig-test examples/my_agent.py --simulate-serverless azure_function \
  --azure-env Production \
  --azure-function-url https://myapp.azurewebsites.net/api/myfunction \
  --dump-swml
```

#### Environment Variable Testing

Test with custom environment variables:
```bash
# Set individual environment variables
swaig-test examples/my_agent.py --simulate-serverless cloud_function \
  --env GOOGLE_CLOUD_PROJECT=my-project \
  --env DEBUG=1 \
  --exec my_function

# Load from environment file
swaig-test examples/my_agent.py --simulate-serverless azure_function \
  --env-file production.env \
  --dump-swml
```

#### Authentication Testing

Test authentication in cloud function environments:
```bash
# Test with authentication (uses agent's configured credentials)
swaig-test examples/my_agent.py --simulate-serverless cloud_function \
  --gcp-project my-project \
  --dump-swml --verbose

# The tool automatically tests:
# - Basic auth credential embedding in URLs
# - Authentication challenge responses
# - Platform-specific auth handling
```

#### URL Generation Testing

Verify that URLs are generated correctly for each platform:
```bash
# Check URL generation with verbose output
swaig-test examples/my_agent.py --simulate-serverless cloud_function \
  --gcp-project my-project \
  --dump-swml --verbose

# Extract webhook URLs from SWML
swaig-test examples/my_agent.py --simulate-serverless azure_function \
  --dump-swml --raw | jq '.sections.main[1].ai.SWAIG.functions[].web_hook_url'
```

#### Available Testing Options

**Platform Selection:**
- `--simulate-serverless cloud_function` - Google Cloud Functions
- `--simulate-serverless azure_function` - Azure Functions  
- `--simulate-serverless lambda` - AWS Lambda
- `--simulate-serverless cgi` - CGI environment

**Google Cloud Platform Options:**
- `--gcp-project PROJECT_ID` - Set Google Cloud project ID
- `--gcp-region REGION` - Set Google Cloud region (default: us-central1)
- `--gcp-service SERVICE` - Set service name
- `--gcp-function-url URL` - Override function URL

**Azure Functions Options:**
- `--azure-env ENVIRONMENT` - Set Azure environment (default: Development)
- `--azure-function-url URL` - Override Azure Function URL

**Environment Variables:**
- `--env KEY=value` - Set individual environment variables
- `--env-file FILE` - Load environment variables from file

**Output Options:**
- `--dump-swml` - Generate and display SWML document
- `--verbose` - Show detailed information
- `--raw` - Output raw JSON (useful for piping to jq)

#### Complete Testing Workflow

```bash
# 1. List available agents and tools
swaig-test examples/my_agent.py --list-agents
swaig-test examples/my_agent.py --list-tools

# 2. Test SWML generation for each platform
swaig-test examples/my_agent.py --simulate-serverless cloud_function --gcp-project test-project --dump-swml
swaig-test examples/my_agent.py --simulate-serverless azure_function --dump-swml

# 3. Test specific function execution
swaig-test examples/my_agent.py --simulate-serverless cloud_function --gcp-project test-project --exec search_knowledge --query "test"

# 4. Test with production-like environment
swaig-test examples/my_agent.py --simulate-serverless azure_function --env-file production.env --exec my_function --param value

# 5. Verify authentication and URL generation
swaig-test examples/my_agent.py --simulate-serverless cloud_function --gcp-project prod-project --dump-swml --verbose
```

### Local Testing

**Google Cloud Functions:**
```bash
# Install Functions Framework
pip install functions-framework

# Run locally
functions-framework --target=agent_handler --debug
```

**Azure Functions:**
```bash
# Install Azure Functions Core Tools
npm install -g azure-functions-core-tools@4

# Run locally
func start
```

### Testing Authentication

```bash
# Test without auth (should return 401)
curl https://your-function-url/

# Test with valid auth
curl -u username:password https://your-function-url/

# Test SWAIG function call
curl -u username:password \
  -H "Content-Type: application/json" \
  -d '{"call_id": "test", "argument": {"parsed": [{"param": "value"}]}}' \
  https://your-function-url/your_function_name
```

## Best Practices

### Performance
- Use connection pooling for database connections
- Implement proper caching strategies
- Minimize cold start times with smaller deployment packages

### Security
- Always use HTTPS endpoints
- Implement proper authentication
- Use environment variables for sensitive data
- Consider using cloud-native secret management

### Monitoring
- Enable cloud platform logging
- Monitor function execution times
- Set up alerts for errors and timeouts
- Use distributed tracing for complex workflows

### Cost Optimization
- Right-size memory allocation
- Implement proper timeout settings
- Use reserved capacity for predictable workloads
- Monitor and optimize function execution patterns

## Troubleshooting

### Common Issues

**Environment Detection:**
```java
// Check detected mode
import com.signalwire.sdk.runtime.ExecutionMode;

System.out.println("Detected mode: " + ExecutionMode.getExecutionMode());
// One of: "cgi", "lambda", "google_cloud_function", "azure_function", "server"
```

**URL Generation:**
```java
// Check the resolved webhook base URL (Lambda Function URL / SWML_PROXY_URL_BASE)
import com.signalwire.sdk.runtime.LambdaUrlResolver;

var resolver = new LambdaUrlResolver();
System.out.println("Base URL: " + resolver.resolveBaseUrl());
```

**Authentication Issues:**
- Verify username/password are set correctly
- Check that Authorization header is being sent
- Ensure credentials match exactly (case-sensitive)

### Debugging

Enable debug logging (the SDK's `Logger` reads `SIGNALWIRE_LOG_LEVEL`):
```bash
export SIGNALWIRE_LOG_LEVEL=debug
```

Check environment variables:
```java
System.getenv().forEach((key, value) -> {
    if (key.contains("FUNCTION") || key.contains("AZURE") || key.contains("GOOGLE")) {
        System.out.println(key + ": " + value);
    }
});
```

## Migration from Other Platforms

### From AWS Lambda
- Update environment variable names
- Modify request/response handling if needed
- Update deployment scripts

### From Traditional Servers
- Add cloud function entry point
- Configure environment variables
- Update URL generation logic
- Test authentication flow

## Examples

See `examples/LambdaAgent.java` for a complete AWS Lambda deployment example (uses `com.signalwire.sdk.runtime.lambda.LambdaAgentHandler`).

## Support

For issues specific to cloud function deployment:
1. Check the troubleshooting section above
2. Verify environment variables are set correctly
3. Test authentication flow manually
4. Check cloud platform logs for detailed error messages
5. Refer to platform-specific documentation for deployment issues 