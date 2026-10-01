/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

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

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Port of {@code tests/unit/ai_chat/test_handoff.py} and {@code
 * test_documented_limits.py::TestTypingLifetime}: moving one conversation between voice and text.
 * The HTTP cases drive {@link HandoffRouter#router()} mounted beside the gateway under {@code
 * /chat} on a real JDK {@link HttpServer}.
 */
class HandoffRouterTest {

  static final String SECRET = "s".repeat(32);

  static ChatGateway gateway() {
    return new ChatGateway(
        ChatGatewayOptions.builder("https://agent.example.com/swml")
            .key("pk_test")
            .secret(SECRET)
            .client(
                new AIChatClient(
                    AIChatClientOptions.builder()
                        .project("p")
                        .token("t")
                        .url("https://service.example.invalid/aichat")
                        .build()))
            .build());
  }

  /** A send_message that records and always succeeds. */
  static BiFunction<String, String, Boolean> recordingSender(List<List<Object>> events) {
    return (callId, text) -> {
      events.add(List.of("say", text));
      return true;
    };
  }

  private ChatGateway gateway;
  private final List<List<Object>> events = new CopyOnWriteArrayList<>();
  private HandoffRouter handoff;
  private HttpServer server;
  private String base;

  @BeforeEach
  void setUp() throws IOException {
    gateway = gateway();
    handoff =
        new HandoffRouter(
            HandoffRouterOptions.builder(gateway)
                .captureLeg(
                    (conversationId, medium) -> {
                      events.add(List.of("capture", conversationId, medium));
                      return true;
                    })
                .endCall(callId -> events.add(List.of("end_call", callId)))
                .sendMessage(
                    (callId, text) -> {
                      events.add(List.of("say", callId, text));
                      return true;
                    })
                .build());
    // Mounted the way the widget expects: the three routes are siblings of the
    // gateway's endpoint under one prefix.
    Map<String, com.sun.net.httpserver.HttpHandler> ctx = new LinkedHashMap<>();
    ctx.put("/chat", gateway.router());
    ctx.put("/chat/handoff", handoff.router());
    ctx.put("/chat/escalate", handoff.router());
    ctx.put("/chat/say", handoff.router());
    server = AiChatHttpTestSupport.start(ctx);
    base = base(server);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private AiChatHttpTestSupport.Resp post(String path, Object body) throws Exception {
    return postJson(base + path, body, Map.of());
  }

  private static Map<String, Object> body(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  @Nested
  class HandoffRedemption {
    @Test
    void returnsAHandleTheGatewayCanRead() throws Exception {
      handoff.register("n1", "conv-root", "call-9");
      AiChatHttpTestSupport.Resp r = post("/chat/handoff", body("nonce", "n1"));
      assertEquals(200, r.status());
      assertNotNull(gateway.readHandle((String) r.json().get("handle")));
    }

    @Test
    void callEndsBeforeTheLegIsCaptured() throws Exception {
      handoff.register("n1", "conv-root", "call-9");
      post("/chat/handoff", body("nonce", "n1"));
      assertEquals(
          List.of(List.of("end_call", "call-9"), List.of("capture", "conv-root", "voice")), events);
    }

    @Test
    void newLegGetsAFreshDottedId() throws Exception {
      handoff.register("n1", "conv-root", "call-9");
      AiChatHttpTestSupport.Resp r = post("/chat/handoff", body("nonce", "n1"));
      assertEquals("conv-root.1", gateway.readHandle((String) r.json().get("handle")));
    }

    @Test
    void legIdsIncrement() {
      assertEquals("root.3", handoff.getNextConversationId().apply("root.2"));
      assertEquals("root.1", handoff.getNextConversationId().apply("root"));
      assertEquals("a.b.1", handoff.getNextConversationId().apply("a.b"));
      assertEquals("x.٣.1", handoff.getNextConversationId().apply("x.٣"));
    }

    @Test
    void aNonceIsSingleUse() throws Exception {
      handoff.register("n1", "conv-root", "call-9");
      assertEquals(200, post("/chat/handoff", body("nonce", "n1")).status());
      assertEquals(404, post("/chat/handoff", body("nonce", "n1")).status());
    }

    @Test
    void unknownAndSpentNoncesAreIndistinguishable() throws Exception {
      handoff.register("n1", "conv-root", "call-9");
      post("/chat/handoff", body("nonce", "n1"));
      AiChatHttpTestSupport.Resp spent = post("/chat/handoff", body("nonce", "n1"));
      AiChatHttpTestSupport.Resp unknown = post("/chat/handoff", body("nonce", "never-existed"));
      assertEquals(404, spent.status());
      assertEquals(404, unknown.status());
      assertEquals(spent.json(), unknown.json());
      assertEquals(Map.of("error", "not found"), spent.json());
    }

    @Test
    void expiredNoncesAreNotRedeemable() {
      HandoffRouter expired =
          new HandoffRouter(HandoffRouterOptions.builder(gateway).nonceTtl(-1).build());
      expired.register("n1", "conv-root", "call-9");
      assertNull(expired.lookup("n1"));
    }

    @Test
    void missingNonceIsRejected() throws Exception {
      assertEquals(404, post("/chat/handoff", body()).status());
    }
  }

  @Nested
  class Registration {
    @Test
    void aRepeatRegistrationKeepsTheTypingCount() {
      HandoffRouter router =
          new HandoffRouter(
              HandoffRouterOptions.builder(gateway)
                  .sendMessage(recordingSender(events))
                  .maxMessagesPerCall(1)
                  .build());
      router.register("n", "c", "call-1");
      assertTrue(router.say("n", "one"));
      assertFalse(router.say("n", "two"));
      router.register("n", "c", "call-1");
      assertFalse(router.say("n", "three"));
      assertEquals(List.of(List.of("say", "one")), events);
    }

    @Test
    void aRepeatRegistrationKeepsTheRegistrationTime() {
      handoff.register("n", "c", "call-1");
      double first = handoff.nonces().get("n").getIssuedAt();
      handoff.register("n", "c", "call-1");
      assertEquals(first, handoff.nonces().get("n").getIssuedAt());
    }

    @Test
    void aLiveNonceCannotBeMovedToAnotherCall() throws Exception {
      handoff.register("n", "conv-a", "call-a");
      handoff.register("n", "conv-b", "call-b");
      assertEquals(200, post("/chat/say", body("nonce", "n", "text", "hi")).status());
      assertEquals(List.of(List.of("say", "call-a", "hi")), events);
    }

    @Test
    void aRedeemedNonceCannotBeRegisteredAndRedeemedAgain() throws Exception {
      handoff.register("n", "conv-root", "call-9");
      assertEquals(200, post("/chat/handoff", body("nonce", "n")).status());
      handoff.register("n", "conv-root", "call-10");
      AiChatHttpTestSupport.Resp again = post("/chat/handoff", body("nonce", "n"));
      assertEquals(404, again.status());
      assertEquals(Map.of("error", "not found"), again.json());
    }

    @Test
    void aRedeemedNonceCannotType() throws Exception {
      handoff.register("n", "conv-root", "call-9");
      post("/chat/handoff", body("nonce", "n"));
      events.clear();
      assertEquals(404, post("/chat/say", body("nonce", "n", "text", "late")).status());
      assertEquals(List.of(), events);
    }

    @Test
    void redemptionIsKeptUntilTheTtlPasses() {
      handoff.register("n", "conv-root", "call-9");
      assertNotNull(handoff.redeem("n"));
      NonceEntry entry = handoff.nonces().get("n");
      assertTrue(entry.isRedeemed());
      entry.setIssuedAt(entry.getIssuedAt() - (handoff.getNonceTtl() + 1));
      handoff.register("n", "conv-new", "call-11");
      assertFalse(handoff.nonces().get("n").isRedeemed());
      assertEquals("conv-new", handoff.nonces().get("n").getConversationId());
    }

    @Test
    void registerWithoutACallIdTypesNothing() {
      HandoffRouter router =
          new HandoffRouter(
              HandoffRouterOptions.builder(gateway).sendMessage(recordingSender(events)).build());
      router.register("n", "c");
      assertNull(router.nonces().get("n").getCallId());
      assertFalse(router.say("n", "hello"));
      assertNotNull(router.redeem("n"));
    }

    @Test
    void anEmptyNonceIsIgnored() {
      handoff.register("", "c", "call-1");
      handoff.register(null, "c", "call-1");
      assertTrue(handoff.nonces().isEmpty());
    }

    @Test
    void aSharedRegistryStoresTheRedemption() {
      List<List<Object>> assigned = new ArrayList<>();
      Map<String, NonceEntry> registry =
          new HashMap<>() {
            @Override
            public NonceEntry put(String key, NonceEntry value) {
              assigned.add(List.of(key, value.isRedeemed()));
              return super.put(key, value);
            }
          };
      HandoffRouter router =
          new HandoffRouter(HandoffRouterOptions.builder(gateway).registry(registry).build());
      router.register("n", "c", "call-1");
      assertNotNull(router.redeem("n"));
      assertEquals(List.of(List.of("n", false), List.of("n", true)), assigned);
    }
  }

  @Nested
  class Concurrency {
    @Test
    void aRegistrationRacingARedemptionCantReviveTheNonce() throws Exception {
      List<String> handles = new CopyOnWriteArrayList<>();
      List<Thread> racers = new ArrayList<>();
      HandoffRouter[] holder = new HandoffRouter[1];
      boolean[] raced = {false};
      Map<String, NonceEntry> racing =
          new HashMap<>() {
            @Override
            public NonceEntry get(Object key) {
              NonceEntry found = super.get(key);
              if (!raced[0]) {
                raced[0] = true;
                Thread other =
                    new Thread(
                        () -> {
                          holder[0].register("n", "late", "call-2");
                          String h = holder[0].redeem("n");
                          handles.add(h == null ? "<none>" : h);
                        });
                other.start();
                try {
                  other.join(500); // blocks on the lock if it's atomic
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                racers.add(other);
              }
              return found;
            }
          };
      holder[0] = new HandoffRouter(HandoffRouterOptions.builder(gateway).registry(racing).build());
      holder[0].register("n", "first", "call-1");
      racers.get(0).join(5000);
      String mine = holder[0].redeem("n");
      handles.add(mine == null ? "<none>" : mine);
      assertEquals(1, handles.stream().filter(h -> !"<none>".equals(h)).count());
    }

    @Test
    void overlappingSaysCantPassTheCap() throws Exception {
      List<String> delivered = new CopyOnWriteArrayList<>();
      HandoffRouter router =
          new HandoffRouter(
              HandoffRouterOptions.builder(gateway)
                  .sendMessage(
                      (callId, text) -> {
                        try {
                          Thread.sleep(10); // delivery takes a moment
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                        delivered.add(text);
                        return true;
                      })
                  .maxMessagesPerCall(1)
                  .build());
      router.register("n", "c", "call-1");
      ExecutorService pool = Executors.newFixedThreadPool(3);
      try {
        List<Future<Boolean>> fs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
          String text = "m" + i;
          fs.add(pool.submit(() -> router.say("n", text)));
        }
        int ok = 0;
        for (Future<Boolean> f : fs) {
          ok += f.get() ? 1 : 0;
        }
        assertEquals(1, ok);
        assertEquals(1, delivered.size());
      } finally {
        pool.shutdownNow();
      }
    }

    @Test
    void aFailedDeliveryGivesItsSlotBack() {
      List<String> attempts = new ArrayList<>();
      HandoffRouter router =
          new HandoffRouter(
              HandoffRouterOptions.builder(gateway)
                  .sendMessage(
                      (callId, text) -> {
                        attempts.add(text);
                        if (attempts.size() == 1) {
                          throw new IllegalStateException("platform unavailable");
                        }
                        return true;
                      })
                  .maxMessagesPerCall(1)
                  .build());
      router.register("n", "c", "call-1");
      assertFalse(router.say("n", "first"));
      assertTrue(router.say("n", "again"));
      assertFalse(router.say("n", "over the cap"));
      assertEquals(List.of("first", "again"), attempts);
    }
  }

  /** A shared registry that returns (and stores) a COPY of an entry, like a cache. */
  static final class Copying extends HashMap<String, NonceEntry> {
    private static NonceEntry copy(NonceEntry e) {
      return e == null
          ? null
          : new NonceEntry(
              e.getConversationId(),
              e.getCallId(),
              e.getIssuedAt(),
              e.getMessages(),
              e.isRedeemed());
    }

    @Override
    public NonceEntry get(Object key) {
      return copy(super.get(key));
    }

    @Override
    public NonceEntry put(String key, NonceEntry value) {
      return super.put(key, copy(value));
    }
  }

  @Nested
  class CopyingRegistry {
    @Test
    void aFailedDeliveryGivesItsSlotBack() {
      List<String> attempts = new ArrayList<>();
      HandoffRouter router =
          new HandoffRouter(
              HandoffRouterOptions.builder(gateway)
                  .sendMessage(
                      (callId, text) -> {
                        attempts.add(text);
                        if (attempts.size() == 1) {
                          throw new IllegalStateException("platform unavailable");
                        }
                        return true;
                      })
                  .maxMessagesPerCall(1)
                  .registry(new Copying())
                  .build());
      router.register("n", "c", "call-1");
      assertFalse(router.say("n", "first"));
      assertTrue(router.say("n", "again"));
      assertFalse(router.say("n", "over the cap"));
      assertEquals(List.of("first", "again"), attempts);
    }

    @Test
    void aRedeemedNonceStaysRedeemed() {
      HandoffRouter router =
          new HandoffRouter(HandoffRouterOptions.builder(gateway).registry(new Copying()).build());
      router.register("n", "c", "call-1");
      assertNotNull(router.redeem("n"));
      router.register("n", "c", "call-1");
      assertNull(router.redeem("n"));
    }
  }

  @Nested
  class Escalate {
    @Test
    void capturesTheChatLegBeforeReturning() throws Exception {
      String handle = gateway.mintHandle("conv-root.5");
      AiChatHttpTestSupport.Resp r = post("/chat/escalate", body("handle", handle));
      assertEquals(200, r.status());
      assertEquals(Map.of("ok", true), r.json());
      assertEquals(List.of(List.of("capture", "conv-root.5", "chat")), events);
    }

    @Test
    void aForgedHandleIsRefused() throws Exception {
      assertEquals(404, post("/chat/escalate", body("handle", "forged")).status());
    }

    @Test
    void aMissingHandleIsABadRequest() throws Exception {
      AiChatHttpTestSupport.Resp r = post("/chat/escalate", body());
      assertEquals(400, r.status());
      assertEquals(Map.of("error", "bad request"), r.json());
      assertEquals(400, post("/chat/escalate", List.of(1)).status());
    }
  }

  @Nested
  class Say {
    @Test
    void deliversTrimmedTextToTheCallTheNonceNames() throws Exception {
      handoff.register("n2", "conv-root", "call-9");
      AiChatHttpTestSupport.Resp r = post("/chat/say", body("nonce", "n2", "text", "  hello  "));
      assertEquals(200, r.status());
      assertEquals(Map.of("ok", true), r.json());
      assertEquals(List.of(List.of("say", "call-9", "hello")), events);
    }

    @Test
    void isRepeatable() throws Exception {
      handoff.register("n2", "conv-root", "call-9");
      for (int i = 0; i < 3; i++) {
        assertEquals(200, post("/chat/say", body("nonce", "n2", "text", "x")).status());
      }
    }

    @Test
    void isCappedPerCall() {
      HandoffRouter router =
          new HandoffRouter(
              HandoffRouterOptions.builder(gateway)
                  .sendMessage(recordingSender(events))
                  .maxMessagesPerCall(2)
                  .build());
      router.register("n", "c", "call-1");
      assertTrue(router.say("n", "one"));
      assertTrue(router.say("n", "two"));
      assertFalse(router.say("n", "three"));
    }

    @Test
    void emptyTextIsRefused() throws Exception {
      handoff.register("n2", "conv-root", "call-9");
      assertEquals(404, post("/chat/say", body("nonce", "n2", "text", "   ")).status());
      assertEquals(404, post("/chat/say", body("nonce", "n2")).status());
      assertEquals(404, post("/chat/say", body("nonce", "n2", "text", null)).status());
    }

    @Test
    void anUnknownNonceCannotInject() throws Exception {
      assertEquals(404, post("/chat/say", body("nonce", "guessed", "text", "hello")).status());
    }

    @Test
    void disabledWhenNoSenderIsConfigured() {
      HandoffRouter router = new HandoffRouter(HandoffRouterOptions.builder(gateway).build());
      router.register("n", "c", "call-1");
      assertFalse(router.say("n", "hello"));
    }

    @Test
    void theSendersReturnValueIsNotTheVerdict() {
      // The reference awaits send_message and ignores what it returns: a delivered
      // call is a success whatever the callback says.
      HandoffRouter router =
          new HandoffRouter(
              HandoffRouterOptions.builder(gateway).sendMessage((c, t) -> false).build());
      router.register("n", "c", "call-1");
      assertTrue(router.say("n", "hello"));
    }
  }

  @Nested
  class SizeLimits {
    @Test
    void sayRefusesTextOverTheMessageLimit() throws Exception {
      handoff.register("n", "conv-root", "call-9");
      AiChatHttpTestSupport.Resp r =
          post(
              "/chat/say",
              body("nonce", "n", "text", "x".repeat(ChatGateway.MAX_MESSAGE_BYTES + 1)));
      assertEquals(413, r.status());
      assertEquals(Map.of("error", "message too large"), r.json());
      assertEquals(List.of(), events);
    }

    @Test
    void theSizeAnswerDoesNotDependOnTheNonce() throws Exception {
      AiChatHttpTestSupport.Resp r =
          post(
              "/chat/say",
              body(
                  "nonce", "never-existed", "text", "x".repeat(ChatGateway.MAX_MESSAGE_BYTES + 1)));
      assertEquals(413, r.status());
      assertEquals(Map.of("error", "message too large"), r.json());
    }

    @Test
    void sayAcceptsTextAtTheLimit() throws Exception {
      handoff.register("n", "conv-root", "call-9");
      String text = "x".repeat(ChatGateway.MAX_MESSAGE_BYTES);
      assertEquals(200, post("/chat/say", body("nonce", "n", "text", text)).status());
      assertEquals(List.of(List.of("say", "call-9", text)), events);
    }

    @Test
    void sayCalledDirectlyRefusesOversizedText() {
      HandoffRouter router =
          new HandoffRouter(
              HandoffRouterOptions.builder(gateway).sendMessage(recordingSender(events)).build());
      router.register("n", "c", "call-1");
      assertFalse(router.say("n", "x".repeat(ChatGateway.MAX_MESSAGE_BYTES + 1)));
      assertEquals(List.of(), events);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/chat/handoff", "/chat/escalate", "/chat/say"})
    void anOversizedBodyIsRefused(String path) throws Exception {
      AiChatHttpTestSupport.Resp r =
          postRaw(
              base + path,
              " ".repeat(ChatGateway.MAX_REQUEST_BODY_BYTES + 1).getBytes(StandardCharsets.UTF_8),
              headers("Content-Type", "application/json"));
      assertEquals(413, r.status());
      assertEquals(Map.of("error", "request too large"), r.json());
    }

    @Test
    void anOversizedHandoffLeavesTheNonceRedeemable() throws Exception {
      handoff.register("n", "conv-root", "call-9");
      byte[] padded =
          ("{\"nonce\": \"n\", \"pad\": \""
                  + "x".repeat(ChatGateway.MAX_REQUEST_BODY_BYTES)
                  + "\"}")
              .getBytes(StandardCharsets.UTF_8);
      AiChatHttpTestSupport.Resp refused =
          postRaw(base + "/chat/handoff", padded, headers("Content-Type", "application/json"));
      assertEquals(413, refused.status());
      assertEquals(200, post("/chat/handoff", body("nonce", "n")).status());
    }
  }

  @Nested
  class Transport {
    @Test
    void anUnlistedOriginIsRefusedOnEveryRoute() throws Exception {
      handoff.register("n", "conv-root", "call-9");
      for (String path : List.of("/chat/handoff", "/chat/escalate", "/chat/say")) {
        AiChatHttpTestSupport.Resp r =
            postJson(base + path, body("nonce", "n"), headers("Origin", "https://evil.test"));
        assertEquals(403, r.status());
        assertEquals(Map.of("error", "origin not allowed"), r.json());
      }
      // The nonce was never touched.
      assertNotNull(handoff.lookup("n"));
    }

    @Test
    void badJsonIsAnEmptyBody() throws Exception {
      AiChatHttpTestSupport.Resp r =
          postRaw(base + "/chat/handoff", "{x".getBytes(StandardCharsets.UTF_8), Map.of());
      assertEquals(404, r.status());
      assertEquals(Map.of("error", "not found"), r.json());
    }

    @Test
    void onlyPostIsRouted() throws Exception {
      AiChatHttpTestSupport.Resp get =
          send("GET", base + "/chat/handoff", HttpRequest.BodyPublishers.noBody(), Map.of());
      assertEquals(405, get.status());
      assertEquals("POST", get.header("allow"));
      assertEquals(Map.of("detail", "Method Not Allowed"), get.json());
      AiChatHttpTestSupport.Resp opt =
          send("OPTIONS", base + "/chat/say", HttpRequest.BodyPublishers.noBody(), Map.of());
      assertEquals(405, opt.status());
    }

    @Test
    void mountedAtThePrefixItRoutesBySubPath() throws Exception {
      // The same router mounted once at a prefix of its own serves all three routes.
      HttpServer s = AiChatHttpTestSupport.start(Map.of("/hand", handoff.router()));
      try {
        handoff.register("n", "conv-root", "call-9");
        assertEquals(
            200,
            postJson(base(s) + "/hand/say", body("nonce", "n", "text", "hi"), Map.of()).status());
        AiChatHttpTestSupport.Resp nf = postJson(base(s) + "/hand/other", body(), Map.of());
        assertEquals(404, nf.status());
        assertEquals(Map.of("detail", "Not Found"), nf.json());
      } finally {
        s.stop(0);
      }
    }
  }

  @Nested
  class CaptureFailures {
    @Test
    void aCaptureTimeoutDoesNotBlockTheSwitch() {
      HandoffRouter router =
          new HandoffRouter(
              HandoffRouterOptions.builder(gateway)
                  .captureLeg(
                      (c, m) -> {
                        try {
                          Thread.sleep(10_000);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                        }
                        return true;
                      })
                  .captureTimeout(0.05)
                  .build());
      router.register("n", "c", "call-1");
      long t0 = System.nanoTime();
      String handle = router.redeem("n");
      assertTrue((System.nanoTime() - t0) / 1e9 < 5.0);
      assertNotNull(handle);
      assertEquals("c.1", gateway.readHandle(handle));
    }

    @Test
    void aRaisingCaptureDoesNotBlockTheSwitch() {
      HandoffRouter router =
          new HandoffRouter(
              HandoffRouterOptions.builder(gateway)
                  .captureLeg(
                      (c, m) -> {
                        throw new IllegalStateException("storage down");
                      })
                  .build());
      router.register("n", "c", "call-1");
      String handle = router.redeem("n");
      assertNotNull(handle);
      assertEquals("c.1", gateway.readHandle(handle));
    }

    @Test
    void aRaisingEndCallDoesNotBlockTheSwitch() {
      HandoffRouter router =
          new HandoffRouter(
              HandoffRouterOptions.builder(gateway)
                  .endCall(
                      c -> {
                        throw new IllegalStateException("platform down");
                      })
                  .build());
      router.register("n", "c", "call-1");
      assertNotNull(router.redeem("n"));
    }

    @Test
    void aRaisingNextIdFailsTheRedemption() {
      HandoffRouter router =
          new HandoffRouter(
              HandoffRouterOptions.builder(gateway)
                  .nextConversationId(
                      c -> {
                        throw new IllegalStateException("no id");
                      })
                  .build());
      router.register("n", "c", "call-1");
      assertNull(router.redeem("n"));
    }
  }

  // test_documented_limits.py::TestTypingLifetime
  @Nested
  class TypingLifetime {
    @Test
    void typingStopsWhenTheNonceExpires() {
      List<List<Object>> sent = new ArrayList<>();
      HandoffRouter router =
          new HandoffRouter(
              HandoffRouterOptions.builder(gateway())
                  .sendMessage(recordingSender(sent))
                  .nonceTtl(600)
                  .build());
      router.register("n", "c", "call-1");
      assertTrue(router.say("n", "before"));
      NonceEntry e = router.nonces().get("n");
      e.setIssuedAt(e.getIssuedAt() - 601);
      assertFalse(router.say("n", "after"));
      assertEquals(List.of(List.of("say", "before")), sent);
    }

    @Test
    void typingStopsOnceTheNonceIsRedeemed() {
      List<List<Object>> sent = new ArrayList<>();
      HandoffRouter router =
          new HandoffRouter(
              HandoffRouterOptions.builder(gateway()).sendMessage(recordingSender(sent)).build());
      router.register("n", "c", "call-1");
      assertTrue(router.say("n", "before"));
      assertNotNull(router.redeem("n"));
      assertFalse(router.say("n", "after"));
      assertEquals(List.of(List.of("say", "before")), sent);
    }
  }

  @Test
  void attributesReadBack() {
    HandoffRouter r = new HandoffRouter(HandoffRouterOptions.builder(gateway).build());
    assertEquals(gateway, r.getGateway());
    assertNull(r.getCaptureLeg());
    assertNull(r.getEndCall());
    assertNull(r.getSendMessage());
    assertEquals(HandoffRouter.DEFAULT_NONCE_TTL, r.getNonceTtl());
    assertEquals(HandoffRouter.DEFAULT_MAX_MESSAGES_PER_CALL, r.getMaxMessagesPerCall());
    assertEquals(HandoffRouter.DEFAULT_CAPTURE_TIMEOUT, r.getCaptureTimeout());
    NonceEntry e = new NonceEntry("c");
    assertNull(e.getCallId());
    assertEquals(0, e.getMessages());
    assertFalse(e.isRedeemed());
  }
}
