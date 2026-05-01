/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-backed unit tests translated from
 * signalwire-python/tests/unit/rest/test_small_namespaces_mock.py.
 *
 * <p>Covers gaps for namespaces that each had a handful of uncovered methods:
 * addresses, recordings, short_codes, imported_numbers, mfa, sip_profile,
 * number_groups, project.tokens, datasphere.documents.get_chunk, queues.
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
            Map<String, Object> body = client.addresses().list(qp("page_size", "10"));
            assertNotNull(body);
            assertTrue(body.containsKey("data"));
            assertTrue(body.get("data") instanceof List);
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/relay/rest/addresses", j.path);
            assertNotNull(j.getMatchedRoute());
            assertEquals(List.of("10"), j.getQueryParams().get("page_size"));
        }

        @Test
        void create() {
            Map<String, Object> body = client.addresses().create(kw(
                    "address_type", "commercial",
                    "first_name", "Ada",
                    "last_name", "Lovelace",
                    "country", "US"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));

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
            Map<String, Object> body = client.addresses().get("addr-123");
            assertNotNull(body);
            assertTrue(body.containsKey("id"));

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
            assertTrue(status == 200 || status == 202 || status == 204,
                    "unexpected status " + status);
        }
    }

    // ── Recordings ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Recordings")
    class Recordings {

        @Test
        void list() {
            Map<String, Object> body = client.recordings().list(qp("page_size", "5"));
            assertNotNull(body);
            assertTrue(body.containsKey("data"));
            assertTrue(body.get("data") instanceof List);
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/relay/rest/recordings", j.path);
            assertEquals(List.of("5"), j.getQueryParams().get("page_size"));
        }

        @Test
        void getById() {
            Map<String, Object> body = client.recordings().get("rec-123");
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
            Map<String, Object> body = client.shortCodes().list(qp("page_size", "20"));
            assertNotNull(body);
            assertTrue(body.containsKey("data"));
            assertTrue(body.get("data") instanceof List);
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/relay/rest/short_codes", j.path);
        }

        @Test
        void getById() {
            Map<String, Object> body = client.shortCodes().get("sc-1");
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/relay/rest/short_codes/sc-1", j.path);
        }

        @Test
        void update() {
            Map<String, Object> body = client.shortCodes().update(
                    "sc-1", kw("name", "Marketing SMS"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
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
            Map<String, Object> body = client.importedNumbers().create(kw(
                    "number", "+15551234567",
                    "sip_username", "alice",
                    "sip_password", "secret",
                    "sip_proxy", "sip.example.com"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals("/api/relay/rest/imported_phone_numbers", j.path);
            Map<String, Object> sent = j.bodyMap();
            assertNotNull(sent);
            assertEquals("+15551234567", sent.get("number"));
            assertEquals("alice", sent.get("sip_username"));
            assertEquals("sip.example.com", sent.get("sip_proxy"));
        }
    }

    // ── MFA ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Mfa")
    class Mfa {

        @Test
        void call() {
            Map<String, Object> body = client.mfa().call(kw(
                    "to", "+15551234567",
                    "from_", "+15559876543",
                    "message", "Your code is {code}"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals("/api/relay/rest/mfa/call", j.path);
            Map<String, Object> sent = j.bodyMap();
            assertNotNull(sent);
            assertEquals("+15551234567", sent.get("to"));
            assertEquals("+15559876543", sent.get("from_"));
            assertEquals("Your code is {code}", sent.get("message"));
        }
    }

    // ── SIP Profile ──────────────────────────────────────────────────

    @Nested
    @DisplayName("SipProfile")
    class SipProfile {

        @Test
        void update() {
            Map<String, Object> body = client.sipProfile().update(kw(
                    "domain", "myco.sip.signalwire.com",
                    "default_codecs", Arrays.asList("PCMU", "PCMA")));
            assertNotNull(body);
            assertTrue(body.containsKey("domain") || body.containsKey("default_codecs"),
                    "expected domain/default_codecs, got " + body.keySet());
            MockTest.JournalEntry j = mock.last();
            assertEquals("PUT", j.method);
            assertEquals("/api/relay/rest/sip_profile", j.path);
            Map<String, Object> sent = j.bodyMap();
            assertNotNull(sent);
            assertEquals("myco.sip.signalwire.com", sent.get("domain"));
            assertEquals(Arrays.asList("PCMU", "PCMA"), sent.get("default_codecs"));
        }
    }

    // ── Number Groups ────────────────────────────────────────────────

    @Nested
    @DisplayName("NumberGroups memberships")
    class NumberGroups {

        @Test
        void listMemberships() {
            Map<String, Object> body = client.numberGroups().listMemberships(
                    "ng-1", qp("page_size", "10"));
            assertNotNull(body);
            assertTrue(body.containsKey("data"));
            assertTrue(body.get("data") instanceof List);
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
            Map<String, Object> body = client.project().tokens().update(
                    "tok-1", kw("name", "renamed-token"));
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
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
            Map<String, Object> body = client.datasphere().documents().getChunk(
                    "doc-1", "chunk-99");
            assertNotNull(body);
            assertTrue(body.containsKey("id"));
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
            Map<String, Object> body = client.queues().getMember("q-1", "mem-7");
            assertNotNull(body);
            assertTrue(body.containsKey("queue_id") || body.containsKey("call_id"),
                    "expected queue_id/call_id, got " + body.keySet());
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/relay/rest/queues/q-1/members/mem-7", j.path);
        }
    }
}
