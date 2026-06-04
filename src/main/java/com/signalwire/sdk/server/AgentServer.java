package com.signalwire.sdk.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.swaig.FunctionResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Multi-agent hosting server.
 * Registers multiple agents on different routes and dispatches requests accordingly.
 */
public class AgentServer {

    private static final Logger log = Logger.getLogger(AgentServer.class);
    private static final int MAX_REQUEST_BODY_SIZE = 1_048_576;
    private static final Gson gson = new Gson();

    private final String host;
    private final int port;
    private final Map<String, AgentBase> agents = new ConcurrentHashMap<>();
    private final Map<String, String> sipRoutes = new ConcurrentHashMap<>();
    private String staticFilesDir;
    private String staticFilesRoute;
    private HttpServer httpServer;

    // HTTPS configuration. Mirrors Python's AgentServer.run(): SSL is driven by
    // the SWML_SSL_ENABLED / SWML_SSL_CERT_PATH / SWML_SSL_KEY_PATH environment
    // variables, with an explicit cert/key option (enableTls) taking
    // precedence. When a cert+key resolve, run() serves over the JDK's
    // com.sun.net.httpserver.HttpsServer instead of the plain HttpServer.
    private String sslCertPath;
    private String sslKeyPath;

    public AgentServer() {
        this("0.0.0.0", resolvePort());
    }

    public AgentServer(int port) {
        this("0.0.0.0", port);
    }

    public AgentServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    private static int resolvePort() {
        String envPort = System.getenv("PORT");
        if (envPort != null) {
            try { return Integer.parseInt(envPort); }
            catch (NumberFormatException ignored) {}
        }
        return 3000;
    }

    // ============================================================
    // Agent Registration
    // ============================================================

    /**
     * Register an agent at a specific route.
     */
    public AgentServer register(AgentBase agent, String route) {
        String normalizedRoute = normalizeRoute(route);
        agents.put(normalizedRoute, agent);

        // Auto-register SIP usernames if enabled
        if (agent.isSipRoutingEnabled()) {
            for (String username : agent.getSipUsernames()) {
                sipRoutes.put(username, normalizedRoute);
            }
        }

        log.info("Registered agent '%s' at route '%s'", agent.getName(), normalizedRoute);
        return this;
    }

    /**
     * Register an agent at its own configured route.
     */
    public AgentServer register(AgentBase agent) {
        return register(agent, agent.getRoute());
    }

    /**
     * Unregister an agent from a route.
     */
    public AgentServer unregister(String route) {
        String normalizedRoute = normalizeRoute(route);
        AgentBase removed = agents.remove(normalizedRoute);
        if (removed != null) {
            // Remove SIP routes
            sipRoutes.values().removeIf(r -> r.equals(normalizedRoute));
            log.info("Unregistered agent from route '%s'", normalizedRoute);
        }
        return this;
    }

    /**
     * Get agent for a route.
     */
    public AgentBase getAgent(String route) {
        return agents.get(normalizeRoute(route));
    }

    /**
     * List all registered routes.
     */
    public Set<String> getRoutes() {
        return Collections.unmodifiableSet(agents.keySet());
    }

    /**
     * Set directory for serving static files at /static route.
     */
    public AgentServer setStaticFilesDir(String dir) {
        this.staticFilesDir = dir;
        this.staticFilesRoute = "/static";
        return this;
    }

    /**
     * Serve static files from a directory at a specific route.
     *
     * @param directory Filesystem path to the directory containing static files
     * @param route     URL route prefix (e.g., "/static" or "/assets")
     */
    public AgentServer serveStaticFiles(String directory, String route) {
        this.staticFilesDir = directory;
        this.staticFilesRoute = normalizeRoute(route);
        return this;
    }

    // ============================================================
    // HTTPS / TLS
    // ============================================================

    /**
     * Serve over HTTPS using an explicit PEM certificate chain and PKCS#8
     * private key. This is the explicit-cert option that parallels Python's
     * {@code SWMLService.serve(ssl_cert=..., ssl_key=...)}; it takes
     * precedence over the {@code SWML_SSL_*} environment variables.
     *
     * <p>{@code certPath} must be a PEM file containing the leaf (and any
     * intermediate) certificates; {@code keyPath} must be the matching
     * unencrypted PKCS#8 private key in PEM form. When both resolve at
     * {@link #run()} time the server binds a
     * {@link com.sun.net.httpserver.HttpsServer}.
     *
     * @param certPath filesystem path to the PEM certificate file
     * @param keyPath  filesystem path to the PEM PKCS#8 private-key file
     */
    public AgentServer enableTls(String certPath, String keyPath) {
        this.sslCertPath = certPath;
        this.sslKeyPath = keyPath;
        return this;
    }

