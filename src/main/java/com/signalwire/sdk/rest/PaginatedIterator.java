/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Iterator that walks paged REST responses by following the
 * {@code links.next} cursor.
 *
 * <p>Mirrors {@code signalwire.rest._pagination.PaginatedIterator}: the
 * constructor records the {@code http} client, path, query params, and the
 * data-list key without performing an HTTP fetch. Each call to
 * {@link #next()} returns the next item from the buffered page; when the
 * buffer is exhausted the iterator follows {@code links.next}, parses the
 * URL query into the next request's params, and fetches the next page.
 *
 * <p>Iteration terminates when the buffer is empty and a fetched response
 * either lacks a {@code links.next} cursor or returns an empty data list.
 */
public final class PaginatedIterator implements Iterator<Map<String, Object>>,
        Iterable<Map<String, Object>> {

    private final HttpClient http;
    private final String path;
    private Map<String, String> params;
    private final String dataKey;

    private final List<Map<String, Object>> items = new ArrayList<>();
    private int index = 0;
    private boolean done = false;

    public PaginatedIterator(HttpClient http, String path) {
        this(http, path, null, "data");
    }

    public PaginatedIterator(HttpClient http, String path, Map<String, String> params) {
        this(http, path, params, "data");
    }

    public PaginatedIterator(HttpClient http, String path,
                             Map<String, String> params, String dataKey) {
        this.http = http;
        this.path = path;
        this.params = params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();
        this.dataKey = dataKey != null ? dataKey : "data";
    }

    @Override
    public PaginatedIterator iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        while (index >= items.size()) {
            if (done) {
                return false;
            }
            fetchNext();
        }
        return index < items.size();
    }

    @Override
    public Map<String, Object> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        Map<String, Object> item = items.get(index++);
        return item;
    }

    /** Test/inspection helpers — package-private for assertions. */
    HttpClient httpClient() { return http; }
    String path() { return path; }
    Map<String, String> params() { return Collections.unmodifiableMap(params); }
    String dataKey() { return dataKey; }
    int index() { return index; }
    List<Map<String, Object>> items() { return Collections.unmodifiableList(items); }
    boolean done() { return done; }

    @SuppressWarnings("unchecked")
    private void fetchNext() {
        Map<String, Object> resp = params.isEmpty()
                ? http.get(path)
                : http.get(path, params);

        Object dataObj = resp != null ? resp.get(dataKey) : null;
        List<Map<String, Object>> data;
        if (dataObj instanceof List) {
            data = new ArrayList<>();
            for (Object o : (List<?>) dataObj) {
                if (o instanceof Map) {
                    data.add((Map<String, Object>) o);
                }
            }
        } else {
            data = Collections.emptyList();
        }
        items.addAll(data);

        Object linksObj = resp != null ? resp.get("links") : null;
        Map<String, Object> links = linksObj instanceof Map ? (Map<String, Object>) linksObj : null;
        Object nextObj = links != null ? links.get("next") : null;
        String nextUrl = nextObj instanceof String ? (String) nextObj : null;
        if (nextUrl != null && !nextUrl.isEmpty() && !data.isEmpty()) {
            params = parseQuery(nextUrl);
        } else {
            done = true;
        }
    }

    /**
     * Parse the query string of {@code nextUrl} into a flat
     * {@code key -> value} map. Multi-valued keys collapse to the last
     * occurrence to match Python's behaviour
     * ({@code v[0] if len(v) == 1 else v}, but flattened to a single string).
     */
    static Map<String, String> parseQuery(String nextUrl) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            URI uri = URI.create(nextUrl);
            String query = uri.getRawQuery();
            if (query == null || query.isEmpty()) {
                return out;
            }
            for (String part : query.split("&")) {
                if (part.isEmpty()) continue;
                int eq = part.indexOf('=');
                String key;
                String value;
                if (eq < 0) {
                    key = URLDecoder.decode(part, StandardCharsets.UTF_8);
                    value = "";
                } else {
                    key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
                    value = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                }
                out.put(key, value);
            }
        } catch (IllegalArgumentException ignored) {
            // best-effort: treat as no cursor
        }
        return out;
    }
}
