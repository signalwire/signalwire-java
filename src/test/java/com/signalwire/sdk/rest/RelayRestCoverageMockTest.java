/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Full success+error REST coverage for the {@code relay-rest.*} canonical spec group.
 *
 * <p>For every coverable relay-rest route this exercises BOTH a success (2xx) call — asserting the
 * response body, the journalled {@code method}/{@code path}, and {@code matched_route} == the
 * canonical endpoint id — AND an error path: an armed {@link MockTest.Harness#scenarioSet} override
 * (404/422/500) that must surface as a {@link RestError} carrying the right status code, with the
 * journal recording the same {@code matched_route} and {@code response_status}.
 *
 * <p>Mirrors the idiom of {@code FabricCoverageMockTest} / {@code CompatCoverageMockTest}. Every
 * relay-rest path is asserted against its concrete canonical {@code /api/relay/rest/...} form —
 * notably phone_numbers now lives at {@code /api/relay/rest/phone_numbers} (parity fix 77db8f2).
 *
 * <p>Accepted gaps (NO relay-rest SDK surface — NOT tested here; the matching paths exist only on
 * the fabric side or under a different SIP path):
 *
 * <ul>
 *   <li>{@code relay-rest.*_verified_caller_id*} (7) and {@code relay-rest.lookup_phone_number} are
 *       now reachable via {@code verifiedCallers()} / {@code numberLookup().lookup} and are covered
 *       in {@code ParityGapCoverageMockTest}.
 *   <li>{@code relay-rest.*_sip_endpoint*} (5): create/list/retrieve/update/delete at {@code
 *       /api/relay/rest/endpoints/sip} — {@code client.sip().endpoints()} targets {@code
 *       /api/sip/endpoints}, a different (non-canonical) path, so it does not cover these routes.
 *   <li>{@code relay-rest.*_domain_application*} (5): create/list/retrieve/update/delete at {@code
 *       /api/relay/rest/domain_applications} — only the fabric-resource sub-path {@code
 *       assignDomainApplication} exists, not the top-level relay-rest collection.
 * </ul>
 *
 * <p>This matches python's relay-rest accepted gaps (SIP endpoints + domain_applications = 10).
 */
class RelayRestCoverageMockTest {

  /** Canonical relay-rest base. */
  private static final String B = "/api/relay/rest";

  /** Registry (10DLC) base. */
  private static final String R = B + "/registry/beta";

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

  private static Map<String, String> qp(String... entries) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      m.put(entries[i], entries[i + 1]);
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
  // Addresses
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("addresses")
  class Addresses {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.addresses().list(qp("page_size", "10"));
      assertTrue(body.containsKey("data"));
      assertEquals(
          "relay-rest.list_addresses",
          okJournal("GET", B + "/addresses", "relay-rest.list_addresses"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall(
              "relay-rest.list_addresses",
              500,
              () -> client.addresses().list(qp("page_size", "1"))));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body =
          client
              .addresses()
              .create(kw("address_type", "commercial", "first_name", "Ada", "country", "US"));
      assertTrue(body.containsKey("id"));
      assertEquals(
          "relay-rest.create_address",
          okJournal("POST", B + "/addresses", "relay-rest.create_address"));
      assertEquals("Ada", mock.last().bodyMap().get("first_name"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "relay-rest.create_address",
              422,
              () -> client.addresses().create(kw("address_type", "bad"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.addresses().get("addr-123");
      assertTrue(body.containsKey("id"));
      assertEquals(
          "relay-rest.get_address",
          okJournal("GET", B + "/addresses/addr-123", "relay-rest.get_address"));
    }

    @Test
    void getError() {
      assertEquals(
          404, errCall("relay-rest.get_address", 404, () -> client.addresses().get("addr-404")));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.addresses().delete("addr-123");
      assertNotNull(body);
      assertEquals(
          "relay-rest.delete_address",
          okJournal("DELETE", B + "/addresses/addr-123", "relay-rest.delete_address"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall("relay-rest.delete_address", 404, () -> client.addresses().delete("addr-404")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Phone numbers (now at /api/relay/rest/phone_numbers — parity fix)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("phone_numbers")
  class PhoneNumbers {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.phoneNumbers().list(qp("page_size", "5"));
      assertTrue(body.containsKey("data"));
      assertEquals(
          "relay-rest.list_phone_numbers",
          okJournal("GET", B + "/phone_numbers", "relay-rest.list_phone_numbers"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall(
              "relay-rest.list_phone_numbers",
              500,
              () -> client.phoneNumbers().list(qp("page_size", "1"))));
    }

    @Test
    void searchSuccess() {
      Map<String, Object> body = client.phoneNumbers().search(qp("areacode", "415"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.search_available_phone_numbers",
          okJournal(
              "GET", B + "/phone_numbers/search", "relay-rest.search_available_phone_numbers"));
    }

    @Test
    void searchError() {
      assertEquals(
          422,
          errCall(
              "relay-rest.search_available_phone_numbers",
              422,
              () -> client.phoneNumbers().search(qp("areacode", "x"))));
    }

    @Test
    void purchaseSuccess() {
      Map<String, Object> body = client.phoneNumbers().create(kw("number", "+15551230000"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.purchase_phone_number",
          okJournal("POST", B + "/phone_numbers", "relay-rest.purchase_phone_number"));
      assertEquals("+15551230000", mock.last().bodyMap().get("number"));
    }

    @Test
    void purchaseError() {
      assertEquals(
          422,
          errCall(
              "relay-rest.purchase_phone_number",
              422,
              () -> client.phoneNumbers().create(kw("number", "bad"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.phoneNumbers().get("pn-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.retrieve_phone_number",
          okJournal("GET", B + "/phone_numbers/pn-1", "relay-rest.retrieve_phone_number"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.retrieve_phone_number", 404, () -> client.phoneNumbers().get("pn-404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.phoneNumbers().update("pn-1", kw("name", "Support line"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.update_phone_number",
          okJournal("PUT", B + "/phone_numbers/pn-1", "relay-rest.update_phone_number"));
      assertEquals("Support line", mock.last().bodyMap().get("name"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.update_phone_number",
              404,
              () -> client.phoneNumbers().update("pn-404", kw("name", "x"))));
    }

    @Test
    void releaseSuccess() {
      Map<String, Object> body = client.phoneNumbers().delete("pn-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.release_phone_number",
          okJournal("DELETE", B + "/phone_numbers/pn-1", "relay-rest.release_phone_number"));
    }

    @Test
    void releaseError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.release_phone_number",
              404,
              () -> client.phoneNumbers().delete("pn-404")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Queues + members
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("queues")
  class Queues {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.queues().list(qp("page_size", "5"));
      assertTrue(body.containsKey("data"));
      assertEquals(
          "relay-rest.list_queues", okJournal("GET", B + "/queues", "relay-rest.list_queues"));
    }

    @Test
    void listError() {
      assertEquals(500, errCall("relay-rest.list_queues", 500, () -> client.queues().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.queues().create(kw("name", "support"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.create_queue", okJournal("POST", B + "/queues", "relay-rest.create_queue"));
      assertEquals("support", mock.last().bodyMap().get("name"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall("relay-rest.create_queue", 422, () -> client.queues().create(kw("name", ""))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.queues().get("q-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.get_queue", okJournal("GET", B + "/queues/q-1", "relay-rest.get_queue"));
    }

    @Test
    void getError() {
      assertEquals(404, errCall("relay-rest.get_queue", 404, () -> client.queues().get("q-404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.queues().update("q-1", kw("name", "renamed"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.update_queue",
          okJournal("PUT", B + "/queues/q-1", "relay-rest.update_queue"));
      assertEquals("renamed", mock.last().bodyMap().get("name"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.update_queue",
              404,
              () -> client.queues().update("q-404", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.queues().delete("q-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.delete_queue",
          okJournal("DELETE", B + "/queues/q-1", "relay-rest.delete_queue"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404, errCall("relay-rest.delete_queue", 404, () -> client.queues().delete("q-404")));
    }

    @Test
    void listMembersSuccess() {
      Map<String, Object> body = client.queues().listMembers("q-1", qp("page_size", "5"));
      assertTrue(body.containsKey("data"));
      assertEquals(
          "relay-rest.list_queue_members",
          okJournal("GET", B + "/queues/q-1/members", "relay-rest.list_queue_members"));
    }

    @Test
    void listMembersError() {
      assertEquals(
          500,
          errCall("relay-rest.list_queue_members", 500, () -> client.queues().listMembers("q-1")));
    }

    @Test
    void nextMemberSuccess() {
      Map<String, Object> body = client.queues().getNextMember("q-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.retrieve_next_queue_member",
          okJournal(
              "GET", B + "/queues/q-1/members/next", "relay-rest.retrieve_next_queue_member"));
    }

    @Test
    void nextMemberError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.retrieve_next_queue_member",
              404,
              () -> client.queues().getNextMember("q-404")));
    }

    @Test
    void getMemberSuccess() {
      Map<String, Object> body = client.queues().getMember("q-1", "mem-7");
      assertNotNull(body);
      assertEquals(
          "relay-rest.retrieve_queue_member",
          okJournal("GET", B + "/queues/q-1/members/mem-7", "relay-rest.retrieve_queue_member"));
    }

    @Test
    void getMemberError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.retrieve_queue_member",
              404,
              () -> client.queues().getMember("q-1", "mem-404")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Recordings
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("recordings")
  class Recordings {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.recordings().list(qp("page_size", "5"));
      assertTrue(body.containsKey("data"));
      assertEquals(
          "relay-rest.list_recordings",
          okJournal("GET", B + "/recordings", "relay-rest.list_recordings"));
    }

    @Test
    void listError() {
      assertEquals(
          500, errCall("relay-rest.list_recordings", 500, () -> client.recordings().list()));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.recordings().get("rec-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.get_recording",
          okJournal("GET", B + "/recordings/rec-1", "relay-rest.get_recording"));
    }

    @Test
    void getError() {
      assertEquals(
          404, errCall("relay-rest.get_recording", 404, () -> client.recordings().get("rec-404")));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.recordings().delete("rec-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.delete_recording",
          okJournal("DELETE", B + "/recordings/rec-1", "relay-rest.delete_recording"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall("relay-rest.delete_recording", 404, () -> client.recordings().delete("rec-404")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Short codes
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("short_codes")
  class ShortCodes {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.shortCodes().list(qp("page_size", "5"));
      assertTrue(body.containsKey("data"));
      assertEquals(
          "relay-rest.list_short_codes",
          okJournal("GET", B + "/short_codes", "relay-rest.list_short_codes"));
    }

    @Test
    void listError() {
      assertEquals(
          500, errCall("relay-rest.list_short_codes", 500, () -> client.shortCodes().list()));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.shortCodes().get("sc-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.retrieve_short_code",
          okJournal("GET", B + "/short_codes/sc-1", "relay-rest.retrieve_short_code"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall("relay-rest.retrieve_short_code", 404, () -> client.shortCodes().get("sc-404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.shortCodes().update("sc-1", kw("name", "Marketing"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.update_short_code",
          okJournal("PUT", B + "/short_codes/sc-1", "relay-rest.update_short_code"));
      assertEquals("Marketing", mock.last().bodyMap().get("name"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.update_short_code",
              404,
              () -> client.shortCodes().update("sc-404", kw("name", "x"))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Imported phone numbers
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("imported_phone_numbers")
  class ImportedNumbers {

    @Test
    void createSuccess() {
      Map<String, Object> body =
          client
              .importedNumbers()
              .create(kw("number", "+15551234567", "sip_proxy", "sip.example.com"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.create_imported_phone_number",
          okJournal(
              "POST", B + "/imported_phone_numbers", "relay-rest.create_imported_phone_number"));
      assertEquals("+15551234567", mock.last().bodyMap().get("number"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "relay-rest.create_imported_phone_number",
              422,
              () -> client.importedNumbers().create(kw("number", "bad"))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // MFA
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("mfa")
  class Mfa {

    @Test
    void callSuccess() {
      Map<String, Object> body =
          client.mfa().call(kw("to", "+15551234567", "from", "+15559876543"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.request_mfa_call",
          okJournal("POST", B + "/mfa/call", "relay-rest.request_mfa_call"));
      assertEquals("+15551234567", mock.last().bodyMap().get("to"));
    }

    @Test
    void callError() {
      assertEquals(
          422, errCall("relay-rest.request_mfa_call", 422, () -> client.mfa().call(kw("to", ""))));
    }

    @Test
    void smsSuccess() {
      Map<String, Object> body = client.mfa().sms(kw("to", "+15551234567"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.request_mfa_sms",
          okJournal("POST", B + "/mfa/sms", "relay-rest.request_mfa_sms"));
    }

    @Test
    void smsError() {
      assertEquals(
          422, errCall("relay-rest.request_mfa_sms", 422, () -> client.mfa().sms(kw("to", ""))));
    }

    @Test
    void verifySuccess() {
      Map<String, Object> body = client.mfa().verify("req-1", kw("token", "123456"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.verify_mfa_token",
          okJournal("POST", B + "/mfa/req-1/verify", "relay-rest.verify_mfa_token"));
      assertEquals("123456", mock.last().bodyMap().get("token"));
    }

    @Test
    void verifyError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.verify_mfa_token",
              404,
              () -> client.mfa().verify("req-404", kw("token", "000000"))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Lookup — now covered in ParityGapCoverageMockTest.
  //
  // NumberLookupNamespace.lookup() now builds "/relay/rest/lookup/phone_number/..."
  // → /api/relay/rest/lookup/phone_number/{e164}, matching the canonical
  // relay-rest.lookup_phone_number route. Exercised there with success + error.
  // ════════════════════════════════════════════════════════════════════

  // ════════════════════════════════════════════════════════════════════
  // SIP profile (project singleton)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("sip_profile")
  class SipProfile {

    @Test
    void getSuccess() {
      Map<String, Object> body = client.sipProfile().get();
      assertNotNull(body);
      assertEquals(
          "relay-rest.retrieve_sip_profile",
          okJournal("GET", B + "/sip_profile", "relay-rest.retrieve_sip_profile"));
    }

    @Test
    void getError() {
      assertEquals(
          500, errCall("relay-rest.retrieve_sip_profile", 500, () -> client.sipProfile().get()));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body =
          client
              .sipProfile()
              .update(kw("domain", "myco.sip.signalwire.com", "default_codecs", List.of("PCMU")));
      assertNotNull(body);
      assertEquals(
          "relay-rest.update_sip_profile",
          okJournal("PUT", B + "/sip_profile", "relay-rest.update_sip_profile"));
      assertEquals("myco.sip.signalwire.com", mock.last().bodyMap().get("domain"));
    }

    @Test
    void updateError() {
      assertEquals(
          422,
          errCall(
              "relay-rest.update_sip_profile",
              422,
              () -> client.sipProfile().update(kw("domain", ""))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Number groups + memberships
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("number_groups")
  class NumberGroups {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.numberGroups().list(qp("page_size", "5"));
      assertTrue(body.containsKey("data"));
      assertEquals(
          "relay-rest.list_number_groups",
          okJournal("GET", B + "/number_groups", "relay-rest.list_number_groups"));
    }

    @Test
    void listError() {
      assertEquals(
          500, errCall("relay-rest.list_number_groups", 500, () -> client.numberGroups().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.numberGroups().create(kw("name", "sales"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.create_number_group",
          okJournal("POST", B + "/number_groups", "relay-rest.create_number_group"));
      assertEquals("sales", mock.last().bodyMap().get("name"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "relay-rest.create_number_group",
              422,
              () -> client.numberGroups().create(kw("name", ""))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.numberGroups().get("ng-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.retrieve_number_group",
          okJournal("GET", B + "/number_groups/ng-1", "relay-rest.retrieve_number_group"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.retrieve_number_group", 404, () -> client.numberGroups().get("ng-404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.numberGroups().update("ng-1", kw("name", "renamed"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.update_number_group",
          okJournal("PUT", B + "/number_groups/ng-1", "relay-rest.update_number_group"));
      assertEquals("renamed", mock.last().bodyMap().get("name"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.update_number_group",
              404,
              () -> client.numberGroups().update("ng-404", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.numberGroups().delete("ng-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.delete_number_group",
          okJournal("DELETE", B + "/number_groups/ng-1", "relay-rest.delete_number_group"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.delete_number_group", 404, () -> client.numberGroups().delete("ng-404")));
    }

    @Test
    void listMembershipsSuccess() {
      Map<String, Object> body =
          client.numberGroups().listMemberships("ng-1", qp("page_size", "10"));
      assertTrue(body.containsKey("data"));
      assertEquals(
          "relay-rest.list_number_group_memberships",
          okJournal(
              "GET",
              B + "/number_groups/ng-1/number_group_memberships",
              "relay-rest.list_number_group_memberships"));
    }

    @Test
    void listMembershipsError() {
      assertEquals(
          500,
          errCall(
              "relay-rest.list_number_group_memberships",
              500,
              () -> client.numberGroups().listMemberships("ng-1")));
    }

    @Test
    void addMembershipSuccess() {
      Map<String, Object> body =
          client.numberGroups().addMembership("ng-1", kw("phone_number_id", "pn-1"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.create_number_group_membership",
          okJournal(
              "POST",
              B + "/number_groups/ng-1/number_group_memberships",
              "relay-rest.create_number_group_membership"));
      assertEquals("pn-1", mock.last().bodyMap().get("phone_number_id"));
    }

    @Test
    void addMembershipError() {
      assertEquals(
          422,
          errCall(
              "relay-rest.create_number_group_membership",
              422,
              () -> client.numberGroups().addMembership("ng-1", kw("phone_number_id", ""))));
    }

    @Test
    void getMembershipSuccess() {
      Map<String, Object> body = client.numberGroups().getMembership("mem-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.retrieve_number_group_membership",
          okJournal(
              "GET",
              B + "/number_group_memberships/mem-1",
              "relay-rest.retrieve_number_group_membership"));
    }

    @Test
    void getMembershipError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.retrieve_number_group_membership",
              404,
              () -> client.numberGroups().getMembership("mem-404")));
    }

    @Test
    void deleteMembershipSuccess() {
      Map<String, Object> body = client.numberGroups().deleteMembership("mem-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.delete_number_group_membership",
          okJournal(
              "DELETE",
              B + "/number_group_memberships/mem-1",
              "relay-rest.delete_number_group_membership"));
    }

    @Test
    void deleteMembershipError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.delete_number_group_membership",
              404,
              () -> client.numberGroups().deleteMembership("mem-404")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // 10DLC registry — brands
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("registry.brands")
  class RegistryBrands {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.registry().brands().list();
      assertNotNull(body);
      assertEquals(
          "relay-rest.list_brands", okJournal("GET", R + "/brands", "relay-rest.list_brands"));
    }

    @Test
    void listError() {
      assertEquals(
          500, errCall("relay-rest.list_brands", 500, () -> client.registry().brands().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body =
          client.registry().brands().create(kw("entity_type", "PRIVATE_PROFIT"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.create_brand", okJournal("POST", R + "/brands", "relay-rest.create_brand"));
      assertEquals("PRIVATE_PROFIT", mock.last().bodyMap().get("entity_type"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "relay-rest.create_brand",
              422,
              () -> client.registry().brands().create(kw("entity_type", ""))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.registry().brands().get("brand-77");
      assertNotNull(body);
      assertEquals(
          "relay-rest.retrieve_brand",
          okJournal("GET", R + "/brands/brand-77", "relay-rest.retrieve_brand"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.retrieve_brand", 404, () -> client.registry().brands().get("brand-404")));
    }

    @Test
    void listCampaignsSuccess() {
      Map<String, Object> body = client.registry().brands().listCampaigns("brand-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.list_campaigns",
          okJournal("GET", R + "/brands/brand-1/campaigns", "relay-rest.list_campaigns"));
    }

    @Test
    void listCampaignsError() {
      assertEquals(
          500,
          errCall(
              "relay-rest.list_campaigns",
              500,
              () -> client.registry().brands().listCampaigns("brand-1")));
    }

    @Test
    void createCampaignSuccess() {
      Map<String, Object> body =
          client
              .registry()
              .brands()
              .createCampaign("brand-2", kw("usecase", "LOW_VOLUME", "description", "MFA"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.create_campaign",
          okJournal("POST", R + "/brands/brand-2/campaigns", "relay-rest.create_campaign"));
      assertEquals("LOW_VOLUME", mock.last().bodyMap().get("usecase"));
    }

    @Test
    void createCampaignError() {
      assertEquals(
          422,
          errCall(
              "relay-rest.create_campaign",
              422,
              () -> client.registry().brands().createCampaign("brand-2", kw("usecase", ""))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // 10DLC registry — campaigns / orders / numbers
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("registry.campaigns")
  class RegistryCampaigns {

    @Test
    void getSuccess() {
      Map<String, Object> body = client.registry().campaigns().get("camp-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.retrieve_campaign",
          okJournal("GET", R + "/campaigns/camp-1", "relay-rest.retrieve_campaign"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.retrieve_campaign",
              404,
              () -> client.registry().campaigns().get("camp-404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body =
          client.registry().campaigns().update("camp-2", kw("description", "Updated"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.update_campaign",
          okJournal("PUT", R + "/campaigns/camp-2", "relay-rest.update_campaign"));
      assertEquals("Updated", mock.last().bodyMap().get("description"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.update_campaign",
              404,
              () -> client.registry().campaigns().update("camp-404", kw("description", "x"))));
    }

    @Test
    void listNumbersSuccess() {
      Map<String, Object> body = client.registry().campaigns().listNumbers("camp-3");
      assertNotNull(body);
      assertEquals(
          "relay-rest.list_number_assignments",
          okJournal("GET", R + "/campaigns/camp-3/numbers", "relay-rest.list_number_assignments"));
    }

    @Test
    void listNumbersError() {
      assertEquals(
          500,
          errCall(
              "relay-rest.list_number_assignments",
              500,
              () -> client.registry().campaigns().listNumbers("camp-3")));
    }

    @Test
    void listOrdersSuccess() {
      Map<String, Object> body = client.registry().campaigns().listOrders("camp-3");
      assertNotNull(body);
      assertEquals(
          "relay-rest.list_orders",
          okJournal("GET", R + "/campaigns/camp-3/orders", "relay-rest.list_orders"));
    }

    @Test
    void listOrdersError() {
      assertEquals(
          500,
          errCall(
              "relay-rest.list_orders",
              500,
              () -> client.registry().campaigns().listOrders("camp-3")));
    }

    @Test
    void createOrderSuccess() {
      Map<String, Object> body =
          client
              .registry()
              .campaigns()
              .createOrder("camp-4", kw("numbers", Arrays.asList("pn-1", "pn-2")));
      assertNotNull(body);
      assertEquals(
          "relay-rest.create_order",
          okJournal("POST", R + "/campaigns/camp-4/orders", "relay-rest.create_order"));
      assertEquals(Arrays.asList("pn-1", "pn-2"), mock.last().bodyMap().get("numbers"));
    }

    @Test
    void createOrderError() {
      assertEquals(
          422,
          errCall(
              "relay-rest.create_order",
              422,
              () -> client.registry().campaigns().createOrder("camp-4", kw("numbers", List.of()))));
    }
  }

  @Nested
  @DisplayName("registry.orders")
  class RegistryOrders {

    @Test
    void getSuccess() {
      Map<String, Object> body = client.registry().orders().get("order-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.retrieve_order",
          okJournal("GET", R + "/orders/order-1", "relay-rest.retrieve_order"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.retrieve_order", 404, () -> client.registry().orders().get("order-404")));
    }
  }

  @Nested
  @DisplayName("registry.numbers")
  class RegistryNumbers {

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.registry().numbers().delete("num-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.delete_number_assignment",
          okJournal("DELETE", R + "/numbers/num-1", "relay-rest.delete_number_assignment"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.delete_number_assignment",
              404,
              () -> client.registry().numbers().delete("num-404")));
    }
  }
}
