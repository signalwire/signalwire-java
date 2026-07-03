package com.signalwire.sdk.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Session manager for HMAC-SHA256 signed tool tokens.
 *
 * <p>Mirrors the reference {@code signalwire.core.security.session_manager.SessionManager}. Tokens
 * are self-contained (all data needed for validation is inside the signed token) so validation is
 * stateless. The Python reference additionally exposes a set of legacy session-lifecycle methods
 * ({@code create_session}/{@code activate_session}/{@code end_session}/{@code
 * get/set_session_metadata}); this port keeps a real per-call metadata store (like the Ruby and
 * TypeScript ports) so the getter/setter pair round-trips, while activation stays a stateless
 * success hook.
 */
public class SessionManager {

  private final byte[] secret;
  private final int defaultExpiry;

  /** Per-session metadata store: call_id -&gt; (key -&gt; value). */
  private final ConcurrentHashMap<String, Map<String, Object>> sessionMetadata =
      new ConcurrentHashMap<>();

  /** When true, {@link #debugToken(String)} decodes token internals; off by default. */
  private volatile boolean debugMode;

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

    String[] parts = payload.split(":", 0);
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

  // ── Python-surface token aliases ─────────────────────────────────
  //
  // The reference exposes generate_token / create_tool_token as the minting
  // names and validate_tool_token as a back-compat alias of validate_token.
  // Java keeps the existing createToken/validateToken wire format untouched
  // and projects the reference names onto them (matching Ruby's alias set).

  /** Alias of {@link #createToken(String, String)} — Python's {@code generate_token}. */
  public String generateToken(String functionName, String callId) {
    return createToken(functionName, callId);
  }

  /** Alias of {@link #createToken(String, String)} — Python's {@code create_tool_token}. */
  public String createToolToken(String functionName, String callId) {
    return createToken(functionName, callId);
  }

  /**
   * Back-compat alias of {@link #validateToken(String, String, String)} — Python's {@code
   * validate_tool_token(function_name, token, call_id)}. Note the reference's parameter order
   * ({@code function_name, token, call_id}) differs from {@code validate_token}; this method
   * mirrors that reference order and delegates.
   */
  public boolean validateToolToken(String functionName, String token, String callId) {
    return validateToken(token, functionName, callId);
  }

  // ── Session lifecycle (Python parity) ────────────────────────────

  /**
   * Return the given {@code callId}, or mint a new URL-safe session identifier when none is
   * supplied. Mirrors the reference's stateless {@code create_session}: the SDK does not persist
   * sessions, it just resolves/creates an identifier callers thread through subsequent operations.
   */
  public String createSession(String callId) {
    if (callId != null && !callId.isEmpty()) {
      return callId;
    }
    // secrets.token_urlsafe(16): 16 random bytes, URL-safe Base64 without padding.
    byte[] buf = new byte[16];
    new SecureRandom().nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }

  /** Mint a new session identifier (no existing call id). */
  public String createSession() {
    return createSession(null);
  }

  /**
   * Legacy lifecycle hook retained for reference parity. The manager is stateless with respect to
   * activation, so this always reports success.
   */
  public boolean activateSession(String callId) {
    return true;
  }

  /**
   * Legacy lifecycle hook retained for reference parity. Clears any metadata accumulated for the
   * session and reports success.
   */
  public boolean endSession(String callId) {
    if (callId != null) {
      sessionMetadata.remove(callId);
    }
    return true;
  }

  /**
   * Fetch the metadata stored for {@code callId}. The reference is stateless and always returns an
   * empty map; this port keeps a real per-session store so the getter/setter pair round-trips, but
   * still returns an empty (never null) map for unknown sessions. Returns a copy so callers cannot
   * mutate the internal store.
   */
  public Map<String, Object> getSessionMetadata(String callId) {
    Map<String, Object> stored = callId == null ? null : sessionMetadata.get(callId);
    return stored == null ? new LinkedHashMap<>() : new LinkedHashMap<>(stored);
  }

  /**
   * Store a single {@code key}/{@code value} pair in {@code callId}'s metadata, merging with
   * anything already recorded for that session. Signature mirrors the reference's {@code
   * set_session_metadata(call_id, key, value)}.
   */
  public boolean setSessionMetadata(String callId, String key, Object value) {
    if (callId == null) {
      return true;
    }
    sessionMetadata.computeIfAbsent(callId, k -> new ConcurrentHashMap<>()).put(key, value);
    return true;
  }

  /** Enable/disable token-internals decoding in {@link #debugToken(String)} (off by default). */
  public void setDebugMode(boolean debugMode) {
    this.debugMode = debugMode;
  }

  /**
   * Decode a token's components for inspection WITHOUT validating it. Requires {@link
   * #setDebugMode(boolean)} to have been set {@code true}; otherwise returns {@code {"error":
   * "debug mode not enabled"}}, matching the reference. Decodes this port's token format ({@code
   * base64(function:call_id:expiry).signature}).
   */
  public Map<String, Object> debugToken(String token) {
    if (!debugMode) {
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("error", "debug mode not enabled");
      return err;
    }
    try {
      int dotIdx = token == null ? -1 : token.indexOf('.');
      if (dotIdx < 0) {
        return malformedDebug(token, 0);
      }
      String encodedPart = token.substring(0, dotIdx);
      String signaturePart = token.substring(dotIdx + 1);
      String payload =
          new String(Base64.getUrlDecoder().decode(encodedPart), StandardCharsets.UTF_8);
      String[] parts = payload.split(":", 0);
      if (parts.length != 3) {
        return malformedDebug(token, parts.length);
      }

      Map<String, Object> status = expiryStatus(parts[2]);
      String expiryDate = (String) status.remove("expiry_date");

      Map<String, Object> components = new LinkedHashMap<>();
      components.put("function", parts[0]);
      components.put("call_id", truncate(parts[1]));
      components.put("expiry", parts[2]);
      components.put("expiry_date", expiryDate);
      components.put("signature", truncate(signaturePart));

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("valid_format", true);
      out.put("components", components);
      out.put("status", status);
      return out;
    } catch (Exception e) {
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("valid_format", false);
      err.put("error", e.getMessage());
      err.put("token_length", token == null ? 0 : token.length());
      return err;
    }
  }

  private static Map<String, Object> malformedDebug(String token, int partsCount) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("valid_format", false);
    out.put("parts_count", partsCount);
    out.put("token_length", token == null ? 0 : token.length());
    return out;
  }

  private static Map<String, Object> expiryStatus(String tokenExpiry) {
    long currentTime = System.currentTimeMillis() / 1000;
    Map<String, Object> status = new LinkedHashMap<>();
    status.put("current_time", currentTime);
    try {
      long expiry = Long.parseLong(tokenExpiry);
      boolean isExpired = expiry < currentTime;
      status.put("is_expired", isExpired);
      status.put("expires_in_seconds", isExpired ? 0L : expiry - currentTime);
      status.put("expiry_date", Instant.ofEpochSecond(expiry).toString());
    } catch (NumberFormatException e) {
      status.put("is_expired", null);
      status.put("expires_in_seconds", null);
      status.put("expiry_date", null);
    }
    return status;
  }

  private static String truncate(String s) {
    if (s == null) {
      return null;
    }
    return s.length() > 8 ? s.substring(0, 8) + "..." : s;
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