    /**
     * Reports whether the server will serve HTTPS, resolving the explicit
     * cert/key option and then the {@code SWML_SSL_*} environment variables
     * the same way {@link #run()} does. A configured cert/key must both point
     * at existing files for TLS to be considered enabled.
     */
    public boolean isTlsEnabled() {
        String[] resolved = resolveTls();
        return resolved != null;
    }

    /**
     * Resolves the effective cert/key pair from the explicit option first,
     * then the SWML_SSL_* environment variables, returning {@code [cert, key]}
     * when both exist on disk, or {@code null} when TLS is not configured.
     * Mirrors the validation Python's AgentServer.run() performs (a configured
     * path that doesn't exist disables SSL rather than crashing the server).
     */
    private String[] resolveTls() {
        String cert = sslCertPath;
        String key = sslKeyPath;
        if (cert == null || key == null) {
            String enabledEnv = System.getenv("SWML_SSL_ENABLED");
            boolean envEnabled = enabledEnv != null
                    && switch (enabledEnv.toLowerCase(Locale.ROOT)) {
                        case "true", "1", "yes" -> true;
                        default -> false;
                    };
            if (envEnabled) {
                cert = System.getenv("SWML_SSL_CERT_PATH");
                key = System.getenv("SWML_SSL_KEY_PATH");
            }
        }
        if (cert == null || key == null) {
            return null;
        }
        if (!Files.exists(Path.of(cert))) {
            log.warn("SSL cert not found: %s", cert);
            return null;
        }
        if (!Files.exists(Path.of(key))) {
            log.warn("SSL key not found: %s", key);
            return null;
        }
        return new String[] {cert, key};
    }

