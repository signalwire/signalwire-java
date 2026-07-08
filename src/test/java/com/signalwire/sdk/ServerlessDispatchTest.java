/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.runtime.EnvProvider;
import com.signalwire.sdk.runtime.ServerlessAdapter;
import com.signalwire.sdk.runtime.lambda.LambdaAgentHandler;
import com.signalwire.sdk.runtime.lambda.LambdaResponse;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behavioral contract #5 (BEHAVIORAL_CONTRACTS.md): serverless requests DISPATCH per-platform to a
 * real response, not Lambda-only / detection-only. For each of lambda + cgi + gcf, feed a synthetic
 * platform event/env and assert the agent renders a real 200 SWML response.
 *
 * <p>Lambda flows through {@link LambdaAgentHandler}; cgi + gcf flow through {@link
 * ServerlessAdapter}, which funnels each platform's shape through the same framework-free {@code
 * handleRequest} core the in-process server uses. The cgi/gcf handlers were previously absent (the
 * Java port shipped Lambda-only), so these two assertions would have had nothing to call.
 */
class ServerlessDispatchTest {

  private static AgentBase agent() {
    AgentBase agent =
        AgentBase.builder().name("srv").route("/").authUser("u").authPassword("p").build();
    agent.setPromptText("hi");
    return agent;
  }

  private static String basicAuth() {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString("u:p".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("Lambda: a root GET event dispatches to a 200 SWML response")
  void lambdaDispatch() {
    LambdaAgentHandler handler = new LambdaAgentHandler(agent());
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("httpMethod", "GET");
    event.put("path", "/");
    event.put("headers", Map.of("Authorization", basicAuth()));

    LambdaResponse resp = handler.handle(event);
    assertEquals(200, resp.getStatusCode(), "Lambda root GET must render SWML");
    assertTrue(resp.getBody().contains("version"), "body should be a SWML doc: " + resp.getBody());
  }

  @Test
  @DisplayName("Lambda: a base64-encoded body is decoded before dispatch")
  void lambdaBase64Body() {
    LambdaAgentHandler handler = new LambdaAgentHandler(agent());
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("httpMethod", "POST");
    event.put("path", "/");
    event.put("headers", Map.of("Authorization", basicAuth()));
    event.put(
        "body",
        Base64.getEncoder()
            .encodeToString(
                "{\"call_id\":\"abc\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    event.put("isBase64Encoded", true);

    LambdaResponse resp = handler.handle(event);
    assertEquals(200, resp.getStatusCode());
    assertTrue(resp.getBody().contains("version"));
  }

  @Test
  @DisplayName("GCF: a synthetic request dispatches through handleRequest to a 200 SWML response")
  void gcfDispatch() {
    Map<String, String> headers = Map.of("Authorization", basicAuth());
    ServerlessAdapter.Response resp =
        ServerlessAdapter.handleGcf(agent(), "GET", "/", headers, null);

    assertEquals(200, resp.status(), "GCF GET must render SWML, not fall through/unsupported");
    assertTrue(resp.body().contains("version"), "body should be a SWML doc: " + resp.body());
    assertEquals("application/json", resp.headers().get("Content-Type"));
  }

  @Test
  @DisplayName("CGI: a synthetic env dispatches through handleRequest to a 200 SWML CGI response")
  void cgiDispatch() {
    Map<String, String> cgiEnv = new LinkedHashMap<>();
    cgiEnv.put("GATEWAY_INTERFACE", "CGI/1.1");
    cgiEnv.put("REQUEST_METHOD", "GET");
    cgiEnv.put("PATH_INFO", "/");
    EnvProvider env = cgiEnv::get;

    Map<String, String> headers = Map.of("Authorization", basicAuth());
    String cgiResponse = ServerlessAdapter.handleCgi(agent(), env, headers, null);

    assertTrue(
        cgiResponse.startsWith("Status: 200"),
        "CGI response must start with a 200 status line, got: " + firstLine(cgiResponse));
    assertTrue(
        cgiResponse.contains("version"),
        "CGI body should be a rendered SWML doc, got: " + cgiResponse);
  }

  @Test
  @DisplayName("CGI: bad auth dispatches to a 401 status line (not a fall-through render)")
  void cgiUnauthorized() {
    Map<String, String> cgiEnv = new LinkedHashMap<>();
    cgiEnv.put("REQUEST_METHOD", "POST");
    cgiEnv.put("PATH_INFO", "/");
    EnvProvider env = cgiEnv::get;

    // No Authorization header -> handleRequest returns 401.
    String cgiResponse = ServerlessAdapter.handleCgi(agent(), env, Map.of(), "{\"call_id\":\"x\"}");
    assertTrue(
        cgiResponse.startsWith("Status: 401"),
        "CGI must dispatch bad auth to 401, got: " + firstLine(cgiResponse));
  }

  private static String firstLine(String s) {
    int nl = s.indexOf('\r');
    return nl >= 0 ? s.substring(0, nl) : s;
  }
}
