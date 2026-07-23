/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client for the SignalWire AI Chat service.
 *
 * <p>Speaks the standard SignalWire front-door protocol: HTTP Basic {@code project:api_token} with
 * the space in the hostname — {@code POST https://{space}.signalwire.com/api/ai/chat} — carrying a
 * JSON-RPC 2.0 body whose params are pure payload (identity NEVER appears in the body; it rides the
 * Basic-auth header only).
 *
 * <p>A {@link #chat} call awaits a full LLM round trip (seconds, not milliseconds). The service
 * streams keepalive whitespace ahead of a slow response body (proxy read-timeout protection), so
 * liveness is byte-driven rather than wall-clock: there is NO total-request timeout an
 * idle-but-live turn could trip. {@code java.net.http.HttpClient} offers a connect timeout and a
 * per-request timeout; the per-request timeout is a wall-clock cap on the WHOLE exchange (it cannot
 * be reset by a heartbeat), so imposing it would sever a slow-but-live turn — exactly what the
 * streaming note forbids. We therefore set a bounded {@code connectTimeout} and leave the
 * per-request timeout UNSET (no total cap), the closest {@code java.net.http} equivalent of the
 * python reference's {@code aiohttp.ClientTimeout(total=None, connect=10, sock_read=60)}: a live
 * turn is never capped, and a truly dead connection is caught by the OS/TCP layer. Leading
 * keepalive whitespace is valid JSON, so the buffered {@code HttpResponse.BodyHandlers.ofString()}
 * parse is unaffected.
 *
 * <p>Mirrors the python reference {@code signalwire.ai_chat.AIChatClient}.
 *
 * <pre>{@code
 * AIChatClient client = new AIChatClient(AIChatClientOptions.builder().space("myspace").build());
 * client.createConversation("conv-1", CreateConversationOptions.builder().configUrl(CFG).build());
 * ChatResponse reply = client.chat("conv-1", "hello");
 * System.out.println(reply.getText());
 * }</pre>
 */
public class AIChatClient implements AutoCloseable {

  /** Default endpoint path appended to a {@code space}-derived base URL. */
  static final String DEFAULT_PATH = "/api/ai/chat";

  /** Bounded connect timeout — mirrors the python reference's {@code connect=10}. */
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  private static final Gson GSON = new Gson();

  /** JSON-RPC error code → the typed error it maps to. Unmapped codes fall to the base error. */
  private static final Map<Integer, ErrorFactory> ERROR_BY_CODE = new LinkedHashMap<>();

  static {
    ERROR_BY_CODE.put(-32001, ConversationNotFoundError::new);
    ERROR_BY_CODE.put(-32005, RateLimitError::new);
    ERROR_BY_CODE.put(-32006, RateLimitError::new);
    ERROR_BY_CODE.put(-32007, ChatInProgressError::new);
    ERROR_BY_CODE.put(-32009, AuthenticationError::new);
  }

  @FunctionalInterface
  private interface ErrorFactory {
    AIChatError create(Integer code, String message);
  }

  /** Fully-qualified endpoint URL requests are POSTed to. */
  private final String url;

  private final String authHeader;
  private final String userAgent;
  private final java.net.http.HttpClient httpClient;
  private int requestCounter = 0;

  /**
   * Create a client from the standard environment variables ({@code SIGNALWIRE_PROJECT_ID} / {@code
   * SIGNALWIRE_API_TOKEN} / {@code SIGNALWIRE_SPACE}).
   */
  public AIChatClient() {
    this(AIChatClientOptions.builder().build());
  }

  /**
   * Create a client.
   *
   * @param options connection + credential options. Either {@code url} or {@code space} (or {@code
   *     SIGNALWIRE_SPACE}) must resolve a target; {@code project} is required (option or {@code
   *     SIGNALWIRE_PROJECT_ID}).
   * @throws IllegalArgumentException when no project is available, or no URL can be resolved.
   */
  public AIChatClient(AIChatClientOptions options) {
    String project = orEnv(options.getProject(), "SIGNALWIRE_PROJECT_ID");
    String token = orEnv(options.getToken(), "SIGNALWIRE_API_TOKEN");
    String space = orEnv(options.getSpace(), "SIGNALWIRE_SPACE");

    if (project.isEmpty()) {
      throw new IllegalArgumentException(
          "project is required. Provide it as an option or set the "
              + "SIGNALWIRE_PROJECT_ID environment variable.");
    }

    this.url = resolveUrl(options.getUrl(), space);
    this.authHeader =
        "Basic "
            + Base64.getEncoder()
                .encodeToString((project + ":" + token).getBytes(StandardCharsets.UTF_8));
    this.userAgent = "signalwire-java-ai-chat/" + version();
    this.httpClient =
        java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
  }

  private static String orEnv(String value, String envVar) {
    if (value != null && !value.isEmpty()) {
      return value;
    }
    String env = System.getenv(envVar);
    return env != null ? env : "";
  }

  private static String resolveUrl(String url, String space) {
    if (url != null && !url.isEmpty()) {
      return url;
    }
    if (!space.isEmpty()) {
      return "https://" + space + ".signalwire.com" + DEFAULT_PATH;
    }
    throw new IllegalArgumentException(
        "No service URL: provide url= or space= / SIGNALWIRE_SPACE.");
  }

  private static String version() {
    Package pkg = AIChatClient.class.getPackage();
    String v = pkg != null ? pkg.getImplementationVersion() : null;
    return v != null ? v : "dev";
  }

  /**
   * The fully-qualified endpoint URL requests are POSTed to.
   *
   * @return the endpoint URL.
   */
  public String getUrl() {
    return url;
  }

  /**
   * Release any client-side resources.
   *
   * <p>The AI Chat client is built on {@link java.net.http.HttpClient}, which is sessionless — each
   * call is a self-contained request with no pooled connection state this client owns — so there is
   * nothing to tear down. {@code close()} is a well-defined no-op that completes the lifecycle
   * contract (mirroring the Python reference's {@code close()} on its owned aiohttp session),
   * letting callers use the client in a try-with-resources block interchangeably with the other SDK
   * clients.
   */
  @Override
  public void close() {
    // no-op: java.net.http.HttpClient holds no per-client session to release.
  }

  // ── Wire ──────────────────────────────────────────────────────────

  /**
   * POST one JSON-RPC call and return its decoded {@code result} object.
   *
   * <p>Success/failure is decided by the JSON-RPC BODY, not the HTTP status: the service's
   * keepalive heartbeat commits {@code 200} before the turn's outcome is known, so a slow error can
   * arrive as {@code 200 + {"error": …}}. Never gate on the HTTP status here (mirrors the python
   * reference).
   *
   * @throws AIChatError (or a typed subclass) when the body carries {@code error}.
   */
  private JsonObject request(String method, JsonObject params) {
    requestCounter += 1;
    JsonObject payload = new JsonObject();
    payload.addProperty("jsonrpc", "2.0");
    payload.addProperty("method", method);
    payload.add("params", params);
    payload.addProperty("id", "req-" + requestCounter);

    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", authHeader)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            // No per-request timeout: it is a wall-clock cap on the whole exchange a
            // keepalive heartbeat cannot reset, so setting it would sever a slow-but-
            // live turn. See the class javadoc / streaming_note.
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8))
            .build();

    HttpResponse<String> response;
    try {
      response = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AIChatError(null, "request interrupted: " + e.getMessage());
    } catch (java.io.IOException e) {
      throw new AIChatError(null, "request failed: " + e.getMessage());
    }

    // Buffer the whole body then parse. Leading keepalive whitespace is valid JSON,
    // so a plain parse handles it — no need to strip.
    JsonObject body;
    try {
      body = GSON.fromJson(response.body(), JsonObject.class);
    } catch (JsonParseException e) {
      throw new AIChatError(
          response.statusCode(), "non-JSON response (HTTP " + response.statusCode() + ")");
    }
    if (body == null) {
      throw new AIChatError(
          response.statusCode(), "non-JSON response (HTTP " + response.statusCode() + ")");
    }

    if (body.has("error") && !body.get("error").isJsonNull()) {
      JsonObject error = body.getAsJsonObject("error");
      Integer code =
          error.has("code") && !error.get("code").isJsonNull()
              ? error.get("code").getAsInt()
              : null;
      String message =
          error.has("message") && !error.get("message").isJsonNull()
              ? error.get("message").getAsString()
              : "";
      ErrorFactory factory = code != null ? ERROR_BY_CODE.get(code) : null;
      if (factory != null) {
        throw factory.create(code, message);
      }
      throw new AIChatError(code, message);
    }

    if (body.has("result") && body.get("result").isJsonObject()) {
      return body.getAsJsonObject("result");
    }
    return new JsonObject();
  }

  // ── API methods ───────────────────────────────────────────────────

  /**
   * Create a conversation (or, with {@code reinit}, reinitialize an existing one) and optionally
   * send its opening user message.
   *
   * @param conversationId the conversation id to create.
   * @param options must include {@code configUrl}; other fields are optional.
   * @return the created conversation's status + optional opening message.
   */
  public ConversationInfo createConversation(
      String conversationId, CreateConversationOptions options) {
    JsonObject params = new JsonObject();
    params.addProperty("id", conversationId);
    params.addProperty("config_url", options.getConfigUrl());
    if (isPresent(options.getUserMessage())) {
      params.addProperty("user_message", options.getUserMessage());
    }
    if (options.getTimeout() != null) {
      params.addProperty("conversation_timeout", options.getTimeout());
    }
    if (options.getUserMetadata() != null) {
      params.add("user_meta_data", GSON.toJsonTree(options.getUserMetadata()));
    }
    if (options.isReinit()) {
      params.addProperty("reinit", true);
    }

    JsonObject result = request("create_conversation", params);
    return new ConversationInfo(
        conversationId,
        getString(result, "status", "created"),
        getStringOrNull(result, "initial_message"));
  }

  /**
   * Send a message and await a full LLM round trip.
   *
   * <p>Passing {@code configUrl} auto-creates the conversation if it doesn't exist yet; {@code
   * timeout} and {@code reinit} apply to that auto-create, with the same meaning as on {@link
   * #createConversation}. Expect seconds — the turn awaits the model.
   *
   * @param conversationId the conversation to send into.
   * @param message the user (or system) message text.
   * @return the assistant reply text plus any structured user event.
   */
  public ChatResponse chat(String conversationId, String message) {
    return chat(conversationId, message, ChatOptions.builder().build());
  }

  /**
   * Send a message and await a full LLM round trip, with options.
   *
   * @param conversationId the conversation to send into.
   * @param message the user (or system) message text.
   * @param options optional role / auto-create / metadata fields.
   * @return the assistant reply text plus any structured user event.
   */
  public ChatResponse chat(String conversationId, String message, ChatOptions options) {
    JsonObject params = new JsonObject();
    params.addProperty("id", conversationId);
    params.addProperty("message", message);
    params.addProperty("role", options.getRole());
    if (isPresent(options.getConfigUrl())) {
      params.addProperty("config_url", options.getConfigUrl());
    }
    if (options.getUserMetadata() != null) {
      params.add("user_meta_data", GSON.toJsonTree(options.getUserMetadata()));
    }
    if (options.getTimeout() != null) {
      params.addProperty("conversation_timeout", options.getTimeout());
    }
    if (options.isReinit()) {
      params.addProperty("reinit", true);
    }

    JsonObject result = request("chat", params);
    Map<String, Object> userEvent =
        result.has("user_event") && result.get("user_event").isJsonObject()
            ? jsonObjectToMap(result.getAsJsonObject("user_event"))
            : null;
    return new ChatResponse(getString(result, "response", ""), conversationId, userEvent);
  }

  /**
   * End a conversation (triggers server-side post-processing / archival).
   *
   * @param conversationId the conversation to end.
   * @return {@code true} when the service reported the conversation ended.
   */
  public boolean end(String conversationId) {
    JsonObject params = new JsonObject();
    params.addProperty("id", conversationId);
    JsonObject result = request("end_conversation", params);
    return "ended".equals(getStringOrNull(result, "status"));
  }

  /**
   * Permanently delete a conversation and its data. Idempotent.
   *
   * @param conversationId the conversation to delete.
   * @return {@code true} when the service reported the conversation deleted.
   */
  public boolean delete(String conversationId) {
    JsonObject params = new JsonObject();
    params.addProperty("id", conversationId);
    JsonObject result = request("delete", params);
    return "deleted".equals(getStringOrNull(result, "status"));
  }

  /**
   * Return the full message history plus the call timeline.
   *
   * @param conversationId the conversation to read.
   * @return the message list and call timeline.
   */
  public ChatLog log(String conversationId) {
    JsonObject params = new JsonObject();
    params.addProperty("id", conversationId);
    JsonObject result = request("chat_log", params);
    return new ChatLog(
        jsonArrayToList(result, "chat_log"), jsonArrayToList(result, "call_timeline"));
  }

  /**
   * Return an AI summary of the conversation (rate limited server-side).
   *
   * <p>The service returns EXACTLY ONE of {@code {summary}} or {@code {error}} — BOTH on the
   * success envelope — so a failed generation surfaces as a thrown {@link SummaryError}, never as
   * an empty string.
   *
   * @param conversationId the conversation to summarize.
   * @return the summary text.
   * @throws SummaryError when the service reports summary generation failed.
   */
  public String summarize(String conversationId) {
    return summarize(conversationId, SummarizeOptions.builder().build());
  }

  /**
   * Return an AI summary of the conversation, with a custom prompt + sampling parameters.
   *
   * @param conversationId the conversation to summarize.
   * @param options optional custom prompt + sampling parameters.
   * @return the summary text.
   * @throws SummaryError when the service reports summary generation failed.
   */
  public String summarize(String conversationId, SummarizeOptions options) {
    JsonObject params = new JsonObject();
    params.addProperty("id", conversationId);
    if (isPresent(options.getSummaryPrompt())) {
      params.addProperty("summary_prompt", options.getSummaryPrompt());
    }
    if (options.getTemperature() != null) {
      params.addProperty("temperature", options.getTemperature());
    }
    if (options.getTopP() != null) {
      params.addProperty("top_p", options.getTopP());
    }
    if (options.getFrequencyPenalty() != null) {
      params.addProperty("frequency_penalty", options.getFrequencyPenalty());
    }
    if (options.getPresencePenalty() != null) {
      params.addProperty("presence_penalty", options.getPresencePenalty());
    }
    if (options.getMaxTokens() != null) {
      params.addProperty("max_tokens", options.getMaxTokens());
    }

    JsonObject result = request("summarize", params);
    if (result.has("error") && !result.has("summary")) {
      throw new SummaryError(null, result.get("error").getAsString());
    }
    return getString(result, "summary", "");
  }

  // ── decode helpers ────────────────────────────────────────────────

  private static boolean isPresent(String s) {
    return s != null && !s.isEmpty();
  }

  private static String getString(JsonObject o, String key, String fallback) {
    return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
  }

  private static String getStringOrNull(JsonObject o, String key) {
    return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> jsonObjectToMap(JsonObject o) {
    return GSON.fromJson(o, Map.class);
  }

  @SuppressWarnings("unchecked")
  private static java.util.List<Map<String, Object>> jsonArrayToList(JsonObject o, String key) {
    if (!o.has(key) || !o.get(key).isJsonArray()) {
      return java.util.Collections.emptyList();
    }
    return GSON.fromJson(o.get(key), java.util.List.class);
  }
}
