/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signalwire.sdk.agent.AgentBase;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Served-path parity for the routing callback (#61). The framework-free {@link
 * com.signalwire.sdk.swml.Service#handleRequest handleRequest} core already returned a {@code 307}
 * for a routing redirect (see {@link HandleRequestTest}), but the ACTUAL served endpoint that
 * {@link AgentBase#serve()} / {@link com.signalwire.sdk.swml.Service#asRouter() asRouter()}
 * register used to re-implement auth+render inline and never reached the routing branch — so a
 * redirect worked when calling {@code handleRequest} directly but NOT when actually serving. These
 * tests bind a real listener and prove the served path now flows through {@code handleRequest}: a
 * routing callback yields a {@code 307} with the {@code Location} header (not a wrong-status {@code
 * 200}), bad auth yields {@code 401}, and the happy path yields {@code 200} SWML.
 */
class ServedRoutingTest {

  private static final HttpClient HTTP =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          // Do NOT auto-follow: we must observe the 307 itself.
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

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
  @DisplayName("Served path: a routing callback redirects with 307 + Location (not 200)")
  void servedRoutingCallbackRedirectsWith307() throws Exception {
    int port = findFreePort();
    try (AgentBase agent =
        AgentBase.builder()
            .name("routed")
            .route("/sip")
            .host("127.0.0.1")
            .port(port)
            .authUser("u")
            .authPassword("p")
            .build()) {
      agent.registerRoutingCallback((body, headers) -> "/other-agent");
      agent.serve();

      HttpResponse<String> resp =
          HTTP.send(
              HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/sip"))
                  .header("Authorization", basicAuth("u", "p"))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString("{\"call_id\":\"abc\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertEquals(
          307,
          resp.statusCode(),
          "served routing callback must redirect with 307, got " + resp.statusCode());
      assertEquals(
          "/other-agent",
          resp.headers().firstValue("Location").orElse(null),
          "307 redirect must carry the Location header from the routing callback");
    }
  }

  @Test
  @DisplayName("Served path: bad credentials yield 401 through handleRequest")
  void servedBadAuthYields401() throws Exception {
    int port = findFreePort();
    try (AgentBase agent =
        AgentBase.builder()
            .name("routed")
            .route("/sip")
            .host("127.0.0.1")
            .port(port)
            .authUser("u")
            .authPassword("p")
            .build()) {
      agent.registerRoutingCallback((body, headers) -> "/other-agent");
      agent.serve();

      HttpResponse<String> resp =
          HTTP.send(
              HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/sip"))
                  .header("Authorization", basicAuth("u", "WRONG"))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString("{\"call_id\":\"abc\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertEquals(401, resp.statusCode(), "bad credentials must be rejected before routing");
    }
  }

  @Test
  @DisplayName("Served path: happy path renders 200 SWML through handleRequest")
  void servedHappyPathRenders200Swml() throws Exception {
    int port = findFreePort();
    try (AgentBase agent =
        AgentBase.builder()
            .name("routed")
            .route("/sip")
            .host("127.0.0.1")
            .port(port)
            .authUser("u")
            .authPassword("p")
            .build()) {
      // Callback returns null -> no redirect -> normal SWML render.
      agent.registerRoutingCallback((body, headers) -> null);
      agent.serve();

      HttpResponse<String> resp =
          HTTP.send(
              HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/sip"))
                  .header("Authorization", basicAuth("u", "p"))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString("{\"call_id\":\"abc\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());

      assertEquals(200, resp.statusCode(), "null routing callback must fall through to 200 render");
      assertNotNull(resp.body());
      assertTrue(
          resp.body().contains("version") || resp.body().contains("sections"),
          "200 body should be a rendered SWML document, got: " + resp.body());
    }
  }
}
