/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared HTTP plumbing for the gateway / handoff tests: a loopback {@link HttpServer} on a FREE
 * ephemeral port, a real {@link HttpClient} driving it, and the in-process stub chat service the
 * Python reference's tests use ({@code tests/unit/ai_chat/test_gateway.py} {@code service}
 * fixture): it records every JSON-RPC body it was sent and answers each method with a canned
 * result.
 */
final class AiChatHttpTestSupport {

  static final Gson GSON =
      new GsonBuilder()
          .disableHtmlEscaping()
          .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
          .create();

  private static final HttpClient HTTP =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

  private AiChatHttpTestSupport() {}

  /** One HTTP exchange's outcome. */
  record Resp(int status, HttpHeaders headers, String body) {
    Map<String, Object> json() {
      return GSON.fromJson(body, new TypeToken<Map<String, Object>>() {}.getType());
    }

    String header(String name) {
      return headers.firstValue(name).orElse(null);
    }
  }

  /** Start a loopback server with the given context → handler table. */
  static HttpServer start(Map<String, HttpHandler> contexts) throws IOException {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    for (Map.Entry<String, HttpHandler> e : contexts.entrySet()) {
      server.createContext(e.getKey(), e.getValue());
    }
    server.start();
    return server;
  }

  static String base(HttpServer server) {
    return "http://"
        + InetAddress.getLoopbackAddress().getHostAddress()
        + ":"
        + server.getAddress().getPort();
  }

  static Resp send(
      String method, String url, HttpRequest.BodyPublisher body, Map<String, String> headers)
      throws IOException, InterruptedException {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).method(method, body);
    for (Map.Entry<String, String> h : headers.entrySet()) {
      b.header(h.getKey(), h.getValue());
    }
    HttpResponse<String> r =
        HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    return new Resp(r.statusCode(), r.headers(), r.body());
  }

  static Resp postJson(String url, Object body, Map<String, String> headers)
      throws IOException, InterruptedException {
    Map<String, String> h = new LinkedHashMap<>(headers);
    h.putIfAbsent("Content-Type", "application/json");
    return send("POST", url, HttpRequest.BodyPublishers.ofString(GSON.toJson(body)), h);
  }

  static Resp postRaw(String url, byte[] body, Map<String, String> headers)
      throws IOException, InterruptedException {
    return send("POST", url, HttpRequest.BodyPublishers.ofByteArray(body), headers);
  }

  /** The stub chat service: records what the gateway forwarded upstream. */
  static final class StubChatService implements AutoCloseable {
    final List<Map<String, Object>> seen = new CopyOnWriteArrayList<>();
    final HttpServer server;
    final String url;

    StubChatService() throws IOException {
      server = start(Map.of("/", this::handle));
      url = base(server) + "/";
    }

    private void handle(HttpExchange exchange) throws IOException {
      byte[] in = exchange.getRequestBody().readAllBytes();
      Map<String, Object> body =
          GSON.fromJson(
              new String(in, StandardCharsets.UTF_8),
              new TypeToken<Map<String, Object>>() {}.getType());
      seen.add(body);
      String method = (String) body.get("method");
      Object result;
      switch (method) {
        case "chat":
          result = Map.of("response", "hi there");
          break;
        case "create_conversation":
          result = Map.of("status", "created", "initial_message", "Hi, I am Sigmond.");
          break;
        case "chat_log":
          result =
              Map.of(
                  "chat_log",
                  List.of(
                      Map.of("role", "system", "content", "secret prompt"),
                      Map.of("role", "user", "content", "hi"),
                      Map.of("role", "assistant", "content", "hi there")));
          break;
        default:
          result = Map.of("status", "ended");
          break;
      }
      Map<String, Object> env = new LinkedHashMap<>();
      env.put("jsonrpc", "2.0");
      env.put("result", result);
      env.put("id", body.get("id"));
      byte[] out = GSON.toJson(env).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, out.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(out);
      }
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }

  static Map<String, String> headers(String... kv) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) {
      m.put(kv[i], kv[i + 1]);
    }
    return Collections.unmodifiableMap(m);
  }
}
