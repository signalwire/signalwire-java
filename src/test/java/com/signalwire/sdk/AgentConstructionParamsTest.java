package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swml.Service;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Construction-contract parity for {@link AgentBase} and {@link Service}.
 *
 * <p>Ported from the Python reference's {@code tests/unit/core/test_agent_base.py} ({@code
 * TestAgentBaseInitialization}) and {@code tests/unit/core/test_swml_service.py} ({@code
 * TestSWMLServiceInitialization}). The reference's {@code AgentBase.__init__} forwards {@code
 * schema_path} / {@code config_file} / {@code schema_validation} to {@code SWMLService.__init__}
 * and {@code token_expiry_secs} to {@code SessionManager}; it stores {@code agent_id}, {@code
 * use_pom}, {@code native_functions}, {@code default_webhook_url}, {@code suppress_logs}, {@code
 * enable_post_prompt_override} and {@code check_for_input_override} on itself. These tests drive
 * that whole chain through the Java builder idiom.
 */
class AgentConstructionParamsTest {

  // ---------------------------------------------------------------
  // agent_id — reference: self.agent_id = agent_id or str(uuid.uuid4())
  // ---------------------------------------------------------------

  @Test
  void agentIdDefaultsToAGeneratedUuid() {
    AgentBase a = AgentBase.builder().name("a").authUser("u").authPassword("p").build();
    assertNotNull(a.getAgentId());
    assertFalse(a.getAgentId().isEmpty());

    AgentBase b = AgentBase.builder().name("b").authUser("u").authPassword("p").build();
    assertNotEquals(a.getAgentId(), b.getAgentId(), "each agent gets its own generated id");
  }

  @Test
  void agentIdBuilderOverrideWins() {
    AgentBase a =
        AgentBase.builder().name("a").agentId("custom-id").authUser("u").authPassword("p").build();
    assertEquals("custom-id", a.getAgentId());
  }

  // ---------------------------------------------------------------
  // use_pom — reference: self._use_pom, self.pom = PromptObjectModel() or None
  // ---------------------------------------------------------------

  @Test
  void usePomTrueByDefaultAndPomSectionsRender() {
    AgentBase a = AgentBase.builder().name("a").authUser("u").authPassword("p").build();
    assertTrue(a.isUsePom());
    a.promptAddSection("Role", "You are helpful.", null);
    assertNotNull(a.getPom());
  }

  @Test
  void usePomFalseDisablesPom() {
    AgentBase a =
        AgentBase.builder().name("a").usePom(false).authUser("u").authPassword("p").build();
    assertFalse(a.isUsePom());
    assertNull(a.getPom(), "reference: agent.pom is None when use_pom=False");
  }

  // ---------------------------------------------------------------
  // native_functions — reference: self.native_functions = native_functions or []
  // renders into ai.SWAIG.native_functions
  // ---------------------------------------------------------------

  @Test
  void nativeFunctionsDefaultsToEmpty() {
    AgentBase a = AgentBase.builder().name("a").authUser("u").authPassword("p").build();
    assertEquals(List.of(), a.getNativeFunctions());
  }

  @Test
  @SuppressWarnings("unchecked")
  void nativeFunctionsFromBuilderReachTheWire() {
    AgentBase a =
        AgentBase.builder()
            .name("a")
            .nativeFunctions(List.of("transfer", "check_time"))
            .authUser("u")
            .authPassword("p")
            .build();
    assertEquals(List.of("transfer", "check_time"), a.getNativeFunctions());

    Map<String, Object> swml = a.renderSwml("http://localhost:3000");
    Map<String, Object> ai = findAiVerb(swml);
    Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
    assertNotNull(swaig, "SWAIG object must be emitted when native_functions are set");
    assertEquals(List.of("transfer", "check_time"), swaig.get("native_functions"));
  }

  // ---------------------------------------------------------------
  // default_webhook_url — reference stores it and uses it as the SWAIG
  // defaults.web_hook_url when set.
  // ---------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void defaultWebhookUrlOverridesTheDerivedSwaigWebhook() {
    AgentBase a =
        AgentBase.builder()
            .name("a")
            .defaultWebhookUrl("https://example.test/hook")
            .authUser("u")
            .authPassword("p")
            .build();
    assertEquals("https://example.test/hook", a.getDefaultWebhookUrl());

