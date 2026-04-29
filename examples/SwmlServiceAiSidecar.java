/**
 * SwmlServiceAiSidecar -- proves that {@link com.signalwire.sdk.swml.Service}
 * can emit the {@code ai_sidecar} verb, register SWAIG tools the sidecar's LLM
 * can call, and dispatch them end-to-end -- all without any
 * {@code com.signalwire.sdk.agent.AgentBase} code path.
 *
 * <p>The {@code ai_sidecar} verb runs an AI listener alongside an in-progress
 * call (real-time copilot, transcription analyzer, compliance monitor, etc.).
 * It is NOT an agent -- it does not own the call. So the right host is
 * {@code Service}, not {@code AgentBase}.
 *
 * <p>What this serves:
 * <pre>
 *     GET  /sales-sidecar           -> SWML doc with the ai_sidecar verb
 *     POST /sales-sidecar/swaig     -> SWAIG tool dispatch (used by the sidecar's LLM)
 *     POST /sales-sidecar/events    -> optional event sink for sidecar lifecycle events
 * </pre>
 *
 * <p>Drive the SWAIG path through the SDK's {@code swaig-test} CLI:
 * <pre>
 *     bin/swaig-test --url http://USER:PASS@localhost:3000/sales-sidecar --list-tools
 *     bin/swaig-test --url http://USER:PASS@localhost:3000/sales-sidecar \
 *         --exec lookup_competitor --param competitor=ACME
 * </pre>
 */

import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swml.Service;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class SwmlServiceAiSidecar {

    /**
     * A {@code Service} subclass that overrides {@code registerAdditionalRoutes}
     * to mount a {@code /events} sink for ai_sidecar lifecycle events. The
     * Java equivalent of Python's {@code register_routing_callback}.
     */
    static class SalesSidecar extends Service {

        SalesSidecar() {
            super("sales-sidecar", "/sales-sidecar");
        }

        @Override
        protected void registerAdditionalRoutes(HttpServer server) {
            String basePath = "/sales-sidecar";
            server.createContext(basePath + "/events", exchange -> {
                try {
                    if (!validateAuth(exchange)) {
                        sendUnauthorized(exchange);
                        return;
                    }
                    addSecurityHeaders(exchange);
                    String body = readEventBody(exchange);
                    System.out.printf("[sidecar event] %s%n", body);
                    byte[] resp = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, resp.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp);
                    }
                } catch (Exception e) {
                    try {
                        exchange.sendResponseHeaders(500, -1);
                        exchange.close();
                    } catch (IOException ignored) {
                    }
                }
            });
        }

        private static String readEventBody(com.sun.net.httpserver.HttpExchange exchange)
                throws IOException {
            try (InputStream is = exchange.getRequestBody()) {
                byte[] buf = is.readAllBytes();
                return new String(buf, StandardCharsets.UTF_8);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // 1. Public URL of this service (env-overridable). The ai_sidecar verb
        //    needs a fully-qualified URL to POST SWAIG calls back to.
        String publicUrl = System.getenv().getOrDefault(
                "PUBLIC_URL",
                "https://your-host.example.com/sales-sidecar");

        var svc = new SalesSidecar();

        // 2. Emit any SWML -- including ai_sidecar. Service exposes
        //    addVerbToSection via getDocument(), so new platform verbs work
        //    without an SDK release. UPPERCASE "SWAIG" is the SWML schema key
        //    that registers the LLM-callable webhook for the sidecar.
        svc.answer(Map.of());
        svc.getDocument().addVerbToSection(
                "main",
                "ai_sidecar",
                Map.of(
                        "prompt", "You are a real-time sales copilot. Listen to the call "
                                + "and surface competitor pricing comparisons when relevant.",
                        "lang", "en-US",
                        // direction: both legs as a list, per ai_sidecar schema.
                        "direction", List.of("remote-caller", "local-caller"),
                        // Optional event sink -- mod_openai POSTs lifecycle/transcription events here.
                        "url", publicUrl + "/events",
                        // SWAIG webhook target -- where the sidecar's LLM POSTs tool calls.
                        // The /swaig endpoint Service mounts is what answers them.
                        "SWAIG", Map.of(
                                "defaults", Map.of("web_hook_url", publicUrl + "/swaig")
                        )
                )
        );
        svc.hangup();

        // 3. Register tools the sidecar's LLM can call. Same defineTool you'd
        //    use on AgentBase -- it lives on Service.
        svc.defineTool(
                "lookup_competitor",
                "Look up competitor pricing by company name. The sidecar should "
                        + "call this whenever the caller mentions a competitor.",
                Map.of(
                        "competitor", Map.of(
                                "type", "string",
                                "description", "The competitor's company name, e.g. 'ACME'."
                        )
                ),
                (toolArgs, rawData) -> {
                    Object competitor = toolArgs.getOrDefault("competitor", "<unknown>");
                    return new FunctionResult(
                            "Pricing for " + competitor + ": $99/seat. Our equivalent "
                                    + "plan is $79/seat with the same SLA.");
                }
        );

        System.out.printf("Starting ai_sidecar service on /sales-sidecar%n");
        System.out.printf("  public URL: %s%n", publicUrl);
        System.out.printf("  auth user:  %s%n", svc.getAuthUser());
        System.out.printf("  auth pass:  %s%n", svc.getAuthPassword());
        System.out.printf("  tools:      %s%n", svc.listToolNames());
        svc.serve();
    }
}
