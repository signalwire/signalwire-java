/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core;

import com.signalwire.sdk.logging.Logger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unified security configuration for SignalWire services.
 *
 * <p>Java port of the Python reference {@code signalwire.core.security_config.SecurityConfig}.
 * Provides centralized security settings (SSL, allowed hosts, CORS, security headers, basic auth)
 * consumed by the web/agent services, ensuring consistent behavior. Defaults are applied first,
 * then environment variables (backward compatibility), then a config file if available (highest
 * priority).
 */
public class SecurityConfig {

  // Security environment variable names.
  private static final String SSL_ENABLED = "SWML_SSL_ENABLED";
  private static final String SSL_CERT_PATH = "SWML_SSL_CERT_PATH";
  private static final String SSL_KEY_PATH = "SWML_SSL_KEY_PATH";
  private static final String SSL_DOMAIN = "SWML_DOMAIN";
  private static final String SSL_VERIFY_MODE = "SWML_SSL_VERIFY_MODE";

  private static final String ALLOWED_HOSTS = "SWML_ALLOWED_HOSTS";
  private static final String CORS_ORIGINS = "SWML_CORS_ORIGINS";
  private static final String MAX_REQUEST_SIZE = "SWML_MAX_REQUEST_SIZE";
  private static final String RATE_LIMIT = "SWML_RATE_LIMIT";
  private static final String REQUEST_TIMEOUT = "SWML_REQUEST_TIMEOUT";
  private static final String USE_HSTS = "SWML_USE_HSTS";
  private static final String HSTS_MAX_AGE = "SWML_HSTS_MAX_AGE";

  private static final String BASIC_AUTH_USER = "SWML_BASIC_AUTH_USER";
  private static final String BASIC_AUTH_PASSWORD = "SWML_BASIC_AUTH_PASSWORD";

  private static final String DEFAULT_VERIFY_MODE = "CERT_REQUIRED";
  private static final long DEFAULT_MAX_REQUEST_SIZE = 10L * 1024 * 1024;
  private static final int DEFAULT_RATE_LIMIT = 60;
  private static final int DEFAULT_REQUEST_TIMEOUT = 30;
  private static final long DEFAULT_HSTS_MAX_AGE = 31_536_000L;

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final Logger log = Logger.getLogger("security_config");

  private boolean sslEnabled;
  private String sslCertPath;
  private String sslKeyPath;
  private String domain;
  private String sslVerifyMode;
  private List<String> allowedHosts;
  private List<String> corsOrigins;
  private long maxRequestSize;
  private int rateLimit;
  private int requestTimeout;
  private boolean useHsts;
  private long hstsMaxAge;
  private String basicAuthUser;
  private String basicAuthPassword;
  private boolean basicAuthAutogenWarned;

  /** Initialize with defaults + environment (no config file). */
  public SecurityConfig() {
    this(null, null);
  }

  /**
   * Initialize security configuration.
   *
   * @param configFile optional explicit config file path
   * @param serviceName optional service name used to locate a config file
   */
  public SecurityConfig(String configFile, String serviceName) {
    setDefaults();
    loadFromEnv();
    loadConfigFile(configFile, serviceName);
  }

  /** Load configuration from environment variables. */
  public final void loadFromEnv() {
    loadSslFromEnv();
    loadHostsFromEnv();
    loadHstsFromEnv();
    this.basicAuthUser = System.getenv(BASIC_AUTH_USER);
    this.basicAuthPassword = System.getenv(BASIC_AUTH_PASSWORD);
  }

  /**
   * Validate SSL configuration. Returns a {@link ValidationResult} whose {@code valid} is {@code
   * false} and {@code error} carries the message when misconfigured.
   */
  public ValidationResult validateSslConfig() {
    if (!sslEnabled) {
      return ValidationResult.ok();
    }
    if (sslCertPath == null) {
      return ValidationResult.error("SSL enabled but SWML_SSL_CERT_PATH not set");
    }
    if (sslKeyPath == null) {
      return ValidationResult.error("SSL enabled but SWML_SSL_KEY_PATH not set");
    }
    if (!Files.exists(Paths.get(sslCertPath))) {
      return ValidationResult.error("SSL certificate file not found: " + sslCertPath);
    }
    if (!Files.exists(Paths.get(sslKeyPath))) {
      return ValidationResult.error("SSL key file not found: " + sslKeyPath);
    }
    return ValidationResult.ok();
  }

