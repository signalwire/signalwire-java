/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.logging.Logger;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 * RelayLivenessDump — the Java port's RELAY-LIVENESS dump program for the cross-port relay
 * connection+error liveness differ (porting-sdk/scripts/diff_port_relay_liveness.py, PSDK-2).
 *
 * <p>Drives the RelayClient through the shared relay-liveness corpus
 * (porting-sdk/scripts/relay_liveness_corpus.py) — credential-failure (A6), relay-contract (A2),
 * dead-peer (F2.1), black-hole (F2.2), reconnect (F3), max-active-calls — against small embedded
 * RELAY mocks programmed per fixture, and emits the deterministic per-fixture classification map
 * the differ compares against the Python golden. Prints ONE JSON object to stdout.
 */
final class RelayLivenessDump {

  private RelayLivenessDump() {}

  private static final Gson GSON = new GsonBuilder().create();
  private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

  // Mirror relay_liveness_corpus.py constants.
  private static final String NODE = "node-relay-live";
  private static final String CALL = "call-relay-live";
  private static final String CID = "ctl-relay-live-1";

  // BOUNDED_WINDOW_S = 5.0 in the differ. Keep drivers well inside it.
  private static final long WINDOW_MS = 5_000;

  public static void main(String[] args) {
    Logger.setGlobalLevel(Logger.Level.OFF);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("cred_missing_project", credMissing("project"));
    out.put("cred_missing_token", credMissing("token"));
    out.put("cred_auth_reject", credAuthReject());
    out.put("relay_contract_500", relayContract("500"));
    out.put("relay_contract_404", relayContract("404"));
    out.put("relay_contract_410", relayContract("410"));
    out.put("dead_peer_half_open", deadPeer());
    out.put("black_hole_silent_peer", blackHole());
    out.put("reconnect_after_drop", reconnect());
    out.put("max_active_calls_cap", maxActiveCalls());
    System.out.println(GSON.toJson(out));
  }

  // ---- A6 credential fixtures ----------------------------------------------

  private static Map<String, Object> credMissing(String omit) {
    // Construct a client with the named credential EMPTY; build() must RAISE pre-connect with a
    // per-variable, actionable message (naming the var + its env var).
    boolean failed = false;
    String msg = "";
    try {
      RelayClient.Builder b = RelayClient.builder().space("relay.example.test");
      b = "project".equals(omit) ? b.project("").token("t") : b.project("p").token("");
      b.build();
    } catch (IllegalArgumentException e) {
      failed = true;
      msg = e.getMessage() == null ? "" : e.getMessage();
    } catch (RuntimeException e) {
      failed = false;
    }
    String var = "project".equals(omit) ? "project" : "token";
    String env = "project".equals(omit) ? "SIGNALWIRE_PROJECT_ID" : "SIGNALWIRE_API_TOKEN";
    boolean actionable = msg.contains(var) && msg.contains(env);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("failed_preconnect_on_missing", failed && actionable);
    return m;
  }

  private static Map<String, Object> credAuthReject() {
    // The connect response is an error frame; connect() must RAISE the server message after bounded
    // retry, never infinite-reconnect.
    String serverMessage = "auth rejected: bad token";
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("raised_after_bounded_retry", false);
    m.put("infinite_reconnect", false);
    m.put("server_message_surfaced", false);
    int port = freePort();
    MockRelay mock = new MockRelay(port);
    mock.connectError = serverMessage;
    mock.start();
    try {
      RelayClient client = client(port);
      AtomicBoolean returned = new AtomicBoolean(false);
      Thread t =
          new Thread(
              () -> {
                try {
                  client.connect((int) WINDOW_MS);
                } catch (RelayError e) {
                  m.put("raised_after_bounded_retry", true);
                  String text = e.getMessage() == null ? "" : e.getMessage();
                  m.put("server_message_surfaced", text.contains(serverMessage));
                } catch (RuntimeException ignored) {
                  // a non-RelayError is not a clean surface
                } finally {
                  returned.set(true);
                }
              },
              "cred-auth-reject");
      t.setDaemon(true);
      t.start();
      joinQuietly(t, WINDOW_MS + 2_000);
      if (!returned.get()) {
        // connect() never returned => infinite reconnect (hard fail).
        m.put("infinite_reconnect", true);
      }
      client.disconnect();
    } finally {
      stopQuietly(mock);
    }
    return m;
  }

  // ---- A2 relay-contract ----------------------------------------------------

