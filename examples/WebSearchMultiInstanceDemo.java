/**
 * Web Search Multiple Instance Demo.
 *
 * Loads the web search skill multiple times with different configurations
 * (general, news, quick). Also includes Wikipedia search.
 *
 * Required: GOOGLE_SEARCH_API_KEY, GOOGLE_SEARCH_ENGINE_ID
 */

import com.signalwire.sdk.agent.AgentBase;

import java.util.Map;

public class WebSearchMultiInstanceDemo {

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("GOOGLE_SEARCH_API_KEY");
        String engineId = System.getenv("GOOGLE_SEARCH_ENGINE_ID");

        var agent = AgentBase.builder()
                .name("multi-search")
                .route("/multi-search")
                .port(3000)
                .build();

        agent.addLanguage("English", "en-US", "en-US-Standard-C");

        agent.promptAddSection("Role",
                "You are a research assistant with access to multiple search tools. " +
                "Use the most appropriate tool for each query.");

        agent.addSkill("datetime", Map.of());
        agent.addSkill("math", Map.of());

        // Wikipedia search
        agent.addSkill("wikipedia_search", Map.of("num_results", 2));

        if (apiKey == null || engineId == null || apiKey.isEmpty() || engineId.isEmpty()) {
            System.out.println("Warning: Missing GOOGLE_SEARCH_API_KEY or GOOGLE_SEARCH_ENGINE_ID.");
            System.out.println("Web search instances will not be available.");
        } else {
            // General web search (default tool name)
            agent.addSkill("web_search", Map.of(
                    "api_key", apiKey,
                    "search_engine_id", engineId,
                    "num_results", 3));

            // News-focused search
            agent.addSkill("web_search", Map.of(
                    "api_key", apiKey,
                    "search_engine_id", engineId,
                    "tool_name", "search_news",
                    "num_results", 5));

            // Quick single-result search
            agent.addSkill("web_search", Map.of(
                    "api_key", apiKey,
                    "search_engine_id", engineId,
                    "tool_name", "quick_search",
                    "num_results", 1));
        }

        System.out.println("Loaded skills: " + agent.listSkills());
        System.out.println("Tools: web_search, search_news, quick_search, search_wiki");
        System.out.println("Starting multi-search agent on port 3000...");
        agent.run();
    }
}
