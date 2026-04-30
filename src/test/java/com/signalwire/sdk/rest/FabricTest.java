package com.signalwire.sdk.rest;

import com.signalwire.sdk.rest.namespaces.FabricNamespace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for REST Fabric namespace.
 */
class FabricTest {

    private final HttpClient httpClient = new HttpClient("test.signalwire.com", "proj", "tok");

    @Test
    void testSubscribersPath() {
        var ns = new FabricNamespace(httpClient);
        assertEquals("/fabric/subscribers", ns.subscribers().getBasePath());
    }

    @Test
    void testAddressesPath() {
        var ns = new FabricNamespace(httpClient);
        assertEquals("/fabric/addresses", ns.addresses().getBasePath());
    }

    @Test
    void testResourcesPath() {
        var ns = new FabricNamespace(httpClient);
        assertEquals("/fabric/resources", ns.resources().getBasePath());
    }

    @Test
    void testAllResourcesNotNull() {
        var ns = new FabricNamespace(httpClient);
        assertNotNull(ns.subscribers());
        assertNotNull(ns.addresses());
        assertNotNull(ns.resources());
    }

    @Test
    void testFabricFromClient() {
        var client = RestClient.builder()
                .project("proj").token("tok").space("test.signalwire.com")
                .build();
        assertNotNull(client.fabric());
        assertSame(client.fabric(), client.fabric());
    }

    @Test
    void testFabricNamespaceResourcesAreReused() {
        var ns = new FabricNamespace(httpClient);
        assertSame(ns.subscribers(), ns.subscribers());
        assertSame(ns.addresses(), ns.addresses());
        assertSame(ns.resources(), ns.resources());
    }

    // Python parity: signalwire/rest/namespaces/fabric.py::FabricNamespace
    // exposes 16 sub-resource accessors. Java had 3; adding the 13 missing.
    @Test
    void testFabricPythonParitySubResources() {
        var ns = new FabricNamespace(httpClient);
        assertEquals("/fabric/ai_agents", ns.aiAgents().getBasePath());
        assertEquals("/fabric/call_flows", ns.callFlows().getBasePath());
        assertEquals("/fabric/conference_rooms", ns.conferenceRooms().getBasePath());
        assertEquals("/fabric/cxml_applications", ns.cxmlApplications().getBasePath());
        assertEquals("/fabric/cxml_scripts", ns.cxmlScripts().getBasePath());
        assertEquals("/fabric/cxml_webhooks", ns.cxmlWebhooks().getBasePath());
        assertEquals("/fabric/freeswitch_connectors", ns.freeswitchConnectors().getBasePath());
        assertEquals("/fabric/relay_applications", ns.relayApplications().getBasePath());
        assertEquals("/fabric/sip_endpoints", ns.sipEndpoints().getBasePath());
        assertEquals("/fabric/sip_gateways", ns.sipGateways().getBasePath());
        assertEquals("/fabric/swml_scripts", ns.swmlScripts().getBasePath());
        assertEquals("/fabric/swml_webhooks", ns.swmlWebhooks().getBasePath());
        assertEquals("/fabric/tokens", ns.tokens().getBasePath());
    }
}
