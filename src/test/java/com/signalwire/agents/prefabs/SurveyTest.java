package com.signalwire.agents.prefabs;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SurveyTest {

    private List<Map<String, Object>> sampleQuestions() {
        return List.of(
                SurveyAgent.ratingQuestion("Rate us", 1, 5),
                SurveyAgent.multipleChoiceQuestion("Feature?", List.of("Chat", "Voice")),
                SurveyAgent.yesNoQuestion("Recommend us?"),
                SurveyAgent.openEndedQuestion("Comments?")
        );
    }

    @Test
    void testCreation() {
        SurveyAgent prefab = new SurveyAgent("test-survey", sampleQuestions());
        assertNotNull(prefab.getAgent());
        assertEquals("test-survey", prefab.getAgent().getName());
    }

    @Test
    void testHasSurveyTools() {
        SurveyAgent prefab = new SurveyAgent("test", sampleQuestions());
        assertTrue(prefab.getAgent().hasTool("start_survey"));
        assertTrue(prefab.getAgent().hasTool("submit_survey_answer"));
    }

    @Test
    void testStartSurveyExecution() {
        SurveyAgent prefab = new SurveyAgent("test", sampleQuestions());
        var result = prefab.getAgent().onFunctionCall("start_survey", Map.of(), Map.of());
        assertNotNull(result);
        assertTrue(result.getResponse().contains("Question 1"));
        assertTrue(result.getResponse().contains("Rate us"));
    }

    @Test
    void testRatingQuestionHelper() {
        Map<String, Object> q = SurveyAgent.ratingQuestion("Rate", 1, 10);
        assertEquals("Rate", q.get("question"));
        assertEquals("rating", q.get("type"));
        assertEquals(1, q.get("min"));
        assertEquals(10, q.get("max"));
    }

    @Test
    void testMultipleChoiceQuestionHelper() {
        Map<String, Object> q = SurveyAgent.multipleChoiceQuestion("Pick", List.of("A", "B"));
        assertEquals("multiple_choice", q.get("type"));
    }

    @Test
    void testYesNoQuestionHelper() {
        Map<String, Object> q = SurveyAgent.yesNoQuestion("Yes or no?");
        assertEquals("yes_no", q.get("type"));
    }

    @Test
    void testOpenEndedQuestionHelper() {
        Map<String, Object> q = SurveyAgent.openEndedQuestion("Tell us more");
        assertEquals("open_ended", q.get("type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSwmlRendering() {
        SurveyAgent prefab = new SurveyAgent("test", sampleQuestions());
        Map<String, Object> swml = prefab.getAgent().renderSwml("http://localhost:3000");
        assertNotNull(swml);
        assertEquals("1.0.0", swml.get("version"));
    }
}
