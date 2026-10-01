/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Tests for {@link SwmlRenderer#renderSwml} / {@link SwmlRenderer#renderFunctionResponseSwml} —
 * render a full SWML doc and assert its exact structure and wire keys.
 */
class SwmlRendererTest {

  private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

  private Service newService() {
    return new Service("renderer-test");
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> mainOf(String json) {
    Map<String, Object> doc = new Gson().fromJson(json, MAP_TYPE);
    Map<String, Object> sections = (Map<String, Object>) doc.get("sections");
    return (List<Map<String, Object>>) sections.get("main");
  }

  @Test
  @SuppressWarnings("unchecked")
  void testRenderSwmlBasicTextPrompt() {
    String json = SwmlRenderer.renderSwml("you are helpful", newService());
    List<Map<String, Object>> main = mainOf(json);
    assertEquals(1, main.size());
    Map<String, Object> ai = (Map<String, Object>) main.get(0).get("ai");
    assertEquals(Map.of("text", "you are helpful"), ai.get("prompt"));
  }

  @Test
  void testRenderSwmlAddAnswerPrecedesAi() {
    String json =
        SwmlRenderer.renderSwml(SwmlRenderer.RenderOptions.of("hi", newService()).addAnswer(true));
    List<Map<String, Object>> main = mainOf(json);
    assertEquals("answer", firstKey(main.get(0)));
    assertEquals("ai", firstKey(main.get(1)));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testRenderSwmlRecordCallWireKeys() {
    String json =
        SwmlRenderer.renderSwml(
            SwmlRenderer.RenderOptions.of("hi", newService())
                .recordCall(true)
                .recordFormat("wav")
                .recordStereo(false));
    Map<String, Object> rc =
        (Map<String, Object>)
            mainOf(json).stream()
                .filter(v -> v.containsKey("record_call"))
                .findFirst()
                .orElseThrow()
                .get("record_call");
    // Exact wire keys: format + stereo.
    assertEquals("wav", rc.get("format"));
    assertEquals(false, rc.get("stereo"));
    assertEquals(2, rc.size());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testRenderSwmlDefaultWebhookUrlBecomesSwaigDefaults() {
    String json =
        SwmlRenderer.renderSwml(
            SwmlRenderer.RenderOptions.of("hi", newService())
                .defaultWebhookUrl("https://ex.com/swaig"));
    Map<String, Object> ai = (Map<String, Object>) mainOf(json).get(0).get("ai");
    Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
    assertEquals(Map.of("web_hook_url", "https://ex.com/swaig"), swaig.get("defaults"));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> hooksFunctions() {
    String json =
        SwmlRenderer.renderSwml(
            SwmlRenderer.RenderOptions.of("hi", newService())
                .startupHookUrl("https://ex.com/start")
                .hangupHookUrl("https://ex.com/end")
                .swaigFunctions(
                    List.of(
                        Map.of(
                            "function", "get_weather", "description", "w", "parameters", Map.of()),
                        // A caller-supplied startup_hook must be skipped (deduped).
                        Map.of("function", "startup_hook", "description", "dup"))));
    Map<String, Object> ai = (Map<String, Object>) mainOf(json).get(0).get("ai");
    Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
    return (List<Map<String, Object>>) swaig.get("functions");
  }

  @Test
  void testRenderSwmlDedupesAndOrdersHooks() {
    List<String> names = hooksFunctions().stream().map(f -> (String) f.get("function")).toList();
    assertEquals(List.of("startup_hook", "hangup_hook", "get_weather"), names);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testRenderSwmlHookWireShape() {
    Map<String, Object> startup = hooksFunctions().get(0);
    assertEquals("https://ex.com/start", startup.get("web_hook_url"));
    Map<String, Object> params = (Map<String, Object>) startup.get("parameters");
    assertEquals("object", params.get("type"));
    assertEquals(Map.of(), params.get("properties"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testRenderSwmlPomPrompt() {
    List<Map<String, Object>> pom = List.of(Map.of("title", "Role", "body", "assistant"));
    String json =
        SwmlRenderer.renderSwml(SwmlRenderer.RenderOptions.of(pom, newService()).promptIsPom(true));
    Map<String, Object> ai = (Map<String, Object>) mainOf(json).get(0).get("ai");
    assertEquals(Map.of("pom", pom), ai.get("prompt"));
  }

  /**
   * {@code params(...)} merges at the {@code ai} TOP level (the reference's {@code **params}
   * splat), so its keys must be ones the closed {@code AIObject} declares. {@code temperature} is
   * an {@code AIParams} key, so it travels under the {@code params} key — the previous fixture
   * asserted it at the top level, which is a document the schema rejects.
   */
  @Test
  @SuppressWarnings("unchecked")
  void testRenderSwmlParamsMergedIntoAi() {
    String json =
        SwmlRenderer.renderSwml(
            SwmlRenderer.RenderOptions.of("hi", newService())
                .params(Map.of("params", Map.of("temperature", 0.3))));
    Map<String, Object> ai = (Map<String, Object>) mainOf(json).get(0).get("ai");
    Map<String, Object> params = (Map<String, Object>) ai.get("params");
    assertEquals(0.3, (Double) params.get("temperature"), 1e-9);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testRenderSwmlYamlFormat() {
    String out =
        SwmlRenderer.renderSwml(SwmlRenderer.RenderOptions.of("hi", newService()).format("yaml"));
    Map<String, Object> parsed = new Yaml().load(out);
    Map<String, Object> sections = (Map<String, Object>) parsed.get("sections");
    List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
    assertEquals("ai", firstKey(main.get(0)));
  }

  // ---- renderFunctionResponseSwml ----

  /**
   * The SWML {@code play} verb has NO {@code text} key — its config is PlayWithURL/PlayWithURLS and
   * spoken text travels through the {@code say:} URL scheme. Emitting {@code {"text": ...}}
   * produced a document the SWML schema rejects, and Java shipped it silently (the renderer wrote
   * through the RAW {@code document.addVerb}, bypassing the validating {@code Service.addVerb}
   * choke point). Mirrors the reference's {@code service.add_verb("play", {"url":
   * f"say:{response_text}"})}.
   */
  @Test
  @SuppressWarnings("unchecked")
  void testFunctionResponsePlaysTextAsSayUrl() {
    String out = SwmlRenderer.renderFunctionResponseSwml("All done", newService());
    List<Map<String, Object>> main = mainOf(out);
    Map<String, Object> play = (Map<String, Object>) main.get(0).get("play");
    assertEquals(Map.of("url", "say:All done"), play);
    assertFalse(play.containsKey("text"), "play has no 'text' key in the SWML schema");
  }

  /**
   * The response play must survive schema validation — it is routed through the validating {@code
   * Service.addVerb}, so a config the schema rejects raises instead of shipping.
   */
  @Test
  void testFunctionResponsePlayIsSchemaValid() {
    Service svc = newService();
    String out = SwmlRenderer.renderFunctionResponseSwml("hello there", svc);
    assertTrue(out.contains("\"url\":\"say:hello there\""), out);
    // The same config, offered to the validating choke point, is accepted.
    assertDoesNotThrow(() -> svc.addVerb("play", Map.of("url", "say:hello there")));
    // ... and the shape the port used to emit is REJECTED by that same choke point,
    // which is why the silent raw-document write was a wire defect.
    assertThrows(
        SchemaValidationError.class, () -> svc.addVerb("play", Map.of("text", "hello there")));
  }

  @Test
  void testFunctionResponseAppendsActions() {
    String out =
        SwmlRenderer.renderFunctionResponseSwml(
            "bye",
            newService(),
            List.of(
                // `reason` is a CLOSED enum (hangup|busy|decline) — routing response
                // actions through the validating Service.addVerb is what makes an
                // out-of-enum value raise instead of shipping.
                Map.of("hangup", Map.of("reason", "busy")),
                Map.of("transfer", Map.of("dest", "sip:x@y"))),
            "json");
    List<Map<String, Object>> main = mainOf(out);
    assertEquals(Map.of("url", "say:bye"), main.get(0).get("play"));
    assertEquals(Map.of("reason", "busy"), main.get(1).get("hangup"));
    assertEquals(Map.of("dest", "sip:x@y"), main.get(2).get("transfer"));
  }

  /**
   * Response actions are routed through the validating {@link Service#addVerb}, not the raw
   * document — so a caller-supplied action config the SWML schema rejects (here a misspelled key on
   * the closed {@code hangup} verb) raises instead of being appended silently.
   *
   * <p>A misspelled key is used rather than an out-of-enum {@code hangup.reason} because it
   * additionally pins the closed-schema check; the value-set case is covered by the engine-reason
   * rows in {@code ValidatorRoutingTest}.
   */
  @Test
  void testFunctionResponseActionIsSchemaValidated() {
    assertThrows(
        SchemaValidationError.class,
        () ->
            SwmlRenderer.renderFunctionResponseSwml(
                "bye", newService(), List.of(Map.of("hangup", Map.of("reasonn", "busy"))), "json"));
  }

  /**
   * An engine-valid reason is accepted by that same path. {@code noAnswer} is one of the six {@code
   * relay_apis.c:1105} accepts and was absent from the schema's earlier three-const union.
   */
  @Test
  void testFunctionResponseActionAcceptsAnEngineHangupReason() {
    assertDoesNotThrow(
        () ->
            SwmlRenderer.renderFunctionResponseSwml(
                "bye",
                newService(),
                List.of(Map.of("hangup", Map.of("reason", "noAnswer"))),
                "json"));
  }

  @Test
  void testFunctionResponseEmptyTextSkipsPlay() {
    String out =
        SwmlRenderer.renderFunctionResponseSwml(
            "", newService(), List.of(Map.of("ai", Map.of("prompt", Map.of("text", "x")))), "json");
    List<Map<String, Object>> main = mainOf(out);
    assertEquals(1, main.size());
    assertEquals("ai", firstKey(main.get(0)));
  }

  private static String firstKey(Map<String, Object> m) {
    return m.keySet().iterator().next();
  }
}
