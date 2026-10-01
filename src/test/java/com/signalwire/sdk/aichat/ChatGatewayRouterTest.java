/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import static com.signalwire.sdk.aichat.AiChatHttpTestSupport.GSON;
import static com.signalwire.sdk.aichat.AiChatHttpTestSupport.base;
import static com.signalwire.sdk.aichat.AiChatHttpTestSupport.headers;
import static com.signalwire.sdk.aichat.AiChatHttpTestSupport.postJson;
import static com.signalwire.sdk.aichat.AiChatHttpTestSupport.postRaw;
import static com.signalwire.sdk.aichat.AiChatHttpTestSupport.send;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Port of the end-to-end half of {@code tests/unit/ai_chat/test_gateway.py} plus {@code
 * test_documented_limits.py::test_the_browser_receives_the_reason}: the gateway's {@link
 * ChatGateway#router()} mounted on a real JDK {@link HttpServer} at {@code /chat}, forwarding to
 * the in-process stub chat service, driven by a real HTTP client.
 */
class ChatGatewayRouterTest {

  private static final String KEY = ChatGatewayTest.KEY;
  private static final String CONFIG_URL = ChatGatewayTest.CONFIG_URL;
  private static final Map<String, String> HEADERS =
      headers("Authorization", "Bearer " + KEY, "Origin", "https://shop.example.com");

  private AiChatHttpTestSupport.StubChatService service;
  private final List<HttpServer> servers = new ArrayList<>();

  @BeforeEach
  void setUp() throws IOException {
    service = new AiChatHttpTestSupport.StubChatService();
  }

  @AfterEach
  void tearDown() {
    servers.forEach(s -> s.stop(0));
    service.close();
  }

  private ChatGateway gateway(Consumer<ChatGatewayOptions.Builder> tune) {
    ChatGatewayOptions.Builder b =
        ChatGatewayOptions.builder(CONFIG_URL)
            .key(KEY)
            .allowedOrigins(List.of("https://shop.example.com"))
            .client(
                new AIChatClient(
                    AIChatClientOptions.builder().project("p").token("t").url(service.url).build()))
            .secret("test-secret");
    tune.accept(b);
    return new ChatGateway(b.build());
  }

  private ChatGateway gateway() {
    return gateway(b -> {});
  }

  /** Mount the gateway at {@code /chat} on a fresh server; return the base URL. */
  private String mount(ChatGateway gw) throws IOException {
    HttpServer s = AiChatHttpTestSupport.start(Map.of("/chat", gw.router()));
    servers.add(s);
    return base(s);
  }

