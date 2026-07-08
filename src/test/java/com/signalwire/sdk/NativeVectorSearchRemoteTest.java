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

import com.google.gson.Gson;
import com.signalwire.sdk.skills.builtin.NativeVectorSearchSkill;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behavioral contract #4 (BEHAVIORAL_CONTRACTS.md): in remote mode the native_vector_search skill
 * performs a REAL HTTP POST to {@code <remote_url>/search} with the query in the body, and formats
 * the returned {@code results[]} into the {@link FunctionResult} — it is not a hardcoded "[Would
 * query…]" string. A mock HTTP server (bound to a FREE ephemeral port, stopped after) captures the
 * request and returns canned results.
 *
 * <p>This test also pins the wire endpoint: the POST must land on {@code /search} (mirroring Python
 * {@code _search_remote}'s {@code f"{remote_base_url}/search"}), not on the bare remote_url.
 */
class NativeVectorSearchRemoteTest {

  @Test
  @DisplayName("remote search POSTs {query} to <remote_url>/search and formats the results")
  void remoteSearchPostsToSearchEndpoint() throws Exception {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
    AtomicReference<String> hitPath = new AtomicReference<>();
    AtomicReference<String> requestBody = new AtomicReference<>();

    server.createContext(
        "/",
        exchange -> {
          hitPath.set(exchange.getRequestURI().getPath());
          byte[] in = exchange.getRequestBody().readAllBytes();
          requestBody.set(new String(in, StandardCharsets.UTF_8));

          String resp =
              new Gson()
                  .toJson(
                      Map.of(
                          "results",
                          List.of(
                              Map.of(
                                  "content",
                                  "The sky is blue.",
                                  "score",
                                  0.97,
                                  "metadata",
                                  Map.of("source", "doc1")))));
          byte[] out = resp.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, out.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
          }
        });
    server.start();
    int port = server.getAddress().getPort();

    try {
      NativeVectorSearchSkill skill = new NativeVectorSearchSkill();
      assertTrue(skill.setup(Map.of("remote_url", "http://127.0.0.1:" + port)));

      List<ToolDefinition> tools = skill.registerTools();
      assertEquals(1, tools.size());

      FunctionResult result =
          tools.get(0).getHandler().handle(Map.of("query", "what color is the sky"), Map.of());

      // Real POST landed on /search (not the bare remote_url root).
      assertEquals("/search", hitPath.get(), "the POST must target <remote_url>/search");
      assertNotNull(requestBody.get(), "the mock must have received a request body");
      assertTrue(
          requestBody.get().contains("what color is the sky"),
          "the query must be in the POST body, got: " + requestBody.get());

      // The mock's results are formatted into the FunctionResult (not a stub string).
      assertTrue(
          result.getResponse().contains("The sky is blue."),
          "the remote result content must be formatted into the response, got: "
              + result.getResponse());
    } finally {
      server.stop(0);
    }
  }
}
