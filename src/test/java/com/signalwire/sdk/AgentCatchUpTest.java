/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.security.WebhookValidator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * AgentBase / WebMixin / webhook surface the reference added in 3.5.x: on_call_end,
 * add_per_call_config, mount, validate_webhook_signature_sha256.
 */
class AgentCatchUpTest {

  private static AgentBase agent() {
    return AgentBase.builder().name("t").route("/t").authUser("u").authPassword("p").build();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> aiParams(AgentBase a) {
    Map<String, Object> swml = a.renderSwml("http://127.0.0.1:3000");
    var sections = (Map<String, Object>) swml.get("sections");
    var main = (List<Map<String, Object>>) sections.get("main");
    for (Map<String, Object> verb : main) {
      if (verb.containsKey("ai")) {
        return (Map<String, Object>) ((Map<String, Object>) verb.get("ai")).get("params");
      }
    }
    throw new AssertionError("no ai verb in " + swml);
  }

  @Test
  void onCallEndRunsHandlersInOrderWithTheCallLog() {
    var a = agent();
    List<String> seen = new ArrayList<>();
    BiConsumer<List<Map<String, Object>>, Map<String, Object>> first =
        (log, raw) -> seen.add("first:" + log.size() + ":" + raw.get("call_id"));
    assertSame(first, a.onCallEnd(first));
    a.onCallEnd(
        (log, raw) -> {
          throw new IllegalStateException("boom"); // isolated; does not stop the others
        });
    a.onCallEnd((log, raw) -> seen.add("third"));
    var log = List.<Map<String, Object>>of(Map.of("role", "user", "content", "hi"));
    var result =
        a.onFunctionCall("hangup_hook", Map.of(), Map.of("call_id", "c-9", "raw_call_log", log));
    assertEquals(List.of("first:1:c-9", "third"), seen);
    assertEquals("", result.getResponse());
  }

  @Test
  void onCallEndTurnsOnSwaigPostConversation() {
    var a = agent();
    a.onCallEnd((log, raw) -> {});
    assertEquals(Boolean.TRUE, aiParams(a).get("swaig_post_conversation"));
  }

  @Test
  void onCallEndLeavesAnExplicitFalseAlone() {
    var a = agent();
    a.setParam("swaig_post_conversation", false);
    a.onCallEnd((log, raw) -> {});
    assertEquals(Boolean.FALSE, aiParams(a).get("swaig_post_conversation"));
  }

  @Test
  void addPerCallConfigAccumulatesInOrder() {
    var a = agent();
    List<String> order = new ArrayList<>();
    a.addPerCallConfig((q, b, h, eph) -> order.add("one"));
    a.addPerCallConfig((q, b, h, eph) -> order.add("two"));
    a.getDynamicConfigCallback().configure(Map.of(), Map.of(), Map.of(), a);
    assertEquals(List.of("one", "two"), order);
  }

  @Test
  void setDynamicConfigCallbackReplacesTheChain() {
    var a = agent();
    List<String> order = new ArrayList<>();
    a.addPerCallConfig((q, b, h, eph) -> order.add("one"));
    a.setDynamicConfigCallback((q, b, h, eph) -> order.add("only"));
    a.addPerCallConfig((q, b, h, eph) -> order.add("two"));
    a.getDynamicConfigCallback().configure(Map.of(), Map.of(), Map.of(), a);
    assertEquals(List.of("only", "two"), order);
  }

  @Test
  void mountRefusesTheAgentsOwnRoutes() {
    var a = agent();
    assertThrows(IllegalArgumentException.class, () -> a.mount(ex -> {}, "/t/swaig"));
    a.mount(ex -> {}, "/chat");
    assertThrows(IllegalArgumentException.class, () -> a.mount(ex -> {}, "/chat/"));
  }

  private static String hexHmacSha256(String key, String msg) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    StringBuilder sb = new StringBuilder();
    for (byte b : mac.doFinal(msg.getBytes(StandardCharsets.UTF_8))) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  @Test
  void sha256SignatureValidates() throws Exception {
    String url = "https://example.com/swaig";
    String body = "{\"a\":1}";
    String sig = hexHmacSha256("key", url + body);
    assertTrue(WebhookValidator.validateWebhookSignatureSha256("key", sig, url, body));
    assertTrue(!WebhookValidator.validateWebhookSignatureSha256("key", sig, url, body + " "));
    assertTrue(!WebhookValidator.validateWebhookSignatureSha256("key", "", url, body));
    assertThrows(
        IllegalArgumentException.class,
        () -> WebhookValidator.validateWebhookSignatureSha256("", sig, url, body));
  }

  @Test
  void middlewarePrefersTheSha256Header() throws Exception {
    String url = "https://example.com/swaig";
    String body = "{}";
    String sig = hexHmacSha256("key", url + body);
    // Valid SHA-256 header alone is enough; a bad SHA-1 header is never consulted.
    assertEquals(
        null,
        WebhookValidator.validate(
            "POST",
            url,
            Map.of("X-SignalWire-Sha256-Signature", sig, "X-SignalWire-Signature", "bad"),
            body,
            "key"));
    // A bad SHA-256 header falls back to the SHA-1 header (absent here -> 403).
    assertEquals(
        403,
        WebhookValidator.validate(
                "POST", url, Map.of("X-SignalWire-Sha256-Signature", "bad"), body, "key")
            .status());
  }
}
