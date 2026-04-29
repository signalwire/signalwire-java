/**
 * Comprehensive Dynamic Agent Configuration.
 *
 * Demonstrates per-request customization based on query parameters:
 * - Tier-based features (standard / premium / enterprise)
 * - Industry-specific prompts (healthcare / finance / retail)
 * - Language and voice selection
 * - A/B testing configuration
 * - Debug mode
 *
 * Try: ?tier=premium&industry=healthcare&lang=es&test_group=B
 */

import com.signalwire.sdk.agent.AgentBase;

import java.util.List;
import java.util.Map;

public class ComprehensiveDynamicAgent {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("comprehensive-dynamic")
                .route("/dynamic")
                .port(3000)
                .recordCall(true)
                .build();

        agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
            String tier     = queryParams.getOrDefault("tier", "standard").toLowerCase();
            String industry = queryParams.getOrDefault("industry", "general").toLowerCase();
            String lang     = queryParams.getOrDefault("lang", "en").toLowerCase();
            String testGroup = queryParams.getOrDefault("test_group", "A").toUpperCase();
            boolean debug   = "true".equals(queryParams.get("debug"));
            String customerId = queryParams.getOrDefault("customer_id", "");

            // --- Voice / Language ---
            if ("es".equals(lang)) {
                configAgent.addLanguage("Spanish", "es-ES", "es-ES-Standard-A");
            } else {
                configAgent.addLanguage("English US", "en-US", "en-US-Standard-C");
            }

            // --- Tier parameters ---
            switch (tier) {
                case "enterprise" -> configAgent.setParams(Map.of(
                        "end_of_speech_timeout", 800,
                        "attention_timeout", 25000));
                case "premium" -> configAgent.setParams(Map.of(
                        "end_of_speech_timeout", 600,
                        "attention_timeout", 20000));
                default -> configAgent.setParams(Map.of(
                        "end_of_speech_timeout", 400,
                        "attention_timeout", 15000));
            }

            // --- Industry prompts ---
            configAgent.promptAddSection("Role",
                    "You are a professional AI assistant specialized in " + industry + " services.");

            if ("healthcare".equals(industry)) {
                configAgent.promptAddSection("Healthcare Guidelines",
                        "Follow HIPAA compliance. Never provide medical diagnoses.", List.of(
                                "Protect patient privacy",
                                "Direct medical questions to providers",
                                "Use appropriate medical terminology"
                        ));
            } else if ("finance".equals(industry)) {
                configAgent.promptAddSection("Financial Guidelines",
                        "Adhere to financial regulations.", List.of(
                                "Never provide investment advice",
                                "Protect sensitive financial data",
                                "Use precise financial terminology"
                        ));
            } else if ("retail".equals(industry)) {
                configAgent.promptAddSection("Customer Service",
                        "Focus on satisfaction and sales support.", List.of(
                                "Be friendly and helpful",
                                "Handle complaints with empathy",
                                "Look for upsell opportunities"
                        ));
            }

            // --- Enhanced tier capabilities ---
            if ("premium".equals(tier) || "enterprise".equals(tier)) {
                configAgent.promptAddSection("Enhanced Capabilities",
                        "As a " + tier + " service you have advanced features:", List.of(
                                "Extended conversation memory",
                                "Priority processing",
                                "Specialized knowledge bases"
                        ));
            }

            // --- A/B testing ---
            if ("B".equals(testGroup)) {
                configAgent.addHints(List.of("enhanced", "personalized", "proactive"));
                configAgent.promptAddSection("Enhanced Interaction Style",
                        "Use an enhanced conversation style:", List.of(
                                "Ask clarifying questions frequently",
                                "Provide detailed explanations",
                                "Offer proactive suggestions"
                        ));
            }

            // --- Debug mode ---
            if (debug) {
                configAgent.promptAddSection("Debug Mode",
                        "Debug mode is active. Show reasoning in responses.");
                configAgent.addHints(List.of("debug", "verbose", "reasoning"));
            }

            // --- Global data ---
            configAgent.updateGlobalData(Map.of(
                    "service_tier", tier,
                    "industry", industry,
                    "test_group", testGroup
            ));
            if (!customerId.isEmpty()) {
                configAgent.updateGlobalData(Map.of("customer_id", customerId));
            }
        });

        System.out.println("Starting comprehensive dynamic agent on port 3000...");
        System.out.println("Try: ?tier=premium&industry=healthcare&lang=es&test_group=B");
        agent.run();
    }
}
