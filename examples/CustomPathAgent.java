/**
 * Custom Path Agent -- agent served at a non-root path.
 *
 * Demonstrates how to create an agent at a custom route (/chat) with
 * dynamic per-request personalization from query parameters.
 *
 * Try: ?user_name=Alice&topic=AI&mood=professional
 */

import com.signalwire.sdk.agent.AgentBase;

import java.util.List;
import java.util.Map;

public class CustomPathAgent {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("chat-assistant")
                .route("/chat")
                .port(3000)
                .recordCall(true)
                .build();

        agent.promptAddSection("Role",
                "You are a friendly chat assistant ready to help with any questions.");

        agent.setDynamicConfigCallback((queryParams, bodyParams, headers, configAgent) -> {
            String userName = queryParams.getOrDefault("user_name", "friend");
            String topic = queryParams.getOrDefault("topic", "general conversation");
            String mood = queryParams.getOrDefault("mood", "friendly").toLowerCase();

            configAgent.promptAddSection("Personalization",
                    "The user's name is " + userName + ". They're interested in " + topic + ".");

            configAgent.addLanguage("English", "en-US", "en-US-Standard-C");

            switch (mood) {
                case "professional" -> configAgent.promptAddSection("Communication Style",
                        "Maintain a professional, business-appropriate tone.");
                case "casual" -> configAgent.promptAddSection("Communication Style",
                        "Use a casual, relaxed conversational style.");
                default -> configAgent.promptAddSection("Communication Style",
                        "Be warm, friendly, and approachable in your responses.");
            }

            configAgent.setGlobalData(Map.of(
                    "user_name", userName,
                    "topic", topic,
                    "mood", mood,
                    "session_type", "chat"
            ));

            configAgent.addHints(List.of("chat", "assistant", "help", "conversation"));
        });

        System.out.println("Starting chat agent at /chat on port 3000...");
        System.out.println("Try: ?user_name=Alice&topic=AI&mood=professional");
        agent.run();
    }
}
