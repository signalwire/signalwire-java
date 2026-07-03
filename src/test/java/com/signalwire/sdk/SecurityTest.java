/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.security.SessionManager;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for SessionManager HMAC-SHA256 token system. */
class SecurityTest {

  @Test
  void testTokenCreationAndValidation() {
    var sm = new SessionManager();
    String token = sm.createToken("get_weather", "call-123");

    assertNotNull(token);
    assertTrue(token.contains("."));
    assertTrue(sm.validateToken(token, "get_weather", "call-123"));
  }

  @Test
  void testTokenWrongFunctionName() {
    var sm = new SessionManager();
    String token = sm.createToken("get_weather", "call-123");

    assertFalse(sm.validateToken(token, "wrong_function", "call-123"));
  }

  @Test
  void testTokenWrongCallId() {
    var sm = new SessionManager();
    String token = sm.createToken("get_weather", "call-123");

    assertFalse(sm.validateToken(token, "get_weather", "wrong-call-id"));
  }

  @Test
  void testTokenExpiry() {
    var sm = new SessionManager();
    // Create token that expired 10 seconds ago
    String token = sm.createToken("get_weather", "call-123", -10);

    assertFalse(sm.validateToken(token, "get_weather", "call-123"));
  }

  @Test
  void testTokenCustomExpiry() {
    var sm = new SessionManager();
    String token = sm.createToken("get_weather", "call-123", 7200);

    assertTrue(sm.validateToken(token, "get_weather", "call-123"));
  }

  @Test
  void testTokenNullInput() {
    var sm = new SessionManager();
    assertFalse(sm.validateToken(null, "func", "call"));
    assertFalse(sm.validateToken("", "func", "call"));
  }

  @Test
  void testTokenInvalidFormat() {
    var sm = new SessionManager();
    assertFalse(sm.validateToken("invalid-no-dot", "func", "call"));
  }

  @Test
  void testTokenTamperedSignature() {
    var sm = new SessionManager();
    String token = sm.createToken("get_weather", "call-123");

    // Tamper with the signature
    int dotIdx = token.indexOf('.');
    String tampered = token.substring(0, dotIdx + 1) + "0000000000000000";
    assertFalse(sm.validateToken(tampered, "get_weather", "call-123"));
  }

  @Test
  void testTokenTamperedPayload() {
    var sm = new SessionManager();
    String token = sm.createToken("get_weather", "call-123");

    // Replace payload but keep signature
    int dotIdx = token.indexOf('.');
    String tampered = "dGFtcGVyZWQ" + token.substring(dotIdx);
    assertFalse(sm.validateToken(tampered, "get_weather", "call-123"));
  }

  @Test
  void testDifferentSessionManagersDifferentSecrets() {
    var sm1 = new SessionManager();
    var sm2 = new SessionManager();

    String token = sm1.createToken("get_weather", "call-123");

    // Token from sm1 should not validate with sm2 (different secrets)
    assertFalse(sm2.validateToken(token, "get_weather", "call-123"));
  }

  @Test
  void testDefaultExpiry() {
    var sm = new SessionManager(); // default 3600
    String token = sm.createToken("func", "call");
    assertTrue(sm.validateToken(token, "func", "call"));
  }

  @Test
  void testTokenContainsDotSeparator() {
    var sm = new SessionManager();
    String token = sm.createToken("func", "call");
    assertTrue(token.contains("."));

    String[] parts = token.split("\\.", 2);
    assertEquals(2, parts.length);
    assertTrue(parts[0].length() > 0);
    assertTrue(parts[1].length() > 0);
  }

  @Test
  void testTokenSignatureIsHex() {
    var sm = new SessionManager();
    String token = sm.createToken("func", "call");
    String sig = token.substring(token.indexOf('.') + 1);
    // HMAC-SHA256 produces 64 hex chars
    assertEquals(64, sig.length());
    assertTrue(sig.matches("[0-9a-f]+"));
  }

  // ── Python-surface token aliases ─────────────────────────────────

  @Test
  void testCreateToolTokenValidatesViaValidateToken() {
    var sm = new SessionManager();
    // create_tool_token is an alias of create_token; the resulting token
    // must validate against validate_token (same wire format).
    String token = sm.createToolToken("lookup_order", "call-abc");
    assertTrue(sm.validateToken(token, "lookup_order", "call-abc"));
  }

