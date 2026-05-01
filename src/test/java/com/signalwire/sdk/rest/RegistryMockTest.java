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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-backed unit tests translated from
 * signalwire-python/tests/unit/rest/test_registry_mock.py.
 *
 * <p>Covers the 10DLC Campaign Registry namespace: brands, campaigns,
 * orders, and numbers.
 */
class RegistryMockTest {

    private static final String BASE = "/api/relay/rest/registry/beta";

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

    @Nested
    @DisplayName("Registry.brands")
    class Brands {

        @Test
        void listReturnsDict() {
            Map<String, Object> body = client.registry().brands().list();
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(BASE + "/brands", j.path);
            assertNotNull(j.getMatchedRoute(), "spec gap: brand list");
        }

        @Test
        void getUsesIdInPath() {
            Map<String, Object> body = client.registry().brands().get("brand-77");
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(BASE + "/brands/brand-77", j.path);
        }

        @Test
        void listCampaignsUsesBrandSubpath() {
            Map<String, Object> body = client.registry().brands().listCampaigns("brand-1");
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(BASE + "/brands/brand-1/campaigns", j.path);
            assertNotNull(j.getMatchedRoute());
        }

        @Test
        void createCampaignPostsToBrandSubpath() {
            Map<String, Object> body = client.registry().brands().createCampaign(
                    "brand-2", kw("usecase", "LOW_VOLUME", "description", "MFA"));
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(BASE + "/brands/brand-2/campaigns", j.path);
            Map<String, Object> jb = j.bodyMap();
            assertNotNull(jb);
            assertEquals("LOW_VOLUME", jb.get("usecase"));
            assertEquals("MFA", jb.get("description"));
        }
    }

    @Nested
    @DisplayName("Registry.campaigns")
    class Campaigns {

        @Test
        void getUsesIdInPath() {
            Map<String, Object> body = client.registry().campaigns().get("camp-1");
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(BASE + "/campaigns/camp-1", j.path);
        }

        @Test
        void updateUsesPut() {
            Map<String, Object> body = client.registry().campaigns().update(
                    "camp-2", kw("description", "Updated"));
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("PUT", j.method);
            assertEquals(BASE + "/campaigns/camp-2", j.path);
            Map<String, Object> jb = j.bodyMap();
            assertNotNull(jb);
            assertEquals("Updated", jb.get("description"));
        }

        @Test
        void listNumbersUsesNumbersSubpath() {
            Map<String, Object> body = client.registry().campaigns().listNumbers("camp-3");
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(BASE + "/campaigns/camp-3/numbers", j.path);
            assertNotNull(j.getMatchedRoute());
        }

        @Test
        void createOrderPostsToOrdersSubpath() {
            Map<String, Object> body = client.registry().campaigns().createOrder(
                    "camp-4", kw("numbers", Arrays.asList("pn-1", "pn-2")));
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(BASE + "/campaigns/camp-4/orders", j.path);
            Map<String, Object> jb = j.bodyMap();
            assertNotNull(jb);
            assertEquals(Arrays.asList("pn-1", "pn-2"), jb.get("numbers"));
        }
    }

    @Nested
    @DisplayName("Registry.orders")
    class Orders {

        @Test
        void getUsesIdInPath() {
            Map<String, Object> body = client.registry().orders().get("order-1");
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(BASE + "/orders/order-1", j.path);
            assertNotNull(j.getMatchedRoute(), "spec gap: order retrieve");
        }
    }

    @Nested
    @DisplayName("Registry.numbers")
    class Numbers {

        @Test
        void deleteUsesIdInPath() {
            Map<String, Object> body = client.registry().numbers().delete("num-1");
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("DELETE", j.method);
            assertEquals(BASE + "/numbers/num-1", j.path);
            assertNotNull(j.getMatchedRoute());
        }
    }
}
