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
 * <p>Tokens are self-contained — everything needed to validate one is inside the signed token — so
 * validation is stateless and no server-side session store has to be consulted or replicated. The
 * legacy session-lifecycle methods ({@code createSession} / {@code activateSession} / {@code
 * endSession} / {@code get}/{@code setSessionMetadata}) remain: the metadata getter/setter pair is
 * backed by a real per-call store so it round-trips, while activation is a stateless success hook
 * that keeps no state of its own.
 *
 * <p><b>Token wire format.</b> A minted token is the URL-safe Base64 encoding of the DECODED
 * 5-field, dot-joined string {@code call_id.function_name.expiry.nonce.signature}. The HMAC-SHA256
 * signed message is {@code call_id:function_name:expiry:nonce} (colon-joined, call_id FIRST). The
 * nonce is 16 hex characters (8 random bytes) drawn from {@link SecureRandom}, so two mints for the
 * SAME (function_name, call_id, expiry) produce DIFFERENT tokens. The signature comparison uses
 * {@link MessageDigest#isEqual} (constant-time).
 */
public class SessionManager {

  /** Seconds until a minted token expires when none is specified: 900, i.e. 15 minutes. */
  private static final int DEFAULT_TOKEN_EXPIRY_SECS = 900;

  /**
   * The signing secret, held as a STRING. The HMAC is keyed with this string's UTF-8 BYTES, and the
   * generated default is a 64-character hex string.
   *
   * <p>Holding the string rather than the 32 raw bytes it spells out is load-bearing for
   * interoperability: the ASCII bytes of a hex string and the raw bytes that string decodes to are
   * DIFFERENT HMAC keys, so hex-decoding the secret here would silently produce tokens that no
   * other SignalWire SDK — and no other service sharing this secret — can validate.
   */
  private final String secretKey;

  private final byte[] secret;
  private final int defaultExpiry;
  private final SecureRandom random = new SecureRandom();

  /** Per-session metadata store: call_id -&gt; (key -&gt; value). */
  private final ConcurrentHashMap<String, Map<String, Object>> sessionMetadata =
      new ConcurrentHashMap<>();

  /** When true, {@link #debugToken(String)} decodes token internals; off by default. */
  private volatile boolean debugMode;

  /**
   * A manager whose tokens expire after the default 900 seconds (15 minutes).
   *
   * <p>Note this is DELIBERATELY different from {@link com.signalwire.sdk.agent.AgentBase}, whose
   * own {@code tokenExpirySecs} default is 3600 and is passed through explicitly — an agent's
   * tokens live for an hour, a bare {@code SessionManager}'s for fifteen minutes. Pass {@link
   * #SessionManager(int)} to choose your own lifetime.
   */
  public SessionManager() {
    this(DEFAULT_TOKEN_EXPIRY_SECS);
  }

  public SessionManager(int defaultExpiry) {
    this(defaultExpiry, null);
  }

  /**
   * Construct a manager with an explicit token lifetime and signing secret. A {@code null} secret
   * is generated as a 64-character hex string from 32 {@link SecureRandom} bytes.
   *
   * @param defaultExpiry seconds until minted tokens expire
   * @param secretKey the HMAC signing secret as a string; generated when {@code null}
   */
  public SessionManager(int defaultExpiry, String secretKey) {
    this.defaultExpiry = defaultExpiry;
    this.secretKey = secretKey != null ? secretKey : randomHexSecret();
    this.secret = this.secretKey.getBytes(StandardCharsets.UTF_8);
  }

  /** 32 random bytes rendered as 64 lower-case hex characters. */
  private static String randomHexSecret() {
    byte[] raw = new byte[32];
    new SecureRandom().nextBytes(raw);
    StringBuilder sb = new StringBuilder(raw.length * 2);
    for (byte b : raw) {
      sb.append(Character.forDigit((b >> 4) & 0xF, 16));
      sb.append(Character.forDigit(b & 0xF, 16));
    }
    return sb.toString();
  }

  /**
   * The HMAC signing secret. Readable so a caller that let the constructor generate one can capture
   * it — a token minted with a generated secret is unvalidatable once the manager is discarded.
   *
   * @return the signing secret as a string.
   */
  public String getSecretKey() {
    return secretKey;
  }

  /**
   * Seconds until minted tokens expire, when {@link #createToken(String, String)} is called without
   * an explicit lifetime.
   *
   * @return the default token lifetime in seconds.
   */
  public int getTokenExpirySecs() {
    return defaultExpiry;
  }

  /**
   * Construct a manager with an explicit signing secret supplied as bytes.
   *
   * <p>The bytes are interpreted as the UTF-8 encoding of the secret STRING, which is what keys the
   * HMAC. Passing {@code "abc".getBytes(UTF_8)} here is therefore identical to passing {@code
   * "abc"} to {@link #SessionManager(int, String)}. Prefer the string overload; this one exists for
   * callers already holding the encoded form.
   *
   * @param secretKey the UTF-8 bytes of the signing secret.
   * @param defaultExpiry seconds until minted tokens expire.
   */
  public SessionManager(byte[] secretKey, int defaultExpiry) {
    this(defaultExpiry, new String(secretKey, StandardCharsets.UTF_8));
  }

  /**
   * The raw HMAC signing secret, defensively copied. Package-private so the security tests can
   * hand-assemble a token in the exact wire format, keyed on this manager's secret, and assert it
   * validates here. Not part of the public API.
   */
  byte[] secretBytes() {
    return secret.clone();
  }

  /**
   * HMAC-SHA256 sign {@code data} with this manager's secret, hex-encoded. Package-private so the
   * security tests can build a wire-format token keyed on the same secret.
   */
  String sign(String data) {
    return hmacSign(data);
  }

  /** Create a signed token for a function + callID. */
  public String createToken(String functionName, String callId) {
    return createToken(functionName, callId, defaultExpiry);
  }

  /**
   * Mint a signed SWAIG-function token scoped to one function and one call.
   *
   * <p>The token is HMAC-SHA256 over {@code call_id:function_name:expiry:nonce}, assembled as five
   * dot-separated fields and base64url-encoded WITH its {@code =} padding intact. The padding is
   * NOT cosmetic: a strict base64url decoder rejects input whose padding has been stripped, so a
   * token emitted without it is undecodable by the services that consume it. Never trim the {@code
   * =}.
   *
   * @param functionName the tool the token authorizes.
   * @param callId the call the token is scoped to.
   * @param expirySeconds lifetime from now, in seconds.
   * @return the encoded token.
   */
  public String createToken(String functionName, String callId, int expirySeconds) {
    long expiry = System.currentTimeMillis() / 1000 + expirySeconds;
    String nonce = randomNonce();

    // Signed message: call_id:function_name:expiry:nonce (call_id FIRST).
    String message = callId + ":" + functionName + ":" + expiry + ":" + nonce;
    String signature = hmacSign(message);

    // Decoded token: call_id.function_name.expiry.nonce.signature (5 dot-fields).
    String token = callId + "." + functionName + "." + expiry + "." + nonce + "." + signature;

    // Base64url-encode the whole token for URL safety, PADDING INTACT. The reference mints with
    // `base64.urlsafe_b64encode`, which KEEPS the '=' padding, and validates with
    // `base64.urlsafe_b64decode`, which RAISES on a stripped '='. `.withoutPadding()` therefore
    // made every token this port minted unusable to the reference and to any port that decodes
    // strictly, even though the message and the HMAC were correct. Our own validateToken still
    // accepted them because `Base64.getUrlDecoder()` tolerates missing padding — that asymmetry
    // is why round-tripping against ourselves could not catch it, and why the TOKEN-INTEROP gate
    // validates against the REFERENCE decoder instead.
    return Base64.getUrlEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
  }

  /** Validate a signed token. */
  public boolean validateToken(String token, String functionName, String callId) {
    if (token == null || token.isEmpty()) return false;

    String decoded;
    try {
      decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
    } catch (Exception e) {
      return false;
    }

    String[] parts = decoded.split("\\.", -1);
    if (parts.length != 5) return false;

    String tokenCallId = parts[0];
    String tokenFunc = parts[1];
    long tokenExpiry;
    try {
      tokenExpiry = Long.parseLong(parts[2]);
    } catch (NumberFormatException e) {
      return false;
    }
    String tokenNonce = parts[3];
    String tokenSignature = parts[4];

    // Reject validation if call_id is not provided (Python parity).
    if (callId == null || callId.isEmpty()) return false;

    // Verify the function matches.
    if (!tokenFunc.equals(functionName)) return false;

    // Check if the token has expired.
    long now = System.currentTimeMillis() / 1000;
    if (tokenExpiry < now) return false;

    // Recreate the message and verify the signature (constant-time compare).
    String message = tokenCallId + ":" + tokenFunc + ":" + tokenExpiry + ":" + tokenNonce;
    String expectedSignature = hmacSign(message);
    boolean sigMatch =
        MessageDigest.isEqual(
            tokenSignature.getBytes(StandardCharsets.UTF_8),
            expectedSignature.getBytes(StandardCharsets.UTF_8));
    if (!sigMatch) return false;

    // Finally verify the call_id matches (done last so the token is otherwise valid).
    return tokenCallId.equals(callId);
  }

  // ── Python-surface token aliases ─────────────────────────────────
  //
  // The reference exposes generate_token / create_tool_token as the minting
  // names and validate_tool_token as a back-compat alias of validate_token.
  // Java keeps the existing createToken/validateToken names and projects the
  // reference names onto them (matching Ruby's alias set).

  /** Alias of {@link #createToken(String, String)}. */
  public String generateToken(String functionName, String callId) {
    return createToken(functionName, callId);
  }

  /** Alias of {@link #createToken(String, String)}. */
  public String createToolToken(String functionName, String callId) {
    return createToken(functionName, callId);
  }

  /**
   * Back-compat alias of {@link #validateToken(String, String, String)}.
   *
   * <p><strong>Careful: the parameter order differs.</strong> This method takes {@code
   * (functionName, token, callId)} whereas {@link #validateToken} takes {@code (token,
   * functionName, callId)}. Both are {@code String}, so swapping them compiles and simply fails
   * every validation.
   *
   * @param functionName the tool the token must authorize.
   * @param token the encoded token to check.
   * @param callId the call the token must be scoped to.
   * @return {@code true} if the token is valid for this function and call.
   */
  public boolean validateToolToken(String functionName, String token, String callId) {
    return validateToken(token, functionName, callId);
  }

  // ── Session lifecycle (Python parity) ────────────────────────────

  /**
   * Return the given {@code callId}, or mint a new URL-safe session identifier (16 random bytes)
   * when none is supplied. No session is persisted by this call — it only resolves or creates the
   * identifier that callers thread through subsequent operations.
   */
  public String createSession(String callId) {
    if (callId != null && !callId.isEmpty()) {
      return callId;
    }
    // secrets.token_urlsafe(16): 16 random bytes, URL-safe Base64 without padding.
    byte[] buf = new byte[16];
    random.nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }

  /** Mint a new session identifier (no existing call id). */
  public String createSession() {
    return createSession(null);
  }

  /**
   * Legacy lifecycle hook. The manager is stateless with respect to activation, so this always
   * reports success.
   */
  public boolean activateSession(String callId) {
    return true;
  }

  /** Legacy lifecycle hook. Clears any metadata accumulated for the session and reports success. */
  public boolean endSession(String callId) {
    if (callId != null) {
      sessionMetadata.remove(callId);
    }
    return true;
  }

  /**
   * Fetch the metadata stored for {@code callId} by {@link #setSessionMetadata}. Returns an empty
   * (never {@code null}) map for an unknown session, and always a COPY — mutating the returned map
   * does not change what is stored.
   */
  public Map<String, Object> getSessionMetadata(String callId) {
    Map<String, Object> stored = callId == null ? null : sessionMetadata.get(callId);
    return stored == null ? new LinkedHashMap<>() : new LinkedHashMap<>(stored);
  }

  /**
   * Store a single {@code key}/{@code value} pair in {@code callId}'s metadata, merging with
   * anything already recorded for that session. A {@code null} {@code callId} is a no-op that still
   * reports success. {@link #endSession} discards the whole store for a session.
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
   * "debug mode not enabled"}} and decodes nothing. Splits the token format {@code
   * base64url(call_id.function_name.expiry.nonce.signature)} back into its five fields.
   */
  public Map<String, Object> debugToken(String token) {
    if (!debugMode) {
      Map<String, Object> err = new LinkedHashMap<>();
      err.put("error", "debug mode not enabled");
      return err;
    }
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
      String[] parts = decoded.split("\\.", -1);
      if (parts.length != 5) {
        return malformedDebug(token, parts.length);
      }

      String tokenCallId = parts[0];
      String tokenFunc = parts[1];
      String tokenExpiry = parts[2];
      String tokenNonce = parts[3];
      String tokenSignature = parts[4];

      Map<String, Object> status = expiryStatus(tokenExpiry);
      String expiryDate = (String) status.remove("expiry_date");

      Map<String, Object> components = new LinkedHashMap<>();
      components.put("call_id", truncate(tokenCallId));
      components.put("function", tokenFunc);
      components.put("expiry", tokenExpiry);
      components.put("expiry_date", expiryDate);
      components.put("nonce", tokenNonce);
      components.put("signature", truncate(tokenSignature));

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

  /** 16 lower-case hex characters (8 {@link SecureRandom} bytes). */
  private String randomNonce() {
    byte[] buf = new byte[8];
    random.nextBytes(buf);
    StringBuilder hex = new StringBuilder(16);
    for (byte b : buf) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
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
