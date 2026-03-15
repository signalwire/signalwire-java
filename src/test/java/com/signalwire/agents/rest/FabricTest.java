package com.signalwire.agents.rest;

import com.signalwire.agents.rest.namespaces.FabricNamespace;
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
        var client = SignalWireClient.builder()
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
}
