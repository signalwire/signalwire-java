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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-backed unit tests translated from
 * signalwire-python/tests/unit/rest/test_compat_accounts.py.
 *
 * <p>Drives {@code client.compat().accounts()} against the live mock server
 * and asserts both the SDK return value and the recorded request journal.
 */
class CompatAccountsMockTest {

    private static final String COLLECTION = "/api/laml/2010-04-01/Accounts";

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
    @DisplayName("CompatAccounts.create")
    class Create {

        @Test
        @DisplayName("returns_account_resource")
        void returnsAccountResource() {
            Map<String, Object> result = client.compat().accounts().create(
                    kw("FriendlyName", "Sub-A"));
            assertNotNull(result);
            assertTrue(result.containsKey("friendly_name"),
                    "expected friendly_name, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_post_to_accounts")
        void journalRecordsPost() {
            client.compat().accounts().create(kw("FriendlyName", "Sub-B"));
            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(COLLECTION, j.path);
            Map<String, Object> body = j.bodyMap();
            assertNotNull(body);
            assertEquals("Sub-B", body.get("FriendlyName"));
            assertNotNull(j.getResponseStatus());
            int status = j.getResponseStatus();
            assertTrue(status >= 200 && status < 400, "unexpected status " + status);
        }
    }

    @Nested
    @DisplayName("CompatAccounts.get")
    class Get {

        @Test
        @DisplayName("returns_account_for_sid")
        void returnsAccount() {
            Map<String, Object> result = client.compat().accounts().get("AC123");
            assertNotNull(result);
            assertTrue(result.containsKey("friendly_name"));
        }

        @Test
        @DisplayName("journal_records_get_with_sid")
        void journalRecordsGet() {
            client.compat().accounts().get("AC_SAMPLE_SID");
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(COLLECTION + "/AC_SAMPLE_SID", j.path);
            assertTrue(j.body == null || (j.body instanceof Map && ((Map<?, ?>) j.body).isEmpty())
                    || (j.body instanceof String && ((String) j.body).isEmpty()),
                    "expected empty body, got " + j.body);
            assertNotNull(j.getMatchedRoute(), "spec gap: account-get should match a route");
        }
    }

    @Nested
    @DisplayName("CompatAccounts.update")
    class Update {

        @Test
        @DisplayName("returns_updated_account")
        void returnsUpdated() {
            Map<String, Object> result = client.compat().accounts().update(
                    "AC123", kw("FriendlyName", "Renamed"));
            assertNotNull(result);
            assertTrue(result.containsKey("friendly_name"));
        }

        @Test
        @DisplayName("journal_records_post_to_account_path")
        void journalRecordsPost() {
            client.compat().accounts().update("AC_X", kw("FriendlyName", "NewName"));
            MockTest.JournalEntry j = mock.last();
            // Twilio-compat update uses POST.
            assertEquals("POST", j.method);
            assertEquals(COLLECTION + "/AC_X", j.path);
            Map<String, Object> body = j.bodyMap();
            assertNotNull(body);
            assertEquals("NewName", body.get("FriendlyName"));
        }
    }
}
