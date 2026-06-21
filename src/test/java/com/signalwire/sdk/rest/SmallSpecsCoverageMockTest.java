/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Full success+error REST coverage for the remaining SMALL canonical spec groups (14 routes):
 * {@code project.*} (3), {@code voice.*} (3), {@code fax.*} (2), {@code message.*} (2), {@code
 * calling.*} (1), {@code chat.*} (1), {@code pubsub.*} (1), {@code logs.*} (1).
 *
 * <p>For every route this exercises BOTH a success (2xx) call — asserting the response body, the
 * journalled {@code method}/{@code path}, and {@code matched_route} == the canonical endpoint id —
 * AND an error path: an armed {@link MockTest.Harness#scenarioSet} override (404/422/500) that must
 * surface as a {@link RestError} carrying the right status code, with the journal recording the
 * same {@code matched_route} and {@code response_status}.
 *
 * <p>Mirrors the DRY-helper idiom of {@code FabricCoverageMockTest}. The voice/fax/message/logs
 * routes are served by the {@code logs()} namespace; {@code calling.call-commands} by {@code
 * calling().dial(...)}; {@code chat.create_chat_token} / {@code pubsub.create_token} by the newly
 * added {@code createToken(Map)} methods. No accepted gaps — all 14 routes are reachable.
 */
class SmallSpecsCoverageMockTest {

  private RestClient client;
  private MockTest.Harness mock;

  @BeforeEach
  void setUp() {
    MockTest.Bound bound = MockTest.newClient();
    this.client = bound.client;
    this.mock = bound.harness;
  }

  private static Map<String, Object> kw(Object... entries) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      m.put((String) entries[i], entries[i + 1]);
    }
    return m;
  }

  // ── DRY helpers (each RETURNS a value asserted by the @Test body) ────

  /** Assert a successful journalled call; returns the matched route for the caller to re-assert. */
  private String okJournal(String expectedMethod, String expectedPath, String expectedRoute) {
    MockTest.JournalEntry j = mock.last();
    assertEquals(expectedMethod, j.method, "method for " + expectedRoute);
    assertEquals(expectedPath, j.path, "path for " + expectedRoute);
    assertEquals(
        expectedRoute, j.getMatchedRoute(), "unexpected matched_route: " + j.getMatchedRoute());
    return j.getMatchedRoute();
  }

  /** Arm a one-shot error, run the call, assert RestError status; returns the status code seen. */
  private int errCall(String routeId, int status, Executable call) {
    mock.scenarioSet(routeId, status, Map.of("error", "x"));
    RestError ex = assertThrows(RestError.class, call);
    MockTest.JournalEntry j = mock.last();
    assertEquals(Integer.valueOf(status), j.getResponseStatus(), "response_status for " + routeId);
    assertEquals(routeId, j.getMatchedRoute(), "matched_route for " + routeId);
    return ex.getStatusCode();
  }

  // ════════════════════════════════════════════════════════════════════
  // project.* (3) — token create / update (PATCH) / delete
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("project tokens")
  class ProjectTokens {

    @Test
    void createSuccess() {
      Map<String, Object> body = client.project().tokens().create(kw("name", "tok"));
      assertNotNull(body);
      assertEquals(
          "project.create_token", okJournal("POST", "/api/project/tokens", "project.create_token"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "project.create_token",
              422,
              () -> client.project().tokens().create(kw("name", "tok"))));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.project().tokens().update("tok-1", kw("name", "renamed"));
      assertNotNull(body);
      assertEquals(
          "project.update_token",
          okJournal("PATCH", "/api/project/tokens/tok-1", "project.update_token"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "project.update_token",
              404,
              () -> client.project().tokens().update("missing", kw("name", "renamed"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.project().tokens().delete("tok-1");
      assertNotNull(body);
      assertEquals(
          "project.delete_token",
          okJournal("DELETE", "/api/project/tokens/tok-1", "project.delete_token"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall("project.delete_token", 404, () -> client.project().tokens().delete("missing")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // voice.* (3) — list / get / listEvents (via logs().voice())
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("voice logs")
  class VoiceLogs {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.logs().voice().list();
      assertNotNull(body);
      assertEquals(
          "voice.list_voice_logs", okJournal("GET", "/api/voice/logs", "voice.list_voice_logs"));
    }

    @Test
    void listError() {
      assertEquals(500, errCall("voice.list_voice_logs", 500, () -> client.logs().voice().list()));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.logs().voice().get("vl-1");
      assertNotNull(body);
      assertEquals(
          "voice.get_voice_log", okJournal("GET", "/api/voice/logs/vl-1", "voice.get_voice_log"));
    }

    @Test
    void getError() {
      assertEquals(
          404, errCall("voice.get_voice_log", 404, () -> client.logs().voice().get("missing")));
    }

    @Test
    void listEventsSuccess() {
      Map<String, Object> body = client.logs().voice().listEvents("vl-1");
      assertNotNull(body);
      assertEquals(
          "voice.list_voice_log_events",
          okJournal("GET", "/api/voice/logs/vl-1/events", "voice.list_voice_log_events"));
    }

    @Test
    void listEventsError() {
      assertEquals(
          500,
          errCall(
              "voice.list_voice_log_events", 500, () -> client.logs().voice().listEvents("vl-1")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // fax.* (2) — list / get (via logs().fax())
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fax logs")
  class FaxLogs {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.logs().fax().list();
      assertNotNull(body);
      assertEquals("fax.list_fax_logs", okJournal("GET", "/api/fax/logs", "fax.list_fax_logs"));
    }

    @Test
    void listError() {
      assertEquals(500, errCall("fax.list_fax_logs", 500, () -> client.logs().fax().list()));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.logs().fax().get("fl-1");
      assertNotNull(body);
      assertEquals("fax.get_fax_log", okJournal("GET", "/api/fax/logs/fl-1", "fax.get_fax_log"));
    }

    @Test
    void getError() {
      assertEquals(404, errCall("fax.get_fax_log", 404, () -> client.logs().fax().get("missing")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // message.* (2) — list / get (via logs().messages())
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("message logs")
  class MessageLogs {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.logs().messages().list();
      assertNotNull(body);
      assertEquals(
          "message.list_message_logs",
          okJournal("GET", "/api/messaging/logs", "message.list_message_logs"));
    }

    @Test
    void listError() {
      assertEquals(
          500, errCall("message.list_message_logs", 500, () -> client.logs().messages().list()));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.logs().messages().get("ml-1");
      assertNotNull(body);
      assertEquals(
          "message.get_message_log",
          okJournal("GET", "/api/messaging/logs/ml-1", "message.get_message_log"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall("message.get_message_log", 404, () -> client.logs().messages().get("missing")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // logs.* (1) — conferences list (via logs().conferences())
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("conference logs")
  class ConferenceLogs {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.logs().conferences().list();
      assertNotNull(body);
      assertEquals(
          "logs.list_conferences",
          okJournal("GET", "/api/logs/conferences", "logs.list_conferences"));
    }

    @Test
    void listError() {
      assertEquals(
          500, errCall("logs.list_conferences", 500, () -> client.logs().conferences().list()));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // calling.* (1) — dial -> POST /api/calling/calls (calling.call-commands)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("calling call-commands")
  class CallingCommands {

    @Test
    void dialSuccess() {
      Map<String, Object> body =
          client.calling().dial(kw("url", "https://example.com/swml", "to", "+15551234567"));
      assertNotNull(body);
      MockTest.JournalEntry j = mock.last();
      assertEquals("dial", j.bodyMap().get("command"));
      assertEquals(
          "calling.call-commands",
          okJournal("POST", "/api/calling/calls", "calling.call-commands"));
    }

    @Test
    void dialError() {
      assertEquals(
          422,
          errCall(
              "calling.call-commands",
              422,
              () ->
                  client
                      .calling()
                      .dial(kw("url", "https://example.com/swml", "to", "+15551234567"))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // chat.* (1) — createToken -> POST /api/chat/tokens (chat.create_chat_token)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("chat create token")
  class ChatCreateToken {

    @Test
    void createTokenSuccess() {
      Map<String, Object> body =
          client.chat().createToken(kw("ttl", 3600, "channels", kw("room", kw("read", true))));
      assertNotNull(body);
      assertEquals(
          "chat.create_chat_token",
          okJournal("POST", "/api/chat/tokens", "chat.create_chat_token"));
    }

    @Test
    void createTokenError() {
      assertEquals(
          422,
          errCall("chat.create_chat_token", 422, () -> client.chat().createToken(kw("ttl", 3600))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // pubsub.* (1) — createToken -> POST /api/pubsub/tokens (pubsub.create_token)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("pubsub create token")
  class PubSubCreateToken {

    @Test
    void createTokenSuccess() {
      Map<String, Object> body =
          client.pubSub().createToken(kw("ttl", 3600, "channels", kw("room", kw("read", true))));
      assertNotNull(body);
      assertEquals(
          "pubsub.create_token", okJournal("POST", "/api/pubsub/tokens", "pubsub.create_token"));
    }

    @Test
    void createTokenError() {
      assertEquals(
          422,
          errCall("pubsub.create_token", 422, () -> client.pubSub().createToken(kw("ttl", 3600))));
    }
  }
}
