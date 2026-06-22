package com.signalwire.sdk.rest;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.rest.namespaces.CallingNamespace;
import com.signalwire.sdk.rest.namespaces.QueueNamespace;
import com.signalwire.sdk.rest.namespaces.RecordingNamespace;
import org.junit.jupiter.api.Test;

/** Tests for REST calling-related namespaces. */
class CallingTest {

  private final HttpClient httpClient = new HttpClient("test.signalwire.com", "proj", "tok");

  @Test
  void testCallingNamespaceDispatch() {
    var ns = new CallingNamespace(httpClient);
    // Calling is command-dispatch (POST /api/calling/calls with a "command"
    // field), not a CRUD resource — the namespace exposes the verb methods only.
    assertNotNull(ns);
  }

  @Test
  void testQueueNamespacePath() {
    var ns = new QueueNamespace(httpClient);
    // Python parity: client.queues hits /api/relay/rest/queues. The
    // legacy queues() accessor still returns a CrudResource scoped to
    // that path for backwards compat.
    assertEquals("/relay/rest/queues", ns.queues().getBasePath());
    assertEquals("/relay/rest/queues", ns.getBasePath());
  }

  @Test
  void testRecordingNamespacePath() {
    var ns = new RecordingNamespace(httpClient);
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
