/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AIChatClient} against a tiny in-process JSON-RPC mock (a {@code
 * com.sun.net.httpserver.HttpServer} on a FREE ephemeral port).
 *
 * <p>The mock mirrors {@code porting-sdk/test_harness/mock_ai_chat}: it decodes the JSON-RPC
 * envelope, records each request, and returns the canned per-method result — including the
 * summarize {@code {error}} one_of branch on the SUCCESS envelope, the {@code __err_<code>}
 * sentinel that forces a JSON-RPC error, and a 401 + {@code -32009} when Basic auth is absent.
 */
class AIChatClientTest {

  private static final Gson GSON = new Gson();

  private HttpServer server;
  private final List<JsonObject> requests = new ArrayList<>();
  private final List<String> authHeaders = new ArrayList<>();
  private String url;

  @BeforeEach
  void setUp() throws Exception {
    requests.clear();
    authHeaders.clear();
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/api/ai/chat", this::handle);
    server.start();
    url =
        "http://"
            + InetAddress.getLoopbackAddress().getHostAddress()
            + ":"
            + server.getAddress().getPort()
            + "/api/ai/chat";
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  private void handle(HttpExchange exchange) throws java.io.IOException {
    String auth = exchange.getRequestHeaders().getFirst("Authorization");
    authHeaders.add(auth);
    // Auth required — mirror the service (401 + -32009).
    if (auth == null || !auth.startsWith("Basic ")) {
      respondError(exchange, -32009, "authentication required", null, 401);
      return;
    }

    byte[] in = exchange.getRequestBody().readAllBytes();
    JsonObject req = GSON.fromJson(new String(in, StandardCharsets.UTF_8), JsonObject.class);
    requests.add(req);
    String method = req.get("method").getAsString();
    Object rid = req.has("id") ? req.get("id").getAsString() : null;
    JsonObject params = req.getAsJsonObject("params");
    String cid = params.has("id") ? params.get("id").getAsString() : null;

    // __err_<code> sentinel: force a JSON-RPC error carrying <code>.
    if (cid != null && cid.startsWith("__err_")) {
      int code = Integer.parseInt(cid.substring("__err_".length()));
      respondError(exchange, code, "forced error", rid, code == -32009 ? 401 : 200);
      return;
    }

    // summarize {error} branch rides the SUCCESS envelope (no JSON-RPC error object).
    if ("summarize".equals(method) && "__summarize_error".equals(cid)) {
      JsonObject result = new JsonObject();
      result.addProperty("error", "Failed to generate summary");
      respondResult(exchange, result, rid);
      return;
    }

    respondResult(exchange, canned(method), rid);
  }

  private static JsonObject canned(String method) {
    JsonObject r = new JsonObject();
    switch (method) {
      case "create_conversation":
        r.addProperty("status", "created");
        r.addProperty("id", "conv-1");
        r.addProperty("initial_message", "hello");
        break;
      case "chat":
        r.addProperty("response", "hi there");
        JsonObject ev = new JsonObject();
        ev.addProperty("event_type", "demo");
        ev.addProperty("n", 1);
        r.add("user_event", ev);
        break;
      case "end_conversation":
        r.addProperty("status", "ended");
        r.addProperty("id", "conv-1");
        break;
      case "delete":
        r.addProperty("status", "deleted");
        r.addProperty("id", "conv-1");
        break;
      case "chat_log":
        r.add("chat_log", GSON.toJsonTree(List.of(Map.of("role", "user", "content", "m"))));
        r.add("call_timeline", GSON.toJsonTree(List.of(Map.of("t", 1))));
        break;
      case "summarize":
        r.addProperty("summary", "a concise summary");
        break;
      default:
        break;
    }
    return r;
  }

  private static void respondResult(HttpExchange exchange, JsonObject result, Object rid)
      throws java.io.IOException {
    JsonObject env = new JsonObject();
    env.addProperty("jsonrpc", "2.0");
    env.add("result", result);
    if (rid != null) {
      env.addProperty("id", rid.toString());
    }
    write(exchange, env, 200);
  }

  private static void respondError(
      HttpExchange exchange, int code, String message, Object rid, int status)
      throws java.io.IOException {
    JsonObject error = new JsonObject();
    error.addProperty("code", code);
    error.addProperty("message", message);
    JsonObject env = new JsonObject();
    env.addProperty("jsonrpc", "2.0");
    env.add("error", error);
    if (rid != null) {
      env.addProperty("id", rid.toString());
    }
    write(exchange, env, status);
  }

  private static void write(HttpExchange exchange, JsonObject env, int status)
      throws java.io.IOException {
    byte[] out = GSON.toJson(env).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, out.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(out);
    }
  }

  private AIChatClient client() {
    return new AIChatClient(
        AIChatClientOptions.builder().url(url).project("proj-1").token("tok-1").build());
  }

  // ── happy-path methods ─────────────────────────────────────────────

  @Test
  @DisplayName("createConversation sends the wire method + required params, decodes the result")
  void createConversation() {
    ConversationInfo info =
        client()
            .createConversation(
                "conv-1",
                CreateConversationOptions.builder()
                    .configUrl("http://cfg")
                    .timeout(30)
                    .reinit(true)
                    .build());
    assertEquals("conv-1", info.getId());
    assertEquals("created", info.getStatus());
    assertEquals("hello", info.getInitialMessage());

    JsonObject params = requests.get(0).getAsJsonObject("params");
    assertEquals("create_conversation", requests.get(0).get("method").getAsString());
    assertEquals("conv-1", params.get("id").getAsString());
    assertEquals("http://cfg", params.get("config_url").getAsString());
    assertEquals(30, params.get("conversation_timeout").getAsInt());
    assertTrue(params.get("reinit").getAsBoolean());
  }

