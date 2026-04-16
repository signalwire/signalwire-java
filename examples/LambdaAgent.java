/**
 * AWS Lambda Deployment Example.
 *
 * Demonstrates deploying a SignalWire agent as an AWS Lambda function
 * using {@link com.signalwire.sdk.runtime.lambda.LambdaAgentHandler}.
 *
 * The adapter accepts raw API Gateway (v1 or v2) / Function URL
 * payloads as {@code Map<String, Object>} and returns a response
 * envelope as {@code Map<String, Object>}, so the SDK itself does
 * NOT pull in {@code aws-lambda-java-core} or {@code aws-lambda-java-events}.
 *
 * Packaging for Lambda (in your consumer project):
 *
 *   plugins {
 *       id 'java'
 *       id 'com.github.johnrengelman.shadow' version '8.1.1'
 *   }
 *   dependencies {
 *       implementation 'com.signalwire:signalwire-agents-java:2.0.0'
 *       // Optional — only needed for the typed RequestHandler interface:
 *       compileOnly 'com.amazonaws:aws-lambda-java-core:1.2.3'
 *   }
 *   shadowJar { archiveClassifier.set('') }
 *
 * Upload the resulting shaded JAR to Lambda and set the handler to:
 *
 *   LambdaAgent::handleLambdaRequest
 *
 * (Lambda supports direct reference to a static
 * {@code Map handleLambdaRequest(Map, Context)} method — no
 * {@code RequestHandler} implementation required.)
 *
 * Runtime env vars used automatically:
 *   AWS_LAMBDA_FUNCTION_URL      Set by Lambda Function URL feature.
 *                                Used as the webhook origin.
 *   AWS_LAMBDA_FUNCTION_NAME     Always set by Lambda. Used to
 *                                synthesise a Function URL if the
 *                                explicit one is absent.
 *   AWS_REGION                   Always set by Lambda.
 *   SWML_PROXY_URL_BASE          Override: force a specific webhook
 *                                origin (e.g. a CloudFront domain).
 *   SWML_BASIC_AUTH_USER         Basic auth username.
 *   SWML_BASIC_AUTH_PASSWORD     Basic auth password. Set it in the
 *                                Lambda console, or callers get 401s.
 *
 * For local testing, running this file starts a normal HTTP server
 * on port 3000.
 */

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.runtime.EnvProvider;
import com.signalwire.sdk.runtime.lambda.LambdaAgentHandler;
import com.signalwire.sdk.swaig.FunctionResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class LambdaAgent {

    // Built once on cold start and reused across warm invocations.
    private static final LambdaAgentHandler HANDLER = new LambdaAgentHandler(buildAgent());

    /**
     * Entry point for AWS Lambda. Configure the Lambda handler as:
     *   LambdaAgent::handleLambdaRequest
     *
     * Lambda will inject the API Gateway / Function URL event as the
     * first argument. The second parameter would be the Lambda Context
     * object if you included {@code aws-lambda-java-core} — omitted
     * here to keep the example dependency-free.
     */
    public static Map<String, Object> handleLambdaRequest(Map<String, Object> event) {
        return HANDLER.handle(event).toMap();
    }

    /**
     * Factory method discovered by {@code swaig-test --simulate-serverless
     * lambda}. The CLI invokes this overload (EnvProvider-aware) so the
     * simulated env values are visible to the {@link AgentBase.Builder}
     * at build time (for {@code SWML_BASIC_AUTH_*} and
     * {@code SWML_PROXY_URL_BASE} resolution).
     */
    public static AgentBase buildAgent(EnvProvider env) {
        return configureAgent(AgentBase.builder()
                .name("lambda-agent")
                .route("/")
                .port(3000)
                .envProvider(env)
                .build());
    }

    public static AgentBase buildAgent() {
        var agent = AgentBase.builder()
                .name("lambda-agent")
                .route("/")
                .port(3000)
                .build();
        return configureAgent(agent);
    }

    private static AgentBase configureAgent(AgentBase agent) {
        agent.addLanguage("English", "en-US", "en-US-Standard-C");

        agent.promptAddSection("Role",
                "You are a helpful AI assistant running in AWS Lambda.");
        agent.promptAddSection("Instructions", "", List.of(
                "Greet users warmly and offer help",
                "Use the greet_user function when asked to greet someone",
                "Use the get_time function when asked about the current time"
        ));

        agent.defineTool("greet_user", "Greet a user by name",
                Map.of("type", "object",
                        "properties", Map.of(
                                "name", Map.of("type", "string",
                                        "description", "User name")
                        )),
                (toolArgs, raw) -> {
                    String name = (String) toolArgs.getOrDefault("name", "friend");
                    return new FunctionResult("Hello " + name + "! I'm running in AWS Lambda!");
                });

        agent.defineTool("get_time", "Get the current time",
                Map.of("type", "object", "properties", Map.of()),
                (toolArgs, raw) -> new FunctionResult(
                        "Current time: " + LocalDateTime.now()
                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));

        return agent;
    }

    // Local-testing entry point.
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Lambda agent (local testing) on port 3000...");
        buildAgent().run();
    }
}
