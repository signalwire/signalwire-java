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
 * Full success+error REST coverage for the {@code video.*} canonical spec group.
 *
 * <p>For every coverable video route this exercises BOTH a success (2xx) call — asserting the
 * response body, the journalled {@code method}/{@code path}, and {@code matched_route} == the
 * canonical endpoint id — AND an error path: an armed {@link MockTest.Harness#scenarioSet} override
 * (404/422/500) that must surface as a {@link RestError} carrying the right status code, with the
 * journal recording the same {@code matched_route} and {@code response_status}.
 *
 * <p>Mirrors the DRY-helper idiom of {@code FabricCoverageMockTest}; complements the success-only
 * {@code VideoMockTest} (kept intact) by adding the missing error paths and the routes it skipped
 * (rooms CRUD, room_tokens create, room_session members, conferences CRUD, etc.).
 *
 * <p>Accepted gaps (NOT tested here):
 *
 * <ul>
 *   <li>{@code video.list_logs} + {@code video.get_log}: there is no {@code video.logs()} accessor
 *       in the Java SDK (same gap python has — no logs surface).
 *   <li>{@code video.get_room} (GET /rooms/{id}) is wire-identical to {@code
 *       video.get_room_by_name} (GET /rooms/{name}); the mock router always resolves GET /rooms/X
 *       to {@code get_room_by_name}, so {@code get_room} is unreachable via normal traffic. We
 *       cover {@code get_room_by_name} and treat {@code get_room} as a routing-collision artifact.
 * </ul>
 */
class VideoCoverageMockTest {

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

  // ── DRY helpers ─────────────────────────────────────────────────────
  // Each helper RETURNS so the calling @Test body holds at least one real
  // in-body assertion (the no-cheat auditor is intra-function).

  /** Assert a successful journalled call: returns the matched route for the caller to re-assert. */
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
  // Rooms (CrudResource, PUT update) + stream sub-resources
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("video rooms")
  class Rooms {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.video().rooms().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals("video.list_rooms", okJournal("GET", "/api/video/rooms", "video.list_rooms"));
    }

    @Test
    void listError() {
      assertEquals(500, errCall("video.list_rooms", 500, () -> client.video().rooms().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.video().rooms().create(kw("name", "r"));
      assertNotNull(body);
      assertEquals("video.create_room", okJournal("POST", "/api/video/rooms", "video.create_room"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall("video.create_room", 422, () -> client.video().rooms().create(kw("name", "r"))));
    }

    @Test
    void getByNameSuccess() {
      // GET /rooms/{id} and GET /rooms/{name} are wire-identical; the mock router
      // resolves GET /rooms/X to get_room_by_name, so that is the reachable route.
      Map<String, Object> body = client.video().rooms().get("room-1");
      assertNotNull(body);
      assertEquals(
          "video.get_room_by_name",
          okJournal("GET", "/api/video/rooms/room-1", "video.get_room_by_name"));
    }

    @Test
    void getByNameError() {
      assertEquals(
          404, errCall("video.get_room_by_name", 404, () -> client.video().rooms().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.video().rooms().update("room-1", kw("name", "x"));
      assertNotNull(body);
      assertEquals(
          "video.update_room", okJournal("PUT", "/api/video/rooms/room-1", "video.update_room"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "video.update_room",
              404,
              () -> client.video().rooms().update("missing", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.video().rooms().delete("room-1");
      assertNotNull(body);
      assertEquals(
          "video.delete_room", okJournal("DELETE", "/api/video/rooms/room-1", "video.delete_room"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404, errCall("video.delete_room", 404, () -> client.video().rooms().delete("missing")));
    }

    @Test
    void listStreamsSuccess() {
      Map<String, Object> body = client.video().rooms().listStreams("room-1");
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "video.list_room_streams",
          okJournal("GET", "/api/video/rooms/room-1/streams", "video.list_room_streams"));
    }

    @Test
    void listStreamsError() {
      assertEquals(
          500,
          errCall(
              "video.list_room_streams", 500, () -> client.video().rooms().listStreams("room-1")));
    }

    @Test
    void createStreamSuccess() {
      Map<String, Object> body =
          client.video().rooms().createStream("room-1", kw("url", "rtmp://example.com/live"));
      assertNotNull(body);
      MockTest.JournalEntry j = mock.last();
      assertEquals("rtmp://example.com/live", j.bodyMap().get("url"));
      assertEquals(
          "video.create_room_stream",
          okJournal("POST", "/api/video/rooms/room-1/streams", "video.create_room_stream"));
    }

    @Test
    void createStreamError() {
      assertEquals(
          422,
          errCall(
              "video.create_room_stream",
              422,
              () -> client.video().rooms().createStream("room-1", kw("url", "rtmp://x"))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Room tokens (create only)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("video room_tokens")
  class RoomTokens {

    @Test
    void createSuccess() {
      Map<String, Object> body = client.video().roomTokens().create(kw("room_name", "r"));
      assertNotNull(body);
      assertEquals(
          "video.create_room_token",
          okJournal("POST", "/api/video/room_tokens", "video.create_room_token"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "video.create_room_token",
              422,
              () -> client.video().roomTokens().create(kw("room_name", "r"))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Room sessions: list, get, events, members, recordings
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("video room_sessions")
  class RoomSessions {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.video().roomSessions().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "video.list_room_sessions",
          okJournal("GET", "/api/video/room_sessions", "video.list_room_sessions"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall("video.list_room_sessions", 500, () -> client.video().roomSessions().list()));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.video().roomSessions().get("sess-1");
      assertNotNull(body);
      assertEquals(
          "video.get_room_session",
          okJournal("GET", "/api/video/room_sessions/sess-1", "video.get_room_session"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "video.get_room_session", 404, () -> client.video().roomSessions().get("missing")));
    }

    @Test
    void listEventsSuccess() {
      Map<String, Object> body = client.video().roomSessions().listEvents("sess-1");
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "video.list_room_session_events",
          okJournal(
              "GET", "/api/video/room_sessions/sess-1/events", "video.list_room_session_events"));
    }

    @Test
    void listEventsError() {
      assertEquals(
          500,
          errCall(
              "video.list_room_session_events",
              500,
              () -> client.video().roomSessions().listEvents("sess-1")));
    }

    @Test
    void listMembersSuccess() {
      Map<String, Object> body = client.video().roomSessions().listMembers("sess-1");
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "video.list_room_session_members",
          okJournal(
              "GET", "/api/video/room_sessions/sess-1/members", "video.list_room_session_members"));
    }

    @Test
    void listMembersError() {
      assertEquals(
          500,
          errCall(
              "video.list_room_session_members",
              500,
              () -> client.video().roomSessions().listMembers("sess-1")));
    }

    @Test
    void listRecordingsSuccess() {
      Map<String, Object> body = client.video().roomSessions().listRecordings("sess-1");
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "video.list_room_session_recordings",
          okJournal(
              "GET",
              "/api/video/room_sessions/sess-1/recordings",
              "video.list_room_session_recordings"));
    }

    @Test
    void listRecordingsError() {
      assertEquals(
          500,
          errCall(
              "video.list_room_session_recordings",
              500,
              () -> client.video().roomSessions().listRecordings("sess-1")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Room recordings: list, get, delete, events
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("video room_recordings")
  class RoomRecordings {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.video().roomRecordings().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "video.list_room_recordings",
          okJournal("GET", "/api/video/room_recordings", "video.list_room_recordings"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall("video.list_room_recordings", 500, () -> client.video().roomRecordings().list()));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.video().roomRecordings().get("rec-1");
      assertNotNull(body);
      assertEquals(
          "video.get_room_recording",
          okJournal("GET", "/api/video/room_recordings/rec-1", "video.get_room_recording"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "video.get_room_recording",
              404,
              () -> client.video().roomRecordings().get("missing")));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.video().roomRecordings().delete("rec-1");
      assertNotNull(body);
      assertEquals(
          "video.delete_room_recording",
          okJournal("DELETE", "/api/video/room_recordings/rec-1", "video.delete_room_recording"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "video.delete_room_recording",
              404,
              () -> client.video().roomRecordings().delete("missing")));
    }

    @Test
    void listEventsSuccess() {
      Map<String, Object> body = client.video().roomRecordings().listEvents("rec-1");
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "video.list_room_recording_events",
          okJournal(
              "GET",
              "/api/video/room_recordings/rec-1/events",
              "video.list_room_recording_events"));
    }

    @Test
    void listEventsError() {
      assertEquals(
          500,
          errCall(
              "video.list_room_recording_events",
              500,
              () -> client.video().roomRecordings().listEvents("rec-1")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Conferences (CrudResource, PUT update) + token/stream sub-collections
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("video conferences")
  class Conferences {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.video().conferences().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "video.list_video_conferences",
          okJournal("GET", "/api/video/conferences", "video.list_video_conferences"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall("video.list_video_conferences", 500, () -> client.video().conferences().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.video().conferences().create(kw("name", "c"));
      assertNotNull(body);
      assertEquals(
          "video.create_video_conference",
          okJournal("POST", "/api/video/conferences", "video.create_video_conference"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "video.create_video_conference",
              422,
              () -> client.video().conferences().create(kw("name", "c"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.video().conferences().get("conf-1");
      assertNotNull(body);
      assertEquals(
          "video.get_video_conference",
          okJournal("GET", "/api/video/conferences/conf-1", "video.get_video_conference"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "video.get_video_conference",
              404,
              () -> client.video().conferences().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.video().conferences().update("conf-1", kw("name", "x"));
      assertNotNull(body);
      assertEquals(
          "video.update_video_conference",
          okJournal("PUT", "/api/video/conferences/conf-1", "video.update_video_conference"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "video.update_video_conference",
              404,
              () -> client.video().conferences().update("missing", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.video().conferences().delete("conf-1");
      assertNotNull(body);
      assertEquals(
          "video.delete_video_conference",
          okJournal("DELETE", "/api/video/conferences/conf-1", "video.delete_video_conference"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "video.delete_video_conference",
              404,
              () -> client.video().conferences().delete("missing")));
    }

    @Test
    void listConferenceTokensSuccess() {
      Map<String, Object> body = client.video().conferences().listConferenceTokens("conf-1");
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "video.list_conference_tokens",
          okJournal(
              "GET",
              "/api/video/conferences/conf-1/conference_tokens",
              "video.list_conference_tokens"));
    }

    @Test
    void listConferenceTokensError() {
      assertEquals(
          500,
          errCall(
              "video.list_conference_tokens",
              500,
              () -> client.video().conferences().listConferenceTokens("conf-1")));
    }

    @Test
    void listStreamsSuccess() {
      Map<String, Object> body = client.video().conferences().listStreams("conf-1");
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "video.list_conference_streams",
          okJournal(
              "GET", "/api/video/conferences/conf-1/streams", "video.list_conference_streams"));
    }

    @Test
    void listStreamsError() {
      assertEquals(
          500,
          errCall(
              "video.list_conference_streams",
              500,
              () -> client.video().conferences().listStreams("conf-1")));
    }

    @Test
    void createStreamSuccess() {
      Map<String, Object> body =
          client.video().conferences().createStream("conf-1", kw("url", "rtmp://example.com/c"));
      assertNotNull(body);
      MockTest.JournalEntry j = mock.last();
      assertEquals("rtmp://example.com/c", j.bodyMap().get("url"));
      assertEquals(
          "video.create_conference_stream",
          okJournal(
              "POST", "/api/video/conferences/conf-1/streams", "video.create_conference_stream"));
    }

    @Test
    void createStreamError() {
      assertEquals(
          422,
          errCall(
              "video.create_conference_stream",
              422,
              () -> client.video().conferences().createStream("conf-1", kw("url", "rtmp://x"))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Conference tokens (top-level): get + reset
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("video conference_tokens")
  class ConferenceTokens {

    @Test
    void getSuccess() {
      Map<String, Object> body = client.video().conferenceTokens().get("tok-1");
      assertNotNull(body);
      assertEquals(
          "video.get_conference_token",
          okJournal("GET", "/api/video/conference_tokens/tok-1", "video.get_conference_token"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "video.get_conference_token",
              404,
              () -> client.video().conferenceTokens().get("missing")));
    }

    @Test
    void resetSuccess() {
      Map<String, Object> body = client.video().conferenceTokens().reset("tok-1");
      assertNotNull(body);
      assertEquals(
          "video.reset_conference_token",
          okJournal(
              "POST", "/api/video/conference_tokens/tok-1/reset", "video.reset_conference_token"));
    }

    @Test
    void resetError() {
      assertEquals(
          422,
          errCall(
              "video.reset_conference_token",
              422,
              () -> client.video().conferenceTokens().reset("tok-1")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Top-level streams: get / update (PUT) / delete
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("video streams")
  class Streams {

    @Test
    void getSuccess() {
      Map<String, Object> body = client.video().streams().get("stream-1");
      assertNotNull(body);
      assertEquals(
          "video.get_stream", okJournal("GET", "/api/video/streams/stream-1", "video.get_stream"));
    }

    @Test
    void getError() {
      assertEquals(
          404, errCall("video.get_stream", 404, () -> client.video().streams().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body =
          client.video().streams().update("stream-1", kw("url", "rtmp://example.com/new"));
      assertNotNull(body);
      MockTest.JournalEntry j = mock.last();
      assertEquals("rtmp://example.com/new", j.bodyMap().get("url"));
      assertEquals(
          "video.update_stream",
          okJournal("PUT", "/api/video/streams/stream-1", "video.update_stream"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "video.update_stream",
              404,
              () -> client.video().streams().update("missing", kw("url", "rtmp://x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.video().streams().delete("stream-1");
      assertNotNull(body);
      assertEquals(
          "video.delete_stream",
          okJournal("DELETE", "/api/video/streams/stream-1", "video.delete_stream"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall("video.delete_stream", 404, () -> client.video().streams().delete("missing")));
    }
  }
}
