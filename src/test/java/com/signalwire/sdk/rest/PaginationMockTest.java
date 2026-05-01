/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-backed pagination tests translated from
 * signalwire-python/tests/unit/rest/test_pagination_mock.py.
 *
 * <p>{@link PaginatedIterator} is exercised end-to-end against the live
 * mock server. Scenarios are staged via the mock control plane to override
 * a stable endpoint id ({@code fabric.list_fabric_addresses}); the iterator
 * must follow the {@code links.next} cursor through pages until it is empty.
 */
class PaginationMockTest {

    // Java's HttpClient already prefixes /api, so the path passed into the
    // iterator is the bare /fabric/addresses; the journal still records the
    // full /api/fabric/addresses URL because that's what hits the wire.
    private static final String FABRIC_ADDRESSES_HTTP_PATH = "/fabric/addresses";
    private static final String FABRIC_ADDRESSES_JOURNAL_PATH = "/api/fabric/addresses";
    // The mock attaches this endpoint id to GETs of /api/fabric/addresses;
    // staging a scenario keyed off it lets us serve pre-canned bodies.
    private static final String ENDPOINT_ID = "fabric.list_fabric_addresses";

    private RestClient client;
    private MockTest.Harness mock;

    @BeforeEach
    void setUp() {
        MockTest.Bound bound = MockTest.newClient();
        this.client = bound.client;
        this.mock = bound.harness;
    }

    private static Map<String, Object> page(List<Map<String, Object>> data, String nextUrl) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("data", data);
        Map<String, Object> links = new LinkedHashMap<>();
        if (nextUrl != null) {
            links.put("next", nextUrl);
        }
        out.put("links", links);
        return out;
    }

    private static Map<String, Object> entry(String id, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        return m;
    }

    private static Map<String, Object> entry(String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        return m;
    }

    @Test
    @DisplayName("Constructor records http/path/params/data_key without fetching")
    void initState() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("page_size", "2");
        PaginatedIterator it = new PaginatedIterator(
                client.getHttpClient(), FABRIC_ADDRESSES_HTTP_PATH, params, "data");
        assertSame(client.getHttpClient(), it.httpClient());
        assertEquals(FABRIC_ADDRESSES_HTTP_PATH, it.path());
        assertEquals(params, it.params());
        assertEquals("data", it.dataKey());
        assertEquals(0, it.index());
        assertTrue(it.items().isEmpty());
        assertFalse(it.done());
        // Constructor must not have fetched anything.
        assertTrue(mock.journal().isEmpty(),
                "expected no journal entries, got " + mock.journal());
    }

    @Test
    @DisplayName("iterator() returns self; iter(it) is the same")
    void iteratorReturnsSelf() {
        PaginatedIterator it = new PaginatedIterator(
                client.getHttpClient(), FABRIC_ADDRESSES_HTTP_PATH);
        assertSame(it, it.iterator());
        assertTrue(mock.journal().isEmpty());
    }

    @Test
    @DisplayName("Walks two pages and stops on the page without links.next")
    void pagesThroughAllItems() {
        // Stage page 1 (with next cursor) and page 2 (terminal).
        mock.scenarioSet(ENDPOINT_ID, 200, page(List.of(
                entry("addr-1", "first"),
                entry("addr-2", "second")
        ), "http://example.com/api/fabric/addresses?cursor=page2"));
        mock.scenarioSet(ENDPOINT_ID, 200, page(List.of(
                entry("addr-3", "third")
        ), null));

        PaginatedIterator it = new PaginatedIterator(
                client.getHttpClient(), FABRIC_ADDRESSES_HTTP_PATH);
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> item : it) {
            ids.add((String) item.get("id"));
        }
        assertEquals(List.of("addr-1", "addr-2", "addr-3"), ids);

        // Journal must have exactly two GETs at the same path.
        List<MockTest.JournalEntry> entries = mock.journal();
        List<MockTest.JournalEntry> gets = new ArrayList<>();
        for (MockTest.JournalEntry e : entries) {
            if (FABRIC_ADDRESSES_JOURNAL_PATH.equals(e.path)) {
                gets.add(e);
            }
        }
        assertEquals(2, gets.size(),
                "expected 2 paginated GETs, got " + gets.size());
        // The second fetch carries cursor=page2 from the first response's
        // links.next URL.
        assertEquals(List.of("page2"),
                gets.get(1).getQueryParams().get("cursor"),
                "second fetch missing cursor=page2: " + gets.get(1).getQueryParams());
    }

    @Test
    @DisplayName("next() raises NoSuchElementException when done")
    void nextRaisesAfterExhaustion() {
        mock.scenarioSet(ENDPOINT_ID, 200, page(List.of(entry("only-one")), null));
        PaginatedIterator it = new PaginatedIterator(
                client.getHttpClient(), FABRIC_ADDRESSES_HTTP_PATH);
        Map<String, Object> first = it.next();
        assertEquals("only-one", first.get("id"));
        // Exhausted.
        assertThrows(NoSuchElementException.class, it::next);
    }
}
