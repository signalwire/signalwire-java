/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mock-relay-backed proof for the {@link Call} overloads that express the reference's OPTIONAL
 * keyword params by omitting Java's trailing options bag.
 *
 * <p>The reference declares these params keyword-only with a {@code None} default ({@code
 * status_url} / {@code prompt} / {@code event}); Java folds them into a trailing {@code
 * Map<String,Object> options}, so "the caller omitted it" is spelled as the no-bag overload. Each
 * test asserts the RELAY frame the SHORT form puts on the wire is identical to the one the LONG
 * form produces when the bag is passed explicitly as {@code null} — comparing against the
 * explicitly-defaulted call, so forwarding some other value would fail the test.
 *
 * <p>Assertions read the shared mock's receive journal, scoped to this test's own client, so the
 * suite is parallel-safe.
 */
class CallReferenceDefaultOverloadMockTest {

  private RelayClient client;
  private RelayMockTest.Harness mock;

  @BeforeEach
  void setUp() {
    this.mock = RelayMockTest.harness();
    this.client =
        RelayClient.builder()
            .project("test_proj")
            .token("test_tok")
            .space(mock.wsUrl())
            .contexts(List.of("default"))
            .build();
    client.connect(10_000);
    try {
      Thread.sleep(50);
    } catch (InterruptedException ignored) {
    }
    this.mock = mock.scopedTo(client);
  }

  @AfterEach
  void tearDown() {
    if (client != null) {
      try {
        client.disconnect();
      } catch (Exception ignored) {
      }
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────

  private static Map<String, Object> phoneDevice() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("to_number", "+15551112222");
    params.put("from_number", "+15553334444");
    Map<String, Object> device = new LinkedHashMap<>();
    device.put("type", "phone");
    device.put("params", params);
    return device;
  }

  private Call dialAnswered(String tag, String callId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tag", tag);
    body.put("winner_call_id", callId);
    body.put("states", List.of("created", "answered"));
    body.put("node_id", "node-mock-1");
    body.put("device", phoneDevice());
    body.put("delay_ms", 1);
    mock.armDial(body);

    Map<String, Object> opts = new LinkedHashMap<>();
    opts.put("tag", tag);
    List<List<Map<String, Object>>> devices = List.of(List.of(phoneDevice()));
    Call call = client.dial(devices, opts, 5_000);
    assertNotNull(call, "dial did not resolve to a Call");
    return call;
  }

  /** The params of the last journalled frame for {@code method}. */
  private Map<String, Object> lastParams(String method) {
    List<RelayMockTest.JournalEntry> entries = mock.journalRecv(method);
    assertFalse(entries.isEmpty(), "no " + method + " frame in journal");
    return entries.get(entries.size() - 1).params();
  }

  /** Run {@code verb}, then read the frame it produced. */
  private Map<String, Object> frameFor(String method, Runnable verb) {
    verb.run();
    return lastParams(method);
  }

  // ── The overloads ────────────────────────────────────────────────

  /** signalwire/relay/call.py:974 — refer(device, *, status_url=None, **kwargs) */
  @Test
  @DisplayName("refer(device) == refer(device, null) on the wire")
  void referDefaultsOptionsToNull() {
    Call call = dialAnswered("t-refer", "C-REFER");
    // The RELAY schema requires device.params, so use a well-formed SIP device spec.
    Map<String, Object> device =
        Map.of("type", "sip", "params", Map.of("to", "sip:agent@example.com"));
    var longForm = frameFor(Constants.METHOD_REFER, () -> call.refer(device, null));
    var shortForm = frameFor(Constants.METHOD_REFER, () -> call.refer(device));
    assertEquals(longForm, shortForm);
    assertFalse(shortForm.containsKey("status_url"), "omitted status_url must not be emitted");
  }

  /** signalwire/relay/call.py:1412 — join_room(name, *, status_url=None, **kwargs) */
  @Test
  @DisplayName("joinRoom(name) == joinRoom(name, null) on the wire")
  void joinRoomDefaultsOptionsToNull() {
    Call call = dialAnswered("t-room", "C-ROOM");
    var longForm = frameFor(Constants.METHOD_JOIN_ROOM, () -> call.joinRoom("room-1", null));
    var shortForm = frameFor(Constants.METHOD_JOIN_ROOM, () -> call.joinRoom("room-1"));
    assertEquals(longForm, shortForm);
    assertFalse(shortForm.containsKey("status_url"), "omitted status_url must not be emitted");
  }

  /** signalwire/relay/call.py:1394 — live_translate(action, *, status_url=None, **kwargs) */
  @Test
  @DisplayName("liveTranslate(action) == liveTranslate(action, null) on the wire")
  void liveTranslateDefaultsOptionsToNull() {
    Call call = dialAnswered("t-lt", "C-LT");
    Map<String, Object> action = Map.of("action", "start");
    var longForm =
        frameFor(Constants.METHOD_LIVE_TRANSLATE, () -> call.liveTranslate(action, null));
    var shortForm = frameFor(Constants.METHOD_LIVE_TRANSLATE, () -> call.liveTranslate(action));
    assertEquals(longForm, shortForm);
    assertFalse(shortForm.containsKey("status_url"), "omitted status_url must not be emitted");
  }

  /** signalwire/relay/call.py:1550 — ai_unhold(*, prompt=None, **kwargs) */
  @Test
  @DisplayName("aiUnhold() == aiUnhold(null) on the wire")
  void aiUnholdDefaultsOptionsToNull() {
    Call call = dialAnswered("t-unhold", "C-UNHOLD");
    var longForm = frameFor(Constants.METHOD_AI_UNHOLD, () -> call.aiUnhold(null));
    var shortForm = frameFor(Constants.METHOD_AI_UNHOLD, () -> call.aiUnhold());
    assertEquals(longForm, shortForm);
    assertFalse(shortForm.containsKey("prompt"), "omitted prompt must not be emitted");
  }

  /**
   * signalwire/relay/call.py:1567 — user_event(*, event=None, **kwargs).
   *
   * <p>NOTE: the authoritative RELAY schema (relay-protocol/calling.user_event.params.json,
   * extracted from switchblade {@code PublicCallUserEventParams.cs}) lists {@code event} in its
   * {@code required} set, while the reference declares it {@code event: str | None = None} and
   * omits the key when it is None. So the all-defaults call is REJECTED by the server with {@code
   * -32602 'event' is a required property} — the reference's default is unsendable. That divergence
   * is reported, not papered over here; this test proves the two forms agree on the frame they
   * BUILD (the surface contract), and pins the rejection so the wire behaviour is recorded.
   */
  @Test
  @DisplayName("userEvent() == userEvent(null); both are rejected by the wire schema")
  void userEventDefaultsEventToNull() {
    Call call = dialAnswered("t-ue", "C-UE");
    // Supplying the event is the sendable path, and the key rides through unchanged.
    var supplied = frameFor(Constants.METHOD_USER_EVENT, () -> call.userEvent("ping"));
    assertEquals("ping", supplied.get("event"));
    // Both no-arg forms omit `event`, so both hit the same schema rejection — identical behaviour,
    // which is what the overload has to guarantee.
    var fromShort = assertThrows(RelayError.class, () -> call.userEvent());
    var fromLong = assertThrows(RelayError.class, () -> call.userEvent(null));
    assertEquals(fromLong.getMessage(), fromShort.getMessage());
  }
}
