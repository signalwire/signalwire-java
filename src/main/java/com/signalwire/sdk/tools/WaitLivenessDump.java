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
import com.signalwire.sdk.relay.Action;
import com.signalwire.sdk.relay.Call;
import com.signalwire.sdk.relay.RelayClient;
import com.signalwire.sdk.relay.RelayEvent;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 * WaitLivenessDump — the Java port's WAIT-LIVENESS dump program for the cross-port liveness differ
 * (porting-sdk/scripts/diff_port_wait_liveness.py).
 *
 * <p>For each liveness case (play, record) it drives a REAL {@link Call} verb against a tiny
 * embedded RELAY WebSocket mock, arms the completing event to arrive {@code DELAY_MS} AFTER {@code
 * waitForCompletion()} begins, measures the wall-clock instants, and derives the same deterministic
 * LIVENESS CLASSIFICATION the differ compares against the Python golden:
 *
 * <pre>
 *   {"blocked_until_event": bool, "returned_after_event": bool,
 *    "completed_state": str, "timed_out": bool}
 * </pre>
 *
 * <p>A {@code waitForCompletion()} that returns immediately (a no-op that never observes the event)
 * yields {@code blocked_until_event=false} → RED; one that hangs blows the deadline → {@code
 * timed_out=true} → RED; a correct wait blocks until the event then returns → the golden
 * classification → GREEN. The classification is unfakeable: you cannot report {@code
 * blocked_until_event=true} without actually having blocked past the delay.
 *
 * <p>Only stdout carries JSON; the SDK Logger is silenced. Run via the {@code waitLivenessDump}
 * Gradle task:
 *
 * <pre>
 *   ./gradlew --no-daemon -q waitLivenessDump
 * </pre>
 */
final class WaitLivenessDump {

  private WaitLivenessDump() {}

  private static final Gson GSON = new GsonBuilder().create();
  private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

  // Mirror wait_liveness_corpus.py constants EXACTLY so the completing event targets
  // the same call/action and the classification lines up with the golden.
  private static final String NODE = "node-abc";
  private static final String CALL = "call-xyz";
  private static final String CID = "ctl-live-1";
  private static final int DELAY_MS = 150;
  // Mirror diff_port_wait_liveness.py: DEADLINE_S=5.0, BLOCK_TOL_MS=40.
  private static final long DEADLINE_MS = 5_000;
  private static final long BLOCK_TOL_MS = 40;

  public static void main(String[] args) throws Exception {
    Logger.setGlobalLevel(Logger.Level.OFF);

    int port;
    try (ServerSocket ss = new ServerSocket(0)) {
      port = ss.getLocalPort();
    }
    MockRelay mock = new MockRelay(port);
    mock.start();

    Map<String, Object> out = new LinkedHashMap<>();
    try {
      RelayClient client =
          RelayClient.builder()
              .project("proj-1")
              .token("tok-1")
              .space("ws://127.0.0.1:" + port + "/api/relay/ws")
              .build();

      SynchronousQueue<Call> callCh = new SynchronousQueue<>();
      client.onCall(
          c -> {
            try {
              callCh.put(c);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });

      Thread runner = new Thread(client::run, "relay-client");
      runner.setDaemon(true);
      runner.start();

      if (!waitConn(mock)) {
        throw new IllegalStateException("client did not connect");
      }
      mock.pushInboundCall();
      Call call = callCh.poll(3, TimeUnit.SECONDS);
      if (call == null) {
        throw new IllegalStateException("inbound call not delivered to onCall");
      }

      out.put("live_play_wait", runPlay(mock, call));
      out.put("live_record_wait", runRecord(mock, call));
    } finally {
      mock.stop();
    }

    System.out.println(GSON.toJson(out));
  }

  // ---- cases ----------------------------------------------------------------

  private static Map<String, Object> runPlay(MockRelay mock, Call call) {
    Action action =
        call.play(
            List.of(map("type", "audio", "params", map("url", "https://x/a.mp3"))),
            map("control_id", CID));
    return classify(mock, action, "calling.call.play");
  }

  private static Map<String, Object> runRecord(MockRelay mock, Call call) {
    Action action = call.record(map("audio", map("format", "mp3")), map("control_id", CID));
    return classify(mock, action, "calling.call.record");
  }

  /**
   * Arm the completing event {@code DELAY_MS} after wait begins, block on {@code
   * waitForCompletion(DEADLINE_MS)}, and derive the liveness classification from the measured
   * instants.
   */
  private static Map<String, Object> classify(MockRelay mock, Action action, String eventType) {
    long tWaitStart = System.nanoTime();
    Thread arm =
        new Thread(
            () -> {
              sleep(DELAY_MS);
              mock.pushActionEvent(eventType, CID, "finished");
            },
            "liveness-arm");
    arm.setDaemon(true);
    arm.start();

    RelayEvent terminal = action.waitForCompletion(DEADLINE_MS);
    long tReturn = System.nanoTime();

    Map<String, Object> result = new LinkedHashMap<>();
    if (terminal == null) {
      // wait() blew the deadline (or returned null) → hung / no terminal event observed.
      result.put("blocked_until_event", false);
      result.put("returned_after_event", false);
      result.put("completed_state", "");
      result.put("timed_out", true);
      return result;
    }
    double elapsedMs = (tReturn - tWaitStart) / 1_000_000.0;
    boolean blocked = elapsedMs >= (DELAY_MS - BLOCK_TOL_MS);
    String completedState = terminal.getStringParam("state");
    result.put("blocked_until_event", blocked);
    result.put("returned_after_event", true);
    result.put("completed_state", completedState == null ? "" : completedState);
    result.put("timed_out", false);
    return result;
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
    public void onClose(WebSocket socket, int code, String reason, boolean remote) {
      // no-op
    }

    @Override
    public void onError(WebSocket socket, Exception ex) {
      System.err.println("mock relay error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
      // no-op
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onMessage(WebSocket socket, String raw) {
      Map<String, Object> msg;
      try {
        msg = GSON.fromJson(raw, MAP_TYPE);
      } catch (Exception e) {
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
          reply(socket, id, map("protocol", "default", "sessionid", "sess-1"));
          break;
        case "signalwire.receive":
          reply(socket, id, map("code", "200"));
          break;
        default:
          // The verb RPC (calling.play / calling.record) just needs to succeed so the
          // Action starts; completion is driven separately by pushActionEvent().
          reply(socket, id, map("code", "200"));
          break;
      }
    }

    private void reply(WebSocket socket, String id, Map<String, Object> result) {
      socket.send(GSON.toJson(map("jsonrpc", "2.0", "id", id, "result", result)));
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
      push(
          "calling.call.receive",
          map("call_id", CALL, "node_id", NODE, "direction", "inbound", "call_state", "created"));
    }

    /** Emit an action completing event routed by call_id + control_id + state. */
    void pushActionEvent(String eventType, String controlId, String state) {
      push(
          eventType,
          map("call_id", CALL, "node_id", NODE, "control_id", controlId, "state", state));
    }
  }

  // ---- helpers --------------------------------------------------------------

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

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
