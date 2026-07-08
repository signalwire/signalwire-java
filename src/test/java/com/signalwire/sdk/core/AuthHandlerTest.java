/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signalwire.sdk.core.AuthHandler.AuthResult;
import com.signalwire.sdk.core.AuthHandler.BasicCredentials;
import com.signalwire.sdk.core.AuthHandler.BearerCredentials;
import com.signalwire.sdk.core.AuthHandler.Response;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Real-behavior tests for {@link AuthHandler} (parity with Python auth_handler.AuthHandler). The
 * framework-bound Python methods flask_decorator / get_fastapi_dependency are exercised via their
 * Java-native equivalents.
 */
class AuthHandlerTest {

  /** Test double exposing the readers AuthHandler reflects on (bearerToken / apiKey / header). */
  static class FakeConfig extends SecurityConfig {
    private final String user;
    private final String password;
    private final String bearerToken;
    private final String apiKey;
    private final String apiKeyHeader;

    FakeConfig(
        String user, String password, String bearerToken, String apiKey, String apiKeyHeader) {
      this.user = user;
      this.password = password;
      this.bearerToken = bearerToken;
      this.apiKey = apiKey;
      this.apiKeyHeader = apiKeyHeader;
    }

    @Override
    public String[] getBasicAuth() {
      return new String[] {user, password};
    }

    public String getBearerToken() {
      return bearerToken;
    }

    public String getApiKey() {
      return apiKey;
    }

    public String getApiKeyHeader() {
      return apiKeyHeader;
    }
  }

  private AuthHandler handler(
      String user, String pass, String bearer, String apiKey, String apiKeyHeader) {
    return new AuthHandler(new FakeConfig(user, pass, bearer, apiKey, apiKeyHeader));
  }

  private Map<String, String> basicHeaders(String user, String pass) {
    Map<String, String> h = new LinkedHashMap<>();
    String token =
        Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
    h.put("Authorization", "Basic " + token);
    return h;
  }

  @Test
  void verifyBasicAuthAcceptsCorrect() {
    AuthHandler h = handler("alice", "secret", null, null, null);
    assertTrue(h.verifyBasicAuth(new BasicCredentials("alice", "secret")));
  }

  @Test
  void verifyBasicAuthRejectsWrong() {
    AuthHandler h = handler("alice", "secret", null, null, null);
    assertFalse(h.verifyBasicAuth(new BasicCredentials("alice", "nope")));
    assertFalse(h.verifyBasicAuth(new BasicCredentials("bob", "secret")));
  }

  @Test
  void verifyBearerToken() {
    AuthHandler h = handler("signalwire", "pw", "tok123", null, null);
    assertTrue(h.verifyBearerToken(new BearerCredentials("tok123")));
    assertFalse(h.verifyBearerToken(new BearerCredentials("wrong")));
  }

  @Test
  void verifyBearerDisabledWithoutToken() {
    AuthHandler h = handler("signalwire", "pw", null, null, null);
    assertFalse(h.verifyBearerToken(new BearerCredentials("anything")));
  }

  @Test
  void verifyApiKey() {
    AuthHandler h = handler("signalwire", "pw", null, "key-abc", null);
    assertTrue(h.verifyApiKey("key-abc"));
    assertFalse(h.verifyApiKey("key-xyz"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void getAuthInfoBasicHasUsernameNotPassword() {
    Map<String, Object> info = handler("alice", "secret", null, null, null).getAuthInfo();
    Map<String, Object> basic = (Map<String, Object>) info.get("basic");
    assertEquals("alice", basic.get("username"));
    assertFalse(basic.containsValue("secret"));
    assertFalse(basic.containsKey("password"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void getAuthInfoBearerAndApiKeyHideSecrets() {
    Map<String, Object> info = handler("signalwire", "pw", "tok", "k", null).getAuthInfo();
    Map<String, Object> bearer = (Map<String, Object>) info.get("bearer");
    assertEquals(true, bearer.get("enabled"));
    assertFalse(bearer.containsKey("token"));
    Map<String, Object> apiKey = (Map<String, Object>) info.get("api_key");
    assertEquals("X-API-Key", apiKey.get("header"));
    assertFalse(apiKey.containsKey("key"));
  }

  @Test
  void flaskDecoratorPassesAuthedRequest() {
    AuthHandler h = handler("u", "p", null, null, null);
    AuthHandler.RequestHandler inner = headers -> new Response(200, Map.of(), "ok");
    AuthHandler.RequestHandler app = h.flaskDecorator(inner);

    Response res = app.handle(basicHeaders("u", "p"));
    assertEquals(200, res.status());
  }

  @Test
  void flaskDecoratorRejectsBadCreds() {
    AuthHandler h = handler("u", "p", null, null, null);
    AuthHandler.RequestHandler inner = headers -> new Response(200, Map.of(), "ok");
    AuthHandler.RequestHandler app = h.flaskDecorator(inner);

    Response res = app.handle(basicHeaders("u", "wrong"));
    assertEquals(401, res.status());
    assertTrue(res.headers().containsKey("www-authenticate"));
  }

  @Test
  void flaskDecoratorAcceptsBearer() {
    AuthHandler h = handler("signalwire", "pw", "tok", null, null);
    AuthHandler.RequestHandler app = h.flaskDecorator(headers -> new Response(200, Map.of(), "ok"));

    Response res = app.handle(Map.of("Authorization", "Bearer tok"));
    assertEquals(200, res.status());
  }

  @Test
  void flaskDecoratorAcceptsApiKey() {
    AuthHandler h = handler("signalwire", "pw", null, "secretkey", "X-API-Key");
    AuthHandler.RequestHandler app = h.flaskDecorator(headers -> new Response(200, Map.of(), "ok"));

    Response res = app.handle(Map.of("X-API-Key", "secretkey"));
    assertEquals(200, res.status());
  }

  @Test
  void fastapiDependencyReturnsResultWhenAuthed() {
    AuthHandler h = handler("u", "p", null, null, null);
    Function<Map<String, String>, AuthResult> dep = h.getFastapiDependency();

    AuthResult result = dep.apply(basicHeaders("u", "p"));
    assertTrue(result.authenticated());
    assertEquals("basic", result.method());
  }

  @Test
  void fastapiDependencyRaisesWhenRequiredAndUnauthed() {
    AuthHandler h = handler("u", "p", null, null, null);
    Function<Map<String, String>, AuthResult> dep = h.getFastapiDependency(false);

    AuthHandler.AuthException err =
        assertThrows(AuthHandler.AuthException.class, () -> dep.apply(basicHeaders("u", "bad")));
    assertEquals(401, err.getResponse().status());
  }

  @Test
  void fastapiDependencyOptionalDoesNotRaise() {
    AuthHandler h = handler("u", "p", null, null, null);
    Function<Map<String, String>, AuthResult> dep = h.getFastapiDependency(true);

    AuthResult result = dep.apply(basicHeaders("u", "bad"));
    assertFalse(result.authenticated());
    assertNull(result.method());
  }
}
