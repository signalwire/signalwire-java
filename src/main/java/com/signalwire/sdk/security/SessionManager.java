package com.signalwire.sdk.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Session manager for HMAC-SHA256 signed tool tokens. Stub for the security module being built by
 * another agent.
 */
public class SessionManager {

  private final byte[] secret;
  private final int defaultExpiry;

  public SessionManager() {
    this(3600);
  }

  public SessionManager(int defaultExpiry) {
    this.defaultExpiry = defaultExpiry;
    this.secret = new byte[32];
    new SecureRandom().nextBytes(this.secret);
  }

  /** Create a signed token for a function + callID. */
  public String createToken(String functionName, String callId) {
    return createToken(functionName, callId, defaultExpiry);
  }

  public String createToken(String functionName, String callId, int expirySeconds) {
    long expiry = System.currentTimeMillis() / 1000 + expirySeconds;
    String payload = functionName + ":" + callId + ":" + expiry;
    String encoded =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    String signature = hmacSign(payload);
    return encoded + "." + signature;
  }

  /** Validate a signed token. */
  public boolean validateToken(String token, String functionName, String callId) {
    if (token == null || token.isEmpty()) return false;
    int dotIdx = token.indexOf('.');
    if (dotIdx < 0) return false;

    String encodedPart = token.substring(0, dotIdx);
    String signaturePart = token.substring(dotIdx + 1);

    String payload;
    try {
      payload = new String(Base64.getUrlDecoder().decode(encodedPart), StandardCharsets.UTF_8);
    } catch (Exception e) {
      return false;
    }

    String[] parts = payload.split(":");
    if (parts.length != 3) return false;

    String tokenFunc = parts[0];
    String tokenCallId = parts[1];
    long tokenExpiry;
    try {
      tokenExpiry = Long.parseLong(parts[2]);
    } catch (NumberFormatException e) {
      return false;
    }

    // Timing-safe comparison
    boolean funcMatch =
        MessageDigest.isEqual(
            tokenFunc.getBytes(StandardCharsets.UTF_8),
            functionName.getBytes(StandardCharsets.UTF_8));
    boolean callMatch =
        MessageDigest.isEqual(
            tokenCallId.getBytes(StandardCharsets.UTF_8), callId.getBytes(StandardCharsets.UTF_8));
    boolean sigMatch =
        MessageDigest.isEqual(
            signaturePart.getBytes(StandardCharsets.UTF_8),
            hmacSign(payload).getBytes(StandardCharsets.UTF_8));

    if (!funcMatch || !callMatch || !sigMatch) return false;

    long now = System.currentTimeMillis() / 1000;
    return now < tokenExpiry;
  }

  private String hmacSign(String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : sig) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (Exception e) {
      throw new RuntimeException("HMAC signing failed", e);
    }
  }
}
