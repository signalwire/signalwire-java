/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import com.signalwire.sdk.rest.namespaces.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the REST client components.
 * No live HTTP connections -- tests verify client creation, namespace
 * construction, CRUD paths, and error formatting.
 */
class RestTest {

    // ── RestClient ─────────────────────────────────────────────

    @Nested
    @DisplayName("RestClient")
    class ClientTests {

        @Test
        @DisplayName("Builder creates client with required params")
        void builderCreates() {
            var client = RestClient.builder()
                    .project("proj-1")
                    .token("tok-1")
                    .space("example.signalwire.com")
                    .build();

            assertEquals("proj-1", client.getProject());
            assertEquals("example.signalwire.com", client.getSpace());
            assertNotNull(client.getHttpClient());
        }

        @Test
        @DisplayName("Builder fails without project")
        void builderNoProject() {
            assertThrows(NullPointerException.class, () ->
                    RestClient.builder()
                            .token("tok")
                            .space("space")
                            .build());
        }

        @Test
        @DisplayName("Builder fails without token")
        void builderNoToken() {
            assertThrows(NullPointerException.class, () ->
                    RestClient.builder()
                            .project("proj")
                            .space("space")
                            .build());
        }

        @Test
        @DisplayName("Builder fails without space")
        void builderNoSpace() {
            assertThrows(NullPointerException.class, () ->
                    RestClient.builder()
                            .project("proj")
                            .token("tok")
                            .build());
        }
    }

    // ── Namespaces ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Namespaces")
    class NamespaceTests {

        private final HttpClient httpClient = new HttpClient("test.signalwire.com", "proj", "tok");

        @Test
        @DisplayName("All 21 namespaces are accessible")
        void allNamespaces() {
            var client = RestClient.builder()
                    .project("proj").token("tok").space("test.signalwire.com")
                    .build();

            assertNotNull(client.fabric());
            assertNotNull(client.calling());
            assertNotNull(client.phoneNumbers());
            assertNotNull(client.datasphere());
            assertNotNull(client.video());
            assertNotNull(client.compat());
            assertNotNull(client.messaging());
            assertNotNull(client.sip());
            assertNotNull(client.fax());
            assertNotNull(client.chat());
            assertNotNull(client.pubSub());
            assertNotNull(client.swml());
            assertNotNull(client.campaign());
            assertNotNull(client.compliance());
            assertNotNull(client.billing());
            assertNotNull(client.project());
            assertNotNull(client.streams());
            assertNotNull(client.numberLookup());
            assertNotNull(client.conferences());
            assertNotNull(client.queues());
            assertNotNull(client.recordings());
            assertNotNull(client.transcriptions());
        }

        @Test
        @DisplayName("Namespaces are lazily initialized and reused")
        void namespacesReused() {
            var client = RestClient.builder()
                    .project("proj").token("tok").space("test.signalwire.com")
                    .build();

            assertSame(client.phoneNumbers(), client.phoneNumbers());
            assertSame(client.fabric(), client.fabric());
            assertSame(client.datasphere(), client.datasphere());
            assertSame(client.compat(), client.compat());
        }

        @Test
        @DisplayName("FabricNamespace has subscribers, addresses, resources")
        void fabricNamespace() {
            var ns = new FabricNamespace(httpClient);
            assertNotNull(ns.subscribers());
            assertNotNull(ns.addresses());
            assertNotNull(ns.resources());
            assertEquals("/fabric/subscribers", ns.subscribers().getBasePath());
            assertEquals("/fabric/addresses", ns.addresses().getBasePath());
            assertEquals("/fabric/resources", ns.resources().getBasePath());
        }

        @Test
        @DisplayName("CallingNamespace has calls")
        void callingNamespace() {
            var ns = new CallingNamespace(httpClient);
            assertNotNull(ns.calls());
            assertEquals("/calling/calls", ns.calls().getBasePath());
        }

        @Test
        @DisplayName("PhoneNumbersNamespace has correct path")
        void phoneNumbersNamespace() {
            var ns = new PhoneNumbersNamespace(httpClient);
            assertNotNull(ns.getResource());
            assertEquals("/phone_numbers", ns.getResource().getBasePath());
        }

