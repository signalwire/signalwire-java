/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core;

import com.signalwire.sdk.logging.Logger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Unified authentication handler supporting multiple auth methods.
 *
 * <p>Java port of the Python reference {@code signalwire.core.auth_handler.AuthHandler}. Provides a
 * clean pattern for Basic Auth, Bearer tokens, and API keys across all SignalWire services. All
 * credential comparisons are timing-safe ({@link MessageDigest#isEqual}).
 *
 * <p>Idiom note: Python's {@code flask_decorator} / {@code get_fastapi_dependency} are
 * framework-bound (Flask / FastAPI). Java has neither; the native equivalents here are (a) {@link
 * #flaskDecorator(RequestHandler)} -- a middleware wrapping a handler so unauthenticated requests
 * get a 401 -- and (b) {@link #getFastapiDependency(boolean)} -- a {@link Function} taking a
 * request header map and returning an {@link AuthResult}. A "request" is modelled
 * framework-neutrally as a {@code Map<String,String>} of header name (case-insensitive) to value.
 */
public class AuthHandler {

  /** A minimal framework-neutral request handler: header map in, {@link Response} out. */
  @FunctionalInterface
  public interface RequestHandler {
    Response handle(Map<String, String> headers);
  }

  /** Basic-auth credential carrier: a decoded username/password pair. */
  public record BasicCredentials(String username, String password) {}

  /** Bearer-token credential carrier: the raw token string. */
  public record BearerCredentials(String credentials) {}

  /** Result of the {@link #getFastapiDependency(boolean)} callable. */
  public record AuthResult(boolean authenticated, String method) {}

  /** A minimal HTTP response tuple used by {@link #flaskDecorator(RequestHandler)}. */
  public record Response(int status, Map<String, String> headers, String body) {}

  /** Thrown by the {@link #getFastapiDependency(boolean)} callable when required auth fails. */
  public static class AuthException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final transient Response response;

    public AuthException(Response response) {
      super("Invalid authentication credentials");
      this.response = response;
    }

    public Response getResponse() {
      return response;
    }
  }

  private final Logger log = Logger.getLogger("auth_handler");
  private final SecurityConfig securityConfig;
  private final Map<String, Map<String, Object>> authMethods = new LinkedHashMap<>();

  /** Initialize the auth handler with a {@link SecurityConfig}. */
  public AuthHandler(SecurityConfig securityConfig) {
    this.securityConfig = securityConfig;
    setupAuthMethods();
  }

  public SecurityConfig getSecurityConfig() {
    return securityConfig;
  }

  /** Verify basic-auth credentials. Timing-safe. */
  public boolean verifyBasicAuth(BasicCredentials credentials) {
    Map<String, Object> basic = authMethods.get("basic");
    if (basic == null || !Boolean.TRUE.equals(basic.get("enabled")) || credentials == null) {
      return false;
    }
    return secureCompare(credentials.username(), (String) basic.get("username"))
        && secureCompare(credentials.password(), (String) basic.get("password"));
  }

  /** Verify a bearer token. Timing-safe. */
  public boolean verifyBearerToken(BearerCredentials credentials) {
    Map<String, Object> bearer = authMethods.get("bearer");
    if (bearer == null || !Boolean.TRUE.equals(bearer.get("enabled")) || credentials == null) {
      return false;
    }
    return secureCompare(credentials.credentials(), (String) bearer.get("token"));
  }

  /** Verify an API key. Timing-safe. */
  public boolean verifyApiKey(String apiKey) {
    Map<String, Object> apiKeyCfg = authMethods.get("api_key");
    if (apiKeyCfg == null || !Boolean.TRUE.equals(apiKeyCfg.get("enabled"))) {
      return false;
    }
    return secureCompare(apiKey, (String) apiKeyCfg.get("key"));
  }

  /**
   * Native equivalent of Python's FastAPI dependency. Returns a callable taking a request header
   * map and returning an {@link AuthResult}. When {@code optional} is false and authentication
   * fails, the callable throws {@link AuthException}; when true it returns the (unauthenticated)
   * result.
   */
  public Function<Map<String, String>, AuthResult> getFastapiDependency(boolean optional) {
    return headers -> {
      String method = authenticate(headers);
      boolean authenticated = method != null;
      if (!authenticated && !optional) {
        throw new AuthException(unauthorizedResponse());
      }
      return new AuthResult(authenticated, method);
    };
  }

  /** Convenience: {@code getFastapiDependency(false)}. */
  public Function<Map<String, String>, AuthResult> getFastapiDependency() {
    return getFastapiDependency(false);
  }

  /**
   * Native equivalent of Python's Flask decorator. Given a downstream {@link RequestHandler},
   * returns a wrapping handler that enforces authentication: authenticated requests pass through,
   * others get an HTTP 401 with a WWW-Authenticate challenge.
   */
  public RequestHandler flaskDecorator(RequestHandler app) {
    return headers -> {
      if (authenticate(headers) != null) {
        return app.handle(headers);
      }
      logAuthFailure(headers);
      return unauthorizedResponse();
    };
  }

  /** Get information about configured auth methods (never includes secrets). */
  public Map<String, Object> getAuthInfo() {
    Map<String, Object> info = new LinkedHashMap<>();
    if (enabled("basic")) {
      Map<String, Object> basic = new LinkedHashMap<>();
      basic.put("enabled", true);
      basic.put("username", authMethods.get("basic").get("username"));
      info.put("basic", basic);
    }
    if (enabled("bearer")) {
      Map<String, Object> bearer = new LinkedHashMap<>();
      bearer.put("enabled", true);
      bearer.put("hint", "Use Authorization: Bearer <token>");
      info.put("bearer", bearer);
    }
    if (enabled("api_key")) {
      String header = (String) authMethods.get("api_key").get("header");
      Map<String, Object> apiKey = new LinkedHashMap<>();
      apiKey.put("enabled", true);
      apiKey.put("header", header);
      apiKey.put("hint", "Use " + header + ": <key>");
      info.put("api_key", apiKey);
    }
    return info;
  }

  // ------------------------------------------------------------------
  // Internal.
  // ------------------------------------------------------------------

  private void setupAuthMethods() {
    String[] basic = securityConfig.getBasicAuth();
    Map<String, Object> basicMethod = new LinkedHashMap<>();
    basicMethod.put("enabled", true);
    basicMethod.put("username", basic[0]);
    basicMethod.put("password", basic[1]);
    authMethods.put("basic", basicMethod);

    String bearerToken = configAttr("bearerToken");
    if (bearerToken != null) {
      Map<String, Object> bearer = new LinkedHashMap<>();
      bearer.put("enabled", true);
      bearer.put("token", bearerToken);
      authMethods.put("bearer", bearer);
    }

    String apiKey = configAttr("apiKey");
    if (apiKey != null) {
      String header = configAttr("apiKeyHeader");
      if (header == null) {
        header = "X-API-Key";
      }
      Map<String, Object> apiKeyMethod = new LinkedHashMap<>();
      apiKeyMethod.put("enabled", true);
      apiKeyMethod.put("key", apiKey);
      apiKeyMethod.put("header", header);
      authMethods.put("api_key", apiKeyMethod);
    }
  }

  /**
   * Optional reader lookup by getter name (bearerToken / apiKey / apiKeyHeader). {@link
   * SecurityConfig} does not expose these today; a subclass or duck-typed config may. Returns
   * {@code null} when absent, so bearer/api-key auth stay disabled.
   */
  private String configAttr(String getterProperty) {
    String getter =
        "get" + Character.toUpperCase(getterProperty.charAt(0)) + getterProperty.substring(1);
    try {
      var m = securityConfig.getClass().getMethod(getter);
      Object v = m.invoke(securityConfig);
      return v == null ? null : String.valueOf(v);
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  /**
   * Try each configured auth method against a header map. Returns the method name, or {@code null}.
   */
  private String authenticate(Map<String, String> headers) {
    if (bearerOk(headers)) {
      return "bearer";
    }
    if (apiKeyOk(headers)) {
      return "api_key";
    }
    if (basicOk(headers)) {
      return "basic";
    }
    return null;
  }

  private boolean bearerOk(Map<String, String> headers) {
    if (!enabled("bearer")) {
      return false;
    }
    String header = str(header(headers, "Authorization"));
    if (!header.startsWith("Bearer ")) {
      return false;
    }
    return verifyBearerToken(new BearerCredentials(header.substring(7)));
  }

  private boolean apiKeyOk(Map<String, String> headers) {
    if (!enabled("api_key")) {
      return false;
    }
    String headerName = (String) authMethods.get("api_key").get("header");
    String key = header(headers, headerName);
    return key != null && verifyApiKey(key);
  }

  private boolean basicOk(Map<String, String> headers) {
    if (!enabled("basic")) {
      return false;
    }
    BasicCredentials creds = parseBasicAuth(str(header(headers, "Authorization")));
    return creds != null && verifyBasicAuth(creds);
  }

  private BasicCredentials parseBasicAuth(String header) {
    if (!header.startsWith("Basic ")) {
      return null;
    }
    try {
      byte[] decoded = Base64.getDecoder().decode(header.substring(6).trim());
      String pair = new String(decoded, StandardCharsets.UTF_8);
      int idx = pair.indexOf(':');
      String user = idx >= 0 ? pair.substring(0, idx) : pair;
      String pass = idx >= 0 ? pair.substring(idx + 1) : "";
      return new BasicCredentials(user, pass);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private boolean enabled(String method) {
    Map<String, Object> m = authMethods.get(method);
    return m != null && Boolean.TRUE.equals(m.get("enabled"));
  }

  private static boolean secureCompare(String lhs, String rhs) {
    byte[] a = str(lhs).getBytes(StandardCharsets.UTF_8);
    byte[] b = str(rhs).getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(a, b);
  }

  private static String header(Map<String, String> headers, String name) {
    if (headers == null) {
      return null;
    }
    String direct = headers.get(name);
    if (direct != null) {
      return direct;
    }
    for (Map.Entry<String, String> e : headers.entrySet()) {
      if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
        return e.getValue();
      }
    }
    return null;
  }

  private Response unauthorizedResponse() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("content-type", "text/plain");
    headers.put("www-authenticate", "Basic realm=\"SignalWire Service\"");
    return new Response(401, headers, "Authentication required");
  }

  private void logAuthFailure(Map<String, String> headers) {
    log.warn(
        "auth_failed ip=%s method=%s path=%s",
        str(header(headers, "X-Forwarded-For")),
        str(header(headers, "X-Request-Method")),
        str(header(headers, "X-Request-Path")));
  }

  private static String str(String v) {
    return v != null ? v : "";
  }

  @SuppressWarnings("unused")
  private static String lower(String v) {
    return v == null ? "" : v.toLowerCase(Locale.ROOT);
  }
}
