package com.signalwire.sdk.runtime.lambda;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.runtime.EnvProvider;
import com.signalwire.sdk.runtime.LambdaUrlResolver;
import com.signalwire.sdk.swaig.FunctionResult;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AWS Lambda adapter for a SignalWire {@link AgentBase}.
 *
 * <p>Translates API Gateway (REST v1 or HTTP v2) / Lambda Function URL
 * events into the same dispatch logic the in-process HTTP server uses,
 * without depending on the {@code aws-lambda-java-events} typed models
 * at runtime (the handler accepts plain {@code Map<String, Object>} so
 * the SDK stays dependency-light).
 *
 * <p><b>Usage</b> — in your Lambda handler class:
 * <pre>{@code
 * public class MyHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
 *     private final LambdaAgentHandler delegate;
 *
 *     public MyHandler() {
 *         AgentBase agent = AgentBase.builder()
 *                 .name("my-agent")
 *                 .route("/")
 *                 .build();
 *         agent.setPromptText("You are helpful.");
 *         this.delegate = new LambdaAgentHandler(agent);
 *     }
 *
 *     public Map<String, Object> handleRequest(Map<String, Object> event, Context ctx) {
 *         return delegate.handle(event).toMap();
 *     }
 * }
 * }</pre>
 *
 * <p>The adapter dispatches based on the request path and the agent's
 * configured route:
 * <ul>
 *   <li>{@code GET  /<route>}          → returns rendered SWML</li>
 *   <li>{@code POST /<route>/swaig}    → executes the named SWAIG tool</li>
 *   <li>{@code POST /<route>/post_prompt} → invokes the summary callback</li>
 *   <li>{@code POST /<route>/mcp}      → JSON-RPC 2.0 (if MCP enabled)</li>
 *   <li>{@code GET  /health}, {@code /ready} → health probes</li>
 * </ul>
 *
 * <p>All webhook URLs generated inside the SWML document are built from
 * the Lambda's Function URL (via {@link LambdaUrlResolver}) or the
 * {@code SWML_PROXY_URL_BASE} override, with the agent's route always
 * layered on top. This is the same invariant the HTTP server path
 * enforces.
 */
public final class LambdaAgentHandler {

    private static final Logger log = Logger.getLogger(LambdaAgentHandler.class);
    private static final Gson gson = new Gson();

    private final AgentBase agent;
    private final EnvProvider env;
    private final LambdaUrlResolver urlResolver;

    /**
     * Create a handler for the given agent using the real process
     * environment.
     *
     * @param agent the configured agent.
     */
    public LambdaAgentHandler(AgentBase agent) {
        this(agent, EnvProvider.SYSTEM);
    }

    /**
     * Create a handler with an injected environment provider (for tests).
     *
     * @param agent the configured agent.
     * @param env environment variable source.
     */
    public LambdaAgentHandler(AgentBase agent, EnvProvider env) {
        if (agent == null) throw new IllegalArgumentException("agent must not be null");
        if (env == null) throw new IllegalArgumentException("env must not be null");
        this.agent = agent;
        this.env = env;
        this.urlResolver = new LambdaUrlResolver(env);
    }

    /**
     * Handle a single invocation.
     *
     * @param event the raw Lambda event (API Gateway v1, v2, or Function
     *     URL payload). Null events are treated as a root GET.
     * @return response envelope ready to convert to
     *     {@code APIGatewayProxyResponseEvent} or equivalent.
     */
    public LambdaResponse handle(Map<String, Object> event) {
        try {
            return dispatch(event);
        } catch (Exception e) {
            log.error("Lambda handler error", e);
            return LambdaResponse.json(500,
                    gson.toJson(Map.of("error", "Internal server error")));
        }
    }

