package com.signalwire.sdk.security;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.runtime.lambda.LambdaAgentHandler;
import com.signalwire.sdk.runtime.lambda.LambdaResponse;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A tool declared {@code secure} requires a valid per-call {@code __token} before its handler runs,
 * on every transport the SDK exposes — the in-process HTTP {@code /swaig} endpoint, the standalone
 * {@code AgentServer} route, and the AWS Lambda adapter.
 *
 * <p>The credential and the identity it is checked against travel separately: the token rides the
 * QUERY STRING, the {@code call_id} rides the POST body. A token can only be validated against a
 * {@code call_id}, so a request that omits one is unvalidated and is refused exactly like a forged
 * token — omitting the credential must never be weaker than presenting a wrong one.
 *
 * <p>A refusal is a {@code 200} carrying a {@link FunctionResult} body, NOT an HTTP error status:
 * the engine has no handling for a SWAIG refusal status, so the tool reports that it cannot execute
 * and the model relays that to the caller.
 *
 * <p>A tool declared {@code secure(false)} runs ungated in every one of these cases.
 */
class SwaigTokenEnforcementTest {

  private static final String USER = "u";
  private static final String PASSWORD = "p";
  private static final Gson GSON = new Gson();

  /** The refusal text a secure tool produces when its token is absent, forged, or unvalidatable. */
  private static final String REFUSAL =
      "I'm sorry, the security token for this function is invalid or expired. "
          + "I cannot execute this action.";

  // ------------------------------------------------------------------
  // fixtures
  // ------------------------------------------------------------------

  /** An agent at route "/" with one secure tool and one insecure tool, both echoing a marker. */
  private static AgentBase agent() {
    return agent(0);
  }

  /** As {@link #agent()}, bound to {@code port} when non-zero so a listener can be started. */
  private static AgentBase agent(int port) {
    AgentBase.Builder b =
        AgentBase.builder().name("demo").route("/").authUser(USER).authPassword(PASSWORD);
    if (port != 0) {
      b = b.port(port);
    }
    AgentBase a = b.build();
    a.defineTool(
        "say_hello", "greet", new LinkedHashMap<>(), (args, raw) -> new FunctionResult("ran"));
    a.defineTool(
        new ToolDefinition(
                "open_tool",
                "no credential required",
                new LinkedHashMap<>(),
                (args, raw) -> new FunctionResult("ran"))
            .setSecure(false));
    return a;
  }

