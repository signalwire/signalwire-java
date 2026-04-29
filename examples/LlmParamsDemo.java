/**
 * LLM Parameter Customization Demo.
 *
 * Shows how to use setPromptLlmParams() and setPostPromptLlmParams()
 * to create agents with different response characteristics:
 * - Precise (low temperature, hard to interrupt)
 * - Creative (high temperature, easy to interrupt)
 * - Customer Service (balanced)
 *
 * Usage: java LlmParams [precise|creative|support]
 */

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

public class LlmParamsDemo {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0].toLowerCase() : "support";

        switch (mode) {
            case "precise" -> startPrecise();
            case "creative" -> startCreative();
            default -> startSupport();
        }
    }

    static void startPrecise() throws Exception {
        var agent = AgentBase.builder()
                .name("precise-assistant")
                .route("/precise")
                .port(3000)
                .build();

        agent.promptAddSection("Role", "You are a precise technical assistant.");
        agent.promptAddSection("Instructions", "", List.of(
                "Provide accurate, factual information",
                "Be concise and direct",
                "Avoid speculation or guessing"
        ));

        agent.setPromptLlmParams(Map.of(
                "temperature", 0.2,
                "top_p", 0.85,
                "barge_confidence", 0.8,
                "frequency_penalty", 0.1
        ));

        agent.setPostPrompt("Provide a brief technical summary of the key points.");
        agent.setPostPromptLlmParams(Map.of("temperature", 0.1));

        agent.defineTool("get_system_info", "Get technical system information",
                Map.of("type", "object", "properties", Map.of()),
                (toolArgs, raw) -> new FunctionResult(
                        "System Status: CPU 45%, Memory 8GB, Disk 200GB free, Uptime 12 days"));

        System.out.println("Starting Precise Assistant (low temperature)...");
        agent.run();
    }

    static void startCreative() throws Exception {
        var agent = AgentBase.builder()
                .name("creative-assistant")
                .route("/creative")
                .port(3000)
                .build();

        agent.promptAddSection("Role", "You are a creative writing assistant.");
        agent.promptAddSection("Instructions", "", List.of(
                "Be imaginative and creative",
                "Use varied vocabulary",
                "Encourage creative thinking"
        ));

        agent.setPromptLlmParams(Map.of(
                "temperature", 0.8,
                "top_p", 0.95,
                "barge_confidence", 0.5,
                "presence_penalty", 0.2,
                "frequency_penalty", 0.3
        ));

        agent.setPostPrompt("Create an artistic summary of our conversation.");
        agent.setPostPromptLlmParams(Map.of("temperature", 0.7));

        agent.defineTool("generate_story_prompt", "Generate a creative story prompt",
                Map.of("type", "object",
                        "properties", Map.of(
                                "theme", Map.of("type", "string",
                                        "description", "Story theme")
                        )),
                (toolArgs, raw) -> {
                    String theme = (String) toolArgs.getOrDefault("theme", "adventure");
                    return new FunctionResult("Story prompt for " + theme +
                            ": A map that only appears during thunderstorms");
                });

        System.out.println("Starting Creative Assistant (high temperature)...");
        agent.run();
    }

    static void startSupport() throws Exception {
        var agent = AgentBase.builder()
                .name("customer-service")
                .route("/support")
                .port(3000)
                .build();

        agent.promptAddSection("Role",
                "You are a professional customer service representative.");
        agent.promptAddSection("Guidelines", "", List.of(
                "Always be polite and empathetic",
                "Listen carefully to customer concerns",
                "Provide clear, helpful solutions"
        ));

        agent.setPromptLlmParams(Map.of(
                "temperature", 0.4,
                "top_p", 0.9,
                "barge_confidence", 0.7,
                "presence_penalty", 0.1,
                "frequency_penalty", 0.1
        ));

        agent.setPostPrompt("Summarize the customer's issue and resolution.");
        agent.setPostPromptLlmParams(Map.of("temperature", 0.3));

        agent.defineTool("check_order_status", "Check order status",
                Map.of("type", "object",
                        "properties", Map.of(
                                "order_id", Map.of("type", "string",
                                        "description", "Order ID")
                        ),
                        "required", List.of("order_id")),
                (toolArgs, raw) -> {
                    String orderId = (String) toolArgs.getOrDefault("order_id", "unknown");
                    return new FunctionResult("Order " + orderId + " status: Shipped - expected delivery tomorrow.");
                });

        System.out.println("Starting Customer Service Agent (balanced)...");
        agent.run();
    }
}
