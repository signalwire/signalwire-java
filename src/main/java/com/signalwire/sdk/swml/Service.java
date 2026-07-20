/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import com.google.gson.Gson;
import com.signalwire.sdk.logging.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
 * Base SWML service with an embedded HTTP server, basic auth, security headers, and explicit
 * methods for all 38 schema-driven verbs.
 *
 * <p>Uses JDK built-in com.sun.net.httpserver.HttpServer with virtual threads.
 */
public class Service implements AutoCloseable {

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
   * HttpExchange attribute key under which the raw POST body is stashed by {@link #serve()} when
   * reading it up-front for signature validation. Subclasses (notably {@link
   * com.signalwire.sdk.agent.AgentBase}) check this attribute in {@code renderMainSwml} so they can
   * re-use the cached body without re-reading the (already-consumed) request stream.
   */
  public static final String REQUEST_BODY_ATTR = "com.signalwire.sdk.requestBody";

  // Auth — protected so AgentBase (extends Service) can read them in subclass
  // helpers. Don't expose via getters that mutate; use the constructor.
  protected String authUser;
  protected String authPassword;

  // HTTP server — protected so AgentBase can register additional routes
  protected HttpServer httpServer;

  // SWMLService reference-API state (verb-handler registry, dynamic routing
  // callback, and schema-validation toggle). Built/registered lazily via the
  // reference-API delegators below.
  protected VerbHandlerRegistry verbHandlerRegistry;
  // Routing callback: (body, headers) -> route | null. Mirrors the Python
  // reference's decomposed callback_fn(body, headers) shape (the framework-free
  // dispatch core in handleRequest consults it). Cross-port parity: the callback
  // receives the parsed request body dict and the request headers dict, and
  // returns a redirect route string or null to continue normal processing.
  protected java.util.function.BiFunction<
          java.util.Map<String, Object>, java.util.Map<String, String>, String>
      routingCallback;
  // Path-keyed routing callbacks. Mirrors Python's _routing_callbacks dict
  // (web_mixin.register_routing_callback), keyed by the NORMALIZED path
  // (strip trailing '/', ensure a leading '/'); multiple paths may each carry
  // their own callback. Insertion-ordered so routingCallbackPaths() is stable.
  protected final java.util.Map<
          String,
          java.util.function.BiFunction<
              java.util.Map<String, Object>, java.util.Map<String, String>, String>>
      routingCallbacks = new java.util.LinkedHashMap<>();
  protected boolean schemaValidation = true;
  // Proxy URL base for webhook callbacks (SWMLService.manual_set_proxy_url).
  protected String proxyUrlBase;

  public Service(String name) {
    this(name, "/", "0.0.0.0", resolvePort(), null, null);
  }

  public Service(String name, String route) {
    this(name, route, "0.0.0.0", resolvePort(), null, null);
  }

  public Service(
      String name, String route, String host, int port, String authUser, String authPassword) {
    this.name = name;
    this.route =
        route.endsWith("/") && route.length() > 1 ? route.substring(0, route.length() - 1) : route;
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
        // malformed PORT env var; fall through to the default
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

  /**
   * Validate provided basic-auth credentials against the configured ones using a constant-time
   * comparison.
   */
  public boolean validateBasicAuth(String username, String password) {
    if (authUser == null || authPassword == null) return false;
    return java.security.MessageDigest.isEqual(
            username.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            authUser.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        && java.security.MessageDigest.isEqual(
            password.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            authPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  /** Get the configured (user, password) pair as a String[2] tuple. */
  public String[] getBasicAuthCredentials() {
    return new String[] {
      authUser != null ? authUser : "", authPassword != null ? authPassword : ""
    };
  }

  /** Get (user, password, source) where source is "provided", "environment", or "generated". */
  public String[] getBasicAuthCredentialsWithSource() {
    String user = authUser != null ? authUser : "";
    String pass = authPassword != null ? authPassword : "";
    String envUser = System.getenv("SWML_BASIC_AUTH_USER");
    String envPass = System.getenv("SWML_BASIC_AUTH_PASSWORD");
    String source;
    if (envUser != null
        && !envUser.isEmpty()
        && envPass != null
        && !envPass.isEmpty()
        && user.equals(envUser)
        && pass.equals(envPass)) {
      source = "environment";
    } else if (user.startsWith("user_") && pass.length() > 20) {
      source = "generated";
    } else {
      source = "provided";
    }
    return new String[] {user, pass, source};
  }

  /** Timing-safe basic auth validation using MessageDigest.isEqual. */
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
    boolean userMatch =
        MessageDigest.isEqual(
            user.getBytes(StandardCharsets.UTF_8), authUser.getBytes(StandardCharsets.UTF_8));
    boolean passMatch =
        MessageDigest.isEqual(
            pass.getBytes(StandardCharsets.UTF_8), authPassword.getBytes(StandardCharsets.UTF_8));

    return userMatch && passMatch;
  }

  // ------------------------------------------------------------------
  // SWAIG tool registry (lifted from AgentBase)
  // ------------------------------------------------------------------

  /**
   * Define a SWAIG function the AI can call. Tool descriptions and parameter descriptions are
   * LLM-facing prompt engineering — see PORTING_GUIDE for guidance on writing them.
   */
  public Service defineTool(
      String name,
      String description,
      java.util.Map<String, Object> parameters,
      com.signalwire.sdk.swaig.ToolHandler handler) {
    tools.put(
        name, new com.signalwire.sdk.swaig.ToolDefinition(name, description, parameters, handler));
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
      String funcName, java.util.Map<String, Object> args, java.util.Map<String, Object> rawData) {
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

  /** Whether a SWAIG function with the given name is registered. */
  public boolean hasFunction(String name) {
    return tools.containsKey(name);
  }

  /** Get a registered SWAIG function by name, or null when absent. */
  public com.signalwire.sdk.swaig.ToolDefinition getFunction(String name) {
    return tools.get(name);
  }

  /** Snapshot of all registered SWAIG functions keyed by name. */
  public java.util.Map<String, com.signalwire.sdk.swaig.ToolDefinition> getAllFunctions() {
    return new java.util.LinkedHashMap<>(tools);
  }

  /**
   * Remove a registered SWAIG function. Returns true when removed, false when the function was not
   * registered.
   */
  public boolean removeFunction(String name) {
    return tools.remove(name) != null;
  }

  /**
   * Public, read-only view of the registered SWAIG tool registry. Returned in insertion order; the
   * map and its definitions are unmodifiable. Used by introspection callers (CLI {@code
   * --list-tools} file-loader path, tests, audit tooling) that need name + description + parameters
   * without going through {@code /swaig} HTTP.
   */
  public java.util.Map<String, com.signalwire.sdk.swaig.ToolDefinition> getRegisteredTools() {
    return java.util.Collections.unmodifiableMap(tools);
  }

  /**
   * Read-only view of the raw SWAIG function entries registered via {@link
   * #registerSwaigFunction(java.util.Map)}. These are typically DataMap or schema-only tools that
   * don't have a Java {@link com.signalwire.sdk.swaig.ToolHandler}. Each entry is a defensive copy
   * of the original map; the outer list is unmodifiable.
   */
  public java.util.List<java.util.Map<String, Object>> getRegisteredSwaigFunctions() {
    java.util.List<java.util.Map<String, Object>> copy =
        new java.util.ArrayList<>(registeredSwaigFunctions.size());
    for (var fn : registeredSwaigFunctions) {
      copy.add(java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fn)));
    }
    return java.util.Collections.unmodifiableList(copy);
  }

  /**
   * Extension point: invoked between argument parsing and function dispatch. Returns a 2-element
   * array: [target Service, shortCircuit Map]. If shortCircuit is non-null, it's returned as the
   * SWAIG response without calling onFunctionCall. AgentBase may override to add session-token
   * validation or ephemeral dynamic-config copies.
   */
  protected Object[] swaigPreDispatch(java.util.Map<String, Object> requestData, String funcName) {
    return new Object[] {this, null};
  }

  /**
   * Extension point: render the SWML document for the main path or for GET /swaig. Default returns
   * the currently-built Document. AgentBase overrides to emit prompt + AI verb at request time.
   */
  protected java.util.Map<String, Object> renderMainSwml(HttpExchange exchange) {
    return document.toMap();
  }

  /**
   * Extension point: register additional HTTP routes after Service mounts /health, /ready, /swaig
   * and the main route. AgentBase uses this to add /post_prompt and /mcp.
   */
  protected void registerAdditionalRoutes(HttpServer server) {}

  /**
   * Customization hook called when SWML is requested. Default delegates to {@link
   * #onSwmlRequest(java.util.Map, String)} and returns its result. Subclasses typically override
   * {@code onSwmlRequest} rather than this method.
   *
   * <p>Returning {@code null} uses the default rendered SWML; returning a non-null map merges the
   * entries as modifications.
   *
   * <p>This hook takes the parsed request body and the callback sub-path; there is no framework-
   * specific request object parameter.
   *
   * @param requestData parsed request body, or {@code null}
   * @param callbackPath optional callback sub-path, or {@code null}
   * @return modifications map, or {@code null} for default rendering
   */
  public java.util.Map<String, Object> onRequest(
      java.util.Map<String, Object> requestData, String callbackPath) {
    return onSwmlRequest(requestData, callbackPath);
  }

  /**
   * Customization point for subclasses to modify SWML based on request data. The default
   * implementation returns {@code null} (no modification). Subclasses override to inspect the body
   * or callback path and return a map of SWML overrides.
   *
   * <p>This hook takes the parsed request body and the callback sub-path; there is no framework-
   * specific request object parameter.
   *
   * @param requestData parsed request body, or {@code null}
   * @param callbackPath optional callback sub-path, or {@code null}
   * @return modifications map, or {@code null}
   */
  public java.util.Map<String, Object> onSwmlRequest(
      java.util.Map<String, Object> requestData, String callbackPath) {
    return null;
  }

  /**
   * Extension hook invoked between raw-body capture and JSON parsing on signed POST routes ({@code
   * /}, {@code /swaig}, {@code /post_prompt}). Subclasses (AgentBase) override to enforce
   * SignalWire webhook signature validation when a signing key is configured. Default returns
   * {@code true} (no validation).
   *
   * <p>Returning {@code false} signals "signature invalid"; the caller sends {@code 403 Forbidden}
   * and stops dispatch. The response body must NOT disclose which branch failed.
   *
   * @param exchange the HTTP exchange.
   * @param rawBody the raw UTF-8 body string already read from the exchange. Pass through to {@link
   *     com.signalwire.sdk.security.WebhookValidator}.
   * @return {@code true} when validation passes (or is disabled); {@code false} to short-circuit
   *     with a 403.
   */
  protected boolean validateSignedWebhook(HttpExchange exchange, String rawBody) {
    return true;
  }

  /** Add security headers to every authenticated response. */
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
   * SchemaUtils helper bound to this Service. Mirrors Python's {@code self.schema_utils} public
   * instance attribute on {@code SWMLService}. Built lazily on first access.
   */
  public SchemaUtils getSchemaUtils() {
    if (schemaUtilsInstance == null) {
      schemaUtilsInstance = new SchemaUtils(null, true);
    }
    return schemaUtilsInstance;
  }

  // -------- SWMLService reference-API delegators --------
  // The Python reference SWMLService exposes document-manipulation and routing
  // helpers directly on the service; Java folds the document model into a
  // Document object and the routing/proxy helpers onto AgentBase. These thin
  // delegators surface the reference SWMLService API on the Service class so
  // the cross-port surface compares equal (mirrors the Ruby port's SWMLService
  // delegators and the SIGNATURE gate's Service→SWMLService rename).

  /** Add a section to the document. Mirrors SWMLService.add_section. */
  public Service addSection(String sectionName) {
    document.addSection(sectionName);
    return this;
  }

  /** Add a verb to the main section. Mirrors SWMLService.add_verb. */
  public Service addVerb(String verbName, Object verbData) {
    document.addVerb(verbName, verbData);
    return this;
  }

  /** Add a verb to a named section. Mirrors SWMLService.add_verb_to_section. */
  public Service addVerbToSection(String sectionName, String verbName, Object verbData) {
    document.addVerbToSection(sectionName, verbName, verbData);
    return this;
  }

  /** Render the current document as a compact JSON string. Mirrors SWMLService.render_document. */
  public String renderDocument() {
    return document.render();
  }

  /** Reset the current document to an empty state. Mirrors SWMLService.reset_document. */
  public Service resetDocument() {
    document.reset();
    return this;
  }

  /**
   * Register a custom SWML verb handler. Mirrors SWMLService.register_verb_handler — delegates to
   * the service's {@link VerbHandlerRegistry}.
   */
  public Service registerVerbHandler(SWMLVerbHandler handler) {
    verbRegistry().registerHandler(handler);
    return this;
  }

  /**
   * The verb-handler registry for this service (built lazily). Mirrors SWMLService.verb_registry.
   */
  public VerbHandlerRegistry verbRegistry() {
    if (verbHandlerRegistry == null) {
      verbHandlerRegistry = new VerbHandlerRegistry();
    }
    return verbHandlerRegistry;
  }

  /**
   * Register a routing callback invoked to resolve dynamic routes. Mirrors
   * SWMLService.register_routing_callback. Java stores the callback for the HTTP layer (and the
   * framework-free {@link #handleRequest} dispatch core) to consult.
   *
   * <p>The callback receives the parsed request body dict and the request headers dict — {@code
   * callback(body, headers)} — and returns a redirect route string, or {@code null} to continue
   * normal processing. This is the decomposed {@code (body, headers) -> String} shape the Python
   * reference and the other SDK ports share.
   *
   * @param callback the routing callback, {@code (body, headers) -> route-or-null}.
   * @return this service for chaining.
   */
  public Service registerRoutingCallback(
      java.util.function.BiFunction<
              java.util.Map<String, Object>, java.util.Map<String, String>, String>
          callback) {
    return registerRoutingCallback(callback, "/sip");
  }

  /**
   * Register a routing callback at a specific path. Mirrors Python's {@code
   * register_routing_callback(callback_fn, path="/sip")} (web_mixin): the path is normalized
   * (trailing {@code /} stripped, a leading {@code /} ensured) and used as the dict key, so
   * distinct paths each carry their own callback. Re-registering a path replaces its callback.
   *
   * @param callback the routing callback, {@code (body, headers) -> route-or-null}.
   * @param path the path this callback handles (default {@code "/sip"}).
   * @return this service for chaining.
   */
  public Service registerRoutingCallback(
      java.util.function.BiFunction<
              java.util.Map<String, Object>, java.util.Map<String, String>, String>
          callback,
      String path) {
    String normalized = normalizeCallbackPath(path);
    routingCallbacks.put(normalized, callback);
    // Keep the single-callback field pointing at the most recent registration so
    // existing single-callback consumers still see a callback.
    this.routingCallback = callback;
    return this;
  }

  /** Normalize a routing-callback path: strip trailing '/', ensure a leading '/'. */
  private static String normalizeCallbackPath(String path) {
    String p = path == null ? "" : path.replaceAll("/+$", "");
    if (!p.startsWith("/")) {
      p = "/" + p;
    }
    return p;
  }

  /**
   * The normalized paths that have a routing callback registered, in registration order. Mirrors
   * reading the keys of Python's {@code _routing_callbacks} dict.
   *
   * @return an unmodifiable list of normalized callback paths.
   */
  public java.util.List<String> routingCallbackPaths() {
    return java.util.List.copyOf(routingCallbacks.keySet());
  }

  /**
   * Return a mountable request handler that embeds this service's routes into a host application.
   *
   * <p>Mirrors {@code SWMLService.as_router} / {@code WebMixin.as_router}: Python returns a FastAPI
   * {@code APIRouter} (a {@code HostAppRouter}) so a host ASGI/FastAPI app can mount the agent's
   * routes without running the service's own server. Java has no external web framework, so the
   * cross-port equivalent of the "embed my routes in a host app" unit is the JDK's {@link
   * com.sun.net.httpserver.HttpHandler} — the same shape Go's {@code AsRouter} uses with {@code
   * http.Handler}. The returned handler dispatches an incoming {@link HttpExchange} to the same
   * route table {@link #serve()} installs (health, ready, {@code /swaig}, subclass extras, and the
   * main SWML endpoint), selecting the handler whose registered context path is the longest prefix
   * of the request path — the same longest-prefix rule {@code HttpServer} itself applies.
   *
   * @return an {@link com.sun.net.httpserver.HttpHandler} exposing this service's routes for
   *     mounting under a host {@link HttpServer} (e.g. {@code hostServer.createContext(prefix,
   *     service.asRouter())}) or invoking directly
   */
  public com.sun.net.httpserver.HttpHandler asRouter() {
    // Register this service's routes onto an unbound server purely to reuse its
    // context table + longest-prefix matching; the server is never started/bound.
    final HttpServer routes;
    try {
      routes = HttpServer.create();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to build router for service '" + name + "'", e);
    }
    // Capture (path, handler) pairs as they're registered — via serve()'s exact
    // route table — so the returned handler can do its own longest-prefix
    // dispatch. The RouteCollector wraps the unbound server and records every
    // createContext call (including subclass extras from registerAdditionalRoutes);
    // the wrapped server never binds a port.
    RouteCollector collector = new RouteCollector(routes);
    registerRoutes(collector);
    java.util.List<com.sun.net.httpserver.HttpContext> contexts = collector.contexts();
    return exchange -> {
      String path = exchange.getRequestURI().getPath();
      // Longest-prefix match over the registered contexts (HttpServer's own rule).
      com.sun.net.httpserver.HttpContext best = null;
      int bestLen = -1;
      for (com.sun.net.httpserver.HttpContext ctx : contexts) {
        String p = ctx.getPath();
        boolean matches = path.equals(p) || path.startsWith(p.endsWith("/") ? p : p + "/");
        if (matches && p.length() > bestLen) {
          best = ctx;
          bestLen = p.length();
        }
      }
      if (best == null) {
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
        return;
      }
      best.getHandler().handle(exchange);
    };
  }

  /**
   * Delegating {@link HttpServer} wrapper that records every {@link #createContext} registration so
   * {@link #asRouter()} can enumerate the routes for its own longest-prefix dispatch (the JDK's
   * {@code HttpServer} exposes no way to list its contexts). All other operations delegate to the
   * wrapped (unbound) server; the wrapper is never bound or started.
   */
  private static final class RouteCollector extends HttpServer {
    private final HttpServer delegate;
    private final java.util.List<com.sun.net.httpserver.HttpContext> contexts =
        new java.util.ArrayList<>();

    RouteCollector(HttpServer delegate) {
      this.delegate = delegate;
    }

    java.util.List<com.sun.net.httpserver.HttpContext> contexts() {
      return contexts;
    }

    @Override
    public com.sun.net.httpserver.HttpContext createContext(
        String path, com.sun.net.httpserver.HttpHandler handler) {
      com.sun.net.httpserver.HttpContext ctx = delegate.createContext(path, handler);
      contexts.add(ctx);
      return ctx;
    }

    @Override
    public com.sun.net.httpserver.HttpContext createContext(String path) {
      com.sun.net.httpserver.HttpContext ctx = delegate.createContext(path);
      contexts.add(ctx);
      return ctx;
    }

    @Override
    public void bind(InetSocketAddress addr, int backlog) throws IOException {
      delegate.bind(addr, backlog);
    }

    @Override
    public void start() {
      delegate.start();
    }

    @Override
    public void setExecutor(java.util.concurrent.Executor executor) {
      delegate.setExecutor(executor);
    }

    @Override
    public java.util.concurrent.Executor getExecutor() {
      return delegate.getExecutor();
    }

    @Override
    public void stop(int delay) {
      delegate.stop(delay);
    }

    @Override
    public void removeContext(String path) {
      delegate.removeContext(path);
    }

    @Override
    public void removeContext(com.sun.net.httpserver.HttpContext context) {
      delegate.removeContext(context);
    }

    @Override
    public InetSocketAddress getAddress() {
      return delegate.getAddress();
    }
  }

  /**
   * Whether full JSON-Schema validation is enabled and available. Mirrors
   * SWMLService.full_validation_enabled.
   */
  public boolean fullValidationEnabled() {
    return schemaValidation && getSchemaUtils().isFullValidationAvailable();
  }

  /**
   * Manually set the proxy URL base for webhook callbacks (may be called at runtime). Mirrors
   * SWMLService.manual_set_proxy_url.
   */
  public Service manualSetProxyUrl(String proxyUrl) {
    this.proxyUrlBase = proxyUrl;
    return this;
  }

  /**
   * The {@code (status, headers, body)} triple returned by the framework-free {@link
   * #handleRequest} dispatch core. Mirrors the language-neutral {@code tuple<int,
   * dict<string,string>, string>} the Python reference {@code handle_request} returns and the
   * {@code (int, Dictionary, string)} .NET {@code HandleRequest} returns — Java has no value-tuple
   * type, so this record stands in for it (the enumerator records the canonical tuple shape).
   *
   * @param status the HTTP status code (200, 307, 401, …).
   * @param headers the response headers (e.g. {@code Location} for a 307 redirect, {@code
   *     WWW-Authenticate} for a 401).
   * @param body the response body string (a JSON SWML document for 200; empty for a redirect).
   */
  public record HttpResult(int status, java.util.Map<String, String> headers, String body) {}

  /**
   * Framework-free request-dispatch core.
   *
   * <p>This is the primitive dispatch surface the SDK ports share (mirrors the Python reference
   * {@code SWMLService.handle_request(method, url, headers, body) -> (status, headers, body)} and
   * the .NET {@code (int, Dictionary, string) HandleRequest(method, path, headers, body)}). It
   * performs basic-auth, the routing-callback check, and the {@code onRequest} modification over
   * plain primitives instead of a {@link HttpExchange}, so the same dispatch behavior (identical
   * status codes, headers, and body) is reachable without the embedded HTTP server.
   *
   * @param method HTTP method, e.g. {@code "GET"} or {@code "POST"}.
   * @param url the full request URL (used to derive the routing-callback path).
   * @param headers request headers as a plain map.
   * @param body the already-parsed JSON body for POST requests, or {@code null}.
   * @return a {@code (status, headers, body)} triple. 200 with a JSON SWML body for normal
   *     rendering; 307 with a {@code Location} header for a routing redirect; 401 with a {@code
   *     WWW-Authenticate: Basic} header for an auth failure.
   */
  public HttpResult handleRequest(
      String method,
      String url,
      java.util.Map<String, String> headers,
      java.util.Map<String, Object> body) {
    java.util.Map<String, Object> reqBody = body != null ? body : new java.util.LinkedHashMap<>();
    String callbackPath = callbackPathForUrl(url);

    // Auth
    if (!checkBasicAuthHeaders(headers)) {
      return new HttpResult(
          401,
          java.util.Map.of("WWW-Authenticate", "Basic"),
          gson.toJson(java.util.Map.of("error", "Unauthorized")));
    }

    // Routing callback: (body, headers) -> route | null. Only runs for a POST
    // with a non-empty parsed body whose callback path has a callback registered.
    java.util.function.BiFunction<
            java.util.Map<String, Object>, java.util.Map<String, String>, String>
        cb = callbackPath == null ? null : routingCallbacks.get(callbackPath);
    if ("POST".equalsIgnoreCase(method) && !reqBody.isEmpty() && cb != null) {
      try {
        String route = cb.apply(reqBody, headers);
        if (route != null) {
          log.info("routing_request route=%s", route);
          // 307 preserves the POST method and its body.
          return new HttpResult(307, java.util.Map.of("Location", route), "");
        }
      } catch (Exception e) {
        log.error("error_in_routing_callback", e);
      }
    }

    // Subclass request modification (mirrors on_request(body, callback_path)).
    java.util.Map<String, Object> modifications = onRequest(reqBody, callbackPath);
    if (modifications != null && !modifications.isEmpty()) {
      java.util.Map<String, Object> doc = new java.util.LinkedHashMap<>(getDocument().toMap());
      for (java.util.Map.Entry<String, Object> e : modifications.entrySet()) {
        if (doc.containsKey(e.getKey())) {
          doc.put(e.getKey(), e.getValue());
        }
      }
      return new HttpResult(200, new java.util.LinkedHashMap<>(), gson.toJson(doc));
    }

    // Default: the current SWML document.
    return new HttpResult(200, new java.util.LinkedHashMap<>(), renderDocument());
  }

  /**
   * Derive the registered routing-callback path (if any) that a given URL targets — the primitive
   * {@link #handleRequest} recovers the equivalent of the FastAPI router's {@code
   * request.state.callback_path} by matching the URL's normalized path against the single
   * registered callback (Java stores one routing callback per service). Returns {@code null} when
   * no callback is registered.
   */
  protected String callbackPathForUrl(String url) {
    if (routingCallbacks.isEmpty() || url == null) {
      return null;
    }
    String path = url;
    int scheme = url.indexOf("://");
    if (scheme >= 0) {
      int slash = url.indexOf('/', scheme + 3);
      path = slash >= 0 ? url.substring(slash) : "/";
    }
    int q = path.indexOf('?');
    if (q >= 0) {
      path = path.substring(0, q);
    }
    String trimmed = path.replaceAll("^/+", "").replaceAll("/+$", "");
    String normalized = trimmed.isEmpty() ? "/" : "/" + trimmed;
    // Match the URL's normalized path against a registered callback path, by
    // exact match or suffix (a route-relative callback path like "/sip" matches
    // the service-rooted URL path "/swml/sip") — mirrors Go's CallbackPathForURL.
    for (String cbPath : routingCallbacks.keySet()) {
      if (normalized.equals(cbPath) || normalized.endsWith(cbPath)) {
        return cbPath;
      }
    }
    return null;
  }

  /**
   * Basic-auth check over a plain header map (the framework-free counterpart of {@link
   * #validateAuth(HttpExchange)}). Reads the {@code Authorization} header case-insensitively and
   * compares credentials timing-safely.
   */
  protected boolean checkBasicAuthHeaders(java.util.Map<String, String> headers) {
    if (headers == null) {
      return false;
    }
    String authHeader = null;
    for (java.util.Map.Entry<String, String> e : headers.entrySet()) {
      if ("Authorization".equalsIgnoreCase(e.getKey())) {
        authHeader = e.getValue();
        break;
      }
    }
    if (authHeader == null || !authHeader.startsWith("Basic ")) {
      return false;
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(authHeader.substring(6));
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
    boolean userMatch =
        MessageDigest.isEqual(
            user.getBytes(StandardCharsets.UTF_8), authUser.getBytes(StandardCharsets.UTF_8));
    boolean passMatch =
        MessageDigest.isEqual(
            pass.getBytes(StandardCharsets.UTF_8), authPassword.getBytes(StandardCharsets.UTF_8));
    return userMatch && passMatch;
  }

  /**
   * Extract the SIP username (the user portion of the {@code to} SIP URI) from a request body's
   * call data. Mirrors the static SWMLService.extract_sip_username.
   *
   * @param requestBody the parsed request body (expects {@code call.to} or a top-level {@code to})
   * @return the SIP username, or null when none is present
   */
  @SuppressWarnings("unchecked")
  public static String extractSipUsername(java.util.Map<String, Object> requestBody) {
    if (requestBody == null) {
      return null;
    }
    Object to = null;
    Object call = requestBody.get("call");
    if (call instanceof java.util.Map) {
      to = ((java.util.Map<String, Object>) call).get("to");
    }
    if (to == null) {
      to = requestBody.get("to");
    }
    if (!(to instanceof String) || ((String) to).isEmpty()) {
      return null;
    }
    String toField = (String) to;
    // TEL URIs ("tel:+15551234567") -> the phone-number part (no '@' split).
    // Mirrors Python extract_sip_username's `elif to_field.startswith("tel:")`.
    for (String telPrefix : new String[] {"tel:", "TEL:"}) {
      if (toField.startsWith(telPrefix)) {
        return toField.substring(telPrefix.length());
      }
    }
    // SIP URIs ("sip:username@domain") -> the user part before '@'.
    String work = toField;
    boolean isSip = false;
    for (String prefix : new String[] {"sip:", "SIP:", "sips:", "SIPS:"}) {
      if (work.startsWith(prefix)) {
        work = work.substring(prefix.length());
        isSip = true;
        break;
      }
    }
    if (isSip) {
      int at = work.indexOf('@');
      return at >= 0 ? work.substring(0, at) : work;
    }
    // Plain value: return as-is (Python's final `else: return to_field`).
    return work;
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

  /**
   * Typed overload of {@link #connect(Map)}: dial a SIP URI or phone number described by the
   * generated {@link com.signalwire.sdk.swml.generated.ConnectConfig} wire type. Mirrors the
   * reference's {@code connect(config: ConnectConfig | None)} (swml_verbs_generated.py) — the
   * config's set fields carry the exact snake wire keys, and unset (null) fields are omitted from
   * the rendered SWML, so this produces byte-identical wire to passing the equivalent {@link Map}.
   * The {@code Map} overload remains for the genuinely-dynamic/forward-compat path.
   *
   * @param config the typed connect config; when {@code null}, renders an empty {@code connect}.
   */
  public Service connect(com.signalwire.sdk.swml.generated.ConnectConfig config) {
    document.addVerb(
        "connect", config != null ? config : new com.signalwire.sdk.swml.generated.ConnectConfig());
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

  /** Sleep takes an integer (milliseconds), not a map. */
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

  /** Read request body with size limit. */
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

  /** Send a JSON response. */
  protected void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
    String json = gson.toJson(body);
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  /** Send a 401 Unauthorized response. */
  protected void sendUnauthorized(HttpExchange exchange) throws IOException {
    exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"SWML Service\"");
    exchange.sendResponseHeaders(401, -1);
    exchange.close();
  }

  /** Send a 413 Payload Too Large response. */
  protected void sendPayloadTooLarge(HttpExchange exchange) throws IOException {
    exchange.sendResponseHeaders(413, -1);
    exchange.close();
  }

  /**
   * Start the HTTP server with health, ready, /swaig, and main SWML endpoint. Subclasses
   * (AgentBase) add additional routes via {@link #registerAdditionalRoutes(HttpServer)} and
   * customize SWML rendering via {@link #renderMainSwml(HttpExchange)}.
   */
  public void serve() throws IOException {
    httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
    httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    registerRoutes(httpServer);
    httpServer.start();
    String basePath = "/".equals(route) ? "" : route;
    log.info(
        "Service '%s' listening on %s:%d%s", name, host, port, basePath.isEmpty() ? "/" : basePath);
  }

  /**
   * Register this service's routes (health, ready, {@code /swaig}, subclass extras, and the main
   * SWML endpoint) onto the given {@link HttpServer}. Shared by {@link #serve()} (which binds and
   * starts its own server) and {@link #asRouter()} (which registers them onto an unbound server it
   * hands back as a mountable handler). Keeping the route table in one place guarantees the
   * embedded-in-a-host-app handler serves exactly what the standalone server does.
   */
  protected void registerRoutes(HttpServer server) {
    // Health endpoint (no auth)
    server.createContext(
        "/health",
        exchange -> {
          try {
            sendJson(exchange, 200, Map.of("status", "healthy"));
          } catch (Exception e) {
            log.error("Health handler error", e);
          }
        });

    // Ready endpoint (no auth)
    server.createContext(
        "/ready",
        exchange -> {
          try {
            sendJson(exchange, 200, Map.of("status", "ready"));
          } catch (Exception e) {
            log.error("Ready handler error", e);
          }
        });

    String basePath = "/".equals(route) ? "" : route;

    // SWAIG endpoint (with auth) — GET returns SWML, POST dispatches a tool.
    server.createContext(
        basePath + "/swaig",
        exchange -> {
          try {
            handleSwaigEndpoint(exchange);
          } catch (Exception e) {
            log.error("SWAIG handler error", e);
            try {
              exchange.sendResponseHeaders(500, -1);
              exchange.close();
            } catch (Exception ignored) {
              // best-effort error response; nothing to do if the exchange is already gone
            }
          }
        });

    // Subclass extension hook — AgentBase adds /post_prompt, /mcp here.
    registerAdditionalRoutes(server);

    // Main SWML endpoint (with auth)
    String swmlPath = basePath.isEmpty() ? "/" : basePath;
    server.createContext(
        swmlPath,
        exchange -> {
          try {
            String path = exchange.getRequestURI().getPath();
            // Don't shadow sub-paths owned by sibling handlers.
            if ((basePath + "/swaig").equals(path)
                || (basePath + "/post_prompt").equals(path)
                || (basePath + "/mcp").equals(path)) {
              return;
            }
            handleSwmlExchange(exchange);
          } catch (Exception e) {
            log.error("SWML handler error", e);
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
          }
        });
  }

  /**
   * Thin framework adapter for the served SWML endpoint: it captures the raw request off the {@link
   * HttpExchange} (body-size limit, signed-webhook validation — the plumbing that genuinely needs
   * the exchange), then routes the auth / routing-callback / render DECISION through {@link
   * #handleRequest}, and marshals the returned {@code (status, headers, body)} triple back onto the
   * exchange. This is what makes {@link #serve()} and {@link #asRouter()} reach the
   * routing-callback {@code 307} branch (previously the served path re-implemented auth+render
   * inline and never called the routing callback). Mirrors the .NET {@code DispatchAsync} / {@code
   * RunHttp} adapters that funnel the served request through {@code HandleRequest}.
   */
  protected void handleSwmlExchange(HttpExchange exchange) throws IOException {
    String method = exchange.getRequestMethod();

    // Raw-body capture is framework plumbing that needs the exchange: enforce the
    // size limit and the signed-webhook check here, BEFORE the request reaches the
    // primitive dispatch core (which operates over already-parsed primitives).
    String rawBody = null;
    if ("POST".equalsIgnoreCase(method)) {
      try {
        rawBody = readBody(exchange);
      } catch (IOException e) {
        sendPayloadTooLarge(exchange);
        return;
      }
      // Stash for downstream consumers that pull the body off the exchange.
      exchange.setAttribute(REQUEST_BODY_ATTR, rawBody);
      // Signature validation is gated behind auth (handleRequest re-checks auth);
      // this mirrors the reference where signing is layered on top of basic auth,
      // and the served path must still 403 an invalid signature before rendering.
      if (validateAuth(exchange) && !validateSignedWebhook(exchange, rawBody)) {
        // No body detail per webhooks.md.
        exchange.sendResponseHeaders(403, -1);
        exchange.close();
        return;
      }
    }

    // Build the primitive request (method, url, headers, body) from the exchange.
    String url = fullUrlOf(exchange);
    java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
    exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, v.isEmpty() ? "" : v.get(0)));
    java.util.Map<String, Object> parsedBody = null;
    if (rawBody != null && !rawBody.isEmpty()) {
      try {
        java.lang.reflect.Type type =
            new com.google.gson.reflect.TypeToken<java.util.Map<String, Object>>() {}.getType();
        parsedBody = gson.fromJson(rawBody, type);
      } catch (Exception e) {
        log.warn("error_parsing_request_body");
      }
    }

    // The DECISION (401 auth / 307 routing / 200 render) comes from handleRequest.
    HttpResult result = handleRequest(method, url, headers, parsedBody);

    if (result.status() == 401) {
      sendUnauthorized(exchange);
      return;
    }
    // Authenticated responses carry the security headers.
    addSecurityHeaders(exchange);
    for (java.util.Map.Entry<String, String> h : result.headers().entrySet()) {
      exchange.getResponseHeaders().set(h.getKey(), h.getValue());
    }
    if (result.status() == 307) {
      // Redirect: 307 preserves method+body; no response body.
      exchange.sendResponseHeaders(307, -1);
      exchange.close();
      return;
    }
    // 200 (or any other body-bearing status): write the rendered SWML JSON.
    byte[] bytes = result.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(result.status(), bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  /** Reconstruct the full request URL (scheme://host/path?query) from an {@link HttpExchange}. */
  private static String fullUrlOf(HttpExchange exchange) {
    java.net.URI uri = exchange.getRequestURI();
    String hostHeader = exchange.getRequestHeaders().getFirst("Host");
    String authority = hostHeader != null ? hostHeader : "localhost";
    String pathPart = uri.getRawPath() != null ? uri.getRawPath() : "/";
    String query = uri.getRawQuery();
    return "http://" + authority + pathPart + (query != null ? "?" + query : "");
  }

  /**
   * Handle GET/POST to /swaig. Lifted from AgentBase. GET: returns the rendered SWML doc (parallel
   * to root /). POST: parses {function, argument, call_id}, validates the function name, calls
   * swaigPreDispatch hook, then dispatches via onFunctionCall.
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
    java.util.Map<String, Object> argument =
        (java.util.Map<String, Object>) payload.get("argument");
    if (argument != null) {
      @SuppressWarnings("unchecked")
      java.util.List<java.util.Map<String, Object>> parsed =
          (java.util.List<java.util.Map<String, Object>>) argument.get("parsed");
      if (parsed != null && !parsed.isEmpty()) {
        args.putAll(parsed.get(0));
      }
    } else {
      @SuppressWarnings("unchecked")
      java.util.Map<String, Object> arguments =
          (java.util.Map<String, Object>) payload.get("arguments");
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

  /** Stop the HTTP server. */
  public void stop() {
    if (httpServer != null) {
      httpServer.stop(0);
      log.info("Service '%s' stopped", name);
    }
  }

  /**
   * {@link AutoCloseable} entry point so a service — and every subclass, including {@code
   * AgentBase} — can be served inside a try-with-resources block:
   *
   * <pre>{@code
   * try (var agent = MyAgent.builder()...build()) {
   *     agent.run();
   *     // ... serve ...
   * } // close() runs here: the HTTP listener is shut down
   * }</pre>
   *
   * Delegates to {@link #stop()}, releasing the bound HTTP(S) listener and its socket — the rough
   * Java parallel of using a Python SWMLService / AgentBase under a context manager. Idempotent:
   * harmless if the service was never served or is already stopped.
   */
  @Override
  public void close() {
    stop();
  }
}
