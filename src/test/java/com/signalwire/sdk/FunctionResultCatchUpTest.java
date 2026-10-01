/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signalwire.sdk.swaig.FunctionResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * FunctionResult surface the reference added in 3.5.x (signalwire-python core/function_result.py):
 * structured tool response, routed hold, change_voice, rpc global_data.
 */
class FunctionResultCatchUpTest {

  @Test
  void setToolResponseIsTheStructuredForm() {
    var fr = new FunctionResult().setToolResponse("status: on hold", "Tell them.");
    assertEquals(
        Map.of("tool_result", "status: on hold", "tool_prompt", "Tell them."),
        fr.toMap().get("response"));
  }

  @Test
  void toolResponseOmitsNullHalves() {
    var fr = new FunctionResult().setToolResponse("done", null);
    assertEquals(Map.of("tool_result", "done"), fr.toMap().get("response"));
  }

  @Test
  void constructorToolArgsSetTheStructuredForm() {
    var fr = new FunctionResult(null, false, "r", "p");
    assertEquals(Map.of("tool_result", "r", "tool_prompt", "p"), fr.toMap().get("response"));
  }

  @Test
  void holdBareIntegerUnchanged() {
    var fr = new FunctionResult().hold(120);
    assertEquals(List.of(Map.of("hold", 120)), fr.toMap().get("action"));
    assertFalse(fr.toMap().containsKey("post_process"));
  }

  @Test
  void holdWithPromptAnnouncesAndPostProcesses() {
    var fr = new FunctionResult().hold("Tell the caller you are placing them on hold.", 2000);
    Map<String, Object> m = fr.toMap();
    assertEquals(List.of(Map.of("hold", 900)), m.get("action"));
    assertEquals(Boolean.TRUE, m.get("post_process"));
    assertEquals(
        Map.of(
            "tool_result",
            "status: on hold",
            "tool_prompt",
            "Tell the caller you are placing them on hold."),
        m.get("response"));
  }

  @Test
  void holdWithStepsEmitsTheObjectForm() {
    var fr = new FunctionResult().hold(null, 300, "back_with_agent", "take_a_message");
    assertEquals(
        List.of(
            Map.of(
                "hold",
                Map.of(
                    "timeout", 300, "step", "back_with_agent", "timeout_step", "take_a_message"))),
        fr.toMap().get("action"));
  }

  @Test
  void changeVoiceIsAnAction() {
    var fr = new FunctionResult().changeVoice("elevenlabs.rachel");
    assertEquals(List.of(Map.of("change_voice", "elevenlabs.rachel")), fr.toMap().get("action"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> rpcParams(FunctionResult fr) {
    var action = (List<Map<String, Object>>) fr.toMap().get("action");
    var swml = (Map<String, Object>) action.get(0).get("SWML");
    var sections = (Map<String, Object>) swml.get("sections");
    var main = (List<Map<String, Object>>) sections.get("main");
    var rpc = (Map<String, Object>) main.get(0).get("execute_rpc");
    return (Map<String, Object>) rpc.get("params");
  }

  @Test
  void rpcAiMessageCarriesGlobalData() {
    var fr = new FunctionResult().rpcAiMessage("c-1", null, "system", Map.of("k", "v"));
    assertEquals(Map.of("global_data", Map.of("k", "v")), rpcParams(fr));
  }

  @Test
  void rpcAiGlobalDataIsTheGlobalDataOnlyForm() {
    var fr = new FunctionResult().rpcAiGlobalData("c-1", Map.of("decline_message", "x"));
    assertEquals(Map.of("global_data", Map.of("decline_message", "x")), rpcParams(fr));
  }

  @Test
  void rpcAiMessageNeedsAPayload() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FunctionResult().rpcAiMessage("c-1", null, "system", null));
  }

  @Test
  void rpcAiMessageTextFormUnchanged() {
    var params = rpcParams(new FunctionResult().rpcAiMessage("c-1", "hi"));
    assertEquals("system", params.get("role"));
    assertEquals("hi", params.get("message_text"));
    assertTrue(!params.containsKey("global_data"));
  }
}
