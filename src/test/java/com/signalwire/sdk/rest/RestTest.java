/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.rest.namespaces.generated.Calling;
import com.signalwire.sdk.rest.namespaces.generated.Chat;
import com.signalwire.sdk.rest.namespaces.generated.DatasphereNamespace;
import com.signalwire.sdk.rest.namespaces.generated.FabricNamespace;
import com.signalwire.sdk.rest.namespaces.generated.PhoneNumbers;
import com.signalwire.sdk.rest.namespaces.generated.PubSub;
import com.signalwire.sdk.rest.namespaces.generated.Queues;
import com.signalwire.sdk.rest.namespaces.generated.Recordings;
import com.signalwire.sdk.rest.namespaces.generated.VideoNamespace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the REST client components. No live HTTP connections -- tests verify client
 * creation, namespace construction, CRUD paths, and error formatting.
 */
class RestTest {

  // ── RestClient ─────────────────────────────────────────────

  @Nested
  @DisplayName("RestClient")
  class ClientTests {

    @Test
    @DisplayName("Builder creates client with required params")
    void builderCreates() {
      var client =
          RestClient.builder()
              .project("proj-1")
              .token("tok-1")
              .space("example.signalwire.com")
              .build();

      assertEquals("proj-1", client.getProject());
      assertEquals("example.signalwire.com", client.getSpace());
      assertNotNull(client.getHttpClient());
    }

    // These assert that a missing credential with NO env-var fallback set is an
    // error. build() reads SIGNALWIRE_PROJECT_ID/_API_TOKEN/SPACE as a fallback
    // (Python parity), so it now throws IllegalArgumentException (mirroring
    // Python's ValueError) only when a credential is absent from BOTH the builder
    // and the environment. Unit tests run without SIGNALWIRE_* creds in the env.
    @Test
    @DisplayName("Builder fails without project")
    void builderNoProject() {
      assertThrows(
          IllegalArgumentException.class,
          () -> RestClient.builder().token("tok").space("space").build());
    }

    @Test
    @DisplayName("Builder fails without token")
    void builderNoToken() {
      assertThrows(
          IllegalArgumentException.class,
          () -> RestClient.builder().project("proj").space("space").build());
    }

    @Test
    @DisplayName("Builder fails without space")
    void builderNoSpace() {
      assertThrows(
          IllegalArgumentException.class,
          () -> RestClient.builder().project("proj").token("tok").build());
    }
  }

  // ── Namespaces ───────────────────────────────────────────────────

  @Nested
  @DisplayName("Namespaces")
  class NamespaceTests {

    private final HttpClient httpClient = new HttpClient("test.signalwire.com", "proj", "tok");

    @Test
    @DisplayName("Namespaces are accessible")
    void allNamespaces() {
      var client =
          RestClient.builder().project("proj").token("tok").space("test.signalwire.com").build();

      assertNotNull(client.fabric());
      assertNotNull(client.calling());
      assertNotNull(client.phoneNumbers());
      assertNotNull(client.datasphere());
      assertNotNull(client.video());
      assertNotNull(client.chat());
      assertNotNull(client.pubsub());
      assertNotNull(client.project());
      assertNotNull(client.lookup());
      assertNotNull(client.queues());
      assertNotNull(client.recordings());
      assertNotNull(client.registry());
      assertNotNull(client.sipProfile());
    }

    @Test
    @DisplayName("Namespaces are lazily initialized and reused")
    void namespacesReused() {
      var client =
          RestClient.builder().project("proj").token("tok").space("test.signalwire.com").build();

      assertSame(client.phoneNumbers(), client.phoneNumbers());
      assertSame(client.fabric(), client.fabric());
      assertSame(client.datasphere(), client.datasphere());
    }

