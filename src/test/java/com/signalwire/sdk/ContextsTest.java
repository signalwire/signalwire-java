/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.contexts.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Tests for Contexts and Steps system. */
class ContextsTest {

  // ======== Step Tests ========

  @Test
  void testStepWithText() {
    var step = new Step("greeting");
    step.setText("Hello, how can I help?");

    var map = step.toMap();
    assertEquals("greeting", map.get("name"));
    assertEquals("Hello, how can I help?", map.get("text"));
  }

  @Test
  void testStepWithPomSections() {
    var step = new Step("task_step");
    step.addSection("Task", "Help the user with their request");
    step.addBullets("Process", List.of("Ask for details", "Provide answer"));

    var map = step.toMap();
    assertEquals("task_step", map.get("name"));
    String text = (String) map.get("text");
    assertTrue(text.contains("## Task"));
    assertTrue(text.contains("Help the user"));
    assertTrue(text.contains("## Process"));
    assertTrue(text.contains("- Ask for details"));
    assertTrue(text.contains("- Provide answer"));
  }

  @Test
  void testStepCannotMixTextAndSections() {
    var step = new Step("test");
    step.setText("Hello");

    assertThrows(IllegalStateException.class, () -> step.addSection("Title", "Body"));
  }

  @Test
  void testStepCannotMixSectionsAndText() {
    var step = new Step("test");
    step.addSection("Title", "Body");

    assertThrows(IllegalStateException.class, () -> step.setText("Hello"));
  }

  @Test
  void testStepCriteria() {
    var step = new Step("collect_info");
    step.setText("Collect user information")
        .setStepCriteria("All required info has been collected");

    var map = step.toMap();
    assertEquals("All required info has been collected", map.get("step_criteria"));
  }

  @Test
  void testStepFunctions() {
    var step = new Step("lookup");
    step.setText("Look up information").setFunctions(List.of("search", "lookup"));

    var map = step.toMap();
    assertTrue(map.containsKey("functions"));
  }

  @Test
  void testStepFunctionsNone() {
    var step = new Step("greeting");
    step.setText("Hello").setFunctions("none");

    var map = step.toMap();
    assertEquals("none", map.get("functions"));
  }

  @Test
  void testStepValidSteps() {
    var step = new Step("step1");
    step.setText("Step 1").setValidSteps(List.of("step2", "step3"));

    var map = step.toMap();

    @SuppressWarnings("unchecked")
    var validSteps = (List<String>) map.get("valid_steps");
    assertEquals(2, validSteps.size());
    assertTrue(validSteps.contains("step2"));
  }

  @Test
  void testStepValidContexts() {
    var step = new Step("transfer");
    step.setText("Transfer to support").setValidContexts(List.of("support", "sales"));

    var map = step.toMap();
    assertTrue(map.containsKey("valid_contexts"));
  }

  @Test
  void testStepEnd() {
    var step = new Step("farewell");
    step.setText("Goodbye").setEnd(true);

    var map = step.toMap();
    assertEquals(true, map.get("end"));
  }

  @Test
  void testStepSkipUserTurn() {
    var step = new Step("auto");
    step.setText("Auto step").setSkipUserTurn(true);

    var map = step.toMap();
    assertEquals(true, map.get("skip_user_turn"));
  }

  @Test
  void testStepSkipToNextStep() {
    var step = new Step("auto");
    step.setText("Auto step").setSkipToNextStep(true);

    var map = step.toMap();
    assertEquals(true, map.get("skip_to_next_step"));
  }

  @Test
  void testStepHistory() {
    for (String mode : List.of("keep", "default", "hide")) {
      var step = new Step("s").setText("Body").setHistory(mode);
      var map = step.toMap();
      assertEquals(mode, map.get("history"));
    }
  }

  @Test
  void testStepHistoryOmittedWhenUnset() {
    var step = new Step("s").setText("Body");
    assertFalse(step.toMap().containsKey("history"));
  }

  @Test
  void testStepHistoryInvalidRejected() {
    var step = new Step("s").setText("Body");
    assertThrows(IllegalArgumentException.class, () -> step.setHistory("bogus"));
  }

  @Test
  void testStepReset() {
    var step = new Step("context_switch");
    step.setText("Switching context")
        .setResetSystemPrompt("New system prompt")
        .setResetUserPrompt("User said hello")
        .setResetConsolidate(true)
        .setResetFullReset(true);

    var map = step.toMap();
    assertTrue(map.containsKey("reset"));

    @SuppressWarnings("unchecked")
    var reset = (Map<String, Object>) map.get("reset");
    assertEquals("New system prompt", reset.get("system_prompt"));
    assertEquals("User said hello", reset.get("user_prompt"));
    assertEquals(true, reset.get("consolidate"));
    assertEquals(true, reset.get("full_reset"));
  }

