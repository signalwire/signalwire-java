/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.server.AgentServer;
import com.signalwire.sdk.swaig.FunctionResult;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link AgentServer} and {@link AgentBase} (via the {@link
 * com.signalwire.sdk.swml.Service Service} superclass) honour {@link AutoCloseable}: serving inside
 * a try-with-resources block actually binds a real HTTP listener, and {@code close()} releases it
 * when the block exits.
 *
 * <p>No mocks — each test binds a real ephemeral port, probes it over loopback HTTP while the
 * server is up, then proves the port was freed afterwards by (a) failing to connect to it and (b)
 * successfully re-binding it.
 */
class AutoCloseableServerTest {

  private static int findFreePort() throws Exception {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }

  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private static HttpResponse<String> get(String url) throws Exception {
    return HTTP.send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /** Assert nothing is listening on the loopback port (connection refused). */
  private static void assertPortClosed(int port) {
    // A freed listener should refuse new connections. java.net.http wraps
    // the refusal; the cause chain carries ConnectException.
    Exception ex =
        assertThrows(
            Exception.class,
            () -> get("http://127.0.0.1:" + port + "/health"),
            "expected the released port to refuse connections");
    boolean refused = hasCause(ex, ConnectException.class);
    if (!refused) {
      // Fall back to the definitive proof: the port can be re-bound,
      // which is only possible if the previous listener truly released it.
      try (ServerSocket s = new ServerSocket(port)) {
        assertEquals(port, s.getLocalPort());
      } catch (Exception rebindFailure) {
        throw new AssertionError(
            "port " + port + " neither refused connections nor could be re-bound", rebindFailure);
      }
    }
  }

  private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
    for (Throwable c = t; c != null; c = c.getCause()) {
      if (type.isInstance(c)) {
        return true;
      }
    }
    return false;
  }

  @Test
  @DisplayName("AgentServer: try-with-resources serves then close() frees the port")
  void agentServerAutoCloseable() throws Exception {
    int port = findFreePort();
    AgentBase agent =
        AgentBase.builder().name("actest").route("/agent").authUser("u").authPassword("p").build();

    try (AgentServer server = new AgentServer("127.0.0.1", port)) {
      server.register(agent);
      server.run();

      // Inside the block: the listener is up and answering /health.
      HttpResponse<String> resp = get("http://127.0.0.1:" + port + "/health");
      assertEquals(200, resp.statusCode());
      assertTrue(
          resp.body().contains("\"status\""),
          "health endpoint should report status JSON, got: " + resp.body());
    } // close() -> stop() runs here

    // After the block: the bound listener must be gone.
    assertPortClosed(port);
  }

  @Test
  @DisplayName("AgentBase: try-with-resources serves then close() frees the port (Service.close)")
  void agentBaseAutoCloseable() throws Exception {
    int port = findFreePort();

    // AgentBase is AutoCloseable through its Service superclass: close()
    // delegates to stop(), shutting the inherited HTTP listener down.
    try (AgentBase agent =
        AgentBase.builder()
            .name("acagent")
            .route("/")
            .host("127.0.0.1")
            .port(port)
            .authUser("u")
            .authPassword("p")
            .build()) {

      agent.defineTool("ping", "Test tool", Map.of(), (args, raw) -> new FunctionResult("pong"));
      agent.serve();

      // Inside the block: the listener answers its unauthenticated
      // /health endpoint (the auth-gated SWML routes need a Basic header;
      // /health is the clean liveness probe Service.serve() registers).
      HttpResponse<String> resp = get("http://127.0.0.1:" + port + "/health");
      assertEquals(200, resp.statusCode(), "agent /health should respond while running");
      assertTrue(
          resp.body().contains("\"status\""),
          "health endpoint should report status JSON, got: " + resp.body());
    } // close() -> Service.stop() runs here

    // After the block: the agent's listener must be released.
    assertPortClosed(port);
  }

  @Test
  @DisplayName("Service.close() is reachable as AutoCloseable on AgentBase and is idempotent")
  void agentBaseCloseIdempotent() throws Exception {
    int port = findFreePort();
    AgentBase agent =
        AgentBase.builder()
            .name("idem")
            .route("/")
            .host("127.0.0.1")
            .port(port)
            .authUser("u")
            .authPassword("p")
            .build();

    // AgentBase widens to AutoCloseable (via Service).
    AutoCloseable closeable = agent;

    agent.serve();
    closeable.close();
    // Second close() must be a harmless no-op (httpServer already stopped).
    closeable.close();

    assertPortClosed(port);
  }
}
