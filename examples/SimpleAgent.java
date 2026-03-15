/**
 * Minimal agent with one tool and POM-based prompts.
 *
 * Run:
 *   java SimpleAgent
 *
 * Test:
 *   bin/swaig-test --url http://agent:PASS@localhost:3000 --list-tools
 *   bin/swaig-test --url http://agent:PASS@localhost:3000 --exec get_weather --param city=Austin
 */

import com.signalwire.agents.agent.AgentBase;
import com.signalwire.agents.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

public class SimpleAgent {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("weather-agent")
                .route("/")
                .port(3000)
                .build();

        // Add prompt sections (Prompt Object Model)
        agent.promptAddSection("Role",
                "You are a helpful weather assistant.");
        agent.promptAddSection("Instructions", "", List.of(
                "When asked about weather, use the get_weather tool",
                "Provide temperature in both Fahrenheit and Celsius",
                "Be friendly and conversational"
        ));

        // Add speech recognition hints
        agent.addHints(List.of("weather", "temperature", "forecast"));

        // Define a tool
        agent.defineTool(
                "get_weather",
                "Get the current weather for a city",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "city", Map.of(
                                        "type", "string",
                                        "description", "The city to get weather for"
                                )
                        ),
                        "required", List.of("city")
                ),
                (toolArgs, rawData) -> {
                    String city = (String) toolArgs.get("city");
                    return new FunctionResult(
                            "The weather in " + city + " is 72F (22C) and sunny.");
                }
        );

        System.out.println("Starting weather agent on port 3000...");
        agent.run();
    }
}
