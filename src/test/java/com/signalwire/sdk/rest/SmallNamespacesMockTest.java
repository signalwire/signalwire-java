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

/**
 * Mock-backed unit tests translated from
 * signalwire-python/tests/unit/rest/test_small_namespaces_mock.py.
 *
 * <p>Covers gaps for namespaces that each had a handful of uncovered methods: addresses,
 * recordings, short_codes, imported_numbers, mfa, sip_profile, number_groups, project.tokens,
 * datasphere.documents.get_chunk, queues.
 */
class SmallNamespacesMockTest {

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

  // ── Addresses ────────────────────────────────────────────────────

  @Nested
  @DisplayName("Addresses")
  class Addresses {

    @Test
    void listSendsPageSizeQueryParam() {
      var body = client.addresses().list(qp("page_size", "10"));
      assertNotNull(body);
      assertNotNull(body.data);
      assertTrue(body.data instanceof List);
      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/relay/rest/addresses", j.path);
      assertNotNull(j.getMatchedRoute());
      assertEquals(List.of("10"), j.getQueryParams().get("page_size"));
    }

    @Test
    void create() {
      var body =
          client
              .addresses()
              .create(
                  com.signalwire.sdk.rest.namespaces.generated.Addresses.CreateRequest.builder()
                      .extras(
                          kw(
                              "address_type", "commercial",
                              "first_name", "Ada",
                              "last_name", "Lovelace",
                              "country", "US"))
                      .build());
      assertNotNull(body);
      assertNotNull(body.id);

      MockTest.JournalEntry j = mock.last();
      assertEquals("POST", j.method);
      assertEquals("/api/relay/rest/addresses", j.path);
      Map<String, Object> sent = j.bodyMap();
      assertNotNull(sent);
      assertEquals("commercial", sent.get("address_type"));
      assertEquals("Ada", sent.get("first_name"));
      assertEquals("US", sent.get("country"));
    }

    @Test
    void getById() {
      var body = client.addresses().get("addr-123", java.util.Map.of());
      assertNotNull(body);
      assertNotNull(body.id);

      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/relay/rest/addresses/addr-123", j.path);
      assertNotNull(j.getMatchedRoute());
    }

    @Test
    void deleteById() {
      Map<String, Object> body = client.addresses().delete("addr-123");
      assertNotNull(body);

      MockTest.JournalEntry j = mock.last();
      assertEquals("DELETE", j.method);
      assertEquals("/api/relay/rest/addresses/addr-123", j.path);
      int status = j.getResponseStatus();
      assertTrue(status == 200 || status == 202 || status == 204, "unexpected status " + status);
    }
  }

  // ── Recordings ───────────────────────────────────────────────────

  @Nested
  @DisplayName("Recordings")
  class Recordings {

    @Test
    void list() {
      var body = client.recordings().list(qp("page_size", "5"));
      assertNotNull(body);
      assertNotNull(body.data);
      assertTrue(body.data instanceof List);
      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/relay/rest/recordings", j.path);
      assertEquals(List.of("5"), j.getQueryParams().get("page_size"));
    }

    @Test
    void getById() {
      Map<String, Object> body = client.recordings().get("rec-123", java.util.Map.of());
      assertNotNull(body);
      assertTrue(body.containsKey("id"));
      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/relay/rest/recordings/rec-123", j.path);
    }

    @Test
    void deleteById() {
      Map<String, Object> body = client.recordings().delete("rec-123");
      assertNotNull(body);
      MockTest.JournalEntry j = mock.last();
      assertEquals("DELETE", j.method);
      assertEquals("/api/relay/rest/recordings/rec-123", j.path);
      int status = j.getResponseStatus();
      assertTrue(status == 200 || status == 202 || status == 204);
    }
  }

  // ── Short Codes ──────────────────────────────────────────────────

  @Nested
  @DisplayName("ShortCodes")
  class ShortCodes {

    @Test
    void list() {
      var body = client.shortCodes().list(qp("page_size", "20"));
      assertNotNull(body);
      assertNotNull(body.data);
      assertTrue(body.data instanceof List);
      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/relay/rest/short_codes", j.path);
    }

    @Test
    void getById() {
      var body = client.shortCodes().get("sc-1", java.util.Map.of());
      assertNotNull(body);
      assertNotNull(body.id);
      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/relay/rest/short_codes/sc-1", j.path);
    }

    @Test
    void update() {
      var body =
          client
              .shortCodes()
              .update(
                  "sc-1",
                  com.signalwire.sdk.rest.namespaces.generated.ShortCodes.UpdateRequest.builder()
                      .extras(kw("name", "Marketing SMS"))
                      .build());
      assertNotNull(body);
      assertNotNull(body.id);
      MockTest.JournalEntry j = mock.last();
      // short_codes uses PUT for update.
      assertEquals("PUT", j.method);
      assertEquals("/api/relay/rest/short_codes/sc-1", j.path);
      Map<String, Object> sent = j.bodyMap();
      assertNotNull(sent);
      assertEquals("Marketing SMS", sent.get("name"));
    }
  }

  // ── Imported Numbers ─────────────────────────────────────────────

  @Nested
  @DisplayName("ImportedNumbers")
  class ImportedNumbers {

