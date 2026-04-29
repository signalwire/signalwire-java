/**
 * Multi-Endpoint Agent.
 *
 * Demonstrates serving an AI agent alongside custom endpoints using
 * AgentServer. The agent is at /swml while static files and a health
 * endpoint are served from the same port.
 */

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.server.AgentServer;
import com.signalwire.sdk.swaig.FunctionResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class MultiEndpointAgent {

    public static void main(String[] args) throws Exception {
        // Create voice AI agent at /swml
        var agent = AgentBase.builder()
                .name("multi-endpoint")
                .route("/swml")
                .build();

        agent.promptAddSection("Role", "You are a helpful voice assistant.");
        agent.promptAddSection("Instructions", "", List.of(
                "Greet callers warmly",
                "Be concise in your responses",
                "Use the available functions when appropriate"
        ));
        agent.addLanguage("English", "en-US", "en-US-Standard-C");

        agent.defineTool("get_time", "Get the current time",
                Map.of("type", "object", "properties", Map.of()),
                (toolArgs, raw) -> new FunctionResult(
                        "The current time is " + LocalTime.now()
                                .format(DateTimeFormatter.ofPattern("hh:mm a"))));

        // Host via AgentServer so we get /health, /ready, and /swml
        var server = new AgentServer(8080);
        server.register(agent);

        System.out.println("Multi-endpoint server starting on port 8080...");
        System.out.println("  SWML:   http://localhost:8080/swml");
        System.out.println("  Health: http://localhost:8080/health");
        System.out.println("  Ready:  http://localhost:8080/ready");
        server.run();
    }
}