    @Test
    @DisplayName("FabricNamespace has subscribers, addresses, resources")
    void fabricNamespace() {
      var ns = new FabricNamespace(httpClient);
      assertNotNull(ns.subscribers());
      assertNotNull(ns.addresses());
      assertNotNull(ns.resources());
      // Python parity: subscribers live under /fabric/resources/subscribers;
      // addresses is its own top-level collection at /fabric/addresses.
      assertEquals("/fabric/resources/subscribers", ns.subscribers().getBasePath());
      assertEquals("/fabric/addresses", ns.addresses().getBasePath());
      assertEquals("/fabric/resources", ns.resources().getBasePath());
    }

    @Test
    @DisplayName("CallingNamespace is command-dispatch")
    void callingNamespace() {
      // Calling is command-dispatch (POST /api/calling/calls), not CRUD.
      var ns = new Calling(httpClient);
      assertNotNull(ns);
    }

    @Test
    @DisplayName("PhoneNumbersNamespace has correct path")
    void phoneNumbersNamespace() {
      var ns = new PhoneNumbers(httpClient);
      assertEquals("/relay/rest/phone_numbers", ns.getBasePath());
    }

    @Test
    @DisplayName("DatasphereNamespace has documents")
    void datasphereNamespace() {
      var ns = new DatasphereNamespace(httpClient);
      assertNotNull(ns.documents());
      assertEquals("/datasphere/documents", ns.documents().getBasePath());
    }

    @Test
    @DisplayName("VideoNamespace has rooms, sessions, recordings, room tokens")
    void videoNamespace() {
      var ns = new VideoNamespace(httpClient);
      assertNotNull(ns.rooms());
      assertNotNull(ns.roomSessions());
      assertNotNull(ns.roomRecordings());
      assertNotNull(ns.roomTokens());
      assertEquals("/video/rooms", ns.rooms().getBasePath());
      assertEquals("/video/room_sessions", ns.roomSessions().getBasePath());
      // Python parity: VideoRoomTokens is create-only at /video/room_tokens.
      assertEquals("/video/room_tokens", ns.roomTokens().getBasePath());
      // Python parity: VideoRoomRecordings lives at /video/room_recordings.
      assertEquals("/video/room_recordings", ns.roomRecordings().getBasePath());
    }

    @Test
    @DisplayName("ChatNamespace mints tokens only")
    void chatNamespace() {
      // Chat REST = token minting only (matches Python's flat ChatResource).
      var ns = new Chat(httpClient);
      assertNotNull(ns);
    }

    @Test
    @DisplayName("PubSubNamespace mints tokens only")
    void pubSubNamespace() {
      // Pub/Sub REST = token minting only (matches Python's flat PubSubResource).
      var ns = new PubSub(httpClient);
      assertNotNull(ns);
    }

    @Test
    @DisplayName("QueueNamespace has queues")
    void queueNamespace() {
      var ns = new Queues(httpClient);
      // Python parity: /api/relay/rest/queues.
      assertEquals("/relay/rest/queues", ns.getBasePath());
    }

    @Test
    @DisplayName("RecordingNamespace has recordings")
    void recordingNamespace() {
      var ns = new Recordings(httpClient);
      // Python parity: /api/relay/rest/recordings.
      assertEquals("/relay/rest/recordings", ns.getBasePath());
    }
  }

  // ── CrudResource ─────────────────────────────────────────────────

  @Nested
  @DisplayName("CrudResource")
  class CrudTests {

    @Test
    @DisplayName("CrudResource stores base path")
    void basePath() {
      var httpClient = new HttpClient("test.signalwire.com", "proj", "tok");
      var crud = new CrudResource(httpClient, "/phone_numbers");
      assertEquals("/phone_numbers", crud.getBasePath());
      assertSame(httpClient, crud.getHttpClient());
    }
  }

  // ── HttpClient ───────────────────────────────────────────────────

  @Nested
  @DisplayName("HttpClient")
  class HttpClientTests {

    @Test
    @DisplayName("HttpClient builds base URL from space")
    void baseUrl() {
      var client = new HttpClient("test.signalwire.com", "proj", "tok");
      assertEquals("https://test.signalwire.com/api", client.getBaseUrl());
    }

