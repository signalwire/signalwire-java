/**
 * Server-side API integration using DataMap (no webhooks needed).
 *
 * DataMap tools execute on SignalWire servers. They can call external APIs
 * and process responses using variable expansion and pattern matching.
 */

import com.signalwire.agents.agent.AgentBase;
import com.signalwire.agents.datamap.DataMap;
import com.signalwire.agents.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

public class DataMapDemo {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("datamap-agent")
                .route("/")
                .port(3000)
                .build();

        agent.promptAddSection("Role",
                "You are an assistant that can look up weather and check order status.");

        // DataMap tool 1: Weather API (webhook-based)
        var weatherMap = new DataMap("get_weather")
                .purpose("Get current weather for a city")
                .parameter("city", "string", "City name", true)
                .webhook("GET", "https://api.weatherapi.com/v1/current.json?key=${env.WEATHER_API_KEY}&q=${args.city}")
                .output(new FunctionResult(
                        "Weather in ${args.city}: ${response.current.temp_f}F, ${response.current.condition.text}"))
                .fallbackOutput(new FunctionResult(
                        "Sorry, I could not retrieve weather data for ${args.city}."));

        agent.registerSwaigFunction(weatherMap.toSwaigFunction());

        // DataMap tool 2: Expression-based (pattern matching, no external API)
        var statusMap = new DataMap("check_order_status")
                .purpose("Check the status of an order by order number")
                .parameter("order_number", "string", "The order number", true)
                .expression("${args.order_number}", "^ORD-[0-9]+$",
                        new FunctionResult("Order ${args.order_number} is confirmed and being processed."),
                        new FunctionResult("Invalid order number format. Orders start with ORD- followed by digits."));

        agent.registerSwaigFunction(statusMap.toSwaigFunction());

        // DataMap tool 3: POST with body
        var feedbackMap = new DataMap("submit_feedback")
                .purpose("Submit customer feedback")
                .parameter("rating", "integer", "Rating from 1-5", true)
                .parameter("comment", "string", "Feedback comment", false)
                .webhook("POST", "https://api.example.com/feedback",
                        Map.of("Content-Type", "application/json"))
                .body(Map.of(
                        "rating", "${args.rating}",
                        "comment", "${args.comment}",
                        "timestamp", "${system.timestamp}"
                ))
                .output(new FunctionResult("Thank you for your feedback!"));

        agent.registerSwaigFunction(feedbackMap.toSwaigFunction());

        System.out.println("Starting DataMap agent on port 3000...");
        agent.run();
    }
}
