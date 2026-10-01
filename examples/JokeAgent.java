/*
 * Joke Agent -- uses a raw data_map configuration to integrate with the API Ninjas joke API.
 *
 * <p>Run with: API_NINJAS_KEY=your_key java JokeAgent
 */
import com.signalwire.sdk.agent.AgentBase;
import java.util.List;
import java.util.Map;

public class JokeAgent {

  public static void main(String[] args) throws Exception {
    String apiKey = System.getenv("API_NINJAS_KEY");
    if (apiKey == null || apiKey.isEmpty()) {
      System.err.println("Error: API_NINJAS_KEY environment variable is required.");
      System.err.println("Get a free key at https://api.api-ninjas.com/");
      System.exit(1);
    }

    var agent = AgentBase.builder().name("joke-agent").route("/joke-agent").port(3000).build();

    agent.promptAddSection("Personality", "You are a funny assistant who loves to tell jokes.");
    agent.promptAddSection("Goal", "Make people laugh with great jokes.");
    agent.promptAddSection(
        "Instructions",
        "",
        List.of(
            "Use the get_joke function to tell jokes when asked",
            "You can tell either regular jokes or dad jokes",
            "Be enthusiastic about sharing humor"));

    // Register a raw data_map function (no webhook handler needed)
    agent.registerSwaigFunction(
        Map.of(
            "function",
            "get_joke",
            "description",
            "Tell a joke",
            "parameters",
            Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                    "type",
                    Map.of(
                        "type", "string",
                        "description", "Must be 'jokes' or 'dadjokes'"))),
            "data_map",
            Map.of(
                "webhooks",
                    List.of(
                        Map.of(
                            "url", "https://api.api-ninjas.com/v1/%{args.type}",
                            "method", "GET",
                            "headers", Map.of("X-Api-Key", apiKey),
                            "output", Map.of("response", "Tell the user: %{array[0].joke}"),
                            "error_keys", "error")),
                "output",
                    Map.of(
                        "response",
                        "The joke service isn't working right now. Make up a joke on your own."))));

    System.out.println("Starting Joke Agent on port 3000...");
    agent.run();
  }
}
