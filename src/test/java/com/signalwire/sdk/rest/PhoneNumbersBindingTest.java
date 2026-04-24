/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the typed phone-binding helpers and {@link PhoneCallHandler}.
 * <p>
 * Each helper is a one-line wrapper over {@code phoneNumbers().update()} —
 * the tests assert the resulting HTTP wire body exactly.
 * <p>
 * Uses a local {@link HttpServer} from the JDK to record real HTTP
 * traffic — no mocking framework, no new dependencies.
 */
class PhoneNumbersBindingTest {

    private static final Gson GSON = new Gson();

    private HttpServer server;
    private int port;
    private final List<RecordedRequest> recorded = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", new RecordingHandler(recorded));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /** Build a SignalWire HttpClient pointed at the local test server (plain HTTP). */
    private HttpClient httpClient() {
        return HttpClient.withBaseUrl("http://127.0.0.1:" + port + "/api", "proj", "tok");
    }

    private RecordedRequest lastRequest() {
        assertFalse(recorded.isEmpty(), "no HTTP request was recorded");
        return recorded.get(recorded.size() - 1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> bodyOf(RecordedRequest req) {
        if (req.body == null || req.body.isEmpty()) return Map.of();
        return GSON.fromJson(req.body, new TypeToken<Map<String, Object>>() {}.getType());
    }

    // ── Enum contract ───────────────────────────────────────────────

    @Nested
    @DisplayName("PhoneCallHandler")
    class EnumTests {

        @Test
        @DisplayName("has all 11 wire values")
        void allElevenValues() {
            Set<String> expected = Set.of(
                    "relay_script", "laml_webhooks", "laml_application",
                    "ai_agent", "call_flow", "relay_application",
                    "relay_topic", "relay_context", "relay_connector",
                    "video_room", "dialogflow"
            );
            Set<String> actual = new java.util.HashSet<>();
            for (PhoneCallHandler h : PhoneCallHandler.values()) {
                actual.add(h.wireValue());
            }
            assertEquals(expected, actual);
            assertEquals(11, PhoneCallHandler.values().length);
        }

        @Test
        @DisplayName("wire values match the enum-to-resource table")
        void wireValuesByMember() {
            assertEquals("relay_script",      PhoneCallHandler.RELAY_SCRIPT.wireValue());
            assertEquals("laml_webhooks",     PhoneCallHandler.LAML_WEBHOOKS.wireValue());
            assertEquals("laml_application",  PhoneCallHandler.LAML_APPLICATION.wireValue());
            assertEquals("ai_agent",          PhoneCallHandler.AI_AGENT.wireValue());
            assertEquals("call_flow",         PhoneCallHandler.CALL_FLOW.wireValue());
            assertEquals("relay_application", PhoneCallHandler.RELAY_APPLICATION.wireValue());
            assertEquals("relay_topic",       PhoneCallHandler.RELAY_TOPIC.wireValue());
            assertEquals("relay_context",     PhoneCallHandler.RELAY_CONTEXT.wireValue());
            assertEquals("relay_connector",   PhoneCallHandler.RELAY_CONNECTOR.wireValue());
            assertEquals("video_room",        PhoneCallHandler.VIDEO_ROOM.wireValue());
            assertEquals("dialogflow",        PhoneCallHandler.DIALOGFLOW.wireValue());
        }

        @Test
        @DisplayName("toString() returns the wire value (serializes transparently)")
        void toStringReturnsWireValue() {
            assertEquals("relay_script", PhoneCallHandler.RELAY_SCRIPT.toString());
            assertEquals("ai_agent",     PhoneCallHandler.AI_AGENT.toString());
            // Gson uses name() for enums by default; wireValue() is the explicit channel.
            // Validate the helper always puts wireValue() into the map, not name().
        }
    }

    // ── Helper wire-body assertions ─────────────────────────────────

    @Test
    @DisplayName("setSwmlWebhook sends PUT with call_handler=relay_script and call_relay_script_url")
    void swmlWebhook() {
        var ns = new com.signalwire.sdk.rest.namespaces.PhoneNumbersNamespace(httpClient());
        ns.setSwmlWebhook("pn-1", "https://example.com/swml");

        var req = lastRequest();
        assertEquals("PUT", req.method);
        assertEquals("/api/phone_numbers/pn-1", req.path);

        var body = bodyOf(req);
        assertEquals("relay_script", body.get("call_handler"));
        assertEquals("https://example.com/swml", body.get("call_relay_script_url"));
        assertEquals(2, body.size(), "only the two canonical fields should be sent");
    }

    @Test
    @DisplayName("setCxmlWebhook (minimal) sends laml_webhooks + call_request_url")
    void cxmlWebhookMinimal() {
        var ns = new com.signalwire.sdk.rest.namespaces.PhoneNumbersNamespace(httpClient());
        ns.setCxmlWebhook("pn-1", "https://example.com/voice.xml");

        var req = lastRequest();
        assertEquals("PUT", req.method);
        var body = bodyOf(req);
        assertEquals("laml_webhooks", body.get("call_handler"));
        assertEquals("https://example.com/voice.xml", body.get("call_request_url"));
        assertFalse(body.containsKey("call_fallback_url"));
        assertFalse(body.containsKey("call_status_callback_url"));
    }

    @Test
    @DisplayName("setCxmlWebhook with fallback + status callback")
    void cxmlWebhookFull() {
        var ns = new com.signalwire.sdk.rest.namespaces.PhoneNumbersNamespace(httpClient());
        ns.setCxmlWebhook(
                "pn-1",
                "https://example.com/voice.xml",
                "https://example.com/fallback.xml",
                "https://example.com/status");

        var req = lastRequest();
        var body = bodyOf(req);
        assertEquals("laml_webhooks", body.get("call_handler"));
        assertEquals("https://example.com/voice.xml", body.get("call_request_url"));
        assertEquals("https://example.com/fallback.xml", body.get("call_fallback_url"));
        assertEquals("https://example.com/status", body.get("call_status_callback_url"));
    }

    @Test
    @DisplayName("setCxmlApplication sends laml_application + call_laml_application_id")
    void cxmlApplication() {
        var ns = new com.signalwire.sdk.rest.namespaces.PhoneNumbersNamespace(httpClient());
        ns.setCxmlApplication("pn-1", "app-uuid");

        var body = bodyOf(lastRequest());
        assertEquals("laml_application", body.get("call_handler"));
        assertEquals("app-uuid", body.get("call_laml_application_id"));
    }

    @Test
    @DisplayName("setAiAgent sends ai_agent + call_ai_agent_id")
    void aiAgent() {
        var ns = new com.signalwire.sdk.rest.namespaces.PhoneNumbersNamespace(httpClient());
        ns.setAiAgent("pn-1", "agent-uuid");

        var body = bodyOf(lastRequest());
        assertEquals("ai_agent", body.get("call_handler"));
        assertEquals("agent-uuid", body.get("call_ai_agent_id"));
    }

    @Test
    @DisplayName("setCallFlow (no version) omits call_flow_version")
    void callFlowNoVersion() {
        var ns = new com.signalwire.sdk.rest.namespaces.PhoneNumbersNamespace(httpClient());
        ns.setCallFlow("pn-1", "flow-uuid");

        var body = bodyOf(lastRequest());
        assertEquals("call_flow", body.get("call_handler"));
        assertEquals("flow-uuid", body.get("call_flow_id"));
        assertFalse(body.containsKey("call_flow_version"));
    }

    @Test
    @DisplayName("setCallFlow (with version) includes call_flow_version")
    void callFlowWithVersion() {
        var ns = new com.signalwire.sdk.rest.namespaces.PhoneNumbersNamespace(httpClient());
        ns.setCallFlow("pn-1", "flow-uuid", "current_deployed");

        var body = bodyOf(lastRequest());
        assertEquals("call_flow", body.get("call_handler"));
        assertEquals("flow-uuid", body.get("call_flow_id"));
        assertEquals("current_deployed", body.get("call_flow_version"));
    }

    @Test
    @DisplayName("setRelayApplication sends relay_application + call_relay_application")
    void relayApplication() {
        var ns = new com.signalwire.sdk.rest.namespaces.PhoneNumbersNamespace(httpClient());
        ns.setRelayApplication("pn-1", "my-relay-app");

        var body = bodyOf(lastRequest());
        assertEquals("relay_application", body.get("call_handler"));
        assertEquals("my-relay-app", body.get("call_relay_application"));
    }

    @Test
    @DisplayName("setRelayTopic (no callback) omits call_relay_topic_status_callback_url")
    void relayTopicMinimal() {
        var ns = new com.signalwire.sdk.rest.namespaces.PhoneNumbersNamespace(httpClient());
        ns.setRelayTopic("pn-1", "office");

        var body = bodyOf(lastRequest());
        assertEquals("relay_topic", body.get("call_handler"));
        assertEquals("office", body.get("call_relay_topic"));
        assertFalse(body.containsKey("call_relay_topic_status_callback_url"));
    }

    @Test
    @DisplayName("setRelayTopic (with callback) includes call_relay_topic_status_callback_url")
    void relayTopicWithCallback() {
        var ns = new com.signalwire.sdk.rest.namespaces.PhoneNumbersNamespace(httpClient());
        ns.setRelayTopic("pn-1", "office", "https://example.com/status");

        var body = bodyOf(lastRequest());
        assertEquals("relay_topic", body.get("call_handler"));
        assertEquals("office", body.get("call_relay_topic"));
        assertEquals("https://example.com/status", body.get("call_relay_topic_status_callback_url"));
    }

    // ── Regression: the post-mortem guarantees ─────────────────────

    @Test
    @DisplayName("Regression: setSwmlWebhook makes exactly one PUT to /phone_numbers/{sid}")
    @SuppressWarnings("unused")
    void regressionSingleRequestHappyPath() {
        var ns = new com.signalwire.sdk.rest.namespaces.PhoneNumbersNamespace(httpClient());
        ns.setSwmlWebhook("pn-postmortem", "https://example.com/swml");

        assertEquals(1, recorded.size(), "binding should be a single HTTP call");

        var req = recorded.get(0);
        assertEquals("PUT", req.method, "phone_numbers.update uses PUT");
        assertEquals("/api/phone_numbers/pn-postmortem", req.path);

        // The whole point of the post-mortem: no fabric webhook create/assign calls.
        for (var r : recorded) {
            assertFalse(r.path.contains("/fabric/resources/swml_webhooks"),
                    "binding must not touch fabric.swml_webhooks.create");
            assertFalse(r.path.contains("/fabric/resources/cxml_webhooks"),
                    "binding must not touch fabric.cxml_webhooks.create");
            assertFalse(r.path.contains("/phone_routes"),
                    "binding must not call assign_phone_route");
        }
    }

    // ── Test helpers ─────────────────────────────────────────────────

    private static class RecordedRequest {
        final String method;
        final String path;
        final String body;

        RecordedRequest(String method, String path, String body) {
            this.method = method;
            this.path = path;
            this.body = body;
        }
    }

    private static class RecordingHandler implements HttpHandler {
        private final List<RecordedRequest> sink;

        RecordingHandler(List<RecordedRequest> sink) {
            this.sink = sink;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            sink.add(new RecordedRequest(method, path, body));

            byte[] resp = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        }
    }
}
