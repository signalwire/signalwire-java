/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.agent.AgentBase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Every path that puts a verb into a SWML document must go through the VALIDATING entry point
 * ({@link Service#addVerb}), not the raw {@link Document#addVerb}.
 *
 * <p>Java had three unvalidated paths: {@link SWMLBuilder} and {@link SwmlRenderer} both reached
 * past the service to {@code service.getDocument().addVerb(...)}, and {@link AgentBase#renderSwml}
 * — the agent's primary render path — hand-assembled the whole {@code {version, sections}} envelope
 * without touching {@link Service} or {@link Document} at all. Because nothing validated, several
 * schema-invalid wire shapes shipped silently: {@code play} with a bare filename or no url at all,
 * and {@code internal_fillers} / {@code contexts} / {@code debug} / {@code temperature} emitted as
 * top-level keys on the CLOSED {@code AIObject}, which declares exactly nine.
 *
 * <p>The reverse failure matters too: {@code $defs/Hangup.reason} carries {@code x-sdk-widen:
 * true}, so its const union is a hint rather than a closed set — the validator must NOT reject a
 * reason outside it, or routing these paths through validation would start rejecting documents the
 * platform accepts.
 *
 * <p>These tests assert THROUGH the validator — they push an invalid shape at each entry point and
 * require it to raise. A test that compared emitted JSON to a hand-written expected blob would
 * re-encode the very blind spot this suite exists to close: it would happily pass on a document the
 * server rejects.
 */
class ValidatorRoutingTest {

  private static Service svc() {
    return new Service("validator-routing", "/vr");
  }

  private static AgentBase agent() {
    return AgentBase.builder()
        .name("vr-agent")
        .route("/vr")
        .authUser("u")
        .authPassword("p")
        .build();
  }

  // ------------------------------------------------------------------
  // SWMLBuilder — every helper + the catch-all dispatch
  // ------------------------------------------------------------------

  /**
   * {@code $defs/Hangup.reason} carries {@code x-sdk-widen: true} — a declared ruling that its
   * {@code hangup|busy|decline} union is a HINT and the platform accepts any string. The validator
   * must keep the TYPE constraint and drop the const-membership one; enforcing the union would
   * invent a constraint the server does not have and reject documents that work on the wire.
   */
  @Test
  void builderHangupAcceptsAnyReasonBecauseTheFieldIsWidened() {
    // The last two are legitimate platform values that sit outside the listed union —
    // the case widening exists for.
    for (String reason :
        List.of("hangup", "busy", "decline", "done", "completed", "user_hangup", "no_answer")) {
      assertDoesNotThrow(() -> new SWMLBuilder(svc()).hangup(reason), reason);
    }
  }

  /**
   * Widening drops the const/enum MEMBERSHIP constraint and nothing else — the underlying TYPE
   * still holds. A widened string field that accepted a number, a boolean, an object or an array
   * would have been widened into an untyped field, which is a different (and wrong) contract.
   */
  @Test
  void builderHangupStillRejectsAWrongTypedReason() {
    SWMLBuilder b = new SWMLBuilder(svc());
    for (Object wrong : List.of(42, true, Map.of(), List.of())) {
      assertThrows(
          SchemaValidationError.class,
          () -> b.verb("hangup", Map.of("reason", wrong)),
          "a widened string field must still reject " + wrong.getClass().getSimpleName());
    }
  }

  @Test
  void builderPlayRejectsConfigWithNoUrl() {
    SWMLBuilder b = new SWMLBuilder(svc());
    assertThrows(SchemaValidationError.class, () -> b.play(new LinkedHashMap<>()));
  }

  @Test
  void builderPlayRejectsUrlWithoutAScheme() {
    // $defs/play_url requires http(s):// | say: | ring: | silence:.
    SWMLBuilder b = new SWMLBuilder(svc());
    assertThrows(
        SchemaValidationError.class,
        () -> b.play(null, List.of("ringback.wav"), null, null, null, null, null));
  }

  @Test
  void builderAiRejectsConfigWithNoPrompt() {
    SWMLBuilder b = new SWMLBuilder(svc());
    assertThrows(SchemaValidationError.class, () -> b.ai(new LinkedHashMap<>()));
  }

  @Test
  void builderAiRejectsUnknownTopLevelKey() {
    SWMLBuilder b = new SWMLBuilder(svc());
    assertThrows(
        SchemaValidationError.class,
        () -> b.ai(null, null, null, null, null, Map.of("temperatur", 0.5)));
  }

  @Test
  void builderGenericVerbDispatchValidatesConfig() {
    SWMLBuilder b = new SWMLBuilder(svc());
    assertThrows(
        SchemaValidationError.class, () -> b.verb("record_call", Map.of("formatt", "wav")));
  }

  @Test
  void builderAnswerRejectsMisspelledKey() {
    SWMLBuilder b = new SWMLBuilder(svc());
    assertThrows(SchemaValidationError.class, () -> b.verb("answer", Map.of("maxduration", 5)));
  }

  @Test
  void builderValidShapesStillPass() {
    SWMLBuilder b = new SWMLBuilder(svc());
    assertDoesNotThrow(
        () ->
            b.reset()
                .answer()
                .say("hello")
                .verb("denoise", null)
                .sleepVerb(500)
                .ai("you are helpful", null, null, null, null, null)
                .hangup("busy"));
  }

  // ------------------------------------------------------------------
  // SwmlRenderer
  // ------------------------------------------------------------------

  @Test
  void rendererRecordCallGoesThroughTheValidator() {
    // An invalid record_call format must raise from the renderer, not ship.
    assertThrows(
        SchemaValidationError.class,
        () ->
            SwmlRenderer.renderSwml(
                SwmlRenderer.RenderOptions.of("hi", svc())
                    .recordCall(true)
                    .recordFormat("not-a-format")));
  }

  @Test
  void rendererRejectsUnknownTopLevelAiKey() {
    assertThrows(
        SchemaValidationError.class,
        () ->
            SwmlRenderer.renderSwml(
                SwmlRenderer.RenderOptions.of("hi", svc()).params(Map.of("temperature", 0.3))));
  }

  @Test
  void rendererValidRenderStillProduces() {
    String out =
        SwmlRenderer.renderSwml(
            SwmlRenderer.RenderOptions.of("hi", svc()).addAnswer(true).recordCall(true));
    assertTrue(out.contains("\"record_call\""), out);
    assertTrue(out.contains("\"ai\""), out);
  }

  // ------------------------------------------------------------------
  // AgentBase.renderSwml — the third path
  // ------------------------------------------------------------------

  @Test
  void agentRenderRejectsInvalidPreAnswerVerb() {
    AgentBase a = agent();
    a.setPromptText("hi");
    a.addPreAnswerVerb("play", Map.of("url", "ringback.wav"));
    assertThrows(SchemaValidationError.class, () -> a.renderSwml("http://localhost:3000"));
  }

  @Test
  void agentRenderRejectsInvalidPostAiVerb() {
    AgentBase a = agent();
    a.setPromptText("hi");
    a.addPostAiVerb("hangup", Map.of("reasonn", "busy"));
    assertThrows(SchemaValidationError.class, () -> a.renderSwml("http://localhost:3000"));
  }

  /**
   * A widened field survives the agent render path too — {@code x-sdk-widen} must be honoured
   * everywhere validation now runs, not just at the builder.
   */
  @Test
  void agentRenderAcceptsAWidenedHangupReason() {
    AgentBase a = agent();
    a.setPromptText("hi");
    a.addPostAiVerb("hangup", Map.of("reason", "done"));
    assertDoesNotThrow(() -> a.renderSwml("http://localhost:3000"));
  }

  @Test
  void agentRenderRejectsUnknownVerbName() {
    AgentBase a = agent();
    a.setPromptText("hi");
    a.addPostAnswerVerb("foobar", Map.of());
    assertThrows(SchemaValidationError.class, () -> a.renderSwml("http://localhost:3000"));
  }

  /**
   * The whole rendered agent document validates against the root schema — the end-to-end proof that
   * the primary render path now emits a document the server accepts.
   */
  @Test
  void agentRenderProducesASchemaValidDocument() {
    AgentBase a = agent();
    a.setPromptText("You are helpful.");
    a.setPostPrompt("Summarize.");
    a.addHint("SignalWire");
    a.addLanguage("English", "en-US", "rachel");
    a.setParam("temperature", 0.5);
    a.updateGlobalData(Map.of("k", "v"));
    a.addInternalFiller("next_step", "en-US", List.of("one moment"));
    a.enableDebugEvents();
    a.addPreAnswerVerb("play", Map.of("url", "say:please hold"));
    a.addPostAiVerb("hangup", Map.of("reason", "hangup"));

    Map<String, Object> doc = a.renderSwml("http://localhost:3000");
    SchemaUtils su = new SchemaUtils(null, true);
    Map.Entry<Boolean, List<String>> res = su.validateDocument(doc);
    assertTrue(res.getKey(), "rendered agent document must be schema-valid: " + res.getValue());
  }

  /** Rendering twice must not accumulate verbs — renderSwml resets the document first. */
  @Test
  @SuppressWarnings("unchecked")
  void agentRenderIsIdempotentAcrossCalls() {
    AgentBase a = agent();
    a.setPromptText("hi");
    Map<String, Object> first = a.renderSwml("http://localhost:3000");
    int firstCount = mainOf(first).size();
    Map<String, Object> second = a.renderSwml("http://localhost:3000");
    assertEquals(firstCount, mainOf(second).size());
  }

  // ------------------------------------------------------------------
  // ai-key PLACEMENT — $defs/AIObject is CLOSED over exactly nine keys:
  // SWAIG, global_data, hints, languages, params, post_prompt,
  // post_prompt_url, prompt, pronounce. Anything else at the ai top level
  // invalidates the document. These pin where the easily-misplaced keys go.
  // ------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void aiTopLevelKeysAreConfinedToTheNineTheSchemaDeclares() {
    AgentBase a = agent();
    a.setPromptText("hi");
    a.setPostPrompt("summarize");
    a.addHint("SignalWire");
    a.addLanguage("English", "en-US", "rachel");
    a.addPronunciation("SW", "SignalWire", true);
    a.setParam("temperature", 0.5);
    a.updateGlobalData(Map.of("k", "v"));
    a.addInternalFiller("next_step", "en-US", List.of("one moment"));
    a.enableDebugEvents();
    var cb = a.defineContexts();
    cb.addContext("default").addStep("greeting").setText("Hello!");

    Map<String, Object> ai = aiOf(a.renderSwml("http://localhost:3000"));
    assertTrue(
        List.of(
                "SWAIG",
                "global_data",
                "hints",
                "languages",
                "params",
                "post_prompt",
                "post_prompt_url",
                "prompt",
                "pronounce")
            .containsAll(ai.keySet()),
        "unexpected top-level ai keys: " + ai.keySet());
  }

  @Test
  @SuppressWarnings("unchecked")
  void contextsNestInsideThePrompt() {
    AgentBase a = agent();
    a.setPromptText("hi");
    var cb = a.defineContexts();
    cb.addContext("default").addStep("greeting").setText("Hello!");
    Map<String, Object> ai = aiOf(a.renderSwml("http://localhost:3000"));
    assertFalse(ai.containsKey("contexts"));
    assertNotNull(((Map<String, Object>) ai.get("prompt")).get("contexts"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void debugWebhookKeysNestInsideParams() {
    AgentBase a = agent();
    a.setPromptText("hi");
    a.enableDebugEvents(2);
    Map<String, Object> ai = aiOf(a.renderSwml("http://localhost:3000"));
    assertFalse(ai.containsKey("debug"));
    assertFalse(ai.containsKey("debug_webhook_url"));
    assertFalse(ai.containsKey("debug_webhook_level"));
    Map<String, Object> params = (Map<String, Object>) ai.get("params");
    assertNotNull(params.get("debug_webhook_url"));
    assertEquals(2, params.get("debug_webhook_level"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void internalFillersNestInsideSwaig() {
    AgentBase a = agent();
    a.setPromptText("hi");
    a.addInternalFiller("next_step", "en-US", List.of("one moment"));
    Map<String, Object> ai = aiOf(a.renderSwml("http://localhost:3000"));
    assertFalse(ai.containsKey("internal_fillers"));
    Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
    assertNotNull(swaig.get("internal_fillers"));
  }

  /**
   * {@code record_call.stereo} is {@code anyOf<boolean, SWMLVar>} — it must serialise as a JSON
   * BOOLEAN. An integer 1/0 (an easy slip in a boxing language) is rejected by the schema, and
   * because the default is on it would be wrong on EVERY recorded call.
   */
  @Test
  @SuppressWarnings("unchecked")
  void recordCallStereoIsAJsonBoolean() {
    AgentBase a =
        AgentBase.builder()
            .name("rec")
            .route("/rec")
            .authUser("u")
            .authPassword("p")
            .recordCall(true)
            .build();
    a.setPromptText("hi");
    Map<String, Object> doc = a.renderSwml("http://localhost:3000");
    Map<String, Object> rc =
        (Map<String, Object>)
            mainOf(doc).stream()
                .filter(v -> v.containsKey("record_call"))
                .findFirst()
                .orElseThrow()
                .get("record_call");
    assertInstanceOf(Boolean.class, rc.get("stereo"));
    // And the whole document validates.
    SchemaUtils su = new SchemaUtils(null, true);
    assertTrue(su.validateDocument(doc).getKey(), "" + su.validateDocument(doc).getValue());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> aiOf(Map<String, Object> doc) {
    return (Map<String, Object>)
        mainOf(doc).stream().filter(v -> v.containsKey("ai")).findFirst().orElseThrow().get("ai");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> mainOf(Map<String, Object> doc) {
    Map<String, Object> sections = (Map<String, Object>) doc.get("sections");
    return (List<Map<String, Object>>) sections.get("main");
  }
}
