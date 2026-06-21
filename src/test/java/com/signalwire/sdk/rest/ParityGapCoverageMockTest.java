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
 * Full success+error coverage for the nine java↔python REST parity gaps closed in this branch:
 *
 * <ul>
 *   <li>{@code fabric.assign_resource_phone_route} — {@code fabric().resources().assignPhoneRoute}
 *       (POST {@code /api/fabric/resources/{id}/phone_routes}).
 *   <li>{@code relay-rest.lookup_phone_number} — {@code numberLookup().lookup} now hits the
 *       canonical {@code /api/relay/rest/lookup/phone_number/{e164}} path (the {@code /relay/rest}
 *       base-path fix).
 *   <li>the seven {@code relay-rest.*_verified_caller_id*} routes — the new {@code
 *       verifiedCallers()} ({@link VerifiedCallersResource}) namespace at {@code
 *       /api/relay/rest/verified_caller_ids}: list/create/retrieve/update(PUT)/delete plus {@code
 *       redial_verification_call} (POST {@code .../{id}/verification}) and {@code
 *       validate_verification_code} (PUT {@code .../{id}/verification}).
 * </ul>
 *
 * <p>Mirrors the idiom of {@code RelayRestCoverageMockTest} / {@code FabricCoverageMockTest}: every
 * route gets a success call (asserting body + journalled method/path + {@code matched_route} ==
 * canonical endpoint id) and an error call (an armed {@link MockTest.Harness#scenarioSet} override
 * surfacing as a {@link RestError} with the right status code + journalled response status).
 */
class ParityGapCoverageMockTest {

  /** Canonical relay-rest base. */
  private static final String B = "/api/relay/rest";

  /** Canonical fabric resources base. */
  private static final String F = "/api/fabric/resources";

  /** Canonical verified-caller-ids base. */
  private static final String VC = B + "/verified_caller_ids";

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

  // ── DRY helpers (each RETURNS so the @Test body holds a real assertion) ──

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
  // A. fabric.assign_resource_phone_route
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("fabric resources.assignPhoneRoute")
  class AssignPhoneRoute {

    @Test
    void success() {
      Map<String, Object> body =
          client.fabric().resources().assignPhoneRoute("res-1", kw("phone_number_id", "pn-9"));
      assertNotNull(body);
      assertEquals(
          "fabric.assign_resource_phone_route",
          okJournal("POST", F + "/res-1/phone_routes", "fabric.assign_resource_phone_route"));
      assertEquals("pn-9", mock.last().bodyMap().get("phone_number_id"));
    }

    @Test
    void error() {
      assertEquals(
          422,
          errCall(
              "fabric.assign_resource_phone_route",
              422,
              () ->
                  client
                      .fabric()
                      .resources()
                      .assignPhoneRoute("res-1", kw("phone_number_id", ""))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // B. relay-rest.lookup_phone_number
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("numberLookup.lookup")
  class LookupPhoneNumber {

    @Test
    void success() {
      Map<String, Object> body = client.numberLookup().lookup("+15551234567");
      assertNotNull(body);
      assertEquals(
          "relay-rest.lookup_phone_number",
          okJournal(
              "GET", B + "/lookup/phone_number/+15551234567", "relay-rest.lookup_phone_number"));
    }

    @Test
    void error() {
      assertEquals(
          404,
          errCall(
              "relay-rest.lookup_phone_number",
              404,
              () -> client.numberLookup().lookup("+15550000000")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // C. verified_caller_ids — CRUD + verification flow (7 routes)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("verifiedCallers")
  class VerifiedCallers {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.verifiedCallers().list(qp("page_size", "10"));
      assertTrue(body.containsKey("data"));
      assertEquals(
          "relay-rest.list_verified_caller_ids",
          okJournal("GET", VC, "relay-rest.list_verified_caller_ids"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall(
              "relay-rest.list_verified_caller_ids",
              500,
              () -> client.verifiedCallers().list(qp("page_size", "1"))));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body =
          client.verifiedCallers().create(kw("number", "+15551230000", "name", "Front desk"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.create_verified_caller_id",
          okJournal("POST", VC, "relay-rest.create_verified_caller_id"));
      assertEquals("+15551230000", mock.last().bodyMap().get("number"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "relay-rest.create_verified_caller_id",
              422,
              () -> client.verifiedCallers().create(kw("number", ""))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.verifiedCallers().get("vc-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.retrieve_verified_caller_id",
          okJournal("GET", VC + "/vc-1", "relay-rest.retrieve_verified_caller_id"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.retrieve_verified_caller_id",
              404,
              () -> client.verifiedCallers().get("vc-404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.verifiedCallers().update("vc-1", kw("name", "Renamed"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.update_verified_caller_id",
          okJournal("PUT", VC + "/vc-1", "relay-rest.update_verified_caller_id"));
      assertEquals("Renamed", mock.last().bodyMap().get("name"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.update_verified_caller_id",
              404,
              () -> client.verifiedCallers().update("vc-404", kw("name", "x"))));
    }

    @Test
    void deleteSuccess() {
      Map<String, Object> body = client.verifiedCallers().delete("vc-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.delete_verified_caller_id",
          okJournal("DELETE", VC + "/vc-1", "relay-rest.delete_verified_caller_id"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.delete_verified_caller_id",
              404,
              () -> client.verifiedCallers().delete("vc-404")));
    }

    @Test
    void redialVerificationSuccess() {
      Map<String, Object> body = client.verifiedCallers().redialVerification("vc-1");
      assertNotNull(body);
      assertEquals(
          "relay-rest.redial_verification_call",
          okJournal("POST", VC + "/vc-1/verification", "relay-rest.redial_verification_call"));
    }

    @Test
    void redialVerificationError() {
      assertEquals(
          404,
          errCall(
              "relay-rest.redial_verification_call",
              404,
              () -> client.verifiedCallers().redialVerification("vc-404")));
    }

    @Test
    void submitVerificationSuccess() {
      Map<String, Object> body =
          client.verifiedCallers().submitVerification("vc-1", kw("verification_code", "123456"));
      assertNotNull(body);
      assertEquals(
          "relay-rest.validate_verification_code",
          okJournal("PUT", VC + "/vc-1/verification", "relay-rest.validate_verification_code"));
      assertEquals("123456", mock.last().bodyMap().get("verification_code"));
    }

    @Test
    void submitVerificationError() {
      assertEquals(
          422,
          errCall(
              "relay-rest.validate_verification_code",
              422,
              () ->
                  client
                      .verifiedCallers()
                      .submitVerification("vc-1", kw("verification_code", ""))));
    }
  }
}
