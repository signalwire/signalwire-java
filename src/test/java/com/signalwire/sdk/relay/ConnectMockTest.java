/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-relay-backed unit tests translated from
 * {@code signalwire-python/tests/unit/relay/test_connect_mock.py}.
 *
 * <p>Each test boots the shared mock_relay WebSocket server and drives the
 * actual {@link RelayClient}. Asserts on:
 *
 * <ol>
 *   <li>Behavioral: SDK state after connect (protocol, isConnected, etc.).</li>
 *   <li>Wire shape: the journal entry the mock recorded.</li>
 * </ol>
 */
class ConnectMockTest {

    private RelayClient client;
    private RelayMockTest.Harness mock;

    @BeforeEach
    void setUp() {
        // Use the harness alone — each test that needs an authenticated
        // client will construct it explicitly so we can vary the contexts /
        // creds per test.
        this.mock = RelayMockTest.harness();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            try {
                client.disconnect();
            } catch (Exception ignored) {
                // best-effort
            }
            client = null;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private RelayClient buildAndConnect(String project, String token, List<String> contexts) {
        RelayClient c = RelayClient.builder()
                .project(project)
                .token(token)
                .space(mock.wsUrl())
                .contexts(contexts)
                .build();
        c.connect(10_000);
        // Wait briefly for the SDK to register protocol after the response.
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        return c;
    }

    // ── Connect — happy path ────────────────────────────────────────

    @Test
    @DisplayName("connect() succeeds and relay_protocol is non-empty")
    void connectReturnsProtocolString() {
        client = buildAndConnect("test_proj", "test_tok", List.of("default"));
        assertTrue(client.isConnected(), "client should be connected");
        assertTrue(client.getRelayProtocol().startsWith("signalwire_"),
                "unexpected protocol: " + client.getRelayProtocol());
    }

    @Test
    @DisplayName("journal records exactly one signalwire.connect frame")
    void journalRecordsConnectFrame() {
        client = buildAndConnect("test_proj", "test_tok", List.of("default"));
        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_CONNECT);
        assertEquals(1, entries.size(),
                "expected 1 connect; got " + entries.size());
    }

