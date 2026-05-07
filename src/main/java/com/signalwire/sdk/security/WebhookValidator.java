/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Webhook signature validation for SignalWire-signed HTTP requests.
 *
 * <p>Implements both schemes from {@code porting-sdk/webhooks.md}:
 *
 * <ul>
 *   <li><b>Scheme A</b> (RELAY/SWML/JSON): {@code hex(HMAC-SHA1(key, url + rawBody))}.</li>
 *   <li><b>Scheme B</b> (Compat/cXML form): {@code base64(HMAC-SHA1(key, url + sortedFormParams))}
 *       with optional {@code bodySHA256} query-param fallback for JSON-on-compat-surface.</li>
 * </ul>
 *
 * <p>All signature comparisons use {@link MessageDigest#isEqual(byte[], byte[])}
 * (constant-time) so the secret cannot be leaked through timing differences.
 *
 * <p>Public API:
 * <ul>
 *   <li>{@link #validateWebhookSignature(String, String, String, String)} — combined entry point.</li>
 *   <li>{@link #validateRequest(String, String, String, Object)} — legacy
 *       {@code @signalwire/compatibility-api} drop-in alias.</li>
 * </ul>
 *
 * <p>This is a stateless utility — every method is static and the class is
 * not intended to be instantiated.
 */
public final class WebhookValidator {

    /** Header name that carries the signature on every signed SignalWire request. */
    public static final String SIGNALWIRE_SIGNATURE_HEADER = "X-SignalWire-Signature";

    /** Legacy alias accepted by the cXML/Compatibility surface. */
    public static final String TWILIO_COMPAT_SIGNATURE_HEADER = "X-Twilio-Signature";

    private WebhookValidator() {
        // Static-only utility — no instantiation.
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Validate a SignalWire webhook signature against both schemes.
     *
     * <p>Tries Scheme A (hex HMAC-SHA1 over {@code url + rawBody}) first; on
     * miss, tries Scheme B (base64 HMAC-SHA1 over {@code url + sortedFormParams})
     * with both port-normalization variants of the URL and an optional
     * {@code bodySHA256} fallback for JSON-on-compat-surface.
     *
     * @param signingKey customer's Signing Key from the Dashboard. Must be
     *                   non-null and non-empty; otherwise an
     *                   {@link IllegalArgumentException} is thrown — that's a
     *                   programming error, not a validation failure.
     * @param signature  the {@code X-SignalWire-Signature} header value (or the
     *                   {@code X-Twilio-Signature} alias). {@code null} or
     *                   empty returns {@code false} without throwing.
     * @param url        the full URL SignalWire POSTed to (scheme, host,
     *                   optional port, path, query). Must match what the
     *                   platform saw — see the URL reconstruction section
     *                   of {@code porting-sdk/webhooks.md}.
     * @param rawBody    the raw UTF-8 request body bytes as a string,
     *                   <b>before</b> any JSON / form parsing. May be empty
     *                   but must not be {@code null} — pass {@code ""} when
     *                   the body was empty.
     * @return {@code true} when the signature matches either scheme, otherwise
     *         {@code false}.
     * @throws IllegalArgumentException when {@code signingKey} is {@code null}
     *                   or empty, or when {@code rawBody} is {@code null}.
     */
    public static boolean validateWebhookSignature(
            String signingKey,
            String signature,
            String url,
            String rawBody) {
        if (signingKey == null || signingKey.isEmpty()) {
            throw new IllegalArgumentException("signingKey is required");
        }
        if (rawBody == null) {
            throw new IllegalArgumentException(
                    "rawBody must be a String (use \"\" for empty bodies); did you pass parsed JSON by mistake?");
        }
        if (url == null) {
            url = "";
        }
        if (signature == null || signature.isEmpty()) {
            return false;
        }

        // ------------------------------------------------------------
        // Scheme A — RELAY/SWML/JSON: hex(HMAC-SHA1(key, url + rawBody))
        // ------------------------------------------------------------
        String expectedA = hexHmacSha1(signingKey, url + rawBody);
        if (safeEquals(expectedA, signature)) {
            return true;
        }

        // ------------------------------------------------------------
        // Scheme B — Compat/cXML form. Try parsed-form params first; fall
        // back to empty params (JSON-on-compat surface). For each param
        // shape, try both URL port-normalization variants.
        // ------------------------------------------------------------
        List<Map.Entry<String, String>> parsedParams = parseFormBody(rawBody);

        @SuppressWarnings({"unchecked", "rawtypes"})
        List<List<Map.Entry<String, String>>> paramShapes = List.of(
                parsedParams,
                List.of()); // empty params for JSON-on-compat

        for (String candidateUrl : candidateUrls(url)) {
            for (List<Map.Entry<String, String>> shape : paramShapes) {
                String concat = sortedConcatParams(shape);
                String expectedB = b64HmacSha1(signingKey, candidateUrl + concat);
                if (safeEquals(expectedB, signature)) {
                    if (checkBodySha256(candidateUrl, rawBody)) {
                        return true;
                    }
                    // bodySHA256 mismatched — try other shapes / URLs.
                }
            }
        }

        return false;
    }

    /**
     * Legacy {@code @signalwire/compatibility-api} drop-in entry point.
     *
     * <p>Dispatches on the runtime type of {@code paramsOrRawBody}:
     * <ul>
     *   <li>{@link String} → delegates to
     *       {@link #validateWebhookSignature(String, String, String, String)}.</li>
     *   <li>{@link Map} or any {@link Iterable} of {@link Map.Entry} (or
     *       2-element arrays / lists) → treats the value as pre-parsed form
     *       params and runs Scheme B directly with URL port normalization.</li>
     *   <li>{@code null} → treated as an empty params map (Scheme B).</li>
     *   <li>Anything else → {@link IllegalArgumentException}.</li>
     * </ul>
     *
     * @param signingKey customer's Signing Key. Non-empty.
     * @param signature  header value. {@code null} / empty returns {@code false}.
     * @param url        full URL SignalWire POSTed to.
     * @param paramsOrRawBody {@code String} raw body OR pre-parsed form params.
     * @return {@code true} on match, {@code false} otherwise.
     * @throws IllegalArgumentException when {@code signingKey} is empty or
     *                   {@code paramsOrRawBody} is of an unsupported type.
     */
    public static boolean validateRequest(
            String signingKey,
            String signature,
            String url,
            Object paramsOrRawBody) {
        if (signingKey == null || signingKey.isEmpty()) {
            throw new IllegalArgumentException("signingKey is required");
        }
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        if (url == null) {
            url = "";
        }

        if (paramsOrRawBody instanceof String) {
            return validateWebhookSignature(signingKey, signature, url, (String) paramsOrRawBody);
        }

        // Pre-parsed form params → Scheme B only.
        List<Map.Entry<String, String>> items = coerceParams(paramsOrRawBody);

        String concat = sortedConcatParams(items);
        for (String candidateUrl : candidateUrls(url)) {
            String expectedB = b64HmacSha1(signingKey, candidateUrl + concat);
            if (safeEquals(expectedB, signature)) {
                // No raw body to verify against bodySHA256 here — skip that check.
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /** Lowercase hex of HMAC-SHA1. */
    private static String hexHmacSha1(String key, String message) {
        byte[] mac = hmacSha1(key, message);
        StringBuilder sb = new StringBuilder(mac.length * 2);
        for (byte b : mac) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** Standard base64 of HMAC-SHA1 (not URL-safe). */
    private static String b64HmacSha1(String key, String message) {
        return Base64.getEncoder().encodeToString(hmacSha1(key, message));
    }

    private static byte[] hmacSha1(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA1 is a JDK-mandated MAC algorithm — failure here would
            // indicate a broken JRE, not a runtime condition we can recover
            // from, so wrap as an unchecked error rather than forcing every
            // caller to catch it.
            throw new IllegalStateException("HmacSHA1 unavailable in this JRE", e);
        }
    }

    /**
     * Constant-time string compare via {@link MessageDigest#isEqual} on UTF-8
     * bytes. Returns {@code false} on any unexpected condition rather than
     * throwing — malformed/garbage signature inputs must never crash the
     * validator.
     */
    private static boolean safeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] eb = expected.getBytes(StandardCharsets.UTF_8);
        byte[] ab = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(eb, ab);
    }

    /**
     * Concatenate form params per Scheme B rules:
     *
     * <ul>
     *   <li>Sort by key (ASCII ascending) using a <b>stable</b> sort so
     *       repeated keys preserve original submission order.</li>
     *   <li>Emit {@code key + value} once per (key, value) pair.</li>
     *   <li>{@code null} value emits as the empty string (matches JS reference).</li>
     * </ul>
     */
    private static String sortedConcatParams(List<Map.Entry<String, String>> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        // Defensive copy so we don't mutate the caller's list.
        List<Map.Entry<String, String>> sorted = new ArrayList<>(items);
        // Collections.sort is stable — matches Python's list.sort and JS's
        // Array.prototype.sort spec contract that we rely on for repeated keys.
        sorted.sort((a, b) -> a.getKey().compareTo(b.getKey()));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> kv : sorted) {
            sb.append(kv.getKey());
            String v = kv.getValue();
            if (v != null) {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    /**
     * Best-effort parse of an x-www-form-urlencoded body. Returns an empty
     * list when the body doesn't look like form data — the caller will then
     * sign against {@code url + ""}.
     */
    private static List<Map.Entry<String, String>> parseFormBody(String rawBody) {
        if (rawBody == null || rawBody.isEmpty()) {
            return List.of();
        }
        // Quick skip — bodies that don't have at least one '=' aren't form-encoded.
        if (rawBody.indexOf('=') < 0) {
            return List.of();
        }
        List<Map.Entry<String, String>> items = new ArrayList<>();
        for (String pair : rawBody.split("&")) {
            int eq = pair.indexOf('=');
            String k;
            String v;
            if (eq >= 0) {
                k = pair.substring(0, eq);
                v = pair.substring(eq + 1);
            } else {
                k = pair;
                v = "";
            }
            try {
                k = java.net.URLDecoder.decode(k, StandardCharsets.UTF_8);
                v = java.net.URLDecoder.decode(v, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                // Bad %-escape — skip pair entirely; matches Python's lenient
                // parse_qsl(strict_parsing=False) behavior.
                continue;
            }
            items.add(Map.entry(k, v));
        }
        return items;
    }

    /**
     * Return URL variants to try for Scheme B port normalization.
     *
     * <ul>
     *   <li>https + no port → input URL AND url with {@code :443}.</li>
     *   <li>http + no port → input URL AND url with {@code :80}.</li>
     *   <li>https + {@code :443} or http + {@code :80} → input URL AND
     *       url with port stripped.</li>
     *   <li>Otherwise (any explicit non-standard port, or an unparseable
     *       URL) → just the input URL.</li>
     * </ul>
     */
    private static List<String> candidateUrls(String url) {
        if (url == null || url.isEmpty()) {
            return List.of(url == null ? "" : url);
        }
        URI parsed;
        try {
            parsed = new URI(url);
        } catch (URISyntaxException ex) {
            return List.of(url);
        }
        String scheme = parsed.getScheme();
        String host = parsed.getHost();
        if (scheme == null || host == null) {
            return List.of(url);
        }
        int port = parsed.getPort();
        String standard = "https".equalsIgnoreCase(scheme) ? "443"
                : "http".equalsIgnoreCase(scheme) ? "80"
                : null;

        List<String> candidates = new ArrayList<>(2);
        candidates.add(url);
        if (standard == null) {
            return candidates;
        }
        if (port < 0) {
            // Input has no port; also try with-standard-port.
            String withPort = rebuildWithPort(parsed, Integer.parseInt(standard));
            if (withPort != null && !withPort.equals(url)) {
                candidates.add(withPort);
            }
        } else if (String.valueOf(port).equals(standard)) {
            // Input has the standard port; also try without-port.
            String withoutPort = rebuildWithPort(parsed, -1);
            if (withoutPort != null && !withoutPort.equals(url)) {
                candidates.add(withoutPort);
            }
        }
        return candidates;
    }

    /** Rebuild a URI with an explicit port (or {@code -1} to strip). */
    private static String rebuildWithPort(URI in, int port) {
        try {
            return new URI(in.getScheme(),
                    in.getUserInfo(),
                    in.getHost(),
                    port,
                    in.getPath(),
                    in.getQuery(),
                    in.getFragment()).toString();
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    /**
     * If the URL carries {@code ?bodySHA256=<hex>}, verify
     * {@code sha256_hex(rawBody)} matches. Returns {@code true} when the
     * param is absent (no constraint) or present and matches.
     */
    private static boolean checkBodySha256(String url, String rawBody) {
        URI parsed;
        try {
            parsed = new URI(url);
        } catch (URISyntaxException ex) {
            return true;
        }
        String query = parsed.getRawQuery();
        if (query == null) {
            return true;
        }
        String expected = null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String k = pair.substring(0, eq);
            if ("bodySHA256".equals(k)) {
                expected = pair.substring(eq + 1);
                break;
            }
        }
        if (expected == null) {
            return true;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return safeEquals(sb.toString(), expected.toLowerCase(Locale.ROOT));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is JDK-mandated; impossible in practice.
            return false;
        }
    }

    /**
     * Coerce a pre-parsed-params object into a list of (key, value) entries.
     * Supports {@link Map}, an {@link Iterable} of {@link Map.Entry}, or an
     * {@link Iterable} of 2-element arrays / lists. Throws on anything else.
     */
    @SuppressWarnings("unchecked")
    private static List<Map.Entry<String, String>> coerceParams(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Map) {
            List<Map.Entry<String, String>> out = new ArrayList<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                String k = Objects.toString(e.getKey(), "");
                Object v = e.getValue();
                if (v instanceof Iterable<?>) {
                    for (Object vi : (Iterable<?>) v) {
                        out.add(Map.entry(k, Objects.toString(vi, "")));
                    }
                } else if (v != null && v.getClass().isArray()) {
                    Object[] arr = (Object[]) v;
                    for (Object vi : arr) {
                        out.add(Map.entry(k, Objects.toString(vi, "")));
                    }
                } else {
                    out.add(Map.entry(k, v == null ? "" : Objects.toString(v, "")));
                }
            }
            return out;
        }
        if (value instanceof Iterable<?>) {
            List<Map.Entry<String, String>> out = new ArrayList<>();
            for (Object item : (Iterable<?>) value) {
                if (item instanceof Map.Entry<?, ?>) {
                    Map.Entry<?, ?> e = (Map.Entry<?, ?>) item;
                    out.add(Map.entry(Objects.toString(e.getKey(), ""), Objects.toString(e.getValue(), "")));
                } else if (item instanceof Object[]) {
                    Object[] pair = (Object[]) item;
                    if (pair.length != 2) {
                        throw new IllegalArgumentException(
                                "param entry array must have exactly 2 elements (key, value)");
                    }
                    out.add(Map.entry(Objects.toString(pair[0], ""), Objects.toString(pair[1], "")));
                } else if (item instanceof List<?>) {
                    List<?> pair = (List<?>) item;
                    if (pair.size() != 2) {
                        throw new IllegalArgumentException(
                                "param entry list must have exactly 2 elements (key, value)");
                    }
                    out.add(Map.entry(Objects.toString(pair.get(0), ""), Objects.toString(pair.get(1), "")));
                } else {
                    throw new IllegalArgumentException(
                            "param entries must be Map.Entry, 2-element array, or 2-element list");
                }
            }
            return out;
        }
        throw new IllegalArgumentException(
                "paramsOrRawBody must be a String, Map, or Iterable of (key, value) entries");
    }
}
