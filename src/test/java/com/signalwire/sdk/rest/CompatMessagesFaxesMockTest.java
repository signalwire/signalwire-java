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
 * signalwire-python/tests/unit/rest/test_compat_messages_faxes.py.
 *
 * <p>Covers Compat Messages and Faxes media + update operations.
 */
class CompatMessagesFaxesMockTest {

    private RestClient client;
    private MockTest.Harness mock;

    @BeforeEach
    void setUp() {
        MockTest.Bound bound = MockTest.newClient();
        this.client = bound.client;
        this.mock = bound.harness;
    }

    // ── Messages ────────────────────────────────────────────────────

    @Nested
    @DisplayName("CompatMessages.update")
    class MessagesUpdate {

        @Test
        @DisplayName("returns_message_resource")
        void returnsMessageResource() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Body", "updated body");
            Map<String, Object> result = client.compat().messages().update("MM_TEST", body);
            assertNotNull(result);
            // Message resources carry body + status + sid fields.
            assertTrue(result.containsKey("body") || result.containsKey("sid"),
                    "expected body/sid, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_post_to_message")
        void journalRecordsPostToMessage() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Body", "x");
            body.put("Status", "canceled");
            client.compat().messages().update("MM_U1", body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Messages/MM_U1",
                    j.path);
            Map<String, Object> jb = j.bodyMap();
            assertNotNull(jb);
            assertEquals("x", jb.get("Body"));
            assertEquals("canceled", jb.get("Status"));
        }
    }

    @Nested
    @DisplayName("CompatMessages.getMedia")
    class MessagesGetMedia {

        @Test
        @DisplayName("returns_media_resource")
        void returnsMediaResource() {
            Map<String, Object> result = client.compat().messages().getMedia("MM_GM", "ME_GM");
            assertNotNull(result);
            // Media resources expose content_type + sid + parent_sid.
            assertTrue(result.containsKey("content_type") || result.containsKey("sid"),
                    "expected content_type/sid, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_get_to_media_path")
        void journalRecordsGetToMediaPath() {
            client.compat().messages().getMedia("MM_X", "ME_X");

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Messages/MM_X/Media/ME_X",
                    j.path);
        }
    }

    @Nested
    @DisplayName("CompatMessages.deleteMedia")
    class MessagesDeleteMedia {

        @Test
        @DisplayName("no_exception_on_delete")
        void noExceptionOnDelete() {
            Map<String, Object> result = client.compat().messages().deleteMedia("MM_DM", "ME_DM");
            // The SDK's DELETE handler returns {} on 204 or whatever the mock
            // body is for non-204 responses.  Either way we expect a Map.
            assertNotNull(result);
        }

        @Test
        @DisplayName("journal_records_delete")
        void journalRecordsDelete() {
            client.compat().messages().deleteMedia("MM_D", "ME_D");

            MockTest.JournalEntry j = mock.last();
            assertEquals("DELETE", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Messages/MM_D/Media/ME_D",
                    j.path);
        }
    }

    // ── Faxes ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("CompatFaxes.update")
    class FaxesUpdate {

        @Test
        @DisplayName("returns_fax_resource")
        void returnsFaxResource() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Status", "canceled");
            Map<String, Object> result = client.compat().faxes().update("FX_U", body);
            assertNotNull(result);
            // Fax resources carry direction + status + duration.
            assertTrue(result.containsKey("status") || result.containsKey("direction"),
                    "expected status/direction, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_post_with_status")
        void journalRecordsPostWithStatus() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Status", "canceled");
            client.compat().faxes().update("FX_U2", body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Faxes/FX_U2",
                    j.path);
            Map<String, Object> jb = j.bodyMap();
            assertNotNull(jb);
            assertEquals("canceled", jb.get("Status"));
        }
    }

    @Nested
    @DisplayName("CompatFaxes.listMedia")
    class FaxesListMedia {

        @Test
        @DisplayName("returns_paginated_list")
        void returnsPaginatedList() {
            Map<String, Object> result = client.compat().faxes().listMedia("FX_LM");
            assertNotNull(result);
            // Fax media listing uses 'media' or 'fax_media' as collection key.
            assertTrue(result.containsKey("media") || result.containsKey("fax_media"),
                    "expected 'media' or 'fax_media' key, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_get_to_fax_media")
        void journalRecordsGetToFaxMedia() {
            client.compat().faxes().listMedia("FX_LM_X");

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Faxes/FX_LM_X/Media",
                    j.path);
        }
    }

    @Nested
    @DisplayName("CompatFaxes.getMedia")
    class FaxesGetMedia {

        @Test
        @DisplayName("returns_fax_media_resource")
        void returnsFaxMediaResource() {
            Map<String, Object> result = client.compat().faxes().getMedia("FX_GM", "ME_GM");
            assertNotNull(result);
            // Fax media carries content_type + sid + fax_sid.
            assertTrue(result.containsKey("content_type") || result.containsKey("sid"),
                    "expected content_type/sid, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_get_to_specific_media")
        void journalRecordsGetToSpecificMedia() {
            client.compat().faxes().getMedia("FX_G", "ME_G");

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Faxes/FX_G/Media/ME_G",
                    j.path);
        }
    }

    @Nested
    @DisplayName("CompatFaxes.deleteMedia")
    class FaxesDeleteMedia {

        @Test
        @DisplayName("no_exception_on_delete")
        void noExceptionOnDelete() {
            Map<String, Object> result = client.compat().faxes().deleteMedia("FX_DM", "ME_DM");
            assertNotNull(result);
        }

        @Test
        @DisplayName("journal_records_delete")
        void journalRecordsDelete() {
            client.compat().faxes().deleteMedia("FX_D", "ME_D");

            MockTest.JournalEntry j = mock.last();
            assertEquals("DELETE", j.method);
            assertEquals(
                    "/api/laml/2010-04-01/Accounts/test_proj/Faxes/FX_D/Media/ME_D",
                    j.path);
        }
    }
}
