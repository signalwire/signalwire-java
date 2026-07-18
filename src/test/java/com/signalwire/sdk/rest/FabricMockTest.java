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
 * Mock-backed unit tests translated from signalwire-python/tests/unit/rest/test_fabric_mock.py.
 *
 * <p>Closes the gap left by {@code FabricTest} (which only checks paths): fabric addresses, generic
 * resources operations, subscriber sip-endpoint sub-resources, call-flow / conference-room
 * singular-path behaviour, the full FabricTokens surface, and {@code cxml_applications.create}
 * raising a deliberate exception.
 */
class FabricMockTest {

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

  // ── Fabric Addresses ─────────────────────────────────────────────

  @Nested
  @DisplayName("FabricAddresses")
  class Addresses {

    @Test
    void listReturnsDataCollection() {
      Map<String, Object> body = client.fabric().addresses().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertTrue(body.get("data") instanceof List);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/fabric/addresses", j.path);
      assertEquals(
          "fabric.list_fabric_addresses",
          j.getMatchedRoute(),
          "unexpected matched_route: " + j.getMatchedRoute());
    }

    @Test
    void getUsesAddressId() {
      Map<String, Object> body = client.fabric().addresses().get("addr-9001");
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/fabric/addresses/addr-9001", j.path);
      assertNotNull(j.getMatchedRoute(), "spec gap: address get");
    }
  }

  // ── CxmlApplicationsResource: no create surface ──────────────────

  @Nested
  @DisplayName("CxmlApplications exposes no create (cXML apps cannot be created via fabric)")
  class CxmlApplicationsCreate {

    // The generated CxmlApplications resource (list/get/update/delete/listAddresses) has NO
    // create method at all — cXML applications cannot be created through the fabric API. The
    // old hand namespace expressed this by throwing UnsupportedOperationException from a stub
    // create(); the generated resource simply omits the method, so the "create is unsupported"
    // contract is now enforced at compile time (there is no create() to call). This test
    // documents that the accessor is wired and usable for its real operations, and that no
    // request was issued merely by obtaining the resource.
    @Test
    void resourceHasNoCreateAndIssuesNoTraffic() {
      var cxml = client.fabric().cxmlApplications();
      assertNotNull(cxml);
      assertEquals("/fabric/resources/cxml_applications", cxml.getBasePath());
      assertTrue(mock.journal().isEmpty(), "expected no journal entries, got " + mock.journal());
    }
  }

  // ── CallFlowsResource.list_addresses (singular path) ─────────────

  @Nested
  @DisplayName("CallFlowsResource sub-paths use singular call_flow")
  class CallFlowsAddresses {

    @Test
    void listAddressesUsesSingularPath() {
      Map<String, Object> body =
          client.fabric().callFlows().listAddresses("cf-1", java.util.Map.of());
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertTrue(body.get("data") instanceof List);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/fabric/resources/call_flow/cf-1/addresses", j.path);
      assertNotNull(j.getMatchedRoute(), "spec gap: call-flow addresses sub-path");
    }
  }

  // ── ConferenceRoomsResource.list_addresses (singular path) ───────

  @Nested
  @DisplayName("ConferenceRoomsResource sub-paths use singular conference_room")
  class ConferenceRoomsAddresses {

    @Test
    void listAddressesUsesSingularPath() {
      Map<String, Object> body =
          client.fabric().conferenceRooms().listAddresses("cr-1", java.util.Map.of());
      assertNotNull(body);
      assertTrue(body.containsKey("data"));

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/fabric/resources/conference_room/cr-1/addresses", j.path);
      assertNotNull(j.getMatchedRoute());
    }
  }

  // ── Subscribers SIP endpoint per-id ops ─────────────────────────

  @Nested
  @DisplayName("Subscribers SIP endpoint ops")
  class SubscribersSipEndpointOps {

    @Test
    void getSipEndpoint() {
      Map<String, Object> body =
          client.fabric().subscribers().getSipEndpoint("sub-1", "ep-1", java.util.Map.of());
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/fabric/resources/subscribers/sub-1/sip_endpoints/ep-1", j.path);
      assertNotNull(j.getMatchedRoute());
    }

    @Test
    void updateSipEndpointUsesPatch() {
      Map<String, Object> body =
          client
              .fabric()
              .subscribers()
              .updateSipEndpoint(
                  "sub-1",
                  "ep-1",
                  com.signalwire.sdk.rest.namespaces.generated.Subscribers.UpdateSipEndpointRequest
                      .builder()
                      .extras(kw("username", "renamed"))
                      .build());
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("PATCH", j.method);
      assertEquals("/api/fabric/resources/subscribers/sub-1/sip_endpoints/ep-1", j.path);
      Map<String, Object> jb = j.bodyMap();
      assertNotNull(jb);
      assertEquals("renamed", jb.get("username"));
    }

