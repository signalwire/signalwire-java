/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real-behavior tests for {@link WebService} (parity with Python web_service.WebService). The
 * service binds the JDK HttpServer on an ephemeral port (0) and serves real files; each test starts
 * and stops it so nothing hangs.
 */
class WebServiceTest {

  private static final String USER = "webuser";
  private static final String PASS = "webpass";

  @TempDir Path dir;
  private WebService svc;
  private int port;
  private final HttpClient client = HttpClient.newHttpClient();

  @BeforeEach
  void setUp() throws IOException {
    Files.write(dir.resolve("hello.txt"), "hello world".getBytes(StandardCharsets.UTF_8));
    Files.write(dir.resolve("page.html"), "<h1>hi</h1>".getBytes(StandardCharsets.UTF_8));
    Files.write(dir.resolve(".env"), "SECRET=1".getBytes(StandardCharsets.UTF_8));
    svc = new WebService(new String[] {USER, PASS});
    svc.addDirectory("/static", dir.toString());
    port = svc.start("127.0.0.1", 0);
  }

  @AfterEach
  void tearDown() {
    if (svc != null) {
      svc.stop();
    }
  }

  private HttpResponse<String> get(String path, boolean auth) throws Exception {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    if (auth) {
      String token =
          Base64.getEncoder().encodeToString((USER + ":" + PASS).getBytes(StandardCharsets.UTF_8));
      b.header("Authorization", "Basic " + token);
    }
    return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void servesRealFileContents() throws Exception {
    HttpResponse<String> res = get("/static/hello.txt", true);
    assertEquals(200, res.statusCode());
    assertEquals("hello world", res.body());
  }

  @Test
  void servesHtmlWithSecurityHeaders() throws Exception {
    HttpResponse<String> res = get("/static/page.html", true);
    assertEquals(200, res.statusCode());
    assertTrue(res.body().contains("<h1>hi</h1>"));
    assertEquals("nosniff", res.headers().firstValue("X-Content-Type-Options").orElse(null));
    assertEquals("public, max-age=3600", res.headers().firstValue("Cache-Control").orElse(null));
  }

  @Test
  void missingFileIsNotFound() throws Exception {
    HttpResponse<String> res = get("/static/does-not-exist.txt", true);
    assertEquals(404, res.statusCode());
  }

  @Test
  void blockedExtensionIsForbidden() throws Exception {
    HttpResponse<String> res = get("/static/.env", true);
    assertEquals(403, res.statusCode());
  }

  @Test
  void pathTraversalDenied() throws Exception {
    HttpResponse<String> res = get("/static/../../etc/passwd", true);
    assertTrue(res.statusCode() == 403 || res.statusCode() == 404 || res.statusCode() == 400);
    assertFalse(res.body().contains("root:"));
  }

  @Test
  void requiresAuth() throws Exception {
    HttpResponse<String> res = get("/static/hello.txt", false);
    assertEquals(401, res.statusCode());
  }

  @Test
  void wrongAuthRejected() throws Exception {
    String token =
        Base64.getEncoder().encodeToString((USER + ":wrongpass").getBytes(StandardCharsets.UTF_8));
    HttpRequest req =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/static/hello.txt"))
            .header("Authorization", "Basic " + token)
            .build();
    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(401, res.statusCode());
  }

  @Test
  void removeDirectoryStopsServingNewRoutes() {
    svc.removeDirectory("/static");
    assertFalse(svc.getDirectories().containsKey("/static"));
  }

  @Test
  void fileAllowedPredicate() {
    assertTrue(svc.fileAllowed(dir.resolve("hello.txt")));
    assertFalse(svc.fileAllowed(dir.resolve(".env")));
  }

  @Test
  void startReturnsBoundEphemeralPort() {
    assertTrue(port > 0);
    assertNotEquals(8002, port);
  }
}
