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
 * signalwire-python/tests/unit/rest/test_compat_misc.py.
 *
 * <p>Covers single-method gaps in compat sub-resources:
 * {@code applications.update} and {@code lamlBins.update}.
 */
class CompatMiscMockTest {

    private static final String ACCT_BASE =
            "/api/laml/2010-04-01/Accounts/test_proj";

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
    @DisplayName("CompatApplications.update")
    class ApplicationsUpdate {

        @Test
        @DisplayName("returns_application_resource")
        void returnsApplication() {
            Map<String, Object> result = client.compat().applications().update(
                    "AP_U", kw("FriendlyName", "updated"));
            assertNotNull(result);
            assertTrue(result.containsKey("friendly_name") || result.containsKey("sid"),
                    "expected friendly_name/sid, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_post_with_friendly_name")
        void journalRecordsPost() {
            client.compat().applications().update(
                    "AP_UU", kw("FriendlyName", "renamed", "VoiceUrl", "https://a.b/v"));
            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(ACCT_BASE + "/Applications/AP_UU", j.path);
            Map<String, Object> body = j.bodyMap();
            assertNotNull(body);
            assertEquals("renamed", body.get("FriendlyName"));
            assertEquals("https://a.b/v", body.get("VoiceUrl"));
        }
    }

    @Nested
    @DisplayName("CompatLamlBins.update")
    class LamlBinsUpdate {

        @Test
        @DisplayName("returns_laml_bin_resource")
        void returnsLamlBin() {
            Map<String, Object> result = client.compat().lamlBins().update(
                    "LB_U", kw("FriendlyName", "updated"));
            assertNotNull(result);
            assertTrue(result.containsKey("friendly_name")
                            || result.containsKey("sid")
                            || result.containsKey("contents"),
                    "expected friendly_name/sid/contents, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_post_with_friendly_name")
        void journalRecordsPost() {
            client.compat().lamlBins().update(
                    "LB_UU", kw("FriendlyName", "renamed", "Contents", "<Response/>"));
            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(ACCT_BASE + "/LamlBins/LB_UU", j.path);
            Map<String, Object> body = j.bodyMap();
            assertNotNull(body);
            assertEquals("renamed", body.get("FriendlyName"));
            assertEquals("<Response/>", body.get("Contents"));
        }
    }
}
