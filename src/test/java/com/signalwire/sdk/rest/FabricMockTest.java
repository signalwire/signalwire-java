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
 * signalwire-python/tests/unit/rest/test_fabric_mock.py.
 *
 * <p>Closes the gap left by {@code FabricTest} (which only checks paths):
 * fabric addresses, generic resources operations, subscriber sip-endpoint
 * sub-resources, call-flow / conference-room singular-path behaviour, the
 * full FabricTokens surface, and {@code cxml_applications.create} raising
 * a deliberate exception.
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
            assertEquals("fabric.list_fabric_addresses", j.getMatchedRoute(),
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

    // ── CxmlApplicationsResource.create ──────────────────────────────

    @Nested
    @DisplayName("CxmlApplications.create raises NotImplementedError-equivalent")
    class CxmlApplicationsCreate {

        @Test
        void createRaisesNotImplemented() {
            UnsupportedOperationException ex = assertThrows(
                    UnsupportedOperationException.class,
                    () -> client.fabric().cxmlApplications().create(kw("name", "never_built")));
            assertTrue(ex.getMessage().contains("cXML applications cannot"),
                    "unexpected message: " + ex.getMessage());
            // Nothing should have hit the wire.
            assertTrue(mock.journal().isEmpty(),
                    "expected no journal entries, got " + mock.journal());
        }
    }

    // ── CallFlowsResource.list_addresses (singular path) ─────────────

    @Nested
    @DisplayName("CallFlowsResource sub-paths use singular call_flow")
    class CallFlowsAddresses {

        @Test
        void listAddressesUsesSingularPath() {
            Map<String, Object> body = client.fabric().callFlows().listAddresses("cf-1");
            assertNotNull(body);
            assertTrue(body.containsKey("data"));
            assertTrue(body.get("data") instanceof List);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/fabric/resources/call_flow/cf-1/addresses", j.path);
            assertNotNull(j.getMatchedRoute(),
                    "spec gap: call-flow addresses sub-path");
        }
    }

    // ── ConferenceRoomsResource.list_addresses (singular path) ───────

    @Nested
    @DisplayName("ConferenceRoomsResource sub-paths use singular conference_room")
    class ConferenceRoomsAddresses {

        @Test
        void listAddressesUsesSingularPath() {
            Map<String, Object> body = client.fabric().conferenceRooms().listAddresses("cr-1");
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
            Map<String, Object> body = client.fabric().subscribers()
                    .getSipEndpoint("sub-1", "ep-1");
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/fabric/resources/subscribers/sub-1/sip_endpoints/ep-1",
                    j.path);
            assertNotNull(j.getMatchedRoute());
        }

        @Test
        void updateSipEndpointUsesPatch() {
            Map<String, Object> body = client.fabric().subscribers().updateSipEndpoint(
                    "sub-1", "ep-1", kw("username", "renamed"));
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("PATCH", j.method);
            assertEquals("/api/fabric/resources/subscribers/sub-1/sip_endpoints/ep-1",
                    j.path);
            Map<String, Object> jb = j.bodyMap();
            assertNotNull(jb);
            assertEquals("renamed", jb.get("username"));
        }

        @Test
        void deleteSipEndpoint() {
            Map<String, Object> body = client.fabric().subscribers()
                    .deleteSipEndpoint("sub-1", "ep-1");
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("DELETE", j.method);
            assertEquals("/api/fabric/resources/subscribers/sub-1/sip_endpoints/ep-1",
                    j.path);
            assertNotNull(j.getMatchedRoute());
        }
    }

    // ── FabricTokens ─────────────────────────────────────────────────

    @Nested
    @DisplayName("FabricTokens")
    class Tokens {

        @Test
        void createInviteToken() {
            Map<String, Object> body = client.fabric().tokens().createInviteToken(
                    kw("email", "invitee@example.com"));
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            // Singular 'subscriber' path segment per spec.
            assertEquals("/api/fabric/subscriber/invites", j.path);
            Map<String, Object> jb = j.bodyMap();
            assertNotNull(jb);
            assertEquals("invitee@example.com", jb.get("email"));
        }

        @Test
        void createEmbedToken() {
            Map<String, Object> body = client.fabric().tokens().createEmbedToken(
                    kw("allowed_addresses", Arrays.asList("addr-1", "addr-2")));
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals("/api/fabric/embeds/tokens", j.path);
            Map<String, Object> jb = j.bodyMap();
            assertNotNull(jb);
            assertEquals(Arrays.asList("addr-1", "addr-2"), jb.get("allowed_addresses"));
        }

        @Test
        void refreshSubscriberToken() {
            Map<String, Object> body = client.fabric().tokens().refreshSubscriberToken(
                    kw("refresh_token", "abc-123"));
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
            Map<String, Object> body = client.fabric().resources().list();
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
            Map<String, Object> body = client.fabric().resources().get("res-1");
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
            Map<String, Object> body = client.fabric().resources().listAddresses("res-3");
            assertNotNull(body);
            assertTrue(body.containsKey("data"));
            assertTrue(body.get("data") instanceof List);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/fabric/resources/res-3/addresses", j.path);
        }

        @Test
        void assignDomainApplication() {
            Map<String, Object> body = client.fabric().resources().assignDomainApplication(
                    "res-4", kw("domain_application_id", "da-7"));
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