  private static Map<String, Object> body(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> lastParams() {
    return (Map<String, Object>) service.seen.get(service.seen.size() - 1).get("params");
  }

  private String lastMethod() {
    return (String) service.seen.get(service.seen.size() - 1).get("method");
  }

  @Test
  @SuppressWarnings("unchecked")
  void aFullExchangeOverHttp() throws Exception {
    ChatGateway gw = gateway();
    String url = mount(gw) + "/chat/";
    AiChatHttpTestSupport.Resp r = postJson(url, body("message", "hello"), HEADERS);
    assertEquals(200, r.status());
    String handle = r.header("x-chat-handle");
    assertNotNull(handle);
    assertEquals("application/json", r.header("content-type"));
    assertEquals("hi there", ((Map<String, Object>) r.json().get("result")).get("response"));

    Map<String, Object> sent = lastParams();
    assertEquals(CONFIG_URL, sent.get("config_url"));
    assertEquals(gw.readHandle(handle), sent.get("id"));
    assertFalse(GSON.toJson(sent).contains("token"));

    AiChatHttpTestSupport.Resp r2 = postJson(url, body("method", "end", "handle", handle), HEADERS);
    assertEquals(200, r2.status());
    assertEquals(Map.of("status", "ended"), r2.json());
    assertEquals("end_conversation", lastMethod());
  }

  @Test
  void aSecondTurnReusesTheHandle() throws Exception {
    ChatGateway gw = gateway();
    String url = mount(gw) + "/chat/";
    String handle = postJson(url, body("message", "one"), HEADERS).header("x-chat-handle");
    AiChatHttpTestSupport.Resp second =
        postJson(url, body("message", "two", "handle", handle), HEADERS);
    assertNull(second.header("x-chat-handle"));
    assertEquals(gw.readHandle(handle), lastParams().get("id"));
  }

  @Test
  void httpRefusesABadKey() throws Exception {
    String url = mount(gateway()) + "/chat/";
    AiChatHttpTestSupport.Resp r =
        postJson(url, body("message", "hi"), headers("Authorization", "Bearer nope"));
    assertEquals(401, r.status());
    assertEquals(Map.of("error", "bad key"), r.json());
  }

  @Test
  void httpRefusesAnUnlistedOrigin() throws Exception {
    String url = mount(gateway()) + "/chat/";
    AiChatHttpTestSupport.Resp r =
        postJson(
            url,
            body("message", "hi"),
            headers("Authorization", "Bearer " + KEY, "Origin", "https://evil.test"));
    assertEquals(403, r.status());
    assertNull(r.header("access-control-allow-origin"));
  }

  @Test
  void theBearerSchemeIsCaseInsensitive() throws Exception {
    String url = mount(gateway()) + "/chat/";
    AiChatHttpTestSupport.Resp r =
        postJson(url, body("method", "end"), headers("Authorization", "BEARER " + KEY));
    assertEquals(400, r.status());
    assertEquals(Map.of("error", "end requires a handle"), r.json());
  }

  @Test
  void preflightAnswersAListedOrigin() throws Exception {
    String url = mount(gateway()) + "/chat/";
    AiChatHttpTestSupport.Resp r =
        send(
            "OPTIONS",
            url,
            HttpRequest.BodyPublishers.noBody(),
            headers("Origin", "https://shop.example.com"));
    assertEquals(204, r.status());
    assertEquals("https://shop.example.com", r.header("access-control-allow-origin"));
    assertTrue(r.header("access-control-expose-headers").contains("X-Chat-Handle"));
    assertEquals("Authorization, Content-Type", r.header("access-control-allow-headers"));
    assertEquals("POST, OPTIONS", r.header("access-control-allow-methods"));
    assertEquals("600", r.header("access-control-max-age"));
    assertEquals("Origin", r.header("vary"));
  }

  @Test
  void preflightForAnUnlistedOriginCarriesNoCorsHeaders() throws Exception {
    String url = mount(gateway()) + "/chat/";
    AiChatHttpTestSupport.Resp r =
        send(
            "OPTIONS",
            url,
            HttpRequest.BodyPublishers.noBody(),
            headers("Origin", "https://evil.test"));
    assertEquals(204, r.status());
    assertNull(r.header("access-control-allow-origin"));
  }

  @Test
  void routingMatchesTheReferenceRouter() throws Exception {
    // Measured against FastAPI: GET on the endpoint is 405 (Allow: OPTIONS), an unknown
    // sub-path is 404, and the slash-less mount path redirects to the slashed one.
    String b = mount(gateway());
    AiChatHttpTestSupport.Resp get =
        send("GET", b + "/chat/", HttpRequest.BodyPublishers.noBody(), HEADERS);
    assertEquals(405, get.status());
    assertEquals("OPTIONS", get.header("allow"));
    assertEquals(Map.of("detail", "Method Not Allowed"), get.json());
    AiChatHttpTestSupport.Resp nope = postJson(b + "/chat/nope", body(), HEADERS);
    assertEquals(404, nope.status());
    assertEquals(Map.of("detail", "Not Found"), nope.json());
    AiChatHttpTestSupport.Resp noSlash = postJson(b + "/chat", body("method", "end"), HEADERS);
    assertEquals(307, noSlash.status());
    assertTrue(noSlash.header("location").endsWith("/chat/"));
  }

  @Test
  void aNonObjectBodyOrBadJsonIsA400() throws Exception {
    String url = mount(gateway()) + "/chat/";
    AiChatHttpTestSupport.Resp list = postJson(url, List.of(1), HEADERS);
    assertEquals(400, list.status());
    assertEquals(Map.of("error", "body must be an object"), list.json());
    assertEquals("https://shop.example.com", list.header("access-control-allow-origin"));
    AiChatHttpTestSupport.Resp bad =
        postRaw(url, "{nope".getBytes(StandardCharsets.UTF_8), HEADERS);
    assertEquals(400, bad.status());
    assertEquals(Map.of("error", "bad request"), bad.json());
    AiChatHttpTestSupport.Resp unhashable = postJson(url, body("method", List.of("x")), HEADERS);
    assertEquals(400, unhashable.status());
    assertEquals(Map.of("error", "bad request"), unhashable.json());
  }

  @Test
  void theBrowserReceivesTheHandleReason() throws Exception {
    // test_documented_limits.py::test_the_browser_receives_the_reason
    ChatGateway gw = gateway(b -> b.handleTtl(-1));
    String url = mount(gw) + "/chat/";
    AiChatHttpTestSupport.Resp r =
        postJson(
            url,
            body("method", "chat", "message", "hi", "handle", gw.mintHandle()),
            headers("Authorization", "Bearer " + KEY));
    assertEquals(403, r.status());
    assertEquals(Map.of("error", "expired handle"), r.json());
  }

  // ── start / log ───────────────────────────────────────────────────

  @Test
  @SuppressWarnings("unchecked")
  void startThenReloadReplaysTheSameConversation() throws Exception {
    ChatGateway gw = gateway();
    String url = mount(gw) + "/chat/";
    AiChatHttpTestSupport.Resp started = postJson(url, body("method", "start"), HEADERS);
    assertEquals(200, started.status());
    String handle = started.header("x-chat-handle");
    assertEquals("Hi, I am Sigmond.", started.json().get("greeting"));
    assertEquals("created", started.json().get("status"));
    assertEquals(3600L, started.json().get("timeout"));

    AiChatHttpTestSupport.Resp replay =
        postJson(url, body("method", "log", "handle", handle), HEADERS);
    assertEquals(200, replay.status());
    Map<String, Object> j = replay.json();
    assertEquals(
        List.of(
            Map.of("role", "user", "content", "hi"),
            Map.of("role", "assistant", "content", "hi there")),
        j.get("messages"));
    assertTrue(j.containsKey("last_activity"));
    assertNull(j.get("last_activity"));
    assertEquals(gw.readHandle(handle), lastParams().get("id"));
    assertEquals("chat_log", lastMethod());
  }

  @Test
  void startForwardsTheConfiguredTimeoutUpstream() throws Exception {
    ChatGateway gw = gateway(b -> b.conversationTimeout(900));
    String url = mount(gw) + "/chat/";
    AiChatHttpTestSupport.Resp started = postJson(url, body("method", "start"), HEADERS);
    assertEquals(200, started.status());
    assertEquals(900L, started.json().get("timeout"));
    assertEquals("create_conversation", lastMethod());
    assertEquals(900L, lastParams().get("conversation_timeout"));

    String handle = started.header("x-chat-handle");
    AiChatHttpTestSupport.Resp chatted =
        postJson(url, body("message", "hi", "handle", handle), HEADERS);
    assertEquals(200, chatted.status());
    assertEquals("chat", lastMethod());
    assertEquals(900L, lastParams().get("conversation_timeout"));
  }

  @Test
  void pageContextSurvivesTheHttpDispatch() throws Exception {
    Map<String, Object> page =
        Map.of("metadata", Map.of("page", Map.of("title", "Pricing", "n", 42L)));
    ChatGateway gw = gateway();
    String url = mount(gw) + "/chat/";
    AiChatHttpTestSupport.Resp started =
        postJson(url, body("method", "start", "user_meta_data", page), HEADERS);
    assertEquals(200, started.status());
    assertEquals("create_conversation", lastMethod());
    assertEquals(page, lastParams().get("user_meta_data"));

    String handle = started.header("x-chat-handle");
    AiChatHttpTestSupport.Resp chatted =
        postJson(url, body("message", "hi", "handle", handle, "user_meta_data", page), HEADERS);
    assertEquals(200, chatted.status());
    assertEquals("chat", lastMethod());
    // Integers stay integers on the way through (42, never 42.0).
    assertEquals(page, lastParams().get("user_meta_data"));
  }

  @Test
  void aMalformedBagIsACleanRejectionNotA500() throws Exception {
    String url = mount(gateway()) + "/chat/";
    AiChatHttpTestSupport.Resp r =
        postJson(
            url,
            body("method", "start", "user_meta_data", List.of("not", "an", "object")),
            HEADERS);
    assertEquals(400, r.status());
    assertEquals("user_meta_data must be an object", r.json().get("error"));
  }

  // ── Size limits ───────────────────────────────────────────────────

  @Test
  void httpRefusesAnOversizedBodyBeforeParsing() throws Exception {
    String url = mount(gateway()) + "/chat/";
    byte[] big =
        "{".repeat(ChatGateway.MAX_REQUEST_BODY_BYTES + 1).getBytes(StandardCharsets.UTF_8);
    Map<String, String> h = new LinkedHashMap<>(HEADERS);
    h.put("Content-Type", "application/json");
    AiChatHttpTestSupport.Resp r = postRaw(url, big, h);
    assertEquals(413, r.status());
    assertEquals(Map.of("error", "request too large"), r.json());
    assertEquals("https://shop.example.com", r.header("access-control-allow-origin"));
    assertTrue(service.seen.isEmpty());
  }

  @Test
  void httpRefusesAnOversizedChunkedBody() throws Exception {
    String url = mount(gateway()) + "/chat/";
    byte[] big =
        " "
            .repeat((ChatGateway.MAX_REQUEST_BODY_BYTES / 1024 + 2) * 1024)
            .getBytes(StandardCharsets.UTF_8);
    // An unknown-length publisher makes the client send it chunked (no Content-Length).
    AiChatHttpTestSupport.Resp r =
        send(
            "POST",
            url,
            HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(big)),
            HEADERS);
    assertEquals(413, r.status());
    assertEquals(Map.of("error", "request too large"), r.json());
    assertTrue(service.seen.isEmpty());
  }

