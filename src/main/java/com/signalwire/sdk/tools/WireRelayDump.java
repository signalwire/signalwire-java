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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 * WireRelayDump — the Java port's WIRE-RELAY dump program for the cross-port relay differ
 * (porting-sdk/scripts/diff_port_wire_relay.py).
 *
 * <p>It captures, for each {@code wire_relay_corpus} case, the observable RELAY artifact:
 *
 * <ul>
 *   <li><b>verb</b>: the {@code {method, params}} JSON-RPC frame a Call verb (or an Action
 *       control-op) hands to the wire.
 *   <li><b>client</b>: the {@code {method, params}} frame a RelayClient call (execute / dial /
 *       send_message) sends.
 *   <li><b>event</b>: the decoded fields a typed event decoder extracts from a payload.
 * </ul>
 *
 * <p>It prints ONE JSON object mapping case-id -&gt; artifact to stdout; the differ canonicalizes
 * both sides (normalizing the random control_id to a sentinel) and byte-compares against the Python
 * oracle. Only stdout carries JSON. Mirrors Go's {@code cmd/wire-relay-dump/main.go}.
 *
 * <p>Frame capture: verb/client verbs send over a real WebSocket, so this program stands up a tiny
 * in-process mock RELAY WS server on a loopback port (pointed to via {@code .space("ws://host:port/
 * api/relay/ws")}), completes the connect handshake, records each {@code calling.*}/{@code
 * messaging.*} frame, and replies with a canned success. Event decoding is pure (no wire).
 *
 * <p>Run via the {@code wireRelayDump} Gradle task:
 *
 * <pre>
 *   ./gradlew --no-daemon -q wireRelayDump
 * </pre>
 */
final class WireRelayDump {

  private WireRelayDump() {}

  private static final String NODE = "node-abc";
  private static final String CALL = "call-xyz";
  private static final String CID = "ctl-123";

  private static final Gson GSON =
      new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
  private static final java.lang.reflect.Type MAP_TYPE =
      new TypeToken<Map<String, Object>>() {}.getType();

  /**
   * A minimal in-process RELAY WebSocket mock. Answers {@code signalwire.connect}, records every
   * {@code calling.*}/{@code messaging.*} frame, and replies with a canned success. On {@code
   * calling.dial} it can push a {@code calling.call.dial} answered event to resolve the client's
   * dial().
   */
  private static final class MockRelay extends WebSocketServer {
    private final Map<String, Map<String, Object>> frames = new ConcurrentHashMap<>();
    private volatile WebSocket conn;
    private volatile String dialArm; // when set, resolve dial for this tag on the next calling.dial

    MockRelay(int port) {
      super(new InetSocketAddress("127.0.0.1", port));
      setReuseAddr(true);
    }

    Map<String, Object> lastFrame(String method) {
      return frames.get(method);
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
      // logged to stderr only; never stdout
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
      // An ACK/response to a server push (has result, no method) — ignore.
      if (method == null || method.isEmpty()) {
        return;
      }
      Map<String, Object> params =
          msg.get("params") instanceof Map ? (Map<String, Object>) msg.get("params") : Map.of();

      switch (method) {
        case "signalwire.connect":
          reply(socket, id, map("protocol", "default", "sessionid", "sess-1"));
          break;
        case "signalwire.receive":
          reply(socket, id, map("code", "200"));
          break;
        case "calling.dial":
          frames.put(method, params);
          reply(socket, id, map("code", "200", "message", "Dialing"));
          if (dialArm != null) {
            pushDialAnswered(socket, dialArm);
          }
          break;
        case "messaging.send":
          frames.put(method, params);
          reply(socket, id, map("code", "200", "message_id", "msg-1"));
          break;
        default:
          frames.put(method, params);
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

    private void pushDialAnswered(WebSocket socket, String tag) {
      sleep(20);
      push(
          "calling.call.dial",
          map("tag", tag, "dial_state", "answered", "call", map("call_id", CALL, "node_id", NODE)));
    }
  }

  /** Ordered map helper. */
  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  @SafeVarargs
  private static <T> List<T> list(T... items) {
    return new ArrayList<>(Arrays.asList(items));
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Wrap a captured params map as {method, params}. */
  private static Map<String, Object> frame(String method, Map<String, Object> params) {
    return map("method", method, "params", params);
  }

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
      decodeEvents(out);
      captureFrames(mock, port, out);
    } finally {
      mock.stop();
    }

    System.out.println(GSON.toJson(out));
  }

  private static void captureFrames(MockRelay mock, int port, Map<String, Object> out)
      throws Exception {
    RelayClient client =
        RelayClient.builder()
            .project("proj-1")
            .token("tok-1")
            .space("ws://127.0.0.1:" + port + "/api/relay/ws")
            .build();

    java.util.concurrent.SynchronousQueue<Call> callCh =
        new java.util.concurrent.SynchronousQueue<>();
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

    // Wait for the handshake, then push the inbound call.
    if (!waitConn(mock)) {
      throw new IllegalStateException("client did not connect");
    }
    mock.pushInboundCall();

    Call c = callCh.poll(3, java.util.concurrent.TimeUnit.SECONDS);
    if (c == null) {
      throw new IllegalStateException("inbound call not delivered to onCall");
    }

    // relay_play
    c.play(
        list(map("type", "audio", "params", map("url", "https://x/a.mp3"))),
        map("volume", 5.0, "control_id", CID));
    settle();
    out.put("relay_play", frame("calling.play", mock.lastFrame("calling.play")));

    // relay_play_tts
    c.playTts("Hello world", map("voice", "en-US-Neural"));
    settle();
    out.put("relay_play_tts", frame("calling.play", mock.lastFrame("calling.play")));

    // relay_record
    c.record(map("audio", map("format", "mp3", "beep", true)), map("control_id", CID));
    settle();
    out.put("relay_record", frame("calling.record", mock.lastFrame("calling.record")));

    // relay_connect
    c.connect(
        list(list(map("type", "phone", "params", map("to_number", "+15551112222")))),
        map(
            "ringback",
            list(map("type", "ringtone", "params", map("name", "us"))),
            "tag",
            "leg-1",
            "max_duration",
            3600));
    settle();
    out.put("relay_connect", frame("calling.connect", mock.lastFrame("calling.connect")));

    // relay_collect
    c.collect(
        map(
            "digits",
            map("max", 4, "terminators", "#"),
            "speech",
            map("language", "en-US"),
            "initial_timeout",
            5.0,
            "partial_results",
            true),
        map("control_id", CID));
    settle();
    out.put("relay_collect", frame("calling.collect", mock.lastFrame("calling.collect")));

    // relay_prompt (play_and_collect)
    c.promptTts("Enter your PIN", map("digits", map("max", 4)), map("voice", "en-US-Neural"));
    settle();
    out.put(
        "relay_prompt",
        frame("calling.play_and_collect", mock.lastFrame("calling.play_and_collect")));

    // relay_detect
    c.detect(map("type", "machine", "params", map("initial_timeout", 4.0)), map("timeout", 30.0));
    settle();
    out.put("relay_detect", frame("calling.detect", mock.lastFrame("calling.detect")));

    // relay_detect_amd
    c.detectAnsweringMachine(
        map("initial_timeout", 4.0, "machine_words_threshold", 6, "timeout", 30.0));
    settle();
    out.put("relay_detect_amd", frame("calling.detect", mock.lastFrame("calling.detect")));

    // relay_tap
    c.tap(
        map("type", "audio", "params", map("direction", "both")),
        map("type", "ws", "params", map("uri", "wss://x/tap")),
        CID);
    settle();
    out.put("relay_tap", frame("calling.tap", mock.lastFrame("calling.tap")));

    // relay_send_fax
    c.sendFax(
        "https://x/doc.pdf",
        map("identity", "+15550001111", "header_info", "Hdr", "control_id", CID));
    settle();
    out.put("relay_send_fax", frame("calling.send_fax", mock.lastFrame("calling.send_fax")));

    // relay_live_transcribe: caller's `action` MUST be wrapped as params.action.
    c.liveTranscribe(map("start", map("lang", "en")));
    settle();
    out.put(
        "relay_live_transcribe",
        frame("calling.live_transcribe", mock.lastFrame("calling.live_transcribe")));

    // relay_live_translate: params.action REQUIRED + status_url optional sibling param.
    c.liveTranslate(
        map("start", map("from_lang", "en", "to_lang", "es")), map("status_url", "https://x/cb"));
    settle();
    out.put(
        "relay_live_translate",
        frame("calling.live_translate", mock.lastFrame("calling.live_translate")));

    // ---- control-ops (Action methods) ----
    // relay_play_stop
    Action.PlayAction pa =
        c.play(
            list(map("type", "audio", "params", map("url", "https://x/a.mp3"))),
            map("control_id", CID));
    settle();
    pa.stop();
    settle();
    out.put("relay_play_stop", frame("calling.play.stop", mock.lastFrame("calling.play.stop")));

    // relay_play_pause
    Action.PlayAction pa2 =
        c.play(
            list(map("type", "audio", "params", map("url", "https://x/a.mp3"))),
            map("control_id", CID));
    settle();
    pa2.pause("silence");
    settle();
    out.put("relay_play_pause", frame("calling.play.pause", mock.lastFrame("calling.play.pause")));

    // relay_record_resume
    Action.RecordAction ra = c.record(map("audio", map("format", "mp3")), map("control_id", CID));
    settle();
    ra.resume();
    settle();
    out.put(
        "relay_record_resume",
        frame("calling.record.resume", mock.lastFrame("calling.record.resume")));

    // relay_play_volume
    Action.PlayAction pa3 =
        c.play(
            list(map("type", "audio", "params", map("url", "https://x/a.mp3"))),
            map("control_id", CID));
    settle();
    pa3.volume(3.5);
    settle();
    out.put(
        "relay_play_volume", frame("calling.play.volume", mock.lastFrame("calling.play.volume")));

    // ---- RelayClient-level frames ----
    // relay_client_execute
    client.execute("calling.answer", map("node_id", NODE, "call_id", CALL));
    settle();
    out.put("relay_client_execute", frame("calling.answer", mock.lastFrame("calling.answer")));

    // relay_send_message (context defaults to the post-handshake protocol "default")
    client.sendMessage(null, "+15553334444", "+15551112222", "hi", null, list("t1"));
    settle();
    out.put("relay_send_message", frame("messaging.send", mock.lastFrame("messaging.send")));

    // relay_dial (arm the dial-answer push for tag "dial-1")
    mock.dialArm = "dial-1";
    client.dial(
        list(list(map("type", "phone", "params", map("to_number", "+15551112222")))),
        map("tag", "dial-1", "max_duration", 600),
        5_000);
    settle();
    out.put("relay_dial", frame("calling.dial", mock.lastFrame("calling.dial")));

    client.disconnect();
  }

  private static void settle() {
    sleep(80);
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

  /** Run the pure typed-event decoders (no wire). */
  private static void decodeEvents(Map<String, Object> out) {
    // relay_evt_queue
    RelayEvent.QueueEvent q =
        new RelayEvent.QueueEvent(
            "calling.queue",
            0.0,
            map(
                "call_id",
                CALL,
                "control_id",
                CID,
                "status",
                "waiting",
                "id",
                "q-42",
                "name",
                "support",
                "position",
                3.0,
                "size",
                10.0));
    out.put(
        "relay_evt_queue",
        map(
            "control_id", q.getControlId(),
            "status", q.getStatus(),
            "queue_id", q.getQueueId(),
            "queue_name", q.getQueueName(),
            "position", q.getPosition(),
            "size", q.getSize()));

    // relay_evt_record
    RelayEvent.CallRecordEvent rec =
        new RelayEvent.CallRecordEvent(
            "calling.call.record",
            0.0,
            map(
                "call_id",
                CALL,
                "control_id",
                CID,
                "state",
                "finished",
                "record",
                map("url", "https://x/rec.mp3", "duration", 12.5, "size", 4096.0)));
    out.put(
        "relay_evt_record",
        map(
            "control_id", rec.getControlId(),
            "state", rec.getState(),
            "url", rec.getUrl(),
            "duration", rec.getDuration(),
            "size", rec.getSize()));

    // relay_evt_state_dispatch (parseEvent -> CallStateEvent)
    RelayEvent obj =
        RelayEvent.parseEvent(
            map(
                "event_type",
                "calling.call.state",
                "params",
                map(
                    "call_id",
                    CALL,
                    "call_state",
                    "answered",
                    "direction",
                    "inbound",
                    "end_reason",
                    "")));
    Map<String, Object> stateOut = new LinkedHashMap<>();
    stateOut.put("_class", obj.getClass().getSimpleName());
    if (obj instanceof RelayEvent.CallStateEvent cse) {
      stateOut.put("call_id", cse.getCallId());
      stateOut.put("call_state", cse.getCallState());
      stateOut.put("direction", cse.getDirection());
    }
    out.put("relay_evt_state_dispatch", stateOut);

    // relay_evt_collect
    RelayEvent.CallCollectEvent col =
        new RelayEvent.CallCollectEvent(
            "calling.call.collect",
            0.0,
            map(
                "call_id",
                CALL,
                "control_id",
                CID,
                "state",
                "finished",
                "result",
                map("type", "digit", "params", map("digits", "1234")),
                "final",
                true));
    out.put(
        "relay_evt_collect",
        map(
            "control_id", col.getControlId(),
            "state", col.getState(),
            "result", col.getResult(),
            "final", col.getFinal()));
  }
}
