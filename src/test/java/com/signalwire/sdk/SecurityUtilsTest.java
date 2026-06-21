/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.security.SecurityUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SecurityUtils} — parity with the Python reference's {@code
 * signalwire.core.security.security_utils} (filter_sensitive_headers, redact_url,
 * is_valid_hostname).
 */
class SecurityUtilsTest {

  // ------------------------------------------------------------------
  // filterSensitiveHeaders
  // ------------------------------------------------------------------

  @Test
  void filterRemovesSensitiveHeadersCaseInsensitively() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Authorization", "Bearer secret");
    headers.put("Cookie", "session=abc");
    headers.put("X-API-Key", "key123");
    headers.put("Proxy-Authorization", "Basic creds");
    headers.put("Set-Cookie", "a=b");
    headers.put("Content-Type", "application/json");
    headers.put("X-Request-Id", "req-1");

    Map<String, String> filtered = SecurityUtils.filterSensitiveHeaders(headers);

    // Sensitive (any casing) removed.
    assertFalse(filtered.containsKey("Authorization"));
    assertFalse(filtered.containsKey("Cookie"));
    assertFalse(filtered.containsKey("X-API-Key"));
    assertFalse(filtered.containsKey("Proxy-Authorization"));
    assertFalse(filtered.containsKey("Set-Cookie"));

    // Non-sensitive preserved, with original casing and value.
    assertEquals(2, filtered.size());
    assertEquals("application/json", filtered.get("Content-Type"));
    assertEquals("req-1", filtered.get("X-Request-Id"));
  }

  @Test
  void filterReturnsNewEmptyMapForNullAndEmpty() {
    assertTrue(SecurityUtils.filterSensitiveHeaders(null).isEmpty());
    assertTrue(SecurityUtils.filterSensitiveHeaders(Map.of()).isEmpty());
  }

  @Test
  void filterDoesNotMutateInput() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("authorization", "secret");
    headers.put("content-type", "text/plain");

    SecurityUtils.filterSensitiveHeaders(headers);

    // Original map untouched.
    assertEquals(2, headers.size());
    assertTrue(headers.containsKey("authorization"));
  }

  // ------------------------------------------------------------------
  // redactUrl
  // ------------------------------------------------------------------

  @Test
  void redactMasksPasswordInUserinfo() {
    assertEquals(
        "https://user:****@host/path", SecurityUtils.redactUrl("https://user:secret@host/path"));
  }

  @Test
  void redactLeavesUrlWithoutCredentialsUnchanged() {
    assertEquals("https://host/path", SecurityUtils.redactUrl("https://host/path"));
    assertEquals("https://host:8080/path", SecurityUtils.redactUrl("https://host:8080/path"));
  }

  @Test
  void redactReturnsNullAsIs() {
    assertNull(SecurityUtils.redactUrl(null));
  }

  // ------------------------------------------------------------------
  // isValidHostname
  // ------------------------------------------------------------------

  @Test
  void hostnameAcceptsPlainHosts() {
    assertTrue(SecurityUtils.isValidHostname("example.com"));
    assertTrue(SecurityUtils.isValidHostname("sub.example.com"));
    assertTrue(SecurityUtils.isValidHostname("localhost"));
    assertTrue(SecurityUtils.isValidHostname("192.168.1.1"));
  }

  @Test
  void hostnameRejectsEmpty() {
    assertFalse(SecurityUtils.isValidHostname(""));
    assertFalse(SecurityUtils.isValidHostname(null));
  }

  @Test
  void hostnameRejectsWhitespaceSlashesAndControlChars() {
    assertFalse(SecurityUtils.isValidHostname("bad host"));
    assertFalse(SecurityUtils.isValidHostname("host/path"));
    assertFalse(SecurityUtils.isValidHostname("host\\path"));
    assertFalse(SecurityUtils.isValidHostname("host\tname"));
    assertFalse(SecurityUtils.isValidHostname("host\nname"));
    // Control characters: NUL (0x00) and DEL (0x7f).
    assertFalse(SecurityUtils.isValidHostname("host\u0000name"));
    assertFalse(SecurityUtils.isValidHostname("host\u007fname"));
  }
}
