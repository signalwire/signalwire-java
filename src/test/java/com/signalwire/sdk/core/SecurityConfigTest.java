/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signalwire.sdk.core.SecurityConfig.ValidationResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real-behavior tests for {@link SecurityConfig} (parity with Python
 * security_config.SecurityConfig).
 *
 * <p>Java {@code System.getenv} cannot be mutated at runtime, so environment-driven paths are
 * exercised via config-file-driven security sections (same fields, deterministic in CI).
 */
class SecurityConfigTest {

  private Path writeConfig(Path dir, String json) throws IOException {
    Path p = dir.resolve("sec_config.json");
    Files.write(p, json.getBytes(StandardCharsets.UTF_8));
    return p;
  }

  @Test
  void secureDefaults() {
    SecurityConfig cfg = new SecurityConfig();

    assertFalse(cfg.isSslEnabled());
    assertEquals(List.of("*"), cfg.getAllowedHosts());
    assertEquals(List.of("*"), cfg.getCorsOrigins());
    assertEquals(60, cfg.getRateLimit());
    assertTrue(cfg.isUseHsts());
    assertEquals("http", cfg.getUrlScheme());
  }

  @Test
  void sslEnabledFromConfigChangesScheme(@TempDir Path dir) throws IOException {
    Path p = writeConfig(dir, "{\"security\": {\"ssl_enabled\": true}}");
    SecurityConfig cfg = new SecurityConfig(p.toString(), null);

    assertTrue(cfg.isSslEnabled());
    assertEquals("https", cfg.getUrlScheme());
  }

  @Test
  void allowedHostsParsedAsList(@TempDir Path dir) throws IOException {
    Path p = writeConfig(dir, "{\"security\": {\"allowed_hosts\": \"a.com, b.com ,c.com\"}}");
    SecurityConfig cfg = new SecurityConfig(p.toString(), null);

    assertEquals(List.of("a.com", "b.com", "c.com"), cfg.getAllowedHosts());
    assertTrue(cfg.shouldAllowHost("b.com"));
    assertFalse(cfg.shouldAllowHost("evil.com"));
  }

  @Test
  void wildcardHostAllowsAll() {
    SecurityConfig cfg = new SecurityConfig();
    assertTrue(cfg.shouldAllowHost("anything.example"));
  }

  @Test
  void securityHeaders() {
    Map<String, String> headers = new SecurityConfig().getSecurityHeaders();

    assertEquals("nosniff", headers.get("X-Content-Type-Options"));
    assertEquals("DENY", headers.get("X-Frame-Options"));
    assertFalse(headers.containsKey("Strict-Transport-Security"));
  }

  @Test
  void hstsHeaderWhenHttps() {
    Map<String, String> headers = new SecurityConfig().getSecurityHeaders(true);
    assertEquals("max-age=31536000; includeSubDomains", headers.get("Strict-Transport-Security"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void corsConfig(@TempDir Path dir) throws IOException {
    Path p = writeConfig(dir, "{\"security\": {\"cors_origins\": \"https://app.example\"}}");
    SecurityConfig cfg = new SecurityConfig(p.toString(), null);
    Map<String, Object> cors = cfg.getCorsConfig();

    assertEquals(List.of("https://app.example"), (List<String>) cors.get("allow_origins"));
    assertEquals(true, cors.get("allow_credentials"));
    assertEquals(List.of("*"), (List<String>) cors.get("allow_methods"));
  }

  @Test
  void basicAuthFromConfig(@TempDir Path dir) throws IOException {
    Path p =
        writeConfig(
            dir,
            "{\"security\": {\"auth\": {\"basic\": {\"user\": \"alice\", \"password\": \"wonderland\"}}}}");
    SecurityConfig cfg = new SecurityConfig(p.toString(), null);

    assertArrayEquals(new String[] {"alice", "wonderland"}, cfg.getBasicAuth());
  }

  @Test
  void basicAuthAutogeneratesPassword() {
    SecurityConfig cfg = new SecurityConfig();
    String[] creds = cfg.getBasicAuth();

    assertEquals("signalwire", creds[0]);
    assertNotNull(creds[1]);
    assertFalse(creds[1].isEmpty());
    // stable across calls
    assertArrayEquals(creds, cfg.getBasicAuth());
  }

  @Test
  void validateSslConfigMissingCert(@TempDir Path dir) throws IOException {
    Path p = writeConfig(dir, "{\"security\": {\"ssl_enabled\": true}}");
    SecurityConfig cfg = new SecurityConfig(p.toString(), null);
    ValidationResult result = cfg.validateSslConfig();

    assertFalse(result.valid());
    assertTrue(result.error().contains("SWML_SSL_CERT_PATH"));
  }

  @Test
  void validateSslConfigValidWhenDisabled() {
    ValidationResult result = new SecurityConfig().validateSslConfig();

    assertTrue(result.valid());
    assertNull(result.error());
  }

  @Test
  void sslContextKwargsEmptyWhenDisabled() {
    assertTrue(new SecurityConfig().getSslContextKwargs().isEmpty());
  }

  @Test
  void sslContextKwargsWithRealCertPaths(@TempDir Path dir) throws IOException {
    Path cert = dir.resolve("cert.pem");
    Path key = dir.resolve("key.pem");
    Files.write(cert, "cert".getBytes(StandardCharsets.UTF_8));
    Files.write(key, "key".getBytes(StandardCharsets.UTF_8));
    Path p =
        writeConfig(
            dir,
            "{\"security\": {\"ssl_enabled\": true, \"ssl_cert_path\": \""
                + cert.toString().replace("\\", "\\\\")
                + "\", \"ssl_key_path\": \""
                + key.toString().replace("\\", "\\\\")
                + "\"}}");
    SecurityConfig cfg = new SecurityConfig(p.toString(), null);
    Map<String, Object> kwargs = cfg.getSslContextKwargs();

    assertEquals(true, kwargs.get("ssl_enabled"));
    assertEquals(cert.toString(), kwargs.get("cert_path"));
    assertEquals(key.toString(), kwargs.get("key_path"));
  }

  @Test
  void logConfigDoesNotThrow() {
    new SecurityConfig().logConfig("test-service");
  }
}
