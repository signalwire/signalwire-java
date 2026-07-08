/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.signalwire.sdk.security.SecurityUtils;
import com.signalwire.sdk.security.SessionManager;
import com.signalwire.sdk.security.WebhookValidator;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * WireDump — the Java port's WIRE-CRYPTO dump program for the cross-port wire differ
 * (porting-sdk/scripts/diff_port_wire.py).
 *
 * <p>It runs the shared {@code wire_crypto} corpus against the Java SDK's native security package
 * ({@link SessionManager} tokens, webhook-signature validation, redact/filter helpers) and prints
 * ONE JSON object mapping
 *
 * <pre>
 *   case-id -&gt; observable-artifact
 * </pre>
 *
 * to stdout. The differ runs this program, canonicalizes both sides, and byte-compares each entry
 * against the Python oracle. Only stdout carries JSON; nothing else is printed there. Mirrors Go's
 * {@code cmd/wire-dump/main.go}.
 *
 * <p>The corpus sentinels ({@code __ORACLE_FORMAT_TOKEN__}, {@code __TAMPERED_TOKEN__}, {@code
 * __ORACLE_SIG__}) are materialized here from the fixed per-case SECRET exactly as the oracle
 * materializes them, so the interop/tamper cases are reproducible.
 *
 * <p>Run via the {@code wireDump} Gradle task:
 *
 * <pre>
 *   ./gradlew --no-daemon -q wireDump
 * </pre>
 */
final class WireDump {

  private WireDump() {}

  /** SECRET mirrors wire_crypto_corpus.SECRET ("a" * 64). */
  private static final String SECRET =
      "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

  /** Fixed far-future expiry (deterministic) — mirrors diff_port_wire._oracle_token. */
  private static final long ORACLE_EXPIRY = 9_999_999_999L;

  /** Fixed 16-hex nonce (deterministic). */
  private static final String ORACLE_NONCE = "0123456789abcdef";

  /** Ordered map helper that preserves insertion order. */
  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  /** Hex-encode bytes (lowercase). */
  private static String hex(byte[] b) {
    StringBuilder sb = new StringBuilder(b.length * 2);
    for (byte x : b) {
      sb.append(Character.forDigit((x >> 4) & 0xF, 16));
      sb.append(Character.forDigit(x & 0xF, 16));
    }
    return sb.toString();
  }

  private static byte[] hmac(String algo, String key, String msg) {
    try {
      Mac mac = Mac.getInstance(algo);
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algo));
      return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Build a token in the SDK wire format (call_id.fn.expiry.nonce.sig, base64url) from the fixed
   * SECRET — the Java mirror of diff_port_wire._oracle_token. The signed message is {@code
   * call_id:fn:expiry:nonce} keyed with HMAC-SHA256, matching {@link SessionManager}.
   */
  private static String oracleToken(String callId, String fn) {
    String signed = callId + ":" + fn + ":" + ORACLE_EXPIRY + ":" + ORACLE_NONCE;
    String sig = hex(hmac("HmacSHA256", SECRET, signed));
    String raw = callId + "." + fn + "." + ORACLE_EXPIRY + "." + ORACLE_NONCE + "." + sig;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /** Flip one byte of the signature — the Java mirror of _tampered_token. */
  private static String tamperedToken() {
    String tok = oracleToken("c", "f");
    byte[] raw = Base64.getUrlDecoder().decode(tok);
    String s = new String(raw, StandardCharsets.UTF_8);
    int last = s.lastIndexOf('.');
    int idx = last + 1;
    char[] cs = s.toCharArray();
    cs[idx] = (cs[idx] == 'f') ? 'e' : 'f';
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(new String(cs).getBytes(StandardCharsets.UTF_8));
  }

  /** Compute the correct webhook signature: hex(HMAC-SHA1(key, url+body)). */
  private static String oracleSig(String url, String body, String key) {
    return hex(hmac("HmacSHA1", key, url + body));
  }

  /** Decode a token and return its wire-format shape (mirrors observeTokenFields). */
  private static Map<String, Object> observeTokenFields(String token) {
    String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
    String[] parts = raw.split("\\.", -1);
    String nonce = parts.length > 3 ? parts[3] : "";
    boolean isHex = parts.length > 3;
    for (int i = 0; i < nonce.length(); i++) {
      char c = nonce.charAt(i);
      if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
        isHex = false;
        break;
      }
    }
    return map(
        "n_fields",
        parts.length,
        "call_id",
        parts.length > 0 ? parts[0] : null,
        "function_name",
        parts.length > 1 ? parts[1] : null,
        "nonce_len",
        nonce.length(),
        "nonce_is_hex",
        isHex);
  }

  public static void main(String[] args) {
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    Map<String, Object> out = new LinkedHashMap<>();

    SessionManager sm = new SessionManager(SECRET.getBytes(StandardCharsets.UTF_8), 9_999_999);

    // token_format: generate a token via the SDK, decode its fields.
    out.put("token_format", observeTokenFields(sm.generateToken("my_func", "call_1")));

    // token_nonce_distinct: two generations must differ (random nonce).
    String n1 = sm.generateToken("f", "c");
    String n2 = sm.generateToken("f", "c");
    out.put("token_nonce_distinct", map("distinct", !n1.equals(n2)));

    // token_interop: validate an oracle-format token built from SECRET.
    out.put(
        "token_interop",
        map(
            "valid",
            sm.validateToken(oracleToken("oracle_call", "oracle_fn"), "oracle_fn", "oracle_call")));

    // token_tamper_rejected: a one-byte-flipped signature must fail.
    out.put("token_tamper_rejected", map("valid", sm.validateToken(tamperedToken(), "f", "c")));

    // wire_validate_webhook_signature: correct HMAC-SHA1 -> valid.
    String whUrl = "https://example.com/hook";
    String whBody = "{\"event\":\"call.created\"}";
    out.put(
        "wire_validate_webhook_signature",
        map(
            "valid",
            WebhookValidator.validateWebhookSignature(
                SECRET, oracleSig(whUrl, whBody, SECRET), whUrl, whBody)));

    // wire_validate_webhook_signature_bad: wrong sig -> invalid.
    String badSig = "deadbeef".repeat(8);
    out.put(
        "wire_validate_webhook_signature_bad",
        map("valid", WebhookValidator.validateWebhookSignature(SECRET, badSig, whUrl, whBody)));

    // wire_redact_url: credentials redacted, token preserved, structure kept.
    out.put(
        "wire_redact_url",
        map(
            "redacted",
            SecurityUtils.redactUrl("https://user:s3cr3t@api.signalwire.com/path?token=abc")));

    // wire_filter_sensitive_headers: authorization + x-api-key dropped, content-type kept.
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Authorization", "Bearer x");
    headers.put("X-Api-Key", "y");
    headers.put("Content-Type", "application/json");
    out.put(
        "wire_filter_sensitive_headers",
        map("filtered", SecurityUtils.filterSensitiveHeaders(headers)));

    System.out.println(gson.toJson(out));
  }
}