  @Test
  void testStepClearSections() {
    var step = new Step("test");
    step.addSection("Title", "Body");
    step.clearSections();

    // Now should be able to use setText
    step.setText("Hello");
    var map = step.toMap();
    assertEquals("Hello", map.get("text"));
  }

  // ======== GatherInfo Tests ========

  @Test
  void testStepGatherInfo() {
    var step = new Step("collect");
    step.setText("Collecting info")
        .setGatherInfo("user_info", "next_step", "Please answer the following")
        .addGatherQuestion("name", "What is your name?")
        .addGatherQuestion("email", "What is your email?", "string", true, null, null);

    var map = step.toMap();
    assertTrue(map.containsKey("gather_info"));

    @SuppressWarnings("unchecked")
    var gatherInfo = (Map<String, Object>) map.get("gather_info");
    assertEquals("user_info", gatherInfo.get("output_key"));
    assertEquals("next_step", gatherInfo.get("completion_action"));
    assertEquals("Please answer the following", gatherInfo.get("prompt"));

    @SuppressWarnings("unchecked")
    var questions = (List<Map<String, Object>>) gatherInfo.get("questions");
    assertEquals(2, questions.size());
    assertEquals("name", questions.get(0).get("key"));
    assertEquals("What is your name?", questions.get(0).get("question"));
  }

  @Test
  void testGatherQuestionConfirm() {
    var step = new Step("test");
    step.setText("Test")
        .setGatherInfo(null, null, null)
        .addGatherQuestion("phone", "What is your phone?", "string", true, null, null);

    var map = step.toMap();

    @SuppressWarnings("unchecked")
    var gatherInfo = (Map<String, Object>) map.get("gather_info");
    @SuppressWarnings("unchecked")
    var questions = (List<Map<String, Object>>) gatherInfo.get("questions");
    assertEquals(true, questions.get(0).get("confirm"));
  }

  @Test
  void testGatherQuestionRequiresSetGatherInfo() {
    var step = new Step("test");
    step.setText("Test");

    assertThrows(IllegalStateException.class, () -> step.addGatherQuestion("key", "question"));
  }

  // ======== Context Tests ========

  @Test
  void testContextBasic() {
    var ctx = new Context("default");
    ctx.addStep("greeting").setText("Hello!");
    ctx.addStep("farewell").setText("Goodbye!");

    var map = ctx.toMap();

    @SuppressWarnings("unchecked")
    var steps = (List<Map<String, Object>>) map.get("steps");
    assertEquals(2, steps.size());
    assertEquals("greeting", steps.get(0).get("name"));
    assertEquals("farewell", steps.get(1).get("name"));
  }

  @Test
  void testContextHistory() {
    for (String mode : List.of("keep", "default", "hide")) {
      var ctx = new Context("default");
      ctx.addStep("greeting").setText("Hello!");
      ctx.setHistory(mode);
      assertEquals(mode, ctx.toMap().get("history"));
    }
  }

  @Test
  void testContextHistoryOmittedWhenUnset() {
    var ctx = new Context("default");
    ctx.addStep("greeting").setText("Hello!");
    assertFalse(ctx.toMap().containsKey("history"));
  }

  @Test
  void testContextHistoryInvalidRejected() {
    var ctx = new Context("default");
    assertThrows(IllegalArgumentException.class, () -> ctx.setHistory("bogus"));
  }

  @Test
  void testContextDuplicateStep() {
    var ctx = new Context("default");
    ctx.addStep("step1").setText("Step 1");

    assertThrows(IllegalStateException.class, () -> ctx.addStep("step1"));
  }

  @Test
  void testContextRemoveStep() {
    var ctx = new Context("default");
    ctx.addStep("step1").setText("Step 1");
    ctx.addStep("step2").setText("Step 2");
    ctx.removeStep("step1");

    var map = ctx.toMap();

    @SuppressWarnings("unchecked")
    var steps = (List<Map<String, Object>>) map.get("steps");
    assertEquals(1, steps.size());
    assertEquals("step2", steps.get(0).get("name"));
  }

  @Test
  void testContextMoveStep() {
    var ctx = new Context("default");
    ctx.addStep("step1").setText("Step 1");
    ctx.addStep("step2").setText("Step 2");
    ctx.addStep("step3").setText("Step 3");
    ctx.moveStep("step3", 0);

    var map = ctx.toMap();

    @SuppressWarnings("unchecked")
    var steps = (List<Map<String, Object>>) map.get("steps");
    assertEquals("step3", steps.get(0).get("name"));
  }