    @Test
    void deleteSipEndpoint() {
      Map<String, Object> body = client.fabric().subscribers().deleteSipEndpoint("sub-1", "ep-1");
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("DELETE", j.method);
      assertEquals("/api/fabric/resources/subscribers/sub-1/sip_endpoints/ep-1", j.path);
      assertNotNull(j.getMatchedRoute());
    }
  }

  // ── FabricTokens ─────────────────────────────────────────────────

  @Nested
  @DisplayName("FabricTokens")
  class Tokens {

    @Test
    void createInviteToken() {
      Map<String, Object> body =
          client
              .fabric()
              .tokens()
              .createInviteToken(
                  com.signalwire.sdk.rest.namespaces.generated.FabricTokens.CreateInviteTokenRequest
                      .builder()
                      .addressId("3fa85f64-5717-4562-b3fc-2c963f66afa6")
                      .build());
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("POST", j.method);
      // Singular 'subscriber' path segment per spec.
      assertEquals("/api/fabric/subscriber/invites", j.path);
      Map<String, Object> jb = j.bodyMap();
      assertNotNull(jb);
      assertEquals("3fa85f64-5717-4562-b3fc-2c963f66afa6", jb.get("address_id"));
    }

    @Test
    void createEmbedToken() {
      Map<String, Object> body =
          client
              .fabric()
              .tokens()
              .createEmbedToken(
                  com.signalwire.sdk.rest.namespaces.generated.FabricTokens.CreateEmbedTokenRequest
                      .builder()
                      .token("c2c_7acc0e5e968706a032983cd80cdca219")
                      .build());
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("POST", j.method);
      assertEquals("/api/fabric/embeds/tokens", j.path);
      Map<String, Object> jb = j.bodyMap();
      assertNotNull(jb);
      assertEquals("c2c_7acc0e5e968706a032983cd80cdca219", jb.get("token"));
    }

    @Test
    void refreshSubscriberToken() {
      Map<String, Object> body =
          client
              .fabric()
              .tokens()
              .refreshSubscriberToken(
                  com.signalwire.sdk.rest.namespaces.generated.FabricTokens
                      .RefreshSubscriberTokenRequest.builder()
                      .extras(kw("refresh_token", "abc-123"))
                      .build());
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("POST", j.method);
      assertEquals("/api/fabric/subscribers/tokens/refresh", j.path);
      Map<String, Object> jb = j.bodyMap();
      assertNotNull(jb);
      assertEquals("abc-123", jb.get("refresh_token"));
    }
  }

  // ── GenericResources ─────────────────────────────────────────────

  @Nested
  @DisplayName("GenericResources")
  class GenericResourcesTests {

    @Test
    void listReturnsDataCollection() {
      Map<String, Object> body = client.fabric().resources().list(java.util.Map.of());
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertTrue(body.get("data") instanceof List);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/fabric/resources", j.path);
      assertNotNull(j.getMatchedRoute());
    }

    @Test
    void getReturnsSingleResource() {
      Map<String, Object> body = client.fabric().resources().get("res-1", java.util.Map.of());
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/fabric/resources/res-1", j.path);
    }

    @Test
    void deleteResource() {
      Map<String, Object> body = client.fabric().resources().delete("res-2");
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("DELETE", j.method);
      assertEquals("/api/fabric/resources/res-2", j.path);
      assertNotNull(j.getMatchedRoute());
    }

    @Test
    void listAddresses() {
      Map<String, Object> body =
          client.fabric().resources().listAddresses("res-3", java.util.Map.of());
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertTrue(body.get("data") instanceof List);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/fabric/resources/res-3/addresses", j.path);
    }

    @Test
    void assignDomainApplication() {
      Map<String, Object> body =
          client
              .fabric()
              .resources()
              .assignDomainApplication(
                  "res-4",
                  com.signalwire.sdk.rest.namespaces.generated.GenericResources
                      .AssignDomainApplicationRequest.builder()
                      .extras(kw("domain_application_id", "da-7"))
                      .build());
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("POST", j.method);
      assertEquals("/api/fabric/resources/res-4/domain_applications", j.path);
      Map<String, Object> jb = j.bodyMap();
      assertNotNull(jb);
      assertEquals("da-7", jb.get("domain_application_id"));
    }
  }
}
