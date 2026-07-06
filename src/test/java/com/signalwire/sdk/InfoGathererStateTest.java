/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signalwire.sdk.prefabs.InfoGathererAgent;
import com.signalwire.sdk.swaig.FunctionResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behavioral contract #3 (BEHAVIORAL_CONTRACTS.md): {@code InfoGathererAgent.submitAnswer} is a
 * real state machine, not an "Answer recorded" echo stub. With 2 questions (index 0), submitting an
 * answer must (a) record the answer in global_data.answers, (b) advance question_index to 1, and
 * (c) present the SECOND question. The state update rides on the returned {@link FunctionResult} as
 * a {@code set_global_data} action (the SWAIG mechanism for mutating global_data).
 */
class InfoGathererStateTest {

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("submitAnswer records the answer, advances the index, and presents the 2nd question")
  void submitAnswerAdvancesState() {
    List<Map<String, Object>> questions =
        List.of(
            InfoGathererAgent.question("name", "What is your name?"),
            InfoGathererAgent.question("color", "What is your favorite color?"));

    InfoGathererAgent gatherer = new InfoGathererAgent("survey", questions);

    // The SWAIG runtime hands the handler global_data (index 0) via rawData.
    Map<String, Object> globalData =
        Map.of("questions", questions, "question_index", 0, "answers", new java.util.ArrayList<>());
    Map<String, Object> rawData = Map.of("global_data", globalData);

    FunctionResult result = gatherer.submitAnswer(Map.of("answer", "Alice"), rawData);

    // The set_global_data action carries the advanced state.
    Map<String, Object> update = null;
    for (Map<String, Object> action : result.getActions()) {
      if (action.containsKey("set_global_data")) {
        update = (Map<String, Object>) action.get("set_global_data");
        break;
      }
    }
    assertNotNull(update, "submitAnswer must emit a set_global_data action with the new state");

    // (a) answer recorded
    List<Map<String, Object>> answers = (List<Map<String, Object>>) update.get("answers");
    assertNotNull(answers, "answers must be recorded in the global_data update");
    assertEquals(1, answers.size(), "exactly one answer should be recorded");
    assertEquals("name", answers.get(0).get("key_name"));
    assertEquals("Alice", answers.get(0).get("answer"));

    // (b) index advanced
    assertEquals(
        1,
        ((Number) update.get("question_index")).intValue(),
        "question_index must advance from 0 to 1");

    // (c) the 2nd question is presented in the response text
    assertTrue(
        result.getResponse().contains("What is your favorite color?"),
        "the result must present the 2nd question, got: " + result.getResponse());
  }
}
