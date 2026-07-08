/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.server.AgentServer;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behavioral contract #6 (BEHAVIORAL_CONTRACTS.md): a SIP username registered for routing must be
 * CONSULTED by the served path — the stored mapping is not inert. With #61 wiring the served path
 * through {@code handleRequest}, {@link AgentServer}'s SIP routing callback now fires: a POST of a
 * SIP-shaped body to the served endpoint extracts the username, resolves it to a target route, and
 * redirects with a {@code 307 + Location}.
 *
 * <p>Previously {@code AgentServer.handleSwml} rendered 200 SWML inline and never called the
 * routing callback, so the SIP mapping was stored-but-unconsulted — this test would have observed a
 * 200 instead of the 307.
 */
class SipServedDispatchTest {

  private static final HttpClient HTTP =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER) // observe the 307 itself
          .build();

  private AgentServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  private static int findFreePort() throws Exception {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }

  private static String basicAuth(String user, String pass) {
    return "Basic "
        + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("Served /sip: a registered SIP username routes with 307 + Location to its target")
  void sipUsernameRoutesOverServedPath() throws Exception {
    int port = findFreePort();
    server = new AgentServer("127.0.0.1", port);

    // Entry agent (receives the SIP POST) + the target agent the username maps to.
    AgentBase entry =
        AgentBase.builder().name("entry").route("/sip").authUser("u").authPassword("p").build();
    AgentBase support =
        AgentBase.builder()
            .name("support")
            .route("/support")
            .authUser("u")
            .authPassword("p")
            .build();
    server.register(entry, "/sip");
    server.register(support, "/support");

    // Enable server-level SIP routing (installs the routing callback on every
    // agent) and map the SIP username "alice" -> /support.
    server.setupSipRouting("/sip", false);
    server.registerSipUsername("alice", "/support");

    server.run();

    // POST a SIP-shaped body (call.to carries the SIP URI) to the served /sip.
    String body = "{\"call\":{\"to\":\"sip:alice@example.com\"}}";
    HttpResponse<String> resp =
        HTTP.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/sip"))
                .header("Authorization", basicAuth("u", "p"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());

    assertEquals(
        307,
        resp.statusCode(),
        "a registered SIP username must route with 307 over the served path, got "
            + resp.statusCode()
            + " body="
            + resp.body());
    assertEquals(
        "/support",
        resp.headers().firstValue("Location").orElse(null),
        "the 307 must redirect to the route the SIP username maps to");
  }
}
