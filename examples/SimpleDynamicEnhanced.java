/**
 * Enhanced Dynamic Agent.
 *
 * Adapts based on request parameters:
 * - vip=true/false (premium voice, faster response)
 * - department=sales/support/billing (specialized expertise)
 * - customer_id=<string> (personalized experience)
 * - language=en/es (language and voice selection)
 *
 * Test:
 *   curl "http://localhost:3000/?vip=true&department=sales"
 *   curl "http://localhost:3000/?department=billing&language=es"
 */

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleDynamicEnhanced {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("dynamic-enhanced")
                .route("/")
                .port(3000)
                .build();

        agent.setDynamicConfigCallback((queryParams, bodyParams, headers, ephemeral) -> {
            boolean isVIP = "true".equalsIgnoreCase(queryParams.getOrDefault("vip", ""));
            String department = queryParams.getOrDefault("department", "general");
            String customerId = queryParams.getOrDefault("customer_id", "");
            String lang = queryParams.getOrDefault("language", "en");

            // Voice and language
            String voice = isVIP ? "en-US-Wavenet-D" : "en-US-Standard-C";
            if ("es".equals(lang)) {
                ephemeral.addLanguage("Spanish", "es-ES", voice);
            } else {
                ephemeral.addLanguage("English", "en-US", voice);
            }

            // AI parameters
            ephemeral.setParam("end_of_speech_timeout", isVIP ? 300 : 500);
            ephemeral.setParam("attention_timeout", isVIP ? 20000 : 15000);

            // Global data
            Map<String, Object> globalData = new HashMap<>();
            globalData.put("department", department);
            globalData.put("service_level", isVIP ? "vip" : "standard");
            if (!customerId.isEmpty()) globalData.put("customer_id", customerId);
            ephemeral.setGlobalData(globalData);

            // Role prompt
            String role = customerId.isEmpty()
                    ? "You are a professional customer service representative."
                    : "You are a customer service rep helping customer " + customerId + ".";
            if (isVIP) role += " This is a VIP customer who receives priority service.";
            ephemeral.promptAddSection("Role", role);

            // Department expertise
            switch (department) {
                case "sales":
                    ephemeral.promptAddSection("Sales Expertise", "You specialize in sales:", List.of(
                            "Present product features and benefits",
                            "Handle pricing questions",
                            "Process orders and upgrades"));
                    break;
                case "billing":
                    ephemeral.promptAddSection("Billing Expertise", "You specialize in billing:", List.of(
                            "Explain statements and charges",
                            "Process payment arrangements",
                            "Handle dispute resolution"));
                    break;
                default:
                    ephemeral.promptAddSection("Support Guidelines", "Follow these principles:", List.of(
                            "Listen carefully to customer needs",
                            "Provide accurate information",
                            "Escalate complex issues when appropriate"));
            }

            // Common tool
            ephemeral.defineTool("check_order", "Check order status",
                    Map.of("type", "object",
                            "properties", Map.of(
                                    "order_number", Map.of("type", "string",
                                            "description", "Order number"))),
                    (toolArgs, raw) -> {
                        String num = (String) toolArgs.getOrDefault("order_number", "unknown");
                        return new FunctionResult(
                                "Order " + num + " is being processed. Ships in 2 business days.");
                    });
        });

        System.out.println("Starting Enhanced Dynamic Agent on port 3000...");
        System.out.println("  ?vip=true          Premium voice + faster response");
        System.out.println("  ?department=sales  Sales expertise");
        System.out.println("  ?customer_id=X     Personalized experience");
        System.out.println("  ?language=es       Spanish");
        agent.run();
    }
}
