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
 * Full success+error REST coverage for the {@code compatibility.*} canonical spec group (the
 * Twilio-compatible LAML 2010-04-01 Accounts API).
 *
 * <p>For every coverable compatibility route this exercises BOTH a success (2xx) call — asserting
 * the response body, the journalled {@code method}/{@code path}, and {@code matched_route} == the
 * canonical endpoint id — AND an error path: an armed {@link MockTest.Harness#scenarioSet} override
 * (404/422/500) that must surface as a {@link RestError} carrying the right status code, with the
 * journal recording the same {@code matched_route} and {@code response_status}.
 *
 * <p>Mirrors the idiom of {@code FabricCoverageMockTest} / the existing {@code Compat*MockTest}
 * files. LAML paths embed the AccountSid, which the SDK resolves from the authenticated project;
 * tests read it from {@link MockTest.Bound#project} (never hard-coded) and assert the concrete
 * {@code /api/laml/2010-04-01/Accounts/<project>/...} path.
 *
 * <p>Accepted gap (NOT tested here — no SDK surface): {@code
 * compatibility.list_available_phone_number_resources_by_country} (bare {@code
 * /AvailablePhoneNumbers/{IsoCountry}}). The Java SDK exposes {@code listAvailableCountries()},
 * {@code searchLocal()} and {@code searchTollFree()} but has no method for the bare per-country
 * listing — same gap Python carries.
 */
class CompatCoverageMockTest {

  /** Top-level Accounts collection — no AccountSid segment. */
  private static final String ACCOUNTS = "/api/laml/2010-04-01/Accounts";

  private RestClient client;
  private MockTest.Harness mock;

  /** Per-account base: {@code /api/laml/2010-04-01/Accounts/<project>}. */
  private String acctBase;

  @BeforeEach
  void setUp() {
    MockTest.Bound bound = MockTest.newClient();
    this.client = bound.client;
    this.mock = bound.harness;
    this.acctBase = ACCOUNTS + "/" + bound.project;
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
  // Accounts / sub-projects
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("compat accounts")
  class Accounts {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.compat().accounts().list();
      assertNotNull(body);
      assertEquals(
          "compatibility.list_accounts", okJournal("GET", ACCOUNTS, "compatibility.list_accounts"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall("compatibility.list_accounts", 500, () -> client.compat().accounts().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.compat().accounts().create(kw("FriendlyName", "Sub-A"));
      assertNotNull(body);
      assertEquals(
          "compatibility.create_subprojects",
          okJournal("POST", ACCOUNTS, "compatibility.create_subprojects"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "compatibility.create_subprojects",
              422,
              () -> client.compat().accounts().create(kw("FriendlyName", "bad"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.compat().accounts().get("AC123");
      assertNotNull(body);
      assertEquals(
          "compatibility.get_account",
          okJournal("GET", ACCOUNTS + "/AC123", "compatibility.get_account"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall("compatibility.get_account", 404, () -> client.compat().accounts().get("AC404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body =
          client.compat().accounts().update("AC123", kw("FriendlyName", "Renamed"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_account",
          okJournal("POST", ACCOUNTS + "/AC123", "compatibility.update_account"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_account",
              404,
              () -> client.compat().accounts().update("AC404", kw("FriendlyName", "x"))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Applications
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("compat applications")
  class Applications {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.compat().applications().list();
      assertNotNull(body);
      assertEquals(
          "compatibility.list_applications",
          okJournal("GET", acctBase + "/Applications", "compatibility.list_applications"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall(
              "compatibility.list_applications", 500, () -> client.compat().applications().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.compat().applications().create(kw("FriendlyName", "App"));
      assertNotNull(body);
      assertEquals(
          "compatibility.create_application",
          okJournal("POST", acctBase + "/Applications", "compatibility.create_application"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "compatibility.create_application",
              422,
              () -> client.compat().applications().create(kw("FriendlyName", "x"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.compat().applications().get("AP1");
      assertNotNull(body);
      assertEquals(
          "compatibility.get_application",
          okJournal("GET", acctBase + "/Applications/AP1", "compatibility.get_application"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "compatibility.get_application",
              404,
              () -> client.compat().applications().get("AP404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body =
          client.compat().applications().update("AP1", kw("FriendlyName", "New"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_application",
          okJournal("POST", acctBase + "/Applications/AP1", "compatibility.update_application"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_application",
              404,
              () -> client.compat().applications().update("AP404", kw("FriendlyName", "x"))));
    }

    @Test
    void deleteSuccess() {
      client.compat().applications().delete("AP1");
      assertEquals(
          "compatibility.delete_application",
          okJournal("DELETE", acctBase + "/Applications/AP1", "compatibility.delete_application"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "compatibility.delete_application",
              404,
              () -> client.compat().applications().delete("AP404")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Available phone numbers
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("compat available phone numbers")
  class AvailablePhoneNumbers {

    @Test
    void listCountriesSuccess() {
      Map<String, Object> body = client.compat().phoneNumbers().listAvailableCountries();
      assertNotNull(body);
      assertEquals(
          "compatibility.list_available_phone_number_resources",
          okJournal(
              "GET",
              acctBase + "/AvailablePhoneNumbers",
              "compatibility.list_available_phone_number_resources"));
    }

    @Test
    void listCountriesError() {
      assertEquals(
          500,
          errCall(
              "compatibility.list_available_phone_number_resources",
              500,
              () -> client.compat().phoneNumbers().listAvailableCountries()));
    }

    @Test
    void searchLocalSuccess() {
      Map<String, Object> body = client.compat().phoneNumbers().searchLocal("US");
      assertNotNull(body);
      assertEquals(
          "compatibility.search_local_available_phone_numbers",
          okJournal(
              "GET",
              acctBase + "/AvailablePhoneNumbers/US/Local",
              "compatibility.search_local_available_phone_numbers"));
    }

    @Test
    void searchLocalError() {
      assertEquals(
          404,
          errCall(
              "compatibility.search_local_available_phone_numbers",
              404,
              () -> client.compat().phoneNumbers().searchLocal("ZZ")));
    }

    @Test
    void searchTollFreeSuccess() {
      Map<String, Object> body = client.compat().phoneNumbers().searchTollFree("US");
      assertNotNull(body);
      assertEquals(
          "compatibility.search_toll_free_available_phone_numbers",
          okJournal(
              "GET",
              acctBase + "/AvailablePhoneNumbers/US/TollFree",
              "compatibility.search_toll_free_available_phone_numbers"));
    }

    @Test
    void searchTollFreeError() {
      assertEquals(
          404,
          errCall(
              "compatibility.search_toll_free_available_phone_numbers",
              404,
              () -> client.compat().phoneNumbers().searchTollFree("ZZ")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Calls (+ recordings / streams sub-resources)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("compat calls")
  class Calls {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.compat().calls().list();
      assertNotNull(body);
      assertEquals(
          "compatibility.list_all_calls",
          okJournal("GET", acctBase + "/Calls", "compatibility.list_all_calls"));
    }

    @Test
    void listError() {
      assertEquals(
          500, errCall("compatibility.list_all_calls", 500, () -> client.compat().calls().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.compat().calls().create(kw("To", "+15551112222"));
      assertNotNull(body);
      assertEquals(
          "compatibility.create_a_call",
          okJournal("POST", acctBase + "/Calls", "compatibility.create_a_call"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "compatibility.create_a_call",
              422,
              () -> client.compat().calls().create(kw("To", "x"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.compat().calls().get("CA1");
      assertNotNull(body);
      assertEquals(
          "compatibility.retrieve_a_call",
          okJournal("GET", acctBase + "/Calls/CA1", "compatibility.retrieve_a_call"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "compatibility.retrieve_a_call", 404, () -> client.compat().calls().get("CA404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.compat().calls().update("CA1", kw("Status", "completed"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_a_call",
          okJournal("POST", acctBase + "/Calls/CA1", "compatibility.update_a_call"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_a_call",
              404,
              () -> client.compat().calls().update("CA404", kw("Status", "x"))));
    }

    @Test
    void deleteSuccess() {
      client.compat().calls().delete("CA1");
      assertEquals(
          "compatibility.delete_a_call",
          okJournal("DELETE", acctBase + "/Calls/CA1", "compatibility.delete_a_call"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "compatibility.delete_a_call", 404, () -> client.compat().calls().delete("CA404")));
    }

    @Test
    void createRecordingSuccess() {
      Map<String, Object> body =
          client.compat().calls().startRecording("CA1", kw("RecordingChannels", "dual"));
      assertNotNull(body);
      assertEquals(
          "compatibility.create_recording",
          okJournal("POST", acctBase + "/Calls/CA1/Recordings", "compatibility.create_recording"));
    }

    @Test
    void createRecordingError() {
      assertEquals(
          422,
          errCall(
              "compatibility.create_recording",
              422,
              () -> client.compat().calls().startRecording("CA1", kw("x", "y"))));
    }

    @Test
    void updateRecordingSuccess() {
      Map<String, Object> body =
          client.compat().calls().updateRecording("CA1", "RE1", kw("Status", "stopped"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_recording",
          okJournal(
              "POST", acctBase + "/Calls/CA1/Recordings/RE1", "compatibility.update_recording"));
    }

    @Test
    void updateRecordingError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_recording",
              404,
              () -> client.compat().calls().updateRecording("CA1", "RE404", kw("Status", "x"))));
    }

    @Test
    void createStreamSuccess() {
      Map<String, Object> body =
          client.compat().calls().startStream("CA1", kw("Url", "wss://a.b/s"));
      assertNotNull(body);
      assertEquals(
          "compatibility.create_stream",
          okJournal("POST", acctBase + "/Calls/CA1/Streams", "compatibility.create_stream"));
    }

    @Test
    void createStreamError() {
      assertEquals(
          422,
          errCall(
              "compatibility.create_stream",
              422,
              () -> client.compat().calls().startStream("CA1", kw("Url", "x"))));
    }

    @Test
    void updateStreamSuccess() {
      Map<String, Object> body =
          client.compat().calls().stopStream("CA1", "MZ1", kw("Status", "stopped"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_stream",
          okJournal("POST", acctBase + "/Calls/CA1/Streams/MZ1", "compatibility.update_stream"));
    }

    @Test
    void updateStreamError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_stream",
              404,
              () -> client.compat().calls().stopStream("CA1", "MZ404", kw("Status", "x"))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Conferences (+ participants / recordings / streams)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("compat conferences")
  class Conferences {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.compat().conferences().list();
      assertNotNull(body);
      assertEquals(
          "compatibility.list_all_conferences",
          okJournal("GET", acctBase + "/Conferences", "compatibility.list_all_conferences"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall(
              "compatibility.list_all_conferences",
              500,
              () -> client.compat().conferences().list()));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.compat().conferences().get("CF1");
      assertNotNull(body);
      assertEquals(
          "compatibility.retrieve_conference",
          okJournal("GET", acctBase + "/Conferences/CF1", "compatibility.retrieve_conference"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "compatibility.retrieve_conference",
              404,
              () -> client.compat().conferences().get("CF404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body =
          client.compat().conferences().update("CF1", kw("Status", "completed"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_conference",
          okJournal("POST", acctBase + "/Conferences/CF1", "compatibility.update_conference"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_conference",
              404,
              () -> client.compat().conferences().update("CF404", kw("Status", "x"))));
    }

    @Test
    void listParticipantsSuccess() {
      Map<String, Object> body = client.compat().conferences().listParticipants("CF1");
      assertNotNull(body);
      assertEquals(
          "compatibility.list_all_participants",
          okJournal(
              "GET",
              acctBase + "/Conferences/CF1/Participants",
              "compatibility.list_all_participants"));
    }

    @Test
    void listParticipantsError() {
      assertEquals(
          500,
          errCall(
              "compatibility.list_all_participants",
              500,
              () -> client.compat().conferences().listParticipants("CF1")));
    }

    @Test
    void getParticipantSuccess() {
      Map<String, Object> body = client.compat().conferences().getParticipant("CF1", "CA1");
      assertNotNull(body);
      assertEquals(
          "compatibility.retrieve_participant",
          okJournal(
              "GET",
              acctBase + "/Conferences/CF1/Participants/CA1",
              "compatibility.retrieve_participant"));
    }

    @Test
    void getParticipantError() {
      assertEquals(
          404,
          errCall(
              "compatibility.retrieve_participant",
              404,
              () -> client.compat().conferences().getParticipant("CF1", "CA404")));
    }

    @Test
    void updateParticipantSuccess() {
      Map<String, Object> body =
          client.compat().conferences().updateParticipant("CF1", "CA1", kw("Muted", "true"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_participant",
          okJournal(
              "POST",
              acctBase + "/Conferences/CF1/Participants/CA1",
              "compatibility.update_participant"));
    }

    @Test
    void updateParticipantError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_participant",
              404,
              () ->
                  client
                      .compat()
                      .conferences()
                      .updateParticipant("CF1", "CA404", kw("Muted", "x"))));
    }

    @Test
    void deleteParticipantSuccess() {
      client.compat().conferences().removeParticipant("CF1", "CA1");
      assertEquals(
          "compatibility.delete_participant",
          okJournal(
              "DELETE",
              acctBase + "/Conferences/CF1/Participants/CA1",
              "compatibility.delete_participant"));
    }

    @Test
    void deleteParticipantError() {
      assertEquals(
          404,
          errCall(
              "compatibility.delete_participant",
              404,
              () -> client.compat().conferences().removeParticipant("CF1", "CA404")));
    }

    @Test
    void listRecordingsSuccess() {
      Map<String, Object> body = client.compat().conferences().listRecordings("CF1");
      assertNotNull(body);
      assertEquals(
          "compatibility.list_conference_recordings",
          okJournal(
              "GET",
              acctBase + "/Conferences/CF1/Recordings",
              "compatibility.list_conference_recordings"));
    }

    @Test
    void listRecordingsError() {
      assertEquals(
          500,
          errCall(
              "compatibility.list_conference_recordings",
              500,
              () -> client.compat().conferences().listRecordings("CF1")));
    }

    @Test
    void getRecordingSuccess() {
      Map<String, Object> body = client.compat().conferences().getRecording("CF1", "RE1");
      assertNotNull(body);
      assertEquals(
          "compatibility.get_conference_recording",
          okJournal(
              "GET",
              acctBase + "/Conferences/CF1/Recordings/RE1",
              "compatibility.get_conference_recording"));
    }

    @Test
    void getRecordingError() {
      assertEquals(
          404,
          errCall(
              "compatibility.get_conference_recording",
              404,
              () -> client.compat().conferences().getRecording("CF1", "RE404")));
    }

    @Test
    void updateRecordingSuccess() {
      Map<String, Object> body =
          client.compat().conferences().updateRecording("CF1", "RE1", kw("Status", "paused"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_conference_recording",
          okJournal(
              "POST",
              acctBase + "/Conferences/CF1/Recordings/RE1",
              "compatibility.update_conference_recording"));
    }

    @Test
    void updateRecordingError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_conference_recording",
              404,
              () ->
                  client
                      .compat()
                      .conferences()
                      .updateRecording("CF1", "RE404", kw("Status", "x"))));
    }

    @Test
    void deleteRecordingSuccess() {
      client.compat().conferences().deleteRecording("CF1", "RE1");
      assertEquals(
          "compatibility.delete_conference_recording",
          okJournal(
              "DELETE",
              acctBase + "/Conferences/CF1/Recordings/RE1",
              "compatibility.delete_conference_recording"));
    }

    @Test
    void deleteRecordingError() {
      assertEquals(
          404,
          errCall(
              "compatibility.delete_conference_recording",
              404,
              () -> client.compat().conferences().deleteRecording("CF1", "RE404")));
    }

    @Test
    void createStreamSuccess() {
      Map<String, Object> body =
          client.compat().conferences().startStream("CF1", kw("Url", "wss://a.b/s"));
      assertNotNull(body);
      assertEquals(
          "compatibility.create_conference_stream",
          okJournal(
              "POST",
              acctBase + "/Conferences/CF1/Streams",
              "compatibility.create_conference_stream"));
    }

    @Test
    void createStreamError() {
      assertEquals(
          422,
          errCall(
              "compatibility.create_conference_stream",
              422,
              () -> client.compat().conferences().startStream("CF1", kw("Url", "x"))));
    }

    @Test
    void updateStreamSuccess() {
      Map<String, Object> body =
          client.compat().conferences().stopStream("CF1", "MZ1", kw("Status", "stopped"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_conference_stream",
          okJournal(
              "POST",
              acctBase + "/Conferences/CF1/Streams/MZ1",
              "compatibility.update_conference_stream"));
    }

    @Test
    void updateStreamError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_conference_stream",
              404,
              () -> client.compat().conferences().stopStream("CF1", "MZ404", kw("Status", "x"))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Faxes (+ media)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("compat faxes")
  class Faxes {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.compat().faxes().list();
      assertNotNull(body);
      assertEquals(
          "compatibility.list_all_faxes",
          okJournal("GET", acctBase + "/Faxes", "compatibility.list_all_faxes"));
    }

    @Test
    void listError() {
      assertEquals(
          500, errCall("compatibility.list_all_faxes", 500, () -> client.compat().faxes().list()));
    }

    @Test
    void sendSuccess() {
      Map<String, Object> body =
          client.compat().faxes().create(kw("To", "+15551112222", "MediaUrl", "https://a.b/f.pdf"));
      assertNotNull(body);
      assertEquals(
          "compatibility.send_fax",
          okJournal("POST", acctBase + "/Faxes", "compatibility.send_fax"));
    }

    @Test
    void sendError() {
      assertEquals(
          422,
          errCall(
              "compatibility.send_fax", 422, () -> client.compat().faxes().create(kw("To", "x"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.compat().faxes().get("FX1");
      assertNotNull(body);
      assertEquals(
          "compatibility.retrieve_fax",
          okJournal("GET", acctBase + "/Faxes/FX1", "compatibility.retrieve_fax"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall("compatibility.retrieve_fax", 404, () -> client.compat().faxes().get("FX404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.compat().faxes().update("FX1", kw("Status", "canceled"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_fax",
          okJournal("POST", acctBase + "/Faxes/FX1", "compatibility.update_fax"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_fax",
              404,
              () -> client.compat().faxes().update("FX404", kw("Status", "x"))));
    }

    @Test
    void deleteSuccess() {
      client.compat().faxes().delete("FX1");
      assertEquals(
          "compatibility.delete_fax",
          okJournal("DELETE", acctBase + "/Faxes/FX1", "compatibility.delete_fax"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall("compatibility.delete_fax", 404, () -> client.compat().faxes().delete("FX404")));
    }

    @Test
    void listMediaSuccess() {
      Map<String, Object> body = client.compat().faxes().listMedia("FX1");
      assertNotNull(body);
      assertEquals(
          "compatibility.list_all_fax_media",
          okJournal("GET", acctBase + "/Faxes/FX1/Media", "compatibility.list_all_fax_media"));
    }

    @Test
    void listMediaError() {
      assertEquals(
          500,
          errCall(
              "compatibility.list_all_fax_media",
              500,
              () -> client.compat().faxes().listMedia("FX1")));
    }

    @Test
    void getMediaSuccess() {
      Map<String, Object> body = client.compat().faxes().getMedia("FX1", "ME1");
      assertNotNull(body);
      assertEquals(
          "compatibility.retrieve_medias",
          okJournal("GET", acctBase + "/Faxes/FX1/Media/ME1", "compatibility.retrieve_medias"));
    }

    @Test
    void getMediaError() {
      assertEquals(
          404,
          errCall(
              "compatibility.retrieve_medias",
              404,
              () -> client.compat().faxes().getMedia("FX1", "ME404")));
    }

    @Test
    void deleteMediaSuccess() {
      client.compat().faxes().deleteMedia("FX1", "ME1");
      assertEquals(
          "compatibility.delete_fax_media",
          okJournal("DELETE", acctBase + "/Faxes/FX1/Media/ME1", "compatibility.delete_fax_media"));
    }

    @Test
    void deleteMediaError() {
      assertEquals(
          404,
          errCall(
              "compatibility.delete_fax_media",
              404,
              () -> client.compat().faxes().deleteMedia("FX1", "ME404")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Incoming + imported phone numbers
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("compat incoming phone numbers")
  class IncomingPhoneNumbers {

    @Test
    void importSuccess() {
      Map<String, Object> body =
          client.compat().phoneNumbers().importNumber(kw("PhoneNumber", "+15551112222"));
      assertNotNull(body);
      assertEquals(
          "compatibility.create_imported_phone_number",
          okJournal(
              "POST",
              acctBase + "/ImportedPhoneNumbers",
              "compatibility.create_imported_phone_number"));
    }

    @Test
    void importError() {
      assertEquals(
          422,
          errCall(
              "compatibility.create_imported_phone_number",
              422,
              () -> client.compat().phoneNumbers().importNumber(kw("PhoneNumber", "x"))));
    }

    @Test
    void listSuccess() {
      Map<String, Object> body = client.compat().phoneNumbers().list();
      assertNotNull(body);
      assertEquals(
          "compatibility.list_incoming_phone_numbers",
          okJournal(
              "GET",
              acctBase + "/IncomingPhoneNumbers",
              "compatibility.list_incoming_phone_numbers"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall(
              "compatibility.list_incoming_phone_numbers",
              500,
              () -> client.compat().phoneNumbers().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body =
          client.compat().phoneNumbers().purchase(kw("PhoneNumber", "+15551112222"));
      assertNotNull(body);
      assertEquals(
          "compatibility.create_incoming_phone_number",
          okJournal(
              "POST",
              acctBase + "/IncomingPhoneNumbers",
              "compatibility.create_incoming_phone_number"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "compatibility.create_incoming_phone_number",
              422,
              () -> client.compat().phoneNumbers().purchase(kw("PhoneNumber", "x"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.compat().phoneNumbers().get("PN1");
      assertNotNull(body);
      assertEquals(
          "compatibility.retrieve_incoming_phone_number",
          okJournal(
              "GET",
              acctBase + "/IncomingPhoneNumbers/PN1",
              "compatibility.retrieve_incoming_phone_number"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "compatibility.retrieve_incoming_phone_number",
              404,
              () -> client.compat().phoneNumbers().get("PN404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body =
          client.compat().phoneNumbers().update("PN1", kw("FriendlyName", "New"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_incoming_phone_number",
          okJournal(
              "POST",
              acctBase + "/IncomingPhoneNumbers/PN1",
              "compatibility.update_incoming_phone_number"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_incoming_phone_number",
              404,
              () -> client.compat().phoneNumbers().update("PN404", kw("FriendlyName", "x"))));
    }

    @Test
    void deleteSuccess() {
      client.compat().phoneNumbers().delete("PN1");
      assertEquals(
          "compatibility.delete_incoming_phone_number",
          okJournal(
              "DELETE",
              acctBase + "/IncomingPhoneNumbers/PN1",
              "compatibility.delete_incoming_phone_number"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "compatibility.delete_incoming_phone_number",
              404,
              () -> client.compat().phoneNumbers().delete("PN404")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // cXML / LaML scripts (LamlBins)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("compat cxml scripts (LamlBins)")
  class CxmlScripts {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.compat().lamlBins().list();
      assertNotNull(body);
      assertEquals(
          "compatibility.list_cxml_scripts",
          okJournal("GET", acctBase + "/LamlBins", "compatibility.list_cxml_scripts"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall("compatibility.list_cxml_scripts", 500, () -> client.compat().lamlBins().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body =
          client.compat().lamlBins().create(kw("Name", "bin", "Contents", "<Response/>"));
      assertNotNull(body);
      assertEquals(
          "compatibility.create_cxml_script",
          okJournal("POST", acctBase + "/LamlBins", "compatibility.create_cxml_script"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "compatibility.create_cxml_script",
              422,
              () -> client.compat().lamlBins().create(kw("Name", "x"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.compat().lamlBins().get("LB1");
      assertNotNull(body);
      assertEquals(
          "compatibility.retrieve_cxml_script",
          okJournal("GET", acctBase + "/LamlBins/LB1", "compatibility.retrieve_cxml_script"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "compatibility.retrieve_cxml_script",
              404,
              () -> client.compat().lamlBins().get("LB404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.compat().lamlBins().update("LB1", kw("Name", "renamed"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_cxml_script",
          okJournal("POST", acctBase + "/LamlBins/LB1", "compatibility.update_cxml_script"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_cxml_script",
              404,
              () -> client.compat().lamlBins().update("LB404", kw("Name", "x"))));
    }

    @Test
    void deleteSuccess() {
      client.compat().lamlBins().delete("LB1");
      assertEquals(
          "compatibility.delete_cxml_script",
          okJournal("DELETE", acctBase + "/LamlBins/LB1", "compatibility.delete_cxml_script"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "compatibility.delete_cxml_script",
              404,
              () -> client.compat().lamlBins().delete("LB404")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Messages (+ media)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("compat messages")
  class Messages {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.compat().messages().list();
      assertNotNull(body);
      assertEquals(
          "compatibility.list_messages",
          okJournal("GET", acctBase + "/Messages", "compatibility.list_messages"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall("compatibility.list_messages", 500, () -> client.compat().messages().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body =
          client.compat().messages().create(kw("To", "+15551112222", "Body", "hi"));
      assertNotNull(body);
      assertEquals(
          "compatibility.create_message",
          okJournal("POST", acctBase + "/Messages", "compatibility.create_message"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "compatibility.create_message",
              422,
              () -> client.compat().messages().create(kw("To", "x"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.compat().messages().get("SM1");
      assertNotNull(body);
      assertEquals(
          "compatibility.retrieve_message",
          okJournal("GET", acctBase + "/Messages/SM1", "compatibility.retrieve_message"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "compatibility.retrieve_message",
              404,
              () -> client.compat().messages().get("SM404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.compat().messages().update("SM1", kw("Body", "edit"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_message",
          okJournal("POST", acctBase + "/Messages/SM1", "compatibility.update_message"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_message",
              404,
              () -> client.compat().messages().update("SM404", kw("Body", "x"))));
    }

    @Test
    void deleteSuccess() {
      client.compat().messages().delete("SM1");
      assertEquals(
          "compatibility.delete_message",
          okJournal("DELETE", acctBase + "/Messages/SM1", "compatibility.delete_message"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "compatibility.delete_message",
              404,
              () -> client.compat().messages().delete("SM404")));
    }

    @Test
    void listMediaSuccess() {
      Map<String, Object> body = client.compat().messages().listMedia("SM1");
      assertNotNull(body);
      assertEquals(
          "compatibility.list_media",
          okJournal("GET", acctBase + "/Messages/SM1/Media", "compatibility.list_media"));
    }

    @Test
    void listMediaError() {
      assertEquals(
          500,
          errCall(
              "compatibility.list_media", 500, () -> client.compat().messages().listMedia("SM1")));
    }

    @Test
    void getMediaSuccess() {
      Map<String, Object> body = client.compat().messages().getMedia("SM1", "ME1");
      assertNotNull(body);
      assertEquals(
          "compatibility.retrieve_media",
          okJournal("GET", acctBase + "/Messages/SM1/Media/ME1", "compatibility.retrieve_media"));
    }

    @Test
    void getMediaError() {
      assertEquals(
          404,
          errCall(
              "compatibility.retrieve_media",
              404,
              () -> client.compat().messages().getMedia("SM1", "ME404")));
    }

    @Test
    void deleteMediaSuccess() {
      client.compat().messages().deleteMedia("SM1", "ME1");
      assertEquals(
          "compatibility.delete_message_media",
          okJournal(
              "DELETE",
              acctBase + "/Messages/SM1/Media/ME1",
              "compatibility.delete_message_media"));
    }

    @Test
    void deleteMediaError() {
      assertEquals(
          404,
          errCall(
              "compatibility.delete_message_media",
              404,
              () -> client.compat().messages().deleteMedia("SM1", "ME404")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Queues (+ members)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("compat queues")
  class Queues {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.compat().queues().list();
      assertNotNull(body);
      assertEquals(
          "compatibility.list_queues",
          okJournal("GET", acctBase + "/Queues", "compatibility.list_queues"));
    }

    @Test
    void listError() {
      assertEquals(
          500, errCall("compatibility.list_queues", 500, () -> client.compat().queues().list()));
    }

    @Test
    void createSuccess() {
      Map<String, Object> body = client.compat().queues().create(kw("FriendlyName", "Q"));
      assertNotNull(body);
      assertEquals(
          "compatibility.create_queue",
          okJournal("POST", acctBase + "/Queues", "compatibility.create_queue"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "compatibility.create_queue",
              422,
              () -> client.compat().queues().create(kw("FriendlyName", "x"))));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.compat().queues().get("QU1");
      assertNotNull(body);
      assertEquals(
          "compatibility.retrieve_queue",
          okJournal("GET", acctBase + "/Queues/QU1", "compatibility.retrieve_queue"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "compatibility.retrieve_queue", 404, () -> client.compat().queues().get("QU404")));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.compat().queues().update("QU1", kw("FriendlyName", "New"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_queue",
          okJournal("POST", acctBase + "/Queues/QU1", "compatibility.update_queue"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_queue",
              404,
              () -> client.compat().queues().update("QU404", kw("FriendlyName", "x"))));
    }

    @Test
    void deleteSuccess() {
      client.compat().queues().delete("QU1");
      assertEquals(
          "compatibility.delete_queue",
          okJournal("DELETE", acctBase + "/Queues/QU1", "compatibility.delete_queue"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "compatibility.delete_queue", 404, () -> client.compat().queues().delete("QU404")));
    }

    @Test
    void listMembersSuccess() {
      Map<String, Object> body = client.compat().queues().listMembers("QU1");
      assertNotNull(body);
      assertEquals(
          "compatibility.list_all_queue_members",
          okJournal(
              "GET", acctBase + "/Queues/QU1/Members", "compatibility.list_all_queue_members"));
    }

    @Test
    void listMembersError() {
      assertEquals(
          500,
          errCall(
              "compatibility.list_all_queue_members",
              500,
              () -> client.compat().queues().listMembers("QU1")));
    }

    @Test
    void getMemberSuccess() {
      Map<String, Object> body = client.compat().queues().getMember("QU1", "CA1");
      assertNotNull(body);
      assertEquals(
          "compatibility.retrieve_queue_member",
          okJournal(
              "GET", acctBase + "/Queues/QU1/Members/CA1", "compatibility.retrieve_queue_member"));
    }

    @Test
    void getMemberError() {
      assertEquals(
          404,
          errCall(
              "compatibility.retrieve_queue_member",
              404,
              () -> client.compat().queues().getMember("QU1", "CA404")));
    }

    @Test
    void updateMemberSuccess() {
      Map<String, Object> body =
          client.compat().queues().dequeueMember("QU1", "CA1", kw("Url", "https://a.b/x"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_queue_member",
          okJournal(
              "POST", acctBase + "/Queues/QU1/Members/CA1", "compatibility.update_queue_member"));
    }

    @Test
    void updateMemberError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_queue_member",
              404,
              () -> client.compat().queues().dequeueMember("QU1", "CA404", kw("Url", "x"))));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Recordings (top-level)
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("compat recordings")
  class Recordings {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.compat().recordings().list();
      assertNotNull(body);
      assertEquals(
          "compatibility.list_recordings",
          okJournal("GET", acctBase + "/Recordings", "compatibility.list_recordings"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall("compatibility.list_recordings", 500, () -> client.compat().recordings().list()));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.compat().recordings().get("RE1");
      assertNotNull(body);
      assertEquals(
          "compatibility.retrieve_recording",
          okJournal("GET", acctBase + "/Recordings/RE1", "compatibility.retrieve_recording"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "compatibility.retrieve_recording",
              404,
              () -> client.compat().recordings().get("RE404")));
    }

    @Test
    void deleteSuccess() {
      client.compat().recordings().delete("RE1");
      assertEquals(
          "compatibility.delete_recording",
          okJournal("DELETE", acctBase + "/Recordings/RE1", "compatibility.delete_recording"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "compatibility.delete_recording",
              404,
              () -> client.compat().recordings().delete("RE404")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Transcriptions
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("compat transcriptions")
  class Transcriptions {

    @Test
    void listSuccess() {
      Map<String, Object> body = client.compat().transcriptions().list();
      assertNotNull(body);
      assertEquals(
          "compatibility.list_transcriptions",
          okJournal("GET", acctBase + "/Transcriptions", "compatibility.list_transcriptions"));
    }

    @Test
    void listError() {
      assertEquals(
          500,
          errCall(
              "compatibility.list_transcriptions",
              500,
              () -> client.compat().transcriptions().list()));
    }

    @Test
    void getSuccess() {
      Map<String, Object> body = client.compat().transcriptions().get("TR1");
      assertNotNull(body);
      assertEquals(
          "compatibility.retrieve_transcription",
          okJournal(
              "GET", acctBase + "/Transcriptions/TR1", "compatibility.retrieve_transcription"));
    }

    @Test
    void getError() {
      assertEquals(
          404,
          errCall(
              "compatibility.retrieve_transcription",
              404,
              () -> client.compat().transcriptions().get("TR404")));
    }

    @Test
    void deleteSuccess() {
      client.compat().transcriptions().delete("TR1");
      assertEquals(
          "compatibility.delete_transcription",
          okJournal(
              "DELETE", acctBase + "/Transcriptions/TR1", "compatibility.delete_transcription"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "compatibility.delete_transcription",
              404,
              () -> client.compat().transcriptions().delete("TR404")));
    }
  }

  // ════════════════════════════════════════════════════════════════════
  // Tokens
  // ════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("compat tokens")
  class Tokens {

    @Test
    void createSuccess() {
      Map<String, Object> body = client.compat().tokens().create(kw("Ttl", "3600"));
      assertNotNull(body);
      assertEquals(
          "compatibility.create_token",
          okJournal("POST", acctBase + "/tokens", "compatibility.create_token"));
    }

    @Test
    void createError() {
      assertEquals(
          422,
          errCall(
              "compatibility.create_token",
              422,
              () -> client.compat().tokens().create(kw("Ttl", "x"))));
    }

    @Test
    void updateSuccess() {
      Map<String, Object> body = client.compat().tokens().update("TK1", kw("Ttl", "7200"));
      assertNotNull(body);
      assertEquals(
          "compatibility.update_token",
          okJournal("PATCH", acctBase + "/tokens/TK1", "compatibility.update_token"));
    }

    @Test
    void updateError() {
      assertEquals(
          404,
          errCall(
              "compatibility.update_token",
              404,
              () -> client.compat().tokens().update("TK404", kw("Ttl", "x"))));
    }

    @Test
    void deleteSuccess() {
      client.compat().tokens().delete("TK1");
      assertEquals(
          "compatibility.delete_token",
          okJournal("DELETE", acctBase + "/tokens/TK1", "compatibility.delete_token"));
    }

    @Test
    void deleteError() {
      assertEquals(
          404,
          errCall(
              "compatibility.delete_token", 404, () -> client.compat().tokens().delete("TK404")));
    }
  }
}