  private static String basicAuth() {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((USER + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
  }

  private static String body(String fn, String callId) {
    String callPart = callId == null ? "" : ",\"call_id\":\"" + callId + "\"";
    return "{\"function\":\"" + fn + "\",\"argument\":{\"parsed\":[{}]}" + callPart + "}";
  }

  /** Drive the Lambda adapter's /swaig endpoint with an optional __token query parameter. */
  private static Map<String, Object> lambdaSwaig(
      AgentBase a, String fn, String callId, String tok) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("rawPath", "/swaig");
    event.put("requestContext", Map.of("http", Map.of("method", "POST")));
    event.put("headers", Map.of("authorization", basicAuth(), "content-type", "application/json"));
    if (tok != null) {
      event.put("queryStringParameters", Map.of("__token", tok));
    }
    event.put("body", body(fn, callId));
    LambdaResponse resp = new LambdaAgentHandler(a).handle(event);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", resp.getStatusCode());
    out.put("body", parse(resp.getBody()));
    return out;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parse(String json) {
    if (json == null || json.isEmpty()) return new LinkedHashMap<>();
    return GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
  }

  private static String responseOf(Map<String, Object> reduced) {
    @SuppressWarnings("unchecked")
    Map<String, Object> b = (Map<String, Object>) reduced.get("body");
    Object r = b.get("response");
    return r == null ? null : r.toString();
  }

  // ==================================================================
  // Transport: AWS Lambda adapter
  // ==================================================================

  @Test
  void lambdaSecureToolRunsWithAValidToken() {
    AgentBase a = agent();
    String token = a.createToolToken("say_hello", "c1");
    assertFalse(token.isEmpty(), "the fixture needs a genuinely minted token");

    Map<String, Object> out = lambdaSwaig(a, "say_hello", "c1", token);

    assertEquals(200.0, ((Number) out.get("status")).doubleValue());
    assertEquals("ran", responseOf(out), "a valid token must let the secure tool's handler run");
  }

  @Test
  void lambdaSecureToolIsRefusedWithAForgedToken() {
    AgentBase a = agent();
    Map<String, Object> out =
        lambdaSwaig(a, "say_hello", "c1", tamper(a.createToolToken("say_hello", "c1")));

    assertEquals(200.0, ((Number) out.get("status")).doubleValue(), "a refusal is 200, not 4xx");
    assertEquals(REFUSAL, responseOf(out), "a forged token must not reach the handler");
  }

  @Test
  void lambdaSecureToolIsRefusedWithNoTokenAtAll() {
    Map<String, Object> out = lambdaSwaig(agent(), "say_hello", "c1", null);

    assertEquals(200.0, ((Number) out.get("status")).doubleValue(), "a refusal is 200, not 4xx");
    assertEquals(
        REFUSAL, responseOf(out), "an absent token must fail closed, exactly like a forged one");
  }

  @Test
  void lambdaSecureToolIsRefusedWhenTheCallIdIsMissing() {
    AgentBase a = agent();
    // A token minted for a real call, but the request body omits call_id — there is
    // nothing to validate the token against, so it counts as unvalidated.
    String token = a.createToolToken("say_hello", "c1");

    Map<String, Object> out = lambdaSwaig(a, "say_hello", null, token);

    assertEquals(200.0, ((Number) out.get("status")).doubleValue(), "a refusal is 200, not 4xx");
    assertEquals(REFUSAL, responseOf(out), "no call_id means the token cannot be validated");
  }

  @Test
  void lambdaInsecureToolRunsUngatedInEveryTokenCase() {
    // valid-shaped token, forged token, no token, and no call_id — all must run.
    AgentBase a = agent();
    String good = a.createToolToken("open_tool", "c1");

    assertEquals("ran", responseOf(lambdaSwaig(a, "open_tool", "c1", good)));
    assertEquals("ran", responseOf(lambdaSwaig(a, "open_tool", "c1", "not-a-token")));
    assertEquals("ran", responseOf(lambdaSwaig(a, "open_tool", "c1", null)));
    assertEquals("ran", responseOf(lambdaSwaig(a, "open_tool", null, null)));
  }

  // ==================================================================
  // Transport: the in-process HTTP /swaig endpoint (Service)
  // ==================================================================

  /** Flip the last character of a base64url token envelope: same shape, broken signature. */
  private static String tamper(String token) {
    char last = token.charAt(token.length() - 1);
    return token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
  }

  @Test
  void httpSecureToolRunsWithAValidToken() throws Exception {
    Map<String, Object> out =
        HttpProbe.swaig("say_hello", "c1", a -> a.createToolToken("say_hello", "c1"));

    assertEquals(200, out.get("status"));
    assertEquals("ran", responseOf(out), "a valid token must let the secure tool's handler run");
  }

  @Test
  void httpSecureToolIsRefusedWithAForgedToken() throws Exception {
    Map<String, Object> out =
        HttpProbe.swaig("say_hello", "c1", a -> tamper(a.createToolToken("say_hello", "c1")));

    assertEquals(200, out.get("status"), "a refusal is 200, not 4xx");
    assertEquals(REFUSAL, responseOf(out), "a forged token must not reach the handler");
  }

  @Test
  void httpSecureToolIsRefusedWithNoTokenAtAll() throws Exception {
    Map<String, Object> out = HttpProbe.swaig("say_hello", "c1", null);

    assertEquals(200, out.get("status"), "a refusal is 200, not 4xx");
    assertEquals(
        REFUSAL, responseOf(out), "an absent token must fail closed, exactly like a forged one");
  }

  @Test
  void httpSecureToolIsRefusedWhenTheCallIdIsMissing() throws Exception {
    // A token minted for a real call, but the body omits call_id — nothing to check it against.
    Map<String, Object> out =
        HttpProbe.swaig("say_hello", null, a -> a.createToolToken("say_hello", "c1"));

    assertEquals(200, out.get("status"), "a refusal is 200, not 4xx");
    assertEquals(REFUSAL, responseOf(out), "no call_id means the token cannot be validated");
  }

  @Test
  void httpInsecureToolRunsUngatedInEveryTokenCase() throws Exception {
    assertEquals(
        "ran",
        responseOf(HttpProbe.swaig("open_tool", "c1", a -> a.createToolToken("open_tool", "c1"))));
    assertEquals("ran", responseOf(HttpProbe.swaig("open_tool", "c1", a -> "not-a-token")));
    assertEquals("ran", responseOf(HttpProbe.swaig("open_tool", "c1", null)));
    assertEquals("ran", responseOf(HttpProbe.swaig("open_tool", null, null)));
  }

  // ==================================================================
  // The transport-agnostic core itself
  // ==================================================================

  @Test
  void validationCoreReturnsNullOnlyForAGenuineTokenAndCallIdPair() {
    AgentBase a = agent();
    String token = a.createToolToken("say_hello", "c1");

    assertNull(a.swaigValidateToken("say_hello", token, "c1"), "a valid pair proceeds");
    assertNotNull(a.swaigValidateToken("say_hello", token, "other"), "wrong call_id is refused");
    assertNotNull(a.swaigValidateToken("say_hello", token, null), "absent call_id is refused");
    assertNotNull(a.swaigValidateToken("say_hello", null, "c1"), "absent token is refused");
    assertNotNull(a.swaigValidateToken("say_hello", "junk", "c1"), "forged token is refused");
    assertNull(a.swaigValidateToken("open_tool", null, null), "an insecure tool is never gated");
    assertNull(a.swaigValidateToken("unregistered", null, null), "an unknown tool is not our call");
  }

  @Test
  void aRefusalCarriesTheFunctionResultShape() {
    AgentBase a = agent();
    Map<String, Object> refusal = a.swaigValidateToken("say_hello", null, null);

    assertNotNull(refusal);
    assertEquals(REFUSAL, refusal.get("response"), "the refusal is a FunctionResult body");
  }

  /**
   * Drives a real {@link com.signalwire.sdk.swml.Service} HTTP listener on an ephemeral port.
   *
   * <p>The agent is built INSIDE the probe because the listening port is a build-time property, and
   * a token minted before the build would belong to a different {@link SessionManager}. The caller
   * therefore hands in a token-minting step rather than a pre-built agent.
   */
  private static final class HttpProbe {
    /** Mints the request's {@code __token} from the very agent that will serve the request. */
    interface TokenSource {
      String tokenFor(AgentBase agent);
    }

    static Map<String, Object> swaig(String fn, String callId, TokenSource tokens)
        throws Exception {
      int port;
      try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
        port = s.getLocalPort();
      }
      AgentBase a = agent(port);
      String tok = tokens == null ? null : tokens.tokenFor(a);
      a.run();
      try {
        String query = tok == null ? "" : "?__token=" + java.net.URLEncoder.encode(tok, "UTF-8");
        java.net.HttpURLConnection c =
            (java.net.HttpURLConnection)
                java.net
                    .URI
                    .create("http://127.0.0.1:" + port + "/swaig" + query)
                    .toURL()
                    .openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", basicAuth());
        c.setRequestProperty("Content-Type", "application/json");
        c.getOutputStream().write(body(fn, callId).getBytes(StandardCharsets.UTF_8));

        int status = c.getResponseCode();
        java.io.InputStream in = status < 400 ? c.getInputStream() : c.getErrorStream();
        String text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", status);
        out.put("body", parse(text));
        return out;
      } finally {
        a.stop();
      }
    }
  }
}
