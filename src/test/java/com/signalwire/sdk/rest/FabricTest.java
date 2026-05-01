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
        // Python parity: subscribers live under /fabric/resources/subscribers.
        assertEquals("/fabric/resources/subscribers", ns.subscribers().getBasePath());
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
    // exposes 16 sub-resource accessors. Note that all resource sub-types
    // hang off /fabric/resources (Python parity), and tokens has no base
    // path because each token endpoint sits at a distinct fixed URL.
    @Test
    void testFabricPythonParitySubResources() {
        var ns = new FabricNamespace(httpClient);
        assertEquals("/fabric/resources/ai_agents", ns.aiAgents().getBasePath());
        assertEquals("/fabric/resources/call_flows", ns.callFlows().getBasePath());
        assertEquals("/fabric/resources/conference_rooms", ns.conferenceRooms().getBasePath());
        assertEquals("/fabric/resources/cxml_applications", ns.cxmlApplications().getBasePath());
        assertEquals("/fabric/resources/cxml_scripts", ns.cxmlScripts().getBasePath());
        assertEquals("/fabric/resources/cxml_webhooks", ns.cxmlWebhooks().getBasePath());
        assertEquals("/fabric/resources/freeswitch_connectors", ns.freeswitchConnectors().getBasePath());
        assertEquals("/fabric/resources/relay_applications", ns.relayApplications().getBasePath());
        assertEquals("/fabric/resources/sip_endpoints", ns.sipEndpoints().getBasePath());
        assertEquals("/fabric/resources/sip_gateways", ns.sipGateways().getBasePath());
        assertEquals("/fabric/resources/swml_scripts", ns.swmlScripts().getBasePath());
        assertEquals("/fabric/resources/swml_webhooks", ns.swmlWebhooks().getBasePath());
        assertNotNull(ns.tokens());
    }
}
