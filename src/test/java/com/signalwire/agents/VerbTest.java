package com.signalwire.agents;

import com.signalwire.agents.agent.AgentBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for 5-phase verb system: pre-answer, answer, post-answer, AI, post-AI verbs.
 */
class VerbTest {

    private AgentBase agent;

    @BeforeEach
    void setUp() {
        agent = AgentBase.builder()
                .name("verb-test")
                .authUser("u")
                .authPassword("p")
                .build();
        agent.setPromptText("Test");
    }

    // ======== Pre-Answer Verbs ========

    @Test
    @SuppressWarnings("unchecked")
    void testPreAnswerVerbBeforeAnswer() {
        agent.addPreAnswerVerb("play", Map.of("url", "ringback.wav"));
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        // First verb should be the pre-answer play
        assertTrue(main.get(0).containsKey("play"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testMultiplePreAnswerVerbs() {
        agent.addPreAnswerVerb("play", Map.of("url", "a.wav"));
        agent.addPreAnswerVerb("play", Map.of("url", "b.wav"));
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        assertTrue(main.get(0).containsKey("play"));
        assertTrue(main.get(1).containsKey("play"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearPreAnswerVerbs() {
        agent.addPreAnswerVerb("play", Map.of("url", "a.wav"));
        agent.clearPreAnswerVerbs();
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        // First verb should be answer, not play
        assertTrue(main.get(0).containsKey("answer"));
    }

    // ======== Answer Verb ========

    @Test
    @SuppressWarnings("unchecked")
    void testAnswerVerbPresent() {
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        boolean hasAnswer = main.stream().anyMatch(v -> v.containsKey("answer"));
        assertTrue(hasAnswer);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAnswerVerbHasMaxDuration() {
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        Map<String, Object> answerVerb = main.stream()
                .filter(v -> v.containsKey("answer"))
                .findFirst().orElseThrow();
        Map<String, Object> params = (Map<String, Object>) answerVerb.get("answer");
        assertTrue(params.containsKey("max_duration"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAutoAnswerDisabled() {
        AgentBase noAnswer = AgentBase.builder()
                .name("no-answer")
                .autoAnswer(false)
                .authUser("u")
                .authPassword("p")
                .build();
        noAnswer.setPromptText("Test");
        Map<String, Object> swml = noAnswer.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        boolean hasAnswer = main.stream().anyMatch(v -> v.containsKey("answer"));
        assertFalse(hasAnswer);
    }

    // ======== Post-Answer Verbs ========

    @Test
    @SuppressWarnings("unchecked")
    void testPostAnswerVerbAfterAnswer() {
        agent.addPostAnswerVerb("sleep", 500);
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        // Find the answer index
        int answerIdx = -1;
        int sleepIdx = -1;
        for (int i = 0; i < main.size(); i++) {
            if (main.get(i).containsKey("answer")) answerIdx = i;
            if (main.get(i).containsKey("sleep")) sleepIdx = i;
        }
        assertTrue(answerIdx >= 0);
        assertTrue(sleepIdx > answerIdx);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearPostAnswerVerbs() {
        agent.addPostAnswerVerb("sleep", 500);
        agent.clearPostAnswerVerbs();
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        boolean hasSleep = main.stream().anyMatch(v -> v.containsKey("sleep"));
        assertFalse(hasSleep);
    }

    // ======== Post-AI Verbs ========

    @Test
    @SuppressWarnings("unchecked")
    void testPostAiVerbAfterAi() {
        agent.addPostAiVerb("hangup", Map.of());
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        // Last verb should be hangup
        assertTrue(main.get(main.size() - 1).containsKey("hangup"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testClearPostAiVerbs() {
        agent.addPostAiVerb("hangup", Map.of());
        agent.clearPostAiVerbs();
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        // Last verb should be the AI verb, not hangup
        assertTrue(main.get(main.size() - 1).containsKey("ai"));
    }

    // ======== Answer Verbs (Phase 2 custom) ========

    @Test
    @SuppressWarnings("unchecked")
    void testAnswerVerb() {
        agent.addAnswerVerb("play", Map.of("url", "welcome.wav"));
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        // The play verb should appear after answer
        int answerIdx = -1;
        int playIdx = -1;
        for (int i = 0; i < main.size(); i++) {
            if (main.get(i).containsKey("answer")) answerIdx = i;
            if (main.get(i).containsKey("play")) playIdx = i;
        }
        assertTrue(answerIdx >= 0);
        assertTrue(playIdx > answerIdx);
    }

    // ======== Record Call ========

    @Test
    @SuppressWarnings("unchecked")
    void testRecordCallVerb() {
        AgentBase recAgent = AgentBase.builder()
                .name("rec")
                .recordCall(true)
                .recordFormat("mp3")
                .recordStereo(false)
                .authUser("u")
                .authPassword("p")
                .build();
        recAgent.setPromptText("Test");
        Map<String, Object> swml = recAgent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        boolean hasRecord = main.stream().anyMatch(v -> v.containsKey("record_call"));
        assertTrue(hasRecord);
    }

    // ======== Phase ordering ========

    @Test
    @SuppressWarnings("unchecked")
    void testPhaseOrdering() {
        agent.addPreAnswerVerb("play", Map.of("url", "pre.wav"));
        agent.addPostAnswerVerb("sleep", 100);
        agent.addPostAiVerb("hangup", Map.of());

        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);

        int prePlayIdx = -1, answerIdx = -1, sleepIdx = -1, aiIdx = -1, hangupIdx = -1;
        for (int i = 0; i < main.size(); i++) {
            if (main.get(i).containsKey("play")) prePlayIdx = i;
            if (main.get(i).containsKey("answer")) answerIdx = i;
            if (main.get(i).containsKey("sleep")) sleepIdx = i;
            if (main.get(i).containsKey("ai")) aiIdx = i;
            if (main.get(i).containsKey("hangup")) hangupIdx = i;
        }

        assertTrue(prePlayIdx < answerIdx, "pre-answer before answer");
        assertTrue(answerIdx < sleepIdx, "answer before post-answer");
        assertTrue(sleepIdx < aiIdx, "post-answer before AI");
        assertTrue(aiIdx < hangupIdx, "AI before post-AI");
    }

    // ======== Method chaining ========

    @Test
    void testVerbMethodChaining() {
        AgentBase result = agent
                .addPreAnswerVerb("play", Map.of())
                .addAnswerVerb("play", Map.of())
                .addPostAnswerVerb("sleep", 100)
                .addPostAiVerb("hangup", Map.of())
                .clearPreAnswerVerbs()
                .clearPostAnswerVerbs()
                .clearPostAiVerbs();
        assertSame(agent, result);
    }

    // ======== Helper ========

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getMain(Map<String, Object> swml) {
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        return (List<Map<String, Object>>) sections.get("main");
    }
}
