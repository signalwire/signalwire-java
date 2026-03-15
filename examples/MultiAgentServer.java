/**
 * Host multiple agents on different routes using AgentServer.
 *
 * Routes:
 *   /sales     -> Sales agent
 *   /support   -> Support agent
 *   /health    -> Health check
 */

import com.signalwire.agents.agent.AgentBase;
import com.signalwire.agents.server.AgentServer;
import com.signalwire.agents.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

public class MultiAgentServer {

    public static void main(String[] args) throws Exception {
        // Create Sales agent
        var salesAgent = AgentBase.builder()
                .name("sales-agent")
                .route("/sales")
                .build();
        salesAgent.promptAddSection("Role",
                "You are a sales representative for Acme Corp.");
        salesAgent.promptAddSection("Instructions", "", List.of(
                "Help customers learn about our products",
                "Answer pricing questions",
                "Collect contact information for follow-up"
        ));
        salesAgent.defineTool("get_pricing", "Get product pricing",
                Map.of("type", "object", "properties", Map.of(
                        "product", Map.of("type", "string", "description", "Product name")
                )),
                (toolArgs, raw) -> {
                    String product = (String) toolArgs.getOrDefault("product", "unknown");
                    return new FunctionResult("The price for " + product + " is $99/month.");
                });

        // Create Support agent
        var supportAgent = AgentBase.builder()
                .name("support-agent")
                .route("/support")
                .build();
        supportAgent.promptAddSection("Role",
                "You are a technical support specialist.");
        supportAgent.promptAddSection("Instructions", "", List.of(
                "Help users troubleshoot technical issues",
                "Escalate complex issues to a human agent",
                "Be patient and thorough"
        ));
        supportAgent.defineTool("create_ticket", "Create a support ticket",
                Map.of("type", "object", "properties", Map.of(
                        "subject", Map.of("type", "string", "description", "Ticket subject"),
                        "description", Map.of("type", "string", "description", "Issue description")
                )),
                (toolArgs, raw) -> new FunctionResult(
                        "Ticket created: " + toolArgs.get("subject")));

        // Create and start the server
        var server = new AgentServer(3000);
        server.register(salesAgent);
        server.register(supportAgent);

        System.out.println("Starting multi-agent server on port 3000...");
        System.out.println("  Sales:   http://localhost:3000/sales");
        System.out.println("  Support: http://localhost:3000/support");
        server.run();
    }
}
