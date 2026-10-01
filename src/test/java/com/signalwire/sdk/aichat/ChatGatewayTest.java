/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Port of {@code tests/unit/ai_chat/test_gateway.py} (the non-HTTP half) and {@code
 * test_documented_limits.py::TestHandleRejectionReasons}: what a browser holding a publishable key
 * can and cannot do through {@link ChatGateway#prepare}.
 */
class ChatGatewayTest {

  static final String CONFIG_URL = "https://agent.example.com/swml";
  static final String KEY = "pk_test_key";

  private static AIChatClient client() {
    return new AIChatClient(
        AIChatClientOptions.builder()
            .project("p")
            .token("t")
            .url("https://service.example.invalid/aichat")
            .build());
  }

  /** The {@code gateway} fixture: listed shop origin, fixed secret. */
  static ChatGateway gateway() {
    return new ChatGateway(
        ChatGatewayOptions.builder(CONFIG_URL)
            .key(KEY)
            .allowedOrigins(List.of("https://shop.example.com"))
            .client(client())
            .secret("test-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build());
  }

  /** {@code make_gateway}: a gateway with a caller-tuned builder. */
  static ChatGateway make(java.util.function.Consumer<ChatGatewayOptions.Builder> tune) {
    ChatGatewayOptions.Builder b =
        ChatGatewayOptions.builder(CONFIG_URL).key(KEY).client(client()).secret("s");
    tune.accept(b);
    return new ChatGateway(b.build());
  }

  private static PreparedCall prep(ChatGateway gw, Map<String, Object> body) {
    return gw.prepare(body, "https://shop.example.com", KEY);
  }

  private static Map<String, Object> body(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  private static int status(Runnable r) {
    return assertThrows(GatewayRejection.class, r::run).getStatus();
  }

  // ── construction ──────────────────────────────────────────────────

  @Test
  void configUrlIsRequired() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ChatGateway(ChatGatewayOptions.builder("").client(client()).build()));
  }

  @Test
  void attributesReadBack() {
    ChatGateway gw = gateway();
    assertEquals(CONFIG_URL, gw.getConfigUrl());
    assertEquals(KEY, gw.getKey());
    assertEquals(java.util.Set.of("https://shop.example.com"), gw.getAllowedOrigins());
    assertEquals(ChatGateway.DEFAULT_HANDLE_TTL, gw.getHandleTtl());
    assertNull(gw.getConversationTimeout());
    assertEquals(60, gw.getMaxNewConversations());
    assertEquals(200, gw.getMaxTurns());
    assertEquals(60, gw.getWindowSeconds());
  }

  @Test
  void aKeyIsGeneratedWhenOmitted() {
    ChatGateway gw =
        new ChatGateway(ChatGatewayOptions.builder(CONFIG_URL).client(client()).build());
    String env = System.getenv("SIGNALWIRE_CHAT_GATEWAY_KEY");
    if (env == null || env.isEmpty()) {
      assertTrue(gw.getKey().startsWith("pk_"));
      assertEquals(3 + 32, gw.getKey().length());
    }
  }

  @Test
  void allowedOriginsAreNormalisedWithoutTrailingSlash() {
    ChatGateway gw = make(b -> b.allowedOrigins(List.of("https://a.example.com/")));
    gw.checkOrigin("https://a.example.com");
    gw.checkOrigin("https://a.example.com/");
  }

  // ── Handles ───────────────────────────────────────────────────────

  @Test
  void aHandleRoundTrips() {
    ChatGateway gw = gateway();
    assertTrue(gw.readHandle(gw.mintHandle()).startsWith("chat-"));
  }

  @Test
  void aHandleCarriesTheGivenConversationId() {
    ChatGateway gw = gateway();
    assertEquals("conv-root.5", gw.readHandle(gw.mintHandle("conv-root.5")));
  }

  @Test
  void theBrowserCannotForgeAConversation() {
    ChatGateway gw = gateway();
    String minted = gw.mintHandle();
    String tampered = minted.substring(0, minted.indexOf('.')) + ".AAAA";
    GatewayRejection err = assertThrows(GatewayRejection.class, () -> gw.readHandle(tampered));
    assertEquals(403, err.getStatus());
    assertEquals("invalid handle", err.getReason());
  }

  @Test
  void aHandleFromAnotherGatewayIsRefused() {
    ChatGateway gw = gateway();
    ChatGateway other = make(b -> b.secret("different"));
    assertThrows(GatewayRejection.class, () -> gw.readHandle(other.mintHandle()));
  }

  @Test
  void anExpiredHandleIsRefused() {
    ChatGateway gw = make(b -> b.handleTtl(-1));
    GatewayRejection err =
        assertThrows(GatewayRejection.class, () -> gw.readHandle(gw.mintHandle()));
    assertEquals(403, err.getStatus());
    assertEquals("expired handle", err.getReason());
  }

  @Test
  void garbageIsRefusedWithoutLeakingWhy() {
    ChatGateway gw = gateway();
    for (String bad : List.of("", "not-a-handle", "a.b.c", "!!!.!!!")) {
      assertThrows(GatewayRejection.class, () -> gw.readHandle(bad), bad);
    }
  }

  @Test
  void aMalformedHandleIsA400() {
    // test_documented_limits.py::test_malformed_handle
    GatewayRejection err =
        assertThrows(GatewayRejection.class, () -> gateway().readHandle("not-a-handle"));
    assertEquals(400, err.getStatus());
    assertEquals("malformed handle", err.getReason());
  }

  @Test
  void garbageHandlesGetPythonsBuckets() {
    // Python decodes base64 leniently (non-alphabet characters are discarded), so a
    // handle whose halves decode to SOMETHING fails the signature (403), while one
    // whose halves cannot decode at all is malformed (400). Pinned per input so the
    // status a browser sees for garbage matches the reference exactly.
    // Expected statuses measured against the Python reference's read_handle.
    ChatGateway gw = gateway();
    Map<String, Integer> want = new LinkedHashMap<>();
    want.put("", 400);
    want.put("not-a-handle", 400);
    want.put("a.b.c", 400);
    want.put("!!!.!!!", 403);
    want.put("abcde.AAAA", 400);
    want.put("abcd.AAAA", 403);
    want.put(".", 403);
    want.put("a=b.c", 400);
    want.put("ab.cd", 403);
    want.put("a.b", 400);
    for (Map.Entry<String, Integer> e : want.entrySet()) {
      assertEquals(e.getValue(), status(() -> gw.readHandle(e.getKey())), e.getKey());
    }
  }

  // ── Origin ────────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(strings = {"http://localhost:3000", "http://127.0.0.1:8080", "http://app.localhost"})
  void localhostNeverNeedsListing(String origin) {
    ChatGateway gw = gateway();
    gw.checkOrigin(origin);
    assertThrows(GatewayRejection.class, () -> gw.checkOrigin("https://evil.example.com"));
  }

  @Test
  void hostnameParsingMatchesUrlparse() {
    // Measured against the reference: urlparse lower-cases the host, strips IPv6
    // brackets and userinfo; a bare word has no host; a broken IPv6 literal raises.
    ChatGateway gw = gateway();
    gw.checkOrigin("http://[::1]:3000");
    gw.checkOrigin("http://LOCALHOST:1");
    gw.checkOrigin("http://a@localhost");
    gw.checkOrigin("http://x.LOCALHOST");
    assertEquals(403, status(() -> gw.checkOrigin("localhost")));
    assertEquals(403, status(() -> gw.checkOrigin("null")));
    assertThrows(IllegalArgumentException.class, () -> gw.checkOrigin("http://[::1"));
  }

  @Test
  void aListedOriginIsAllowed() {
    ChatGateway gw = gateway();
    gw.checkOrigin("https://shop.example.com");
    assertThrows(
        GatewayRejection.class, () -> gw.checkOrigin("https://shop.example.com.evil.test"));
  }

  @Test
  void anUnlistedOriginIsRefused() {
    GatewayRejection err =
        assertThrows(
            GatewayRejection.class, () -> gateway().checkOrigin("https://evil.example.com"));
    assertEquals(403, err.getStatus());
    assertEquals("origin not allowed", err.getReason());
  }

  @Test
  void aMissingOriginIsAllowed() {
    ChatGateway gw = gateway();
    gw.checkOrigin(null);
    assertThrows(GatewayRejection.class, () -> gw.checkOrigin("https://evil.example.com"));
  }

  // ── Key ───────────────────────────────────────────────────────────

  @Test
  void theKeyIsRequired() {
    ChatGateway gw = gateway();
    for (String bad : Arrays.asList(null, "", "pk_wrong")) {
      GatewayRejection err = assertThrows(GatewayRejection.class, () -> gw.checkKey(bad));
      assertEquals(401, err.getStatus());
      assertEquals("bad key", err.getReason());
    }
    gw.checkKey(KEY);
  }

  // ── What the browser may ask for ──────────────────────────────────

  @Test
  void configUrlIsOursNotTheirs() {
    PreparedCall c = prep(gateway(), body("message", "hi", "config_url", "https://evil/swml"));
    assertEquals(CONFIG_URL, c.getParams().get("config_url"));
  }

  @Test
  void theBrowserCannotNameTheConversation() {
    ChatGateway gw = gateway();
    PreparedCall c = prep(gw, body("message", "hi", "id", "someone-elses-chat"));
    assertNotEquals("someone-elses-chat", c.getParams().get("id"));
    assertNotNull(c.getMintedHandle());
    assertEquals(gw.readHandle(c.getMintedHandle()), c.getParams().get("id"));
  }

  @Test
  void onlyTheBrowserMethodsPass() {
    ChatGateway gw = gateway();
    for (String method : List.of("chat_log", "summarize", "delete", "create_conversation")) {
      assertEquals(400, status(() -> prep(gw, body("method", method, "message", "hi"))));
    }
  }

  @Test
  void chatLogIsNotReachable() {
    assertThrows(GatewayRejection.class, () -> prep(gateway(), body("method", "chat_log")));
  }

  @Test
  void theFirstChatMintsAndLaterOnesReuse() {
    ChatGateway gw = gateway();
    PreparedCall first = prep(gw, body("message", "one"));
    assertNotNull(first.getMintedHandle());
    PreparedCall second = prep(gw, body("message", "two", "handle", first.getMintedHandle()));
    assertNull(second.getMintedHandle());
    assertEquals(first.getParams().get("id"), second.getParams().get("id"));
  }

  @Test
  void theChatCallCarriesExactlyTheGatewayOwnedParams() {
    ChatGateway gw = gateway();
    PreparedCall c = prep(gw, body("message", "hi"));
    assertEquals("chat", c.getMethod());
    Map<String, Object> want = new HashMap<>();
    want.put("id", gw.readHandle(c.getMintedHandle()));
    want.put("message", "hi");
    want.put("config_url", CONFIG_URL);
    assertEquals(want, c.getParams());
  }

  @Test
  void endNeedsAHandle() {
    GatewayRejection err =
        assertThrows(GatewayRejection.class, () -> prep(gateway(), body("method", "end")));
    assertEquals(400, err.getStatus());
    assertEquals("end requires a handle", err.getReason());
  }

  @Test
  void endMapsToTheServiceMethod() {
    ChatGateway gw = gateway();
    String minted = gw.mintHandle();
    PreparedCall c = prep(gw, body("method", "end", "handle", minted));
    assertEquals("end_conversation", c.getMethod());
    assertEquals(Map.of("id", gw.readHandle(minted)), c.getParams());
    assertNull(c.getMintedHandle());
  }

  @Test
  void anEmptyMessageIsRefused() {
    ChatGateway gw = gateway();
    for (Object bad : Arrays.asList(null, "", "   ", 5L)) {
      GatewayRejection err =
          assertThrows(GatewayRejection.class, () -> prep(gw, body("message", bad)));
      assertEquals(400, err.getStatus());
      assertEquals("message is required", err.getReason());
    }
  }

  @Test
  void anUnhashableMethodIsNotARejection() {
    // Python's `method not in ALLOWED_METHODS` raises TypeError for a list, which the
    // router answers as a generic 400 "bad request" rather than a GatewayRejection.
    assertThrows(
        IllegalArgumentException.class,
        () -> prep(gateway(), body("method", List.of("chat"), "message", "hi")));
  }

  // ── The caps ──────────────────────────────────────────────────────

  @Test
  void mintingIsCapped() {
    ChatGateway gw = make(b -> b.maxNewConversations(3));
    for (int i = 0; i < 3; i++) {
      gw.prepare(body("message", "hi"), null, KEY);
    }
    GatewayRejection err =
        assertThrows(GatewayRejection.class, () -> gw.prepare(body("message", "hi"), null, KEY));
    assertEquals(429, err.getStatus());
    assertEquals("too many new conversations", err.getReason());
  }

  @Test
  void theMintWindowSlides() {
    ChatGateway gw = make(b -> b.maxNewConversations(1).windowSeconds(0));
    gw.prepare(body("message", "hi"), null, KEY);
    gw.prepare(body("message", "hi"), null, KEY);
  }

  @Test
  void turnsAreCappedPerConversation() {
    ChatGateway gw = make(b -> b.maxTurns(2));
    String handle = gw.mintHandle();
    for (int i = 0; i < 2; i++) {
      gw.prepare(body("message", "hi", "handle", handle), null, KEY);
    }
    GatewayRejection err =
        assertThrows(
            GatewayRejection.class,
            () -> gw.prepare(body("message", "hi", "handle", handle), null, KEY));
    assertEquals(429, err.getStatus());
    assertEquals("conversation turn limit reached", err.getReason());
  }

  @Test
  void oneConversationHittingItsCapDoesNotStopAnother() {
    ChatGateway gw = make(b -> b.maxTurns(1));
    String a = gw.mintHandle();
    String b2 = gw.mintHandle();
    gw.prepare(body("message", "hi", "handle", a), null, KEY);
    gw.prepare(body("message", "hi", "handle", b2), null, KEY);
    assertThrows(
        GatewayRejection.class, () -> gw.prepare(body("message", "again", "handle", a), null, KEY));
  }

  // ── start / log ───────────────────────────────────────────────────

  @Test
  void startMintsAndOpensWithNoMessage() {
    ChatGateway gw = gateway();
    PreparedCall c = prep(gw, body("method", "start"));
    assertEquals("create_conversation", c.getMethod());
    assertNotNull(c.getMintedHandle());
    assertEquals(
        Map.of("id", gw.readHandle(c.getMintedHandle()), "config_url", CONFIG_URL), c.getParams());
  }

  @Test
  void startAndChatCarryTheConfiguredTimeout() {
    ChatGateway gw = make(b -> b.conversationTimeout(900));
    assertEquals(
        900,
        gw.prepare(body("method", "start"), null, KEY).getParams().get("conversation_timeout"));
    assertEquals(
        900, gw.prepare(body("message", "hi"), null, KEY).getParams().get("conversation_timeout"));
    assertEquals(900, gw.getEffectiveTimeout());
  }

  @Test
  void logIsScopedToTheHandleNotTheBody() {
    ChatGateway gw = gateway();
    String handle = gw.mintHandle();
    PreparedCall c = prep(gw, body("method", "log", "handle", handle, "id", "someone-elses-chat"));
    assertEquals("chat_log", c.getMethod());
    assertEquals(Map.of("id", gw.readHandle(handle)), c.getParams());
  }

  @Test
  void logNeedsAHandle() {
    GatewayRejection err =
        assertThrows(GatewayRejection.class, () -> prep(gateway(), body("method", "log")));
    assertEquals("log requires a handle", err.getReason());
  }

  @Test
  void theTranscriptHidesEverythingButTheDialogue() {
    List<Map<String, Object>> raw = new ArrayList<>();
    raw.add(body("role", "system", "content", "You are Sigmond. Secret instructions."));
    raw.add(body("role", "user", "content", "hi", "timestamp", 123L));
    raw.add(body("role", "assistant", "content", null, "tool_calls", List.of(Map.of("id", "c"))));
    raw.add(body("role", "tool", "content", "{\"internal\": \"result\"}"));
    raw.add(body("role", "assistant", "content", "Hello!", "timestamp", 124L));
    raw.add(body("role", "assistant", "content", "   "));
    List<Map<String, Object>> out = ChatGateway.visibleMessages(raw);
    assertEquals(
        List.of(
            Map.of("role", "user", "content", "hi", "timestamp", 123 / 1_000_000.0),
            Map.of("role", "assistant", "content", "Hello!", "timestamp", 124 / 1_000_000.0)),
        out);
    String blob = AiChatHttpTestSupport.GSON.toJson(out);
    assertFalse(blob.contains("Secret instructions"));
    assertFalse(blob.contains("tool_calls") || blob.contains("internal"));
  }

  @Test
  void theTranscriptReportsSecondsNotMicroseconds() {
    long tsUs = 1_786_258_737_756_596L;
    List<Map<String, Object>> msgs =
        List.of(body("role", "user", "content", "hi", "timestamp", tsUs));
    assertEquals(
        1_786_258_737.756596,
        (Double) ChatGateway.visibleMessages(msgs).get(0).get("timestamp"),
        1e-6);
    assertEquals(1_786_258_737.756596, ChatGateway.lastActivity(msgs), 1e-6);
  }

  @Test
  void aTimestampDecodedAsAWholeDoubleStillCounts() {
    // AIChatClient.log() decodes JSON numbers as Double; an integral Double is the
    // service's integer timestamp.
    List<Map<String, Object>> msgs =
        List.of(body("role", "user", "content", "hi", "timestamp", 3_000_000.0));
    assertEquals(3.0, ChatGateway.lastActivity(msgs));
    assertEquals(3.0, ChatGateway.visibleMessages(msgs).get(0).get("timestamp"));
  }

  @Test
  void lastActivityTakesTheNewestMessageOfAnyRole() {
    List<Map<String, Object>> msgs =
        List.of(
            body("role", "user", "content", "first", "timestamp", 1_000_000L),
            body("role", "assistant", "content", "second", "timestamp", 3_000_000L),
            body("role", "tool", "content", "internal", "timestamp", 5_000_000L));
    assertEquals(5.0, ChatGateway.lastActivity(msgs));
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void lastActivityIsNullWhenNothingIsDated() {
    assertNull(ChatGateway.lastActivity(List.of(body("role", "user", "content", "hi"))));
    assertNull(ChatGateway.lastActivity(List.of()));
    assertNull(ChatGateway.lastActivity(null));
    assertNull(
        ChatGateway.lastActivity(List.of(body("role", "user", "timestamp", "not a number"))));
    List junk = List.of("not a dict");
    assertNull(ChatGateway.lastActivity(junk));
  }

  @Test
  void effectiveTimeoutIsAlwaysANumber() {
    assertEquals(3600, gateway().getEffectiveTimeout());
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void theTranscriptSurvivesJunk() {
    List junk = Arrays.asList("not a dict", body("role", "user"));
    assertEquals(List.of(), ChatGateway.visibleMessages(List.of()));
    assertEquals(List.of(), ChatGateway.visibleMessages(null));
    assertEquals(List.of(), ChatGateway.visibleMessages(junk));
  }

  // ── Page context the browser volunteers ───────────────────────────

  static final Map<String, Object> PAGE =
      Map.of(
          "capabilities", Map.of("widget", "signalwire-address", "medium", "chat"),
          "metadata",
              Map.of(
                  "page", Map.of("url", "https://shop.example.com/pricing", "title", "Pricing")));

  @Test
  @SuppressWarnings("unchecked")
  void pageContextReachesTheCreateParams() {
    PreparedCall c = prep(gateway(), body("method", "start", "user_meta_data", PAGE));
    assertEquals("create_conversation", c.getMethod());
    Map<String, Object> meta = (Map<String, Object>) c.getParams().get("user_meta_data");
    assertEquals(
        "Pricing",
        ((Map<String, Object>) ((Map<String, Object>) meta.get("metadata")).get("page"))
            .get("title"));
  }

  @Test
  void pageContextRidesTheChatPathToo() {
    ChatGateway gw = gateway();
    PreparedCall first = prep(gw, body("method", "start", "user_meta_data", PAGE));
    assertEquals(PAGE, first.getParams().get("user_meta_data"));
    Map<String, Object> moved =
        Map.of("metadata", Map.of("page", Map.of("url", "https://shop.example.com/docs")));
    PreparedCall later =
        prep(
            gw,
            body(
                "message", "and now?", "handle", first.getMintedHandle(), "user_meta_data", moved));
    assertEquals(moved, later.getParams().get("user_meta_data"));
  }

  @Test
  void pageContextIsOptional() {
    ChatGateway gw = gateway();
    for (Map<String, Object> b :
        List.of(
            body("method", "start"),
            body("method", "start", "user_meta_data", null),
            body("method", "start", "user_meta_data", Map.of()))) {
      assertFalse(prep(gw, b).getParams().containsKey("user_meta_data"));
    }
  }

  @Test
  void pageContextMustBeAnObject() {
    ChatGateway gw = gateway();
    for (Object bad : List.of("a string", 42L, List.of("a", "list"), true)) {
      GatewayRejection err =
          assertThrows(
              GatewayRejection.class,
              () -> prep(gw, body("method", "start", "user_meta_data", bad)));
      assertEquals(400, err.getStatus());
      assertEquals("user_meta_data must be an object", err.getReason());
    }
  }

  @Test
  void pageContextIsBounded() {
    ChatGateway gw = gateway();
    Map<String, Object> fat = Map.of("junk", "x".repeat(ChatGateway.MAX_USER_METADATA_BYTES + 1));
    GatewayRejection err =
        assertThrows(
            GatewayRejection.class, () -> prep(gw, body("method", "start", "user_meta_data", fat)));
    assertEquals(413, err.getStatus());
    assertEquals("user_meta_data too large", err.getReason());
    assertEquals(
        PAGE,
        prep(gw, body("method", "start", "user_meta_data", PAGE))
            .getParams()
            .get("user_meta_data"));
  }

  @Test
  void pageContextSizeCountsPythonsAsciiEscapedJson() {
    // json.dumps(ensure_ascii=True) writes each non-ASCII char as a 6-byte \\uXXXX
    // escape, so 1366 'é' plus the `{"j":""}` frame is 8204 bytes > 8192 even though
    // it is far fewer characters.
    ChatGateway gw = gateway();
    Map<String, Object> wide = Map.of("j", "é".repeat(1366));
    GatewayRejection err =
        assertThrows(
            GatewayRejection.class, () -> gw.readUserMetadata(Map.of("user_meta_data", wide)));
    assertEquals(413, err.getStatus());
    Map<String, Object> fits = Map.of("j", "é".repeat(1364));
    assertEquals(fits, gw.readUserMetadata(Map.of("user_meta_data", fits)));
  }

  @Test
  void pageContextIsRejectedBeforeAConversationIsCharged() {
    ChatGateway gw = make(b -> b.maxNewConversations(1));
    assertThrows(
        GatewayRejection.class,
        () -> gw.prepare(body("method", "start", "user_meta_data", "nope"), null, KEY));
    assertNotNull(gw.prepare(body("method", "start"), null, KEY).getMintedHandle());
  }

  @Test
  void pageContextCannotDisplaceWhatTheGatewayOwns() {
    ChatGateway gw = gateway();
    Map<String, Object> hostile =
        Map.of("id", "someone-elses-chat", "config_url", "https://evil/swml");
    PreparedCall c = prep(gw, body("message", "hi", "user_meta_data", hostile));
    assertNotNull(c.getMintedHandle());
    assertEquals(gw.readHandle(c.getMintedHandle()), c.getParams().get("id"));
    assertEquals(CONFIG_URL, c.getParams().get("config_url"));
    assertEquals(hostile, c.getParams().get("user_meta_data"));
  }

  // ── Size limits ───────────────────────────────────────────────────

  @Test
  void aMessageOverTheLimitIsRefused() {
    GatewayRejection err =
        assertThrows(
            GatewayRejection.class,
            () -> prep(gateway(), body("message", "x".repeat(ChatGateway.MAX_MESSAGE_BYTES + 1))));
    assertEquals(413, err.getStatus());
    assertEquals("message too large", err.getReason());
  }

  @Test
  void aMessageAtTheLimitPasses() {
    String atLimit = "x".repeat(ChatGateway.MAX_MESSAGE_BYTES);
    assertEquals(atLimit, prep(gateway(), body("message", atLimit)).getParams().get("message"));
  }

  @Test
  void theMessageLimitCountsUtf8BytesNotCharacters() {
    String wide = "é".repeat(ChatGateway.MAX_MESSAGE_BYTES / 2 + 1);
    assertTrue(wide.length() < ChatGateway.MAX_MESSAGE_BYTES);
    assertEquals(413, status(() -> prep(gateway(), body("message", wide))));
  }

  @Test
  void aLoneSurrogateCountsThreeBytes() {
    // Python encodes with "surrogatepass": a lone surrogate is 3 bytes, never 1.
    String lone = "\ud800".repeat(ChatGateway.MAX_MESSAGE_BYTES / 3 + 1);
    assertEquals(413, status(() -> prep(gateway(), body("message", lone))));
  }

  @Test
  void anOversizedMessageMintsNothing() {
    ChatGateway gw = make(b -> b.maxNewConversations(1));
    assertThrows(
        GatewayRejection.class,
        () ->
            gw.prepare(body("message", "x".repeat(ChatGateway.MAX_MESSAGE_BYTES + 1)), null, KEY));
    assertNotNull(gw.prepare(body("message", "hi"), null, KEY).getMintedHandle());
  }

  @Test
  void anOversizedMessageChargesNoTurn() {
    ChatGateway gw = make(b -> b.maxTurns(1));
    String handle = gw.mintHandle();
    assertEquals(
        413,
        status(
            () ->
                gw.prepare(
                    body(
                        "message", "x".repeat(ChatGateway.MAX_MESSAGE_BYTES + 1), "handle", handle),
                    null,
                    KEY)));
    assertEquals(
        "hi",
        gw.prepare(body("message", "hi", "handle", handle), null, KEY).getParams().get("message"));
  }

  @Test
  void rejectionCarriesStatusAndReason() {
    GatewayRejection r = new GatewayRejection(429, "too many new conversations");
    assertEquals(429, r.getStatus());
    assertEquals("too many new conversations", r.getReason());
    assertEquals("429: too many new conversations", r.getMessage());
  }

  @Test
  void closeLeavesACallerOwnedClientAlone() {
    gateway().close();
  }
}
