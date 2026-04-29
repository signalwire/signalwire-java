/**
 * SWAIG Features Example.
 *
 * Demonstrates enhanced SWAIG features:
 * - Properly structured tool parameters
 * - Multiple tools with different parameter shapes
 * - Post-prompt for conversation summary
 * - Hints for speech recognition
 */

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class SwaigFeaturesAgent {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("swaig-features")
                .route("/swaig-features")
                .port(3000)
                .build();

        agent.promptAddSection("Personality",
                "You are a friendly and helpful assistant.");
        agent.promptAddSection("Goal",
                "Demonstrate advanced SWAIG features.");
        agent.promptAddSection("Instructions", "", List.of(
                "Be concise and direct in your responses.",
                "Use the get_weather function when asked about weather.",
                "Use the get_time function when asked about the current time."
        ));

        agent.addHints(List.of("weather", "temperature", "forecast", "time"));

        agent.setPostPrompt("Return a JSON summary: " +
                "{\"topic\":\"...\",\"functions_used\":[\"...\"]}");

        // Tool 1: No parameters
        agent.defineTool("get_time", "Get the current time",
                Map.of("type", "object", "properties", Map.of()),
                (toolArgs, raw) -> {
                    String time = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    return new FunctionResult("The current time is " + time);
                });

        // Tool 2: One required parameter
        agent.defineTool("get_weather",
                "Get the current weather for a location (including Star Wars planets)",
                Map.of("type", "object",
                        "properties", Map.of(
                                "location", Map.of("type", "string",
                                        "description", "City or location")
                        ),
                        "required", List.of("location")),
                (toolArgs, raw) -> {
                    String loc = ((String) toolArgs.getOrDefault("location", "")).toLowerCase();
                    String weather = switch (loc) {
                        case "tatooine" -> "Hot and dry with twin suns at their peak.";
                        case "hoth"     -> "Extremely cold with blizzard conditions. -20C.";
                        case "endor"    -> "Mild forest weather. Partly cloudy, 22C.";
                        default         -> "Sunny and 72F.";
                    };
                    return new FunctionResult("Weather in " + toolArgs.get("location") + ": " + weather);
                });

        // Tool 3: Required + optional parameter with enum
        agent.defineTool("get_forecast",
                "Get a 3-day weather forecast",
                Map.of("type", "object",
                        "properties", Map.of(
                                "location", Map.of("type", "string",
                                        "description", "City or location"),
                                "units", Map.of("type", "string",
                                        "description", "Temperature units",
                                        "enum", List.of("celsius", "fahrenheit"))
                        ),
                        "required", List.of("location")),
                (toolArgs, raw) -> {
                    String location = (String) toolArgs.getOrDefault("location", "Unknown");
                    String units = (String) toolArgs.getOrDefault("units", "fahrenheit");
                    boolean celsius = "celsius".equals(units);
                    String forecast = String.format(
                            "Today: %s, Tomorrow: %s, Day After: %s",
                            celsius ? "22C Sunny" : "72F Sunny",
                            celsius ? "20C Partly Cloudy" : "68F Partly Cloudy",
                            celsius ? "24C Clear" : "75F Clear"
                    );
                    return new FunctionResult("3-day forecast for " + location + ": " + forecast);
                });

        System.out.println("Starting SWAIG features agent on port 3000...");
        agent.run();
    }
}
