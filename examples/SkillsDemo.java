/**
 * Adding built-in skills to an agent.
 *
 * Skills are modular capabilities that auto-register tools, prompts, and hints.
 * The SDK includes 18+ built-in skills.
 */

import com.signalwire.agents.agent.AgentBase;

import java.util.Map;

public class SkillsDemo {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("skilled-agent")
                .route("/")
                .port(3000)
                .build();

        agent.promptAddSection("Role",
                "You are a versatile assistant with many capabilities.");

        // Add datetime skill -- provides current date/time information
        agent.addSkill("datetime", Map.of(
                "timezone", "America/Chicago"
        ));

        // Add math skill -- basic calculator
        agent.addSkill("math", Map.of());

        // Add web search skill (requires SERPER_API_KEY env var)
        agent.addSkill("web_search", Map.of(
                "tool_name", "search_web",
                "num_results", 5
        ));

        // Add joke skill -- tell jokes
        agent.addSkill("joke", Map.of());

        // List registered skills
        System.out.println("Registered skills: " + agent.listSkills());

        System.out.println("Starting skilled agent on port 3000...");
        agent.run();
    }
}
