/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.agents.BedrockAgent;
import com.signalwire.sdk.contexts.Step;
import com.signalwire.sdk.core.PomBuilder;
import com.signalwire.sdk.prefabs.InfoGathererAgent;
import com.signalwire.sdk.server.AgentServer;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.SWAIGFunction;
import com.signalwire.sdk.swml.Service;
import com.signalwire.sdk.utils.UrlValidator;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * Java has no default arguments; its idiomatic substitute is METHOD OVERLOADING — a shorter
 * overload that omits an optional parameter and supplies the reference's default. This suite is the
 * BEHAVIOURAL proof for each such overload added to close the {@code required-flip} signature
 * drift.
 *
 * <p>Every test has the same shape, and the shape is the point: call the SHORT form, then call the
 * LONG form with the reference's default written out EXPLICITLY, and assert the two produce the
 * same observable result. A test that only exercised the long form would prove nothing, and a test
 * comparing the short form against a hand-copied expected literal would still pass if the delegate
 * forwarded some OTHER constant. Comparing against the explicitly-defaulted long call means that
 * changing a delegate to forward a wrong default makes its test fail.
 *
 * <p>Each reference default is cited by file:line against the signalwire-python reference.
 */
class ReferenceDefaultOverloadTest {

  // ======== FunctionResult ========

  /** signalwire/core/function_result.py:190 — swml_transfer(dest, ai_response, final=True) */
  @Test
  void swmlTransferDefaultsFinalToTrue() {
    assertEquals(
        new FunctionResult("r").swmlTransfer("sip:a@b", "done", true).toMap(),
        new FunctionResult("r").swmlTransfer("sip:a@b", "done").toMap());
  }

  /** signalwire/core/function_result.py:440 — hold(timeout: int = 300) */
  @Test
  void holdDefaultsTimeoutTo300() {
    assertEquals(new FunctionResult("r").hold(300).toMap(), new FunctionResult("r").hold().toMap());
  }

  /** signalwire/core/function_result.py:672 — replace_in_history(text: str | bool = True) */
  @Test
  void replaceInHistoryDefaultsToTrue() {
    assertEquals(
        new FunctionResult("r").replaceInHistory(true).toMap(),
        new FunctionResult("r").replaceInHistory().toMap());
  }

  /** signalwire/core/function_result.py:647 — enable_functions_on_timeout(enabled: bool = True) */
  @Test
  void enableFunctionsOnTimeoutDefaultsToTrue() {
    assertEquals(
        new FunctionResult("r").enableFunctionsOnTimeout(true).toMap(),
        new FunctionResult("r").enableFunctionsOnTimeout().toMap());
  }

  /** signalwire/core/function_result.py:659 — enable_extensive_data(enabled: bool = True) */
  @Test
  void enableExtensiveDataDefaultsToTrue() {
    assertEquals(
        new FunctionResult("r").enableExtensiveData(true).toMap(),
        new FunctionResult("r").enableExtensiveData().toMap());
  }

  /**
   * signalwire/core/function_result.py:1224 — tap(uri, control_id=None, direction="both",
   * codec="PCMU", rtp_ptime=20, status_url=None)
   */
  @Test
  void tapDefaultsMatchTheReference() {
    var full = new FunctionResult("r").tap("ws://x", null, "both", "PCMU", 20, null).toMap();
    assertEquals(full, new FunctionResult("r").tap("ws://x").toMap());
    assertEquals(full, new FunctionResult("r").tap("ws://x", null).toMap());
    assertEquals(full, new FunctionResult("r").tap("ws://x", null, "both").toMap());
  }

  /**
   * signalwire/core/function_result.py:752 — send_sms(to_number, from_number, body=None,
   * media=None, tags=None, region=None)
   */
  @Test
  void sendSmsDefaultsMatchTheReference() {
    var full =
        new FunctionResult("r")
            .sendSms("+15551112222", "+15553334444", "hi", null, null, null)
            .toMap();
    assertEquals(
        full, new FunctionResult("r").sendSms("+15551112222", "+15553334444", "hi").toMap());
    assertEquals(
        full, new FunctionResult("r").sendSms("+15551112222", "+15553334444", "hi", null).toMap());
  }

