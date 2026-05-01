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
 * signalwire-python/tests/unit/rest/test_compat_queues.py.
 *
 * <p>Covers {@code CompatQueues}: update + member operations.
 */
class CompatQueuesMockTest {

    private static final String QUEUES_BASE =
            "/api/laml/2010-04-01/Accounts/test_proj/Queues";

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
    @DisplayName("CompatQueues.update")
    class Update {

        @Test
        @DisplayName("returns_queue_resource")
        void returnsQueue() {
            Map<String, Object> result = client.compat().queues().update(
                    "QU_U", kw("FriendlyName", "updated"));
            assertNotNull(result);
            assertTrue(result.containsKey("friendly_name") || result.containsKey("sid"),
                    "expected friendly_name/sid, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_post_with_friendly_name")
        void journalRecordsPost() {
            client.compat().queues().update(
                    "QU_UU", kw("FriendlyName", "renamed", "MaxSize", 200));
            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(QUEUES_BASE + "/QU_UU", j.path);
            Map<String, Object> body = j.bodyMap();
            assertNotNull(body);
            assertEquals("renamed", body.get("FriendlyName"));
            assertEquals(200, ((Number) body.get("MaxSize")).intValue());
        }
    }

    @Nested
    @DisplayName("CompatQueues.listMembers")
    class ListMembers {

        @Test
        @DisplayName("returns_paginated_members")
        void returnsMembers() {
            Map<String, Object> result = client.compat().queues().listMembers("QU_LM");
            assertNotNull(result);
            assertTrue(result.containsKey("queue_members"),
                    "expected 'queue_members' key, got " + result.keySet());
            assertTrue(result.get("queue_members") instanceof List);
        }

        @Test
        @DisplayName("journal_records_get_to_members")
        void journalRecordsGet() {
            client.compat().queues().listMembers("QU_LMX");
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(QUEUES_BASE + "/QU_LMX/Members", j.path);
        }
    }

    @Nested
    @DisplayName("CompatQueues.getMember")
    class GetMember {

        @Test
        @DisplayName("returns_member_resource")
        void returnsMember() {
            Map<String, Object> result = client.compat().queues().getMember(
                    "QU_GM", "CA_GM");
            assertNotNull(result);
            assertTrue(result.containsKey("call_sid") || result.containsKey("queue_sid"),
                    "expected call_sid/queue_sid, got " + result.keySet());
        }

        @Test
        @DisplayName("journal_records_get_to_specific_member")
        void journalRecordsGet() {
            client.compat().queues().getMember("QU_GMX", "CA_GMX");
            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals(QUEUES_BASE + "/QU_GMX/Members/CA_GMX", j.path);
        }
    }

    @Nested
    @DisplayName("CompatQueues.dequeueMember")
    class DequeueMember {

        @Test
        @DisplayName("returns_member_resource")
        void returnsMember() {
            Map<String, Object> result = client.compat().queues().dequeueMember(
                    "QU_DM", "CA_DM", kw("Url", "https://a.b"));
            assertNotNull(result);
            assertTrue(result.containsKey("call_sid") || result.containsKey("queue_sid"));
        }

        @Test
        @DisplayName("journal_records_post_with_url")
        void journalRecordsPost() {
            client.compat().queues().dequeueMember(
                    "QU_DMX", "CA_DMX",
                    kw("Url", "https://a.b/url", "Method", "POST"));
            MockTest.JournalEntry j = mock.last();
            assertEquals("POST", j.method);
            assertEquals(QUEUES_BASE + "/QU_DMX/Members/CA_DMX", j.path);
            Map<String, Object> body = j.bodyMap();
            assertNotNull(body);
            assertEquals("https://a.b/url", body.get("Url"));
            assertEquals("POST", body.get("Method"));
        }
    }
}