        @Test
        @DisplayName("DatasphereNamespace has documents")
        void datasphereNamespace() {
            var ns = new DatasphereNamespace(httpClient);
            assertNotNull(ns.documents());
            assertEquals("/datasphere/documents", ns.documents().getBasePath());
        }

        @Test
        @DisplayName("VideoNamespace has rooms, sessions, recordings")
        void videoNamespace() {
            var ns = new VideoNamespace(httpClient);
            assertNotNull(ns.rooms());
            assertNotNull(ns.roomSessions());
            assertNotNull(ns.recordings());
            assertEquals("/video/rooms", ns.rooms().getBasePath());
            assertEquals("/video/room_sessions", ns.roomSessions().getBasePath());
            assertEquals("/video/recordings", ns.recordings().getBasePath());
        }

        @Test
        @DisplayName("CompatNamespace paths include account SID")
        void compatNamespace() {
            var ns = new CompatNamespace(httpClient, "AC123");
            assertEquals("/laml/2010-04-01/Accounts/AC123/Calls", ns.calls().getBasePath());
            assertEquals("/laml/2010-04-01/Accounts/AC123/Messages", ns.messages().getBasePath());
            assertNotNull(ns.recordings());
            assertNotNull(ns.queues());
            assertNotNull(ns.conferences());
            assertNotNull(ns.transcriptions());
            assertNotNull(ns.applications());
            assertNotNull(ns.sipDomains());
            assertNotNull(ns.sipCredentialLists());
            assertNotNull(ns.sipIpAccessControlLists());
        }

        @Test
        @DisplayName("MessagingNamespace has messages")
        void messagingNamespace() {
            var ns = new MessagingNamespace(httpClient);
            assertNotNull(ns.messages());
            assertEquals("/messaging/messages", ns.messages().getBasePath());
        }

        @Test
        @DisplayName("SipNamespace has endpoints and profiles")
        void sipNamespace() {
            var ns = new SipNamespace(httpClient);
            assertEquals("/sip/endpoints", ns.endpoints().getBasePath());
            assertEquals("/sip/profiles", ns.profiles().getBasePath());
        }

        @Test
        @DisplayName("FaxNamespace has faxes")
        void faxNamespace() {
            var ns = new FaxNamespace(httpClient);
            assertEquals("/fax/faxes", ns.faxes().getBasePath());
        }

        @Test
        @DisplayName("ChatNamespace has channels, messages, members")
        void chatNamespace() {
            var ns = new ChatNamespace(httpClient);
            assertEquals("/chat/channels", ns.channels().getBasePath());
            assertEquals("/chat/messages", ns.messages().getBasePath());
            assertEquals("/chat/members", ns.members().getBasePath());
        }

        @Test
        @DisplayName("PubSubNamespace has channels")
        void pubSubNamespace() {
            var ns = new PubSubNamespace(httpClient);
            assertEquals("/pubsub/channels", ns.channels().getBasePath());
        }

        @Test
        @DisplayName("SwmlNamespace has scripts")
        void swmlNamespace() {
            var ns = new SwmlNamespace(httpClient);
            assertEquals("/relay/swml/scripts", ns.scripts().getBasePath());
        }

        @Test
        @DisplayName("CampaignNamespace has brands, campaigns, orders, assignments")
        void campaignNamespace() {
            var ns = new CampaignNamespace(httpClient);
            assertEquals("/campaign/brands", ns.brands().getBasePath());
            assertEquals("/campaign/campaigns", ns.campaigns().getBasePath());
            assertEquals("/campaign/orders", ns.orders().getBasePath());
            assertEquals("/campaign/assignments", ns.assignments().getBasePath());
        }

        @Test
        @DisplayName("ComplianceNamespace has cnam and shakenStir")
        void complianceNamespace() {
            var ns = new ComplianceNamespace(httpClient);
            assertEquals("/compliance/cnam", ns.cnamRegistrations().getBasePath());
            assertEquals("/compliance/shaken_stir", ns.shakenStir().getBasePath());
        }

        @Test
        @DisplayName("BillingNamespace has invoices and usage")
        void billingNamespace() {
            var ns = new BillingNamespace(httpClient);
            assertEquals("/billing/invoices", ns.invoices().getBasePath());
            assertEquals("/billing/usage", ns.usage().getBasePath());
        }

