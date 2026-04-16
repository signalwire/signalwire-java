package com.signalwire.sdk.cli;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.cli.simulation.ServerlessSimulator;
import com.signalwire.sdk.runtime.EnvProvider;
import com.signalwire.sdk.runtime.lambda.LambdaAgentHandler;
import com.signalwire.sdk.runtime.lambda.LambdaResponse;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * CLI tool for testing SWAIG functions against an agent.
 * <p>
 * Uses only JDK classes (java.net.http.HttpClient) -- no external dependencies.
 * <p>
 * Two modes:
 * <ul>
 *   <li><b>URL mode</b> — hit a running agent HTTP server.</li>
 *   <li><b>Simulation mode</b> — {@code --simulate-serverless &lt;platform&gt;} loads the
 *       agent class directly and routes invocation through the matching
 *       serverless adapter (e.g. {@link LambdaAgentHandler}) instead of
 *       the HTTP server. Uses an injected {@link EnvProvider} to mask the
 *       real process env with simulated values, since Java cannot mutate
 *       {@link System#getenv()}.</li>
 * </ul>
 * <p>
 * Usage:
 * <pre>
 *   swaig-test --url http://user:pass@localhost:3000 --list-tools
 *   swaig-test --url http://user:pass@localhost:3000 --dump-swml
 *   swaig-test --url http://user:pass@localhost:3000 --exec tool_name --param key=value
 *   swaig-test &lt;agent-class&gt; --simulate-serverless lambda --dump-swml
 *   swaig-test &lt;agent-class&gt; --simulate-serverless lambda --exec tool_name --param k=v
 *   swaig-test &lt;agent-class&gt; --simulate-serverless lambda
 * </pre>
 */
public class SwaigTest {

    private String baseUrl;
    private String authUser;
    private String authPassword;
    private boolean verbose;
    private boolean raw;

    // Commands
    private boolean dumpSwml;
    private boolean listTools;
    private String execTool;
    private final Map<String, String> params = new LinkedHashMap<>();

    // Simulation mode
    private String agentClassName;
    private String simulatePlatformRaw;

    // Testability hook: an alternative env source for the simulator's
    // "real env" fallback layer. Never consulted unless explicitly set
    // (in which case the CLI is being driven by a test harness that
    // needs to inject a controlled real-env view without touching
    // {@link System#getenv()}).
    private EnvProvider realEnvOverride;

    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) System.exit(code);
    }

    /**
     * Run the CLI and return the exit code. Extracted so tests can drive
     * {@code main} without calling {@link System#exit(int)}.
     *
     * @param args command-line arguments.
     * @return 0 on success, non-zero on error.
     */
    public static int run(String[] args) {
        var cli = new SwaigTest();
        try {
            cli.parseArgs(args);
            cli.run();
            return 0;
        } catch (HelpRequested e) {
            return 0;
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            if (args.length == 0 || argsContainHelp(args)) printUsage();
            return 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (cli.verbose) {
                e.printStackTrace(System.err);
            }
            return 1;
        }
    }

    /**
     * Inject an alternative "real env" for the simulator's fallback
     * layer. Primarily for tests that need to pretend the process env
     * has {@code SWML_PROXY_URL_BASE} set without actually mutating it.
     * Null means "use {@link EnvProvider#SYSTEM}".
     *
     * @param env env provider, or null.
     */
    void setRealEnvOverrideForTests(EnvProvider env) {
        this.realEnvOverride = env;
    }

    private static boolean argsContainHelp(String[] args) {
        for (String a : args) {
            if ("--help".equals(a) || "-h".equals(a)) return true;
        }
        return false;
    }

    private void parseArgs(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("No arguments provided");
        }

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--url" -> {
                    if (++i >= args.length) throw new IllegalArgumentException("--url requires a value");
                    parseUrl(args[i]);
                }
                case "--dump-swml" -> dumpSwml = true;
                case "--list-tools" -> listTools = true;
                case "--exec" -> {
                    if (++i >= args.length) throw new IllegalArgumentException("--exec requires a tool name");
                    execTool = args[i];
                }
                case "--param" -> {
                    if (++i >= args.length) throw new IllegalArgumentException("--param requires key=value");
                    String kv = args[i];
                    int eq = kv.indexOf('=');
                    if (eq <= 0) throw new IllegalArgumentException("--param must be key=value, got: " + kv);
                    params.put(kv.substring(0, eq), kv.substring(eq + 1));
                }
                case "--simulate-serverless" -> {
                    if (++i >= args.length) {
                        throw new IllegalArgumentException("--simulate-serverless requires a platform name");
                    }
                    simulatePlatformRaw = args[i];
                }
                case "--raw" -> raw = true;
                case "--verbose" -> verbose = true;
                case "--help", "-h" -> {
                    printUsage();
                    throw new HelpRequested();
                }
                default -> {
                    if (args[i].startsWith("--")) {
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                    }
                    // Positional: agent class name. Only one allowed.
                    if (agentClassName != null) {
                        throw new IllegalArgumentException(
                                "Unexpected positional argument: " + args[i]
                                        + " (agent class already set to: " + agentClassName + ")");
                    }
                    agentClassName = args[i];
                }
            }
        }

        boolean simulating = simulatePlatformRaw != null;

        if (simulating) {
            if (agentClassName == null) {
                throw new IllegalArgumentException(
                        "--simulate-serverless requires a positional agent-class argument "
                                + "(e.g. 'swaig-test MyAgent --simulate-serverless lambda')");
            }
            if (baseUrl != null) {
                throw new IllegalArgumentException("--url cannot be combined with --simulate-serverless");
            }
            // list-tools is not yet implemented in simulation mode (not
            // required by the task). Explicitly reject to avoid silent
            // no-ops.
            if (listTools) {
                throw new IllegalArgumentException(
                        "--list-tools is not supported in --simulate-serverless mode; "
                                + "use --dump-swml and inspect the functions array");
            }
        } else {
            if (baseUrl == null) {
                throw new IllegalArgumentException(
                        "--url is required (or pass <agent-class> --simulate-serverless <platform>)");
            }
            if (!dumpSwml && !listTools && execTool == null) {
                throw new IllegalArgumentException(
                        "One of --dump-swml, --list-tools, or --exec is required");
            }
        }
    }

    /**
     * Sentinel signalling {@code --help} was requested. Distinct from
     * {@link IllegalArgumentException} so the caller can exit 0.
     */
    static final class HelpRequested extends RuntimeException {
        HelpRequested() { super("help requested"); }
    }

    /**
     * Parse URL, extracting embedded auth credentials if present.
     * Supports: http://user:pass@host:port/path
     */
    private void parseUrl(String urlStr) {
        try {
            // Extract auth from URL if present
            if (urlStr.contains("@")) {
                int schemeEnd = urlStr.indexOf("://");
                if (schemeEnd >= 0) {
                    String scheme = urlStr.substring(0, schemeEnd + 3);
                    String rest = urlStr.substring(schemeEnd + 3);
                    int atIdx = rest.indexOf('@');
                    if (atIdx >= 0) {
                        String authPart = rest.substring(0, atIdx);
                        String hostPart = rest.substring(atIdx + 1);
                        int colonIdx = authPart.indexOf(':');
                        if (colonIdx >= 0) {
                            authUser = authPart.substring(0, colonIdx);
                            authPassword = authPart.substring(colonIdx + 1);
                        } else {
                            authUser = authPart;
                            authPassword = "";
                        }
                        urlStr = scheme + hostPart;
                    }
                }
            }

            // Remove trailing slash
            if (urlStr.endsWith("/") && urlStr.length() > 1) {
                urlStr = urlStr.substring(0, urlStr.length() - 1);
            }
            this.baseUrl = urlStr;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL: " + urlStr);
        }
    }

    private void run() throws Exception {
        if (simulatePlatformRaw != null) {
            runSimulation();
            return;
        }
        if (dumpSwml) {
            doDumpSwml();
        } else if (listTools) {
            doListTools();
        } else if (execTool != null) {
            doExecTool();
        }
    }

    // ── Simulate-serverless mode ─────────────────────────────────────

    /**
     * Drive the agent through the serverless adapter with a simulated
     * env. Steps:
     * <ol>
     *   <li>Parse + validate the platform (reject unimplemented ones).</li>
     *   <li>Build a layered {@link EnvProvider} that masks
     *       {@code SWML_PROXY_URL_BASE} and overlays simulated values.</li>
     *   <li>Warn if the real env had {@code SWML_PROXY_URL_BASE} set —
     *       the simulated view hides it, matching Python's behaviour.</li>
     *   <li>Load the agent class by reflection, call its factory method,
     *       rebuild through {@link AgentBase.Builder#envProvider(EnvProvider)}
     *       if the user returned a raw-{@code build()} instance.</li>
     *   <li>Route the request through the platform's adapter (e.g.
     *       {@link LambdaAgentHandler}) — NOT the HTTP server.</li>
     *   <li>Nothing to restore — we never touched the real process env.
     *       (The {@code try/finally} is still there for symmetry with
     *       Python and for any future state-bearing resources.)</li>
     * </ol>
     */
    private void runSimulation() throws Exception {
        ServerlessSimulator.Platform platform =
                ServerlessSimulator.parsePlatform(simulatePlatformRaw);

        EnvProvider realEnv = realEnvOverride != null ? realEnvOverride : EnvProvider.SYSTEM;
        ServerlessSimulator simulator =
                new ServerlessSimulator(platform, realEnv, Collections.emptyMap());

        if (simulator.proxyUrlBaseMaskedFromRealEnv()) {
            System.err.println(
                    "[warning] SWML_PROXY_URL_BASE is set in the real environment "
                            + "but the --simulate-serverless harness is masking it so "
                            + "the " + platform.name().toLowerCase()
                            + " adapter's URL synthesis is actually exercised. "
                            + "To use the real proxy base, drop --simulate-serverless.");
        }

        EnvProvider simEnv = simulator.buildEnvProvider();

        if (verbose) {
            System.err.println("[verbose] Simulating platform: " + platform);
            System.err.println("[verbose] Simulated env keys: "
                    + simulator.getSimulatedEnv().keySet());
            System.err.println("[verbose] Masked env keys: " + simulator.getMaskedKeys());
        }

        try {
            AgentBase agent = loadAgent(agentClassName, simEnv);

            switch (platform) {
                case LAMBDA -> runLambdaSimulation(agent, simEnv);
                default -> throw new IllegalArgumentException(
                        "No adapter wired up for platform: " + platform);
            }
        } finally {
            // Nothing to restore: simEnv is a local view, never applied
            // to System.getenv(). Kept as a finally block for parity
            // with Python's mock_env.py contract ("restore on any
            // exit path") so future state-bearing additions can't forget.
        }
    }

    private void runLambdaSimulation(AgentBase agent, EnvProvider simEnv) throws Exception {
        LambdaAgentHandler handler = new LambdaAgentHandler(agent, simEnv);

        String route = normaliseAgentRoute(agent);
        String path = route.isEmpty() ? "/" : route;

        if (execTool != null) {
            String body = buildSwaigRequestJson(execTool, params);
            Map<String, Object> event = buildApiGatewayV2Event(
                    "POST",
                    route + "/swaig",
                    basicAuthHeader(agent),
                    body);
            if (verbose) System.err.println("[verbose] Dispatching SWAIG: " + body);
            LambdaResponse response = handler.handle(event);
            emitResponse(response);
            return;
        }

        // Bare or --dump-swml: render the SWML document through the adapter.
        Map<String, Object> event = buildApiGatewayV2Event(
                "GET",
                path,
                basicAuthHeader(agent),
                null);
        if (verbose) System.err.println("[verbose] Dispatching SWML render: GET " + path);
        LambdaResponse response = handler.handle(event);
        emitResponse(response);
    }

    private void emitResponse(LambdaResponse response) {
        if (response.getStatusCode() >= 400) {
            System.err.println("[warning] Lambda adapter returned HTTP "
                    + response.getStatusCode());
        }
        String body = response.getBody();
        if (body == null) body = "";
        if (raw) {
            System.out.println(body);
        } else {
            System.out.println(prettyPrintJson(body));
        }
    }

    private static String normaliseAgentRoute(AgentBase agent) {
        String r = agent.getNormalisedRoute();
        return r == null ? "" : r;
    }

    private static Map<String, String> basicAuthHeader(AgentBase agent) {
        Map<String, String> h = new LinkedHashMap<>();
        String creds = agent.getAuthUser() + ":" + (agent.getAuthPassword() != null
                ? agent.getAuthPassword() : "");
        String encoded = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
        h.put("Authorization", "Basic " + encoded);
        h.put("Content-Type", "application/json");
        return h;
    }

    private static Map<String, Object> buildApiGatewayV2Event(String method, String path,
                                                              Map<String, String> headers,
                                                              String body) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("version", "2.0");
        event.put("rawPath", path);
        event.put("headers", headers != null ? headers : new LinkedHashMap<>());
        if (body != null) event.put("body", body);
        Map<String, Object> http = new LinkedHashMap<>();
        http.put("method", method);
        http.put("path", path);
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("http", http);
        event.put("requestContext", ctx);
        return event;
    }

    /**
     * Build a SWAIG invocation payload. Mirrors the structure the real
     * HTTP server expects and the Python simulation produces.
     */
    private static String buildSwaigRequestJson(String tool, Map<String, String> params) {
        StringBuilder json = new StringBuilder();
        json.append("{\"function\":\"").append(escapeJsonStatic(tool)).append("\"");
        if (!params.isEmpty()) {
            json.append(",\"argument\":{\"parsed\":[{");
            boolean first = true;
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(escapeJsonStatic(e.getKey())).append("\":\"")
                        .append(escapeJsonStatic(e.getValue())).append("\"");
                first = false;
            }
            json.append("}]}");
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Load the agent by reflection. The CLI expects the supplied class
     * to expose a {@code public static AgentBase} factory method whose
     * name is one of (in order of preference):
     * {@code createAgent}, {@code buildAgent}, {@code newAgent},
     * {@code getAgent}. If the factory method accepts an
     * {@link EnvProvider} parameter, the simulated env is passed through
     * directly. Otherwise the returned agent is rebuilt with the
     * simulated env applied to its {@link AgentBase.Builder}.
     *
     * <p>Users whose agents live entirely inside a {@code main()} method
     * should extract the build logic into a public static factory —
     * that's the documented pattern for making an agent testable through
     * this CLI (and is what {@code examples/LambdaAgent.java} already
     * does with its {@code buildAgent()} method).
     */
    private AgentBase loadAgent(String className, EnvProvider simEnv) throws Exception {
        Class<?> cls;
        try {
            cls = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "Agent class not found on classpath: " + className
                            + ". Ensure the compiled class is on the classpath.");
        }

        // Preferred: factory method that takes EnvProvider and returns AgentBase.
        for (String name : List.of("createAgent", "buildAgent", "newAgent", "getAgent")) {
            try {
                Method m = cls.getDeclaredMethod(name, EnvProvider.class);
                m.setAccessible(true);
                Object result = m.invoke(null, simEnv);
                if (result instanceof AgentBase agent) {
                    return agent;
                }
                throw new IllegalStateException(
                        className + "." + name + "(EnvProvider) must return AgentBase");
            } catch (NoSuchMethodException ignored) {
                // Try next name or signature.
            }
        }

        // Fallback: no-arg factory. The agent was built with real env
        // (which the simulator deliberately did not mutate), so we accept
        // the instance as-is and trust the injected EnvProvider in the
        // adapter to mask SWML_PROXY_URL_BASE. Warn the user that build-
        // time env reads (SWML_BASIC_AUTH_*, SWML_PROXY_URL_BASE) won't
        // see the simulated values — for full isolation they should use
        // the EnvProvider-aware factory.
        for (String name : List.of("createAgent", "buildAgent", "newAgent", "getAgent")) {
            try {
                Method m = cls.getDeclaredMethod(name);
                m.setAccessible(true);
                Object result = m.invoke(null);
                if (result instanceof AgentBase agent) {
                    if (verbose) {
                        System.err.println("[verbose] Loaded agent via "
                                + className + "." + name + "() (no EnvProvider).");
                    }
                    return agent;
                }
                throw new IllegalStateException(
                        className + "." + name + "() must return AgentBase");
            } catch (NoSuchMethodException ignored) {
                // Try next.
            }
        }

        throw new IllegalArgumentException(
                "Could not find a factory method on " + className + ". "
                        + "Expected a public static method named one of "
                        + "[createAgent, buildAgent, newAgent, getAgent] "
                        + "returning AgentBase (optionally taking EnvProvider).");
    }

    private void doDumpSwml() throws Exception {
        if (verbose) System.err.println("[verbose] GET " + baseUrl);

        String response = httpGet(baseUrl);
        if (raw) {
            System.out.println(response);
        } else {
            System.out.println(prettyPrintJson(response));
        }
    }

    private void doListTools() throws Exception {
        if (verbose) System.err.println("[verbose] GET " + baseUrl + " (fetching SWML to extract tools)");

        String response = httpGet(baseUrl);

        // Extract function names from SWML JSON
        // Look for "functions" array, each with "function" key
        List<String> tools = extractToolNames(response);
        if (tools.isEmpty()) {
            System.out.println("No tools found.");
        } else {
            System.out.println("Tools (" + tools.size() + "):");
            for (String tool : tools) {
                System.out.println("  - " + tool);
            }
        }
    }

    private void doExecTool() throws Exception {
        String swaigUrl = baseUrl + "/swaig";
        if (verbose) System.err.println("[verbose] POST " + swaigUrl + " (exec: " + execTool + ")");

        // Build SWAIG request payload
        StringBuilder json = new StringBuilder();
        json.append("{\"function\":\"").append(escapeJson(execTool)).append("\"");

        if (!params.isEmpty()) {
            json.append(",\"argument\":{\"parsed\":[{");
            boolean first = true;
            for (var entry : params.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
                    .append(escapeJson(entry.getValue())).append("\"");
                first = false;
            }
            json.append("}]}");
        }
        json.append("}");

        if (verbose) System.err.println("[verbose] Payload: " + json);

        String response = httpPost(swaigUrl, json.toString());
        if (raw) {
            System.out.println(response);
        } else {
            System.out.println(prettyPrintJson(response));
        }
    }

    // ── HTTP helpers using java.net.http ──────────────────────────────

    private String httpGet(String url) throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET();
        addAuthHeader(builder);
        var req = builder.build();
        var resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (verbose) System.err.println("[verbose] Response status: " + resp.statusCode());
        if (resp.statusCode() >= 400) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }

    private String httpPost(String url, String body) throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        addAuthHeader(builder);
        var req = builder.build();
        var resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (verbose) System.err.println("[verbose] Response status: " + resp.statusCode());
        if (resp.statusCode() >= 400) {
            throw new IOException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp.body();
    }

    private void addAuthHeader(HttpRequest.Builder builder) {
        if (authUser != null) {
            String creds = authUser + ":" + (authPassword != null ? authPassword : "");
            String encoded = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        }
    }

    // ── JSON helpers (no external deps) ──────────────────────────────

    /**
     * Extract tool names from SWML JSON response.
     * Looks for "function":"name" patterns within the functions array.
     */
    private List<String> extractToolNames(String json) {
        List<String> names = new ArrayList<>();
        // Simple pattern matching: find "function":"value" patterns
        String marker = "\"function\":\"";
        int idx = 0;
        while ((idx = json.indexOf(marker, idx)) >= 0) {
            int start = idx + marker.length();
            int end = json.indexOf("\"", start);
            if (end > start) {
                String name = json.substring(start, end);
                // Skip "function" key that's part of SWML version/sections structure
                if (!name.isEmpty() && !name.contains("{") && !name.contains("}")) {
                    names.add(name);
                }
            }
            idx = start;
        }
        return names;
    }

    /**
     * Minimal JSON pretty-printer (no dependencies).
     */
    private static String prettyPrintJson(String json) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                sb.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                sb.append(c);
                continue;
            }

            if (inString) {
                sb.append(c);
                continue;
            }

            switch (c) {
                case '{', '[' -> {
                    sb.append(c);
                    indent += 2;
                    sb.append('\n');
                    sb.append(" ".repeat(indent));
                }
                case '}', ']' -> {
                    indent -= 2;
                    sb.append('\n');
                    sb.append(" ".repeat(Math.max(0, indent)));
                    sb.append(c);
                }
                case ',' -> {
                    sb.append(c);
                    sb.append('\n');
                    sb.append(" ".repeat(indent));
                }
                case ':' -> sb.append(": ");
                case ' ', '\t', '\n', '\r' -> {
                    // skip whitespace
                }
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escapeJson(String s) {
        return escapeJsonStatic(s);
    }

    private static String escapeJsonStatic(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void printUsage() {
        System.err.println("""
                Usage:
                  swaig-test --url <URL> [--dump-swml | --list-tools | --exec <tool> [--param k=v ...]]
                  swaig-test <agent-class> --simulate-serverless <platform> [--dump-swml | --exec <tool> [--param k=v ...]]

                Options:
                  --url URL                       Agent URL (e.g. http://user:pass@localhost:3000)
                  --simulate-serverless PLATFORM  Load agent class and route through the serverless adapter.
                                                  Supported platforms: lambda
                  --dump-swml                     Fetch / render the SWML document
                  --list-tools                    List all registered SWAIG tools (URL mode only)
                  --exec NAME                     Execute a SWAIG tool by name
                  --param key=value               Pass a parameter to the tool (repeatable)
                  --raw                           Output raw JSON without pretty-printing
                  --verbose                       Show verbose debug output
                  --help, -h                      Show this help message

                Examples:
                  swaig-test --url http://user:pass@localhost:3000 --dump-swml
                  swaig-test --url http://user:pass@localhost:3000 --exec get_weather --param city=Austin
                  swaig-test MyAgent --simulate-serverless lambda --dump-swml
                  swaig-test MyAgent --simulate-serverless lambda --exec get_weather --param city=Austin

                Agent class convention (simulate-serverless mode):
                  The <agent-class> must be a fully-qualified Java class name with a public static
                  factory method returning AgentBase. Accepted method names (in order):
                    createAgent(EnvProvider) / buildAgent(EnvProvider) / newAgent(EnvProvider) / getAgent(EnvProvider)
                    createAgent()            / buildAgent()            / newAgent()            / getAgent()
                  The EnvProvider-aware signature is recommended — it lets the agent's build-time
                  env reads (SWML_BASIC_AUTH_*, SWML_PROXY_URL_BASE) see the simulated values.
                """);
    }
}
