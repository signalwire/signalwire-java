/*
 * Web Search Agent.
 *
 * <p>Uses the web_search skill to give the agent internet search capabilities.
 *
 * <p>Requires env vars: GOOGLE_SEARCH_API_KEY GOOGLE_SEARCH_ENGINE_ID
 *
 * <p>Get credentials at: https://developers.google.com/custom-search/v1/introduction
 */
import com.signalwire.sdk.agent.AgentBase;
import java.util.List;
import java.util.Map;

public class WebSearchAgent {

  public static void main(String[] args) throws Exception {
    String apiKey = System.getenv("GOOGLE_SEARCH_API_KEY");
    String engineId = System.getenv("GOOGLE_SEARCH_ENGINE_ID");

    if (apiKey == null || engineId == null || apiKey.isEmpty() || engineId.isEmpty()) {
      System.err.println("Missing required env vars:");
      System.err.println("  GOOGLE_SEARCH_API_KEY");
      System.err.println("  GOOGLE_SEARCH_ENGINE_ID");
      System.exit(1);
    }

    var agent =
        AgentBase.builder().name("web-search-assistant").route("/search").port(3000).build();

    agent.addLanguage("English", "en-US", "en-US-Standard-C");

    agent.promptAddSection(
        "Personality", "You are Franklin, a friendly and knowledgeable search bot.");
    agent.promptAddSection(
        "Goal", "Help users find accurate, up-to-date information from the web.");
    agent.promptAddSection(
        "Instructions",
        "",
        List.of(
            "Always introduce yourself as Franklin",
            "Use the web search tool to find current information",
            "Present results in a clear, organized manner",
            "Be enthusiastic about searching and learning"));

    agent.addSkill(
        "web_search",
        Map.of(
            "api_key",
            apiKey,
            "search_engine_id",
            engineId,
            "num_results",
            1,
            "max_content_length",
            3000));

    System.out.println("Loaded skills: " + agent.listSkills());
    System.out.println("Starting Web Search Agent on port 3000...");
    agent.run();
  }
}