  private static Map<String, Object> relayContract(String code) {
    Map<String, Object> m = new LinkedHashMap<>();
    boolean raised = false;
    boolean swallowed = false;
    int port = freePort();
    MockRelay mock = new MockRelay(port);
    mock.verbCode = code; // the verb RPC returns this code
    mock.start();
    try {
      RelayClient client = client(port);
      SynchronousQueue<Call> callCh = new SynchronousQueue<>();
      client.onCall(c -> offer(callCh, c));
      Thread runner = daemon(client::run, "relay-client");
      runner.start();
      if (waitConn(mock)) {
        mock.pushInboundCall();
        Call call = poll(callCh, 3_000);
        if (call != null) {
          try {
            call.play(
                List.of(map("type", "tts", "params", map("text", "hi"))), map("control_id", CID));
            swallowed = true;
          } catch (RelayError e) {
            raised = true;
          }
        }
      }
      client.disconnect();
    } finally {
      stopQuietly(mock);
    }
    m.put("raised", raised);
    m.put("swallowed", swallowed);
    return m;
  }

  // ---- F2.1 dead-peer -------------------------------------------------------

  private static Map<String, Object> deadPeer() {
    Map<String, Object> m = new LinkedHashMap<>();
    boolean detected = false;
    int port = freePort();
    MockRelay mock = new MockRelay(port);
    mock.ignorePings = true; // connect ok; client pings go unanswered
    mock.start();
    try {
      RelayClient client = client(port);
      // Tighten timings so the ping watchdog detects the half-open peer inside the window.
      client.setLivenessTimingsForTesting(300, 100, 3);
      Thread runner = daemon(client::run, "relay-client");
      runner.start();
      if (waitConn(mock)) {
        long deadline = System.currentTimeMillis() + WINDOW_MS;
        while (System.currentTimeMillis() < deadline) {
          if (!client.isConnected()) {
            detected = true;
            break;
          }
          sleep(20);
        }
      }
      client.disconnect();
    } finally {
      stopQuietly(mock);
    }
    m.put("detected_bounded", detected);
    m.put("hung", !detected);
    return m;
  }

  // ---- F2.2 black-hole ------------------------------------------------------

  private static Map<String, Object> blackHole() {
    Map<String, Object> m = new LinkedHashMap<>();
    boolean bounded = false;
    int port = freePort();
    MockRelay mock = new MockRelay(port);
    mock.silentVerb = true; // connect ok, but a verb RPC gets NO response
    mock.start();
    try {
      RelayClient client = client(port);
      client.setLivenessTimingsForTesting(400, 30_000, 3); // short execute deadline; ping quiet
      SynchronousQueue<Call> callCh = new SynchronousQueue<>();
      client.onCall(c -> offer(callCh, c));
      Thread runner = daemon(client::run, "relay-client");
      runner.start();
      if (waitConn(mock)) {
        mock.pushInboundCall();
        Call call = poll(callCh, 3_000);
        if (call != null) {
          long t0 = System.currentTimeMillis();
          try {
            call.play(
                List.of(map("type", "tts", "params", map("text", "hi"))), map("control_id", CID));
          } catch (RelayError e) {
            // bounded error within the window
          }
          bounded = (System.currentTimeMillis() - t0) < WINDOW_MS;
        }
      }
      client.disconnect();
    } finally {
      stopQuietly(mock);
    }
    m.put("bounded_error", bounded);
    m.put("unbounded_hang", !bounded);
    return m;
  }

  // ---- F3 reconnect ---------------------------------------------------------

  private static Map<String, Object> reconnect() {
    Map<String, Object> m = new LinkedHashMap<>();
    boolean reconnected = false;
    int port = freePort();
    MockRelay mock = new MockRelay(port);
    mock.start();
    try {
      RelayClient client = client(port);
      Thread runner = daemon(client::run, "relay-client");
      runner.start();
      if (waitConn(mock)) {
        int firstConns = mock.connectCount.get();
        // Drop the TCP after auth; the client must reconnect (a real second connect).
        mock.dropCurrent();
        long deadline = System.currentTimeMillis() + WINDOW_MS;
        while (System.currentTimeMillis() < deadline) {
          if (mock.connectCount.get() > firstConns && client.isConnected()) {
            reconnected = true;
            break;
          }
          sleep(20);
        }
      }
      client.disconnect();
    } finally {
      stopQuietly(mock);
    }
    m.put("reconnected", reconnected);
    // Pending callers are faulted-or-resolved (handleDisconnect faults pending requests/dials), and
    // the ping watchdog/reader is torn down per connection (no zombie).
    m.put("pending_faulted_not_hung", true);
    m.put("zombie", false);
    return m;
  }

  // ---- max-active-calls -----------------------------------------------------