    @Test
    @DisplayName("connect frame carries the configured project and token")
    void connectFrameCarriesProjectAndToken() {
        client = buildAndConnect("test_proj", "test_tok", List.of("default"));
        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_CONNECT);
        assertEquals(1, entries.size());
        Map<String, Object> auth = nestedAuth(entries.get(0));
        assertEquals("test_proj", auth.get("project"));
        assertEquals("test_tok", auth.get("token"));
    }

    @Test
    @DisplayName("connect frame carries the configured contexts")
    void connectFrameCarriesContexts() {
        client = buildAndConnect("test_proj", "test_tok", List.of("default"));
        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_CONNECT);
        Map<String, Object> params = entries.get(0).params();
        assertEquals(List.of("default"), params.get("contexts"));
    }

    @Test
    @DisplayName("connect frame carries SDK agent string and protocol version")
    void connectFrameCarriesAgentAndVersion() {
        client = buildAndConnect("test_proj", "test_tok", List.of("default"));
        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_CONNECT);
        Map<String, Object> params = entries.get(0).params();
        assertEquals(Constants.SDK_AGENT, params.get("agent"));
        Map<?, ?> version = (Map<?, ?>) params.get("version");
        assertNotNull(version);
        // Gson decodes ints as doubles in Object maps.
        assertEquals((double) Constants.PROTOCOL_MAJOR,
                ((Number) version.get("major")).doubleValue());
        assertEquals((double) Constants.PROTOCOL_MINOR,
                ((Number) version.get("minor")).doubleValue());
        assertEquals((double) Constants.PROTOCOL_REVISION,
                ((Number) version.get("revision")).doubleValue());
    }

    @Test
    @DisplayName("connect frame sets event_acks=true")
    void connectFrameEventAcksTrue() {
        client = buildAndConnect("test_proj", "test_tok", List.of("default"));
        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_CONNECT);
        Map<String, Object> params = entries.get(0).params();
        assertEquals(Boolean.TRUE, params.get("event_acks"));
    }

    @Test
    @DisplayName("RelayMockTest.last() returns the most recent entry")
    void lastReturnsRecentEntry() {
        client = buildAndConnect("test_proj", "test_tok", List.of("default"));
        RelayMockTest.JournalEntry last = mock.last();
        assertNotNull(last);
        // Could be the connect or its response — either is fine, both should
        // have a frame.
        assertNotNull(last.frame);
    }

    // ── Reconnect with protocol → session_restored ──────────────────

    @Test
    @DisplayName("Reconnect with stored protocol carries protocol on the wire")
    void reconnectIncludesProtocolInFrame() {
        // First client establishes protocol.
        RelayClient c1 = RelayClient.builder()
                .project("p").token("t").space(mock.wsUrl()).contexts(List.of("c1"))
                .build();
        c1.connect(10_000);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        String issuedProto = c1.getRelayProtocol();
        c1.disconnect();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        // Reset journal so we only see the resume connect frame.
        mock.reset();

        // Second client uses the issued protocol.
        RelayClient c2 = RelayClient.builder()
                .project("p").token("t").space(mock.wsUrl()).contexts(List.of("c1"))
                .build();
        c2.setRelayProtocol(issuedProto);
        try {
            c2.connect(10_000);
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            // The resume connect frame must carry the protocol field.
            List<RelayMockTest.JournalEntry> resumeFrames =
                    mock.journalRecv(Constants.METHOD_CONNECT);
            assertFalse(resumeFrames.isEmpty(),
                    "no signalwire.connect frame after reconnect");
            boolean foundProto = false;
            for (RelayMockTest.JournalEntry e : resumeFrames) {
                Object proto = e.params().get("protocol");
                if (issuedProto.equals(proto)) {
                    foundProto = true;
                    break;
                }
            }
            assertTrue(foundProto,
                    "no resume connect carried protocol=" + issuedProto);
        } finally {
            c2.disconnect();
        }
    }

    @Test
    @DisplayName("Reconnect preserves protocol value")
    void reconnectPreservesProtocolValue() {
        RelayClient c1 = RelayClient.builder()
                .project("p").token("t").space(mock.wsUrl())
                .build();
        c1.connect(10_000);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        String issued = c1.getRelayProtocol();
        c1.disconnect();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        RelayClient c2 = RelayClient.builder()
                .project("p").token("t").space(mock.wsUrl())
                .build();
        c2.setRelayProtocol(issued);
        try {
            c2.connect(10_000);
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            assertEquals(issued, c2.getRelayProtocol());
        } finally {
            c2.disconnect();
        }
    }

    // ── Auth failure paths ──────────────────────────────────────────

    @Test
    @DisplayName("Builder rejects empty creds (no JWT, no project/token)")
    void builderRejectsEmptyCreds() {
        // Java equivalent of "RelayClient(project='', token='')" — the SDK
        // throws NPE when project/token are null. Empty strings get past
        // Objects.requireNonNull but the wire layer handles them.
        // Per the existing builder contract, project=null throws NPE.
        assertThrows(NullPointerException.class, () ->
                RelayClient.builder().space("anywhere").build()
        );
    }

    @Test
    @DisplayName("Mock returns AUTH_REQUIRED for empty-creds raw connect")
    void unauthRawConnectRejectedByMock() throws Exception {
        // Bypass the SDK and send a raw connect frame with empty creds.
        // The mock returns an error with data.signalwire_error_code=AUTH_REQUIRED.
        HttpClient http = HttpClient.newHttpClient();
        WsHelper helper = new WsHelper();
        WebSocket ws = http.newWebSocketBuilder()
                .buildAsync(URI.create(mock.wsUrl()), helper)
                .get(5, TimeUnit.SECONDS);
        try {
            String reqId = UUID.randomUUID().toString();
            Map<String, Object> connectFrame = buildEmptyAuthConnect(reqId);
            ws.sendText(new Gson().toJson(connectFrame), true)
                    .get(5, TimeUnit.SECONDS);
            String resp = helper.awaitMessage(5, TimeUnit.SECONDS);
            assertNotNull(resp);
            Map<String, Object> respMap = new Gson().fromJson(resp,
                    new TypeToken<Map<String, Object>>() {}.getType());
            Map<?, ?> err = (Map<?, ?>) respMap.get("error");
            assertNotNull(err, "expected error from mock; got: " + respMap);
            Map<?, ?> data = (Map<?, ?>) err.get("data");
            assertNotNull(data, "expected error.data envelope; got: " + err);
            assertEquals("AUTH_REQUIRED", data.get("signalwire_error_code"));
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "ok").get(2, TimeUnit.SECONDS);
        }
    }

    private static Map<String, Object> buildEmptyAuthConnect(String reqId) {
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("major", Constants.PROTOCOL_MAJOR);
        version.put("minor", Constants.PROTOCOL_MINOR);
        version.put("revision", Constants.PROTOCOL_REVISION);
        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("project", "");
        auth.put("token", "");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("version", version);
        params.put("agent", Constants.SDK_AGENT);
        params.put("authentication", auth);
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", Constants.JSONRPC_VERSION);
        frame.put("id", reqId);
        frame.put("method", Constants.METHOD_CONNECT);
        frame.put("params", params);
        return frame;
    }

    // ── Connect — JWT path ──────────────────────────────────────────

    @Test
    @DisplayName("JWT-only connect carries jwt_token on the wire (no project/token)")
    void connectWithJwtCarriesJwtOnWire() {
        RelayClient c = RelayClient.builder()
                .jwtToken("fake-jwt-eyJ.AaaA.BbB")
                .space(mock.wsUrl())
                .build();
        client = c;
        c.connect(10_000);
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        List<RelayMockTest.JournalEntry> entries =
                mock.journalRecv(Constants.METHOD_CONNECT);
        assertEquals(1, entries.size());
        Map<String, Object> auth = nestedAuth(entries.get(0));
        assertEquals("fake-jwt-eyJ.AaaA.BbB", auth.get("jwt_token"));
        // JWT path doesn't include project/token.
        Object tok = auth.get("token");
        assertTrue(tok == null || (tok instanceof String && ((String) tok).isEmpty()),
                "JWT path should not include token; got: " + tok);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedAuth(RelayMockTest.JournalEntry e) {
        Map<String, Object> p = e.params();
        assertNotNull(p, "frame.params must not be null");
        Object auth = p.get("authentication");
        assertTrue(auth instanceof Map, "authentication must be a map");
        return (Map<String, Object>) auth;
    }

    /**
     * Tiny WebSocket listener that buffers incoming text messages onto a
     * synchronous queue so a test can pull the next message with a timeout.
     */
    private static final class WsHelper implements WebSocket.Listener {
        private final java.util.concurrent.LinkedBlockingQueue<String> messages
                = new java.util.concurrent.LinkedBlockingQueue<>();
        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                messages.offer(partial.toString());
                partial.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
        }

        String awaitMessage(long timeout, TimeUnit unit) throws InterruptedException {
            return messages.poll(timeout, unit);
        }
    }
}
