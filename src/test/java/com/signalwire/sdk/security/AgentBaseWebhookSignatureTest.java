/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.security;

import com.google.gson.Gson;
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.runtime.EnvProvider;
import com.signalwire.sdk.swaig.FunctionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the AgentBase HTTP server signature-validation
 * flow. The agent serves on a real ephemeral port; we POST signed and
 * unsigned bodies via {@code java.net.http.HttpClient} and assert the
 * status codes (and that valid POSTs reach the SWAIG handler).
 *
 * <p>Mirrors the Python suite at
 * {@code signalwire-python/tests/unit/security/test_webhook_agent_integration.py}.
 */
class AgentBaseWebhookSignatureTest {

    private static final String SIGNING_KEY = "PSKtest1234567890abcdef";
    private static final Gson GSON = new Gson();

    private AgentBase agent;
    private int port;
    private HttpClient client;

    @BeforeEach
    void start() throws Exception {
        port = findFreePort();
        agent = AgentBase.builder()
                .name("sigtest")
                .route("/")
                .port(port)
                .authUser("user")
                .authPassword("pass")
                .signingKey(SIGNING_KEY)
                .build();
        agent.defineTool("ping", "Test tool", Map.of(), (args, raw) -> new FunctionResult("pong"));
        agent.serve();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void stop() {
        if (agent != null) agent.stop();
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** Compute Scheme A signature for a (key, url, body) triple. */
    private static String schemeASignature(String key, String url, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] raw = mac.doFinal((url + body).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder()
                .encodeToString(("user:pass").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Builder accepts signingKey and exposes it via getSigningKey()")
    void builderAcceptsSigningKey() {
        assertEquals(SIGNING_KEY, agent.getSigningKey());
    }

    @Test
    @DisplayName("Builder env fallback: SIGNALWIRE_SIGNING_KEY picked up when no explicit key")
    void envFallback() {
        EnvProvider env = name ->
                "SIGNALWIRE_SIGNING_KEY".equals(name) ? "env-key-12345" : null;
        AgentBase a = AgentBase.builder()
                .name("envtest")
                .authUser("u")
                .authPassword("p")
                .envProvider(env)
                .build();
        assertEquals("env-key-12345", a.getSigningKey());
    }

    @Test
    @DisplayName("No key configured: getSigningKey() returns null and getter for trustProxy works")
    void noKeyMeansNull() {
        AgentBase a = AgentBase.builder()
                .name("nokey")
                .authUser("u")
                .authPassword("p")
                .envProvider(name -> null) // mask the real env
                .build();
        assertNull(a.getSigningKey());
        assertFalse(a.isTrustProxyForSignature());
    }

    @Test
    @DisplayName("Builder trustProxyForSignature() flag persists")
    void trustProxyFlag() {
        AgentBase a = AgentBase.builder()
                .name("tpx")
                .authUser("u")
                .authPassword("p")
                .signingKey("k")
                .trustProxyForSignature(true)
                .build();
        assertTrue(a.isTrustProxyForSignature());
    }

    @Test
    @DisplayName("POST /swaig with valid signature → 200 and the SWAIG handler runs")
    void swaigValidSignatureAccepted() throws Exception {
        String body = "{\"function\":\"ping\",\"argument\":{\"parsed\":[{}]}}";
        String url = "http://127.0.0.1:" + port + "/swaig";
        String sig = schemeASignature(SIGNING_KEY, url, body);

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", basicAuth())
                        .header(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER, sig)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertNotNull(resp.body());
        assertTrue(resp.body().contains("pong"),
                "Valid-signature POST should reach the SWAIG handler — got: " + resp.body());
    }

    @Test
    @DisplayName("POST /swaig with invalid signature → 403")
    void swaigInvalidSignatureRejected() throws Exception {
        String body = "{\"function\":\"ping\",\"argument\":{\"parsed\":[{}]}}";
        String url = "http://127.0.0.1:" + port + "/swaig";

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", basicAuth())
                        .header(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER, "bogus")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(403, resp.statusCode());
    }

    @Test
    @DisplayName("POST /swaig with no signature header → 403")
    void swaigMissingSignatureRejected() throws Exception {
        String body = "{\"function\":\"ping\",\"argument\":{\"parsed\":[{}]}}";
        String url = "http://127.0.0.1:" + port + "/swaig";

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", basicAuth())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(403, resp.statusCode());
    }

    @Test
    @DisplayName("POST /swaig with X-Twilio-Signature alias is accepted")
    void swaigTwilioAliasAccepted() throws Exception {
        String body = "{\"function\":\"ping\",\"argument\":{\"parsed\":[{}]}}";
        String url = "http://127.0.0.1:" + port + "/swaig";
        String sig = schemeASignature(SIGNING_KEY, url, body);

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", basicAuth())
                        .header(WebhookValidator.TWILIO_COMPAT_SIGNATURE_HEADER, sig)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("pong"));
    }

    @Test
    @DisplayName("POST /post_prompt with valid signature → 200")
    void postPromptValidSignatureAccepted() throws Exception {
        String body = "{\"post_prompt_data\":{\"parsed\":{\"summary\":\"hi\"}}}";
        String url = "http://127.0.0.1:" + port + "/post_prompt";
        String sig = schemeASignature(SIGNING_KEY, url, body);

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", basicAuth())
                        .header(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER, sig)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
    }

    @Test
    @DisplayName("POST /post_prompt with invalid signature → 403")
    void postPromptInvalidSignatureRejected() throws Exception {
        String body = "{\"post_prompt_data\":{}}";
        String url = "http://127.0.0.1:" + port + "/post_prompt";

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", basicAuth())
                        .header(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER, "bogus")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(403, resp.statusCode());
    }

    @Test
    @DisplayName("POST / (root) with valid signature → 200")
    void rootValidSignatureAccepted() throws Exception {
        String body = "{}";
        String url = "http://127.0.0.1:" + port + "/";
        String sig = schemeASignature(SIGNING_KEY, url, body);

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", basicAuth())
                        .header(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER, sig)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
    }

    @Test
    @DisplayName("POST / (root) with invalid signature → 403")
    void rootInvalidSignatureRejected() throws Exception {
        String body = "{}";
        String url = "http://127.0.0.1:" + port + "/";

        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", basicAuth())
                        .header(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER, "bogus")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(403, resp.statusCode());
    }

    @Test
    @DisplayName("GET / (no signature header) is unaffected when signing key is set")
    void getRootUnaffectedBySigningKey() throws Exception {
        String url = "http://127.0.0.1:" + port + "/";
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", basicAuth())
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        // GET never carries a signature; the validator runs only on POST.
        assertEquals(200, resp.statusCode());
    }

    @Test
    @DisplayName("Auth check still runs ahead of signature check")
    void authStillRequired() throws Exception {
        String body = "{\"function\":\"ping\",\"argument\":{\"parsed\":[{}]}}";
        String url = "http://127.0.0.1:" + port + "/swaig";
        String sig = schemeASignature(SIGNING_KEY, url, body);
        // Valid signature, NO basic-auth header → expect 401 (not 403).
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header(WebhookValidator.SIGNALWIRE_SIGNATURE_HEADER, sig)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode(),
                "missing basic-auth must produce 401, not 403");
    }
}
