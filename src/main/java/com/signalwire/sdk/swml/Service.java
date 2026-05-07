/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.signalwire.sdk.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Base SWML service with an embedded HTTP server, basic auth, security headers,
 * and explicit methods for all 38 schema-driven verbs.
 * <p>
 * Uses JDK built-in com.sun.net.httpserver.HttpServer with virtual threads.
 */
public class Service {

    private static final Logger log = Logger.getLogger(Service.class);
    private static final int MAX_REQUEST_BODY_SIZE = 1_048_576; // 1 MB
    private static final Gson gson = new Gson();

    protected final String name;
    protected String route;
    protected String host;
    protected int port;
    protected final Document document;
    // SchemaUtils helper — Python parity at
    // signalwire.utils.schema_utils.SchemaUtils. Built lazily so existing
    // subclasses constructed without the schema env still work.
    protected SchemaUtils schemaUtilsInstance;

    // SWAIG tool registry — lifted from AgentBase so any Service (sidecar,
    // non-agent verb host) can register and dispatch SWAIG functions.
    protected final java.util.Map<String, com.signalwire.sdk.swaig.ToolDefinition> tools =
            new java.util.LinkedHashMap<>();
    protected final java.util.List<java.util.Map<String, Object>> registeredSwaigFunctions =
            new java.util.ArrayList<>();