    private LambdaResponse dispatch(Map<String, Object> event) {
        if (event == null) event = Map.of();

        String method = extractMethod(event).toUpperCase(Locale.ROOT);
        String rawPath = extractPath(event);
        String path = normalisePath(rawPath);

        // Health/readiness probes require no auth.
        if ("/health".equals(path)) {
            return LambdaResponse.json(gson.toJson(Map.of("status", "healthy")));
        }
        if ("/ready".equals(path)) {
            return LambdaResponse.json(gson.toJson(Map.of("status", "ready")));
        }

        String agentPath = agent.getNormalisedRoute(); // "" for root, "/foo" otherwise
        String rel = stripAgentPath(path, agentPath);
        if (rel == null) {
            // Request path doesn't belong to this agent.
            return LambdaResponse.json(404, gson.toJson(Map.of("error", "Not found")));
        }

        // MCP: unauthenticated endpoint (matches server behaviour).
        if ("/mcp".equals(rel)) {
            return handleMcp(event, method);
        }

        // Every other endpoint requires basic auth.
        if (!checkBasicAuth(event)) {
            return unauthorised();
        }

        switch (rel) {
            case "/swaig":
                return handleSwaig(event, method);
            case "/post_prompt":
                return handlePostPrompt(event, method);
            case "":
            case "/":
                return handleSwml(event, method);
            default:
                return LambdaResponse.json(404,
                        gson.toJson(Map.of("error", "Not found: " + rel)));
        }
    }

    // ------------------------------------------------------------------
    // Endpoint handlers
    // ------------------------------------------------------------------

    private LambdaResponse handleSwml(Map<String, Object> event, String method) {
        if (!"GET".equals(method) && !"POST".equals(method)) {
            return methodNotAllowed();
        }

        // Dynamic config support: clone and invoke callback if configured.
        AgentBase renderAgent = agent;
        AgentBase.DynamicConfigCallback cb = agent.getDynamicConfigCallback();
        if (cb != null) {
            renderAgent = agent.clone();
            Map<String, String> query = extractQuery(event);
            Map<String, Object> body = "POST".equals(method)
                    ? parseJsonBody(event)
                    : new LinkedHashMap<>();
            Map<String, List<String>> headerMap = new LinkedHashMap<>();
            Map<String, String> rawHeaders = extractHeaders(event);
            for (Map.Entry<String, String> e : rawHeaders.entrySet()) {
                headerMap.put(e.getKey(), List.of(e.getValue()));
            }
            try {
                cb.configure(query, body, headerMap, renderAgent);
            } catch (Exception ex) {
                log.error("Dynamic config callback threw", ex);
            }
        }

        String baseUrl = resolveBaseUrl();
        Map<String, Object> swml = renderAgent.renderSwml(baseUrl);
        return LambdaResponse.json(gson.toJson(swml));
    }

    private LambdaResponse handleSwaig(Map<String, Object> event, String method) {
        if (!"POST".equals(method)) return methodNotAllowed();

        Map<String, Object> payload = parseJsonBody(event);
        if (payload == null) {
            return LambdaResponse.json(400, gson.toJson(Map.of("error", "Invalid JSON")));
        }
        if (payload.isEmpty()) {
            return LambdaResponse.json(400, gson.toJson(Map.of("error", "Empty payload")));
        }

        String funcName = asString(payload.get("function"));
        if (funcName == null || funcName.isEmpty()) {
            return LambdaResponse.json(400, gson.toJson(Map.of("error", "Missing function name")));
        }
        if (!agent.hasTool(funcName)) {
            return LambdaResponse.json(404,
                    gson.toJson(Map.of("error", "Function not found: " + funcName)));
        }

        Map<String, Object> args = extractParsedArgs(payload);
        FunctionResult result = agent.onFunctionCall(funcName, args, payload);
        return LambdaResponse.json(gson.toJson(result.toMap()));
    }

