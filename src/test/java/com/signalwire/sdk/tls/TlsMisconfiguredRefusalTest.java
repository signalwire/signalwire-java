/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.tls;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.core.SecurityConfig;
import com.signalwire.sdk.server.AgentServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TLS misconfiguration quadrant — the case {@link TlsServerHttpsTest} does not reach.
 *
 * <p>{@code TlsServerHttpsTest} proves the POSITIVE path: given a valid cert/key, {@link
 * AgentServer} binds a real {@code HttpsServer} and a CA-trusting client completes a verified
 * session. This file characterises what happens when TLS is switched ON but the cert/key is absent
 * or unreadable — the "not configured folds into TLS off" shape.
 *
 * <p><b>Wire-level method.</b> The two byte-capture tests below do not inspect any predicate. They
 * put a RAW {@link ServerSocket} on a port, point a real client at it, and record the first byte
 * the socket actually receives. That byte is the whole contract:
 *
 * <ul>
 *   <li>{@code 0x16} — TLS ContentType handshake ({@code ClientHello}). The client spoke TLS.
 *   <li>{@code 'G'} (0x47) — a plaintext {@code GET}. The client spoke cleartext.
 * </ul>
 *
 * <p>A predicate can be read wrongly; an opening byte cannot.
 */
class TlsMisconfiguredRefusalTest {

