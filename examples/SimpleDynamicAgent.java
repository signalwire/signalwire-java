/**
 * Agent with per-request dynamic configuration.
 *
 * The dynamic config callback receives query params, POST body, and headers,
 * allowing the agent to customize its behavior per-request (e.g., multi-tenancy).
 */

import com.signalwire.agents.agent.AgentBase;
import com.signalwire.agents.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

public class SimpleDynamicAgent {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("dynamic-agent")
                .route("/")
                .port(3000)
                .build();

        agent.promptAddSection("Role",
                "You are a customizable assistant.");

        // Dynamic config callback: customize per request
        agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
            // Customize based on query params
            String tenant = queryParams.getOrDefault("tenant", "default");
            String language = queryParams.getOrDefault("lang", "en");

            configAgent.promptAddSection("Tenant",
                    "You are serving tenant: " + tenant);

            if ("es".equals(language)) {
                configAgent.addLanguage("Spanish", "es", "es-ES-Standard-A");
                configAgent.promptAddSection("Language",
                        "Respond in Spanish.");
            }

            // Customize based on headers
            List<String> customHeader = headers.getOrDefault("X-Custom-Config", List.of());
            if (!customHeader.isEmpty()) {
                configAgent.promptAddSection("Custom",
                        "Custom config: " + customHeader.get(0));
            }
        });

        agent.defineTool("greet", "Greet the user by name",
                Map.of("type", "object",
                        "properties", Map.of(
                                "name", Map.of("type", "string", "description", "User name")
                        ),
                        "required", List.of("name")),
                (toolArgs, raw) -> {
                    String name = (String) toolArgs.get("name");
                    return new FunctionResult("Hello, " + name + "! Welcome.");
                });

        System.out.println("Starting dynamic agent on port 3000...");
        System.out.println("Try: ?tenant=acme&lang=es");
        agent.run();
    }
}