    private LambdaResponse handlePostPrompt(Map<String, Object> event, String method) {
        if (!"POST".equals(method)) return methodNotAllowed();

        Map<String, Object> payload = parseJsonBody(event);
        if (payload == null) {
            return LambdaResponse.json(400, gson.toJson(Map.of("error", "Invalid JSON")));
        }

        var cb = agent.getOnSummaryCallback();
        if (cb != null) {
            Map<String, Object> parsed = null;
            Object ppData = payload.get("post_prompt_data");
            if (ppData instanceof Map<?, ?> ppMap) {
                Object p = ppMap.get("parsed");
                if (p instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsedCast = (Map<String, Object>) p;
                    parsed = parsedCast;
                }
            }
            try {
                cb.accept(parsed, payload);
            } catch (Exception e) {
                log.error("Error in summary callback", e);
            }
        }
        return LambdaResponse.json(gson.toJson(Map.of("status", "ok")));
    }

    private LambdaResponse handleMcp(Map<String, Object> event, String method) {
        if (!agent.isMcpServerEnabled()) {
            return LambdaResponse.json(404, gson.toJson(Map.of("error", "MCP not enabled")));
        }
        if (!"POST".equals(method)) return methodNotAllowed();

        Map<String, Object> payload = parseJsonBody(event);
        if (payload == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("jsonrpc", "2.0");
            err.put("id", null);
            err.put("error", Map.of("code", -32700, "message", "Parse error"));
            return LambdaResponse.json(gson.toJson(err));
        }
        Map<String, Object> response = agent.handleMcpRequest(payload);
        return LambdaResponse.json(gson.toJson(response));
    }

    // ------------------------------------------------------------------
    // Helpers: auth, URL, parsing
    // ------------------------------------------------------------------

    private boolean checkBasicAuth(Map<String, Object> event) {
        String header = getHeaderCaseInsensitive(extractHeaders(event), "authorization");
        if (header == null || !header.toLowerCase(Locale.ROOT).startsWith("basic ")) {
            return false;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(header.substring(6).trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        String creds = new String(decoded, StandardCharsets.UTF_8);
        int colon = creds.indexOf(':');
        if (colon < 0) return false;
        String u = creds.substring(0, colon);
        String p = creds.substring(colon + 1);
        return u.equals(agent.getAuthUser()) && p.equals(agent.getAuthPassword());
    }

    private LambdaResponse unauthorised() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "application/json");
        h.put("WWW-Authenticate", "Basic realm=\"SWML Agent\"");
        return new LambdaResponse(401, h,
                gson.toJson(Map.of("error", "Unauthorized")), false);
    }

    private LambdaResponse methodNotAllowed() {
        return LambdaResponse.json(405, gson.toJson(Map.of("error", "Method not allowed")));
    }

    private String resolveBaseUrl() {
        // The agent may already have a proxy base configured (from
        // SWML_PROXY_URL_BASE env or manual call). If so, defer to the
        // agent's own logic which preserves the route invariant.
        //
        // Pass the injected EnvProvider through so the simulate-serverless
        // harness (which masks the real env with a layered provider) sees
        // the same view the adapter uses.
        String agentBase = agent.detectServerlessBaseUrl(env);
        if (agentBase != null) return agentBase;

        // Otherwise synthesise from Lambda env vars using the injected
        // resolver (tests can supply their own env).
        String url = urlResolver.resolveBaseUrl();
        if (url != null) return url;

        // Absolute last-resort fallback so the SWML rendering doesn't
        // emit "null/foo/swaig". Use a synthetic local host — this
        // should almost never happen in practice.
        return "https://localhost";
    }

    // ------------------------------------------------------------------
    // Event-shape normalisation (API Gateway v1/v2, Function URL all
    // differ subtly in casing and field names)
    // ------------------------------------------------------------------

