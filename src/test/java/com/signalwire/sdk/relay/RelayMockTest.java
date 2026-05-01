/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * RelayMockTest is the Java port of the porting-sdk mock_relay WebSocket-server
 * harness. It mirrors the Python {@code _MockRelayHarness} fixture in
 * {@code signalwire-python/tests/unit/relay/conftest.py}.
 *
 * <p>Lifecycle is per-process: the first {@link #newClient()} call probes
 * {@code http://127.0.0.1:<http-port>/__mock__/health} and either confirms a
 * running server or starts one as a detached subprocess. Each test gets a
 * freshly reset journal/scenario state.
 *
 * <p>Default WebSocket port is 8777 (Java's slot in the parallel rollout) and
 * the HTTP control plane port is 9777. Override via
 * {@code MOCK_RELAY_PORT} / {@code MOCK_RELAY_HTTP_PORT} env vars.
 */
public final class RelayMockTest {

    private static final Gson GSON = new Gson();
    private static final int DEFAULT_WS_PORT = 8777;
    private static final int DEFAULT_HTTP_PORT = 9777;
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private static final Object STATE_LOCK = new Object();
    private static volatile Harness sharedHarness;
    private static volatile Throwable startupFailure;
    private static volatile Process mockProcess;

    private RelayMockTest() {
        // static helper
    }

    /**
     * Walk this repo's working directory upward looking for an adjacent
     * {@code porting-sdk/test_harness/<name>/<name>/__init__.py}. The
     * adjacency contract is "porting-sdk lives next to signalwire-java in
     * ~/src/", so a fresh clone of either repo can find the mock harness
     * with no prior {@code pip install -e}.
     */
    static String discoverPortingSdkPackage(String name) {
        String userDir = System.getProperty("user.dir");
        if (userDir == null || userDir.isEmpty()) {
            return null;
        }
        Path dir = Path.of(userDir).toAbsolutePath();
        while (dir != null) {
            Path parent = dir.getParent();
            if (parent == null) break;
            Path candidate = parent.resolve("porting-sdk").resolve("test_harness").resolve(name);
            Path init = candidate.resolve(name).resolve("__init__.py");
            if (Files.isRegularFile(init)) {
                return candidate.toString();
            }
            dir = parent;
        }
        return null;
    }

    /**
     * Wraps the live mock relay server and exposes journal/scenario controls.
     */
    public static final class Harness {

        private final String httpUrl;
        private final String wsUrl;
        private final int wsPort;
        private final int httpPort;
        private final java.net.http.HttpClient http;

        private Harness(String httpUrl, String wsUrl, int wsPort, int httpPort) {
            this.httpUrl = httpUrl;
            this.wsUrl = wsUrl;
            this.wsPort = wsPort;
            this.httpPort = httpPort;
            this.http = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(HTTP_TIMEOUT)
                    .build();
        }

        public String httpUrl() {
            return httpUrl;
        }

        public String wsUrl() {
            return wsUrl;
        }

        public String relayHost() {
            return "127.0.0.1:" + wsPort;
        }

        public int wsPort() {
            return wsPort;
        }

        public int httpPort() {
            return httpPort;
        }

        // ── Journal ──────────────────────────────────────────────────

        /**
         * Returns every entry recorded since the last reset, in arrival order.
         */
        public List<JournalEntry> journal() {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(httpUrl + "/__mock__/journal"))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            try {
                HttpResponse<String> resp = http.send(req,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() != 200) {
                    throw new IllegalStateException(
                            "RelayMockTest: GET /__mock__/journal returned " + resp.statusCode());
                }
                return GSON.fromJson(resp.body(),
                        new TypeToken<List<JournalEntry>>() {}.getType());
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("RelayMockTest: journal fetch failed: " + e, e);
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
                        "RelayMockTest: journal is empty - the SDK call did not reach the mock server");
            }
            return entries.get(entries.size() - 1);
        }

        /**
         * Returns inbound (SDK→server) entries, optionally filtered by method.
         */
        public List<JournalEntry> journalRecv(String method) {
            List<JournalEntry> result = new ArrayList<>();
            for (JournalEntry e : journal()) {
                if (!"recv".equals(e.direction)) continue;
                if (method != null && !method.equals(e.method)) continue;
                result.add(e);
            }
            return result;
        }

        /**
         * Returns server→SDK entries, optionally filtered by inner event_type.
         */
        @SuppressWarnings("unchecked")
        public List<JournalEntry> journalSend(String eventType) {
            List<JournalEntry> result = new ArrayList<>();
            for (JournalEntry e : journal()) {
                if (!"send".equals(e.direction)) continue;
                if (eventType == null) {
                    result.add(e);
                    continue;
                }
                Object frame = e.frame;
                if (frame instanceof Map) {
                    Map<String, Object> m = (Map<String, Object>) frame;
                    Object params = m.get("params");
                    if (!"signalwire.event".equals(m.get("method"))) continue;
                    if (params instanceof Map
                            && eventType.equals(((Map<String, Object>) params).get("event_type"))) {
                        result.add(e);
                    }
                }
            }
            return result;
        }

        /**
         * Clears journal + scenarios on the mock server.
         */
        public void reset() {
            postNoBody("/__mock__/journal/reset");
            postNoBody("/__mock__/scenarios/reset");
        }

        // ── Scenario plumbing ──────────────────────────────────────

        /**
         * Queue scripted post-RPC events for {@code method} (FIFO consume-once).
         */
        public void armMethod(String method, List<Map<String, Object>> events) {
            postJson("/__mock__/scenarios/" + method, events);
        }

        /**
         * Queue a dial-dance scenario (winner state events + final dial event).
         */
        public void armDial(Map<String, Object> body) {
            postJson("/__mock__/scenarios/dial", body);
        }

        // ── Server-initiated pushes ─────────────────────────────────

        /**
         * Push a single {@code signalwire.event} (or other) frame to the SDK.
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> push(Map<String, Object> frame) {
            return push(frame, null);
        }

        /**
         * Push a frame to a specific session (or all sessions if sessionId is null).
         */
        public Map<String, Object> push(Map<String, Object> frame, String sessionId) {
            String url = "/__mock__/push";
            if (sessionId != null && !sessionId.isEmpty()) {
                url = url + "?session_id=" + sessionId;
            }
            Map<String, Object> body = new HashMap<>();
            body.put("frame", frame);
            return postJson(url, body);
        }

        /**
         * Inject an inbound call into one or every session.
         */
        public Map<String, Object> inboundCall(InboundCallSpec spec) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("from_number", spec.fromNumber);
            body.put("to_number", spec.toNumber);
            body.put("context", spec.context);
            body.put("auto_states", spec.autoStates);
            body.put("delay_ms", spec.delayMs);
            if (spec.callId != null) body.put("call_id", spec.callId);
            if (spec.sessionId != null) body.put("session_id", spec.sessionId);
            return postJson("/__mock__/inbound_call", body);
        }

        /**
         * Run a scripted timeline of pushes/sleeps/expect_recv on the server.
         */
        public Map<String, Object> scenarioPlay(List<Map<String, Object>> ops) {
            return postJson("/__mock__/scenario_play", ops);
        }

        /**
         * List active WebSocket session metadata.
         */
        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> sessions() {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(httpUrl + "/__mock__/sessions"))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            try {
                HttpResponse<String> resp = http.send(req,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                Map<String, Object> result = GSON.fromJson(resp.body(),
                        new TypeToken<Map<String, Object>>() {}.getType());
                Object list = result.get("sessions");
                if (list instanceof List) return (List<Map<String, Object>>) list;
                return new ArrayList<>();
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("RelayMockTest: sessions fetch failed: " + e, e);
            }
        }

        // ── HTTP helpers ──────────────────────────────────────────────

        @SuppressWarnings("unchecked")
        private Map<String, Object> postJson(String path, Object body) {
            String json = GSON.toJson(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(httpUrl + path))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<String> resp = http.send(req,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() / 100 != 2) {
                    throw new IllegalStateException(
                            "RelayMockTest: POST " + path + " returned " + resp.statusCode()
                                    + ": " + resp.body());
                }
                if (resp.body().isEmpty()) return new HashMap<>();
                return GSON.fromJson(resp.body(),
                        new TypeToken<Map<String, Object>>() {}.getType());
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("RelayMockTest: POST " + path + " failed: " + e, e);
            }
        }

        private void postNoBody(String path) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(httpUrl + path))
                    .timeout(HTTP_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            try {
                http.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new IllegalStateException("RelayMockTest: " + path + " failed: " + e, e);
            }
        }
    }

    /**
     * Inbound call factory spec.
     */
    public static final class InboundCallSpec {
        public String callId;
        public String fromNumber = "+15551234567";
        public String toNumber = "+15559876543";
        public String context = "default";
        public List<String> autoStates = List.of("created");
        public int delayMs = 50;
        public String sessionId;

        public InboundCallSpec callId(String id) { this.callId = id; return this; }
        public InboundCallSpec fromNumber(String n) { this.fromNumber = n; return this; }
        public InboundCallSpec toNumber(String n) { this.toNumber = n; return this; }
        public InboundCallSpec context(String c) { this.context = c; return this; }
        public InboundCallSpec autoStates(List<String> s) { this.autoStates = s; return this; }
        public InboundCallSpec delayMs(int ms) { this.delayMs = ms; return this; }
        public InboundCallSpec sessionId(String s) { this.sessionId = s; return this; }
    }

    /**
     * Lightweight view of a frame the mock server recorded. Mirrors the
     * dataclass in {@code mock_relay.journal._JournalEntry}.
     */
    public static final class JournalEntry {
        public double timestamp;
        public String direction; // "recv" | "send"
        public String method;

        @com.google.gson.annotations.SerializedName("request_id")
        public String requestId;

        public Map<String, Object> frame;

        @com.google.gson.annotations.SerializedName("connection_id")
        public String connectionId;

        @com.google.gson.annotations.SerializedName("session_id")
        public String sessionId;

        /**
         * Helper to navigate frame.params.
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> params() {
            if (frame == null) return null;
            Object p = frame.get("params");
            return p instanceof Map ? (Map<String, Object>) p : null;
        }

        /**
         * Helper to navigate frame.params.params (event-shaped).
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> innerParams() {
            Map<String, Object> p = params();
            if (p == null) return null;
            Object inner = p.get("params");
            return inner instanceof Map ? (Map<String, Object>) inner : null;
        }
    }

    /**
     * Returns (RelayClient, Harness) for a single test. The client is a real
     * RelayClient with project=test_proj and token=test_tok pointed at the
     * local mock server's WebSocket. The contexts default to ["default"].
     */
    public static Bound newClient() {
        return newClient("test_proj", "test_tok", List.of("default"));
    }

    /**
     * Returns (RelayClient, Harness) with custom credentials/contexts. The
     * client is constructed using {@link RelayClient#builder()} and pointed
     * at the mock via {@code space("ws://127.0.0.1:<port>")}. The caller is
     * responsible for invoking {@code client.run()} (or the equivalent
     * connect path) — the helper exposes both objects.
     *
     * <p>Note: like the Python harness, the journal/scenario queues are
     * reset before every newClient() call.
     */
    public static Bound newClient(String project, String token, List<String> contexts) {
        Harness h = ensureServer();
        h.reset();
        RelayClient client = RelayClient.builder()
                .project(project)
                .token(token)
                .space(h.wsUrl())
                .contexts(contexts != null ? contexts : List.of())
                .build();
        return new Bound(client, h);
    }

    /**
     * Returns just the harness (no client), useful for tests that want to
     * construct multiple clients themselves.
     */
    public static Harness harness() {
        Harness h = ensureServer();
        h.reset();
        return h;
    }

    /**
     * Tuple of the RelayClient and harness handed back to a test.
     */
    public static final class Bound implements AutoCloseable {
        public final RelayClient client;
        public final Harness harness;

        Bound(RelayClient client, Harness harness) {
            this.client = Objects.requireNonNull(client);
            this.harness = Objects.requireNonNull(harness);
        }

        @Override
        public void close() {
            try {
                client.disconnect();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
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
                        "RelayMockTest: previous startup failed: " + startupFailure.getMessage(),
                        startupFailure);
            }
            int wsPort = resolvePort("MOCK_RELAY_PORT", DEFAULT_WS_PORT);
            int httpPort = resolvePort("MOCK_RELAY_HTTP_PORT", DEFAULT_HTTP_PORT);
            String httpBase = "http://127.0.0.1:" + httpPort;
            String wsBase = "ws://127.0.0.1:" + wsPort;
            java.net.http.HttpClient probeClient = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            if (probeHealth(probeClient, httpBase)) {
                Harness h = new Harness(httpBase, wsBase, wsPort, httpPort);
                sharedHarness = h;
                return h;
            }
            try {
                Process p = spawnMockServer(wsPort, httpPort);
                mockProcess = p;
                long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT.toMillis();
                while (System.currentTimeMillis() < deadline) {
                    if (probeHealth(probeClient, httpBase)) {
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try { p.destroy(); } catch (Exception ignored) {}
                        }, "RelayMockTestShutdown"));
                        Harness h = new Harness(httpBase, wsBase, wsPort, httpPort);
                        sharedHarness = h;
                        return h;
                    }
                    if (!p.isAlive()) {
                        startupFailure = new IllegalStateException(
                                "mock_relay process exited before becoming ready (exit "
                                        + p.exitValue() + ")");
                        throw (IllegalStateException) startupFailure;
                    }
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                                "RelayMockTest: interrupted while waiting for mock", e);
                    }
                }
                p.destroy();
                startupFailure = new IllegalStateException(
                        "RelayMockTest: `python -m mock_relay` did not become ready within "
                                + STARTUP_TIMEOUT + " on http port " + httpPort
                                + " / ws port " + wsPort
                                + " (clone porting-sdk next to signalwire-java so tests can find "
                                + "porting-sdk/test_harness/mock_relay/, or pip install the "
                                + "mock_relay package)");
                throw (IllegalStateException) startupFailure;
            } catch (IOException e) {
                startupFailure = new IllegalStateException(
                        "RelayMockTest: failed to spawn `python -m mock_relay`: " + e
                                + " (set MOCK_RELAY_PORT / MOCK_RELAY_HTTP_PORT to use a "
                                + "pre-running instance)", e);
                throw (IllegalStateException) startupFailure;
            }
        }
    }

    private static int resolvePort(String envVar, int defaultPort) {
        String raw = System.getenv(envVar);
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
        return defaultPort;
    }

    private static Process spawnMockServer(int wsPort, int httpPort) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "python3", "-m", "mock_relay",
                "--host", "127.0.0.1",
                "--ws-port", String.valueOf(wsPort),
                "--http-port", String.valueOf(httpPort),
                "--log-level", "error"
        );
        // Inject porting-sdk/test_harness/mock_relay/ into PYTHONPATH so
        // `python -m mock_relay` resolves without a prior `pip install -e ...`.
        // Adjacency contract: porting-sdk next to signalwire-java in ~/src/.
        String pkgDir = discoverPortingSdkPackage("mock_relay");
        if (pkgDir != null) {
            Map<String, String> env = pb.environment();
            String existing = env.get("PYTHONPATH");
            String sep = System.getProperty("path.separator", ":");
            String newPP = (existing != null && !existing.isEmpty())
                    ? pkgDir + sep + existing
                    : pkgDir;
            env.put("PYTHONPATH", newPP);
        }

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
            // The health endpoint emits a JSON object containing
            // "schemas_loaded"; treat any other shape as a probe failure.
            return text.contains("\"schemas_loaded\"");
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

    // Test-only escape hatch for inspecting the active harness.
    static Optional<Harness> sharedHarnessForTesting() {
        return Optional.ofNullable(sharedHarness);
    }
}