  @Test
  void testContextWithFullStepSetup() {
    var ctx = new Context("default");
    ctx.addStep(
        "greeting",
        "Greet the user",
        List.of("Be friendly", "Ask how you can help"),
        "User has stated their need",
        "none",
        List.of("next"));

    var map = ctx.toMap();

    @SuppressWarnings("unchecked")
    var steps = (List<Map<String, Object>>) map.get("steps");
    var step = steps.get(0);
    assertEquals("greeting", step.get("name"));
    assertTrue(((String) step.get("text")).contains("## Task"));
    assertTrue(((String) step.get("text")).contains("## Process"));
  }

  @Test
  void testContextSystemPrompt() {
    var ctx = new Context("support");
    ctx.addStep("step1").setText("Step 1");
    ctx.setSystemPrompt("You are a support agent.").setConsolidate(true).setFullReset(true);

    var map = ctx.toMap();
    assertEquals("You are a support agent.", map.get("system_prompt"));
    assertEquals(true, map.get("consolidate"));
    assertEquals(true, map.get("full_reset"));
  }

  @Test
  void testContextPostPrompt() {
    var ctx = new Context("default");
    ctx.addStep("step1").setText("Step 1");
    ctx.setPostPrompt("Summarize the conversation.");

    var map = ctx.toMap();
    assertEquals("Summarize the conversation.", map.get("post_prompt"));
  }

  @Test
  void testContextIsolated() {
    var ctx = new Context("support");
    ctx.addStep("step1").setText("Step 1");
    ctx.setIsolated(true);

    var map = ctx.toMap();
    assertEquals(true, map.get("isolated"));
  }

  @Test
  void testContextPrompt() {
    var ctx = new Context("default");
    ctx.addStep("step1").setText("Step 1");
    ctx.setPrompt("You are a helpful assistant.");

    var map = ctx.toMap();
    assertEquals("You are a helpful assistant.", map.get("prompt"));
  }

  @Test
  void testContextPomSections() {
    var ctx = new Context("default");
    ctx.addStep("step1").setText("Step 1");
    ctx.addSection("Role", "You are an assistant.");
    ctx.addBullets("Rules", List.of("Be helpful", "Be concise"));

    var map = ctx.toMap();
    assertTrue(map.containsKey("pom"));

    @SuppressWarnings("unchecked")
    var pom = (List<Map<String, Object>>) map.get("pom");
    assertEquals(2, pom.size());
  }

  @Test
  void testContextFillers() {
    var ctx = new Context("default");
    ctx.addStep("step1").setText("Step 1");
    ctx.setEnterFillers(Map.of("en-US", List.of("Welcome!", "Hello!")));
    ctx.setExitFillers(Map.of("en-US", List.of("Goodbye!", "See you!")));

    var map = ctx.toMap();
    assertTrue(map.containsKey("enter_fillers"));
    assertTrue(map.containsKey("exit_fillers"));
  }

  @Test
  void testContextAddFiller() {
    var ctx = new Context("default");
    ctx.addStep("step1").setText("Step 1");
    ctx.addEnterFiller("en-US", List.of("Welcome!"));
    ctx.addExitFiller("es", List.of("Adios!"));

    var map = ctx.toMap();
    assertTrue(map.containsKey("enter_fillers"));
    assertTrue(map.containsKey("exit_fillers"));
  }

  @Test
  void testContextEmptyStepsThrows() {
    var ctx = new Context("default");
    assertThrows(IllegalStateException.class, ctx::toMap);
  }

  @Test
  void testContextValidContexts() {
    var ctx = new Context("default");
    ctx.addStep("step1").setText("Step 1");
    ctx.setValidContexts(List.of("support", "sales"));

    var map = ctx.toMap();
    assertTrue(map.containsKey("valid_contexts"));
  }

  // ======== ContextBuilder Tests ========

  @Test
  void testContextBuilderSingleDefault() {
    var builder = new ContextBuilder();
    var ctx = builder.addContext("default");
    ctx.addStep("greeting").setText("Hello!");

    var map = builder.toMap();
    assertTrue(map.containsKey("default"));
  }

  @Test
  void testContextBuilderMultipleContexts() {
    var builder = new ContextBuilder();

    var sales = builder.addContext("sales");
    sales.addStep("intro").setText("Sales intro");

    var support = builder.addContext("support");
    support.addStep("intro").setText("Support intro");

    var map = builder.toMap();
    assertEquals(2, map.size());
    assertTrue(map.containsKey("sales"));
    assertTrue(map.containsKey("support"));
  }

  @Test
  void testContextBuilderSingleNonDefaultFails() {
    var builder = new ContextBuilder();
    var ctx = builder.addContext("custom");
    ctx.addStep("step1").setText("Step 1");

    assertThrows(IllegalStateException.class, builder::toMap);
  }

