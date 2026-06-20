/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.tls;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.server.AgentServer;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TLS capability test, quadrant 3 of 3 (the server side): prove the SDK's own {@link AgentServer}
 * serves a REAL verified HTTPS endpoint.
 *
 * <p>This exercises the port-specific server-TLS fix. The JDK's {@code
 * com.sun.net.httpserver.HttpServer} (what AgentServer used) has no TLS; the SDK now binds {@code
 * com.sun.net.httpserver.HttpsServer} + {@code setHttpsConfigurator(new
 * HttpsConfigurator(sslContext))} when TLS is configured — via {@link AgentServer#enableTls(String,
 * String)} (explicit cert/key, the precedence option) or the {@code SWML_SSL_*} environment
 * variables, mirroring Python's {@code AgentServer.run()}.
 *
 * <p>The test starts the server with the harness's CA-signed leaf ({@code server.crt}/{@code
 * server.key}, SAN localhost/127.0.0.1) in a background thread, then reaches its unauthenticated
 * {@code /health} route from an in-test JDK {@link HttpClient} that trusts the test CA ({@link
 * TlsSupport#trustingSslContext}). It asserts a 200, a non-null {@link SSLSession} on the response
 * (the request really went over TLS), and a {@code status=healthy} body. CA trust is the in-memory
 * KeyStore → TrustManagerFactory path; NO trust-all TrustManager.
 *
 * <p>Negative control: a client built from an EMPTY trust store must be rejected, proving the SDK
 * server presents a cert that is actually verified.
 */
class TlsServerHttpsTest {

  private static final Gson GSON = new Gson();

  private AgentServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  @Test
  @DisplayName("AgentServer serves verified HTTPS via HttpsServer + HttpsConfigurator")
  void agentServerServesVerifiedHttps() throws Exception {
    Path certs = TlsSupport.certsDir();
    String certFile = certs.resolve("server.crt").toString();
    String keyFile = certs.resolve("server.key").toString();

    int port = freeTcpPort();
    server = new AgentServer("127.0.0.1", port);
    server.enableTls(certFile, keyFile);
    AgentBase agent =
        AgentBase.builder().name("tls-cap-test").authUser("u").authPassword("p").build();
    server.register(agent, "/");

    // Sanity: the SDK reports TLS will be served (explicit cert resolves).
    assertTrue(
        server.isTlsEnabled(),
        "isTlsEnabled() false after enableTls; server would not serve https");

    server.run(); // binds + starts the HttpsServer listener (non-blocking)

    String base = "https://127.0.0.1:" + port;

    // ── Positive: CA-trusting client reaches /health over HTTPS ──
    SSLContext trusting = TlsSupport.trustingSslContext(certs);
    HttpClient client =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .sslContext(trusting)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    HttpResponse<String> resp = getHealthWithRetry(client, base, Duration.ofSeconds(10));
    assertEquals(200, resp.statusCode(), "https /health status; body=" + resp.body());

    // Proof the request really went over TLS: the JDK exposes the
    // negotiated SSLSession only for https responses.
    Optional<SSLSession> session = resp.sslSession();
    assertTrue(session.isPresent(), "response has no SSLSession; the request did not go over TLS");

    Map<String, Object> body =
        GSON.fromJson(resp.body(), new TypeToken<Map<String, Object>>() {}.getType());
    assertNotNull(body, "empty /health body");
    assertEquals("healthy", body.get("status"), "https /health body status; got " + body);

    // ── Negative: empty-trust client must be rejected ──
    assertHttpsRejectedWithoutTrust(base);
  }

  private HttpResponse<String> getHealthWithRetry(HttpClient client, String base, Duration timeout)
      throws Exception {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    IOException last = null;
    while (System.currentTimeMillis() < deadline) {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(base + "/health"))
              .timeout(Duration.ofSeconds(3))
              .GET()
              .build();
      try {
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      } catch (IOException e) {
        last = e; // listener may not be up for the first poll
        Thread.sleep(100);
      }
    }
    throw new AssertionError("server /health never became reachable over https", last);
  }

  /**
   * A client from an EMPTY trust store must fail the handshake against the SDK server's CA-signed
   * leaf cert, proving the cert is actually verified.
   */
  private void assertHttpsRejectedWithoutTrust(String base) {
    SSLContext empty = TlsSupport.emptyTrustSslContext();
    HttpClient untrusted =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .sslContext(empty)
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(base + "/health"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
    IOException failure =
        assertThrows(
            IOException.class,
            () -> untrusted.send(req, HttpResponse.BodyHandlers.ofString()),
            "https /health with empty trust store unexpectedly succeeded");
    boolean sslRelated =
        failure instanceof SSLHandshakeException
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

  /** Asks the OS for an unused loopback TCP port. */
  private static int freeTcpPort() throws IOException {
    try (ServerSocket s = new ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))) {
      return s.getLocalPort();
    }
  }
}
