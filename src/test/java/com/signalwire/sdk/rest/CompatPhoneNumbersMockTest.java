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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-backed unit tests translated from
 * signalwire-python/tests/unit/rest/test_compat_phone_numbers.py.
 *
 * <p>Covers IncomingPhoneNumbers + AvailablePhoneNumbers operations.
 */
class CompatPhoneNumbersMockTest {

    private RestClient client;
    private MockTest.Harness mock;

    @BeforeEach
    void setUp() {
        MockTest.Bound bound = MockTest.newClient();
        this.client = bound.client;
        this.mock = bound.harness;
    }

    @Nested
    @DisplayName("CompatPhoneNumbers.list")
    class List_ {

        @Test
        @DisplayName("returns_paginated_list")
        void returnsPaginatedList() {
            Map<String, Object> result = client.compat().phoneNumbers().list();
            assertNotNull(result);
            assertTrue(result.containsKey("incoming_phone_numbers"),
                    "expected 'incoming_phone_numbers' key, got " + result.keySet());
            assertTrue(result.get("incoming_phone_numbers") instanceof List,
                    "expected list, got " + result.get("incoming_phone_numbers"));
        }

        @Test
        @DisplayName("journal_records_get_to_incoming_phone_numbers")
        void journalRecordsGet() {
            client.compat().phoneNumbers().list();
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/IncomingPhoneNumbers",
                    j.path);
        }
    }

    @Nested
    @DisplayName("CompatPhoneNumbers.get")
    class Get {

        @Test
        @DisplayName("returns_phone_number_resource")
        void returnsPhoneNumberResource() {
            Map<String, Object> result = client.compat().phoneNumbers().get("PN_TEST");
            assertNotNull(result);
            // Incoming phone-number resources carry phone_number + sid + capabilities.
            assertTrue(result.containsKey("phone_number") || result.containsKey("sid"),
                    "expected phone_number/sid, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_get_with_sid")
        void journalRecordsGetWithSid() {
            client.compat().phoneNumbers().get("PN_GET");
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/IncomingPhoneNumbers/PN_GET",
                    j.path);
        }
    }

    @Nested
    @DisplayName("CompatPhoneNumbers.update")
    class Update {

        @Test
        @DisplayName("returns_phone_number_resource")
        void returnsPhoneNumberResource() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("FriendlyName", "updated");
            Map<String, Object> result = client.compat().phoneNumbers().update("PN_U", body);
            assertNotNull(result);
            assertTrue(result.containsKey("phone_number") || result.containsKey("sid"),
                    "expected phone_number/sid, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_post_with_friendly_name")
        void journalRecordsPostWithFriendlyName() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("FriendlyName", "updated");
            body.put("VoiceUrl", "https://a.b/v");
            client.compat().phoneNumbers().update("PN_UU", body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/IncomingPhoneNumbers/PN_UU",
                    j.path);
            Map<String, Object> jb = j.bodyMap();
            assertNotNull(jb);
            assertEquals("updated", jb.get("FriendlyName"));
            assertEquals("https://a.b/v", jb.get("VoiceUrl"));
        }
    }

    @Nested
    @DisplayName("CompatPhoneNumbers.delete")
    class Delete {

        @Test
        @DisplayName("no_exception_on_delete")
        void noExceptionOnDelete() {
            Map<String, Object> result = client.compat().phoneNumbers().delete("PN_D");
            assertNotNull(result);
        }

        @Test
        @DisplayName("journal_records_delete_at_phone_number_path")
        void journalRecordsDelete() {
            client.compat().phoneNumbers().delete("PN_DEL");
            MockTest.JournalEntry j = mock.last();
            assertEquals("DELETE", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/IncomingPhoneNumbers/PN_DEL",
                    j.path);
        }
    }

    @Nested
    @DisplayName("CompatPhoneNumbers.purchase")
    class Purchase {

        @Test
        @DisplayName("returns_purchased_number")
        void returnsPurchasedNumber() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("PhoneNumber", "+15555550100");
            Map<String, Object> result = client.compat().phoneNumbers().purchase(body);
            assertNotNull(result);
            // Purchase returns the newly created IncomingPhoneNumber.
            assertTrue(result.containsKey("phone_number") || result.containsKey("sid"),
                    "expected phone_number/sid, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_post_with_phone_number")
        void journalRecordsPost() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("PhoneNumber", "+15555550100");
            body.put("FriendlyName", "Main");
            client.compat().phoneNumbers().purchase(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/IncomingPhoneNumbers",
                    j.path);
            Map<String, Object> jb = j.bodyMap();
            assertNotNull(jb);
            assertEquals("+15555550100", jb.get("PhoneNumber"));
            assertEquals("Main", jb.get("FriendlyName"));
        }
    }

    @Nested
    @DisplayName("CompatPhoneNumbers.importNumber")
    class Import_ {

        @Test
        @DisplayName("returns_imported_number")
        void returnsImportedNumber() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("PhoneNumber", "+15555550111");
            Map<String, Object> result = client.compat().phoneNumbers().importNumber(body);
            assertNotNull(result);
            // Imported numbers also synthesise to IncomingPhoneNumber-shaped.
            assertTrue(result.containsKey("phone_number") || result.containsKey("sid"),
                    "expected phone_number/sid, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_post_to_imported_phone_numbers")
        void journalRecordsPost() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("PhoneNumber", "+15555550111");
            body.put("VoiceUrl", "https://a.b/v");
            client.compat().phoneNumbers().importNumber(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            // Note the path is ImportedPhoneNumbers, not IncomingPhoneNumbers.
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/ImportedPhoneNumbers",
                    j.path);
            Map<String, Object> jb = j.bodyMap();
            assertNotNull(jb);
            assertEquals("+15555550111", jb.get("PhoneNumber"));
        }
    }

    @Nested
    @DisplayName("CompatPhoneNumbers.listAvailableCountries")
    class ListAvailableCountries {

        @Test
        @DisplayName("returns_countries_collection")
        void returnsCountriesCollection() {
            Map<String, Object> result = client.compat().phoneNumbers().listAvailableCountries();
            assertNotNull(result);
            assertTrue(result.containsKey("countries"),
                    "expected 'countries' key, got " + result.keySet());
            assertTrue(result.get("countries") instanceof List,
                    "expected list, got " + result.get("countries"));
        }

        @Test
        @DisplayName("journal_records_get_to_available_phone_numbers")
        void journalRecordsGet() {
            client.compat().phoneNumbers().listAvailableCountries();
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/AvailablePhoneNumbers",
                    j.path);
        }
    }

    @Nested
    @DisplayName("CompatPhoneNumbers.searchTollFree")
    class SearchTollFree {

        @Test
        @DisplayName("returns_available_numbers")
        void returnsAvailableNumbers() {
            Map<String, String> q = new LinkedHashMap<>();
            q.put("AreaCode", "800");
            Map<String, Object> result = client.compat().phoneNumbers().searchTollFree("US", q);
            assertNotNull(result);
            assertTrue(result.containsKey("available_phone_numbers"),
                    "expected 'available_phone_numbers' key, got " + result.keySet());
            assertTrue(result.get("available_phone_numbers") instanceof List);
        }

        @Test
        @DisplayName("journal_records_get_with_country_in_path")
        void journalRecordsGet() {
            Map<String, String> q = new LinkedHashMap<>();
            q.put("AreaCode", "888");
            client.compat().phoneNumbers().searchTollFree("US", q);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/AvailablePhoneNumbers/US/TollFree",
                    j.path);
            // The AreaCode should be on the query string, not body.
            Map<String, List<String>> qp = j.getQueryParams();
            assertNotNull(qp, "expected query params");
            assertTrue(qp.containsKey("AreaCode"),
                    "expected AreaCode in query, got " + qp.keySet());
            assertEquals(List.of("888"), qp.get("AreaCode"));
        }
    }
}