    a.defineTool("noop", "does nothing", Map.of(), (args, raw) -> new FunctionResult("ok"));
    Map<String, Object> swml = a.renderSwml("http://localhost:3000");
    Map<String, Object> ai = findAiVerb(swml);
    Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
    List<Map<String, Object>> fns = (List<Map<String, Object>>) swaig.get("functions");
    assertNotNull(fns);
    assertEquals(
        "https://example.test/hook",
        fns.get(0).get("web_hook_url"),
        "the builder default_webhook_url must be the webhook the wire carries");
  }

  // ---------------------------------------------------------------
  // suppress_logs / enable_post_prompt_override / check_for_input_override
  // ---------------------------------------------------------------

  @Test
  void suppressLogsIsSettableAndDefaultsFalse() {
    AgentBase a = AgentBase.builder().name("a").authUser("u").authPassword("p").build();
    assertFalse(a.isSuppressLogs());

    AgentBase quiet =
        AgentBase.builder().name("q").suppressLogs(true).authUser("u").authPassword("p").build();
    assertTrue(quiet.isSuppressLogs());
  }

  @Test
  void postPromptAndCheckForInputOverridesAreSettable() {
    AgentBase a = AgentBase.builder().name("a").authUser("u").authPassword("p").build();
    assertFalse(a.isEnablePostPromptOverride());
    assertFalse(a.isCheckForInputOverride());

    AgentBase b =
        AgentBase.builder()
            .name("b")
            .enablePostPromptOverride(true)
            .checkForInputOverride(true)
            .authUser("u")
            .authPassword("p")
            .build();
    assertTrue(b.isEnablePostPromptOverride());
    assertTrue(b.isCheckForInputOverride());
  }

  // ---------------------------------------------------------------
  // token_expiry_secs — forwarded to SessionManager (reference line 247)
  // ---------------------------------------------------------------

  @Test
  void tokenExpirySecsIsForwardedToTheSessionManager() {
    AgentBase a =
        AgentBase.builder().name("a").tokenExpirySecs(7200).authUser("u").authPassword("p").build();
    assertEquals(7200, a.getTokenExpirySecs());

    // Behavioral: the token minted by this agent carries the configured expiry.
    String token = a.createToolToken("noop", "call-1");
    assertNotNull(token);
    long expiry = expiryOf(token);
    long now = System.currentTimeMillis() / 1000L;
    assertTrue(
        expiry - now > 7000 && expiry - now <= 7200,
        "token expiry should reflect the 7200s builder value, got delta " + (expiry - now));
  }

  @Test
  void tokenExpirySecsDefaultsTo3600() {
    AgentBase a = AgentBase.builder().name("a").authUser("u").authPassword("p").build();
    assertEquals(3600, a.getTokenExpirySecs());
    String token = a.createToolToken("noop", "call-1");
    long delta = expiryOf(token) - System.currentTimeMillis() / 1000L;
    assertTrue(delta > 3400 && delta <= 3600, "default expiry should be 3600s, got " + delta);
  }

  // ---------------------------------------------------------------
  // schema_validation — forwarded to SWMLService/SchemaUtils
  // ---------------------------------------------------------------

  @Test
  void schemaValidationDefaultsTrueAndIsDisablable() {
    AgentBase on = AgentBase.builder().name("a").authUser("u").authPassword("p").build();
    assertTrue(on.isSchemaValidation());

    AgentBase off =
        AgentBase.builder()
            .name("a")
            .schemaValidation(false)
            .authUser("u")
            .authPassword("p")
            .build();
    assertFalse(off.isSchemaValidation());
    assertFalse(
        off.fullValidationEnabled(), "reference: schema_validation=False disables full validation");
  }

  @Test
  void serviceSchemaValidationIsAConstructionParam() {
    Service on = new Service("svc", "/", "0.0.0.0", 3000, "u", "p", null, null, true);
    assertTrue(on.isSchemaValidation());

    Service off = new Service("svc", "/", "0.0.0.0", 3000, "u", "p", null, null, false);
    assertFalse(off.isSchemaValidation());
    assertFalse(off.fullValidationEnabled());
  }

  // ---------------------------------------------------------------
  // schema_path — forwarded to SWMLService -> SchemaUtils
  // ---------------------------------------------------------------

  @Test
  void schemaPathIsForwardedToSchemaUtils(@TempDir Path tmp) throws Exception {
    Path schema = tmp.resolve("custom_schema.json");
    Files.writeString(
        schema,
        "{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"$defs\":{},"
            + "\"properties\":{\"sections\":{}}}");

    AgentBase a =
        AgentBase.builder()
            .name("a")
            .schemaPath(schema.toString())
            .authUser("u")
            .authPassword("p")
            .build();
    assertEquals(schema.toString(), a.getSchemaPath());
    assertEquals(schema.toString(), a.getSchemaUtils().getSchemaPath());
  }

  @Test
  void serviceSchemaPathIsAConstructionParam(@TempDir Path tmp) throws Exception {
    Path schema = tmp.resolve("s.json");
    Files.writeString(schema, "{\"$defs\":{}}");
    Service svc = new Service("svc", "/", "0.0.0.0", 3000, "u", "p", schema.toString(), null, true);
    assertEquals(schema.toString(), svc.getSchemaPath());
    assertEquals(schema.toString(), svc.getSchemaUtils().getSchemaPath());
  }

  // ---------------------------------------------------------------
  // config_file — the capability the port already had (ConfigLoader) but
  // could not reach from AgentBase. Reference `_load_service_config` reads
  // the `service` section; explicit constructor params win over it.
  // ---------------------------------------------------------------

  @Test
  void configFileSuppliesServiceRouteHostPortAndName(@TempDir Path tmp) throws Exception {
    Path cfg = tmp.resolve("agent_config.json");
    Files.writeString(
        cfg,
        "{\"service\":{\"name\":\"from-config\",\"route\":\"/cfg\","
            + "\"host\":\"127.0.0.1\",\"port\":9123}}");

    AgentBase a =
        AgentBase.builder()
            .name("placeholder")
            .configFile(cfg.toString())
            .authUser("u")
            .authPassword("p")
            .build();

    assertEquals("from-config", a.getName());
    assertEquals("/cfg", a.getRoute());
    assertEquals("127.0.0.1", a.getHost());
    assertEquals(9123, a.getPort());
    assertEquals(cfg.toString(), a.getConfigFile());
  }

  @Test
  void explicitBuilderParamsBeatTheConfigFile(@TempDir Path tmp) throws Exception {
    Path cfg = tmp.resolve("agent_config.json");
    Files.writeString(
        cfg, "{\"service\":{\"route\":\"/cfg\",\"host\":\"127.0.0.1\",\"port\":9123}}");

    AgentBase a =
        AgentBase.builder()
            .name("a")
            .route("/explicit")
            .host("10.0.0.1")
            .port(4321)
            .configFile(cfg.toString())
            .authUser("u")
            .authPassword("p")
            .build();

    assertEquals("/explicit", a.getRoute());
    assertEquals("10.0.0.1", a.getHost());
    assertEquals(4321, a.getPort());
  }

  @Test
  void configFileWithoutAServiceSectionIsHarmless(@TempDir Path tmp) throws Exception {
    Path cfg = tmp.resolve("agent_config.json");
    Files.writeString(cfg, "{\"security\":{\"ssl_enabled\":false}}");

    AgentBase a =
        AgentBase.builder()
            .name("a")
            .route("/r")
            .configFile(cfg.toString())
            .authUser("u")
            .authPassword("p")
            .build();
    assertEquals("a", a.getName());
    assertEquals("/r", a.getRoute());
  }

  @Test
  void serviceConfigFileIsAConstructionParam(@TempDir Path tmp) throws Exception {
    Path cfg = tmp.resolve("agent_config.json");
    Files.writeString(cfg, "{\"security\":{\"ssl_enabled\":true,\"domain\":\"cfg.example\"}}");

    Service svc = new Service("svc", "/", "0.0.0.0", 3000, "u", "p", null, cfg.toString(), true);
    assertEquals(cfg.toString(), svc.getConfigFile());
    // The config file reaches SecurityConfig — the collaborator the reference
    // forwards config_file to (swml_service.py:139).
    assertTrue(svc.getSecurity().isSslEnabled());
    assertEquals("cfg.example", svc.getSecurity().getDomain());
  }

  // ---------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static Map<String, Object> findAiVerb(Map<String, Object> swml) {
    Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
    List<Object> main = (List<Object>) sections.get("main");
    for (Object verb : main) {
      if (verb instanceof Map<?, ?> m && m.containsKey("ai")) {
        return (Map<String, Object>) m.get("ai");
      }
    }
    throw new AssertionError("no ai verb in rendered SWML: " + swml);
  }

  /** Token format is {@code callId.function.expiry.nonce.sig}, base64url-encoded. */
  private static long expiryOf(String token) {
    byte[] raw = java.util.Base64.getUrlDecoder().decode(token);
    String[] parts = new String(raw, java.nio.charset.StandardCharsets.UTF_8).split("\\.", -1);
    return Long.parseLong(parts[2]);
  }
}