    private static String extractMethod(Map<String, Object> event) {
        Object m = event.get("httpMethod"); // API GW REST v1
        if (m instanceof String s) return s;

        // API GW HTTP v2 / Function URL
        Object ctx = event.get("requestContext");
        if (ctx instanceof Map<?, ?> ctxMap) {
            Object http = ctxMap.get("http");
            if (http instanceof Map<?, ?> httpMap) {
                Object method = httpMap.get("method");
                if (method instanceof String s) return s;
            }
        }
        Object direct = event.get("method");
        if (direct instanceof String s) return s;
        return "GET";
    }

    private static String extractPath(Map<String, Object> event) {
        Object raw = event.get("rawPath"); // API GW HTTP v2 / Function URL
        if (raw instanceof String s) return s;

        Object path = event.get("path"); // API GW REST v1
        if (path instanceof String s) return s;

        // path parameters fallback
        Object params = event.get("pathParameters");
        if (params instanceof Map<?, ?> pm) {
            Object proxy = pm.get("proxy");
            if (proxy instanceof String s) return "/" + s;
        }
        return "/";
    }

    private static String normalisePath(String p) {
        if (p == null || p.isEmpty()) return "/";
        if (!p.startsWith("/")) p = "/" + p;
        // Strip trailing slash except for root.
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /**
     * Strip the agent's route prefix from an incoming path.
     *
     * <p>{@code agentPath} is {@code ""} for root or e.g. {@code "/my-agent"}.
     * Returns the remainder (e.g. {@code "/swaig"}) or {@code null} if the
     * path doesn't start with the agent's route.
     */
    static String stripAgentPath(String path, String agentPath) {
        if (agentPath.isEmpty()) return path;
        if (path.equals(agentPath)) return "";
        if (path.startsWith(agentPath + "/")) return path.substring(agentPath.length());
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> extractHeaders(Map<String, Object> event) {
        Object h = event.get("headers");
        if (h instanceof Map<?, ?> map) {
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    out.put(e.getKey().toString(), e.getValue().toString());
                }
            }
            return out;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> extractQuery(Map<String, Object> event) {
        Object q = event.get("queryStringParameters");
        if (q instanceof Map<?, ?> map) {
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    out.put(e.getKey().toString(), e.getValue().toString());
                }
            }
            return out;
        }
        return new LinkedHashMap<>();
    }

    private static String getHeaderCaseInsensitive(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    private static Map<String, Object> parseJsonBody(Map<String, Object> event) {
        Object body = event.get("body");
        if (body == null) return new LinkedHashMap<>();

        String text;
        if (body instanceof String s) {
            text = s;
            Object isB64 = event.get("isBase64Encoded");
            if (Boolean.TRUE.equals(isB64)) {
                try {
                    text = new String(Base64.getDecoder().decode(s),
                            StandardCharsets.UTF_8);
                } catch (IllegalArgumentException ex) {
                    return null;
                }
            }
        } else if (body instanceof Map<?, ?>) {
            // Already-parsed body (common in test harnesses).
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) body;
            return m;
        } else {
            return null;
        }

        if (text.isEmpty()) return new LinkedHashMap<>();

        try {
            Type t = new TypeToken<Map<String, Object>>() { }.getType();
            Map<String, Object> parsed = gson.fromJson(text, t);
            return parsed != null ? parsed : new LinkedHashMap<>();
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractParsedArgs(Map<String, Object> payload) {
        Map<String, Object> args = new LinkedHashMap<>();
        Object argument = payload.get("argument");
        if (argument instanceof Map<?, ?> argMap) {
            Object parsed = argMap.get("parsed");
            if (parsed instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Map<?, ?> firstMap) {
                    args.putAll((Map<String, Object>) firstMap);
                }
            } else if (argMap.get("raw") instanceof String raw) {
                try {
                    Type t = new TypeToken<Map<String, Object>>() { }.getType();
                    Map<String, Object> parsedRaw = gson.fromJson(raw, t);
                    if (parsedRaw != null) args.putAll(parsedRaw);
                } catch (Exception ignored) {
                    // Fall through with empty args.
                }
            }
        }
        return args;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }
}