    /**
     * Builds an {@link SSLContext} from a PEM certificate chain and an
     * unencrypted PKCS#8 PEM private key, loading them into an in-memory
     * keystore so the JDK HttpsServer can present the leaf cert. Kept package
     * self-contained: no third-party crypto, only {@code javax.net.ssl} +
     * {@code java.security}.
     */
    private static SSLContext sslContextFromPem(String certPath, String keyPath) throws IOException {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            List<Certificate> chain;
            try (InputStream in = Files.newInputStream(Path.of(certPath))) {
                chain = new ArrayList<>(cf.generateCertificates(in));
            }
            if (chain.isEmpty()) {
                throw new IOException("no certificates found in " + certPath);
            }

            PrivateKey privateKey = parsePkcs8(Files.readString(Path.of(keyPath)));

            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            char[] pw = new char[0];
            ks.setKeyEntry("server", privateKey, pw, chain.toArray(new Certificate[0]));

            KeyManagerFactory kmf =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, pw);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            return ctx;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("failed to build SSLContext from " + certPath + " / " + keyPath, e);
        }
    }

    /** Decode a PEM PKCS#8 private key ("-----BEGIN PRIVATE KEY-----"). */
    private static PrivateKey parsePkcs8(String pem) throws Exception {
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        // gen_certs.sh emits RSA keys; PKCS#8 carries the algorithm OID so we
        // try RSA first and fall back to EC for forward-compatibility.
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception rsaFailure) {
            return KeyFactory.getInstance("EC").generatePrivate(spec);
        }
    }

    // ============================================================
    // SIP Routing
    // ============================================================

    /**
     * Register a SIP username to route to a specific agent.
     */
    public AgentServer registerSipRoute(String username, String route) {
        sipRoutes.put(username, normalizeRoute(route));
        return this;
    }

    /**
     * Get route for a SIP username.
     */
    public String getSipRoute(String username) {
        return sipRoutes.get(username);
    }

    // ============================================================
    // HTTP Server
    // ============================================================

    /**
     * Start the multi-agent server.
     */
    public void run() throws IOException {
        InetSocketAddress address = new InetSocketAddress(host, port);
        String[] tls = resolveTls();
        if (tls != null) {
            // HTTPS: bind the JDK's HttpsServer and hand it an SSLContext built
            // from the configured cert/key (mirrors Python passing
            // ssl_certfile/ssl_keyfile to uvicorn). Same routing/handlers as
            // the plain path — only the listener's transport changes.
            HttpsServer httpsServer = HttpsServer.create(address, 0);
            httpsServer.setHttpsConfigurator(
                    new HttpsConfigurator(sslContextFromPem(tls[0], tls[1])));
            httpServer = httpsServer;
            log.info("AgentServer TLS enabled (cert: %s)", tls[0]);
        } else {
            httpServer = HttpServer.create(address, 0);
        }
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // Health endpoint
        httpServer.createContext("/health", exchange -> {
            try {
                sendJson(exchange, 200, Map.of(
                        "status", "healthy",
                        "agents", agents.size(),
                        "routes", new ArrayList<>(agents.keySet())
                ));
            } catch (Exception e) {
                log.error("Health handler error", e);
            }
        });

        // Ready endpoint
        httpServer.createContext("/ready", exchange -> {
            try {
                sendJson(exchange, 200, Map.of("status", "ready"));
            } catch (Exception e) {
                log.error("Ready handler error", e);
            }
        });

        // Static file handler
        if (staticFilesDir != null && staticFilesRoute != null) {
            httpServer.createContext(staticFilesRoute, this::handleStaticFile);
            log.info("Serving static files from '%s' at '%s'", staticFilesDir, staticFilesRoute);
        }

        // Main dispatch handler - catches all routes
        httpServer.createContext("/", this::handleRequest);

        httpServer.start();
        String scheme = (tls != null) ? "https" : "http";
        log.info("AgentServer listening on %s://%s:%d with %d agent(s)", scheme, host, port, agents.size());
        for (Map.Entry<String, AgentBase> entry : agents.entrySet()) {
            log.info("  Route: %s -> Agent: %s", entry.getKey(), entry.getValue().getName());
        }
    }

    private void handleRequest(HttpExchange exchange) {
        try {
            String path = exchange.getRequestURI().getPath();

            // Skip health/ready (already handled)
            if ("/health".equals(path) || "/ready".equals(path)) {
                return;
            }

            // Find matching agent by route
            AgentBase agent = findAgentForPath(path);
            if (agent == null) {
                sendJson(exchange, 404, Map.of("error", "No agent found for path: " + path));
                return;
            }

            // Determine sub-path
            String agentRoute = findRouteForPath(path);
            String subPath = path.substring(agentRoute.length());

            // Validate auth
            if (!validateAuth(exchange, agent)) {
                sendUnauthorized(exchange);
                return;
            }
            addSecurityHeaders(exchange);

            String baseUrl = detectBaseUrl(exchange, agent);

            if (subPath.equals("/swaig")) {
                handleSwaig(exchange, agent);
            } else if (subPath.equals("/post_prompt")) {
                handlePostPrompt(exchange, agent);
            } else {
                // Main SWML endpoint
                handleSwml(exchange, agent, baseUrl);
            }
        } catch (Exception e) {
            log.error("Request handler error", e);
            try {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            } catch (Exception ignored) {}
        }
    }

    private AgentBase findAgentForPath(String path) {
        // Find the longest matching route
        String bestRoute = null;
        for (String route : agents.keySet()) {
            if (path.equals(route) || path.startsWith(route + "/") || (route.equals("/") && path.startsWith("/"))) {
                if (bestRoute == null || route.length() > bestRoute.length()) {
                    bestRoute = route;
                }
            }
        }
        return bestRoute != null ? agents.get(bestRoute) : null;
    }

    private String findRouteForPath(String path) {
        String bestRoute = null;
        for (String route : agents.keySet()) {
            if (path.equals(route) || path.startsWith(route + "/") || (route.equals("/") && path.startsWith("/"))) {
                if (bestRoute == null || route.length() > bestRoute.length()) {
                    bestRoute = route;
                }
            }
        }
        return bestRoute != null ? bestRoute : "/";
    }

    private void handleSwml(HttpExchange exchange, AgentBase agent, String baseUrl) throws IOException {
        // POST root requires signature validation when the agent has a
        // signing key set (porting-sdk/webhooks.md). For GET we just render.
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body;
            try {
                body = readBody(exchange);
            } catch (IOException e) {
                exchange.sendResponseHeaders(413, -1);
                exchange.close();
                return;
            }
            if (!agent.validateWebhook(exchange, body)) {
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                return;
            }
        }
        sendJson(exchange, 200, agent.renderSwml(baseUrl));
    }

    @SuppressWarnings("unchecked")
    private void handleSwaig(HttpExchange exchange, AgentBase agent) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
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

        // Webhook signature validation when the agent has a signing key set.
        // No body detail (per porting-sdk/webhooks.md).
        if (!agent.validateWebhook(exchange, body)) {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
            return;
        }

        Map<String, Object> payload;
        try {
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
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
        if (funcName == null || funcName.isEmpty() || !agent.hasTool(funcName)) {
            sendJson(exchange, 404, Map.of("error", "Function not found: " + funcName));
            return;
        }

        Map<String, Object> args = new LinkedHashMap<>();
        Map<String, Object> argument = (Map<String, Object>) payload.get("argument");
        if (argument != null) {
            List<Map<String, Object>> parsed = (List<Map<String, Object>>) argument.get("parsed");
            if (parsed != null && !parsed.isEmpty()) {
                args.putAll(parsed.get(0));
            }
        }

        FunctionResult result = agent.onFunctionCall(funcName, args, payload);
        sendJson(exchange, 200, result.toMap());
    }

    @SuppressWarnings("unchecked")
    private void handlePostPrompt(HttpExchange exchange, AgentBase agent) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
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

        // Webhook signature validation when the agent has a signing key set.
        if (!agent.validateWebhook(exchange, body)) {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
            return;
        }

        // Just acknowledge - the agent handles the callback internally
        sendJson(exchange, 200, Map.of("status", "ok"));
    }

    // ============================================================
    // HTTP Utilities
    // ============================================================

    private boolean validateAuth(HttpExchange exchange, AgentBase agent) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) return false;

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(authHeader.substring(6));
        } catch (IllegalArgumentException e) {
            return false;
        }

        String credentials = new String(decoded, StandardCharsets.UTF_8);
        int colonIdx = credentials.indexOf(':');
        if (colonIdx < 0) return false;

        String user = credentials.substring(0, colonIdx);
        String pass = credentials.substring(colonIdx + 1);

        return MessageDigest.isEqual(
                user.getBytes(StandardCharsets.UTF_8),
                agent.getAuthUser().getBytes(StandardCharsets.UTF_8))
                && MessageDigest.isEqual(
                pass.getBytes(StandardCharsets.UTF_8),
                agent.getAuthPassword().getBytes(StandardCharsets.UTF_8));
    }

    private void addSecurityHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Cache-Control", "no-store");
    }

    private void sendUnauthorized(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"Agent Server\"");
        exchange.sendResponseHeaders(401, -1);
        exchange.close();
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        String json = gson.toJson(body);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
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

    private String detectBaseUrl(HttpExchange exchange, AgentBase agent) {
        var headers = exchange.getRequestHeaders();
        String proto = headers.getFirst("X-Forwarded-Proto");
        String fwdHost = headers.getFirst("X-Forwarded-Host");
        if (proto != null && fwdHost != null) {
            return proto + "://" + fwdHost;
        }
        String original = headers.getFirst("X-Original-URL");
        if (original != null) return original;
        return "http://" + agent.getAuthUser() + ":" + agent.getAuthPassword() + "@" + host + ":" + port;
    }

    private String normalizeRoute(String route) {
        if (route == null || route.isEmpty()) return "/";
        if (!route.startsWith("/")) route = "/" + route;
        if (route.endsWith("/") && route.length() > 1) route = route.substring(0, route.length() - 1);
        return route;
    }

    private void handleStaticFile(HttpExchange exchange) {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            String requestPath = exchange.getRequestURI().getPath();
            // Strip the route prefix
            String relativePath = requestPath.substring(staticFilesRoute.length());
            if (relativePath.isEmpty() || "/".equals(relativePath)) {
                relativePath = "/index.html";
            }

            // Prevent directory traversal
            Path basePath = Path.of(staticFilesDir).toAbsolutePath().normalize();
            Path filePath = basePath.resolve(relativePath.substring(1)).normalize();
            if (!filePath.startsWith(basePath)) {
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                return;
            }

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            // Determine content type
            String contentType = guessContentType(filePath.toString());
            byte[] data = Files.readAllBytes(filePath);

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        } catch (Exception e) {
            log.error("Static file handler error", e);
            try {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            } catch (Exception ignored) {}
        }
    }

    private String guessContentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    /**
     * Stop the server.
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            log.info("AgentServer stopped");
        }
    }
}
