package com.signalwire.sdk.rest;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.rest.namespaces.generated.Calling;
import com.signalwire.sdk.rest.namespaces.generated.Queues;
import com.signalwire.sdk.rest.namespaces.generated.Recordings;
import org.junit.jupiter.api.Test;

/** Tests for REST calling-related namespaces. */
class CallingTest {

  private final HttpClient httpClient = new HttpClient("test.signalwire.com", "proj", "tok");

  @Test
  void testCallingNamespaceDispatch() {
    var ns = new Calling(httpClient);
    // Calling is command-dispatch (POST /api/calling/calls with a "command"
    // field), not a CRUD resource — the namespace exposes the verb methods only.
    assertNotNull(ns);
  }

  @Test
  void testQueueNamespacePath() {
    var ns = new Queues(httpClient);
    // Python parity: client.queues hits /api/relay/rest/queues.
    assertEquals("/relay/rest/queues", ns.getBasePath());
  }

  @Test
  void testRecordingNamespacePath() {
    var ns = new Recordings(httpClient);
    // Python parity: client.recordings hits /api/relay/rest/recordings.
    assertEquals("/relay/rest/recordings", ns.getBasePath());
  }

  @Test
  void testCallingFromClient() {
    var client =
        RestClient.builder().project("proj").token("tok").space("test.signalwire.com").build();
    assertNotNull(client.calling());
    assertNotNull(client.queues());
    assertNotNull(client.recordings());
  }
}