  @Test
  void testContextBuilderEmptyFails() {
    var builder = new ContextBuilder();
    assertThrows(IllegalStateException.class, builder::toMap);
  }

  @Test
  void testContextBuilderDuplicateContextFails() {
    var builder = new ContextBuilder();
    builder.addContext("default");

    assertThrows(IllegalStateException.class, () -> builder.addContext("default"));
  }

  @Test
  void testContextBuilderInvalidStepReference() {
    var builder = new ContextBuilder();
    var ctx = builder.addContext("default");
    ctx.addStep("step1").setText("Step 1").setValidSteps(List.of("nonexistent_step"));

    assertThrows(IllegalStateException.class, builder::toMap);
  }

  @Test
  void testContextBuilderValidStepNextIsOk() {
    var builder = new ContextBuilder();
    var ctx = builder.addContext("default");
    ctx.addStep("step1").setText("Step 1").setValidSteps(List.of("next"));
    ctx.addStep("step2").setText("Step 2");

    // "next" is a valid special value
    assertDoesNotThrow(() -> builder.toMap());
  }

  @Test
  void testContextBuilderInvalidContextReference() {
    var builder = new ContextBuilder();
    var ctx = builder.addContext("default");
    ctx.addStep("step1").setText("Step 1");
    ctx.setValidContexts(List.of("nonexistent_context"));

    assertThrows(IllegalStateException.class, builder::toMap);
  }

  @Test
  void testContextBuilderStepInvalidContextReference() {
    var builder = new ContextBuilder();
    var sales = builder.addContext("sales");
    sales.addStep("step1").setText("Step 1").setValidContexts(List.of("nonexistent"));

    var support = builder.addContext("support");
    support.addStep("step1").setText("Step 1");

    assertThrows(IllegalStateException.class, builder::toMap);
  }

  @Test
  void testContextBuilderGatherInfoNoQuestions() {
    var builder = new ContextBuilder();
    var ctx = builder.addContext("default");
    ctx.addStep("step1").setText("Step 1").setGatherInfo(null, null, null);

    assertThrows(IllegalStateException.class, builder::toMap);
  }

  @Test
  void testContextBuilderGatherInfoDuplicateKeys() {
    var builder = new ContextBuilder();
    var ctx = builder.addContext("default");
    var step = ctx.addStep("step1");
    step.setText("Step 1")
        .setGatherInfo(null, null, null)
        .addGatherQuestion("name", "What is your name?")
        .addGatherQuestion("name", "Name again?");

    assertThrows(IllegalStateException.class, builder::toMap);
  }

  @Test
  void testContextBuilderGatherInfoNextStepOnLastStep() {
    var builder = new ContextBuilder();
    var ctx = builder.addContext("default");
    ctx.addStep("only_step")
        .setText("Only step")
        .setGatherInfo(null, "next_step", null)
        .addGatherQuestion("name", "Name?");

    assertThrows(IllegalStateException.class, builder::toMap);
  }

  @Test
  void testContextBuilderGatherInfoNextStepValid() {
    var builder = new ContextBuilder();
    var ctx = builder.addContext("default");
    ctx.addStep("step1")
        .setText("Step 1")
        .setGatherInfo(null, "next_step", null)
        .addGatherQuestion("name", "Name?");
    ctx.addStep("step2").setText("Step 2");

    assertDoesNotThrow(() -> builder.toMap());
  }

  @Test
  void testContextBuilderGatherInfoInvalidCompletionAction() {
    var builder = new ContextBuilder();
    var ctx = builder.addContext("default");
    ctx.addStep("step1")
        .setText("Step 1")
        .setGatherInfo(null, "nonexistent_step", null)
        .addGatherQuestion("name", "Name?");
    ctx.addStep("step2").setText("Step 2");

    assertThrows(IllegalStateException.class, builder::toMap);
  }

  @Test
  void testContextBuilderGetContext() {
    var builder = new ContextBuilder();
    builder.addContext("default");

    assertNotNull(builder.getContext("default"));
    assertNull(builder.getContext("nonexistent"));
  }

  @Test
  void testContextBuilderIsEmpty() {
    var builder = new ContextBuilder();
    assertTrue(builder.isEmpty());

    builder.addContext("default");
    assertFalse(builder.isEmpty());
  }

  @Test
  void testContextBuilderEmptyStepsFails() {
    var builder = new ContextBuilder();
    builder.addContext("default"); // No steps added

    assertThrows(IllegalStateException.class, builder::toMap);
  }

  @Test
  void testContextGetStep() {
    var ctx = new Context("default");
    ctx.addStep("step1").setText("Step 1");

    assertNotNull(ctx.getStep("step1"));
    assertNull(ctx.getStep("nonexistent"));
  }
}
