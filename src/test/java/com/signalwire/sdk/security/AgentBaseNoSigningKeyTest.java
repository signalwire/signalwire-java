/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.security;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When AgentBase is started without a signing key, signature validation is
 * disabled and unsigned POSTs to the signed routes still flow through —
 * the spec says SDKs MUST log a prominent warning instead of silently
 * rejecting.
 */
class AgentBaseNoSigningKeyTest {

    private AgentBase agent;
    private int port;
    private HttpClient client;

    @BeforeEach
    void start() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) { port = s.getLocalPort(); }
        agent = AgentBase.builder()
                .name("nokey")
                .route("/")
                .port(port)
                .authUser("user")
                .authPassword("pass")
                .envProvider(name -> null) // ensure SIGNALWIRE_SIGNING_KEY isn't picked up
                .build();
        agent.defineTool("ping", "Test tool", Map.of(), (args, raw) -> new FunctionResult("pong"));
        agent.serve();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void stop() {
        if (agent != null) agent.stop();
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder()
                .encodeToString(("user:pass").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Without signingKey: POST /swaig with NO signature reaches the handler")
    void unsignedSwaigPostFlowsThrough() throws Exception {
        // Confirm the agent has no signing key configured.
        assertNull(agent.getSigningKey(), "no signing key should be configured for this test");

        String body = "{\"function\":\"ping\",\"argument\":{\"parsed\":[{}]}}";
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/swaig"))
                        .header("Authorization", basicAuth())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode(),
                "unsigned POST must reach the SWAIG handler when no signing key is configured");
        assertTrue(resp.body().contains("pong"));
    }
}
