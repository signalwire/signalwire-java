/**
 * SwmlServiceSwaigStandalone -- proves that {@link com.signalwire.sdk.swml.Service}
 * by itself, with NO {@code com.signalwire.sdk.agent.AgentBase}, can host SWAIG
 * functions and serve them on its own {@code /swaig} endpoint.
 *
 * <p>This is the path you take when you want a SWAIG-callable HTTP service that
 * isn't an {@code <ai>} agent: the SWAIG verb is a generic LLM-tool surface and
 * {@code Service} is the host. {@code AgentBase} is a {@code Service} subclass
 * that <em>also</em> layers in prompts, AI config, dynamic config, and token
 * validation -- but {@code defineTool}, {@code registerSwaigFunction}, and
 * {@code onFunctionCall} all live on {@code Service} itself.
 *
 * <p>The class extends {@link com.signalwire.sdk.swml.Service} directly and
 * registers its SWAIG tools in the constructor. {@code main()} only calls
 * {@code serve()}. This shape is reusable: the SDK's {@code swaig-test} CLI
 * can load the class in-process via reflection and inspect the tool registry
 * without starting an HTTP server.
 *
 * <p>Run as a server:
 * <pre>
 *     javac -cp build/libs/signalwire-sdk-*.jar -d /tmp/swaig-standalone examples/SwmlServiceSwaigStandalone.java
 *     java  -cp build/libs/signalwire-sdk-*.jar:/tmp/swaig-standalone SwmlServiceSwaigStandalone
 * </pre>
 *
 * <p>Then exercise the endpoints (auth user/password are printed at startup):
 * <pre>
 *     curl -u USER:PASS http://localhost:3000/standalone
 *     curl -u USER:PASS http://localhost:3000/standalone/swaig \
 *         -H 'Content-Type: application/json' \
 *         -d '{"function":"lookup_competitor","argument":{"parsed":[{"competitor":"ACME"}]}}'
 * </pre>
 *
 * <p>Or drive it through the SDK's {@code swaig-test} CLI:
 * <pre>
 *     # In-process (no HTTP -- inspect the tool registry directly):
 *     SWAIG_TEST_CLASSPATH=/tmp/swaig-standalone bin/swaig-test \
 *         --class SwmlServiceSwaigStandalone --list-tools
 *
 *     # Or against a running server (URL mode):
 *     bin/swaig-test --url http://USER:PASS@localhost:3000/standalone --list-tools
 *     bin/swaig-test --url http://USER:PASS@localhost:3000/standalone \
 *         --exec lookup_competitor --param competitor=ACME
 * </pre>
 */

import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swml.Service;

import java.util.Map;

public class SwmlServiceSwaigStandalone extends Service {

    public SwmlServiceSwaigStandalone() {
        // 1. Plain Service -- no AgentBase. Route is independent of the SWAIG
        //    surface; /standalone/swaig is what answers tool calls.
        super("standalone-swaig", "/standalone");

        // 2. Build a minimal SWML document. Any verbs are fine -- the SWAIG
        //    HTTP surface is independent of the document contents.
        answer(Map.of());
        hangup();

        // 3. Register a SWAIG function. defineTool's signature on Service is:
        //
        //        Service defineTool(String name,
        //                           String description,
        //                           Map<String, Object> parameters,
        //                           ToolHandler handler)
        //
        //    The handler receives parsed arguments + the raw POST body and
        //    returns a FunctionResult.
        defineTool(
                "lookup_competitor",
                "Look up competitor pricing by company name. Use this when "
                        + "the user asks how a competitor's price compares to ours.",
                Map.of(
                        "competitor", Map.of(
                                "type", "string",
                                "description", "The competitor's company name, e.g. 'ACME'."
                        )
                ),
                (toolArgs, rawData) -> {
                    Object competitor = toolArgs.getOrDefault("competitor", "<unknown>");
                    return new FunctionResult(
                            competitor + " pricing is $99/seat; we're $79/seat.");
                }
        );
    }

    public static void main(String[] args) throws Exception {
        var svc = new SwmlServiceSwaigStandalone();
        System.out.printf("Starting standalone SWAIG service on /standalone%n");
        System.out.printf("  auth user: %s%n", svc.getAuthUser());
        System.out.printf("  auth pass: %s%n", svc.getAuthPassword());
        System.out.printf("  tools:     %s%n", svc.listToolNames());
        svc.serve();
    }
}