  @Test
  void testGenerateTokenIsAliasOfCreateToken() {
    var sm = new SessionManager();
    String token = sm.generateToken("lookup_order", "call-abc");
    assertTrue(sm.validateToken(token, "lookup_order", "call-abc"));
    assertFalse(sm.validateToken(token, "other_fn", "call-abc"));
  }

  @Test
  void testValidateToolTokenReferenceParamOrder() {
    var sm = new SessionManager();
    String token = sm.createToolToken("lookup_order", "call-abc");
    // validate_tool_token(function_name, token, call_id) — reference order.
    assertTrue(sm.validateToolToken("lookup_order", token, "call-abc"));
    assertFalse(sm.validateToolToken("lookup_order", token, "wrong-call"));
    assertFalse(sm.validateToolToken("wrong_fn", token, "call-abc"));
  }

  // ── Session lifecycle + metadata round-trip ──────────────────────

  @Test
  void testCreateSessionReturnsProvidedId() {
    var sm = new SessionManager();
    assertEquals("existing-call", sm.createSession("existing-call"));
  }

  @Test
  void testCreateSessionMintsIdWhenAbsent() {
    var sm = new SessionManager();
    String a = sm.createSession(null);
    String b = sm.createSession();
    assertNotNull(a);
    assertFalse(a.isEmpty());
    assertNotEquals(a, b, "each mint should be unique");
  }

  @Test
  void testSessionMetadataRoundTrip() {
    var sm = new SessionManager();
    // create -> activate -> set/get metadata -> end round-trip.
    String callId = sm.createSession(null);
    assertTrue(sm.activateSession(callId));

    // Unknown session yields an empty (non-null) map.
    assertTrue(sm.getSessionMetadata(callId).isEmpty());

    assertTrue(sm.setSessionMetadata(callId, "user", "alice"));
    assertTrue(sm.setSessionMetadata(callId, "step", 3));

    Map<String, Object> meta = sm.getSessionMetadata(callId);
    assertEquals("alice", meta.get("user"));
    assertEquals(3, meta.get("step"));

    // Returned map is a copy — mutating it must not affect the store.
    meta.put("injected", "x");
    assertFalse(sm.getSessionMetadata(callId).containsKey("injected"));

    // end_session clears the metadata.
    assertTrue(sm.endSession(callId));
    assertTrue(sm.getSessionMetadata(callId).isEmpty());
  }

  @Test
  void testGetSessionMetadataUnknownIsEmptyNotNull() {
    var sm = new SessionManager();
    Map<String, Object> meta = sm.getSessionMetadata("never-seen");
    assertNotNull(meta);
    assertTrue(meta.isEmpty());
  }

  // ── debug_token ──────────────────────────────────────────────────

  @Test
  void testDebugTokenDisabledByDefault() {
    var sm = new SessionManager();
    String token = sm.createToken("func", "call-123");
    Map<String, Object> dbg = sm.debugToken(token);
    assertEquals("debug mode not enabled", dbg.get("error"));
  }

  @Test
  void testDebugTokenDecodesComponentsWhenEnabled() {
    var sm = new SessionManager();
    sm.setDebugMode(true);
    String token = sm.createToken("get_weather", "call-123", 3600);

    Map<String, Object> dbg = sm.debugToken(token);
    assertEquals(Boolean.TRUE, dbg.get("valid_format"));

    @SuppressWarnings("unchecked")
    Map<String, Object> components = (Map<String, Object>) dbg.get("components");
    assertEquals("get_weather", components.get("function"));
    assertEquals("call-123", components.get("call_id"));

    @SuppressWarnings("unchecked")
    Map<String, Object> status = (Map<String, Object>) dbg.get("status");
    assertEquals(Boolean.FALSE, status.get("is_expired"));
  }

  @Test
  void testDebugTokenMalformed() {
    var sm = new SessionManager();
    sm.setDebugMode(true);
    Map<String, Object> dbg = sm.debugToken("no-dot-here");
    assertEquals(Boolean.FALSE, dbg.get("valid_format"));
  }
}