  /**
   * signalwire/core/function_result.py:765 — "Either body or media (or both) must be provided", so
   * the all-defaults form raises exactly like the reference does.
   */
  @Test
  void sendSmsWithNeitherBodyNorMediaRaisesLikeTheReference() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FunctionResult("r").sendSms("+15551112222", "+15553334444"));
  }

  /**
   * signalwire/core/function_result.py:810 — pay(payment_connector_url, input_method="dtmf",
   * status_url=None, payment_method="credit-card", timeout=5, max_attempts=1, security_code=True,
   * postal_code=True, min_postal_code_length=0, token_type="reusable", charge_amount=None,
   * currency="usd", language="en-US", voice="woman", description=None, valid_card_types="visa
   * mastercard amex", parameters=None, prompts=None, ai_response=DEFAULT_PAY_AI_RESPONSE)
   */
  @Test
  void payDefaultsMatchTheReference() {
    var longForm =
        new FunctionResult("r")
            .pay(
                "https://pay.example",
                "dtmf",
                null,
                "credit-card",
                5,
                1,
                true,
                Boolean.TRUE,
                0,
                "reusable",
                null,
                "usd",
                "en-US",
                "woman",
                null,
                "visa mastercard amex",
                null,
                null,
                FunctionResult.DEFAULT_PAY_AI_RESPONSE)
            .toMap();
    assertEquals(longForm, new FunctionResult("r").pay("https://pay.example").toMap());
  }

  /**
   * signalwire/core/function_result.py:1471 — create_payment_prompt(for_situation, actions,
   * card_type=None, error_type=None)
   */
  @Test
  void createPaymentPromptDefaultsCardAndErrorTypeToNull() {
    List<Map<String, String>> actions =
        List.of(FunctionResult.createPaymentAction("say", "Card number please"));
    var full = FunctionResult.createPaymentPrompt("payment-card-number", actions, null, null);
    assertEquals(full, FunctionResult.createPaymentPrompt("payment-card-number", actions));
    assertEquals(full, FunctionResult.createPaymentPrompt("payment-card-number", actions, null));
    // And the default's VALUE matters: omitting card_type must leave the key out.
    assertFalse(
        FunctionResult.createPaymentPrompt("payment-card-number", actions)
            .containsKey("card_type"));
    assertTrue(
        FunctionResult.createPaymentPrompt("payment-card-number", actions, "visa")
            .containsKey("card_type"));
  }

  // ======== Agent surface ========

  /**
   * signalwire/agent_server.py:750 — serve_static_files(directory, route="/").
   *
   * <p>{@code staticFilesDir} / {@code staticFilesRoute} are private with no accessor and are only
   * observable by actually serving HTTP, so this reads them reflectively rather than widening the
   * public surface for a test. The assertion is still against the explicitly-defaulted long call.
   */
  @Test
  void serveStaticFilesDefaultsRouteToSlash() throws Exception {
    var shortForm = new AgentServer("127.0.0.1", 0).serveStaticFiles("/var/assets");
    var longForm = new AgentServer("127.0.0.1", 0).serveStaticFiles("/var/assets", "/");
    assertEquals(staticField(longForm, "staticFilesDir"), staticField(shortForm, "staticFilesDir"));
    assertEquals(
        staticField(longForm, "staticFilesRoute"), staticField(shortForm, "staticFilesRoute"));
    // The default's VALUE matters: a different route must NOT compare equal.
    var other = new AgentServer("127.0.0.1", 0).serveStaticFiles("/var/assets", "/assets");
    assertNotEquals(
        staticField(other, "staticFilesRoute"), staticField(shortForm, "staticFilesRoute"));
  }

  private static Object staticField(AgentServer server, String name) throws Exception {
    var f = AgentServer.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(server);
  }

  /**
   * signalwire/agents/bedrock.py:215 — set_inference_params(temperature=None, top_p=None,
   * max_tokens=None); each {@code None} leaves the current value unchanged.
   */
  @Test
  void setInferenceParamsDefaultsToNullMeaningUnchanged() {
    var viaShort = new BedrockAgent("a", "/a").setInferenceParams(0.5, 0.9, 100);
    var viaLong = new BedrockAgent("a", "/a").setInferenceParams(0.5, 0.9, 100);

    // Omitting all three must be a no-op — exactly like passing three explicit nulls.
    viaShort.setInferenceParams();
    viaLong.setInferenceParams(null, null, null);
    assertEquals(viaLong.getTemperature(), viaShort.getTemperature());
    assertEquals(viaLong.getTopP(), viaShort.getTopP());
    assertEquals(viaLong.getMaxTokens(), viaShort.getMaxTokens());

    // Supplying only temperature must leave top_p / max_tokens untouched.
    viaShort.setInferenceParams(0.25);
    viaLong.setInferenceParams(0.25, null, null);
    assertEquals(viaLong.getTemperature(), viaShort.getTemperature());
    assertEquals(viaLong.getTopP(), viaShort.getTopP());
    assertEquals(viaLong.getMaxTokens(), viaShort.getMaxTokens());
    // The forwarded default is load-bearing: top_p must still be the 0.9 set above.
    assertEquals(0.9, viaShort.getTopP());
  }

  /**
   * signalwire/core/mixins/ai_config_mixin.py:249 — add_pronunciation(replace, with_text,
   * ignore_case=False). Observed through the rendered SWML ai.pronounce list.
   */
  @Test
  void addPronunciationDefaultsIgnoreCaseToFalse() {
    var shortForm = AgentBase.builder().name("a").route("/a").build();
    var longForm = AgentBase.builder().name("a").route("/a").build();
    shortForm.addPronunciation("SW", "SignalWire");
    longForm.addPronunciation("SW", "SignalWire", false);
    assertEquals(longForm.renderSwml("http://localhost"), shortForm.renderSwml("http://localhost"));
    // The default's VALUE matters: ignore_case=true must render differently.
    var flipped = AgentBase.builder().name("a").route("/a").build();
    flipped.addPronunciation("SW", "SignalWire", true);
    assertNotEquals(
        flipped.renderSwml("http://localhost"), shortForm.renderSwml("http://localhost"));
  }

  /** signalwire/core/mixins/tool_mixin.py:235 — on_function_call(name, args, raw_data=None) */
  @Test
  void onFunctionCallDefaultsRawDataToNull() {
    var seen = new ArrayList<Map<String, Object>>();
    var agent = AgentBase.builder().name("a").route("/a").build();
    agent.defineTool(
        "echo",
        "echo",
        new LinkedHashMap<>(),
        (args, rawData) -> {
          seen.add(rawData);
          return new FunctionResult("ok");
        });
    var shortResult = agent.onFunctionCall("echo", Map.of("k", "v"));
    var longResult = agent.onFunctionCall("echo", Map.of("k", "v"), null);
    assertEquals(longResult.toMap(), shortResult.toMap());
    assertEquals(2, seen.size());
    assertEquals(seen.get(1), seen.get(0), "short form must forward the same raw_data as null");
  }

  /**
   * signalwire/core/mixins/web_mixin.py:1212 — on_request(request_data=None, callback_path=None)
   */
  @Test
  void onRequestDefaultsBothParamsToNull() {
    var svc = new Service("s");
    assertEquals(svc.onRequest(null, null), svc.onRequest());
    assertEquals(svc.onRequest(Map.of("a", 1), null), svc.onRequest(Map.of("a", 1)));
  }

  /**
   * signalwire/prefabs/info_gatherer.py:162 — on_swml_request(request_data=None,
   * callback_path=None, request=None)
   */
  @Test
  void onSwmlRequestDefaultsAllThreeToNull() {
    var a = new InfoGathererAgent("g", null);
    assertEquals(a.onSwmlRequest(null, null, null), a.onSwmlRequest());
    assertEquals(a.onSwmlRequest(Map.of("b", 1), null, null), a.onSwmlRequest(Map.of("b", 1)));
    assertEquals(
        a.onSwmlRequest(Map.of("b", 1), Map.of("q", 2), null),
        a.onSwmlRequest(Map.of("b", 1), Map.of("q", 2)));
  }

  // ======== Core value builders ========

  /**
   * signalwire/core/contexts.py:407 — set_gather_info(output_key=None, completion_action=None,
   * prompt=None, isolated=False)
   */
  @Test
  void setGatherInfoDefaultsMatchTheReference() {
    assertEquals(
        gathering(step().setGatherInfo(null, null, null, false)),
        gathering(step().setGatherInfo()));
    assertEquals(
        gathering(step().setGatherInfo("k", null, null, false)),
        gathering(step().setGatherInfo("k")));
    assertEquals(
        gathering(step().setGatherInfo("k", "done", null, false)),
        gathering(step().setGatherInfo("k", "done")));
    // isolated defaults to FALSE — flipping it must change the rendered step.
    assertNotEquals(
        gathering(step().setGatherInfo("k", "done", null, true)),
        gathering(step().setGatherInfo("k", "done")));
  }

  /** A Step needs prompt text before {@code toMap()} will render it. */
  private static Step step() {
    return new Step("s").setText("say something");
  }

  /**
   * {@code gather_info} refuses to render without at least one question, so add one AFTER
   * setGatherInfo — identically on both sides, so the comparison still isolates the defaults.
   */
  private static Map<String, Object> gathering(Step s) {
    return s.addGatherQuestion("q1", "What is your name?").toMap();
  }

  /**
   * signalwire/core/pom_builder.py:84 — add_to_section(title, body=None, bullet=None, bullets=None)
   */
  @Test
  void addToSectionDefaultsMatchTheReference() {
    assertEquals(
        new PomBuilder().addToSection("T", null, null, null).toMap(),
        new PomBuilder().addToSection("T").toMap());
    assertEquals(
        new PomBuilder().addToSection("T", "body", null, null).toMap(),
        new PomBuilder().addToSection("T", "body").toMap());
    assertEquals(
        new PomBuilder().addToSection("T", "body", "b1", null).toMap(),
        new PomBuilder().addToSection("T", "body", "b1").toMap());
    // The omitted bullet really is omitted, not silently supplied.
    assertNotEquals(
        new PomBuilder().addToSection("T", "body", "b1", null).toMap(),
        new PomBuilder().addToSection("T", "body").toMap());
  }

  /** signalwire/core/swaig_function.py:142 — execute(args, raw_data=None) */
  @Test
  void swaigFunctionExecuteDefaultsRawDataToNull() {
    var seen = new ArrayList<Map<String, Object>>();
    var fn =
        SWAIGFunction.builder()
            .name("f")
            .description("desc")
            .parameters(new LinkedHashMap<>())
            .handler(
                (args, rawData) -> {
                  seen.add(rawData);
                  return new FunctionResult("ok");
                })
            .build();
    var shortForm = fn.execute(Map.of("a", 1));
    var longForm = fn.execute(Map.of("a", 1), null);
    assertEquals(longForm, shortForm);
    assertEquals(2, seen.size());
    assertEquals(seen.get(1), seen.get(0));
  }

  /** signalwire/utils/url_validator.py:34 — validate_url(url, allow_private=False) */
  @Test
  void validateUrlDefaultsAllowPrivateToFalse() {
    assertEquals(
        UrlValidator.validateUrl("http://127.0.0.1/x", false),
        UrlValidator.validateUrl("http://127.0.0.1/x"));
    // Pins the default's VALUE, not merely that the two calls agree: a private-network
    // URL is rejected under allow_private=False and accepted only when overridden.
    assertFalse(UrlValidator.validateUrl("http://127.0.0.1/x"));
    assertTrue(UrlValidator.validateUrl("http://127.0.0.1/x", true));
  }
}
