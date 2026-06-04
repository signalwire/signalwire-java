/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.tls;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TLS capability test, quadrant 2 of 3: prove a JDK {@link HttpClient} performs
 * a REAL verified {@code https://} GET against the shared mock_signalwire (whole
 * app served over HTTPS in {@code --tls} mode), and gets a genuine JSON
 * response back.
 *
 * <p>CA trust: the client is built with {@code HttpClient.newBuilder().sslContext(...)}
 * where the {@link SSLContext} trusts ONLY {@code ca.crt}, assembled via an
 * in-memory {@link java.security.KeyStore} → {@code TrustManagerFactory}
 * ({@link TlsSupport#trustingSslContext}). Java does NOT read the OS trust
 * store, so this explicit context is what makes the harness's CA-signed leaf
 * cert verify. NO trust-all TrustManager, NO transport mock — a {@code data}
 * array can only come back over a completed, CA-verified TLS session.
 *
 * <p>This drives the JDK HttpClient directly (the same transport the SDK's REST
 * {@code HttpClient} is built on) because the SDK's REST client builds its
 * {@code java.net.http.HttpClient} internally with no SSLContext hook; pointing
 * a custom-CA context at the production REST client is not possible today (see
 * the report's SDK-gap finding). The GET targets the same spec-backed
 * {@code /api/relay/rest/addresses} collection the SDK's
 * {@code RestClient.addresses().list()} hits.
 *
 * <p>Negative control: the same GET issued by a client built from an EMPTY
 * trust store must fail the handshake, proving the cert is actually verified.
 */
class TlsHttpsMockTest {

    private static final Gson GSON = new Gson();

    @Test
    @DisplayName("JDK HttpClient performs a verified https:// GET and gets real JSON")
    void httpsGetReturnsRealJson() throws Exception {
        Path certs = TlsSupport.certsDir();

        try (TlsSupport.MockHandle mock = TlsSupport.spawnTlsMockSignalwire(certs)) {
            String base = "https://127.0.0.1:" + mock.port;

            // ── Positive: CA-trusting client gets a real JSON response ──
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .sslContext(TlsSupport.trustingSslContext(certs))
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            String basic = Base64.getEncoder()
                    .encodeToString("test_proj:test_tok".getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/api/relay/rest/addresses?page_size=5"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Basic " + basic)
                    .GET()
                    .build();

            HttpResponse<String> resp =
                    client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertEquals(200, resp.statusCode(),
                    "https GET status; body=" + resp.body());

            Map<String, Object> body =
                    GSON.fromJson(resp.body(), new TypeToken<Map<String, Object>>() {}.getType());
            assertNotNull(body, "empty body over https");
            assertTrue(body.containsKey("data"),
                    "https response missing 'data' key; got " + body.keySet());

            // Wire proof: the mock journaled the GET on its (HTTPS) control plane.
            Map<String, Object> last = lastJournal(mock);
            assertEquals("GET", last.get("method"), "journal method");
            assertEquals("/api/relay/rest/addresses", last.get("path"), "journal path");

            // ── Negative: empty-trust client must be rejected ──
            assertHttpsRejectedWithoutTrust(base);
        }
    }

    private Map<String, Object> lastJournal(TlsSupport.MockHandle mock) {
        List<Map<String, Object>> entries = mock.journal();
        assertFalse(entries == null || entries.isEmpty(),
                "mock journal empty - the HTTPS GET did not reach the mock");
        return entries.get(entries.size() - 1);
    }

    /**
     * A client built from an EMPTY trust store must fail the TLS handshake
     * against the harness's CA-signed leaf cert, proving real verification.
     */
    private void assertHttpsRejectedWithoutTrust(String base) {
        SSLContext empty = TlsSupport.emptyTrustSslContext();
        HttpClient untrusted = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .sslContext(empty)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "/__mock__/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        IOException failure = assertThrows(IOException.class,
                () -> untrusted.send(req, HttpResponse.BodyHandlers.ofString()),
                "https GET with empty trust store unexpectedly succeeded");
        // The JDK surfaces a CA-validation failure as SSLHandshakeException
        // (a subtype of IOException); accept either the exact type or its
        // presence in the cause chain.
        boolean sslRelated = failure instanceof SSLHandshakeException
                || hasCause(failure, SSLHandshakeException.class)
                || hasCause(failure, javax.net.ssl.SSLException.class);
        assertTrue(sslRelated, "expected an SSL handshake failure, got: " + failure);
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) return true;
        }
        return false;
    }
}
