/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-language SDK contract tests for {@link WebhookValidator}.
 *
 * <p>Mirrors the Python reference suite at
 * {@code signalwire-python/tests/unit/security/test_webhook_validator.py}.
 * Vectors A, B, C are the canonical vectors from
 * {@code porting-sdk/webhooks.md}; if they break, this port has a real
 * bug — DO NOT relax them.
 */
class WebhookValidatorTest {

    // -------- Canonical vectors from porting-sdk/webhooks.md --------

    private static final String VECTOR_A_KEY = "PSKtest1234567890abcdef";
    private static final String VECTOR_A_URL = "https://example.ngrok.io/webhook";
    private static final String VECTOR_A_BODY =
            "{\"event\":\"call.state\",\"params\":"
                    + "{\"call_id\":\"abc-123\",\"state\":\"answered\"}}";
    private static final String VECTOR_A_EXPECTED =
            "c3c08c1fefaf9ee198a100d5906765a6f394bf0f";

    private static final String VECTOR_B_KEY = "12345";
    private static final String VECTOR_B_URL = "https://mycompany.com/myapp.php?foo=1&bar=2";
    private static final Map<String, String> VECTOR_B_PARAMS;
    private static final String VECTOR_B_EXPECTED = "RSOYDt4T1cUTdK1PDd93/VVr8B8=";

    static {
        // Use LinkedHashMap to preserve insertion order — the spec example
        // submits keys in this order.
        VECTOR_B_PARAMS = new LinkedHashMap<>();
        VECTOR_B_PARAMS.put("CallSid", "CA1234567890ABCDE");
        VECTOR_B_PARAMS.put("Caller", "+14158675309");
        VECTOR_B_PARAMS.put("Digits", "1234");
        VECTOR_B_PARAMS.put("From", "+14158675309");
        VECTOR_B_PARAMS.put("To", "+18005551212");
    }

    private static final String VECTOR_C_KEY = "PSKtest1234567890abcdef";
    private static final String VECTOR_C_BODY = "{\"event\":\"call.state\"}";
    private static final String VECTOR_C_URL =
            "https://example.ngrok.io/webhook?bodySHA256="
                    + "69f3cbfc18e386ef8236cb7008cd5a54b7fed637a8cb3373b5a1591d7f0fd5f4";
    private static final String VECTOR_C_EXPECTED = "dfO9ek8mxyFtn2nMz24plPmPfIY=";

    /**
     * Build a percent-encoded form body that round-trips through the
     * validator's parser back to the same key/value pairs Scheme B will
     * sort and concatenate. Matches the equivalent helper in the Python
     * reference suite.
     */
    private static String formEncoded(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return sb.toString();
    }

