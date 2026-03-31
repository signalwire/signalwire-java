/**
 * Joke Skill Demo -- Using the Modular Skills System.
 *
 * Demonstrates the joke skill via the skills system with DataMap for
 * serverless execution. Compare with JokeAgent.java (raw data_map).
 *
 * Required: API_NINJAS_KEY environment variable.
 */

import com.signalwire.sdk.agent.AgentBase;

import java.util.List;
import java.util.Map;

public class JokeSkillDemo {

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("API_NINJAS_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Error: API_NINJAS_KEY environment variable is required.");
            System.err.println("Get your free API key from https://api.api-ninjas.com/");
            System.exit(1);
        }

        var agent = AgentBase.builder()
                .name("joke-skill-demo")
                .route("/joke-skill")
                .port(3000)
                .build();

        agent.addLanguage("English", "en-US", "en-US-Standard-C");

        agent.promptAddSection("Personality",
                "You are a cheerful comedian who loves sharing jokes.");
        agent.promptAddSection("Instructions", "", List.of(
                "When users ask for jokes, use your joke functions",
                "Be enthusiastic and fun in your responses",
                "You can tell both regular jokes and dad jokes"));

        agent.addSkill("joke", Map.of("api_key", apiKey));

        System.out.println("Joke Skill Demo (modular skills system)");
        System.out.println("  Benefits over raw DataMap:");
        System.out.println("    - One-liner integration via skills system");
        System.out.println("    - Automatic validation and error handling");
        System.out.println("    - Reusable across agents");
        System.out.println("Starting agent on port 3000...");
        agent.run();
    }
}
