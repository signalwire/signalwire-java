/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.runtime.lambda.LambdaAgentHandler;
import com.signalwire.sdk.runtime.lambda.LambdaResponse;
import com.signalwire.sdk.security.WebhookValidator;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swml.Service;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HttpDump — the Java port's HTTP dump program for the cross-port HTTP differ
 * (porting-sdk/scripts/diff_port_http.py).
 *
 * <p>For each {@code http_corpus} case it feeds a synthetic request into the Java SDK's
 * framework-free dispatch core ({@link Service#handleRequest}, {@link Service#extractSipUsername},
 * the webhook {@link WebhookValidator}, and the {@link LambdaAgentHandler} serverless adapter) and
 * prints ONE JSON object mapping
 *
 * <pre>
 *   case-id -&gt; reduced-artifact
 * </pre>
 *
 * to stdout, reduced to the same shape the Python oracle emits. Only stdout carries JSON. Mirrors
 * Go's {@code cmd/http-dump/main.go}.
 *
 * <p>Run via the {@code httpDump} Gradle task:
 *
 * <pre>
 *   ./gradlew --no-daemon -q httpDump
 * </pre>
 */
final class HttpDump {

  private HttpDump() {}

  private static final String USER = "user";
  private static final String PASSWORD = "pass";
  private static final String SIGNING_KEY = "PSK-fixed-signing-key";
  private static final String WH_URL = "https://agent.example.com/webhook";
  private static final String WH_BODY = "{\"event\":\"call.created\",\"id\":\"abc\"}";

  private static String basicAuth(String u, String p) {
    return "Basic "
        + Base64.getEncoder().encodeToString((u + ":" + p).getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(byte[] b) {
    StringBuilder sb = new StringBuilder(b.length * 2);
    for (byte x : b) {
      sb.append(Character.forDigit((x >> 4) & 0xF, 16));
      sb.append(Character.forDigit(x & 0xF, 16));
    }
    return sb.toString();
  }

  private static String webhookSig(String url, String body, String key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
      return hex(mac.doFinal((url + body).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** Ordered map helper. */
  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  private static Service newSwmlService() {
    return new Service("demo", "/swml", "0.0.0.0", 3000, USER, PASSWORD);
  }

  /**
   * Reduce a {@link Service.HttpResult} to a comparable artifact — the Java mirror of
   * diff_port_http._observe_response. {@code kind}: "response_full" also parses+includes the body,
   * "response_status_headers" only status + header keys.
   */
  private static Map<String, Object> observeResponse(Service.HttpResult res, String kind) {
    List<String> keys = new ArrayList<>(res.headers().keySet());
    Collections.sort(keys);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", res.status());
    out.put("header_keys", keys);
    if (res.headers().containsKey("Location")) {
      out.put("location", res.headers().get("Location"));
    }
    if (res.headers().containsKey("WWW-Authenticate")) {
      out.put("www_authenticate", res.headers().get("WWW-Authenticate"));
    }
    if ("response_full".equals(kind)) {
      String body = res.body();
      if (body == null || body.isEmpty()) {
        out.put("body", "");
      } else {
        out.put("body", parseJsonOrString(body));
      }
    }
    return out;
  }

  private static Object parseJsonOrString(String s) {
    try {
      java.lang.reflect.Type t = new TypeToken<Object>() {}.getType();
      Object parsed = GSON.fromJson(s, t);
      return parsed != null ? parsed : s;
    } catch (Exception e) {
      return s;
    }
  }

  private static final Gson GSON =
      new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

  private static Map<String, Object> extractUsername(Map<String, Object> body) {
    String u = Service.extractSipUsername(body);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("username", (u == null || u.isEmpty()) ? null : u);
    return m;
  }

  private static Map<String, Object> webhookDecision(
      String method, String url, String body, Map<String, String> headers, String key) {
    WebhookValidator.WebhookRejection rej =
        WebhookValidator.validate(method, url, headers, body, key);
    if (rej == null) {
      return map("decision", "pass");
    }
    return map("decision", "reject", "status", rej.status());
  }

  public static void main(String[] args) {
    Logger.setGlobalLevel(Logger.Level.OFF);
    Map<String, Object> out = new LinkedHashMap<>();

    // ---- handle_request: 200 SWML happy path ----
    {
      Service svc = newSwmlService();
      Service.HttpResult r =
          svc.handleRequest(
              "GET",
              "http://localhost:3000/swml",
              headerMap("Authorization", basicAuth(USER, PASSWORD)),
              null);
      out.put("http_handle_request_200_swml", observeResponse(r, "response_full"));
    }
    // ---- handle_request: 401 no auth ----
    {
      Service svc = newSwmlService();
      Service.HttpResult r =
          svc.handleRequest("GET", "http://localhost:3000/swml", new LinkedHashMap<>(), null);
      out.put("http_handle_request_401_no_auth", observeResponse(r, "response_full"));
    }
    // ---- handle_request: 401 bad password (status+headers only) ----
    {
      Service svc = newSwmlService();
      Service.HttpResult r =
          svc.handleRequest(
              "GET",
              "http://localhost:3000/swml",
              headerMap("Authorization", basicAuth(USER, "wrong")),
              null);
      out.put(
          "http_handle_request_401_bad_password", observeResponse(r, "response_status_headers"));
    }
    // ---- handle_request: 307 redirect via routing callback ----
    {
      Service svc = newSwmlService();
      svc.registerRoutingCallback(HttpDump::redirectCb, "/sip");
      Service.HttpResult r =
          svc.handleRequest(
              "POST",
              "http://localhost:3000/swml/sip",
              headerMap("Authorization", basicAuth(USER, PASSWORD)),
              map("call", map("to", "sip:redirect-me@space")));
      out.put("http_handle_request_307_redirect", observeResponse(r, "response_full"));
    }
    // ---- handle_request: callback returns null -> normal 200 SWML ----
    {
      Service svc = newSwmlService();
      svc.registerRoutingCallback(HttpDump::redirectCb, "/sip");
      Service.HttpResult r =
          svc.handleRequest(
              "POST",
              "http://localhost:3000/swml/sip",
              headerMap("Authorization", basicAuth(USER, PASSWORD)),
              map("call", map("to", "sip:keep@space")));
      out.put("http_handle_request_callback_passthrough_200", observeResponse(r, "response_full"));
    }

    // ---- extract_sip_username: pure extractor ----
    out.put(
        "http_extract_sip_username_sip",
        extractUsername(map("call", map("to", "sip:alice@agents.signalwire.com"))));
    out.put(
        "http_extract_sip_username_tel",
        extractUsername(map("call", map("to", "tel:+15551234567"))));
    out.put("http_extract_sip_username_plain", extractUsername(map("call", map("to", "support"))));
    out.put("http_extract_sip_username_missing", extractUsername(map("vars", map())));

    // ---- webhook validate ----
    out.put(
        "http_webhook_validate_ok",
        webhookDecision(
            "POST",
            WH_URL,
            WH_BODY,
            headerMap("x-signalwire-signature", webhookSig(WH_URL, WH_BODY, SIGNING_KEY)),
            SIGNING_KEY));
    String badSig = "deadbeef".repeat(5);
    out.put(
        "http_webhook_validate_bad_sig",
        webhookDecision(
            "POST", WH_URL, WH_BODY, headerMap("x-signalwire-signature", badSig), SIGNING_KEY));
    out.put(
        "http_webhook_validate_missing_sig",
        webhookDecision("POST", WH_URL, WH_BODY, new LinkedHashMap<>(), SIGNING_KEY));
    out.put(
        "http_webhook_validate_twilio_alias",
        webhookDecision(
            "POST",
            WH_URL,
            WH_BODY,
            headerMap("x-twilio-signature", webhookSig(WH_URL, WH_BODY, SIGNING_KEY)),
            SIGNING_KEY));

    // ---- serverless (lambda) ----
    out.put("http_serverless_lambda_swaig", serverlessSwaig());
    out.put("http_serverless_lambda_noauth_401", serverlessNoAuth());

    System.out.println(GSON.toJson(out));
  }

  /** A String-keyed, String-valued header map. */
  private static Map<String, String> headerMap(String... kv) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put(kv[i], kv[i + 1]);
    }
    return m;
  }

  /** Redirect one specific 'to', else pass through (null). */
  private static String redirectCb(Map<String, Object> body, Map<String, String> headers) {
    Object callObj = body.get("call");
    if (callObj instanceof Map) {
      @SuppressWarnings("unchecked")
      Object to = ((Map<String, Object>) callObj).get("to");
      if ("sip:redirect-me@space".equals(to)) {
        return "/other-route";
      }
    }
    return null;
  }

  /**
   * Drive the lambda adapter for the /swaig dispatch case. The agent is built at route "/" so the
   * event's root-relative "/swaig" path routes correctly.
   */
  private static Map<String, Object> serverlessSwaig() {
    AgentBase a =
        AgentBase.builder().name("demo").route("/").authUser(USER).authPassword(PASSWORD).build();
    a.defineTool(
        "say_hello",
        "greet",
        new LinkedHashMap<>(),
        (argsMap, raw) -> new FunctionResult("hello there"));
    LambdaAgentHandler h = new LambdaAgentHandler(a);
    Map<String, Object> event =
        map(
            "rawPath",
            "/swaig",
            "requestContext",
            map("http", map("method", "POST")),
            "headers",
            map("authorization", basicAuth(USER, PASSWORD), "content-type", "application/json"),
            "body",
            "{\"function\":\"say_hello\",\"argument\":{\"parsed\":[{}]},\"call_id\":\"c1\"}");
    return reduceLambda(h.handle(event));
  }

  private static Map<String, Object> serverlessNoAuth() {
    AgentBase a =
        AgentBase.builder().name("demo").route("/").authUser(USER).authPassword(PASSWORD).build();
    LambdaAgentHandler h = new LambdaAgentHandler(a);
    Map<String, Object> event =
        map("rawPath", "/", "requestContext", map("http", map("method", "GET")), "headers", map());
    return reduceLambda(h.handle(event));
  }

  /** Reduce a lambda response to {status, body} with the body parsed as JSON. */
  private static Map<String, Object> reduceLambda(LambdaResponse resp) {
    Object body = resp.getBody();
    if (resp.getBody() != null && !resp.getBody().isEmpty()) {
      body = parseJsonOrString(resp.getBody());
    }
    return map("status", resp.getStatusCode(), "body", body);
  }
}
