/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.swml.Service;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for the framework-free {@code handleRequest(method, url, headers, body) -> (status,
 * headers, body)} dispatch core (parity with the Python reference {@code
 * SWMLService.handle_request}). Proves the same auth / routing-callback / render behavior over
 * plain primitives — without the embedded HTTP server.
 */
class HandleRequestTest {

  private static Map<String, String> basicAuth(String user, String pass) {
    Map<String, String> h = new LinkedHashMap<>();
    String token =
        Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
    h.put("Authorization", "Basic " + token);
    return h;
  }

  @Test
  void unauthorizedWithoutCredentials() {
    var svc = new Service("svc", "/", "0.0.0.0", 3000, "u", "p");
    Service.HttpResult res = svc.handleRequest("GET", "http://localhost/", Map.of(), null);
    assertEquals(401, res.status());
    assertEquals("Basic", res.headers().get("WWW-Authenticate"));
  }

  @Test
  void rendersDocumentWhenAuthorized() {
    var svc = new Service("svc", "/", "0.0.0.0", 3000, "u", "p");
    Service.HttpResult res =
        svc.handleRequest("GET", "http://localhost/", basicAuth("u", "p"), null);
    assertEquals(200, res.status());
    assertNotNull(res.body());
    assertFalse(res.body().isEmpty());
  }

  @Test
  void routingCallbackRedirectsWith307() {
    var svc = new Service("svc", "/sip", "0.0.0.0", 3000, "u", "p");
    svc.registerRoutingCallback((body, headers) -> "/other-agent");

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("call_id", "abc");
    Service.HttpResult res =
        svc.handleRequest("POST", "http://localhost/sip", basicAuth("u", "p"), body);

    assertEquals(307, res.status());
    assertEquals("/other-agent", res.headers().get("Location"));
    assertEquals("", res.body());
  }

  @Test
  void routingCallbackContinuesWhenNull() {
    var svc = new Service("svc", "/sip", "0.0.0.0", 3000, "u", "p");
    svc.registerRoutingCallback((body, headers) -> null);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("call_id", "abc");
    Service.HttpResult res =
        svc.handleRequest("POST", "http://localhost/sip", basicAuth("u", "p"), body);

    // No redirect: falls through to normal 200 render.
    assertEquals(200, res.status());
  }
}