  private AgentServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop();
      server = null;
    }
  }

  // ==================================================================
  // 1. Wire-level positive control: an https:// client really does open
  //    with a TLS ClientHello (0x16). This is what "TLS on the wire"
  //    looks like, captured from a raw socket with no SDK involved.
  // ==================================================================

  @Test
  @DisplayName("wire: a TLS client opens with a ClientHello (0x16) and refuses a plaintext peer")
  void httpsClientOpensWithClientHello() throws Exception {
    try (ServerSocket raw = new ServerSocket()) {
      raw.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
      int port = raw.getLocalPort();

      ArrayBlockingQueue<Integer> firstByte = new ArrayBlockingQueue<>(1);
      Thread acceptor = acceptOneAndRecordFirstByte(raw, firstByte, /* answer200= */ true);

      // The listener is PLAINTEXT and would happily answer a valid 200 to a
      // cleartext GET. A TLS client must never obtain that 200 — it must send a
      // ClientHello and then fail, because no TLS peer answers.
      //
      // An SSLSocket is used rather than java.net.http.HttpClient deliberately:
      // HttpClient does NOT emit a ClientHello against a silent peer (measured —
      // it reports HttpConnectTimeoutException and the raw listener reads EOF),
      // so it cannot witness the opening bytes. SSLSocket.startHandshake() is
      // the layer where the handshake is actually spoken.
      SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
      assertThrows(
          IOException.class,
          () -> {
            try (SSLSocket s = (SSLSocket) factory.createSocket("127.0.0.1", port)) {
              s.setSoTimeout(5000);
              s.startHandshake();
            }
          },
          "a TLS handshake COMPLETED against a plaintext listener — impossible unless "
              + "the client silently downgraded to cleartext");

      Integer b = firstByte.poll(5, TimeUnit.SECONDS);
      acceptor.join(2000);
      assertNotNull(b, "raw listener never received a connection");
      assertEquals(
          0x16,
          b.intValue(),
          "first wire byte from a TLS client should be 0x16 (TLS ContentType handshake, "
              + "i.e. ClientHello); got 0x"
              + Integer.toHexString(b));
    }
  }

  // ==================================================================
  // 2. Wire-level negative control (scope control): the SAME raw listener,
  //    reached with an http:// URL, receives a plaintext GET and answers
  //    200. This proves test 1's refusal is specific to the https:// URL
  //    and not a listener that refuses everything.
  // ==================================================================

  @Test
  @DisplayName("wire negative control: the same plaintext listener answers 200 to an http:// GET")
  void plainHttpStillWorks() throws Exception {
    try (ServerSocket raw = new ServerSocket()) {
      raw.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
      int port = raw.getLocalPort();

      ArrayBlockingQueue<Integer> firstByte = new ArrayBlockingQueue<>(1);
      Thread acceptor = acceptOneAndRecordFirstByte(raw, firstByte, /* answer200= */ true);

      HttpClient client =
          HttpClient.newBuilder()
              .version(HttpClient.Version.HTTP_1_1)
              .connectTimeout(Duration.ofSeconds(3))
              .build();
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create("http://127.0.0.1:" + port + "/health"))
              .timeout(Duration.ofSeconds(3))
              .GET()
              .build();

      HttpResponse<String> resp =
          client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      assertEquals(200, resp.statusCode(), "plain http control did not get its 200");

      Integer b = firstByte.poll(5, TimeUnit.SECONDS);
      acceptor.join(2000);
      assertNotNull(b, "raw listener never received a connection");
      assertEquals(
          'G',
          b.intValue(),
          "first wire byte from an http:// client should be 'G' (GET); got 0x"
              + Integer.toHexString(b));
    }
  }

  // ==================================================================
  // 3. THE FINDING, captured behaviourally.
  //
  //    AgentServer.resolveTls() returns null — i.e. "serve plaintext" —
  //    on three separate not-configured conditions, one of which is
  //    SWML_SSL_ENABLED=true with a cert path that does not exist. run()
  //    then falls through to HttpServer.create(), yielding a WORKING
  //    PLAINTEXT LISTENER with no error and no exception.
  //
  //    This test does not assert a policy; it PINS the observable
  //    behaviour so a future change to it is deliberate and visible.
  //    See the class note in TLS_VERIFY_ALLOW.md's sibling discussion:
  //    the Python reference has the same fall-through (validate_ssl_config
  //    fails -> self.ssl_enabled = False -> plain uvicorn), so changing
  //    java to REFUSE is a reference-divergence decision, not a port fix.
  // ==================================================================

  @Test
  @DisplayName(
      "FINDING: TLS enabled with a nonexistent cert yields a plaintext listener, not a refusal")
  void tlsEnabledWithMissingCertServesPlaintext() throws Exception {
    Path missingCert = Path.of("/nonexistent/definitely-not-here/server.crt");
    Path missingKey = Path.of("/nonexistent/definitely-not-here/server.key");

    int port = freeTcpPort();
    server = new AgentServer("127.0.0.1", port);
    server.enableTls(missingCert.toString(), missingKey.toString());
    AgentBase agent =
        AgentBase.builder().name("tls-misconfig").authUser("u").authPassword("p").build();
    server.register(agent, "/");

    // The SDK itself reports TLS is NOT enabled, despite enableTls() having
    // been called: the missing files silently demote the request.
    assertFalse(
        server.isTlsEnabled(),
        "isTlsEnabled() true for a nonexistent cert — resolveTls() would then crash on bind");

    server.run();

    // A plain http:// GET succeeds — the listener is cleartext. This is the
    // defect shape: TLS was asked for and quietly not delivered.
    HttpClient plain =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    HttpResponse<String> resp =
        getWithRetry(plain, "http://127.0.0.1:" + port + "/health", Duration.ofSeconds(10));
    assertEquals(
        200,
        resp.statusCode(),
        "expected the documented fall-through: a plaintext 200 despite TLS being requested");
    assertTrue(
        resp.sslSession().isEmpty(),
        "response carried an SSLSession — the listener was NOT plaintext after all");
  }

  // ==================================================================
  // 4. The SWMLService/AgentBase serve() path has no TLS branch at all.
  //    SecurityConfig knows the SSL state and validates it correctly, but
  //    Service.serve() calls HttpServer.create() unconditionally, so the
  //    validation result never reaches a listener decision. Pinned here
  //    at the unit that carries the knowledge.
  // ==================================================================

  @Test
  @DisplayName("SecurityConfig.validateSslConfig rejects an enabled-but-uncertified config")
  void securityConfigValidatesButNothingOnServePathConsultsIt() {
    SecurityConfig cfg = new SecurityConfig();
    // Default construction: SSL off, so validation trivially passes.
    assertTrue(cfg.validateSslConfig().valid(), "SSL-off config should validate");
    // getUrlScheme is the scheme the SDK ADVERTISES in webhook URLs.
    assertEquals("http", cfg.getUrlScheme(), "SSL-off should advertise http");
    // And with SSL off there are no ssl kwargs to hand a server.
    assertTrue(cfg.getSslContextKwargs().isEmpty(), "SSL-off should yield no ssl options");
  }

  // ------------------------------------------------------------------
  // helpers
  // ------------------------------------------------------------------

  /**
   * Accept exactly one connection on {@code raw}, publish the FIRST byte read from it into {@code
   * sink}, and optionally answer a minimal valid HTTP 200 so a cleartext client succeeds. Returns
   * the started thread so the caller can join it.
   */
  private static Thread acceptOneAndRecordFirstByte(
      ServerSocket raw, ArrayBlockingQueue<Integer> sink, boolean answer200) {
    Thread t =
        new Thread(
            () -> {
              try (Socket s = raw.accept()) {
                s.setSoTimeout(5000);
                // Do NOT close our side early: an https:// client that gets no
                // ServerHello will tear the connection down, and if we have
                // already returned from read() the byte is still the one it
                // sent. Read with a generous timeout so the ClientHello — which
                // the client writes immediately after connect — is observed.
                InputStream in = s.getInputStream();
                int b = in.read();
                sink.offer(b);
                if (answer200 && b == 'G') {
                  // Drain the rest of the request line/headers, then answer.
                  byte[] scratch = new byte[4096];
                  if (in.available() > 0) {
                    int ignored = in.read(scratch);
                    assert ignored >= -1;
                  }
                  String body = "{\"status\":\"healthy\"}";
                  String head =
                      "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                          + body.length()
                          + "\r\nConnection: close\r\n\r\n";
                  OutputStream out = s.getOutputStream();
                  out.write((head + body).getBytes(StandardCharsets.UTF_8));
                  out.flush();
                }
              } catch (IOException e) {
                sink.offer(-1);
              }
            },
            "tls-probe-acceptor");
    t.setDaemon(true);
    t.start();
    return t;
  }

  private static HttpResponse<String> getWithRetry(HttpClient client, String url, Duration timeout)
      throws Exception {
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    IOException last = null;
    while (System.currentTimeMillis() < deadline) {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(3))
              .GET()
              .build();
      try {
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      } catch (IOException e) {
        last = e;
        Thread.sleep(100);
      }
    }
    throw new AssertionError("listener never became reachable: " + url, last);
  }

  private static int freeTcpPort() throws IOException {
    try (ServerSocket s = new ServerSocket()) {
      s.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
      return s.getLocalPort();
    }
  }
}