  /**
   * Get SSL options for binding an HTTPS server. Returns an empty map when SSL is disabled or the
   * configuration fails validation, otherwise a neutral map with keys {@code ssl_enabled}, {@code
   * cert_path}, {@code key_path} for the web service to consume.
   *
   * <p>Java idiom note: Python returns uvicorn {@code ssl_certfile}/{@code ssl_keyfile} kwargs;
   * Java returns a language-neutral option map.
   */
  public Map<String, Object> getSslContextKwargs() {
    Map<String, Object> out = new LinkedHashMap<>();
    if (!sslEnabled) {
      return out;
    }
    ValidationResult result = validateSslConfig();
    if (!result.valid()) {
      log.error("ssl_validation_failed error=" + result.error());
      return out;
    }
    out.put("ssl_enabled", true);
    out.put("cert_path", sslCertPath);
    out.put("key_path", sslKeyPath);
    return out;
  }

  /**
   * Get basic auth credentials, generating a random URL-safe password if not set. Returns a
   * two-element {@code String[]{username, password}}.
   */
  public String[] getBasicAuth() {
    String username = basicAuthUser != null ? basicAuthUser : "signalwire";
    if (basicAuthPassword == null || basicAuthPassword.isEmpty()) {
      byte[] raw = new byte[32];
      SECURE_RANDOM.nextBytes(raw);
      this.basicAuthPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
      warnBasicAuthAutogen(username);
    }
    return new String[] {username, basicAuthPassword};
  }

