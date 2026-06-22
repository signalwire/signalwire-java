package com.signalwire.sdk.rest;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.rest.namespaces.*;
import org.junit.jupiter.api.Test;

/** Tests for all REST namespaces. */
class NamespacesTest {

  private final HttpClient httpClient = new HttpClient("test.signalwire.com", "proj", "tok");

  @Test
  void testNamespacesAccessible() {
    var client =
        RestClient.builder().project("proj").token("tok").space("test.signalwire.com").build();
    assertNotNull(client.fabric());
    assertNotNull(client.calling());
    assertNotNull(client.phoneNumbers());
    assertNotNull(client.datasphere());
    assertNotNull(client.video());
    assertNotNull(client.compat());
    assertNotNull(client.chat());
    assertNotNull(client.pubSub());
    assertNotNull(client.project());
    assertNotNull(client.numberLookup());
    assertNotNull(client.queues());
    assertNotNull(client.recordings());
    assertNotNull(client.registry());
    assertNotNull(client.sipProfile());
  }

  @Test
  void testPhoneNumbersPath() {
    var ns = new PhoneNumbersNamespace(httpClient);
    assertEquals("/relay/rest/phone_numbers", ns.getResource().getBasePath());
  }

  @Test
  void testDatasphereDocumentsPath() {
    var ns = new DatasphereNamespace(httpClient);
    assertEquals("/datasphere/documents", ns.documents().getBasePath());
  }

  @Test
  void testVideoNamespacePaths() {
    var ns = new VideoNamespace(httpClient);
    assertEquals("/video/rooms", ns.rooms().getBasePath());
    assertEquals("/video/room_tokens", ns.roomTokens().getBasePath());
    assertEquals("/video/room_sessions", ns.roomSessions().getBasePath());
    // Python parity: VideoRoomRecordings lives at /video/room_recordings;
    // recordings() is a legacy alias retained for backwards compat.
    assertEquals("/video/room_recordings", ns.recordings().getBasePath());
    assertEquals("/video/room_recordings", ns.roomRecordings().getBasePath());
    assertEquals("/video/conferences", ns.conferences().getBasePath());
    assertEquals("/video/streams", ns.streams().getBasePath());
  }

  @Test
  void testChatTokenOnly() {
    // Chat REST = token minting only (matches Python's flat ChatResource).
    var ns = new ChatNamespace(httpClient);
    assertNotNull(ns);
  }

  @Test
  void testPubSubTokenOnly() {
    // Pub/Sub REST = token minting only (matches Python's flat PubSubResource).
    var ns = new PubSubNamespace(httpClient);
    assertNotNull(ns);
  }

  @Test
  void testNumberLookupNamespace() {
    var ns = new NumberLookupNamespace(httpClient);
    assertNotNull(ns);
  }

  @Test
  void testCompatNamespacePaths() {
    var ns = new CompatNamespace(httpClient, "AC123");
    assertTrue(ns.calls().getBasePath().contains("AC123"));
    assertTrue(ns.messages().getBasePath().contains("AC123"));
    assertNotNull(ns.recordings());
    assertNotNull(ns.queues());
    assertNotNull(ns.conferences());
    assertNotNull(ns.transcriptions());
    assertNotNull(ns.applications());
  }

  @Test
  void testCrudResourceStoresPath() {
    var crud = new CrudResource(httpClient, "/test/path");
    assertEquals("/test/path", crud.getBasePath());
    assertSame(httpClient, crud.getHttpClient());
  }

  @Test
  void testHttpClientBaseUrl() {
    var client = new HttpClient("test.signalwire.com", "proj", "tok");
    assertEquals("https://test.signalwire.com/api", client.getBaseUrl());
  }
}
