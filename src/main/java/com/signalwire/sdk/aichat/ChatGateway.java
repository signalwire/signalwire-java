/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Server-side proxy that lets a browser chat with the SignalWire AI Chat service without holding a
 * token.
 *
 * <p>A chat widget in a page cannot hold a SignalWire API token — the token carries the whole
 * project, and every turn bills. So the widget talks to this gateway, mounted in your own app,
 * which holds the credential server-side and forwards on the widget's behalf:
 *
 * <pre>
 *   browser ──(publishable key)──▶ your app ──(project:token)──▶ chat service
 * </pre>
 *
 * <p>The browser learns exactly two things: the gateway's URL and a publishable key. The gateway
 * injects {@code config_url} itself, so a key can only ever reach the one agent it was issued for.
 * Mount it on a JDK {@code HttpServer} (an {@code AgentBase} already runs one):
 *
 * <pre>{@code
 * ChatGateway gateway = new ChatGateway(
 *     ChatGatewayOptions.builder("https://my-agent.example.com/swml")
 *         .key("pk_live_...")
 *         .allowedOrigins(List.of("https://shop.example.com"))
 *         .build());
 * agent.getApp().createContext("/chat", gateway.router());
 * }</pre>
 *
 * <p>What a stolen key gets you is the ability to <em>talk</em>, which costs the project money;
 * {@code max_new_conversations} and {@code max_turns} bound that bill from both directions. The
 * origin allowlist is leak containment (a browser sends the page's origin), not access control. The
 * single browser-supplied field forwarded rather than overwritten is {@code user_meta_data}, the
 * page context a widget collects about itself; it is bounded and kept nested under its own key.
 * Every size-bearing field is bounded and answered with {@code 413} past its limit.
 *
 * <p>Counters live in this process; behind several replicas each holds its own.
 */
public class ChatGateway implements AutoCloseable {

  /** A handle outlives a page refresh but not a session left open overnight. */
  public static final int DEFAULT_HANDLE_TTL = 24 * 60 * 60;

  /** The chat service's own default conversation timeout, reported when none is configured. */
  public static final int SERVICE_DEFAULT_CONVERSATION_TIMEOUT = 3600;

  /** New conversations per window, per gateway. */
  public static final int DEFAULT_MAX_NEW_CONVERSATIONS = 60;

  /** Turns per conversation, ever. */
  public static final int DEFAULT_MAX_TURNS = 200;

  /** Window for the new-conversation cap, in seconds. */
  public static final int DEFAULT_WINDOW_SECONDS = 60;

  /** Bound on the browser-volunteered {@code user_meta_data} bag, serialized. */
  public static final int MAX_USER_METADATA_BYTES = 8 * 1024;

  /** Bound on one typed message, UTF-8 encoded. */
  public static final int MAX_MESSAGE_BYTES = 8 * 1024;

  /** Bound on a whole request body, checked before it is parsed. */
  public static final int MAX_REQUEST_BODY_BYTES = 64 * 1024;

  /** Browser methods the gateway accepts. */
  public static final Set<String> ALLOWED_METHODS = Set.of("start", "chat", "log", "end");

  /** Roles a browser may see in a restored transcript. */
  public static final Set<String> VISIBLE_ROLES = Set.of("user", "assistant");

  private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

  private static final SecureRandom RANDOM = new SecureRandom();

  private final String configUrl;
  private final String key;
  private final Set<String> allowedOrigins;
  private final int handleTtl;
  private final Integer conversationTimeout;
  private final int maxNewConversations;
  private final int maxTurns;
  private final int windowSeconds;

  private final AIChatClient client;
  private final boolean ownsClient;
  private final byte[] secret;

  private final List<Double> mints = new ArrayList<>();
  private final Map<String, double[]> turns = new LinkedHashMap<>();

  /**
   * Build a gateway that fronts one agent for browser traffic.
   *
   * @param options the gateway's configuration; {@code configUrl} is required.
   * @throws IllegalArgumentException if {@code configUrl} is empty, or no client was given and none
   *     can be built from the environment.
   */
  public ChatGateway(ChatGatewayOptions options) {
    String cfg = options.getConfigUrl();
    if (cfg == null || cfg.isEmpty()) {
      throw new IllegalArgumentException("config_url is required — it is what a key is scoped to.");
    }
    this.configUrl = cfg;
    String k = options.getKey();
    if (k == null || k.isEmpty()) {
      k = System.getenv("SIGNALWIRE_CHAT_GATEWAY_KEY");
    }
    if (k == null || k.isEmpty()) {
      k = "pk_" + tokenUrlsafe(24);
    }
    this.key = k;
    Set<String> origins = new LinkedHashSet<>();
    for (String o : options.getAllowedOrigins()) {
      origins.add(stripTrailingSlashes(o));
    }
    this.allowedOrigins = Collections.unmodifiableSet(origins);
    this.handleTtl = options.getHandleTtl();
    this.conversationTimeout = options.getConversationTimeout();
    this.maxNewConversations = options.getMaxNewConversations();
    this.maxTurns = options.getMaxTurns();
    this.windowSeconds = options.getWindowSeconds();

    AIChatClient c = options.getClient();
    this.ownsClient = c == null;
    this.client = c != null ? c : new AIChatClient();

    byte[] s = options.getSecret();
    if (s == null) {
      String env = System.getenv("SIGNALWIRE_CHAT_GATEWAY_SECRET");
      if (env != null && !env.isEmpty()) {
        s = env.getBytes(StandardCharsets.UTF_8);
      } else {
        s = new byte[32];
        RANDOM.nextBytes(s);
      }
    }
    this.secret = s;
  }

  // ── Attributes ────────────────────────────────────────────────────

  /**
   * The SWML config URL every upstream call carries. Never taken from the request.
   *
   * @return the config URL.
   */
  public String getConfigUrl() {
    return configUrl;
  }

  /**
   * The publishable key the browser presents.
   *
   * @return the key.
   */
  public String getKey() {
    return key;
  }

  /**
   * Origins permitted besides localhost, with any trailing {@code /} removed.
   *
   * @return an unmodifiable set of origins.
   */
  public Set<String> getAllowedOrigins() {
    return allowedOrigins;
  }

  /**
   * Seconds a signed handle stays valid.
   *
   * @return the handle TTL.
   */
  public int getHandleTtl() {
    return handleTtl;
  }

  /**
   * Idle seconds before the service ends a conversation, as configured.
   *
   * @return the timeout, or {@code null} to leave it to the service default.
   */
  public Integer getConversationTimeout() {
    return conversationTimeout;
  }

  /**
   * Cap on conversations minted per window.
   *
   * @return the cap.
   */
  public int getMaxNewConversations() {
    return maxNewConversations;
  }

  /**
   * Cap on turns per conversation.
   *
   * @return the cap.
   */
  public int getMaxTurns() {
    return maxTurns;
  }

  /**
   * Length of the rolling window the mint cap counts over.
   *
   * @return the window in seconds.
   */
  public int getWindowSeconds() {
    return windowSeconds;
  }

  /**
   * Idle seconds a conversation actually gets: the configured timeout, or the service's default
   * when unset — never null, so a widget can always schedule its idle warning.
   *
   * @return the effective timeout in seconds.
   */
  public int getEffectiveTimeout() {
    return conversationTimeout != null && conversationTimeout != 0
        ? conversationTimeout
        : SERVICE_DEFAULT_CONVERSATION_TIMEOUT;
  }

  /**
   * Epoch SECONDS of the newest message, or {@code null} if nothing is dated.
   *
   * <p>Bootstraps a browser's idle clock across a reload. The service stamps messages in
   * MICROseconds; this converts. Every role counts, not just the visible ones: the service's idle
   * clock runs off {@code updated_at}, which any write moves.
   *
   * @param messages the transcript as the service returned it (may be {@code null}).
   * @return the newest timestamp in seconds, or {@code null}.
   */
  public static Double lastActivity(List<Map<String, Object>> messages) {
    Long newest = null;
    if (messages != null) {
      for (Object msg : messages) {
        if (!(msg instanceof Map<?, ?> m)) {
          continue;
        }
        Long ts = intTimestamp(m.get("timestamp"));
        if (ts != null && ts > 0 && (newest == null || ts > newest)) {
          newest = ts;
        }
      }
    }
    return newest != null ? newest / 1_000_000.0 : null;
  }

  /**
   * The transcript a browser may redraw, and nothing else.
   *
   * <p>{@code chat_log} hands back the conversation as the service holds it: the substituted system
   * prompt first, then tool calls and their results alongside the dialogue. Only user and assistant
   * turns with actual text survive, reduced to role, content and (in epoch seconds) when they were
   * said.
   *
   * @param messages the transcript as the service returned it (may be {@code null}).
   * @return the visible messages.
   */
  public static List<Map<String, Object>> visibleMessages(List<Map<String, Object>> messages) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (messages == null) {
      return out;
    }
    for (Object msg : messages) {
      if (!(msg instanceof Map<?, ?> m)) {
        continue;
      }
      Object role = m.get("role");
      Object content = m.get("content");
      if (role instanceof String r
          && VISIBLE_ROLES.contains(r)
          && content instanceof String c
          && !GatewayHttp.pyStrip(c).isEmpty()) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("role", r);
        entry.put("content", c);
        Long ts = intTimestamp(m.get("timestamp"));
        if (ts != null && ts > 0) {
          entry.put("timestamp", ts / 1_000_000.0);
        }
        out.add(entry);
      }
    }
    return out;
  }

  /** An integer timestamp, accepting a whole Double (how a generic JSON decode yields ints). */
  private static Long intTimestamp(Object ts) {
    if (ts instanceof Long || ts instanceof Integer || ts instanceof Short || ts instanceof Byte) {
      return ((Number) ts).longValue();
    }
    if (ts instanceof java.math.BigInteger bi) {
      return bi.bitLength() < 64 ? bi.longValue() : null;
    }
    if (ts instanceof Double d && !d.isInfinite() && d == Math.rint(d)) {
      return d.longValue();
    }
    return null;
  }

  /**
   * Release the upstream client, if this gateway built it. A client passed in via the options
   * belongs to the caller and is left open.
   */
  @Override
  public void close() {
    if (ownsClient) {
      client.close();
    }
  }

  // ── Handles ───────────────────────────────────────────────────────

  /**
   * Issue a signed handle for a new conversation with a generated id.
   *
   * @return the handle.
   */
  public String mintHandle() {
    return mintHandle(null);
  }

  /**
   * Issue a signed handle for a conversation.
   *
   * <p>The browser never names a conversation. If it did, a publishable key plus a guessed id would
   * be enough to continue someone else's chat; signing means a caller can only present handles this
   * gateway issued.
   *
   * @param conversationId the conversation to name, or {@code null} to generate {@code chat-...}.
   * @return the handle.
   */
  public String mintHandle(String conversationId) {
    String id =
        conversationId == null || conversationId.isEmpty()
            ? "chat-" + tokenUrlsafe(18)
            : conversationId;
    long expires = System.currentTimeMillis() / 1000L + handleTtl;
    byte[] payload = (id + ":" + expires).getBytes(StandardCharsets.UTF_8);
    return b64(payload) + "." + b64(hmac(payload));
  }

  /**
   * Return the conversation id inside a handle, or throw. Signature first, expiry second, both
   * before the id is trusted for anything.
   *
   * @param handle the handle the browser presented.
   * @return the conversation id.
   * @throws GatewayRejection 400 "malformed handle", 403 "invalid handle" or 403 "expired handle".
   */
  public String readHandle(String handle) {
    byte[] payload;
    byte[] given;
    try {
      int dot = handle.indexOf('.');
      if (dot < 0) {
        throw new IllegalArgumentException("no separator");
      }
      payload = unb64(handle.substring(0, dot));
      given = unb64(handle.substring(dot + 1));
    } catch (RuntimeException e) {
      throw new GatewayRejection(400, "malformed handle");
    }
    if (!MessageDigest.isEqual(given, hmac(payload))) {
      throw new GatewayRejection(403, "invalid handle");
    }
    String text;
    try {
      text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(java.nio.ByteBuffer.wrap(payload))
              .toString();
    } catch (CharacterCodingException e) {
      throw new GatewayRejection(400, "malformed handle");
    }
    int colon = text.lastIndexOf(':');
    if (colon < 0) {
      throw new GatewayRejection(400, "malformed handle");
    }
    long expires;
    try {
      expires = Long.parseLong(GatewayHttp.pyStrip(text.substring(colon + 1)).replace("_", ""));
    } catch (NumberFormatException e) {
      throw new GatewayRejection(400, "malformed handle");
    }
    if (System.currentTimeMillis() / 1000.0 > expires) {
      throw new GatewayRejection(403, "expired handle");
    }
    return text.substring(0, colon);
  }

  // ── Guards ────────────────────────────────────────────────────────

  /**
   * Localhost always; anything else must be listed.
   *
   * <p>A missing {@code Origin} is allowed: browsers always send one for the cross-origin POSTs
   * this serves, so absence means a non-browser caller.
   *
   * @param origin the request's {@code Origin} header, or {@code null}.
   * @throws GatewayRejection 403 if the origin is present and not allowed.
   * @throws IllegalArgumentException if the origin carries a malformed IPv6 literal.
   */
  public void checkOrigin(String origin) {
    if (origin == null) {
      return;
    }
    String host = hostname(origin);
    if (LOCAL_HOSTS.contains(host) || host.endsWith(".localhost")) {
      return;
    }
    if (allowedOrigins.contains(stripTrailingSlashes(origin))) {
      return;
    }
    throw new GatewayRejection(403, "origin not allowed");
  }

  /**
   * Verify the publishable key the browser sent, in constant time.
   *
   * @param presented key from the request, or {@code null} when the header is absent.
   * @throws GatewayRejection 401 if the key is missing or does not match.
   */
  public void checkKey(String presented) {
    if (presented == null
        || presented.isEmpty()
        || !MessageDigest.isEqual(
            presented.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
      throw new GatewayRejection(401, "bad key");
    }
  }

  private synchronized void chargeMint() {
    double now = monotonic();
    double cutoff = now - windowSeconds;
    mints.removeIf(t -> t <= cutoff);
    if (mints.size() >= maxNewConversations) {
      throw new GatewayRejection(429, "too many new conversations");
    }
    mints.add(now);
  }

  private synchronized void chargeTurn(String conversationId) {
    double now = monotonic();
    // Swept here rather than on a timer: a handle cannot outlive its TTL, so anything
    // older can never be charged against again.
    double cutoff = now - handleTtl;
    for (Iterator<double[]> it = turns.values().iterator(); it.hasNext(); ) {
      if (it.next()[1] <= cutoff) {
        it.remove();
      }
    }
    double[] entry = turns.get(conversationId);
    int count = entry == null ? 0 : (int) entry[0];
    if (count >= maxTurns) {
      throw new GatewayRejection(429, "conversation turn limit reached");
    }
    turns.put(conversationId, new double[] {count + 1, now});
  }

  // ── The proxied call ──────────────────────────────────────────────

  /**
   * Validate the page context a browser volunteered, or {@code null}. Absent, null and empty all
   * collapse to {@code null}.
   *
   * @param body the decoded request body.
   * @return the metadata object, or {@code null}.
   * @throws GatewayRejection 400 if it is not a JSON object, 413 if it exceeds {@link
   *     #MAX_USER_METADATA_BYTES} serialized.
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> readUserMetadata(Map<String, Object> body) {
    Object raw = body.get("user_meta_data");
    if (raw == null) {
      return null;
    }
    if (!(raw instanceof Map)) {
      throw new GatewayRejection(400, "user_meta_data must be an object");
    }
    Map<String, Object> bag = (Map<String, Object>) raw;
    if (bag.isEmpty()) {
      return null;
    }
    String encoded;
    try {
      encoded = GatewayHttp.JSON.toJson(bag);
    } catch (RuntimeException e) {
      throw new GatewayRejection(400, "user_meta_data must be JSON-serializable");
    }
    if (asciiJsonLength(encoded) > MAX_USER_METADATA_BYTES) {
      throw new GatewayRejection(413, "user_meta_data too large");
    }
    return bag;
  }

  /**
   * Length of compact JSON once every non-ASCII character is written as a {@code \}{@code uXXXX}
   * escape — the bytes Python's {@code json.dumps} (with {@code ensure_ascii}) produces.
   */
  private static int asciiJsonLength(String json) {
    int n = 0;
    for (int i = 0; i < json.length(); i++) {
      n += json.charAt(i) < 0x7f ? 1 : 6;
    }
    return n;
  }

  /**
   * Validate a browser request and build the upstream JSON-RPC call.
   *
   * <p>Everything the browser could use to widen its own access is either rejected or overwritten
   * here: the method must be one of the browser methods, the conversation comes from a signed
   * handle, and {@code config_url} is ours. The single exception is {@code user_meta_data}, which
   * is forwarded. A chat message over {@link #MAX_MESSAGE_BYTES} (UTF-8) is refused with 413 before
   * a conversation is minted or a turn charged.
   *
   * @param body the decoded request body.
   * @param origin the request's {@code Origin} header, or {@code null}.
   * @param key the presented publishable key, or {@code null}.
   * @return the upstream call; {@link PreparedCall#getMintedHandle()} is set only on the call that
   *     created the conversation.
   * @throws GatewayRejection for any refused request.
   * @throws IllegalArgumentException for a {@code method} that is not a scalar.
   */
  public PreparedCall prepare(Map<String, Object> body, String origin, String key) {
    checkKey(key);
    checkOrigin(origin);

    Object method = body.containsKey("method") ? body.get("method") : "chat";
    if (method instanceof Map || method instanceof java.util.Collection) {
      // Python: `method not in frozenset(...)` raises TypeError for an unhashable value.
      throw new IllegalArgumentException("method must be a string");
    }
    if (!(method instanceof String m) || !ALLOWED_METHODS.contains(m)) {
      throw new GatewayRejection(400, "method not allowed");
    }

    // Read before minting so a malformed bag costs the caller nothing.
    Map<String, Object> userMetadata = readUserMetadata(body);

    // The message size is checked before minting for the same reason.
    Object message = body.get("message");
    if ("chat".equals(m)
        && message instanceof String s
        && GatewayHttp.utf8Len(s) > MAX_MESSAGE_BYTES) {
      throw new GatewayRejection(413, "message too large");
    }

    Object handle = body.get("handle");
    String minted = null;
    String conversationId;
    if (GatewayHttp.truthy(handle)) {
      if (!(handle instanceof String h)) {
        throw new GatewayRejection(400, "malformed handle");
      }
      conversationId = readHandle(h);
    } else if ("end".equals(m) || "log".equals(m)) {
      throw new GatewayRejection(400, m + " requires a handle");
    } else {
      chargeMint();
      minted = mintHandle();
      conversationId = readHandle(minted);
    }

    if ("end".equals(m)) {
      return new PreparedCall("end_conversation", mapOf("id", conversationId), null);
    }
    if ("log".equals(m)) {
      // Scoped to the conversation named INSIDE the signed handle, never to anything
      // the caller sent.
      return new PreparedCall("chat_log", mapOf("id", conversationId), null);
    }
    if ("start".equals(m)) {
      Map<String, Object> params = mapOf("id", conversationId);
      params.put("config_url", configUrl);
      if (conversationTimeout != null && conversationTimeout != 0) {
        params.put("conversation_timeout", conversationTimeout);
      }
      if (userMetadata != null) {
        params.put("user_meta_data", userMetadata);
      }
      return new PreparedCall("create_conversation", params, minted);
    }

    if (!(message instanceof String text) || GatewayHttp.pyStrip(text).isEmpty()) {
      throw new GatewayRejection(400, "message is required");
    }

    chargeTurn(conversationId);
    // config_url on every chat so the service auto-creates on the first one and
    // ignores it after; the timeout and the page context ride every chat because any
    // chat may be the one that creates.
    Map<String, Object> chatParams = mapOf("id", conversationId);
    chatParams.put("message", text);
    chatParams.put("config_url", configUrl);
    if (conversationTimeout != null && conversationTimeout != 0) {
      chatParams.put("conversation_timeout", conversationTimeout);
    }
    if (userMetadata != null) {
      chatParams.put("user_meta_data", userMetadata);
    }
    return new PreparedCall("chat", chatParams, minted);
  }

  private static Map<String, Object> mapOf(String k, Object v) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put(k, v);
    return m;
  }

  // ── HTTP surface ──────────────────────────────────────────────────

  /**
   * A mountable handler exposing this gateway — the JDK {@code HttpServer} form of the reference's
   * router. Mount it at a prefix: {@code server.createContext("/chat", gateway.router())}.
   *
   * <p>{@code POST /} takes {@code {"method": "start"|"chat"|"log"|"end", "handle"?, "message"?,
   * "user_meta_data"?}} with the key in {@code Authorization: Bearer}; {@code OPTIONS /} answers
   * the CORS preflight. A chat streams the service's JSON-RPC response body through
   * <b>unbuffered</b> — the service pads slow turns with keepalive whitespace so proxies do not
   * sever the connection, and collecting the body here would swallow that padding. A newly minted
   * handle rides back in the {@code X-Chat-Handle} header. Rejections come back as {@code {"error":
   * reason}} with their status; a body over {@link #MAX_REQUEST_BODY_BYTES} is answered with 413
   * without being parsed.
   *
   * @return the handler.
   */
  public HttpHandler router() {
    return exchange -> {
      try {
        route(exchange);
      } finally {
        exchange.close();
      }
    };
  }

  private void route(HttpExchange exchange) throws IOException {
    String r = GatewayHttp.resolve(exchange, Set.of("/"));
    if (r == null) {
      GatewayHttp.notFound(exchange);
      return;
    }
    if (r.startsWith("redirect:")) {
      GatewayHttp.redirect(exchange, r.substring("redirect:".length()));
      return;
    }
    String verb = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
    Map<String, String> cors;
    try {
      cors = cors(exchange.getRequestHeaders().getFirst("Origin"));
    } catch (RuntimeException e) {
      GatewayHttp.sendServerError(exchange);
      return;
    }
    if ("OPTIONS".equals(verb)) {
      if (!cors.isEmpty()) {
        cors.put("Access-Control-Allow-Headers", "Authorization, Content-Type");
        cors.put("Access-Control-Allow-Methods", "POST, OPTIONS");
        cors.put("Access-Control-Max-Age", "600");
      }
      cors.forEach((k, v) -> exchange.getResponseHeaders().set(k, v));
      exchange.sendResponseHeaders(204, -1);
      return;
    }
    if (!"POST".equals(verb)) {
      GatewayHttp.methodNotAllowed(exchange, "OPTIONS");
      return;
    }
    proxy(exchange, cors);
  }

  /** CORS headers for an allowed origin, and none otherwise. */
  private Map<String, String> cors(String origin) {
    Map<String, String> h = new LinkedHashMap<>();
    if (origin == null) {
      return h;
    }
    try {
      checkOrigin(origin);
    } catch (GatewayRejection e) {
      return h;
    }
    h.put("Access-Control-Allow-Origin", origin);
    h.put("Access-Control-Expose-Headers", "X-Chat-Handle");
    h.put("Vary", "Origin");
    return h;
  }

  @SuppressWarnings("unchecked")
  private void proxy(HttpExchange exchange, Map<String, String> cors) throws IOException {
    String origin = exchange.getRequestHeaders().getFirst("Origin");
    String auth = exchange.getRequestHeaders().getFirst("Authorization");
    if (auth == null) {
      auth = "";
    }
    String presented =
        auth.toLowerCase(Locale.ROOT).startsWith("bearer ") ? auth.substring(7) : null;

    PreparedCall call;
    try {
      Object body = GatewayHttp.parseJsonBody(exchange, MAX_REQUEST_BODY_BYTES);
      if (!(body instanceof Map)) {
        throw new GatewayRejection(400, "body must be an object");
      }
      call = prepare((Map<String, Object>) body, origin, presented);
    } catch (GatewayRejection rej) {
      GatewayHttp.sendJson(exchange, rej.getStatus(), Map.of("error", rej.getReason()), cors);
      return;
    } catch (RuntimeException e) {
      GatewayHttp.sendJson(exchange, 400, Map.of("error", "bad request"), cors);
      return;
    }

    Map<String, Object> params = call.getParams();
    try {
      switch (call.getMethod()) {
        case "end_conversation" -> {
          client.end((String) params.get("id"));
          GatewayHttp.sendJson(exchange, 200, Map.of("status", "ended"), cors);
        }
        case "create_conversation" -> {
          // prepare() decides whether a timeout applies; dropping it here would report
          // one number to the browser while the service quietly keeps its own default.
          CreateConversationOptions.Builder b =
              CreateConversationOptions.builder().configUrl((String) params.get("config_url"));
          if (params.get("conversation_timeout") instanceof Integer t) {
            b.timeout(t);
          }
          if (params.get("user_meta_data") instanceof Map<?, ?> meta) {
            b.userMetadata((Map<String, Object>) meta);
          }
          ConversationInfo info = client.createConversation((String) params.get("id"), b.build());
          Map<String, String> headers = new LinkedHashMap<>(cors);
          if (call.getMintedHandle() != null) {
            headers.put("X-Chat-Handle", call.getMintedHandle());
          }
          Map<String, Object> out = new LinkedHashMap<>();
          out.put("greeting", info.getInitialMessage());
          out.put("status", info.getStatus());
          out.put("timeout", getEffectiveTimeout());
          GatewayHttp.sendJson(exchange, 200, out, headers);
        }
        case "chat_log" -> {
          ChatLog log = client.log((String) params.get("id"));
          Map<String, Object> out = new LinkedHashMap<>();
          out.put("messages", visibleMessages(log.getMessages()));
          out.put("timeout", getEffectiveTimeout());
          // Computed from the raw log — the only place every role's timestamp exists.
          out.put("last_activity", lastActivity(log.getMessages()));
          GatewayHttp.sendJson(exchange, 200, out, cors);
        }
        default -> stream(exchange, call, cors);
      }
    } catch (AIChatError e) {
      GatewayHttp.sendServerError(exchange);
    }
  }

  /** Relay the service's response body chunk by chunk, unbuffered. */
  private void stream(HttpExchange exchange, PreparedCall call, Map<String, String> cors)
      throws IOException {
    cors.forEach((k, v) -> exchange.getResponseHeaders().set(k, v));
    if (call.getMintedHandle() != null) {
      exchange.getResponseHeaders().set("X-Chat-Handle", call.getMintedHandle());
    }
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, 0);
    try (InputStream in = client.rawPost(call.getMethod(), call.getParams());
        OutputStream out = exchange.getResponseBody()) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) {
        out.write(buf, 0, n);
        out.flush();
      }
    }
  }

  // ── helpers ───────────────────────────────────────────────────────

  private byte[] hmac(byte[] payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      // A zero-length key is legal for HMAC but not for SecretKeySpec; HMAC pads the key
      // with zeros to the block size, so one zero byte is the same key.
      mac.init(new SecretKeySpec(secret.length == 0 ? new byte[1] : secret, "HmacSHA256"));
      return mac.doFinal(payload);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HmacSHA256 unavailable", e);
    }
  }

  private static double monotonic() {
    return System.nanoTime() / 1e9;
  }

  private static String tokenUrlsafe(int nbytes) {
    byte[] b = new byte[nbytes];
    RANDOM.nextBytes(b);
    return b64(b);
  }

  private static String stripTrailingSlashes(String s) {
    int end = s.length();
    while (end > 0 && s.charAt(end - 1) == '/') {
      end--;
    }
    return s.substring(0, end);
  }

  /** Unpadded URL-safe base64. */
  private static String b64(byte[] raw) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  /**
   * Python's {@code base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))}: characters outside
   * the alphabet are discarded rather than rejected, a pad sequence ends the input, and an
   * incomplete final quantum is an error.
   */
  static byte[] unb64(String text) {
    StringBuilder padded = new StringBuilder(text);
    int pad = Math.floorMod(-text.length(), 4);
    for (int i = 0; i < pad; i++) {
      padded.append('=');
    }
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    int quadPos = 0;
    int pads = 0;
    int leftChar = 0;
    for (int i = 0; i < padded.length(); i++) {
      char c = padded.charAt(i);
      if (c == '=') {
        if (quadPos >= 2 && quadPos + ++pads >= 4) {
          return out.toByteArray();
        }
        continue;
      }
      int v = b64Value(c);
      if (v < 0) {
        continue;
      }
      pads = 0;
      switch (quadPos) {
        case 0 -> {
          quadPos = 1;
          leftChar = v;
        }
        case 1 -> {
          quadPos = 2;
          out.write((leftChar << 2) | (v >> 4));
          leftChar = v & 0x0f;
        }
        case 2 -> {
          quadPos = 3;
          out.write((leftChar << 4) | (v >> 2));
          leftChar = v & 0x03;
        }
        default -> {
          quadPos = 0;
          out.write((leftChar << 6) | v);
          leftChar = 0;
        }
      }
    }
    if (quadPos != 0) {
      throw new IllegalArgumentException("incorrect base64 padding");
    }
    return out.toByteArray();
  }

  private static int b64Value(char c) {
    if (c >= 'A' && c <= 'Z') {
      return c - 'A';
    }
    if (c >= 'a' && c <= 'z') {
      return c - 'a' + 26;
    }
    if (c >= '0' && c <= '9') {
      return c - '0' + 52;
    }
    if (c == '+' || c == '-') {
      return 62;
    }
    if (c == '/' || c == '_') {
      return 63;
    }
    return -1;
  }

  /**
   * The host of an origin the way {@code urllib.parse.urlparse(origin).hostname} reads it:
   * lower-cased, userinfo and port dropped, IPv6 brackets removed; empty when there is none.
   */
  private static String hostname(String origin) {
    String rest = origin;
    int colon = origin.indexOf(':');
    if (colon > 0 && isScheme(origin.substring(0, colon))) {
      rest = origin.substring(colon + 1);
    }
    if (!rest.startsWith("//")) {
      return "";
    }
    String netloc = rest.substring(2);
    int end = netloc.length();
    for (char d : new char[] {'/', '?', '#'}) {
      int i = netloc.indexOf(d);
      if (i >= 0 && i < end) {
        end = i;
      }
    }
    netloc = netloc.substring(0, end);
    if ((netloc.contains("[") && !netloc.contains("]"))
        || (netloc.contains("]") && !netloc.contains("["))) {
      throw new IllegalArgumentException("Invalid IPv6 URL");
    }
    String hostport = netloc.substring(netloc.lastIndexOf('@') + 1);
    String host;
    if (hostport.startsWith("[")) {
      int close = hostport.indexOf(']');
      host = close > 0 ? hostport.substring(1, close) : "";
    } else {
      int c = hostport.indexOf(':');
      host = c >= 0 ? hostport.substring(0, c) : hostport;
    }
    return host.toLowerCase(Locale.ROOT);
  }

  private static boolean isScheme(String s) {
    if (s.isEmpty() || !Character.isLetter(s.charAt(0))) {
      return false;
    }
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!(c < 0x80 && (Character.isLetterOrDigit(c) || c == '+' || c == '-' || c == '.'))) {
        return false;
      }
    }
    return true;
  }
}
