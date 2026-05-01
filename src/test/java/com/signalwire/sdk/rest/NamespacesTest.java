package com.signalwire.sdk.rest;

import com.signalwire.sdk.rest.namespaces.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for all REST namespaces.
 */
class NamespacesTest {

    private final HttpClient httpClient = new HttpClient("test.signalwire.com", "proj", "tok");

    @Test
    void testAll21NamespacesAccessible() {
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
    void testPhoneNumbersPath() {
        var ns = new PhoneNumbersNamespace(httpClient);
        assertEquals("/phone_numbers", ns.getResource().getBasePath());
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
        assertEquals("/video/room_sessions", ns.roomSessions().getBasePath());
        // Python parity: VideoRoomRecordings lives at /video/room_recordings;
        // recordings() is a legacy alias retained for backwards compat.
        assertEquals("/video/room_recordings", ns.recordings().getBasePath());
        assertEquals("/video/room_recordings", ns.roomRecordings().getBasePath());
        assertEquals("/video/conferences", ns.conferences().getBasePath());
        assertEquals("/video/streams", ns.streams().getBasePath());
    }

    @Test
    void testMessagingPath() {
        var ns = new MessagingNamespace(httpClient);
        assertEquals("/messaging/messages", ns.messages().getBasePath());
    }

    @Test
    void testSipPaths() {
        var ns = new SipNamespace(httpClient);
        assertEquals("/sip/endpoints", ns.endpoints().getBasePath());
        assertEquals("/sip/profiles", ns.profiles().getBasePath());
    }

    @Test
    void testFaxPath() {
        var ns = new FaxNamespace(httpClient);
        assertEquals("/fax/faxes", ns.faxes().getBasePath());
    }

    @Test
    void testChatPaths() {
        var ns = new ChatNamespace(httpClient);
        assertEquals("/chat/channels", ns.channels().getBasePath());
        assertEquals("/chat/messages", ns.messages().getBasePath());
        assertEquals("/chat/members", ns.members().getBasePath());
    }

    @Test
    void testPubSubPath() {
        var ns = new PubSubNamespace(httpClient);
        assertEquals("/pubsub/channels", ns.channels().getBasePath());
    }

    @Test
    void testSwmlPath() {
        var ns = new SwmlNamespace(httpClient);
        assertEquals("/relay/swml/scripts", ns.scripts().getBasePath());
    }

    @Test
    void testCampaignPaths() {
        var ns = new CampaignNamespace(httpClient);
        assertEquals("/campaign/brands", ns.brands().getBasePath());
        assertEquals("/campaign/campaigns", ns.campaigns().getBasePath());
        assertEquals("/campaign/orders", ns.orders().getBasePath());
        assertEquals("/campaign/assignments", ns.assignments().getBasePath());
    }

    @Test
    void testCompliancePaths() {
        var ns = new ComplianceNamespace(httpClient);
        assertEquals("/compliance/cnam", ns.cnamRegistrations().getBasePath());
        assertEquals("/compliance/shaken_stir", ns.shakenStir().getBasePath());
    }

    @Test
    void testBillingPaths() {
        var ns = new BillingNamespace(httpClient);
        assertEquals("/billing/invoices", ns.invoices().getBasePath());
        assertEquals("/billing/usage", ns.usage().getBasePath());
    }

    @Test
    void testStreamPath() {
        var ns = new StreamNamespace(httpClient);
        assertEquals("/streams", ns.streams().getBasePath());
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
        assertNotNull(ns.sipDomains());
        assertNotNull(ns.sipCredentialLists());
        assertNotNull(ns.sipIpAccessControlLists());
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
