/**
 * Kubernetes-Ready Agent.
 *
 * Configured for production Kubernetes deployment with:
 * - /health and /ready endpoints (built-in)
 * - Environment variable configuration (PORT, LOG_LEVEL)
 * - Graceful operation
 *
 * Usage:
 *   PORT=8080 java KubernetesAgent
 */

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

public class KubernetesReadyAgent {

    public static void main(String[] args) throws Exception {
        // PORT env var is automatically respected by AgentBase
        var agent = AgentBase.builder()
                .name("k8s-agent")
                .route("/")
                .build();

        agent.addLanguage("English", "en-US", "en-US-Standard-C");

        agent.promptAddSection("Role",
                "You are a production-ready AI agent running in Kubernetes. " +
                "You can help users with general questions and demonstrate cloud-native deployment.");

        // Health status tool
        agent.defineTool("health_status", "Get the health status of this agent",
                Map.of("type", "object", "properties", Map.of()),
                (toolArgs, raw) -> new FunctionResult(
                        "Agent k8s-agent is healthy, running in Kubernetes."));

        int port = agent.getPort();
        System.out.println("Kubernetes-ready agent starting on port " + port);
        System.out.println("Health:  http://localhost:" + port + "/health");
        System.out.println("Ready:   http://localhost:" + port + "/ready");
        agent.run();
    }
}
