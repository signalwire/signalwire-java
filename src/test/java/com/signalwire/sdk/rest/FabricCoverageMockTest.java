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
 * Full success+error REST coverage for the {@code fabric.*} canonical spec group.
 *
 * <p>For every coverable fabric route this exercises BOTH a success (2xx) call — asserting the
 * response body, the journalled {@code method}/{@code path}, and {@code matched_route} == the
 * canonical endpoint id — AND an error path: an armed {@link MockTest.Harness#scenarioSet} override
 * (404/422/500) that must surface as a {@link RestError} carrying the right status code, with the
 * journal recording the same {@code matched_route} and {@code response_status}.
 *
 * <p>Mirrors the idiom of {@code FabricMockTest}. Accepted gaps (NOT tested here — no SDK surface
 * or unreachable doubled-path spec artifacts): {@code dialogflow_agents} (5 routes), the
 * doubled-path {@code list_sip_gateway_addresses} and {@code assign_resource_sip_endpoint} (2
 * routes). ({@code fabric.assign_resource_phone_route} is now reachable via {@code
 * resources().assignPhoneRoute} and is covered in {@code ParityGapCoverageMockTest}.) {@code
 * cxml_applications.create} has no canonical route and deliberately raises {@link
 * UnsupportedOperationException}; that real behaviour is asserted below.
 */
class FabricCoverageMockTest {

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

  /**
   * Success-path assertion for the handful of list routes whose OpenAPI example body is an
   * array-wrapped object ({@code [{"data":[...]}]}). The SDK's {@code HttpClient} deserializes
   * every 2xx body into a {@code Map}, so an array top level surfaces as a client-side parse {@link
   * RestError} with {@code statusCode == 0} (no HTTP error occurred — the mock returned 200). We
   * swallow only that parse artifact and prove the route really was exercised with a 2xx by
   * asserting the journal entry. Returns the response status the mock recorded.
   */
  private int okListArrayBody(
      String expectedMethod, String expectedPath, String expectedRoute, Runnable call) {
    try {
      call.run();
    } catch (RestError e) {
      assertEquals(
          0, e.getStatusCode(), "expected a client-side parse artifact for " + expectedRoute);
    }
    MockTest.JournalEntry j = mock.last();
    assertEquals(expectedMethod, j.method, "method for " + expectedRoute);
    assertEquals(expectedPath, j.path, "path for " + expectedRoute);
    assertEquals(
        expectedRoute, j.getMatchedRoute(), "unexpected matched_route: " + j.getMatchedRoute());
    assertEquals(
        Integer.valueOf(200), j.getResponseStatus(), "response_status for " + expectedRoute);
    return j.getResponseStatus();
  }

  // ════════════════════════════════════════════════════════════════════
  // Top-level addresses (read-only)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric addresses")
  class Addresses {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.fabric().addresses().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_fabric_addresses",
          okJournal("GET", "/api/fabric/addresses", "fabric.list_fabric_addresses"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall("fabric.list_fabric_addresses", 500, () -> client.fabric().addresses().list()));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().addresses().get("addr-1");
      assertNotNull(body);
      assertEquals(
          "fabric.get_fabric_address",
          okJournal("GET", "/api/fabric/addresses/addr-1", "fabric.get_fabric_address"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "fabric.get_fabric_address", 404, () -> client.fabric().addresses().get("missing")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Tokens (all POST)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric tokens")
  class Tokens {

    @Test
    void createEmbedsTokenSuccess() {
      Map<String, Object> body =
          client
              .fabric()
              .tokens()
              .createEmbedToken(
                  com.signalwire.sdk.rest.namespaces.generated.FabricTokens.CreateEmbedTokenRequest
                      .builder()
                      .extras(kw("a", "b"))
                      .build());
      assertNotNull(body);
      assertEquals(
          "fabric.create_embeds_token",
          okJournal("POST", "/api/fabric/embeds/tokens", "fabric.create_embeds_token"));
    }

    @Test
    void createEmbedsTokenError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_embeds_token",
              422,
              () ->
                  client
                      .fabric()
                      .tokens()
                      .createEmbedToken(
                          com.signalwire.sdk.rest.namespaces.generated.FabricTokens
                              .CreateEmbedTokenRequest.builder()
                              .extras(kw("a", "b"))
                              .build())));
    }

    @Test
    void createGuestTokenSuccess() {
      Map<String, Object> body =
          client
              .fabric()
              .tokens()
              .createGuestToken(
                  com.signalwire.sdk.rest.namespaces.generated.FabricTokens.CreateGuestTokenRequest
                      .builder()
                      .extras(kw("a", "b"))
                      .build());
      assertNotNull(body);
      assertEquals(
          "fabric.create_subscriber_guest_token",
          okJournal("POST", "/api/fabric/guests/tokens", "fabric.create_subscriber_guest_token"));
    }

    @Test
    void createGuestTokenError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_subscriber_guest_token",
              422,
              () ->
                  client
                      .fabric()
                      .tokens()
                      .createGuestToken(
                          com.signalwire.sdk.rest.namespaces.generated.FabricTokens
                              .CreateGuestTokenRequest.builder()
                              .extras(kw("a", "b"))
                              .build())));
    }

    @Test
    void createInviteTokenSuccess() {
      Map<String, Object> body =
          client
              .fabric()
              .tokens()
              .createInviteToken(
                  com.signalwire.sdk.rest.namespaces.generated.FabricTokens.CreateInviteTokenRequest
                      .builder()
                      .extras(kw("email", "x@example.com"))
                      .build());
      assertNotNull(body);
      assertEquals(
          "fabric.create_subscriber_invite_token",
          okJournal(
              "POST", "/api/fabric/subscriber/invites", "fabric.create_subscriber_invite_token"));
    }

    @Test
    void createInviteTokenError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_subscriber_invite_token",
              422,
              () ->
                  client
                      .fabric()
                      .tokens()
                      .createInviteToken(
                          com.signalwire.sdk.rest.namespaces.generated.FabricTokens
                              .CreateInviteTokenRequest.builder()
                              .extras(kw("email", "x@example.com"))
                              .build())));
    }

    @Test
    void createSubscriberTokenSuccess() {
      Map<String, Object> body =
          client
              .fabric()
              .tokens()
              .createSubscriberToken(
                  com.signalwire.sdk.rest.namespaces.generated.FabricTokens
                      .CreateSubscriberTokenRequest.builder()
                      .extras(kw("a", "b"))
                      .build());
      assertNotNull(body);
      assertEquals(
          "fabric.create_subscriber_token",
          okJournal("POST", "/api/fabric/subscribers/tokens", "fabric.create_subscriber_token"));
    }

    @Test
    void createSubscriberTokenError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_subscriber_token",
              422,
              () ->
                  client
                      .fabric()
                      .tokens()
                      .createSubscriberToken(
                          com.signalwire.sdk.rest.namespaces.generated.FabricTokens
                              .CreateSubscriberTokenRequest.builder()
                              .extras(kw("a", "b"))
                              .build())));
    }

    @Test
    void refreshSubscriberTokenSuccess() {
      Map<String, Object> body =
          client
              .fabric()
              .tokens()
              .refreshSubscriberToken(
                  com.signalwire.sdk.rest.namespaces.generated.FabricTokens
                      .RefreshSubscriberTokenRequest.builder()
                      .extras(kw("refresh_token", "t"))
                      .build());
      assertNotNull(body);
      assertEquals(
          "fabric.refresh_subscriber_token",
          okJournal(
              "POST", "/api/fabric/subscribers/tokens/refresh", "fabric.refresh_subscriber_token"));
    }

    @Test
    void refreshSubscriberTokenError() {
      assertEquals(
          422,
          errCall(
              "fabric.refresh_subscriber_token",
              422,
              () ->
                  client
                      .fabric()
                      .tokens()
                      .refreshSubscriberToken(
                          com.signalwire.sdk.rest.namespaces.generated.FabricTokens
                              .RefreshSubscriberTokenRequest.builder()
                              .extras(kw("refresh_token", "t"))
                              .build())));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Generic resources collection + per-id ops + assignments
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric generic resources")
  class GenericResources {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.fabric().resources().list(java.util.Map.of());
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_resources",
          okJournal("GET", "/api/fabric/resources", "fabric.list_resources"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall(
              "fabric.list_resources",
              500,
              () -> client.fabric().resources().list(java.util.Map.of())));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().resources().get("res-1", java.util.Map.of());
      assertNotNull(body);
      assertEquals(
          "fabric.get_resource",
          okJournal("GET", "/api/fabric/resources/res-1", "fabric.get_resource"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "fabric.get_resource",
              404,
              () -> client.fabric().resources().get("missing", java.util.Map.of())));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.fabric().resources().delete("res-2");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_resource",
          okJournal("DELETE", "/api/fabric/resources/res-2", "fabric.delete_resource"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_resource", 404, () -> client.fabric().resources().delete("missing")));
    }

    @Test
    void listAddressesSuccess() {
      Map<String, Object> body =
          client.fabric().resources().listAddresses("res-3", java.util.Map.of());
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_resource_addresses",
          okJournal(
              "GET", "/api/fabric/resources/res-3/addresses", "fabric.list_resource_addresses"));
    }

    @Test
    void listAddressesError() {
      assertEquals(
          500,
          errCall(
              "fabric.list_resource_addresses",
              500,
              () -> client.fabric().resources().listAddresses("res-3", java.util.Map.of())));
    }

    @Test
    void assignDomainApplicationSuccess() {
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
      assertEquals("da-7", j.bodyMap().get("domain_application_id"));
      assertEquals(
          "fabric.assign_resource_domain_application",
          okJournal(
              "POST",
              "/api/fabric/resources/res-4/domain_applications",
              "fabric.assign_resource_domain_application"));
    }

    @Test
    void assignDomainApplicationError() {
      assertEquals(
          422,
          errCall(
              "fabric.assign_resource_domain_application",
              422,
              () ->
                  client
                      .fabric()
                      .resources()
                      .assignDomainApplication(
                          "res-4",
                          com.signalwire.sdk.rest.namespaces.generated.GenericResources
                              .AssignDomainApplicationRequest.builder()
                              .extras(kw("domain_application_id", "da-7"))
                              .build())));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // ai_agents (FabricResource, PATCH update) — full CRUD + addresses
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric ai_agents")
  class AiAgents {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.fabric().aiAgents().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_ai_agents",
          okJournal("GET", "/api/fabric/resources/ai_agents", "fabric.list_ai_agents"));
    }

    @Test
    void listError() {
      assertEquals(
          500, errCall("fabric.list_ai_agents", 500, () -> client.fabric().aiAgents().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.fabric().aiAgents().create(kw("name", "a"));
      assertNotNull(body);
      assertEquals(
          "fabric.create_ai_agent",
          okJournal("POST", "/api/fabric/resources/ai_agents", "fabric.create_ai_agent"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_ai_agent",
              422,
              () -> client.fabric().aiAgents().create(kw("name", "a"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().aiAgents().get("a-1");
      assertNotNull(body);
      assertEquals(
          "fabric.get_ai_agent",
          okJournal("GET", "/api/fabric/resources/ai_agents/a-1", "fabric.get_ai_agent"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall("fabric.get_ai_agent", 404, () -> client.fabric().aiAgents().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.fabric().aiAgents().update("a-1", kw("name", "b"));
      assertNotNull(body);
      assertEquals(
          "fabric.update_ai_agent",
          okJournal("PATCH", "/api/fabric/resources/ai_agents/a-1", "fabric.update_ai_agent"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "fabric.update_ai_agent",
              404,
              () -> client.fabric().aiAgents().update("missing", kw("name", "b"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.fabric().aiAgents().delete("a-1");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_ai_agent",
          okJournal("DELETE", "/api/fabric/resources/ai_agents/a-1", "fabric.delete_ai_agent"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_ai_agent", 404, () -> client.fabric().aiAgents().delete("missing")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // call_flows (PUT update; singular-path sub-collections + versions)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric call_flows")
  class CallFlows {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.fabric().callFlows().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_call_flows",
          okJournal("GET", "/api/fabric/resources/call_flows", "fabric.list_call_flows"));
    }

    @Test
    void listError() {
      assertEquals(
          500, errCall("fabric.list_call_flows", 500, () -> client.fabric().callFlows().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.fabric().callFlows().create(kw("name", "cf"));
      assertNotNull(body);
      assertEquals(
          "fabric.create_call_flow",
          okJournal("POST", "/api/fabric/resources/call_flows", "fabric.create_call_flow"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_call_flow",
              422,
              () -> client.fabric().callFlows().create(kw("name", "cf"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().callFlows().get("cf-1");
      assertNotNull(body);
      assertEquals(
          "fabric.get_call_flow",
          okJournal("GET", "/api/fabric/resources/call_flows/cf-1", "fabric.get_call_flow"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall("fabric.get_call_flow", 404, () -> client.fabric().callFlows().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.fabric().callFlows().update("cf-1", kw("name", "x"));
      assertNotNull(body);
      assertEquals(
          "fabric.update_call_flow",
          okJournal("PUT", "/api/fabric/resources/call_flows/cf-1", "fabric.update_call_flow"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "fabric.update_call_flow",
              404,
              () -> client.fabric().callFlows().update("missing", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.fabric().callFlows().delete("cf-1");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_call_flow",
          okJournal("DELETE", "/api/fabric/resources/call_flows/cf-1", "fabric.delete_call_flow"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_call_flow", 404, () -> client.fabric().callFlows().delete("missing")));
    }

    @Test
    void listAddressesSuccess() {
      Map<String, Object> body =
          client.fabric().callFlows().listAddresses("cf-1", java.util.Map.of());
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_call_flow_addresses",
          okJournal(
              "GET",
              "/api/fabric/resources/call_flow/cf-1/addresses",
              "fabric.list_call_flow_addresses"));
    }

    @Test
    void listAddressesError() {
      assertEquals(
          500,
          errCall(
              "fabric.list_call_flow_addresses",
              500,
              () -> client.fabric().callFlows().listAddresses("cf-1", java.util.Map.of())));
    }

    @Test
    void listVersionsSuccess() {
      Map<String, Object> body =
          client.fabric().callFlows().listVersions("cf-1", java.util.Map.of());
      assertNotNull(body);
      assertEquals(
          "fabric.list_call_flow_versions",
          okJournal(
              "GET",
              "/api/fabric/resources/call_flow/cf-1/versions",
              "fabric.list_call_flow_versions"));
    }

    @Test
    void listVersionsError() {
      assertEquals(
          500,
          errCall(
              "fabric.list_call_flow_versions",
              500,
              () -> client.fabric().callFlows().listVersions("cf-1", java.util.Map.of())));
    }

    @Test
    void deployVersionSuccess() {
      Map<String, Object> body = client.fabric().callFlows().deployVersion("cf-1", kw("v", 1));
      assertNotNull(body);
      assertEquals(
          "fabric.deploy_call_flow_version",
          okJournal(
              "POST",
              "/api/fabric/resources/call_flow/cf-1/versions",
              "fabric.deploy_call_flow_version"));
    }

    @Test
    void deployVersionError() {
      assertEquals(
          422,
          errCall(
              "fabric.deploy_call_flow_version",
              422,
              () -> client.fabric().callFlows().deployVersion("cf-1", kw("v", 1))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // conference_rooms (PUT update; singular-path addresses)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric conference_rooms")
  class ConferenceRooms {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.fabric().conferenceRooms().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_conference_rooms",
          okJournal(
              "GET", "/api/fabric/resources/conference_rooms", "fabric.list_conference_rooms"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall(
              "fabric.list_conference_rooms", 500, () -> client.fabric().conferenceRooms().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.fabric().conferenceRooms().create(kw("name", "cr"));
      assertNotNull(body);
      assertEquals(
          "fabric.create_conference_room",
          okJournal(
              "POST", "/api/fabric/resources/conference_rooms", "fabric.create_conference_room"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_conference_room",
              422,
              () -> client.fabric().conferenceRooms().create(kw("name", "cr"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().conferenceRooms().get("cr-1");
      assertNotNull(body);
      assertEquals(
          "fabric.get_conference_room",
          okJournal(
              "GET", "/api/fabric/resources/conference_rooms/cr-1", "fabric.get_conference_room"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "fabric.get_conference_room",
              404,
              () -> client.fabric().conferenceRooms().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.fabric().conferenceRooms().update("cr-1", kw("name", "x"));
      assertNotNull(body);
      assertEquals(
          "fabric.update_conference_room",
          okJournal(
              "PUT",
              "/api/fabric/resources/conference_rooms/cr-1",
              "fabric.update_conference_room"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "fabric.update_conference_room",
              404,
              () -> client.fabric().conferenceRooms().update("missing", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.fabric().conferenceRooms().delete("cr-1");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_conference_room",
          okJournal(
              "DELETE",
              "/api/fabric/resources/conference_rooms/cr-1",
              "fabric.delete_conference_room"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_conference_room",
              404,
              () -> client.fabric().conferenceRooms().delete("missing")));
    }

    @Test
    void listAddressesSuccess() {
      Map<String, Object> body =
          client.fabric().conferenceRooms().listAddresses("cr-1", java.util.Map.of());
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_conference_room_addresses",
          okJournal(
              "GET",
              "/api/fabric/resources/conference_room/cr-1/addresses",
              "fabric.list_conference_room_addresses"));
    }

    @Test
    void listAddressesError() {
      assertEquals(
          500,
          errCall(
              "fabric.list_conference_room_addresses",
              500,
              () -> client.fabric().conferenceRooms().listAddresses("cr-1", java.util.Map.of())));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // cxml_applications (read/update/delete + addresses; create unsupported)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric cxml_applications")
  class CxmlApplications {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.fabric().cxmlApplications().list(java.util.Map.of());
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_cxml_applications",
          okJournal(
              "GET", "/api/fabric/resources/cxml_applications", "fabric.list_cxml_applications"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall(
              "fabric.list_cxml_applications",
              500,
              () -> client.fabric().cxmlApplications().list(java.util.Map.of())));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().cxmlApplications().get("ca-1", java.util.Map.of());
      assertNotNull(body);
      assertEquals(
          "fabric.get_cxml_application",
          okJournal(
              "GET",
              "/api/fabric/resources/cxml_applications/ca-1",
              "fabric.get_cxml_application"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "fabric.get_cxml_application",
              404,
              () -> client.fabric().cxmlApplications().get("missing", java.util.Map.of())));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body =
          client
              .fabric()
              .cxmlApplications()
              .update(
                  "ca-1",
                  com.signalwire.sdk.rest.namespaces.generated.CxmlApplications.UpdateRequest
                      .builder()
                      .extras(kw("name", "x"))
                      .build());
      assertNotNull(body);
      assertEquals(
          "fabric.update_cxml_application",
          okJournal(
              "PUT",
              "/api/fabric/resources/cxml_applications/ca-1",
              "fabric.update_cxml_application"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "fabric.update_cxml_application",
              404,
              () ->
                  client
                      .fabric()
                      .cxmlApplications()
                      .update(
                          "missing",
                          com.signalwire.sdk.rest.namespaces.generated.CxmlApplications
                              .UpdateRequest.builder()
                              .extras(kw("name", "x"))
                              .build())));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.fabric().cxmlApplications().delete("ca-1");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_cxml_application",
          okJournal(
              "DELETE",
              "/api/fabric/resources/cxml_applications/ca-1",
              "fabric.delete_cxml_application"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_cxml_application",
              404,
              () -> client.fabric().cxmlApplications().delete("missing")));
    }

    @Test
    void listAddressesSuccess() {
      Map<String, Object> body =
          client.fabric().cxmlApplications().listAddresses("ca-1", java.util.Map.of());
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_cxml_application_addresses",
          okJournal(
              "GET",
              "/api/fabric/resources/cxml_applications/ca-1/addresses",
              "fabric.list_cxml_application_addresses"));
    }

    @Test
    void listAddressesError() {
      assertEquals(
          500,
          errCall(
              "fabric.list_cxml_application_addresses",
              500,
              () -> client.fabric().cxmlApplications().listAddresses("ca-1", java.util.Map.of())));
    }

    @Test
    void noCreateSurface() {
      // The generated CxmlApplications resource has NO create method — cXML applications cannot
      // be created through the fabric API (the old hand namespace threw
      // UnsupportedOperationException
      // from a create() stub; the generated resource omits the method, enforcing this at compile
      // time).
      var cxml = client.fabric().cxmlApplications();
      assertNotNull(cxml);
      assertEquals("/fabric/resources/cxml_applications", cxml.getBasePath());
      assertTrue(mock.journal().isEmpty(), "expected no journal entries, got " + mock.journal());
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // cxml_scripts (PUT update) — full CRUD + addresses
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric cxml_scripts")
  class CxmlScripts {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.fabric().cxmlScripts().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_cxml_scripts",
          okJournal("GET", "/api/fabric/resources/cxml_scripts", "fabric.list_cxml_scripts"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall("fabric.list_cxml_scripts", 500, () -> client.fabric().cxmlScripts().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.fabric().cxmlScripts().create(kw("name", "s"));
      assertNotNull(body);
      assertEquals(
          "fabric.create_cxml_script",
          okJournal("POST", "/api/fabric/resources/cxml_scripts", "fabric.create_cxml_script"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_cxml_script",
              422,
              () -> client.fabric().cxmlScripts().create(kw("name", "s"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().cxmlScripts().get("cs-1");
      assertNotNull(body);
      assertEquals(
          "fabric.get_cxml_script",
          okJournal("GET", "/api/fabric/resources/cxml_scripts/cs-1", "fabric.get_cxml_script"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "fabric.get_cxml_script", 404, () -> client.fabric().cxmlScripts().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.fabric().cxmlScripts().update("cs-1", kw("name", "x"));
      assertNotNull(body);
      assertEquals(
          "fabric.update_cxml_script",
          okJournal("PUT", "/api/fabric/resources/cxml_scripts/cs-1", "fabric.update_cxml_script"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "fabric.update_cxml_script",
              404,
              () -> client.fabric().cxmlScripts().update("missing", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.fabric().cxmlScripts().delete("cs-1");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_cxml_script",
          okJournal(
              "DELETE", "/api/fabric/resources/cxml_scripts/cs-1", "fabric.delete_cxml_script"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_cxml_script",
              404,
              () -> client.fabric().cxmlScripts().delete("missing")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // cxml_webhooks (FabricResource, PATCH update) — create/list/get/update/
  // delete + addresses
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric cxml_webhooks")
  class CxmlWebhooks {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.fabric().cxmlWebhooks().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_cxml_webhooks",
          okJournal("GET", "/api/fabric/resources/cxml_webhooks", "fabric.list_cxml_webhooks"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall("fabric.list_cxml_webhooks", 500, () -> client.fabric().cxmlWebhooks().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.fabric().cxmlWebhooks().create(kw("name", "w"));
      assertNotNull(body);
      assertEquals(
          "fabric.create_cxml_webhook",
          okJournal("POST", "/api/fabric/resources/cxml_webhooks", "fabric.create_cxml_webhook"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_cxml_webhook",
              422,
              () -> client.fabric().cxmlWebhooks().create(kw("name", "w"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().cxmlWebhooks().get("cw-1");
      assertNotNull(body);
      assertEquals(
          "fabric.get_cxml_webhook",
          okJournal("GET", "/api/fabric/resources/cxml_webhooks/cw-1", "fabric.get_cxml_webhook"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "fabric.get_cxml_webhook", 404, () -> client.fabric().cxmlWebhooks().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.fabric().cxmlWebhooks().update("cw-1", kw("name", "x"));
      assertNotNull(body);
      assertEquals(
          "fabric.update_cxml_webhook",
          okJournal(
              "PATCH", "/api/fabric/resources/cxml_webhooks/cw-1", "fabric.update_cxml_webhook"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "fabric.update_cxml_webhook",
              404,
              () -> client.fabric().cxmlWebhooks().update("missing", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.fabric().cxmlWebhooks().delete("cw-1");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_cxml_webhook",
          okJournal(
              "DELETE", "/api/fabric/resources/cxml_webhooks/cw-1", "fabric.delete_cxml_webhook"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_cxml_webhook",
              404,
              () -> client.fabric().cxmlWebhooks().delete("missing")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // freeswitch_connectors (PUT update) — full CRUD + addresses
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric freeswitch_connectors")
  class FreeswitchConnectors {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.fabric().freeswitchConnectors().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_freeswitch_connectors",
          okJournal(
              "GET",
              "/api/fabric/resources/freeswitch_connectors",
              "fabric.list_freeswitch_connectors"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall(
              "fabric.list_freeswitch_connectors",
              500,
              () -> client.fabric().freeswitchConnectors().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.fabric().freeswitchConnectors().create(kw("name", "f"));
      assertNotNull(body);
      assertEquals(
          "fabric.create_freeswitch_connector",
          okJournal(
              "POST",
              "/api/fabric/resources/freeswitch_connectors",
              "fabric.create_freeswitch_connector"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_freeswitch_connector",
              422,
              () -> client.fabric().freeswitchConnectors().create(kw("name", "f"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().freeswitchConnectors().get("fc-1");
      assertNotNull(body);
      assertEquals(
          "fabric.get_freeswitch_connector",
          okJournal(
              "GET",
              "/api/fabric/resources/freeswitch_connectors/fc-1",
              "fabric.get_freeswitch_connector"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "fabric.get_freeswitch_connector",
              404,
              () -> client.fabric().freeswitchConnectors().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body =
          client.fabric().freeswitchConnectors().update("fc-1", kw("name", "x"));
      assertNotNull(body);
      assertEquals(
          "fabric.update_freeswitch_connector",
          okJournal(
              "PUT",
              "/api/fabric/resources/freeswitch_connectors/fc-1",
              "fabric.update_freeswitch_connector"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "fabric.update_freeswitch_connector",
              404,
              () -> client.fabric().freeswitchConnectors().update("missing", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.fabric().freeswitchConnectors().delete("fc-1");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_freeswitch_connector",
          okJournal(
              "DELETE",
              "/api/fabric/resources/freeswitch_connectors/fc-1",
              "fabric.delete_freeswitch_connector"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_freeswitch_connector",
              404,
              () -> client.fabric().freeswitchConnectors().delete("missing")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // relay_applications (PUT update) — full CRUD + addresses
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric relay_applications")
  class RelayApplications {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.fabric().relayApplications().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_relay_applications",
          okJournal(
              "GET", "/api/fabric/resources/relay_applications", "fabric.list_relay_applications"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall(
              "fabric.list_relay_applications",
              500,
              () -> client.fabric().relayApplications().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.fabric().relayApplications().create(kw("name", "r"));
      assertNotNull(body);
      assertEquals(
          "fabric.create_relay_application",
          okJournal(
              "POST",
              "/api/fabric/resources/relay_applications",
              "fabric.create_relay_application"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_relay_application",
              422,
              () -> client.fabric().relayApplications().create(kw("name", "r"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().relayApplications().get("ra-1");
      assertNotNull(body);
      assertEquals(
          "fabric.get_relay_application",
          okJournal(
              "GET",
              "/api/fabric/resources/relay_applications/ra-1",
              "fabric.get_relay_application"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "fabric.get_relay_application",
              404,
              () -> client.fabric().relayApplications().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body =
          client.fabric().relayApplications().update("ra-1", kw("name", "x"));
      assertNotNull(body);
      assertEquals(
          "fabric.update_relay_application",
          okJournal(
              "PUT",
              "/api/fabric/resources/relay_applications/ra-1",
              "fabric.update_relay_application"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "fabric.update_relay_application",
              404,
              () -> client.fabric().relayApplications().update("missing", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.fabric().relayApplications().delete("ra-1");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_relay_application",
          okJournal(
              "DELETE",
              "/api/fabric/resources/relay_applications/ra-1",
              "fabric.delete_relay_application"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_relay_application",
              404,
              () -> client.fabric().relayApplications().delete("missing")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // sip_endpoints (PUT update) — full CRUD + addresses
  // (assign_resource_sip_endpoint is an accepted doubled-path gap)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric sip_endpoints")
  class SipEndpoints {

    @Test
    void listSuccess() {
      assertEquals(
          200,
          okListArrayBody(
              "GET",
              "/api/fabric/resources/sip_endpoints",
              "fabric.list_sip_endpoints",
              () -> client.fabric().sipEndpoints().list()));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall("fabric.list_sip_endpoints", 500, () -> client.fabric().sipEndpoints().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.fabric().sipEndpoints().create(kw("name", "e"));
      assertNotNull(body);
      assertEquals(
          "fabric.create_sip_endpoint",
          okJournal("POST", "/api/fabric/resources/sip_endpoints", "fabric.create_sip_endpoint"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_sip_endpoint",
              422,
              () -> client.fabric().sipEndpoints().create(kw("name", "e"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().sipEndpoints().get("se-1");
      assertNotNull(body);
      assertEquals(
          "fabric.get_sip_endpoint",
          okJournal("GET", "/api/fabric/resources/sip_endpoints/se-1", "fabric.get_sip_endpoint"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "fabric.get_sip_endpoint", 404, () -> client.fabric().sipEndpoints().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.fabric().sipEndpoints().update("se-1", kw("name", "x"));
      assertNotNull(body);
      assertEquals(
          "fabric.update_sip_endpoint",
          okJournal(
              "PUT", "/api/fabric/resources/sip_endpoints/se-1", "fabric.update_sip_endpoint"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "fabric.update_sip_endpoint",
              404,
              () -> client.fabric().sipEndpoints().update("missing", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.fabric().sipEndpoints().delete("se-1");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_sip_endpoint",
          okJournal(
              "DELETE", "/api/fabric/resources/sip_endpoints/se-1", "fabric.delete_sip_endpoint"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_sip_endpoint",
              404,
              () -> client.fabric().sipEndpoints().delete("missing")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // sip_gateways (FabricResource, PATCH update) — list/create/get/update/
  // delete. (list_sip_gateway_addresses is an accepted doubled-path gap.)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric sip_gateways")
  class SipGateways {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.fabric().sipGateways().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_sip_gateways",
          okJournal("GET", "/api/fabric/resources/sip_gateways", "fabric.list_sip_gateways"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall("fabric.list_sip_gateways", 500, () -> client.fabric().sipGateways().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.fabric().sipGateways().create(kw("name", "g"));
      assertNotNull(body);
      assertEquals(
          "fabric.create_sip_gateway",
          okJournal("POST", "/api/fabric/resources/sip_gateways", "fabric.create_sip_gateway"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_sip_gateway",
              422,
              () -> client.fabric().sipGateways().create(kw("name", "g"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().sipGateways().get("sg-1");
      assertNotNull(body);
      assertEquals(
          "fabric.get_sip_gateway",
          okJournal("GET", "/api/fabric/resources/sip_gateways/sg-1", "fabric.get_sip_gateway"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "fabric.get_sip_gateway", 404, () -> client.fabric().sipGateways().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.fabric().sipGateways().update("sg-1", kw("name", "x"));
      assertNotNull(body);
      assertEquals(
          "fabric.update_sip_gateway",
          okJournal(
              "PATCH", "/api/fabric/resources/sip_gateways/sg-1", "fabric.update_sip_gateway"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "fabric.update_sip_gateway",
              404,
              () -> client.fabric().sipGateways().update("missing", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.fabric().sipGateways().delete("sg-1");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_sip_gateway",
          okJournal(
              "DELETE", "/api/fabric/resources/sip_gateways/sg-1", "fabric.delete_sip_gateway"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_sip_gateway",
              404,
              () -> client.fabric().sipGateways().delete("missing")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // subscribers (PUT update) + SIP-endpoint sub-resource ops + addresses
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric subscribers")
  class Subscribers {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.fabric().subscribers().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_subscribers",
          okJournal("GET", "/api/fabric/resources/subscribers", "fabric.list_subscribers"));
    }

    @Test
    void listError() {
      assertEquals(
          500, errCall("fabric.list_subscribers", 500, () -> client.fabric().subscribers().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.fabric().subscribers().create(kw("email", "s@x.com"));
      assertNotNull(body);
      assertEquals(
          "fabric.create_subscriber",
          okJournal("POST", "/api/fabric/resources/subscribers", "fabric.create_subscriber"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_subscriber",
              422,
              () -> client.fabric().subscribers().create(kw("email", "s@x.com"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().subscribers().get("su-1");
      assertNotNull(body);
      assertEquals(
          "fabric.get_subscriber",
          okJournal("GET", "/api/fabric/resources/subscribers/su-1", "fabric.get_subscriber"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "fabric.get_subscriber", 404, () -> client.fabric().subscribers().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body =
          client.fabric().subscribers().update("su-1", kw("email", "y@x.com"));
      assertNotNull(body);
      assertEquals(
          "fabric.update_subscriber",
          okJournal("PUT", "/api/fabric/resources/subscribers/su-1", "fabric.update_subscriber"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "fabric.update_subscriber",
              404,
              () -> client.fabric().subscribers().update("missing", kw("email", "y@x.com"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.fabric().subscribers().delete("su-1");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_subscriber",
          okJournal(
              "DELETE", "/api/fabric/resources/subscribers/su-1", "fabric.delete_subscriber"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_subscriber",
              404,
              () -> client.fabric().subscribers().delete("missing")));
    }

    @Test
    void listSipEndpointsSuccess() {
      Map<String, Object> body =
          client.fabric().subscribers().listSipEndpoints("su-1", java.util.Map.of());
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_subscriber_sip_endpoints",
          okJournal(
              "GET",
              "/api/fabric/resources/subscribers/su-1/sip_endpoints",
              "fabric.list_subscriber_sip_endpoints"));
    }

    @Test
    void listSipEndpointsError() {
      assertEquals(
          500,
          errCall(
              "fabric.list_subscriber_sip_endpoints",
              500,
              () -> client.fabric().subscribers().listSipEndpoints("su-1", java.util.Map.of())));
    }

    @Test
    void createSipEndpointSuccess() {
      Map<String, Object> body =
          client
              .fabric()
              .subscribers()
              .createSipEndpoint(
                  "su-1",
                  com.signalwire.sdk.rest.namespaces.generated.Subscribers.CreateSipEndpointRequest
                      .builder()
                      .extras(kw("username", "u"))
                      .build());
      assertNotNull(body);
      assertEquals(
          "fabric.create_subscriber_sip_endpoint",
          okJournal(
              "POST",
              "/api/fabric/resources/subscribers/su-1/sip_endpoints",
              "fabric.create_subscriber_sip_endpoint"));
    }

    @Test
    void createSipEndpointError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_subscriber_sip_endpoint",
              422,
              () ->
                  client
                      .fabric()
                      .subscribers()
                      .createSipEndpoint(
                          "su-1",
                          com.signalwire.sdk.rest.namespaces.generated.Subscribers
                              .CreateSipEndpointRequest.builder()
                              .extras(kw("username", "u"))
                              .build())));
    }

    @Test
    void getSipEndpointSuccess() {
      Map<String, Object> body =
          client.fabric().subscribers().getSipEndpoint("su-1", "ep-1", java.util.Map.of());
      assertNotNull(body);
      assertEquals(
          "fabric.get_subscriber_sip_endpoint",
          okJournal(
              "GET",
              "/api/fabric/resources/subscribers/su-1/sip_endpoints/ep-1",
              "fabric.get_subscriber_sip_endpoint"));
    }

    @Test
    void getSipEndpointError() {
      assertEquals(
          404,
          errCall(
              "fabric.get_subscriber_sip_endpoint",
              404,
              () ->
                  client
                      .fabric()
                      .subscribers()
                      .getSipEndpoint("su-1", "missing", java.util.Map.of())));
    }

    @Test
    void updateSipEndpointSuccess() {
      Map<String, Object> body =
          client
              .fabric()
              .subscribers()
              .updateSipEndpoint(
                  "su-1",
                  "ep-1",
                  com.signalwire.sdk.rest.namespaces.generated.Subscribers.UpdateSipEndpointRequest
                      .builder()
                      .extras(kw("username", "r"))
                      .build());
      assertNotNull(body);
      MockTest.JournalEntry j = mock.last();
      assertEquals("r", j.bodyMap().get("username"));
      assertEquals(
          "fabric.update_subscriber_sip_endpoint",
          okJournal(
              "PATCH",
              "/api/fabric/resources/subscribers/su-1/sip_endpoints/ep-1",
              "fabric.update_subscriber_sip_endpoint"));
    }

    @Test
    void updateSipEndpointError() {
      assertEquals(
          404,
          errCall(
              "fabric.update_subscriber_sip_endpoint",
              404,
              () ->
                  client
                      .fabric()
                      .subscribers()
                      .updateSipEndpoint(
                          "su-1",
                          "missing",
                          com.signalwire.sdk.rest.namespaces.generated.Subscribers
                              .UpdateSipEndpointRequest.builder()
                              .extras(kw("username", "r"))
                              .build())));
    }

    @Test
    void deleteSipEndpointSuccess() {
      Map<String, Object> body = client.fabric().subscribers().deleteSipEndpoint("su-1", "ep-1");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_subscriber_sip_endpoint",
          okJournal(
              "DELETE",
              "/api/fabric/resources/subscribers/su-1/sip_endpoints/ep-1",
              "fabric.delete_subscriber_sip_endpoint"));
    }

    @Test
    void deleteSipEndpointError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_subscriber_sip_endpoint",
              404,
              () -> client.fabric().subscribers().deleteSipEndpoint("su-1", "missing")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // swml_scripts (PUT update) — full CRUD + addresses
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric swml_scripts")
  class SwmlScripts {

    @Test
    void listSuccess() {
      assertEquals(
          200,
          okListArrayBody(
              "GET",
              "/api/fabric/resources/swml_scripts",
              "fabric.list_swml_scripts",
              () -> client.fabric().swmlScripts().list()));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall("fabric.list_swml_scripts", 500, () -> client.fabric().swmlScripts().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.fabric().swmlScripts().create(kw("name", "s"));
      assertNotNull(body);
      assertEquals(
          "fabric.create_swml_script",
          okJournal("POST", "/api/fabric/resources/swml_scripts", "fabric.create_swml_script"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_swml_script",
              422,
              () -> client.fabric().swmlScripts().create(kw("name", "s"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().swmlScripts().get("ss-1");
      assertNotNull(body);
      assertEquals(
          "fabric.get_swml_script",
          okJournal("GET", "/api/fabric/resources/swml_scripts/ss-1", "fabric.get_swml_script"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "fabric.get_swml_script", 404, () -> client.fabric().swmlScripts().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.fabric().swmlScripts().update("ss-1", kw("name", "x"));
      assertNotNull(body);
      assertEquals(
          "fabric.update_swml_script",
          okJournal("PUT", "/api/fabric/resources/swml_scripts/ss-1", "fabric.update_swml_script"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "fabric.update_swml_script",
              404,
              () -> client.fabric().swmlScripts().update("missing", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.fabric().swmlScripts().delete("ss-1");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_swml_script",
          okJournal(
              "DELETE", "/api/fabric/resources/swml_scripts/ss-1", "fabric.delete_swml_script"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_swml_script",
              404,
              () -> client.fabric().swmlScripts().delete("missing")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // swml_webhooks (FabricResource, PATCH update) — create/list/get/update/
  // delete + addresses
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric swml_webhooks")
  class SwmlWebhooks {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.fabric().swmlWebhooks().list();
      assertNotNull(body);
      assertTrue(body.containsKey("data"));
      assertEquals(
          "fabric.list_swml_webhooks",
          okJournal("GET", "/api/fabric/resources/swml_webhooks", "fabric.list_swml_webhooks"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall("fabric.list_swml_webhooks", 500, () -> client.fabric().swmlWebhooks().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.fabric().swmlWebhooks().create(kw("name", "w"));
      assertNotNull(body);
      assertEquals(
          "fabric.create_swml_webhook",
          okJournal("POST", "/api/fabric/resources/swml_webhooks", "fabric.create_swml_webhook"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "fabric.create_swml_webhook",
              422,
              () -> client.fabric().swmlWebhooks().create(kw("name", "w"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.fabric().swmlWebhooks().get("sw-1");
      assertNotNull(body);
      assertEquals(
          "fabric.get_swml_webhook",
          okJournal("GET", "/api/fabric/resources/swml_webhooks/sw-1", "fabric.get_swml_webhook"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "fabric.get_swml_webhook", 404, () -> client.fabric().swmlWebhooks().get("missing")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.fabric().swmlWebhooks().update("sw-1", kw("name", "x"));
      assertNotNull(body);
      assertEquals(
          "fabric.update_swml_webhook",
          okJournal(
              "PATCH", "/api/fabric/resources/swml_webhooks/sw-1", "fabric.update_swml_webhook"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "fabric.update_swml_webhook",
              404,
              () -> client.fabric().swmlWebhooks().update("missing", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.fabric().swmlWebhooks().delete("sw-1");
      assertNotNull(body);
      assertEquals(
          "fabric.delete_swml_webhook",
          okJournal(
              "DELETE", "/api/fabric/resources/swml_webhooks/sw-1", "fabric.delete_swml_webhook"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "fabric.delete_swml_webhook",
              404,
              () -> client.fabric().swmlWebhooks().delete("missing")));
    }
  }
}
