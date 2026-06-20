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
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TLS capability test, quadrant 1 of 3: prove a Java-WebSocket client performs a REAL verified
 * {@code wss://} handshake against the shared mock_relay, then drives the actual RELAY {@code
 * signalwire.connect} authentication exchange over that TLS session.
 *
 * <p>CA trust: an {@link SSLContext} built from {@code ca.crt} via an in-memory {@link
 * java.security.KeyStore} → {@code TrustManagerFactory} (see {@link TlsSupport#trustingSslContext})
 * is handed to the Java-WebSocket client via {@link WebSocketClient#setSocketFactory} ({@code
 * sslContext.getSocketFactory()}). NO trust-all TrustManager, NO transport mock — the server-issued
 * {@code protocol} string in the {@code signalwire.connect} result can only come back over a
 * genuinely completed, CA-verified TLS WebSocket.
 *
 * <p>Why the Java-WebSocket client directly rather than the SDK's RelayClient: the SDK's {@code
 * RelayClient} wraps an internal {@code WebSocketClient} but exposes no hook to inject a custom-CA
 * SSLContext / SocketFactory (it relies on the JVM default trust store, which does not contain the
 * throwaway test CA). Driving the same {@code org.java_websocket} client the SDK builds on — with
 * the connect frame shaped exactly as {@code RelayClient.authenticate()} emits it — proves the WSS
 * capability and pinpoints the missing SDK affordance (see the test report / PORT findings).
 *
 * <p>Negative control: a second client built from an EMPTY trust store ({@link
 * TlsSupport#emptyTrustSslContext}) must have its handshake rejected, proving the cert is actually
 * verified.
 */
class TlsWssMockTest {

  private static final Gson GSON = new Gson();

  @Test
  @DisplayName("Java-WebSocket client connects + authenticates over verified wss://")
  void wssConnectAndAuthenticate() throws Exception {
    Path certs = TlsSupport.certsDir();
    SSLContext trusting = TlsSupport.trustingSslContext(certs);

    try (TlsSupport.MockHandle mock = TlsSupport.spawnTlsMockRelay()) {
      URI wss = URI.create("wss://127.0.0.1:" + mock.port + "/api/relay/ws");

      // ── Positive: CA-trusting client completes WSS + RELAY connect ──
      CompletableFuture<Map<String, Object>> connectResult = new CompletableFuture<>();
      CompletableFuture<Void> opened = new CompletableFuture<>();
      String requestId = UUID.randomUUID().toString();

      WebSocketClient client =
          new WebSocketClient(wss) {
            @Override
            public void onOpen(ServerHandshake handshake) {
              opened.complete(null);
              send(GSON.toJson(connectFrame(requestId)));
            }

            @Override
            @SuppressWarnings("unchecked")
            public void onMessage(String message) {
              Map<String, Object> frame =
                  GSON.fromJson(message, new TypeToken<Map<String, Object>>() {}.getType());
              if (frame != null
                  && requestId.equals(frame.get("id"))
                  && frame.containsKey("result")) {
                connectResult.complete((Map<String, Object>) frame.get("result"));
              }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
              if (!connectResult.isDone()) {
                connectResult.completeExceptionally(
                    new IllegalStateException("closed before connect result: " + reason));
              }
            }

            @Override
            public void onError(Exception ex) {
              if (!opened.isDone()) opened.completeExceptionally(ex);
              if (!connectResult.isDone()) connectResult.completeExceptionally(ex);
            }
          };
      // The crux: drive WSS through a SocketFactory derived from the
      // CA-trusting SSLContext. This is the only TLS wiring; nothing is
      // skipped.
      client.setSocketFactory(trusting.getSocketFactory());

      try {
        boolean connected = client.connectBlocking(15, TimeUnit.SECONDS);
        assertTrue(connected, "WSS handshake did not complete against mock_relay");
        opened.get(5, TimeUnit.SECONDS);

        Map<String, Object> result = connectResult.get(10, TimeUnit.SECONDS);

        // Behavioral proof: the mock only issues a non-empty protocol
        // string on a successful credential exchange — and it could
        // only travel back over the completed TLS WebSocket.
        Object protocol = result.get("protocol");
        assertNotNull(protocol, "connect result missing protocol over WSS");
        assertTrue(
            protocol.toString().startsWith("signalwire_"),
            "unexpected protocol over WSS: " + protocol);

        // Wire proof: the mock journaled the inbound signalwire.connect
        // frame, carried over the same (TLS) WebSocket. The journal is
        // read over the plain-HTTP control plane mock_relay keeps in
        // --tls mode.
        List<String> recv = TlsSupport.recvMethods(mock.journal());
        assertTrue(
            recv.contains("signalwire.connect"),
            "mock journal has no recv signalwire.connect over the WSS link; got " + recv);
      } finally {
        client.closeBlocking();
      }

      // ── Negative: empty-trust client must be rejected ──
      assertWssRejectedWithoutTrust(wss);
    }
  }

  /**
   * A client built from an EMPTY trust store must fail the WSS handshake against the harness's
   * CA-signed leaf cert, proving real verification. Java-WebSocket surfaces the SSL failure either
   * by returning {@code false} from {@code connectBlocking} or via {@code onError}.
   */
  private void assertWssRejectedWithoutTrust(URI wss) throws Exception {
    SSLContext empty = TlsSupport.emptyTrustSslContext();
    CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();
    WebSocketClient untrusted =
        new WebSocketClient(wss) {
          @Override
          public void onOpen(ServerHandshake h) {}

          @Override
          public void onMessage(String m) {}

          @Override
          public void onClose(int c, String r, boolean remote) {}

          @Override
          public void onError(Exception ex) {
            errorFuture.complete(ex);
          }
        };
    untrusted.setSocketFactory(empty.getSocketFactory());
    try {
      boolean connected = untrusted.connectBlocking(10, TimeUnit.SECONDS);
      assertFalse(connected, "WSS handshake with empty trust store unexpectedly succeeded");
      // An SSL handshake failure should have surfaced through onError.
      Throwable err = errorFuture.getNow(null);
      if (err != null) {
        assertTrue(
            err instanceof SSLException
                || err.getCause() instanceof SSLException
                || err.getMessage() != null,
            "expected an SSL handshake failure, got: " + err);
      }
    } finally {
      untrusted.closeBlocking();
    }
  }

  /**
   * Builds the {@code signalwire.connect} JSON-RPC frame, shaped exactly as {@code
   * RelayClient.authenticate()} emits it (version triple, agent, event_acks,
   * authentication{project,token}, contexts).
   */
  private static Map<String, Object> connectFrame(String requestId) {
    Map<String, Object> version = new LinkedHashMap<>();
    version.put("major", 2);
    version.put("minor", 4);
    version.put("revision", 0);

    Map<String, Object> auth = new LinkedHashMap<>();
    auth.put("project", "test_proj");
    auth.put("token", "test_tok");

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("version", version);
    params.put("agent", "signalwire-tls-cap-test");
    params.put("event_acks", true);
    params.put("authentication", auth);
    params.put("contexts", List.of("default"));

    Map<String, Object> frame = new LinkedHashMap<>();
    frame.put("jsonrpc", "2.0");
    frame.put("id", requestId);
    frame.put("method", "signalwire.connect");
    frame.put("params", params);
    return frame;
  }
}
