/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.security.SessionManager;
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
}
