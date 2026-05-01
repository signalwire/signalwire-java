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
 * signalwire-python/tests/unit/rest/test_compat_tokens.py.
 *
 * <p>{@code CompatTokens.update} uses PATCH (not POST) — it inherits the
 * {@code BaseResource} convention rather than the Twilio-style POST.
 */
class CompatTokensMockTest {

    private static final String TOKENS_BASE =
            "/api/laml/2010-04-01/Accounts/test_proj/tokens";

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
    @DisplayName("CompatTokens.create")
    class Create {

        @Test
        @DisplayName("returns_token_resource")
        void returnsToken() {
            Map<String, Object> result = client.compat().tokens().create(
                    kw("Ttl", 3600));
            assertNotNull(result);
            assertTrue(result.containsKey("token") || result.containsKey("id"),
                    "expected token/id, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_post_with_ttl")
        void journalRecordsPost() {
            client.compat().tokens().create(kw("Ttl", 3600, "Name", "api-key"));
            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(TOKENS_BASE, j.path);
            Map<String, Object> body = j.bodyMap();
            assertNotNull(body);
            assertEquals(3600, ((Number) body.get("Ttl")).intValue());
            assertEquals("api-key", body.get("Name"));
        }
    }

    @Nested
    @DisplayName("CompatTokens.update")
    class Update {

        @Test
        @DisplayName("returns_token_resource")
        void returnsToken() {
            Map<String, Object> result = client.compat().tokens().update(
                    "TK_U", kw("Ttl", 7200));
            assertNotNull(result);
            assertTrue(result.containsKey("token") || result.containsKey("id"));
        }

        @Test
        @DisplayName("journal_records_patch_with_ttl")
        void journalRecordsPatch() {
            client.compat().tokens().update("TK_UU", kw("Ttl", 7200));
            MockTest.JournalEntry j = mock.last();
            assertEquals("PATCH", j.method);
            assertEquals(TOKENS_BASE + "/TK_UU", j.path);
            Map<String, Object> body = j.bodyMap();
            assertNotNull(body);
            assertEquals(7200, ((Number) body.get("Ttl")).intValue());
        }
    }

    @Nested
    @DisplayName("CompatTokens.delete")
    class Delete {

        @Test
        @DisplayName("no_exception_on_delete")
        void noExceptionOnDelete() {
            Map<String, Object> result = client.compat().tokens().delete("TK_D");
            assertNotNull(result);
        }

        @Test
        @DisplayName("journal_records_delete")
        void journalRecordsDelete() {
            client.compat().tokens().delete("TK_DEL");
            MockTest.JournalEntry j = mock.last();
            assertEquals("DELETE", j.method);
            assertEquals(TOKENS_BASE + "/TK_DEL", j.path);
        }
    }
}