  @Test
  @DisplayName("identity never rides in params; project is the Basic-auth username")
  void identityNeverInParams() {
    client()
        .createConversation("conv-1", CreateConversationOptions.builder().configUrl("c").build());
    JsonObject params = requests.get(0).getAsJsonObject("params");
    for (String forbidden :
        List.of("project_id", "project", "token", "api_token", "space_id", "space")) {
      assertFalse(params.has(forbidden), forbidden + " leaked into params");
    }
    String decoded =
        new String(
            Base64.getDecoder().decode(authHeaders.get(0).substring(6)), StandardCharsets.UTF_8);
    assertEquals("proj-1", decoded.split(":", 2)[0]);
  }

  @Test
  @DisplayName("chat decodes response text + user_event")
  void chat() {
    ChatResponse reply = client().chat("conv-1", "hello");
    assertEquals("hi there", reply.getText());
    assertEquals("conv-1", reply.getConversationId());
    assertEquals("demo", reply.getUserEvent().get("event_type"));
    assertEquals("chat", requests.get(0).get("method").getAsString());
    assertEquals("user", requests.get(0).getAsJsonObject("params").get("role").getAsString());
  }

  @Test
  @DisplayName("end / delete map to their wire methods and derive the boolean status")
  void endAndDelete() {
    assertTrue(client().end("conv-1"));
    assertEquals("end_conversation", requests.get(0).get("method").getAsString());

    requests.clear();
    assertTrue(client().delete("conv-1"));
    assertEquals("delete", requests.get(0).get("method").getAsString());
  }

  @Test
  @DisplayName("log decodes chat_log + call_timeline")
  void log() {
    ChatLog log = client().log("conv-1");
    assertEquals(1, log.getMessages().size());
    assertEquals(1, log.getCallTimeline().size());
    assertEquals("chat_log", requests.get(0).get("method").getAsString());
  }

  @Test
  @DisplayName("summarize returns the {summary} branch")
  void summarizeSuccess() {
    assertEquals("a concise summary", client().summarize("conv-1"));
  }

  // ── the one_of {error} branch: MUST surface as SummaryError, never "" ──

  @Test
  @DisplayName("summarize {error} branch throws SummaryError (never a silent empty string)")
  void summarizeErrorBranchThrows() {
    SummaryError e =
        assertThrows(SummaryError.class, () -> client().summarize("__summarize_error"));
    assertNull(e.getCode(), "SummaryError rides the success envelope — no JSON-RPC code");
    assertEquals("Failed to generate summary", e.getServerMessage());
    // the wire call still happened
    assertEquals("summarize", requests.get(0).get("method").getAsString());
  }

  // ── error-code mapping ──────────────────────────────────────────────

  @Test
  @DisplayName("-32001 → ConversationNotFoundError")
  void notFound() {
    AIChatError e = assertThrows(ConversationNotFoundError.class, () -> forceError(-32001));
    assertEquals(-32001, e.getCode());
  }

  @Test
  @DisplayName("-32005 / -32006 → RateLimitError")
  void rateLimit() {
    assertEquals(-32005, assertThrows(RateLimitError.class, () -> forceError(-32005)).getCode());
    assertEquals(-32006, assertThrows(RateLimitError.class, () -> forceError(-32006)).getCode());
  }

  @Test
  @DisplayName("-32007 → ChatInProgressError")
  void chatInProgress() {
    assertEquals(
        -32007, assertThrows(ChatInProgressError.class, () -> forceError(-32007)).getCode());
  }

  @Test
  @DisplayName("-32009 → AuthenticationError")
  void authenticationError() {
    assertEquals(
        -32009, assertThrows(AuthenticationError.class, () -> forceError(-32009)).getCode());
  }

  @Test
  @DisplayName("an unmapped code falls to the base AIChatError")
  void unmappedCode() {
    AIChatError e = assertThrows(AIChatError.class, () -> forceError(-32602));
    assertEquals(AIChatError.class, e.getClass());
    assertEquals(-32602, e.getCode());
  }

  private void forceError(int code) {
    client().chat("__err_" + code, "x");
  }

  // ── URL resolution + validation ─────────────────────────────────────

  @Test
  @DisplayName("space builds the front-door URL; url overrides it")
  void urlResolution() {
    AIChatClient bySpace =
        new AIChatClient(AIChatClientOptions.builder().project("p").space("myspace").build());
    assertEquals("https://myspace.signalwire.com/api/ai/chat", bySpace.getUrl());

    AIChatClient byUrl =
        new AIChatClient(
            AIChatClientOptions.builder().project("p").url("http://verbatim/x").space("s").build());
    assertEquals("http://verbatim/x", byUrl.getUrl());
  }

  @Test
  @DisplayName("a blank project (no option, no env) is rejected")
  void missingProject() {
    // Only assert when the environment doesn't supply SIGNALWIRE_PROJECT_ID — the
    // constructor's documented fallback. Under the gate/CI (which sets it) the
    // fallback legitimately succeeds, so skip the negative assertion there.
    String env = System.getenv("SIGNALWIRE_PROJECT_ID");
    if (env == null || env.isEmpty()) {
      assertThrows(
          IllegalArgumentException.class,
          () -> new AIChatClient(AIChatClientOptions.builder().url(url).build()));
    }
  }
}