    /** Compute base64(HMAC-SHA1(key, message)). Used for vector synthesis. */
    private static String b64HmacSha1(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    // ------------------------------------------------------------------
    // Scheme A — RELAY/JSON (hex)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Scheme A — RELAY/JSON (hex)")
    class SchemeATests {
        @Test
        @DisplayName("Vector A canonical: known JSON body + URL + key matches expected hex digest")
        void positiveCanonicalVector() {
            assertTrue(WebhookValidator.validateWebhookSignature(
                    VECTOR_A_KEY, VECTOR_A_EXPECTED, VECTOR_A_URL, VECTOR_A_BODY));
        }

        @Test
        @DisplayName("Tampered body returns false")
        void negativeTamperedBody() {
            String tampered = VECTOR_A_BODY.replace("answered", "ringing");
            assertFalse(WebhookValidator.validateWebhookSignature(
                    VECTOR_A_KEY, VECTOR_A_EXPECTED, VECTOR_A_URL, tampered));
        }

        @Test
        @DisplayName("Wrong signing key returns false")
        void negativeWrongKey() {
            assertFalse(WebhookValidator.validateWebhookSignature(
                    "wrong-key", VECTOR_A_EXPECTED, VECTOR_A_URL, VECTOR_A_BODY));
        }

        @Test
        @DisplayName("Different URL path returns false (URL is part of the digest)")
        void negativeWrongUrl() {
            assertFalse(WebhookValidator.validateWebhookSignature(
                    VECTOR_A_KEY, VECTOR_A_EXPECTED,
                    "https://example.ngrok.io/different", VECTOR_A_BODY));
        }
    }

    // ------------------------------------------------------------------
    // Scheme B — Compat/cXML form (base64)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Scheme B — Compat/cXML (base64 form)")
    class SchemeBTests {
        @Test
        @DisplayName("Vector B canonical: form params via raw body matches the canonical Twilio digest")
        void positiveCanonicalFormVector() {
            String body = formEncoded(VECTOR_B_PARAMS);
            assertTrue(WebhookValidator.validateWebhookSignature(
                    VECTOR_B_KEY, VECTOR_B_EXPECTED, VECTOR_B_URL, body));
        }

        @Test
        @DisplayName("validateRequest with Map runs Scheme B directly")
        void validateRequestWithMap() {
            assertTrue(WebhookValidator.validateRequest(
                    VECTOR_B_KEY, VECTOR_B_EXPECTED, VECTOR_B_URL, VECTOR_B_PARAMS));
        }

        @Test
        @DisplayName("validateRequest with List<Map.Entry> runs Scheme B directly")
        void validateRequestWithEntryList() {
            // Build a list of Map.Entry to mirror the Python list-of-tuples test.
            List<Map.Entry<String, String>> entries = List.copyOf(VECTOR_B_PARAMS.entrySet());
            assertTrue(WebhookValidator.validateRequest(
                    VECTOR_B_KEY, VECTOR_B_EXPECTED, VECTOR_B_URL, entries));
        }

        @Test
        @DisplayName("Vector C bodySHA256: JSON body on compat surface matches signature over URL+empty params")
        void bodySha256CanonicalVector() {
            assertTrue(WebhookValidator.validateWebhookSignature(
                    VECTOR_C_KEY, VECTOR_C_EXPECTED, VECTOR_C_URL, VECTOR_C_BODY));
        }

        @Test
        @DisplayName("bodySHA256 mismatch is rejected even if HMAC matches the URL")
        void bodySha256MismatchRejected() {
            // Same URL + signature as Vector C, but a different body. The HMAC
            // would match URL+'' but the bodySHA256 query check should fail.
            String wrongBody = "{\"event\":\"DIFFERENT\"}";
            assertFalse(WebhookValidator.validateWebhookSignature(
                    VECTOR_C_KEY, VECTOR_C_EXPECTED, VECTOR_C_URL, wrongBody));
        }
    }

    // ------------------------------------------------------------------
    // URL port normalization
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("URL port normalization")
    class PortNormalizationTests {

        private String b64Sig(String key, String url, Map<String, String> params) {
            StringBuilder data = new StringBuilder(url);
            // Sort by key, ASCII ascending, and append key+value once.
            for (Map.Entry<String, String> e : new java.util.TreeMap<>(params).entrySet()) {
                data.append(e.getKey()).append(e.getValue());
            }
            return b64HmacSha1(key, data.toString());
        }

        @Test
        @DisplayName("Backend signed with :443; request URL has no port → accept")
        void withPortAcceptedWhenRequestHasNoPort() {
            String key = "test-key";
            String urlWithPort = "https://example.com:443/webhook";
            String urlWithoutPort = "https://example.com/webhook";
            String sig = b64Sig(key, urlWithPort, Map.of());
            // raw_body is JSON; Scheme B falls back to empty params.
            assertTrue(WebhookValidator.validateWebhookSignature(
                    key, sig, urlWithoutPort, "{}"));
        }

        @Test
        @DisplayName("Backend signed without port; request URL has :443 → accept")
        void withoutPortAcceptedWhenRequestHasStandardPort() {
            String key = "test-key";
            String urlWithPort = "https://example.com:443/webhook";
            String urlWithoutPort = "https://example.com/webhook";
            String sig = b64Sig(key, urlWithoutPort, Map.of());
            assertTrue(WebhookValidator.validateWebhookSignature(
                    key, sig, urlWithPort, "{}"));
        }

        @Test
        @DisplayName("HTTP + :80 mirrors HTTPS + :443")
        void http80Normalization() {
            String key = "test-key";
            String urlWithPort = "http://example.com:80/path";
            String urlWithoutPort = "http://example.com/path";
            String sig = b64Sig(key, urlWithPort, Map.of());
            assertTrue(WebhookValidator.validateWebhookSignature(
                    key, sig, urlWithoutPort, ""));
        }
    }

    // ------------------------------------------------------------------
    // Repeated form keys
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Repeated form keys")
    class RepeatedKeyTests {
        @Test
        @DisplayName("To=a&To=b → signing string URL+'ToaTob', deterministic")
        void repeatedKeysConcatInSubmissionOrder() {
            String key = "test-key";
            String url = "https://example.com/hook";
            String body = "To=a&To=b";
            String expectedData = url + "ToaTob";
            String sig = b64HmacSha1(key, expectedData);
            assertTrue(WebhookValidator.validateWebhookSignature(key, sig, url, body));
        }

        @Test
        @DisplayName("Order within repeated keys is preserved (To=a&To=b is NOT the same as To=b&To=a)")
        void repeatedKeysOrderMatters() {
            String key = "test-key";
            String url = "https://example.com/hook";
            String bodyAB = "To=a&To=b";
            String bodyBA = "To=b&To=a";
            String dataAB = url + "ToaTob";
            String sigForAB = b64HmacSha1(key, dataAB);
            assertTrue(WebhookValidator.validateWebhookSignature(key, sigForAB, url, bodyAB));
            assertFalse(WebhookValidator.validateWebhookSignature(key, sigForAB, url, bodyBA));
        }
    }

    // ------------------------------------------------------------------
    // Error modes
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Error modes")
    class ErrorModeTests {
        @Test
        @DisplayName("Empty signature header returns false (no exception)")
        void missingSignatureReturnsFalse() {
            assertFalse(WebhookValidator.validateWebhookSignature(
                    VECTOR_A_KEY, "", VECTOR_A_URL, VECTOR_A_BODY));
            assertFalse(WebhookValidator.validateWebhookSignature(
                    VECTOR_A_KEY, null, VECTOR_A_URL, VECTOR_A_BODY));
        }

        @Test
        @DisplayName("Missing signing key throws IllegalArgumentException")
        void missingSigningKeyThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    WebhookValidator.validateWebhookSignature(
                            "", "sig", VECTOR_A_URL, VECTOR_A_BODY));
            assertThrows(IllegalArgumentException.class, () ->
                    WebhookValidator.validateWebhookSignature(
                            null, "sig", VECTOR_A_URL, VECTOR_A_BODY));
        }

