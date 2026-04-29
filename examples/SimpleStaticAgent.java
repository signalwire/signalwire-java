/**
 * Simple Static Agent.
 *
 * All configuration is set once during initialization and never changes.
 * Demonstrates voice, params, hints, global data, and structured prompts.
 */

import com.signalwire.sdk.agent.AgentBase;

import java.util.List;
import java.util.Map;

public class SimpleStaticAgent {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("simple-static")
                .route("/")
                .port(3000)
                .recordCall(true)
                .build();

        // Voice and language
        agent.addLanguage("English", "en-US", "en-US-Standard-C");

        // AI parameters
        agent.setParams(Map.of(
                "end_of_speech_timeout", 500,
                "attention_timeout", 15000,
                "background_file_volume", -20
        ));

        // Hints for speech recognition
        agent.addHints(List.of("SignalWire", "SWML", "API", "webhook", "SIP"));

        // Global data (same for every call)
        agent.setGlobalData(Map.of(
                "agent_type", "customer_service",
                "service_level", "standard",
                "features_enabled", List.of("basic_conversation", "help_desk")
        ));

        // Prompts
        agent.promptAddSection("Role and Purpose",
                "You are a professional customer service representative. " +
                "Your goal is to help customers with their questions.");

        agent.promptAddSection("Guidelines",
                "Follow these customer service principles:", List.of(
                        "Listen carefully to customer needs",
                        "Provide accurate and helpful information",
                        "Maintain a professional and friendly tone",
                        "Escalate complex issues when appropriate",
                        "Always confirm understanding before ending"
                ));

        agent.promptAddSection("Available Services",
                "You can help customers with:", List.of(
                        "General product information",
                        "Account questions and support",
                        "Technical troubleshooting guidance",
                        "Billing and payment inquiries",
                        "Service status and updates"
                ));

        System.out.println("Starting Simple Static Agent on port 3000...");
        System.out.println("Configuration is fixed at startup.");
        agent.run();
    }
}
