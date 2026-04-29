/**
 * Wikipedia Search Agent.
 *
 * Uses the wikipedia_search skill for factual information retrieval
 * from Wikipedia articles.
 */

import com.signalwire.sdk.agent.AgentBase;

import java.util.Map;

public class WikipediaDemo {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("wikipedia-assistant")
                .route("/wiki-demo")
                .port(3000)
                .build();

        agent.addLanguage("English", "en-US", "en-US-Standard-C");

        agent.promptAddSection("Role",
                "You are a knowledgeable assistant that specializes in looking up " +
                "factual information from Wikipedia.");

        // Add datetime for convenience
        agent.addSkill("datetime", Map.of());

        // Add Wikipedia search skill
        agent.addSkill("wikipedia_search", Map.of(
                "num_results", 2
        ));

        System.out.println("Loaded skills: " + agent.listSkills());
        System.out.println("Starting Wikipedia Assistant on port 3000...");
        System.out.println("Try asking about people, places, concepts, or history.");
        agent.run();
    }
}
