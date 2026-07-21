/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.relay.RelayClient;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 * SecretScrubDump — the Java port's SECRET-SCRUB-LIVE dump program for the cross-port secret-scrub
 * differ (porting-sdk/scripts/diff_port_secret_scrub.py, PSDK-5).
 *
 * <p>Drives the RelayClient through a real connect + an inbound {@code
 * signalwire.authorization.state} event at {@code SIGNALWIRE_LOG_LEVEL=debug} with the fixture
 * sentinel credentials (project= {@code PJ-TESTLEAK}, token={@code PT-TESTLEAK},
 * authorization_state={@code AENC-TESTLEAK}), captures its OWN stdout+stderr, and reports
 * per-sentinel {@code {leaked: bool}} — True iff the sentinel string appears verbatim in the
 * captured output. All must be False: a port that logs the raw connect frame ({@code >>}) or the
 * raw inbound frame ({@code <<}) at debug leaks the sentinel and reds.
 *
 * <p>Prints ONE JSON object mapping sentinel-id -&gt; classification to stdout (on the RESTORED
 * stdout, after capture ends).
 */
final class SecretScrubDump {

  private SecretScrubDump() {}

  private static final Gson GSON = new GsonBuilder().create();
  private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

  // Byte-identical to secret_scrub_corpus.py.
  private static final String PROJECT = "PJ-TESTLEAK";
  private static final String TOKEN = "PT-TESTLEAK";
  private static final String AUTHORIZATION_STATE = "AENC-TESTLEAK";

  public static void main(String[] args) throws Exception {
    // Capture this process's OWN stdout+stderr while the client runs at debug.
    PrintStream realOut = System.out;
    PrintStream realErr = System.err;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream cap = new PrintStream(captured, true, StandardCharsets.UTF_8);

    int port;
    try (ServerSocket ss = new ServerSocket(0)) {
      port = ss.getLocalPort();
    }
    MockRelay mock = new MockRelay(port);

    System.setOut(cap);
    System.setErr(cap);
    try {
      // Route the SDK logger through the CAPTURED streams at debug level.
      Logger.setGlobalLevel(Logger.Level.DEBUG);
      mock.start();

      RelayClient client =
          RelayClient.builder()
              .project(PROJECT)
              .token(TOKEN)
              .space("ws://127.0.0.1:" + port + "/api/relay/ws")
              .build();

      Thread runner = new Thread(client::run, "relay-client");
      runner.setDaemon(true);
      runner.start();

      waitConn(mock, 3_000);
      // Give the connect >> frame a moment, then push the inbound authorization.state event whose
      // params carry the sentinel re-auth blob — a debug << raw-frame log would leak it.
      Thread.sleep(200);
      mock.pushAuthorizationState(AUTHORIZATION_STATE);
      Thread.sleep(200);

      client.disconnect();
      mock.stop();
    } finally {
      Logger.setGlobalLevel(Logger.Level.OFF);
      System.out.flush();
      System.err.flush();
      System.setOut(realOut);
      System.setErr(realErr);
    }

    String log = captured.toString(StandardCharsets.UTF_8);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("project", leaked(log, PROJECT));
    out.put("token", leaked(log, TOKEN));
    out.put("authorization_state", leaked(log, AUTHORIZATION_STATE));
    System.out.println(GSON.toJson(out));
  }

  private static Map<String, Object> leaked(String log, String sentinel) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("leaked", log.contains(sentinel));
    return m;
  }

  // ---- embedded RELAY mock --------------------------------------------------

  private static final class MockRelay extends WebSocketServer {
    private volatile WebSocket conn;

    MockRelay(int port) {
      super(new InetSocketAddress("127.0.0.1", port));
      setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket socket, ClientHandshake handshake) {
      this.conn = socket;
    }

    @Override
    public void onClose(WebSocket socket, int code, String reason, boolean remote) {}

    @Override
    public void onError(WebSocket socket, Exception ex) {}

    @Override
    public void onStart() {}

    @Override
    public void onMessage(WebSocket socket, String raw) {
      Map<String, Object> msg;
      try {
        msg = GSON.fromJson(raw, MAP_TYPE);
      } catch (RuntimeException e) {
        return;
      }
      if (msg == null) {
        return;
      }
      String id = str(msg.get("id"));
      String method = str(msg.get("method"));
      if (method == null || method.isEmpty()) {
        return; // an ACK to a server push
      }
      if ("signalwire.connect".equals(method)) {
        reply(socket, id, map("protocol", "default", "sessionid", "sess-1"));
      } else {
        reply(socket, id, map("code", "200", "message", "OK"));
      }
    }

    private void reply(WebSocket socket, String id, Map<String, Object> result) {
      socket.send(GSON.toJson(map("jsonrpc", "2.0", "id", id, "result", result)));
    }

    /** Push a signalwire.authorization.state event whose params carry the sentinel re-auth blob. */
    void pushAuthorizationState(String sentinel) {
      WebSocket c = conn;
      if (c == null) {
        return;
      }
      c.send(
          GSON.toJson(
              map(
                  "jsonrpc",
                  "2.0",
                  "id",
                  "evt-auth",
                  "method",
                  "signalwire.event",
                  "params",
                  map(
                      "event_type",
                      "signalwire.authorization.state",
                      "params",
                      map("authorization_state", sentinel)))));
    }
  }

  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }

  private static void waitConn(MockRelay mock, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (mock.conn != null) {
        return;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }
}