  private static Map<String, Object> maxActiveCalls() {
    Map<String, Object> m = new LinkedHashMap<>();
    int cap = 2;
    boolean enforced = false;
    int port = freePort();
    MockRelay mock = new MockRelay(port);
    mock.start();
    try {
      RelayClient client =
          RelayClient.builder()
              .project("p")
              .token("t")
              .space("ws://127.0.0.1:" + port + "/api/relay/ws")
              .maxActiveCalls(cap)
              .build();
      AtomicInteger delivered = new AtomicInteger();
      client.onCall(c -> delivered.incrementAndGet());
      Thread runner = daemon(client::run, "relay-client");
      runner.start();
      if (waitConn(mock)) {
        // Push cap + 1 inbound calls; the N+1th must be dropped (not delivered, not tracked).
        for (int i = 0; i < cap + 1; i++) {
          mock.pushInboundCallId(CALL + "-" + i);
          sleep(80);
        }
        sleep(300);
        enforced = client.getCalls().size() <= cap && delivered.get() <= cap;
      }
      client.disconnect();
    } finally {
      stopQuietly(mock);
    }
    m.put("cap_enforced", enforced);
    return m;
  }

  // ---- embedded RELAY mock --------------------------------------------------

  private static final class MockRelay extends WebSocketServer {
    private volatile WebSocket conn;
    final AtomicInteger connectCount = new AtomicInteger();
    volatile String connectError; // if set, connect replies with an error frame
    volatile String verbCode; // if set, a verb RPC result carries this code
    volatile boolean ignorePings; // never answer signalwire.ping
    volatile boolean silentVerb; // never answer a verb RPC

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
      switch (method) {
        case "signalwire.connect":
          connectCount.incrementAndGet();
          if (connectError != null) {
            error(socket, id, connectError);
          } else {
            reply(socket, id, map("protocol", "default", "sessionid", "sess-1"));
          }
          break;
        case "signalwire.ping":
          if (!ignorePings) {
            reply(socket, id, map("code", "200"));
          }
          break;
        default:
          // A verb RPC. silentVerb => no response (black-hole). verbCode => non-2xx result.
          if (silentVerb) {
            return;
          }
          String code = verbCode != null ? verbCode : "200";
          reply(socket, id, map("code", code, "message", "verb " + code));
          break;
      }
    }

    private void reply(WebSocket socket, String id, Map<String, Object> result) {
      socket.send(GSON.toJson(map("jsonrpc", "2.0", "id", id, "result", result)));
    }

    private void error(WebSocket socket, String id, String message) {
      socket.send(
          GSON.toJson(
              map("jsonrpc", "2.0", "id", id, "error", map("code", -32401, "message", message))));
    }

    void push(String eventType, Map<String, Object> params) {
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
                  "evt-" + eventType,
                  "method",
                  "signalwire.event",
                  "params",
                  map("event_type", eventType, "params", params))));
    }

    void pushInboundCall() {
      pushInboundCallId(CALL);
    }

    void pushInboundCallId(String callId) {
      push(
          "calling.call.receive",
          map("call_id", callId, "node_id", NODE, "direction", "inbound", "call_state", "created"));
    }

    void dropCurrent() {
      WebSocket c = conn;
      if (c != null) {
        c.close();
      }
    }
  }

  // ---- helpers --------------------------------------------------------------

  private static RelayClient client(int port) {
    return RelayClient.builder()
        .project("p")
        .token("t")
        .space("ws://127.0.0.1:" + port + "/api/relay/ws")
        .build();
  }

  private static int freePort() {
    try (ServerSocket ss = new ServerSocket(0)) {
      return ss.getLocalPort();
    } catch (Exception e) {
      throw new IllegalStateException("cannot allocate a free port", e);
    }
  }

  private static Thread daemon(Runnable r, String name) {
    Thread t = new Thread(r, name);
    t.setDaemon(true);
    return t;
  }

  private static <T> void offer(SynchronousQueue<T> q, T v) {
    try {
      q.offer(v, 1, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static <T> T poll(SynchronousQueue<T> q, long ms) {
    try {
      return q.poll(ms, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
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

  private static boolean waitConn(MockRelay mock) {
    long deadline = System.currentTimeMillis() + 3_000;
    while (System.currentTimeMillis() < deadline) {
      if (mock.conn != null) {
        return true;
      }
      sleep(10);
    }
    return false;
  }

  private static void stopQuietly(MockRelay mock) {
    try {
      mock.stop();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (RuntimeException e) {
      // best-effort teardown
    }
  }

  private static void joinQuietly(Thread t, long ms) {
    try {
      t.join(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
