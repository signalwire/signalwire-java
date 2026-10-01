/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import com.signalwire.sdk.logging.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The three routes a browser client needs beside a {@link ChatGateway}: moving one conversation
 * between voice and text, and typing into a live call.
 *
 * <p>The SignalWire address widget hardcodes {@code {gateway-url}/handoff}, {@code
 * {gateway-url}/escalate} and {@code {gateway-url}/say} against the same URL that points at a
 * {@code ChatGateway}, and sends {@code handoff_nonce} and {@code chat_handle} as user variables.
 *
 * <p><b>Mechanism vs policy.</b> This class owns the wire contract only: the routes, the nonce, the
 * ordering guarantee, and the spend guards. Where a leg's transcript gets written, how a call is
 * hung up and how text reaches it are the application's, injected as callbacks ({@code captureLeg},
 * {@code endCall}, {@code sendMessage}).
 *
 * <p><b>The nonce.</b> A browser cannot be trusted to name a call. The application puts a random
 * {@code handoff_nonce} in the user variables of one dial, {@linkplain #register registers} it here
 * against that call's ids, and the browser presents it later. A nonce is registered once; a repeat
 * registration changes nothing. Redemption is single use; typing is repeatable, bounded by {@code
 * maxMessagesPerCall}, until the nonce is redeemed or {@code nonceTtl} seconds pass. An unknown
 * nonce is answered exactly like an expired or redeemed one.
 *
 * <p><b>The ordering guarantee.</b> A medium never starts until the one it replaces has finished
 * and its record is durable: {@code /handoff} ends the call and waits for capture before minting a
 * handle; {@code /escalate} ends the chat leg and waits before returning. The wait is bounded by
 * {@code captureTimeout}.
 *
 * <p><b>Deployment.</b> The nonce registry lives in this process. Registration, redemption and the
 * typing count are atomic within one router; across routers sharing a {@code registry} they are
 * not.
 */
public class HandoffRouter {

  /** Seconds a nonce stays usable after its first registration. */
  public static final int DEFAULT_NONCE_TTL = 3600;

  /** Ceiling on typed messages for one call. */
  public static final int DEFAULT_MAX_MESSAGES_PER_CALL = 200;

  /** Seconds to wait for the capture callback. */
  public static final double DEFAULT_CAPTURE_TIMEOUT = 8.0;

  private static final Logger LOG = Logger.getLogger("signalwire.ai_chat.handoff");

  private static final Set<String> ROUTES = Set.of("/handoff", "/escalate", "/say");

  /** Runs capture callbacks so their wait can be bounded; daemon threads never pin the JVM. */
  private static final ExecutorService CAPTURE_POOL =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = new Thread(r, "signalwire-handoff-capture");
            t.setDaemon(true);
            return t;
          });

  private final ChatGateway gateway;
  private final BiFunction<String, String, Boolean> captureLeg;
  private final Consumer<String> endCall;
  private final BiFunction<String, String, Boolean> sendMessage;
  private final Function<String, String> nextConversationId;
  private final int nonceTtl;
  private final int maxMessagesPerCall;
  private final double captureTimeout;
  private final Map<String, NonceEntry> nonces;

  /** Guards every read-modify-write of the nonce table; never held across a callback. */
  private final Object lock = new Object();

  /**
   * Configure the handoff.
   *
   * @param options the router's configuration; {@code gateway} is required.
   */
  public HandoffRouter(HandoffRouterOptions options) {
    if (options.getGateway() == null) {
      throw new IllegalArgumentException("gateway is required");
    }
    this.gateway = options.getGateway();
    this.captureLeg = options.getCaptureLeg();
    this.endCall = options.getEndCall();
    this.sendMessage = options.getSendMessage();
    this.nextConversationId =
        options.getNextConversationId() != null
            ? options.getNextConversationId()
            : HandoffRouter::defaultNextId;
    this.nonceTtl = options.getNonceTtl();
    this.maxMessagesPerCall = options.getMaxMessagesPerCall();
    this.captureTimeout = options.getCaptureTimeout();
    this.nonces = options.getRegistry() != null ? options.getRegistry() : new HashMap<>();
  }

  // ── Attributes ────────────────────────────────────────────────────

  /**
   * The gateway that owns the conversations.
   *
   * @return the gateway.
   */
  public ChatGateway getGateway() {
    return gateway;
  }

  /**
   * The capture callback.
   *
   * @return the callback, or {@code null}.
   */
  public BiFunction<String, String, Boolean> getCaptureLeg() {
    return captureLeg;
  }

  /**
   * The hang-up callback.
   *
   * @return the callback, or {@code null}.
   */
  public Consumer<String> getEndCall() {
    return endCall;
  }

  /**
   * The typing callback.
   *
   * @return the callback, or {@code null}.
   */
  public BiFunction<String, String, Boolean> getSendMessage() {
    return sendMessage;
  }

  /**
   * The function producing a new leg's conversation id ({@code root} → {@code root.1}, {@code
   * root.2} → {@code root.3} by default).
   *
   * @return the function.
   */
  public Function<String, String> getNextConversationId() {
    return nextConversationId;
  }

  /**
   * Seconds a nonce stays usable after its first registration.
   *
   * @return the TTL.
   */
  public int getNonceTtl() {
    return nonceTtl;
  }

  /**
   * Ceiling on typed messages for one call.
   *
   * @return the cap.
   */
  public int getMaxMessagesPerCall() {
    return maxMessagesPerCall;
  }

  /**
   * Seconds to wait for the capture callback.
   *
   * @return the ceiling.
   */
  public double getCaptureTimeout() {
    return captureTimeout;
  }

  /** The nonce table (tests). */
  Map<String, NonceEntry> nonces() {
    return nonces;
  }

  // ── nonce lifecycle ───────────────────────────────────────────────

  /**
   * {@code root} → {@code root.1}; {@code root.2} → {@code root.3}. {@code .} specifically: the
   * chat service strips {@code ~} silently, {@code _} and {@code -} already occur inside generated
   * ids, and {@code :} is the gateway's handle delimiter.
   */
  private static String defaultNextId(String conversationId) {
    int dot = conversationId.lastIndexOf('.');
    if (dot > 0) {
      String tail = conversationId.substring(dot + 1);
      if (!tail.isEmpty() && tail.chars().allMatch(c -> c >= '0' && c <= '9')) {
        return conversationId.substring(0, dot)
            + "."
            + new java.math.BigInteger(tail).add(java.math.BigInteger.ONE);
      }
    }
    return conversationId + ".1";
  }

  /**
   * Record what a nonce is a capability for, with no call yet.
   *
   * @param nonce the nonce the dial carried.
   * @param conversationId the conversation it belongs to.
   */
  public void register(String nonce, String conversationId) {
    register(nonce, conversationId, null);
  }

  /**
   * Record what a nonce is a capability for.
   *
   * <p>Call this from the dynamic-config callback of the dial that carried the nonce, reading
   * {@code callId} from the request the platform sent — never from anything the browser supplied.
   * The first registration stands: registering a nonce already in the table changes nothing, and a
   * redeemed nonce stays redeemed. Once the entry's {@code nonceTtl} has passed, the nonce can be
   * registered again.
   *
   * @param nonce the nonce the dial carried; empty or {@code null} is ignored.
   * @param conversationId the conversation it belongs to.
   * @param callId the call that carried it, or {@code null}.
   */
  public void register(String nonce, String conversationId, String callId) {
    if (nonce == null || nonce.isEmpty()) {
      return;
    }
    NonceEntry existing;
    synchronized (lock) {
      prune();
      existing = nonces.get(nonce);
      if (existing == null) {
        nonces.put(nonce, new NonceEntry(conversationId, callId));
      }
    }
    if (existing != null) {
      if (existing.isRedeemed()
          || !java.util.Objects.equals(existing.getConversationId(), conversationId)
          || !java.util.Objects.equals(existing.getCallId(), callId)) {
        LOG.warn(
            "handoff_nonce_already_registered conversation_id=%s call_id=%s redeemed=%s",
            existing.getConversationId(), existing.getCallId(), existing.isRedeemed());
      }
      return;
    }
    LOG.info("handoff_nonce_registered conversation_id=%s call_id=%s", conversationId, callId);
  }

  /** Drop entries, redeemed ones included, whose TTL has passed. Call with the lock held. */
  private void prune() {
    double cutoff = NonceEntry.monotonicNow() - nonceTtl;
    List<String> expired = new ArrayList<>();
    for (Map.Entry<String, NonceEntry> e : nonces.entrySet()) {
      if (e.getValue().getIssuedAt() < cutoff) {
        expired.add(e.getKey());
      }
    }
    for (String n : expired) {
      nonces.remove(n);
    }
  }

  /** The live entry for {@code nonce}: null if unknown, expired or redeemed. */
  NonceEntry lookup(Object nonce) {
    synchronized (lock) {
      return lookupLocked(nonce);
    }
  }

  private NonceEntry lookupLocked(Object nonce) {
    if (!(nonce instanceof String n) || n.isEmpty()) {
      return null;
    }
    prune();
    NonceEntry entry = nonces.get(n);
    if (entry == null || entry.isRedeemed()) {
      return null;
    }
    return entry;
  }

  // ── operations ────────────────────────────────────────────────────

  /** Await the application's capture, bounded by {@code captureTimeout}. Never throws. */
  private boolean capture(String conversationId, String medium) {
    if (captureLeg == null) {
      return false;
    }
    Future<Boolean> f = CAPTURE_POOL.submit(() -> captureLeg.apply(conversationId, medium));
    try {
      Boolean ok = f.get((long) (captureTimeout * 1_000_000_000L), TimeUnit.NANOSECONDS);
      return ok != null && ok;
    } catch (TimeoutException e) {
      f.cancel(true);
      LOG.warn(
          "handoff_capture_timeout conversation_id=%s medium=%s note=starting the next medium "
              + "without this leg's record",
          conversationId, medium);
    } catch (ExecutionException e) {
      LOG.error(
          "handoff_capture_failed conversation_id=%s error=%s",
          conversationId, String.valueOf(e.getCause()));
    } catch (InterruptedException e) {
      f.cancel(true);
      Thread.currentThread().interrupt();
    }
    return false;
  }

  /**
   * Exchange a nonce for a chat handle. Single use.
   *
   * <p>Ends the call, waits for its record, and only then mints a handle for a new leg of the same
   * conversation.
   *
   * @param nonce the nonce the browser presented.
   * @return the signed handle, or {@code null} for an unknown, expired or already redeemed nonce —
   *     deliberately indistinguishable from each other.
   */
  public String redeem(String nonce) {
    NonceEntry entry;
    synchronized (lock) {
      entry = lookupLocked(nonce);
      if (entry == null) {
        return null;
      }
      // Consumed even if what follows fails: a nonce is one attempt. Written back so a
      // shared registry stores the change.
      entry.setRedeemed(true);
      nonces.put(nonce, entry);
    }

    if (entry.getCallId() != null && !entry.getCallId().isEmpty() && endCall != null) {
      try {
        endCall.accept(entry.getCallId());
      } catch (RuntimeException e) {
        LOG.warn("handoff_end_call_failed error=%s", String.valueOf(e));
      }
    }

    capture(entry.getConversationId(), "voice");

    String handle;
    try {
      handle = gateway.mintHandle(nextConversationId.apply(entry.getConversationId()));
    } catch (RuntimeException e) {
      LOG.error("handoff_mint_failed error=%s", String.valueOf(e));
      return null;
    }
    LOG.info("handoff_redeemed conversation_id=%s", entry.getConversationId());
    return handle;
  }

  /**
   * End a chat leg and wait for its record, before a call is placed. The browser calls this and
   * waits, so a voice leg started immediately afterwards finds the text leg already recorded.
   *
   * @param handle the chat handle naming the leg.
   * @return {@code false} if the handle does not verify, else {@code true}.
   */
  public boolean escalate(String handle) {
    String conversationId;
    try {
      conversationId = gateway.readHandle(handle);
    } catch (RuntimeException e) {
      return false;
    }
    capture(conversationId, "chat");
    LOG.info("handoff_escalated conversation_id=%s", conversationId);
    return true;
  }

  /**
   * Deliver typed text into the live call the nonce names.
   *
   * <p>Does NOT consume the nonce: typing is repeatable until the nonce is redeemed or its {@code
   * nonceTtl} passes, up to {@code maxMessagesPerCall} messages. Text over {@link
   * ChatGateway#MAX_MESSAGE_BYTES} (UTF-8) is refused.
   *
   * @param nonce the nonce the browser presented.
   * @param text the typed text; trimmed before delivery.
   * @return {@code true} if the text was handed to {@code sendMessage}.
   */
  public boolean say(String nonce, String text) {
    if (sendMessage == null) {
      return false;
    }
    String cleaned = GatewayHttp.pyStrip(text == null ? "" : text);
    if (cleaned.isEmpty() || GatewayHttp.utf8Len(cleaned) > ChatGateway.MAX_MESSAGE_BYTES) {
      return false;
    }
    NonceEntry entry;
    synchronized (lock) {
      entry = lookupLocked(nonce);
      if (entry == null || entry.getCallId() == null || entry.getCallId().isEmpty()) {
        return false;
      }
      if (entry.getMessages() >= maxMessagesPerCall) {
        LOG.warn("handoff_say_cap_reached call_id=%s", entry.getCallId());
        return false;
      }
      // Take the message's slot before delivering, so overlapping requests can't all
      // pass the cap. Written back so a shared registry stores the change.
      entry.setMessages(entry.getMessages() + 1);
      nonces.put(nonce, entry);
    }
    try {
      // The reference awaits delivery and ignores what the callback returns.
      Boolean ignored = sendMessage.apply(entry.getCallId(), cleaned);
      LOG.debug("handoff_say_delivered call_id=%s result=%s", entry.getCallId(), ignored);
    } catch (RuntimeException e) {
      LOG.error("handoff_say_failed error=%s", String.valueOf(e));
      synchronized (lock) {
        // Not delivered: give the slot back, if the table still holds this registration.
        // A shared registry may return a copy, so it's matched by value.
        NonceEntry current = nonces.get(nonce);
        if (current != null
            && current.getMessages() > 0
            && java.util.Objects.equals(current.getConversationId(), entry.getConversationId())
            && java.util.Objects.equals(current.getCallId(), entry.getCallId())
            && current.getIssuedAt() == entry.getIssuedAt()) {
          current.setMessages(current.getMessages() - 1);
          nonces.put(nonce, current);
        }
      }
      return false;
    }
    return true;
  }

  // ── transport ─────────────────────────────────────────────────────

  /**
   * A mountable handler serving {@code POST /handoff}, {@code POST /escalate} and {@code POST /say}
   * — the JDK {@code HttpServer} form of the reference's router. The browser derives all three
   * paths from the gateway's URL, so they must be siblings of the gateway's endpoint. A JDK {@code
   * HttpServer} holds one handler per context, so mount the gateway at the prefix and this router
   * at each route beneath it:
   *
   * <pre>{@code
   * server.createContext("/chat", gateway.router());
   * HttpHandler handoffRoutes = handoff.router();
   * for (String route : List.of("handoff", "escalate", "say")) {
   *   server.createContext("/chat/" + route, handoffRoutes);
   * }
   * }</pre>
   *
   * <p>(Mounted on a prefix of its own, it routes by sub-path.) Every route refuses a disallowed
   * {@code Origin} with 403 and answers 413 for a body over {@link
   * ChatGateway#MAX_REQUEST_BODY_BYTES}, and {@code /say} for text over {@link
   * ChatGateway#MAX_MESSAGE_BYTES}; both are checked before the nonce is looked up, so the answer
   * says nothing about whether the nonce is live.
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
    String r = GatewayHttp.resolve(exchange, ROUTES);
    if (r == null) {
      GatewayHttp.notFound(exchange);
      return;
    }
    if (r.startsWith("redirect:")) {
      GatewayHttp.redirect(exchange, r.substring("redirect:".length()));
      return;
    }
    if (!"POST".equals(exchange.getRequestMethod().toUpperCase(Locale.ROOT))) {
      GatewayHttp.methodNotAllowed(exchange, "POST");
      return;
    }
    try {
      gateway.checkOrigin(exchange.getRequestHeaders().getFirst("Origin"));
    } catch (RuntimeException e) {
      error(exchange, 403, "origin not allowed");
      return;
    }
    Map<String, Object> data;
    try {
      data = body(exchange);
    } catch (GatewayRejection rej) {
      error(exchange, rej.getStatus(), rej.getReason());
      return;
    }
    switch (r) {
      case "/handoff" -> handoff(exchange, data);
      case "/escalate" -> escalate(exchange, data);
      default -> say(exchange, data);
    }
  }

  private void handoff(HttpExchange exchange, Map<String, Object> data) throws IOException {
    if (!(data.get("nonce") instanceof String nonce)) {
      error(exchange, 404, "not found");
      return;
    }
    String handle = redeem(nonce);
    if (handle == null || handle.isEmpty()) {
      // Same answer for unknown, expired and already-redeemed.
      error(exchange, 404, "not found");
      return;
    }
    GatewayHttp.sendJson(exchange, 200, Map.of("handle", handle), Map.of());
  }

  private void escalate(HttpExchange exchange, Map<String, Object> data) throws IOException {
    if (!(data.get("handle") instanceof String handle) || handle.isEmpty()) {
      error(exchange, 400, "bad request");
      return;
    }
    if (!escalate(handle)) {
      error(exchange, 404, "not found");
      return;
    }
    GatewayHttp.sendJson(exchange, 200, Map.of("ok", true), Map.of());
  }

  private void say(HttpExchange exchange, Map<String, Object> data) throws IOException {
    Object nonce = data.get("nonce");
    Object text = data.containsKey("text") ? data.get("text") : "";
    if (!(nonce instanceof String n) || !(text instanceof String t)) {
      error(exchange, 404, "not found");
      return;
    }
    if (GatewayHttp.utf8Len(t) > ChatGateway.MAX_MESSAGE_BYTES) {
      error(exchange, 413, "message too large");
      return;
    }
    if (!say(n, t)) {
      error(exchange, 404, "not found");
      return;
    }
    GatewayHttp.sendJson(exchange, 200, Map.of("ok", true), Map.of());
  }

  /** The JSON object sent, or an empty map for anything else; 413 propagates. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> body(HttpExchange exchange) {
    Object data;
    try {
      data = GatewayHttp.parseJsonBody(exchange, ChatGateway.MAX_REQUEST_BODY_BYTES);
    } catch (GatewayRejection rej) {
      throw rej;
    } catch (RuntimeException e) {
      return Map.of();
    }
    return data instanceof Map ? (Map<String, Object>) data : Map.of();
  }

  private static void error(HttpExchange exchange, int status, String reason) throws IOException {
    GatewayHttp.sendJson(exchange, status, Map.of("error", reason), Map.of());
  }
}
