/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.logging.Logger;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

/**
 * HTTP client for the SignalWire REST API.
 * <p>
 * Uses {@code java.net.http.HttpClient} (JDK 11+ built-in) with Basic Auth
 * and JSON content types. Provides low-level GET, POST, PUT, DELETE methods
 * used by {@link CrudResource} and namespace classes.
 */
public class HttpClient {

    private static final Logger log = Logger.getLogger(HttpClient.class);
    private static final Gson gson = new Gson();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final String authHeader;
    private final java.net.http.HttpClient httpClient;

    /**
     * Create an HTTP client.
     *
     * @param space SignalWire space (e.g., "example.signalwire.com")
     * @param project project ID used as Basic Auth username
     * @param token API token used as Basic Auth password
     */
    public HttpClient(String space, String project, String token) {
        this.baseUrl = "https://" + space + "/api";
        String credentials = project + ":" + token;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
        this.httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .build();
    }

    /**
     * Create an HTTP client with an explicit base URL (e.g., plain HTTP for
     * local integration tests, or to point a {@link RestClient} at an audit
     * fixture). Production callers use the {@code (space, project, token)}
     * constructor instead.
     *
     * @param baseUrl fully qualified base URL ending in {@code /api}
     * @param project project ID used as Basic Auth username
     * @param token API token used as Basic Auth password
     * @return a configured HTTP client
     */
    public static HttpClient withBaseUrl(String baseUrl, String project, String token) {
        return new HttpClient(baseUrl, project, token, null);
    }

    private HttpClient(String baseUrl, String project, String token, Void marker) {
        this.baseUrl = baseUrl;
        String credentials = project + ":" + token;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
        this.httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .build();
    }

    // ── Public methods ───────────────────────────────────────────────

    /**
     * GET request, returns parsed JSON as a Map.
     */
    public Map<String, Object> get(String path) {
        return get(path, null);
    }

    /**
     * GET request with query parameters.
     */
    public Map<String, Object> get(String path, Map<String, String> queryParams) {
        String url = buildUrl(path, queryParams);
        HttpRequest request = requestBuilder(url)
                .GET()
                .build();
        return executeRequest("GET", path, request);
    }

    /**
     * POST request with JSON body.
     */
    public Map<String, Object> post(String path, Map<String, Object> body) {
        String url = buildUrl(path, null);
        String json = body != null ? gson.toJson(body) : "{}";
        HttpRequest request = requestBuilder(url)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .build();
        return executeRequest("POST", path, request);
    }

    /**
     * PUT request with JSON body.
     */
    public Map<String, Object> put(String path, Map<String, Object> body) {
        String url = buildUrl(path, null);
        String json = body != null ? gson.toJson(body) : "{}";
        HttpRequest request = requestBuilder(url)
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .build();
        return executeRequest("PUT", path, request);
    }

    /**
     * DELETE request.
     */
    public Map<String, Object> delete(String path) {
        String url = buildUrl(path, null);
        HttpRequest request = requestBuilder(url)
                .DELETE()
                .build();
        return executeRequest("DELETE", path, request);
    }

    /**
     * Get the base URL.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    // ── Internal ─────────────────────────────────────────────────────

    private HttpRequest.Builder requestBuilder(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .timeout(DEFAULT_TIMEOUT);
    }

    private Map<String, Object> executeRequest(String method, String path, HttpRequest request) {
        try {
            log.debug("%s %s", method, request.uri());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String body = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                if (body == null || body.isEmpty()) {
                    return Collections.emptyMap();
                }
                return gson.fromJson(body,
                        new TypeToken<Map<String, Object>>() {}.getType());
            }

            throw new RestError(statusCode, method, path, body);
        } catch (RestError e) {
            throw e;
        } catch (Exception e) {
            throw new RestError(0, method, path, e.getMessage(), e);
        }
    }

    private String buildUrl(String path, Map<String, String> queryParams) {
        StringBuilder url = new StringBuilder(baseUrl);
        if (path != null && !path.isEmpty()) {
            if (!path.startsWith("/")) {
                url.append("/");
            }
            url.append(path);
        }

        if (queryParams != null && !queryParams.isEmpty()) {
            url.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (!first) url.append("&");
                url.append(encodeParam(entry.getKey()))
                   .append("=")
                   .append(encodeParam(entry.getValue()));
                first = false;
            }
        }

        return url.toString();
    }

    private String encodeParam(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
