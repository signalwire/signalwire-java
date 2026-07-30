/**
 * Declarative Agent Example.
 *
 * <p>Demonstrates building an agent entirely from prompt sections defined up front, with simple
 * tool definitions. This pattern keeps prompt structure visible and separated from implementation
 * logic.
 */
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class DeclarativeAgent {

  public static void main(String[] args) throws Exception {
    var agent = AgentBase.builder().name("declarative").route("/declarative").port(3000).build();

    // Personality
    agent.promptAddSection(
        "Personality",
        "You are a friendly and helpful AI assistant who responds in a casual, conversational tone.");

    // Goal
    agent.promptAddSection("Goal", "Help users with their questions about time and weather.");

    // Instructions
    agent.promptAddSection(
        "Instructions",
        "",
        List.of(
            "Be concise and direct in your responses.",
            "If you don't know something, say so clearly.",
            "Use the get_time function when asked about the current time.",
            "Use the get_weather function when asked about the weather."));

    // Post-prompt for summary
    agent.setPostPrompt("Return a JSON summary: {\"topic\":\"...\",\"satisfied\":true/false}");

    // Tools
    agent.defineTool(
        "get_time",
        "Get the current time",
        Map.of("type", "object", "properties", Map.of()),
        (toolArgs, raw) -> {
          String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
          return new FunctionResult("The current time is " + time);
        });

    agent.defineTool(
        "get_weather",
        "Get the current weather for a location",
        Map.of(
            "type",
            "object",
            "properties",
            Map.of("location", Map.of("type", "string", "description", "City or location")),
            "required",
            List.of("location")),
        (toolArgs, raw) -> {
          String location = (String) toolArgs.getOrDefault("location", "Unknown");
          return new FunctionResult("It's sunny and 72F in " + location + ".");
        });

    // Summary callback
    agent.onSummary(
        (summary, rawPayload) -> System.out.println("Conversation summary: " + summary));

    System.out.println("Starting declarative agent on port 3000...");
    agent.run();
  }
}
