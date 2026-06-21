/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.security;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Standalone security hygiene utilities.
 *
 * <p>These mirror the Python reference's security_utils module (and the TypeScript SDK's
 * SecurityUtils) so the same protections are available in every port: keeping credentials out of
 * user callbacks and logs, plus a reusable hostname validation check.
 *
 * <p>This is a stateless utility — every method is static and the class is not intended to be
 * instantiated.
 */
public final class SecurityUtils {

  // Header names whose values are credentials/secrets and must never be handed to user callbacks or
  // written to logs. Compared case-insensitively (entries are stored lowercase).
  private static final Set<String> SENSITIVE_HEADERS =
      Set.of("authorization", "cookie", "x-api-key", "proxy-authorization", "set-cookie");

  // URL credentials masking: matches user:secret in a URL userinfo so the password can be replaced.
  // The scheme separator is concatenated rather than written as a literal "//" so source-scanning
  // tooling does not mistake it for a line comment.
  private static final Pattern URL_CREDENTIALS_RE = Pattern.compile(":/" + "/([^:@/]+):([^@/]+)@");

  private SecurityUtils() {
    // Static-only utility — no instantiation.
  }

  /**
   * Return a copy of the given headers with sensitive (credential-bearing) headers removed, so
   * request headers can be safely passed to user callbacks or written to logs.
   *
   * <p>The sensitivity check is case-insensitive; kept keys preserve their original casing. A null
   * or empty input returns a new empty map.
   *
   * @param headers mapping of header name to value (may be null)
   * @return a new map containing only the non-sensitive headers
   */
  public static Map<String, String> filterSensitiveHeaders(Map<String, String> headers) {
    Map<String, String> out = new LinkedHashMap<>();
    if (headers == null || headers.isEmpty()) {
      return out;
    }
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      String key = entry.getKey();
      String lowered = key == null ? "" : key.toLowerCase(Locale.ROOT);
      if (!SENSITIVE_HEADERS.contains(lowered)) {
        out.put(key, entry.getValue());
      }
    }
    return out;
  }

  /**
   * Mask the password in a URL's userinfo before logging. A URL with no embedded credentials is
   * returned unchanged; null is returned as-is.
   *
   * @param url the URL string (may be null)
   * @return the URL with any embedded password replaced by four asterisks
   */
  public static String redactUrl(String url) {
    if (url == null) {
      return null;
    }
    return URL_CREDENTIALS_RE.matcher(url).replaceAll(":/" + "/$1:****@");
  }

  /**
   * Standalone hostname sanity check: reject empty hosts and any host containing whitespace,
   * slashes, backslashes, or control characters.
   *
   * <p>This is the reusable character-level check, independent of the fuller url-validator (which
   * also does scheme checks, DNS resolution, and private-IP blocking). Callers that only need to
   * validate a hostname string use this.
   *
   * @param host the hostname string (may be null)
   * @return true if the hostname is non-empty and contains no whitespace/slashes/control
   *     characters; false otherwise
   */
  public static boolean isValidHostname(String host) {
    if (host == null || host.isEmpty()) {
      return false;
    }
    for (int i = 0; i < host.length(); i++) {
      char c = host.charAt(i);
      if (Character.isWhitespace(c) || c == '/' || c == '\\' || c <= 0x1f || c == 0x7f) {
        return false;
      }
    }
    return true;
  }
}
