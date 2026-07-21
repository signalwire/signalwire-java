/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Mock-backed unit tests translated from signalwire-python/tests/unit/rest/test_video_mock.py.
 *
 * <p>Covers room streams, room sessions (events / recordings), room recordings, conferences (token
 * / stream sub-collections), conference tokens (get / reset), and top-level streams (get / update /
 * delete).
 */
class VideoMockTest {

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

  // ── Rooms — streams sub-resource ─────────────────────────────────

  @Nested
  @DisplayName("VideoRooms streams")
  class RoomsStreams {

    @Test
    @DisplayName("list_streams returns data collection")
    void listStreams() {
      var body = client.video().rooms().listStreams("room-1", java.util.Map.of());
      assertNotNull(body);
      assertNotNull(body.data, "expected 'data' to be set");
      assertTrue(body.data instanceof List);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/video/rooms/room-1/streams", j.path);
      assertNotNull(j.getMatchedRoute(), "spec gap: rooms streams list");
    }

    @Test
    @DisplayName("create_stream posts kwargs in body")
    void createStream() {
      var body =
          client
              .video()
              .rooms()
              .createStream(
                  "room-1",
                  com.signalwire.sdk.rest.namespaces.generated.VideoRooms.CreateStreamRequest
                      .builder()
                      .extras(kw("url", "rtmp://example.com/live"))
                      .build());
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("POST", j.method);
      assertEquals("/api/video/rooms/room-1/streams", j.path);
      Map<String, Object> jb = j.bodyMap();
      assertNotNull(jb);
      assertEquals("rtmp://example.com/live", jb.get("url"));
    }
  }

  // ── Room Sessions ────────────────────────────────────────────────

  @Nested
  @DisplayName("VideoRoomSessions")
  class RoomSessions {

    @Test
    void listReturnsDataCollection() {
      Map<String, Object> body = client.video().roomSessions().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertTrue(body.get("data") instanceof List);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/video/room_sessions", j.path);
    }

    @Test
    void getReturnsSessionObject() {
      Map<String, Object> body = client.video().roomSessions().get("sess-abc");
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/video/room_sessions/sess-abc", j.path);
      assertNotNull(j.getMatchedRoute());
    }

    @Test
    void listEventsUsesEventsSubpath() {
      var body = client.video().roomSessions().listEvents("sess-1", java.util.Map.of());
      assertNotNull(body);
      assertNotNull(body.data);
      assertTrue(body.data instanceof List);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/video/room_sessions/sess-1/events", j.path);
    }

    @Test
    void listRecordingsUsesRecordingsSubpath() {
      var body = client.video().roomSessions().listRecordings("sess-2", java.util.Map.of());
      assertNotNull(body);
      assertNotNull(body.data);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/video/room_sessions/sess-2/recordings", j.path);
    }
  }

  // ── Room Recordings ───────────────────────────────────────────────

  @Nested
  @DisplayName("VideoRoomRecordings")
  class RoomRecordings {

    @Test
    void listReturnsDataCollection() {
      var body = client.video().roomRecordings().list(java.util.Map.of());
      assertNotNull(body);
      assertNotNull(body.data);
      assertTrue(body.data instanceof List);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/video/room_recordings", j.path);
    }

    @Test
    void getReturnsSingleRecording() {
      var body = client.video().roomRecordings().get("rec-xyz", java.util.Map.of());
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/video/room_recordings/rec-xyz", j.path);
    }

    @Test
    void deleteReturnsEmptyDictFor204() {
      Map<String, Object> body = client.video().roomRecordings().delete("rec-del");
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("DELETE", j.method);
      assertEquals("/api/video/room_recordings/rec-del", j.path);
      assertNotNull(j.getMatchedRoute());
    }

    @Test
    void listEventsUsesEventsSubpath() {
      var body = client.video().roomRecordings().listEvents("rec-1", java.util.Map.of());
      assertNotNull(body);
      assertNotNull(body.data);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/video/room_recordings/rec-1/events", j.path);
    }
  }

  // ── Conferences sub-collections ───────────────────────────────────

  @Nested
  @DisplayName("VideoConferences sub-collections")
  class ConferencesSubCollections {

    @Test
    void listConferenceTokens() {
      var body = client.video().conferences().listConferenceTokens("conf-1", java.util.Map.of());
      assertNotNull(body);
      assertNotNull(body.data);
      assertTrue(body.data instanceof List);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/video/conferences/conf-1/conference_tokens", j.path);
    }

    @Test
    void listStreams() {
      var body = client.video().conferences().listStreams("conf-2", java.util.Map.of());
      assertNotNull(body);
      assertNotNull(body.data);
      assertTrue(body.data instanceof List);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/video/conferences/conf-2/streams", j.path);
    }
  }

  // ── Conference Tokens (top-level) ────────────────────────────────

  @Nested
  @DisplayName("VideoConferenceTokens")
  class ConferenceTokens {

    @Test
    void getReturnsSingleToken() {
      var body = client.video().conferenceTokens().get("tok-1", java.util.Map.of());
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/video/conference_tokens/tok-1", j.path);
      assertNotNull(j.getMatchedRoute());
    }

    @Test
    void resetPostsToResetSubpath() {
      var body = client.video().conferenceTokens().reset("tok-2");
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("POST", j.method);
      assertEquals("/api/video/conference_tokens/tok-2/reset", j.path);
      // reset is a no-body POST — should be empty (Java sends "{}").
      Object jb = j.body;
      assertTrue(
          jb == null
              || (jb instanceof Map && ((Map<?, ?>) jb).isEmpty())
              || (jb instanceof String && ((String) jb).isEmpty()),
          "expected empty body, got " + jb);
    }
  }

  // ── Top-level Streams ────────────────────────────────────────────

  @Nested
  @DisplayName("VideoStreams")
  class Streams {

    @Test
    void getReturnsStreamResource() {
      var body = client.video().streams().get("stream-1", java.util.Map.of());
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/video/streams/stream-1", j.path);
    }

    @Test
    void updateUsesPutWithKwargs() {
      var body =
          client
              .video()
              .streams()
              .update(
                  "stream-2",
                  com.signalwire.sdk.rest.namespaces.generated.VideoStreams.UpdateRequest.builder()
                      .extras(kw("url", "rtmp://example.com/new"))
                      .build());
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("PUT", j.method);
      assertEquals("/api/video/streams/stream-2", j.path);
      Map<String, Object> jb = j.bodyMap();
      assertNotNull(jb);
      assertEquals("rtmp://example.com/new", jb.get("url"));
    }

    @Test
    void deleteStream() {
      Map<String, Object> body = client.video().streams().delete("stream-3");
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("DELETE", j.method);
      assertEquals("/api/video/streams/stream-3", j.path);
      assertNotNull(j.getMatchedRoute());
    }
  }
}
