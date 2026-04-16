package com.signalwire.sdk.runtime.lambda;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.runtime.EnvProvider;
import com.signalwire.sdk.swaig.FunctionResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LambdaAgentHandler}. Covers:
 * <ul>
 *   <li>Path routing (root route, non-root route, health, 404)</li>
 *   <li>Basic auth (including the 401 path for invalid credentials)</li>
 *   <li>SWAIG dispatch with parsed args</li>
 *   <li>Post-prompt summary callback invocation</li>
 *   <li>SWML rendering with Lambda-derived webhook URLs</li>
 *   <li><b>The route-preservation regression test</b> — non-root route +
 *       Lambda env vars + {@code SWML_PROXY_URL_BASE} must still emit
 *       webhook URLs that include the agent's route before {@code /swaig}.</li>
 * </ul>
 */
class LambdaAgentHandlerTest {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static EnvProvider envOf(Map<String, String> m) {
        return name -> m.get(name);
    }

    private static String basicAuth(String user, String pass) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (user + ":" + pass).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, Object> v2Event(String method, String path,
                                               Map<String, String> headers,
                                               String body) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("version", "2.0");
        event.put("rawPath", path);
        event.put("headers", headers != null ? headers : new LinkedHashMap<>());
        if (body != null) event.put("body", body);
        Map<String, Object> http = new LinkedHashMap<>();
        http.put("method", method);
        event.put("requestContext", Map.of("http", http));
        return event;
    }

    private static AgentBase buildAgent(String route) {
        AgentBase agent = AgentBase.builder()
                .name("lambda-agent")
                .route(route)
                .authUser("u")
                .authPassword("p")
                .build();
        agent.setPromptText("You are helpful.");
        agent.defineTool("greet", "Greet someone",
                Map.of("type", "object", "properties",
                        Map.of("name", Map.of("type", "string"))),
                (args, raw) -> new FunctionResult(
                        "Hello, " + args.getOrDefault("name", "friend") + "!"));
        return agent;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseBody(LambdaResponse r) {
        return GSON.fromJson(r.getBody(), MAP_TYPE);
    }

    // -----------------------------------------------------------------
    // Health / readiness
    // -----------------------------------------------------------------

    @Test
    void healthEndpointRequiresNoAuth() {
        var handler = new LambdaAgentHandler(buildAgent("/"), envOf(Map.of()));
        LambdaResponse r = handler.handle(v2Event("GET", "/health", Map.of(), null));
        assertEquals(200, r.getStatusCode());
        assertEquals("healthy", parseBody(r).get("status"));
    }

    @Test
    void readyEndpointRequiresNoAuth() {
        var handler = new LambdaAgentHandler(buildAgent("/"), envOf(Map.of()));
        LambdaResponse r = handler.handle(v2Event("GET", "/ready", Map.of(), null));
        assertEquals(200, r.getStatusCode());
        assertEquals("ready", parseBody(r).get("status"));
    }

    // -----------------------------------------------------------------
    // Auth
    // -----------------------------------------------------------------

    @Test
    void swmlRequiresAuth() {
        var handler = new LambdaAgentHandler(buildAgent("/"), envOf(Map.of()));
        LambdaResponse r = handler.handle(v2Event("GET", "/", Map.of(), null));
        assertEquals(401, r.getStatusCode());
        assertTrue(r.getHeaders().containsKey("WWW-Authenticate"));
    }

    @Test
    void swmlWithBadPasswordRejected() {
        var handler = new LambdaAgentHandler(buildAgent("/"), envOf(Map.of()));
        Map<String, String> headers = Map.of("Authorization", basicAuth("u", "WRONG"));
        LambdaResponse r = handler.handle(v2Event("GET", "/", headers, null));
        assertEquals(401, r.getStatusCode());
    }

    @Test
    void swmlWithValidAuthReturns200() {
        var handler = new LambdaAgentHandler(buildAgent("/"),
                envOf(Map.of("AWS_LAMBDA_FUNCTION_URL",
                        "https://xyz.lambda-url.us-east-1.on.aws")));
        Map<String, String> headers = Map.of("Authorization", basicAuth("u", "p"));
        LambdaResponse r = handler.handle(v2Event("GET", "/", headers, null));
        assertEquals(200, r.getStatusCode());
        assertNotNull(parseBody(r).get("sections"));
    }

    // -----------------------------------------------------------------
    // SWAIG
    // -----------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void swaigExecutesRegisteredTool() {
        var handler = new LambdaAgentHandler(buildAgent("/"),
                envOf(Map.of("AWS_LAMBDA_FUNCTION_URL",
                        "https://xyz.lambda-url.us-east-1.on.aws")));
        Map<String, String> headers = Map.of(
                "Authorization", basicAuth("u", "p"),
                "Content-Type", "application/json");
        String payload = GSON.toJson(Map.of(
                "function", "greet",
                "argument", Map.of("parsed", List.of(Map.of("name", "Ada")))));
        LambdaResponse r = handler.handle(v2Event("POST", "/swaig", headers, payload));
        assertEquals(200, r.getStatusCode());
        Map<String, Object> body = parseBody(r);
        assertEquals("Hello, Ada!", body.get("response"));
    }

    @Test
    void swaigUnknownFunctionReturns404() {
        var handler = new LambdaAgentHandler(buildAgent("/"), envOf(Map.of()));
        Map<String, String> headers = Map.of("Authorization", basicAuth("u", "p"));
        String payload = GSON.toJson(Map.of("function", "nope"));
        LambdaResponse r = handler.handle(v2Event("POST", "/swaig", headers, payload));
        assertEquals(404, r.getStatusCode());
    }

    @Test
    void swaigBase64BodyDecoded() {
        var handler = new LambdaAgentHandler(buildAgent("/"), envOf(Map.of()));
        Map<String, String> headers = Map.of("Authorization", basicAuth("u", "p"));
        String json = GSON.toJson(Map.of(
                "function", "greet",
                "argument", Map.of("parsed", List.of(Map.of("name", "Grace")))));
        String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> event = v2Event("POST", "/swaig", headers, b64);
        event.put("isBase64Encoded", true);

        LambdaResponse r = handler.handle(event);
        assertEquals(200, r.getStatusCode());
        assertEquals("Hello, Grace!", parseBody(r).get("response"));
    }

    @Test
    void swaigWrongMethodReturns405() {
        var handler = new LambdaAgentHandler(buildAgent("/"), envOf(Map.of()));
        Map<String, String> headers = Map.of("Authorization", basicAuth("u", "p"));
        LambdaResponse r = handler.handle(v2Event("GET", "/swaig", headers, null));
        assertEquals(405, r.getStatusCode());
    }

    // -----------------------------------------------------------------
    // Post-prompt
    // -----------------------------------------------------------------

    @Test
    void postPromptInvokesSummaryCallback() {
        AgentBase agent = buildAgent("/");
        final Map<String, Object>[] captured = new Map[2];
        agent.onSummary((parsed, raw) -> {
            captured[0] = parsed;
            captured[1] = raw;
        });
        var handler = new LambdaAgentHandler(agent, envOf(Map.of()));
        Map<String, String> headers = Map.of("Authorization", basicAuth("u", "p"));
        String payload = GSON.toJson(Map.of(
                "post_prompt_data", Map.of("parsed", Map.of("name", "Test"))));
        LambdaResponse r = handler.handle(v2Event("POST", "/post_prompt", headers, payload));
        assertEquals(200, r.getStatusCode());
        assertNotNull(captured[0]);
        assertEquals("Test", captured[0].get("name"));
    }

    // -----------------------------------------------------------------
    // Non-root route handling
    // -----------------------------------------------------------------

    @Test
    void nonRootRouteDispatchesSwml() {
        var handler = new LambdaAgentHandler(buildAgent("/my-agent"),
                envOf(Map.of("AWS_LAMBDA_FUNCTION_URL",
                        "https://xyz.lambda-url.us-east-1.on.aws")));
        Map<String, String> headers = Map.of("Authorization", basicAuth("u", "p"));
        LambdaResponse r = handler.handle(v2Event("GET", "/my-agent", headers, null));
        assertEquals(200, r.getStatusCode());
        assertNotNull(parseBody(r).get("sections"));
    }

    @Test
    void pathOutsideAgentRouteReturns404() {
        var handler = new LambdaAgentHandler(buildAgent("/my-agent"), envOf(Map.of()));
        Map<String, String> headers = Map.of("Authorization", basicAuth("u", "p"));
        LambdaResponse r = handler.handle(v2Event("GET", "/different", headers, null));
        assertEquals(404, r.getStatusCode());
    }

    // -----------------------------------------------------------------
    // Route-preservation invariant (THE regression test)
    // -----------------------------------------------------------------

    /**
     * Regression test for the bug fixed in Python and TypeScript.
     *
     * <p>An agent mounted at a non-root route MUST have its route
     * preserved in rendered webhook URLs, even when a proxy base URL
     * and Lambda env vars are both present. The proxy short-circuit
     * must NOT return the bare proxy URL without the agent's route.
     */
    @Test
    @SuppressWarnings("unchecked")
    void routePreservedWithProxyBaseAndLambdaEnv() {
        // Simulate the problematic combo: non-root route + Lambda env
        // vars + explicit SWML_PROXY_URL_BASE. The proxy base wins as
        // the origin, but the agent's route and /swaig MUST still be
        // appended.
        AgentBase agent = buildAgent("/my-agent");
        agent.manualSetProxyUrl("https://xyz.lambda-url.us-east-1.on.aws");

        var env = envOf(Map.of(
                "AWS_LAMBDA_FUNCTION_NAME", "something-else",
                "AWS_LAMBDA_FUNCTION_URL", "https://DIFFERENT.lambda-url.us-east-1.on.aws"));
        var handler = new LambdaAgentHandler(agent, env);

        Map<String, String> headers = Map.of("Authorization", basicAuth("u", "p"));
        LambdaResponse r = handler.handle(v2Event("GET", "/my-agent", headers, null));
        assertEquals(200, r.getStatusCode());

        String swaigUrl = extractFirstSwaigUrl(parseBody(r));
        assertNotNull(swaigUrl, "expected at least one SWAIG function in rendered SWML");
        // The proxy base wins as origin.
        assertTrue(swaigUrl.contains("xyz.lambda-url.us-east-1.on.aws"),
                "expected proxy origin in webhook URL but got: " + swaigUrl);
        // The route MUST be present before /swaig.
        assertTrue(swaigUrl.contains("/my-agent/swaig"),
                "expected '/my-agent/swaig' in webhook URL but got: " + swaigUrl);
        // Should NEVER be the bare proxy URL + /swaig (the pre-fix bug).
        assertFalse(swaigUrl.endsWith(".on.aws/swaig"),
                "BUG REGRESSION: webhook URL lost the agent's route: " + swaigUrl);
    }

    /**
     * Complementary regression test: non-root route + Lambda env vars
     * only (no explicit proxy). The synthesised Lambda URL must also
     * have the route appended.
     */
    @Test
    void routePreservedWithSynthesisedLambdaUrl() {
        // No proxy this time: we rely on env var synthesis.
        AgentBase agent = buildAgent("/widgets");

        var env = envOf(Map.of(
                "AWS_LAMBDA_FUNCTION_NAME", "widget-fn",
                "AWS_REGION", "us-west-2"));
        var handler = new LambdaAgentHandler(agent, env);

        Map<String, String> headers = Map.of("Authorization", basicAuth("u", "p"));
        LambdaResponse r = handler.handle(v2Event("GET", "/widgets", headers, null));
        assertEquals(200, r.getStatusCode());

        String swaigUrl = extractFirstSwaigUrl(parseBody(r));
        assertNotNull(swaigUrl);
        // The synthesised Lambda URL is only used when the agent's
        // own proxy-base resolution returns null. In the default
        // {@link AgentBase#build()} path, SWML_PROXY_URL_BASE is read
        // from the real process env — which in tests is not set —
        // but ExecutionMode.detect() also reads the real env, so
        // we can't fully control it here. The invariant we assert is
        // the one that always holds: /widgets/swaig in the URL.
        assertTrue(swaigUrl.contains("/widgets/swaig"),
                "expected '/widgets/swaig' in webhook URL but got: " + swaigUrl);
    }

    // -----------------------------------------------------------------
    // Dynamic config callback integration
    // -----------------------------------------------------------------

    @Test
    void dynamicConfigCallbackInvoked() {
        AgentBase agent = buildAgent("/");
        final boolean[] called = {false};
        agent.setDynamicConfigCallback((query, body, hdrs, a) -> {
            called[0] = true;
            a.setPromptText("OVERRIDDEN");
        });

        var handler = new LambdaAgentHandler(agent,
                envOf(Map.of("AWS_LAMBDA_FUNCTION_URL",
                        "https://xyz.lambda-url.us-east-1.on.aws")));
        Map<String, String> headers = Map.of("Authorization", basicAuth("u", "p"));
        LambdaResponse r = handler.handle(v2Event("GET", "/", headers, null));
        assertEquals(200, r.getStatusCode());
        assertTrue(called[0], "dynamic config callback must be invoked");
    }

    // -----------------------------------------------------------------
    // Event shape compatibility
    // -----------------------------------------------------------------

    @Test
    void apiGatewayRestV1EventShapeWorks() {
        // API Gateway REST v1 uses 'httpMethod' and 'path' instead of
        // 'requestContext.http.method' / 'rawPath'.
        var handler = new LambdaAgentHandler(buildAgent("/"), envOf(Map.of()));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("httpMethod", "GET");
        event.put("path", "/health");
        event.put("headers", Map.of());
        LambdaResponse r = handler.handle(event);
        assertEquals(200, r.getStatusCode());
        assertEquals("healthy", parseBody(r).get("status"));
    }

    @Test
    void nullEventTreatedAsRootGet() {
        var handler = new LambdaAgentHandler(buildAgent("/"), envOf(Map.of()));
        LambdaResponse r = handler.handle(null);
        // Unauthenticated → 401.
        assertEquals(401, r.getStatusCode());
    }

    // -----------------------------------------------------------------
    // stripAgentPath helper
    // -----------------------------------------------------------------

    @Test
    void stripAgentPathRoot() {
        assertEquals("/swaig", LambdaAgentHandler.stripAgentPath("/swaig", ""));
        assertEquals("/", LambdaAgentHandler.stripAgentPath("/", ""));
    }

    @Test
    void stripAgentPathNonRoot() {
        assertEquals("/swaig", LambdaAgentHandler.stripAgentPath("/foo/swaig", "/foo"));
        assertEquals("", LambdaAgentHandler.stripAgentPath("/foo", "/foo"));
    }

    @Test
    void stripAgentPathMismatch() {
        assertNull(LambdaAgentHandler.stripAgentPath("/bar/swaig", "/foo"));
    }

    // -----------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static String extractFirstSwaigUrl(Map<String, Object> swml) {
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        if (sections == null) return null;
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
        if (main == null) return null;
        for (Map<String, Object> verb : main) {
            if (verb.containsKey("ai")) {
                Map<String, Object> ai = (Map<String, Object>) verb.get("ai");
                Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
                if (swaig == null) return null;
                List<Map<String, Object>> fns = (List<Map<String, Object>>) swaig.get("functions");
                if (fns == null || fns.isEmpty()) return null;
                return (String) fns.get(0).get("web_hook_url");
            }
        }
        return null;
    }
}
