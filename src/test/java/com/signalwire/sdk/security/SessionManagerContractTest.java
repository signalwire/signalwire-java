package com.signalwire.sdk.security;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Contract #70 / behavioral-contract #7 — tool-token WIRE FORMAT + nonce parity.
 *
 * <p>Python (`core/security/session_manager.py`): a minted tool token is the base64url encoding of
 * the 5 dot-joined DECODED fields {@code call_id.function_name.expiry.nonce.signature}; the
 * HMAC-SHA256 signed message is {@code call_id:function_name:expiry:nonce}; {@code nonce =
 * secrets.token_hex(8)} (16 hex chars); validation uses a constant-time compare. These tests assert
 * on the DECODED form, that two mints for the same (fn, call, expiry) get different nonces, that a
 * token constructed in the python-oracle format validates in-port (interop), and that a tampered
 * signature is rejected. The pre-fix Java body (3-field {@code function:call:expiry}, no nonce,
 * fn-first) would fail every one of these.
 */
class SessionManagerContractTest {

  private static String decode(String token) {
    return new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
  }

  @Test
  void mintedTokenDecodesToFiveDotFieldsWithNonEmptyNonce() {
    SessionManager sm = new SessionManager();
    String token = sm.createToken("my_func", "call-abc-123");
    String decoded = decode(token);
    String[] parts = decoded.split("\\.", -1);

    assertEquals(5, parts.length, "decoded token must have exactly 5 dot-fields");
    // call_id FIRST, function_name SECOND (NOT fn-first).
    assertEquals("call-abc-123", parts[0], "field[0] must be call_id");
    assertEquals("my_func", parts[1], "field[1] must be function_name");
    // nonce is field[3], non-empty, 16 hex chars.
    String nonce = parts[3];
    assertFalse(nonce.isEmpty(), "nonce must be non-empty");
    assertEquals(16, nonce.length(), "nonce must be 16 hex chars (token_hex(8))");
    assertTrue(nonce.matches("[0-9a-f]{16}"), "nonce must be lowercase hex");
    // signature is field[4], non-empty.
    assertFalse(parts[4].isEmpty(), "signature must be non-empty");
  }

  @Test
  void twoMintsSameInputsGetDifferentNonces() {
    SessionManager sm = new SessionManager();
    String t1 = sm.createToken("func", "call", 3600);
    String t2 = sm.createToken("func", "call", 3600);

    String[] p1 = decode(t1).split("\\.", -1);
    String[] p2 = decode(t2).split("\\.", -1);
    // Same call_id/function; the nonce (and therefore the whole token) must differ.
    assertEquals(p1[0], p2[0]);
    assertEquals(p1[1], p2[1]);
    assertNotEquals(p1[3], p2[3], "two mints must produce DIFFERENT nonces");
    assertNotEquals(t1, t2, "two mints must produce different tokens");
  }

  @Test
  void pythonOracleFormatTokenValidatesInPort() {
    // Build a token in EXACTLY the python-oracle wire format, keyed on this
    // manager's secret, and assert it validates here (cross-port interop leg).
    byte[] secret = new byte[32];
    java.util.Arrays.fill(secret, (byte) 7);
    SessionManager sm = new SessionManager(secret, 3600);

    String callId = "call-xyz";
    String functionName = "search";
    long expiry = System.currentTimeMillis() / 1000 + 3600;
    String nonce = "0123456789abcdef"; // 16 hex chars, token_hex(8) shape

    // Signed message: call_id:function_name:expiry:nonce (colon-joined, call_id first).
    String message = callId + ":" + functionName + ":" + expiry + ":" + nonce;
    String signature = sm.sign(message);

    // Decoded token: call_id.function_name.expiry.nonce.signature; base64url the whole thing.
    String decoded = callId + "." + functionName + "." + expiry + "." + nonce + "." + signature;
    String token =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(decoded.getBytes(StandardCharsets.UTF_8));

    assertTrue(
        sm.validateToken(token, functionName, callId),
        "a python-oracle-format token must validate in-port");
  }

  @Test
  void tamperedSignatureFailsValidation() {
    SessionManager sm = new SessionManager();
    String token = sm.createToken("func", "call");

    String[] parts = decode(token).split("\\.", -1);
    // Flip one character of the signature (last field).
    char[] sig = parts[4].toCharArray();
    sig[0] = sig[0] == 'a' ? 'b' : 'a';
    parts[4] = new String(sig);
    String tamperedDecoded = String.join(".", parts);
    String tampered =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(tamperedDecoded.getBytes(StandardCharsets.UTF_8));

    assertFalse(sm.validateToken(tampered, "func", "call"), "a flipped signature must be rejected");
  }

  @Test
  void validationRejectsThreeFieldFnFirstToken() {
    // The pre-fix wire shape (function:call:expiry, no nonce, fn-first) must NOT validate —
    // proves the port is on the 5-field call-id-first format, not the old 3-field one.
    SessionManager sm = new SessionManager();
    long expiry = System.currentTimeMillis() / 1000 + 3600;
    String legacyDecoded = "func" + "." + "call" + "." + expiry; // 3 fields, fn-first
    String legacyToken =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(legacyDecoded.getBytes(StandardCharsets.UTF_8));
    assertFalse(sm.validateToken(legacyToken, "func", "call"));
  }
}