    private static final java.util.regex.Pattern SWAIG_FN_NAME =
            java.util.regex.Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * HttpExchange attribute key under which the raw POST body is stashed by
     * {@link #serve()} when reading it up-front for signature validation.
     * Subclasses (notably {@link com.signalwire.sdk.agent.AgentBase}) check
     * this attribute in {@code renderMainSwml} so they can re-use the cached
     * body without re-reading the (already-consumed) request stream.
     */
    public static final String REQUEST_BODY_ATTR = "com.signalwire.sdk.requestBody";

    // Auth — protected so AgentBase (extends Service) can read them in subclass
    // helpers. Don't expose via getters that mutate; use the constructor.
    protected String authUser;
    protected String authPassword;

    // HTTP server — protected so AgentBase can register additional routes
    protected HttpServer httpServer;

    public Service(String name) {
        this(name, "/", "0.0.0.0", resolvePort(), null, null);
    }

    public Service(String name, String route) {
        this(name, route, "0.0.0.0", resolvePort(), null, null);
    }

    public Service(String name, String route, String host, int port,
                   String authUser, String authPassword) {
        this.name = name;
        this.route = route.endsWith("/") && route.length() > 1
                ? route.substring(0, route.length() - 1) : route;
        this.host = host;
        this.port = port;
        this.document = new Document();

        // Auth setup
        if (authUser != null && authPassword != null) {
            this.authUser = authUser;
            this.authPassword = authPassword;
        } else {
            String envUser = System.getenv("SWML_BASIC_AUTH_USER");
            String envPass = System.getenv("SWML_BASIC_AUTH_PASSWORD");
            this.authUser = (envUser != null && !envUser.isEmpty()) ? envUser : name;
            this.authPassword = (envPass != null && !envPass.isEmpty()) ? envPass : generatePassword();
        }

        log.info("Service '%s' initialized with auth user: %s", name, this.authUser);
    }

    protected static int resolvePort() {
        String envPort = System.getenv("PORT");
        if (envPort != null) {
            try {
                return Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {
            }
        }
        return 3000;
    }

    protected static String generatePassword() {
        var random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // -------- Auth --------

    public String getAuthUser() {
        return authUser;
    }

    public String getAuthPassword() {
        return authPassword;
    }

    /** Validate provided basic-auth credentials against the configured ones
     * using a constant-time comparison. (Python parity:
     * ``AuthMixin.validate_basic_auth(username, password)``.) */
    public boolean validateBasicAuth(String username, String password) {
        if (authUser == null || authPassword == null) return false;
        return java.security.MessageDigest.isEqual(
                username.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                authUser.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            && java.security.MessageDigest.isEqual(
                password.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                authPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Get the configured (user, password) pair as a String[2] tuple.
     * (Python parity: ``AuthMixin.get_basic_auth_credentials``.) */
    public String[] getBasicAuthCredentials() {
        return new String[] { authUser != null ? authUser : "", authPassword != null ? authPassword : "" };
    }

    /** Get (user, password, source) where source is "provided",
     * "environment", or "generated". (Python parity:
     * ``AuthMixin.get_basic_auth_credentials(include_source=True)``.) */
    public String[] getBasicAuthCredentialsWithSource() {
        String user = authUser != null ? authUser : "";
        String pass = authPassword != null ? authPassword : "";
        String envUser = System.getenv("SWML_BASIC_AUTH_USER");
        String envPass = System.getenv("SWML_BASIC_AUTH_PASSWORD");
        String source;
        if (envUser != null && !envUser.isEmpty()
            && envPass != null && !envPass.isEmpty()
            && user.equals(envUser) && pass.equals(envPass)) {
            source = "environment";
        } else if (user.startsWith("user_") && pass.length() > 20) {
            source = "generated";
        } else {
            source = "provided";
        }
        return new String[] { user, pass, source };
    }

    /**
     * Timing-safe basic auth validation using MessageDigest.isEqual.
     */
    protected boolean validateAuth(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }

        String encoded = authHeader.substring(6);
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            return false;
        }

        String credentials = new String(decoded, StandardCharsets.UTF_8);
        int colonIdx = credentials.indexOf(':');
        if (colonIdx < 0) {
            return false;
        }

        String user = credentials.substring(0, colonIdx);
        String pass = credentials.substring(colonIdx + 1);

        // Timing-safe comparison for both user and password
        boolean userMatch = MessageDigest.isEqual(
                user.getBytes(StandardCharsets.UTF_8),
                authUser.getBytes(StandardCharsets.UTF_8));
        boolean passMatch = MessageDigest.isEqual(
                pass.getBytes(StandardCharsets.UTF_8),
                authPassword.getBytes(StandardCharsets.UTF_8));

        return userMatch && passMatch;
    }

    // ------------------------------------------------------------------
    // SWAIG tool registry (lifted from AgentBase)
    // ------------------------------------------------------------------

    /**
     * Define a SWAIG function the AI can call. Tool descriptions and
     * parameter descriptions are LLM-facing prompt engineering — see
     * PORTING_GUIDE for guidance on writing them.
     */
    public Service defineTool(String name, String description,
                              java.util.Map<String, Object> parameters,
                              com.signalwire.sdk.swaig.ToolHandler handler) {
        tools.put(name, new com.signalwire.sdk.swaig.ToolDefinition(name, description, parameters, handler));
        return this;
    }

    /** Register a SWAIG tool from a pre-built ToolDefinition. */
    public Service defineTool(com.signalwire.sdk.swaig.ToolDefinition toolDef) {
        tools.put(toolDef.getName(), toolDef);
        return this;
    }

    /** Register a raw SWAIG function definition (e.g. DataMap tools). */
    public Service registerSwaigFunction(java.util.Map<String, Object> swaigFunc) {
        registeredSwaigFunctions.add(new java.util.LinkedHashMap<>(swaigFunc));
        return this;
    }

    /** Register multiple tool definitions at once. */
    public Service defineTools(java.util.List<com.signalwire.sdk.swaig.ToolDefinition> toolDefs) {
        for (var def : toolDefs) {
            defineTool(def);
        }
        return this;
    }

    /** Dispatch a function call to the registered handler. */
    public com.signalwire.sdk.swaig.FunctionResult onFunctionCall(
            String funcName,
            java.util.Map<String, Object> args,
            java.util.Map<String, Object> rawData) {
        var tool = tools.get(funcName);
        if (tool == null) {
            return null;
        }
        return tool.getHandler().handle(args, rawData);
    }

    /** List registered SWAIG tool names in insertion order. */
    public java.util.List<String> listToolNames() {
        return new java.util.ArrayList<>(tools.keySet());
    }

    /** Whether a SWAIG function with the given name is registered.
     * (Python parity: ``ToolRegistry.has_function``.) */
    public boolean hasFunction(String name) {
        return tools.containsKey(name);
    }

    /** Get a registered SWAIG function by name, or null when absent.
     * (Python parity: ``ToolRegistry.get_function``.) */
    public com.signalwire.sdk.swaig.ToolDefinition getFunction(String name) {
        return tools.get(name);
    }

    /** Snapshot of all registered SWAIG functions keyed by name.
     * (Python parity: ``ToolRegistry.get_all_functions``.) */
    public java.util.Map<String, com.signalwire.sdk.swaig.ToolDefinition> getAllFunctions() {
        return new java.util.LinkedHashMap<>(tools);
    }

    /** Remove a registered SWAIG function. Returns true when removed,
     * false when the function was not registered.
     * (Python parity: ``ToolRegistry.remove_function``.) */
    public boolean removeFunction(String name) {
        return tools.remove(name) != null;
    }

    /**
     * Public, read-only view of the registered SWAIG tool registry.
     * Returned in insertion order; the map and its definitions are
     * unmodifiable. Used by introspection callers (CLI {@code --list-tools}
     * file-loader path, tests, audit tooling) that need name + description +
     * parameters without going through {@code /swaig} HTTP.
     */
    public java.util.Map<String, com.signalwire.sdk.swaig.ToolDefinition> getRegisteredTools() {
        return java.util.Collections.unmodifiableMap(tools);
    }

    /**
     * Read-only view of the raw SWAIG function entries registered via
     * {@link #registerSwaigFunction(java.util.Map)}. These are typically
     * DataMap or schema-only tools that don't have a Java {@link com.signalwire.sdk.swaig.ToolHandler}.
     * Each entry is a defensive copy of the original map; the outer list
     * is unmodifiable.
     */
    public java.util.List<java.util.Map<String, Object>> getRegisteredSwaigFunctions() {
        java.util.List<java.util.Map<String, Object>> copy = new java.util.ArrayList<>(registeredSwaigFunctions.size());
        for (var fn : registeredSwaigFunctions) {
            copy.add(java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fn)));
        }
        return java.util.Collections.unmodifiableList(copy);
    }

    /**
     * Extension point: invoked between argument parsing and function
     * dispatch. Returns a 2-element array: [target Service, shortCircuit Map].
     * If shortCircuit is non-null, it's returned as the SWAIG response
     * without calling onFunctionCall. AgentBase may override to add
     * session-token validation or ephemeral dynamic-config copies.
     */
    protected Object[] swaigPreDispatch(java.util.Map<String, Object> requestData,
                                        String funcName) {
        return new Object[] { this, null };
    }

    /**
     * Extension point: render the SWML document for the main path or for
     * GET /swaig. Default returns the currently-built Document. AgentBase
     * overrides to emit prompt + AI verb at request time.
     */
    protected java.util.Map<String, Object> renderMainSwml(HttpExchange exchange) {
        return document.toMap();
    }

    /**
     * Extension point: register additional HTTP routes after Service
     * mounts /health, /ready, /swaig and the main route. AgentBase uses
     * this to add /post_prompt and /mcp.
     */
    protected void registerAdditionalRoutes(HttpServer server) {
    }

    /**
     * Customization hook called when SWML is requested. Default
     * delegates to {@link #onSwmlRequest(java.util.Map, String)} and
     * returns its result. Subclasses typically override
     * {@code onSwmlRequest} rather than this method.
     *
     * <p>Returning {@code null} uses the default rendered SWML;
     * returning a non-null map merges the entries as modifications.
     *
     * <p>Python parity: {@code WebMixin.on_request(request_data,
     * callback_path)}. The Python third {@code request} parameter is
     * FastAPI-specific and intentionally not mirrored.
     *
     * @param requestData parsed request body, or {@code null}
     * @param callbackPath optional callback sub-path, or {@code null}
     * @return modifications map, or {@code null} for default rendering
     */
    public java.util.Map<String, Object> onRequest(
            java.util.Map<String, Object> requestData,
            String callbackPath) {
        return onSwmlRequest(requestData, callbackPath);
    }

    /**
     * Customization point for subclasses to modify SWML based on
     * request data. The default implementation returns {@code null}
     * (no modification). Subclasses override to inspect the body or
     * callback path and return a map of SWML overrides.
     *
     * <p>Python parity: {@code WebMixin.on_swml_request(request_data,
     * callback_path)}. The Python third {@code request} parameter is
     * FastAPI-specific and intentionally not mirrored.
     *
     * @param requestData parsed request body, or {@code null}
     * @param callbackPath optional callback sub-path, or {@code null}
     * @return modifications map, or {@code null}
     */
    public java.util.Map<String, Object> onSwmlRequest(
            java.util.Map<String, Object> requestData,
            String callbackPath) {
        return null;
    }

    /**
     * Extension hook invoked between raw-body capture and JSON parsing on
     * signed POST routes ({@code /}, {@code /swaig}, {@code /post_prompt}).
     * Subclasses (AgentBase) override to enforce SignalWire webhook signature
     * validation when a signing key is configured. Default returns
     * {@code true} (no validation).
     *
     * <p>Returning {@code false} signals "signature invalid"; the caller
     * sends {@code 403 Forbidden} and stops dispatch. Per
     * porting-sdk/webhooks.md the response body must NOT disclose which
     * branch failed.
     *
     * @param exchange the HTTP exchange.
     * @param rawBody the raw UTF-8 body string already read from the
     *                exchange. Pass through to {@link com.signalwire.sdk.security.WebhookValidator}.
     * @return {@code true} when validation passes (or is disabled);
     *         {@code false} to short-circuit with a 403.
     */
    protected boolean validateSignedWebhook(HttpExchange exchange, String rawBody) {
        return true;
    }

    /**
     * Add security headers to every authenticated response.
     */
    protected void addSecurityHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Cache-Control", "no-store");
    }

    // -------- Document access --------

    public Document getDocument() {
        return document;
    }

    /**
     * SchemaUtils helper bound to this Service.  Mirrors Python's
     * {@code self.schema_utils} public instance attribute on
     * {@code SWMLService}.  Built lazily on first access.
     */
    public SchemaUtils getSchemaUtils() {
        if (schemaUtilsInstance == null) {
            schemaUtilsInstance = new SchemaUtils(null, true);
        }
        return schemaUtilsInstance;
    }

    // -------- 38 Schema-Driven Verb Methods --------
    // Each method adds the verb to the document's main section.
    // Java has no method_missing, so all are explicit.

    public Service answer(Map<String, Object> params) {
        document.addVerb("answer", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service ai(Map<String, Object> params) {
        document.addVerb("ai", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service amazonBedrock(Map<String, Object> params) {
        document.addVerb("amazon_bedrock", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service cond(List<Map<String, Object>> conditions) {
        document.addVerb("cond", conditions);
        return this;
    }

    public Service connect(Map<String, Object> params) {
        document.addVerb("connect", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service denoise(Map<String, Object> params) {
        document.addVerb("denoise", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service detectMachine(Map<String, Object> params) {
        document.addVerb("detect_machine", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service enterQueue(Map<String, Object> params) {
        document.addVerb("enter_queue", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service execute(Map<String, Object> params) {
        document.addVerb("execute", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service gotoLabel(Map<String, Object> params) {
        document.addVerb("goto", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service hangup(Map<String, Object> params) {
        document.addVerb("hangup", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service hangup() {
        document.addVerb("hangup", new LinkedHashMap<>());
        return this;
    }

    public Service joinConference(Map<String, Object> params) {
        document.addVerb("join_conference", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service joinRoom(Map<String, Object> params) {
        document.addVerb("join_room", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service label(Map<String, Object> params) {
        document.addVerb("label", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service liveTranscribe(Map<String, Object> params) {
        document.addVerb("live_transcribe", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service liveTranslate(Map<String, Object> params) {
        document.addVerb("live_translate", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service pay(Map<String, Object> params) {
        document.addVerb("pay", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service play(Map<String, Object> params) {
        document.addVerb("play", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service prompt(Map<String, Object> params) {
        document.addVerb("prompt", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service receiveFax(Map<String, Object> params) {
        document.addVerb("receive_fax", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service record(Map<String, Object> params) {
        document.addVerb("record", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service recordCall(Map<String, Object> params) {
        document.addVerb("record_call", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service request(Map<String, Object> params) {
        document.addVerb("request", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service returnVerb(Map<String, Object> params) {
        document.addVerb("return", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service sipRefer(Map<String, Object> params) {
        document.addVerb("sip_refer", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service sendDigits(Map<String, Object> params) {
        document.addVerb("send_digits", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service sendFax(Map<String, Object> params) {
        document.addVerb("send_fax", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service sendSms(Map<String, Object> params) {
        document.addVerb("send_sms", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service set(Map<String, Object> params) {
        document.addVerb("set", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    /**
     * Sleep takes an integer (milliseconds), not a map.
     */
    public Service sleep(int milliseconds) {
        document.addVerb("sleep", milliseconds);
        return this;
    }

    public Service stopDenoise(Map<String, Object> params) {
        document.addVerb("stop_denoise", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service stopRecordCall(Map<String, Object> params) {
        document.addVerb("stop_record_call", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service stopTap(Map<String, Object> params) {
        document.addVerb("stop_tap", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service switchVerb(Map<String, Object> params) {
        document.addVerb("switch", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service tap(Map<String, Object> params) {
        document.addVerb("tap", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service transfer(Map<String, Object> params) {
        document.addVerb("transfer", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service unset(Map<String, Object> params) {
        document.addVerb("unset", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    public Service userEvent(Map<String, Object> params) {
        document.addVerb("user_event", params != null ? params : new LinkedHashMap<>());
        return this;
    }

    // -------- HTTP Server --------

    /**
     * Read request body with size limit.
     */
    protected String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            byte[] buf = new byte[MAX_REQUEST_BODY_SIZE + 1];
            int total = 0;
            int n;
            while ((n = is.read(buf, total, buf.length - total)) > 0) {
                total += n;
                if (total > MAX_REQUEST_BODY_SIZE) {
                    throw new IOException("Request body exceeds maximum size");
                }
            }
            return new String(buf, 0, total, StandardCharsets.UTF_8);
        }
    }

    /**
     * Send a JSON response.
     */
    protected void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        String json = gson.toJson(body);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Send a 401 Unauthorized response.
     */
    protected void sendUnauthorized(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"SWML Service\"");
        exchange.sendResponseHeaders(401, -1);
        exchange.close();
    }

    /**
     * Send a 413 Payload Too Large response.
     */
    protected void sendPayloadTooLarge(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(413, -1);
        exchange.close();
    }

    /**
     * Start the HTTP server with health, ready, /swaig, and main SWML endpoint.
     * Subclasses (AgentBase) add additional routes via
     * {@link #registerAdditionalRoutes(HttpServer)} and customize SWML
     * rendering via {@link #renderMainSwml(HttpExchange)}.
     */
    public void serve() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // Health endpoint (no auth)
        httpServer.createContext("/health", exchange -> {
            try {
                sendJson(exchange, 200, Map.of("status", "healthy"));
            } catch (Exception e) {
                log.error("Health handler error", e);
            }
        });

        // Ready endpoint (no auth)
        httpServer.createContext("/ready", exchange -> {
            try {
                sendJson(exchange, 200, Map.of("status", "ready"));
            } catch (Exception e) {
                log.error("Ready handler error", e);
            }
        });

        String basePath = route.equals("/") ? "" : route;

        // SWAIG endpoint (with auth) — GET returns SWML, POST dispatches a tool.
        httpServer.createContext(basePath + "/swaig", exchange -> {
            try {
                handleSwaigEndpoint(exchange);
            } catch (Exception e) {
                log.error("SWAIG handler error", e);
                try { exchange.sendResponseHeaders(500, -1); exchange.close(); }
                catch (Exception ignored) {}
            }
        });

        // Subclass extension hook — AgentBase adds /post_prompt, /mcp here.
        registerAdditionalRoutes(httpServer);

        // Main SWML endpoint (with auth)
        String swmlPath = basePath.isEmpty() ? "/" : basePath;
        httpServer.createContext(swmlPath, exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                // Don't shadow sub-paths owned by sibling handlers.
                if (path.equals(basePath + "/swaig")
                        || path.equals(basePath + "/post_prompt")
                        || path.equals(basePath + "/mcp")) {
                    return;
                }
                if (!validateAuth(exchange)) {
                    sendUnauthorized(exchange);
                    return;
                }
                addSecurityHeaders(exchange);

                // For POST requests, capture the body BEFORE rendering so
                // (a) the signature validator sees the raw bytes, and
                // (b) downstream renderMainSwml can re-use it without
                // re-reading the stream. We stash the body on the exchange
                // attributes so renderMainSwml(exchange) — which already
                // wants the body for dynamic-config dispatch on AgentBase —
                // can pull it instead of calling readBody again.
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String body;
                    try {
                        body = readBody(exchange);
                    } catch (IOException e) {
                        sendPayloadTooLarge(exchange);
                        return;
                    }
                    exchange.setAttribute(REQUEST_BODY_ATTR, body);
                    if (!validateSignedWebhook(exchange, body)) {
                        // No body detail per webhooks.md.
                        exchange.sendResponseHeaders(403, -1);
                        exchange.close();
                        return;
                    }
                }

                sendJson(exchange, 200, renderMainSwml(exchange));
            } catch (Exception e) {
                log.error("SWML handler error", e);
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            }
        });

        httpServer.start();
        log.info("Service '%s' listening on %s:%d%s", name, host, port, swmlPath);
    }

    /**
     * Handle GET/POST to /swaig. Lifted from AgentBase.
     * GET: returns the rendered SWML doc (parallel to root /).
     * POST: parses {function, argument, call_id}, validates the function
     * name, calls swaigPreDispatch hook, then dispatches via onFunctionCall.
     */
    private void handleSwaigEndpoint(HttpExchange exchange) throws IOException {
        if (!validateAuth(exchange)) {
            sendUnauthorized(exchange);
            return;
        }
        addSecurityHeaders(exchange);

        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            sendJson(exchange, 200, renderMainSwml(exchange));
            return;
        }

        String body;
        try {
            body = readBody(exchange);
        } catch (IOException e) {
            exchange.sendResponseHeaders(413, -1);
            exchange.close();
            return;
        }

        // Webhook signature validation (porting-sdk/webhooks.md). Default
        // hook returns true; AgentBase overrides to enforce when signingKey
        // is set. Validation runs AFTER auth and AFTER raw-body read so the
        // signed digest sees the exact bytes the platform sent.
        if (!validateSignedWebhook(exchange, body)) {
            // No body detail — must not disclose which branch failed.
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
            return;
        }

        java.util.Map<String, Object> payload;
        try {
            java.lang.reflect.Type type =
                    new com.google.gson.reflect.TypeToken<java.util.Map<String, Object>>() {}.getType();
            payload = gson.fromJson(body, type);
        } catch (Exception e) {
            sendJson(exchange, 400, Map.of("error", "Invalid JSON"));
            return;
        }
        if (payload == null) {
            sendJson(exchange, 400, Map.of("error", "Empty payload"));
            return;
        }

        String funcName = (String) payload.get("function");
        if (funcName == null || funcName.isEmpty()) {
            sendJson(exchange, 400, Map.of("error", "Missing function name"));
            return;
        }
        if (!SWAIG_FN_NAME.matcher(funcName).matches()) {
            sendJson(exchange, 400, Map.of("error", "Invalid function name format: '" + funcName + "'"));
            return;
        }

        // Argument extraction: nested {argument:{parsed:[...]}} OR flat {arguments:{...}}
        java.util.Map<String, Object> args = new java.util.LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> argument = (java.util.Map<String, Object>) payload.get("argument");
        if (argument != null) {
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> parsed =
                    (java.util.List<java.util.Map<String, Object>>) argument.get("parsed");
            if (parsed != null && !parsed.isEmpty()) {
                args.putAll(parsed.get(0));
            }
        } else {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> arguments = (java.util.Map<String, Object>) payload.get("arguments");
            if (arguments != null) {
                args.putAll(arguments);
            }
        }

        Object[] dispatch = swaigPreDispatch(payload, funcName);
        Service target = (Service) dispatch[0];
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> shortCircuit = (java.util.Map<String, Object>) dispatch[1];
        if (shortCircuit != null) {
            sendJson(exchange, 200, shortCircuit);
            return;
        }

        var result = target.onFunctionCall(funcName, args, payload);
        if (result == null) {
            sendJson(exchange, 404, Map.of("error", "Function not found: " + funcName));
            return;
        }
        sendJson(exchange, 200, result.toMap());
    }

    /**
     * Stop the HTTP server.
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            log.info("Service '%s' stopped", name);
        }
    }
}
