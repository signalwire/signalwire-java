/**
 * Advanced DataMap Features Demo.
 *
 * Demonstrates expression-based responses, webhook chains, foreach array
 * processing, and fallback outputs -- all without custom webhook endpoints.
 */

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.datamap.DataMap;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

public class AdvancedDatamapDemo {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("advanced-datamap")
                .route("/")
                .port(3000)
                .build();

        agent.promptAddSection("Role",
                "You are an assistant that demonstrates advanced DataMap features.");

        // 1. Expression-based command processor with pattern matching
        var commandMap = new DataMap("command_processor")
                .purpose("Process user commands with pattern matching")
                .parameter("command", "string", "User command to process", true)
                .parameter("target", "string", "Optional target for the command", false)
                .expression("${args.command}", "^start",
                        new FunctionResult("Starting process: ${args.target}"))
                .expression("${args.command}", "^stop",
                        new FunctionResult("Stopping process: ${args.target}"))
                .expression("${args.command}", "^status",
                        new FunctionResult("Checking status of: ${args.target}"),
                        new FunctionResult("Unknown command: ${args.command}. Try start, stop, or status."));

        agent.registerSwaigFunction(commandMap.toSwaigFunction());

        // 2. Webhook with foreach array processing
        var searchMap = new DataMap("search_results")
                .purpose("Search and format results from API")
                .parameter("query", "string", "Search query", true)
                .parameter("limit", "string", "Maximum results", false)
                .webhook("GET", "https://search-api.example.com/search",
                        Map.of("Authorization", "Bearer ${search_token}"))
                .params(Map.of("q", "${args.query}"))
                .foreach(Map.of(
                        "input_key", "results",
                        "output_key", "formatted_results",
                        "max", 5,
                        "append", "Title: ${this.title}\n${this.summary}\nURL: ${this.url}\n\n"
                ))
                .output(new FunctionResult("Search results for \"${args.query}\":\n\n${formatted_results}"))
                .errorKeys(List.of("error"));

        agent.registerSwaigFunction(searchMap.toSwaigFunction());

        // 3. POST webhook with body and fallback
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
                .output(new FunctionResult("Thank you for your feedback!"))
                .fallbackOutput(new FunctionResult("Feedback service is temporarily unavailable."))
                .globalErrorKeys(List.of("error", "fault", "exception"));

        agent.registerSwaigFunction(feedbackMap.toSwaigFunction());

        // 4. Expression-based smart calculator
        var calcMap = new DataMap("smart_calculator")
                .purpose("Smart calculator with conditional responses")
                .parameter("expression", "string", "Mathematical expression", true)
                .parameter("format", "string", "Output format (simple/detailed)", false)
                .expression("${args.expression}", "^\\s*\\d+\\s*[+\\-*/]\\s*\\d+\\s*$",
                        new FunctionResult("Quick calculation: ${args.expression} = @{expr ${args.expression}}"))
                .expression("${args.format}", "^detailed$",
                        new FunctionResult("Detailed: ${args.expression} = @{expr ${args.expression}} at @{strftime_tz UTC %Y-%m-%d %H:%M:%S}"))
                .fallbackOutput(new FunctionResult(
                        "Expression: ${args.expression}\nResult: @{expr ${args.expression}}"));

        agent.registerSwaigFunction(calcMap.toSwaigFunction());

        System.out.println("Starting Advanced DataMap agent on port 3000...");
        agent.run();
    }
}