  @Test
  void httpRefusesAnOversizedMessage() throws Exception {
    String url = mount(gateway()) + "/chat/";
    AiChatHttpTestSupport.Resp r =
        postJson(url, body("message", "x".repeat(ChatGateway.MAX_MESSAGE_BYTES + 1)), HEADERS);
    assertEquals(413, r.status());
    assertEquals(Map.of("error", "message too large"), r.json());
    assertNull(r.header("x-chat-handle"));
    assertTrue(service.seen.isEmpty());
  }

  @Test
  void httpAcceptsABodyUnderTheLimit() throws Exception {
    String url = mount(gateway()) + "/chat/";
    Map<String, Object> bag = Map.of("junk", "y".repeat(ChatGateway.MAX_USER_METADATA_BYTES - 20));
    String msg = "x".repeat(ChatGateway.MAX_MESSAGE_BYTES);
    AiChatHttpTestSupport.Resp r =
        postJson(url, body("message", msg, "user_meta_data", bag), HEADERS);
    assertEquals(200, r.status());
    assertEquals(msg, lastParams().get("message"));
  }

  // ── Streaming passthrough ─────────────────────────────────────────

  /**
   * The {@code slow_service} fixture: pads the response with keepalive whitespace the way the real
   * service does on a slow turn, then writes the JSON-RPC result. It waits on {@code release} after
   * the padding, so a test can prove the padding reached the browser BEFORE the upstream finished —
   * i.e. the gateway relays rather than collects.
   */
  private static HttpServer slowService(CountDownLatch release) throws IOException {
    return AiChatHttpTestSupport.start(
        Map.of(
            "/",
            (HttpExchange ex) -> {
              byte[] in = ex.getRequestBody().readAllBytes();
              Map<?, ?> req = GSON.fromJson(new String(in, StandardCharsets.UTF_8), Map.class);
              ex.getResponseHeaders().set("Content-Type", "application/json");
              ex.sendResponseHeaders(200, 0);
              try (OutputStream os = ex.getResponseBody()) {
                for (int i = 0; i < 3; i++) {
                  os.write(" ".repeat(16).getBytes(StandardCharsets.US_ASCII));
                  os.flush();
                  sleep(10);
                }
                try {
                  release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                Map<String, Object> env = new LinkedHashMap<>();
                env.put("jsonrpc", "2.0");
                env.put("result", Map.of("response", "slow reply"));
                env.put("id", req.get("id"));
                os.write(GSON.toJson(env).getBytes(StandardCharsets.UTF_8));
              }
            }));
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void theKeepalivePaddingIsRelayedNotSwallowed() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    release.countDown();
    HttpServer slow = slowService(release);
    servers.add(slow);
    ChatGateway gw =
        new ChatGateway(
            ChatGatewayOptions.builder(CONFIG_URL)
                .key(KEY)
                .secret("s")
                .client(
                    new AIChatClient(
                        AIChatClientOptions.builder()
                            .project("p")
                            .token("t")
                            .url(base(slow) + "/")
                            .build()))
                .build());
    String url = mount(gw) + "/chat/";
    AiChatHttpTestSupport.Resp r =
        postJson(url, body("message", "hi"), headers("Authorization", "Bearer " + KEY));
    assertEquals(200, r.status());
    assertTrue(r.body().startsWith(" "), "padding was consumed instead of forwarded");
    assertEquals(
        "slow reply",
        ((Map<String, Object>) GSON.fromJson(r.body().strip(), Map.class).get("result"))
            .get("response"));
  }

  @Test
  void theRelayStreamsRatherThanCollects() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    HttpServer slow = slowService(release);
    servers.add(slow);
    AIChatClient client =
        new AIChatClient(
            AIChatClientOptions.builder().project("p").token("t").url(base(slow) + "/").build());

    // Seam 1: rawPost hands back the body unread — the padding is readable while the
    // upstream is still blocked mid-turn.
    try (InputStream in = client.rawPost("chat", Map.of("id", "c", "message", "hi"))) {
      byte[] first = new byte[16];
      int n = in.readNBytes(first, 0, 16);
      assertEquals(16, n);
      assertEquals("", new String(first, StandardCharsets.US_ASCII).strip());
      release.countDown();
      String rest = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(rest.contains("slow reply"));
    }

    // Seam 2: end to end through the gateway route — the browser receives the padding
    // before the upstream turn has finished, which a collecting relay cannot do.
    CountDownLatch release2 = new CountDownLatch(1);
    HttpServer slow2 = slowService(release2);
    servers.add(slow2);
    ChatGateway gw =
        new ChatGateway(
            ChatGatewayOptions.builder(CONFIG_URL)
                .key(KEY)
                .secret("s")
                .client(
                    new AIChatClient(
                        AIChatClientOptions.builder()
                            .project("p")
                            .token("t")
                            .url(base(slow2) + "/")
                            .build()))
                .build());
    String url = mount(gw) + "/chat/";
    HttpRequest req =
        HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer " + KEY)
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body("message", "hi"))))
            .build();
    HttpResponse<InputStream> resp =
        HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofInputStream());
    assertEquals(200, resp.statusCode());
    assertNotNull(resp.headers().firstValue("x-chat-handle").orElse(null));
    try (InputStream in = resp.body()) {
      byte[] first = new byte[16];
      assertEquals(16, in.readNBytes(first, 0, 16));
      assertEquals("", new String(first, StandardCharsets.US_ASCII).strip());
      assertEquals(1, release2.getCount(), "upstream must still be mid-turn");
      release2.countDown();
      assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("slow reply"));
    }
  }

  @Test
  void rawPostSendsTheJsonRpcEnvelope() throws Exception {
    AIChatClient client =
        new AIChatClient(
            AIChatClientOptions.builder().project("p").token("t").url(service.url).build());
    try (InputStream in = client.rawPost("chat", Map.of("id", "c1", "message", "hi"))) {
      assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("hi there"));
    }
    Map<String, Object> sent = service.seen.get(0);
    assertEquals("2.0", sent.get("jsonrpc"));
    assertEquals("chat", sent.get("method"));
    assertEquals(Map.of("id", "c1", "message", "hi"), sent.get("params"));
    assertTrue(((String) sent.get("id")).startsWith("req-"));
  }
}