    @Test
    void create() {
      var body =
          client
              .importedNumbers()
              .create(
                  com.signalwire.sdk.rest.namespaces.generated.ImportedNumbers.CreateRequest
                      .builder()
                      .number("+15551234567")
                      .numberType("longcode")
                      .capabilities(Arrays.asList("sms", "voice"))
                      .build());
      assertNotNull(body);
      assertNotNull(body.id);
      MockTest.JournalEntry j = mock.last();
      assertEquals("POST", j.method);
      assertEquals("/api/relay/rest/imported_phone_numbers", j.path);
      Map<String, Object> sent = j.bodyMap();
      assertNotNull(sent);
      assertEquals("+15551234567", sent.get("number"));
      assertEquals("longcode", sent.get("number_type"));
      assertEquals(Arrays.asList("sms", "voice"), sent.get("capabilities"));
    }
  }

  // ── MFA ──────────────────────────────────────────────────────────

  @Nested
  @DisplayName("Mfa")
  class Mfa {

    @Test
    void call() {
      var body =
          client
              .mfa()
              .call(
                  com.signalwire.sdk.rest.namespaces.generated.Mfa.CallRequest.builder()
                      .to("+15551234567")
                      .from("+15559876543")
                      .message("Your code is {code}")
                      .build());
      assertNotNull(body);
      assertNotNull(body.id);
      MockTest.JournalEntry j = mock.last();
      assertEquals("POST", j.method);
      assertEquals("/api/relay/rest/mfa/call", j.path);
      Map<String, Object> sent = j.bodyMap();
      assertNotNull(sent);
      assertEquals("+15551234567", sent.get("to"));
      assertEquals("+15559876543", sent.get("from"));
      assertEquals("Your code is {code}", sent.get("message"));
    }
  }

  // ── SIP Profile ──────────────────────────────────────────────────

  @Nested
  @DisplayName("SipProfile")
  class SipProfile {

    @Test
    void update() {
      var body =
          client
              .sipProfile()
              .update(
                  com.signalwire.sdk.rest.namespaces.generated.SipProfile.UpdateRequest.builder()
                      .domainIdentifier("myco")
                      .defaultCodecs(Arrays.asList("PCMU", "PCMA"))
                      .build());
      assertNotNull(body);
      assertTrue(
          body.domain_identifier != null || body.default_codecs != null,
          "expected domain_identifier/default_codecs to be set");
      MockTest.JournalEntry j = mock.last();
      assertEquals("PUT", j.method);
      assertEquals("/api/relay/rest/sip_profile", j.path);
      Map<String, Object> sent = j.bodyMap();
      assertNotNull(sent);
      assertEquals("myco", sent.get("domain_identifier"));
      assertEquals(Arrays.asList("PCMU", "PCMA"), sent.get("default_codecs"));
    }
  }

  // ── Number Groups ────────────────────────────────────────────────

  @Nested
  @DisplayName("NumberGroups memberships")
  class NumberGroups {

    @Test
    void listMemberships() {
      var body = client.numberGroups().listMemberships("ng-1", qp("page_size", "10"));
      assertNotNull(body);
      assertNotNull(body.data);
      assertTrue(body.data instanceof List);
      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/relay/rest/number_groups/ng-1/number_group_memberships", j.path);
      assertEquals(List.of("10"), j.getQueryParams().get("page_size"));
    }

    @Test
    void deleteMembership() {
      Map<String, Object> body = client.numberGroups().deleteMembership("mem-1");
      assertNotNull(body);
      MockTest.JournalEntry j = mock.last();
      assertEquals("DELETE", j.method);
      assertEquals("/api/relay/rest/number_group_memberships/mem-1", j.path);
      int status = j.getResponseStatus();
      assertTrue(status == 200 || status == 202 || status == 204);
    }
  }

  // ── Project tokens ───────────────────────────────────────────────

  @Nested
  @DisplayName("Project.tokens")
  class ProjectTokens {

    @Test
    void update() {
      var body =
          client
              .project()
              .tokens()
              .update(
                  "tok-1",
                  com.signalwire.sdk.rest.namespaces.generated.ProjectTokens.UpdateRequest.builder()
                      .extras(kw("name", "renamed-token"))
                      .build());
      assertNotNull(body);
      assertNotNull(body.id);
      MockTest.JournalEntry j = mock.last();
      assertEquals("PATCH", j.method);
      assertEquals("/api/project/tokens/tok-1", j.path);
      Map<String, Object> sent = j.bodyMap();
      assertNotNull(sent);
      assertEquals("renamed-token", sent.get("name"));
    }

    @Test
    void delete() {
      Map<String, Object> body = client.project().tokens().delete("tok-1");
      assertNotNull(body);
      MockTest.JournalEntry j = mock.last();
      assertEquals("DELETE", j.method);
      assertEquals("/api/project/tokens/tok-1", j.path);
      int status = j.getResponseStatus();
      assertTrue(status == 200 || status == 202 || status == 204);
    }
  }

  // ── Datasphere ───────────────────────────────────────────────────

  @Nested
  @DisplayName("Datasphere.documents.getChunk")
  class Datasphere {

    @Test
    void getChunk() {
      var body = client.datasphere().documents().getChunk("doc-1", "chunk-99", java.util.Map.of());
      assertNotNull(body);
      assertNotNull(body.id);
      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/datasphere/documents/doc-1/chunks/chunk-99", j.path);
    }
  }

  // ── Queues ───────────────────────────────────────────────────────

  @Nested
  @DisplayName("Queues.getMember")
  class Queues {

    @Test
    void getMember() {
      var body = client.queues().getMember("q-1", "mem-7", java.util.Map.of());
      assertNotNull(body);
      assertTrue(
          body.queue_id != null || body.call_id != null,
          "expected queue_id/call_id to be set");
      MockTest.JournalEntry j = mock.last();
      assertEquals("GET", j.method);
      assertEquals("/api/relay/rest/queues/q-1/members/mem-7", j.path);
    }
  }
}
