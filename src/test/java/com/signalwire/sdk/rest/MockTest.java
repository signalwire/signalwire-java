/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * MockTest is the Java port of the porting-sdk mock_signalwire HTTP-server
 * harness. It mirrors the Go pilot at
 * {@code signalwire-go/pkg/rest/internal/mocktest/mocktest.go} and the
 * Python conftest fixtures (signalwire_client + mock).
 *
 * <p>Lifecycle is per-process: the first {@link #newClient()} call probes
 * {@code http://127.0.0.1:<port>/__mock__/health} and either confirms a
 * running server or starts one as a detached subprocess. Each test gets a
 * freshly reset journal/scenario state (callers should invoke
 * {@link Harness#reset()} at the top of each test).
 *
 * <p>Default port is 8767 (Java's slot in the parallel rollout); override
 * via {@code MOCK_SIGNALWIRE_PORT} in the environment.
 */
public final class MockTest {

    private static final Gson GSON = new Gson();
    private static final int DEFAULT_PORT = 8767;
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private static final Object STATE_LOCK = new Object();
    private static volatile Harness sharedHarness;
    private static volatile Throwable startupFailure;
    private static volatile Process mockProcess;

    private MockTest() {
        // static helper
    }

    /**
     * Wraps the live mock server and exposes journal/scenario controls.
     */
    public static final class Harness {

        private final String url;
        private final int port;
        private final java.net.http.HttpClient http;

        private Harness(String url, int port) {
            this.url = url;
            this.port = port;
            this.http = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .build();
        }

        public String url() {
            return url;
        }

        public int port() {
            return port;
        }

        /**
         * Returns every entry recorded since the last reset, in arrival order.
         */
        public List<JournalEntry> journal() {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/__mock__/journal"))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            try {
                HttpResponse<String> resp = http.send(req,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() != 200) {
                    throw new IllegalStateException(
                            "MockTest: GET /__mock__/journal returned " + resp.statusCode());
                }
                return GSON.fromJson(resp.body(),
                        new TypeToken<List<JournalEntry>>() {}.getType());
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("MockTest: journal fetch failed: " + e, e);
            }
        }

        /**
         * Returns the most recent journal entry. Throws if the journal is
         * empty — every test that exercises the SDK should produce at least
         * one entry.
         */
        public JournalEntry last() {
            List<JournalEntry> entries = journal();
            if (entries == null || entries.isEmpty()) {
                throw new AssertionError(
                        "MockTest: journal is empty - the SDK call did not reach the mock server");
            }
            return entries.get(entries.size() - 1);
        }

        /**
         * Clears journal + scenarios on the mock server.
         */
        public void reset() {
            postNoBody("/__mock__/journal/reset");
            postNoBody("/__mock__/scenarios/reset");
        }

        /**
         * Stages a one-shot response override for the named operation. The
         * status + body returned here will be served the next time the route
         * is hit; subsequent hits fall back to spec synthesis.
         */
        public void scenarioSet(String operationId, int status, Map<String, Object> body) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("status", status);
            payload.put("response", body);
            String json = GSON.toJson(payload);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/__mock__/scenarios/" + operationId))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<String> resp = http.send(req,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() / 100 != 2) {
                    throw new IllegalStateException(
                            "MockTest: scenarioSet(" + operationId + ") returned " + resp.statusCode());
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("MockTest: scenarioSet failed: " + e, e);
            }
        }

        private void postNoBody(String path) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + path))
                    .timeout(HTTP_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            try {
                http.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("MockTest: " + path + " failed: " + e, e);
            }
        }
    }

    /**
     * Lightweight view of a request the mock server recorded. Mirrors the
     * dataclass in {@code mock_signalwire.journal.JournalEntry}.
     */
    public static final class JournalEntry {
        public double timestamp;
        public String method;
        public String path;
        public Map<String, List<String>> queryParams;
        public Map<String, String> headers;
        public Object body;
        public String matchedRoute;
        public Integer responseStatus;

        // Gson maps via @SerializedName equivalents below; field names use
        // the JSON keys directly.

        // Matching the JSON keys from the mock server. Gson by default uses
        // exact field-name matching, so we provide explicit accessors and let
        // the FieldNamingPolicy keep them in snake_case via reflection helpers.

        @com.google.gson.annotations.SerializedName("query_params")
        private Map<String, List<String>> _queryParams;

        @com.google.gson.annotations.SerializedName("matched_route")
        private String _matchedRoute;

        @com.google.gson.annotations.SerializedName("response_status")
        private Integer _responseStatus;

        /** Returns the JSON body coerced to a string-keyed map, or null. */
        @SuppressWarnings("unchecked")
        public Map<String, Object> bodyMap() {
            if (body instanceof Map) {
                return (Map<String, Object>) body;
            }
            return null;
        }

        public Map<String, List<String>> getQueryParams() {
            return _queryParams != null ? _queryParams : queryParams;
        }

        public String getMatchedRoute() {
            return _matchedRoute != null ? _matchedRoute : matchedRoute;
        }

        public Integer getResponseStatus() {
            return _responseStatus != null ? _responseStatus : responseStatus;
        }
    }

    /**
     * Returns (RestClient, Harness) for a single test. The client is a real
     * RestClient pointed at the local mock server with project=test_proj and
     * token=test_tok — matching the Python signalwire_client fixture.
     */
    public static Bound newClient() {
        Harness h = ensureServer();
        h.reset();
        RestClient client = RestClient.withBaseUrl(h.url(), "test_proj", "test_tok");
        return new Bound(client, h);
    }

    /**
     * Tuple of the RestClient and harness handed back to a test.
     */
    public static final class Bound {
        public final RestClient client;
        public final Harness harness;

        Bound(RestClient client, Harness harness) {
            this.client = Objects.requireNonNull(client);
            this.harness = Objects.requireNonNull(harness);
        }
    }

    // ── Server lifecycle ──────────────────────────────────────────────

    private static Harness ensureServer() {
        Harness existing = sharedHarness;
        if (existing != null) {
            return existing;
        }
        synchronized (STATE_LOCK) {
            if (sharedHarness != null) {
                return sharedHarness;
            }
            if (startupFailure != null) {
                throw new IllegalStateException(
                        "MockTest: previous startup failed: " + startupFailure.getMessage(),
                        startupFailure);
            }
            int port = resolvePort();
            String base = "http://127.0.0.1:" + port;
            java.net.http.HttpClient probeClient = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            if (probeHealth(probeClient, base)) {
                Harness h = new Harness(base, port);
                sharedHarness = h;
                return h;
            }
            try {
                Process p = spawnMockServer(port);
                mockProcess = p;
                long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT.toMillis();
                while (System.currentTimeMillis() < deadline) {
                    if (probeHealth(probeClient, base)) {
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try { p.destroy(); } catch (Exception ignored) {}
                        }, "MockTestShutdown"));
                        Harness h = new Harness(base, port);
                        sharedHarness = h;
                        return h;
                    }
                    if (!p.isAlive()) {
                        startupFailure = new IllegalStateException(
                                "mock_signalwire process exited before becoming ready (exit " + p.exitValue() + ")");
                        throw (IllegalStateException) startupFailure;
                    }
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("MockTest: interrupted while waiting for mock", e);
                    }
                }
                p.destroy();
                startupFailure = new IllegalStateException(
                        "MockTest: `python -m mock_signalwire` did not become ready within "
                                + STARTUP_TIMEOUT + " on port " + port);
                throw (IllegalStateException) startupFailure;
            } catch (IOException e) {
                startupFailure = new IllegalStateException(
                        "MockTest: failed to spawn `python -m mock_signalwire`: " + e
                                + " (set MOCK_SIGNALWIRE_PORT to use a pre-running instance)", e);
                throw (IllegalStateException) startupFailure;
            }
        }
    }

    private static int resolvePort() {
        String raw = System.getenv("MOCK_SIGNALWIRE_PORT");
        if (raw != null && !raw.isBlank()) {
            try {
                int p = Integer.parseInt(raw.trim());
                if (p > 0) {
                    return p;
                }
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return DEFAULT_PORT;
    }

    private static Process spawnMockServer(int port) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "python3", "-m", "mock_signalwire",
                "--host", "127.0.0.1",
                "--port", String.valueOf(port),
                "--log-level", "error"
        );
        // Detach by routing IO to /dev/null so the child does not block on
        // pipe buffers when the test JVM exits.
        Path devnull = Path.of("/dev/null");
        if (Files.exists(devnull)) {
            pb.redirectInput(devnull.toFile());
            pb.redirectOutput(devnull.toFile());
            pb.redirectError(devnull.toFile());
        } else {
            pb.redirectErrorStream(true);
        }
        return pb.start();
    }

    private static boolean probeHealth(java.net.http.HttpClient client, String base) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(base + "/__mock__/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        try {
            HttpResponse<InputStream> resp = client.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                drain(resp.body());
                return false;
            }
            byte[] body = resp.body().readAllBytes();
            String text = new String(body, StandardCharsets.UTF_8);
            // The health endpoint always emits a JSON object containing
            // "specs_loaded"; treat any other shape as a probe failure.
            return text.contains("\"specs_loaded\"");
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void drain(InputStream is) {
        if (is == null) return;
        try {
            is.readAllBytes();
        } catch (IOException ignored) {
            // best-effort
        }
    }

    // Test-only escape hatch for inspecting the active harness without
    // reinvoking newClient (e.g., to assert lifecycle behavior).
    static Optional<Harness> sharedHarnessForTesting() {
        return Optional.ofNullable(sharedHarness);
    }
}