        @Test
        @DisplayName("StreamNamespace has streams")
        void streamNamespace() {
            var ns = new StreamNamespace(httpClient);
            assertEquals("/streams", ns.streams().getBasePath());
        }

        @Test
        @DisplayName("ConferenceNamespace has conferences and participants")
        void conferenceNamespace() {
            var ns = new ConferenceNamespace(httpClient);
            assertEquals("/calling/conferences", ns.conferences().getBasePath());
            assertEquals("/calling/conferences/participants", ns.participants().getBasePath());
        }

        @Test
        @DisplayName("QueueNamespace has queues")
        void queueNamespace() {
            var ns = new QueueNamespace(httpClient);
            assertEquals("/calling/queues", ns.queues().getBasePath());
        }

        @Test
        @DisplayName("RecordingNamespace has recordings")
        void recordingNamespace() {
            var ns = new RecordingNamespace(httpClient);
            assertEquals("/calling/recordings", ns.recordings().getBasePath());
        }

        @Test
        @DisplayName("TranscriptionNamespace has transcriptions")
        void transcriptionNamespace() {
            var ns = new TranscriptionNamespace(httpClient);
            assertEquals("/calling/transcriptions", ns.transcriptions().getBasePath());
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
        @DisplayName("Error message includes status, method, path")
        void errorMessage() {
            var error = new RestError(404, "GET", "/phone_numbers/123", "Not found");
            assertTrue(error.getMessage().contains("404"));
            assertTrue(error.getMessage().contains("GET"));
            assertTrue(error.getMessage().contains("/phone_numbers/123"));
            assertTrue(error.getMessage().contains("Not found"));
        }

        @Test
        @DisplayName("Error properties are accessible")
        void errorProperties() {
            var error = new RestError(500, "POST", "/path", "body");
            assertEquals(500, error.getStatusCode());
            assertEquals("POST", error.getMethod());
            assertEquals("/path", error.getPath());
            assertEquals("body", error.getResponseBody());
        }

        @Test
        @DisplayName("isClientError returns true for 4xx")
        void clientError() {
            assertTrue(new RestError(400, "GET", "/", "").isClientError());
            assertTrue(new RestError(404, "GET", "/", "").isClientError());
            assertTrue(new RestError(422, "GET", "/", "").isClientError());
            assertFalse(new RestError(500, "GET", "/", "").isClientError());
            assertFalse(new RestError(200, "GET", "/", "").isClientError());
        }

        @Test
        @DisplayName("isServerError returns true for 5xx")
        void serverError() {
            assertTrue(new RestError(500, "GET", "/", "").isServerError());
            assertTrue(new RestError(503, "GET", "/", "").isServerError());
            assertFalse(new RestError(404, "GET", "/", "").isServerError());
        }

        @Test
        @DisplayName("isNotFound returns true for 404")
        void notFound() {
            assertTrue(new RestError(404, "GET", "/", "").isNotFound());
            assertFalse(new RestError(400, "GET", "/", "").isNotFound());
        }

        @Test
        @DisplayName("isUnauthorized returns true for 401 and 403")
        void unauthorized() {
            assertTrue(new RestError(401, "GET", "/", "").isUnauthorized());
            assertTrue(new RestError(403, "GET", "/", "").isUnauthorized());
            assertFalse(new RestError(404, "GET", "/", "").isUnauthorized());
        }

        @Test
        @DisplayName("Error with cause preserves exception chain")
        void errorWithCause() {
            var cause = new RuntimeException("original");
            var error = new RestError(0, "GET", "/path", "msg", cause);
            assertSame(cause, error.getCause());
        }

        @Test
        @DisplayName("Error truncates long response body in message")
        void errorTruncatesBody() {
            String longBody = "x".repeat(300);
            var error = new RestError(500, "GET", "/", longBody);
            assertTrue(error.getMessage().contains("..."));
            assertTrue(error.getMessage().length() < longBody.length() + 100);
        }

        @Test
        @DisplayName("Error with null response body")
        void errorNullBody() {
            var error = new RestError(500, "GET", "/", null);
            assertNotNull(error.getMessage());
            assertFalse(error.getMessage().contains("null"));
        }

        @Test
        @DisplayName("Error with empty response body")
        void errorEmptyBody() {
            var error = new RestError(500, "GET", "/", "");
            assertNotNull(error.getMessage());
        }
    }
}