    @Test
    @DisplayName("HttpClient with different space")
    void differentSpace() {
      var client = new HttpClient("custom.example.com", "my-project", "my-token");
      assertEquals("https://custom.example.com/api", client.getBaseUrl());
    }
  }

  // ── RestError ──────────────────────────────────────────

  @Nested
  @DisplayName("RestError")
  class ErrorTests {

    @Test
    @DisplayName("Error message includes status, method, url")
    void errorMessage() {
      var error =
          new RestError(
              404,
              "GET",
              "/phone_numbers/123",
              "https://example.signalwire.com/api/phone_numbers/123",
              "Not found");
      assertTrue(error.getMessage().contains("404"));
      assertTrue(error.getMessage().contains("GET"));
      assertTrue(
          error.getMessage().contains("https://example.signalwire.com/api/phone_numbers/123"));
      assertTrue(error.getMessage().contains("Not found"));
    }

    @Test
    @DisplayName("Error properties are accessible")
    void errorProperties() {
      var error =
          new RestError(
              500, "POST", "/path", "https://example.signalwire.com/api/path?foo=bar", "body");
      assertEquals(500, error.getStatusCode());
      assertEquals("POST", error.getMethod());
      assertEquals("/path", error.getPath());
      assertEquals("https://example.signalwire.com/api/path?foo=bar", error.getUrl());
      assertEquals("body", error.getResponseBody());
    }

    @Test
    @DisplayName("isClientError returns true for 4xx")
    void clientError() {
      assertTrue(new RestError(400, "GET", "/", "u", "").isClientError());
      assertTrue(new RestError(404, "GET", "/", "u", "").isClientError());
      assertTrue(new RestError(422, "GET", "/", "u", "").isClientError());
      assertFalse(new RestError(500, "GET", "/", "u", "").isClientError());
      assertFalse(new RestError(200, "GET", "/", "u", "").isClientError());
    }

    @Test
    @DisplayName("isServerError returns true for 5xx")
    void serverError() {
      assertTrue(new RestError(500, "GET", "/", "u", "").isServerError());
      assertTrue(new RestError(503, "GET", "/", "u", "").isServerError());
      assertFalse(new RestError(404, "GET", "/", "u", "").isServerError());
    }

    @Test
    @DisplayName("isNotFound returns true for 404")
    void notFound() {
      assertTrue(new RestError(404, "GET", "/", "u", "").isNotFound());
      assertFalse(new RestError(400, "GET", "/", "u", "").isNotFound());
    }

    @Test
    @DisplayName("isUnauthorized returns true for 401 and 403")
    void unauthorized() {
      assertTrue(new RestError(401, "GET", "/", "u", "").isUnauthorized());
      assertTrue(new RestError(403, "GET", "/", "u", "").isUnauthorized());
      assertFalse(new RestError(404, "GET", "/", "u", "").isUnauthorized());
    }

    @Test
    @DisplayName("Error with cause preserves exception chain")
    void errorWithCause() {
      var cause = new RuntimeException("original");
      var error = new RestError(0, "GET", "/path", "https://x/api/path", "msg", cause);
      assertSame(cause, error.getCause());
    }

    @Test
    @DisplayName("Error truncates long response body in message")
    void errorTruncatesBody() {
      String longBody = "x".repeat(300);
      var error = new RestError(500, "GET", "/", "u", longBody);
      assertTrue(error.getMessage().contains("..."));
      assertTrue(error.getMessage().length() < longBody.length() + 100);
    }

    @Test
    @DisplayName("Error with null response body")
    void errorNullBody() {
      var error = new RestError(500, "GET", "/", "u", null);
      assertNotNull(error.getMessage());
      assertFalse(error.getMessage().contains("null"));
    }