        @Test
        @DisplayName("Null rawBody throws IllegalArgumentException")
        void nullBodyThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    WebhookValidator.validateWebhookSignature(
                            VECTOR_A_KEY, "sig", VECTOR_A_URL, null));
        }

        @Test
        @DisplayName("Garbage signature returns false without throwing")
        void malformedSignatureReturnsFalse() {
            String[] garbageInputs = {"xyz", "!!!!", "a".repeat(100), "%%notbase64%%"};
            for (String garbage : garbageInputs) {
                assertFalse(WebhookValidator.validateWebhookSignature(
                                VECTOR_A_KEY, garbage, VECTOR_A_URL, VECTOR_A_BODY),
                        "garbage input should return false: " + garbage);
            }
        }
    }

    // ------------------------------------------------------------------
    // validateRequest legacy alias dispatch
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("validateRequest legacy dispatch")
    class ValidateRequestDispatchTests {
        @Test
        @DisplayName("String 4th arg behaves identically to validateWebhookSignature")
        void stringDelegates() {
            assertTrue(WebhookValidator.validateRequest(
                    VECTOR_A_KEY, VECTOR_A_EXPECTED, VECTOR_A_URL, VECTOR_A_BODY));
        }

        @Test
        @DisplayName("Map 4th arg goes straight to Scheme B")
        void mapRunsSchemeBDirectly() {
            assertTrue(WebhookValidator.validateRequest(
                    VECTOR_B_KEY, VECTOR_B_EXPECTED, VECTOR_B_URL, VECTOR_B_PARAMS));
        }

        @Test
        @DisplayName("Unsupported type throws IllegalArgumentException")
        void unsupportedTypeThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    WebhookValidator.validateRequest(
                            VECTOR_A_KEY, "sig", VECTOR_A_URL, Integer.valueOf(42)));
        }

        @Test
        @DisplayName("validateRequest with empty signing key throws")
        void emptyKeyThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                    WebhookValidator.validateRequest("", "sig", VECTOR_A_URL, VECTOR_A_BODY));
        }
    }

    // ------------------------------------------------------------------
    // Constant-time compare — validate the implementation uses
    // MessageDigest.isEqual, the JDK's documented constant-time compare.
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Constant-time compare")
    class ConstantTimeCompareTests {
        @Test
        @DisplayName("Validator source uses MessageDigest.isEqual")
        void validatorSourceUsesMessageDigestIsEqual() throws Exception {
            // Read the implementation source file and assert that the
            // documented constant-time compare appears at least once. We
            // read the source rather than time-measure because timing tests
            // are flaky in CI; the spec explicitly names the function to
            // use. Other ports do the equivalent (hmac.compare_digest in
            // Python, crypto.timingSafeEqual in Node).
            java.nio.file.Path src = java.nio.file.Path.of(
                    System.getProperty("user.dir"),
                    "src/main/java/com/signalwire/sdk/security/WebhookValidator.java");
            String content = java.nio.file.Files.readString(src);
            assertTrue(content.contains("MessageDigest.isEqual"),
                    "WebhookValidator must use MessageDigest.isEqual for "
                            + "signature compare (constant-time).");
            // And it must NOT use String.equals on the compared signature
            // values themselves. The substring check below is conservative
            // — it only flags the literal patterns we'd reach for if
            // someone removed the constant-time guard.
            assertFalse(content.contains("expected.equals(actual)")
                            || content.contains("actual.equals(expected)"),
                    "WebhookValidator must not use String.equals on "
                            + "expected/actual signature values.");
        }
    }

    // ------------------------------------------------------------------
    // Static cross-reference of the canonical vectors. Belt-and-braces:
    // confirms the test file's own copy of the vectors matches the spec.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("All three canonical vectors validate end-to-end")
    void allCanonicalVectorsValidate() {
        // Vector A
        assertTrue(WebhookValidator.validateWebhookSignature(
                VECTOR_A_KEY, VECTOR_A_EXPECTED, VECTOR_A_URL, VECTOR_A_BODY));
        // Vector B (raw body)
        assertTrue(WebhookValidator.validateWebhookSignature(
                VECTOR_B_KEY, VECTOR_B_EXPECTED, VECTOR_B_URL, formEncoded(VECTOR_B_PARAMS)));
        // Vector B (map)
        assertTrue(WebhookValidator.validateRequest(
                VECTOR_B_KEY, VECTOR_B_EXPECTED, VECTOR_B_URL, VECTOR_B_PARAMS));
        // Vector C (bodySHA256)
        assertTrue(WebhookValidator.validateWebhookSignature(
                VECTOR_C_KEY, VECTOR_C_EXPECTED, VECTOR_C_URL, VECTOR_C_BODY));
    }

    @Test
    @DisplayName("Header constants match the canonical names")
    void headerConstants() {
        assertEquals("X-SignalWire-Signature",
                WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER);
        assertEquals("X-Twilio-Signature",
                WebhookValidator.TWILIO_COMPAT_SIGNATURE_HEADER);
    }
}