  /**
   * Get security headers to add to responses. When {@code isHttps} is true and HSTS is enabled, a
   * Strict-Transport-Security header is included.
   */
  public Map<String, String> getSecurityHeaders(boolean isHttps) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-Content-Type-Options", "nosniff");
    headers.put("X-Frame-Options", "DENY");
    headers.put("X-XSS-Protection", "1; mode=block");
    headers.put("Referrer-Policy", "strict-origin-when-cross-origin");
    if (isHttps && useHsts) {
      headers.put("Strict-Transport-Security", "max-age=" + hstsMaxAge + "; includeSubDomains");
    }
    return headers;
  }

  /** Convenience: {@code getSecurityHeaders(false)}. */
  public Map<String, String> getSecurityHeaders() {
    return getSecurityHeaders(false);
  }

  /** Check if a host is allowed ({@code *} in the allowed list allows all). */
  public boolean shouldAllowHost(String host) {
    if (allowedHosts.contains("*")) {
      return true;
    }
    return allowedHosts.contains(host);
  }

  /** Get CORS configuration. */
  public Map<String, Object> getCorsConfig() {
    Map<String, Object> cors = new LinkedHashMap<>();
    cors.put("allow_origins", corsOrigins);
    cors.put("allow_credentials", true);
    cors.put("allow_methods", List.of("*"));
    cors.put("allow_headers", List.of("*"));
    return cors;
  }

  /** Get the URL scheme based on SSL configuration. */
  public String getUrlScheme() {
    return sslEnabled ? "https" : "http";
  }

  /** Log the current security configuration (never logs secrets). */
  public void logConfig(String serviceName) {
    boolean hasBasicAuth = basicAuthUser != null && basicAuthPassword != null;
    log.info(
        "security_config_loaded service=%s ssl_enabled=%s domain=%s allowed_hosts=%s "
            + "cors_origins=%s max_request_size=%s rate_limit=%s use_hsts=%s has_basic_auth=%s",
        serviceName,
        sslEnabled,
        String.valueOf(domain),
        allowedHosts,
        corsOrigins,
        maxRequestSize,
        rateLimit,
        useHsts,
        hasBasicAuth);
  }

  // ------------------------------------------------------------------
  // Accessors.
  // ------------------------------------------------------------------

  public boolean isSslEnabled() {
    return sslEnabled;
  }

  public String getSslCertPath() {
    return sslCertPath;
  }

  public String getSslKeyPath() {
    return sslKeyPath;
  }

  public String getDomain() {
    return domain;
  }

  public String getSslVerifyMode() {
    return sslVerifyMode;
  }

  public List<String> getAllowedHosts() {
    return allowedHosts;
  }

  public List<String> getCorsOrigins() {
    return corsOrigins;
  }

  public long getMaxRequestSize() {
    return maxRequestSize;
  }

  public int getRateLimit() {
    return rateLimit;
  }

  public int getRequestTimeout() {
    return requestTimeout;
  }

  public boolean isUseHsts() {
    return useHsts;
  }

  public long getHstsMaxAge() {
    return hstsMaxAge;
  }

  public String getBasicAuthUser() {
    return basicAuthUser;
  }

  public String getBasicAuthPassword() {
    return basicAuthPassword;
  }

  // ------------------------------------------------------------------
  // Internal.
  // ------------------------------------------------------------------

  private void setDefaults() {
    this.sslEnabled = false;
    this.sslCertPath = null;
    this.sslKeyPath = null;
    this.domain = null;
    this.sslVerifyMode = DEFAULT_VERIFY_MODE;
    this.allowedHosts = parseList("*");
    this.corsOrigins = parseList("*");
    this.maxRequestSize = DEFAULT_MAX_REQUEST_SIZE;
    this.rateLimit = DEFAULT_RATE_LIMIT;
    this.requestTimeout = DEFAULT_REQUEST_TIMEOUT;
    this.useHsts = true;
    this.hstsMaxAge = DEFAULT_HSTS_MAX_AGE;
    this.basicAuthUser = null;
    this.basicAuthPassword = null;
  }

  private void loadSslFromEnv() {
    String enabled = str(System.getenv(SSL_ENABLED)).toLowerCase(Locale.ROOT);
    this.sslEnabled = "true".equals(enabled) || "1".equals(enabled) || "yes".equals(enabled);
    this.sslCertPath = System.getenv(SSL_CERT_PATH);
    this.sslKeyPath = System.getenv(SSL_KEY_PATH);
    this.domain = System.getenv(SSL_DOMAIN);
    String verify = System.getenv(SSL_VERIFY_MODE);
    this.sslVerifyMode = verify != null ? verify : DEFAULT_VERIFY_MODE;
  }

  private void loadHostsFromEnv() {
    this.allowedHosts = parseList(envOr(ALLOWED_HOSTS, "*"));
    this.corsOrigins = parseList(envOr(CORS_ORIGINS, "*"));
    this.maxRequestSize = parseLong(System.getenv(MAX_REQUEST_SIZE), DEFAULT_MAX_REQUEST_SIZE);
    this.rateLimit = (int) parseLong(System.getenv(RATE_LIMIT), DEFAULT_RATE_LIMIT);
    this.requestTimeout = (int) parseLong(System.getenv(REQUEST_TIMEOUT), DEFAULT_REQUEST_TIMEOUT);
  }

  private void loadHstsFromEnv() {
    String useHstsEnv = str(System.getenv(USE_HSTS)).toLowerCase(Locale.ROOT);
    this.useHsts = useHstsEnv.isEmpty() ? true : !"false".equals(useHstsEnv);
    this.hstsMaxAge = parseLong(System.getenv(HSTS_MAX_AGE), DEFAULT_HSTS_MAX_AGE);
  }

  private void loadConfigFile(String configFile, String serviceName) {
    if (configFile == null) {
      configFile = ConfigLoader.findConfigFile(serviceName);
    }
    if (configFile == null) {
      return;
    }
    ConfigLoader loader = new ConfigLoader(List.of(configFile));
    if (!loader.hasConfig()) {
      return;
    }
    Map<String, Object> section = loader.getSection("security");
    if (section == null || section.isEmpty()) {
      return;
    }
    applySecuritySection(section);
  }

  private void applySecuritySection(Map<String, Object> section) {
    if (section.containsKey("ssl_enabled")) {
      this.sslEnabled = truthy(section.get("ssl_enabled"));
    }
    if (section.containsKey("ssl_cert_path")) {
      this.sslCertPath = strOrNull(section.get("ssl_cert_path"));
    }
    if (section.containsKey("ssl_key_path")) {
      this.sslKeyPath = strOrNull(section.get("ssl_key_path"));
    }
    if (section.containsKey("domain")) {
      this.domain = strOrNull(section.get("domain"));
    }
    if (section.containsKey("ssl_verify_mode")) {
      this.sslVerifyMode = strOrNull(section.get("ssl_verify_mode"));
    }
    if (section.containsKey("allowed_hosts")) {
      this.allowedHosts = parseListObject(section.get("allowed_hosts"));
    }
    if (section.containsKey("cors_origins")) {
      this.corsOrigins = parseListObject(section.get("cors_origins"));
    }
    if (section.containsKey("max_request_size")) {
      this.maxRequestSize = parseLong(strOrNull(section.get("max_request_size")), maxRequestSize);
    }
    if (section.containsKey("rate_limit")) {
      this.rateLimit = (int) parseLong(strOrNull(section.get("rate_limit")), rateLimit);
    }
    if (section.containsKey("request_timeout")) {
      this.requestTimeout =
          (int) parseLong(strOrNull(section.get("request_timeout")), requestTimeout);
    }
    if (section.containsKey("use_hsts")) {
      this.useHsts = truthy(section.get("use_hsts"));
    }
    if (section.containsKey("hsts_max_age")) {
      this.hstsMaxAge = parseLong(strOrNull(section.get("hsts_max_age")), hstsMaxAge);
    }
    applyAuthSection(section);
  }

  @SuppressWarnings("unchecked")
  private void applyAuthSection(Map<String, Object> section) {
    Object authObj = section.get("auth");
    if (!(authObj instanceof Map)) {
      return;
    }
    Object basicObj = ((Map<String, Object>) authObj).get("basic");
    if (!(basicObj instanceof Map)) {
      return;
    }
    Map<String, Object> basic = (Map<String, Object>) basicObj;
    if (basic.containsKey("user")) {
      this.basicAuthUser = strOrNull(basic.get("user"));
    }
    if (basic.containsKey("password")) {
      this.basicAuthPassword = strOrNull(basic.get("password"));
    }
  }

  private List<String> parseList(String value) {
    if (value == null) {
      return new ArrayList<>();
    }
    if ("*".equals(value)) {
      List<String> star = new ArrayList<>();
      star.add("*");
      return star;
    }
    List<String> out = new ArrayList<>();
    for (String part : value.split(",")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        out.add(trimmed);
      }
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private List<String> parseListObject(Object value) {
    if (value instanceof List) {
      List<String> out = new ArrayList<>();
      for (Object item : (List<Object>) value) {
        out.add(String.valueOf(item));
      }
      return out;
    }
    return parseList(strOrNull(value));
  }

  private void warnBasicAuthAutogen(String username) {
    if (basicAuthAutogenWarned) {
      return;
    }
    log.warn(
        "basic_auth_password_autogenerated username=%s: no SWML_BASIC_AUTH_PASSWORD in environment "
            + "and no password passed; generated a random password that exists only in this "
            + "process. External callers will get HTTP 401 unless they read it from this process's "
            + "env. Set SWML_BASIC_AUTH_USER / SWML_BASIC_AUTH_PASSWORD to suppress.",
        username);
    basicAuthAutogenWarned = true;
  }

  private static String envOr(String key, String fallback) {
    String v = System.getenv(key);
    return v != null ? v : fallback;
  }

  private static String str(String v) {
    return v != null ? v : "";
  }

  private static String strOrNull(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  private static boolean truthy(Object v) {
    if (v instanceof Boolean) {
      return (Boolean) v;
    }
    if (v == null) {
      return false;
    }
    String s = String.valueOf(v).toLowerCase(Locale.ROOT);
    return "true".equals(s) || "1".equals(s) || "yes".equals(s);
  }

  private static long parseLong(String v, long fallback) {
    if (v == null || v.isEmpty()) {
      return fallback;
    }
    try {
      return Long.parseLong(v.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  /** Result of {@link SecurityConfig#validateSslConfig()}: a validity flag + optional message. */
  public record ValidationResult(boolean valid, String error) {
    static ValidationResult ok() {
      return new ValidationResult(true, null);
    }

    static ValidationResult error(String message) {
      return new ValidationResult(false, message);
    }
  }
}