    @Test
    @DisplayName("Error with empty response body")
    void errorEmptyBody() {
      var error = new RestError(500, "GET", "/", "u", "");
      assertNotNull(error.getMessage());
    }

    @Test
    @DisplayName("§6.6: request_id extracted from x-request-id response header (case-insensitive)")
    void requestIdFromHeader() {
      var headers = new java.util.LinkedHashMap<String, String>();
      headers.put("Content-Type", "application/json");
      headers.put("X-Request-Id", "req-abc-123");
      var error = new RestError(500, "GET", "/", "u", "boom", headers);

      assertEquals(java.util.Optional.of("req-abc-123"), error.getRequestId());
      assertEquals("req-abc-123", error.getHeaders().get("X-Request-Id"));
      assertTrue(
          error.getMessage().contains("(request-id: req-abc-123)"),
          "message should append the request id");
    }

    @Test
    @DisplayName("§6.6: request_id falls back through the header preference order")
    void requestIdFallbackOrder() {
      var headers =
          java.util.Map.of("x-amzn-requestid", "amzn-9", "x-signalwire-request-id", "sw-5");
      // x-signalwire-request-id has higher preference than x-amzn-requestid.
      var error = new RestError(502, "GET", "/", "u", "", headers);
      assertEquals(java.util.Optional.of("sw-5"), error.getRequestId());
    }

    @Test
    @DisplayName("§6.6: no request-id header → empty Optional, empty headers when none supplied")
    void requestIdAbsent() {
      var noReqId = new RestError(500, "GET", "/", "u", "", java.util.Map.of("Content-Type", "x"));
      assertTrue(noReqId.getRequestId().isEmpty());

      var noHeaders = new RestError(500, "GET", "/", "u", "");
      assertTrue(noHeaders.getRequestId().isEmpty());
      assertTrue(noHeaders.getHeaders().isEmpty());
    }
  }

  // ── SignalWireRestTransportError ────────────────────────────────

  @Nested
  @DisplayName("SignalWireRestTransportError")
  class TransportErrorTests {

    @Test
    @DisplayName("is a member of the RestError family with the NO_STATUS sentinel")
    void constructorShape() {
      var cause = new java.io.IOException("Connection refused");
      var error =
          new SignalWireRestTransportError(
              "GET", "/api/fabric/addresses", "http://127.0.0.1:1/api/fabric/addresses", cause);

      assertInstanceOf(RestError.class, error);
      assertEquals(SignalWireRestTransportError.NO_STATUS, error.getStatusCode());
      assertEquals(0, error.getStatusCode());
      assertEquals("GET", error.getMethod());
      assertEquals("/api/fabric/addresses", error.getPath());
      assertEquals("http://127.0.0.1:1/api/fabric/addresses", error.getUrl());
      assertTrue(error.getMessage().contains("failed to reach the server"));
    }

    @Test
    @DisplayName("preserves the underlying transport exception as the cause")
    void causePreserved() {
      var cause = new java.io.IOException("Connection refused");
      var error = new SignalWireRestTransportError("GET", "/path", "https://x/api/path", cause);
      assertSame(cause, error.getCause());
    }

    @Test
    @DisplayName(
        "a connection-refused request throws the typed transport error, not a bare IOException")
    void connectionRefusedRaisesTypedError() throws Exception {
      int deadPort;
      try (var socket = new java.net.ServerSocket(0)) {
        deadPort = socket.getLocalPort();
      }
      // The socket above is now closed -- nothing is listening on deadPort, so a
      // request to it triggers connection refused.
      var client = RestClient.withBaseUrl("http://127.0.0.1:" + deadPort, "proj", "tok");

      var thrown =
          assertThrows(RestError.class, () -> client.getHttpClient().get("/api/fabric/addresses"));

      assertInstanceOf(SignalWireRestTransportError.class, thrown);
      assertEquals(SignalWireRestTransportError.NO_STATUS, thrown.getStatusCode());
      assertNotNull(thrown.getCause());
    }
  }
}
