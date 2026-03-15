package com.signalwire.agents;

import com.signalwire.agents.agent.AgentBase;
import com.signalwire.agents.swaig.FunctionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SWML rendering covering all 5 phases and full document structure.
 */
class RenderTest {

    private AgentBase agent;

    @BeforeEach
    void setUp() {
        agent = AgentBase.builder()
                .name("render-test")
                .authUser("u")
                .authPassword("p")
                .build();
    }

    // ======== Document structure ========

    @Test
    @SuppressWarnings("unchecked")
    void testSwmlVersionIs100() {
        agent.setPromptText("Test");
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        assertEquals("1.0.0", swml.get("version"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSwmlHasSections() {
        agent.setPromptText("Test");
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        assertTrue(swml.containsKey("sections"));
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        assertTrue(sections.containsKey("main"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSwmlMainIsNonEmptyList() {
        agent.setPromptText("Test");
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
        assertNotNull(main);
        assertFalse(main.isEmpty());
    }

    // ======== Phase 1: Pre-answer ========

    @Test
    @SuppressWarnings("unchecked")
    void testPreAnswerVerbsRendered() {
        agent.setPromptText("Test");
        agent.addPreAnswerVerb("play", Map.of("url", "ringback.wav"));
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        assertTrue(main.get(0).containsKey("play"));
    }

    // ======== Phase 2: Answer ========

    @Test
    @SuppressWarnings("unchecked")
    void testAnswerVerbRendered() {
        agent.setPromptText("Test");
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        boolean hasAnswer = main.stream().anyMatch(v -> v.containsKey("answer"));
        assertTrue(hasAnswer);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRecordCallVerbRendered() {
        AgentBase recAgent = AgentBase.builder()
                .name("rec")
                .recordCall(true)
                .authUser("u")
                .authPassword("p")
                .build();
        recAgent.setPromptText("Test");
        Map<String, Object> swml = recAgent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        boolean hasRecord = main.stream().anyMatch(v -> v.containsKey("record_call"));
        assertTrue(hasRecord);
    }

    // ======== Phase 3: Post-answer ========

    @Test
    @SuppressWarnings("unchecked")
    void testPostAnswerVerbsRendered() {
        agent.setPromptText("Test");
        agent.addPostAnswerVerb("sleep", 500);
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        boolean hasSleep = main.stream().anyMatch(v -> v.containsKey("sleep"));
        assertTrue(hasSleep);
    }

    // ======== Phase 4: AI verb ========

    @Test
    @SuppressWarnings("unchecked")
    void testAiVerbRendered() {
        agent.setPromptText("Test");
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        boolean hasAi = main.stream().anyMatch(v -> v.containsKey("ai"));
        assertTrue(hasAi);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAiVerbContainsPrompt() {
        agent.setPromptText("You are helpful.");
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        assertNotNull(ai.get("prompt"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAiVerbContainsPostPrompt() {
        agent.setPromptText("Test");
        agent.setPostPrompt("Summarize.");
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        assertNotNull(ai.get("post_prompt"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAiVerbContainsPostPromptUrl() {
        agent.setPromptText("Test");
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        assertNotNull(ai.get("post_prompt_url"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAiVerbContainsSwaigWithFunctions() {
        agent.setPromptText("Test");
        agent.defineTool("tool1", "desc", Map.of(), (a, r) -> new FunctionResult("ok"));
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
        assertNotNull(swaig);
        List<Map<String, Object>> fns = (List<Map<String, Object>>) swaig.get("functions");
        assertNotNull(fns);
        assertFalse(fns.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAiVerbContainsGlobalData() {
        agent.setPromptText("Test");
        agent.updateGlobalData(Map.of("key", "value"));
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        Map<String, Object> gd = (Map<String, Object>) ai.get("global_data");
        assertNotNull(gd);
        assertEquals("value", gd.get("key"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAiVerbContainsHints() {
        agent.setPromptText("Test");
        agent.addHints(List.of("hint1", "hint2"));
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        List<String> hints = (List<String>) ai.get("hints");
        assertNotNull(hints);
        assertEquals(2, hints.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAiVerbContainsLanguages() {
        agent.setPromptText("Test");
        agent.addLanguage("English", "en-US", "rachel");
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        assertNotNull(ai.get("languages"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAiVerbContainsPronunciations() {
        agent.setPromptText("Test");
        agent.addPronunciation("SW", "SignalWire", true);
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        assertNotNull(ai.get("pronounce"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAiVerbContainsParams() {
        agent.setPromptText("Test");
        agent.setParam("temperature", 0.5);
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        assertNotNull(ai.get("params"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAiVerbContainsContexts() {
        agent.setPromptText("Test");
        var builder = agent.defineContexts();
        var ctx = builder.addContext("default");
        ctx.addStep("greeting").setText("Hello!");
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        assertNotNull(ai.get("contexts"));
    }

    // ======== Phase 5: Post-AI ========

    @Test
    @SuppressWarnings("unchecked")
    void testPostAiVerbsRendered() {
        agent.setPromptText("Test");
        agent.addPostAiVerb("hangup", Map.of());
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        List<Map<String, Object>> main = getMain(swml);
        assertTrue(main.get(main.size() - 1).containsKey("hangup"));
    }

    // ======== JSON rendering ========

    @Test
    void testRenderSwmlJson() {
        agent.setPromptText("Test");
        String json = agent.renderSwmlJson("http://localhost:3000");
        assertNotNull(json);
        assertTrue(json.contains("\"version\""));
        assertTrue(json.contains("1.0.0"));
        assertTrue(json.contains("\"sections\""));
        assertTrue(json.contains("\"main\""));
        assertTrue(json.contains("\"ai\""));
    }

    // ======== Full rendering with everything ========

    @Test
    @SuppressWarnings("unchecked")
    void testFullRenderingWithAllComponents() {
        agent.setPromptText("You are an assistant.");
        agent.setPostPrompt("Summarize the conversation.");
        agent.addHint("SignalWire");
        agent.addLanguage("English", "en-US", "rachel");
        agent.addPronunciation("SW", "SignalWire", true);
        agent.setParam("temperature", 0.5);
        agent.updateGlobalData(Map.of("key", "val"));
        agent.setNativeFunctions(List.of("check_for_input"));
        agent.defineTool("my_tool", "desc", Map.of(), (a, r) -> new FunctionResult("ok"));
        agent.addPreAnswerVerb("play", Map.of("url", "ring.wav"));
        agent.addPostAiVerb("hangup", Map.of());
        agent.enableDebugEvents();

        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        assertNotNull(swml);
        assertEquals("1.0.0", swml.get("version"));

        List<Map<String, Object>> main = getMain(swml);
        assertTrue(main.size() >= 4); // play, answer, ai, hangup at minimum

        Map<String, Object> ai = extractAi(swml);
        assertNotNull(ai.get("prompt"));
        assertNotNull(ai.get("post_prompt"));
        assertNotNull(ai.get("post_prompt_url"));
        assertNotNull(ai.get("hints"));
        assertNotNull(ai.get("languages"));
        assertNotNull(ai.get("pronounce"));
        assertNotNull(ai.get("params"));
        assertNotNull(ai.get("global_data"));
        assertNotNull(ai.get("debug"));
        assertNotNull(ai.get("SWAIG"));
    }

    // ======== Helpers ========

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getMain(Map<String, Object> swml) {
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        return (List<Map<String, Object>>) sections.get("main");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAi(Map<String, Object> swml) {
        List<Map<String, Object>> main = getMain(swml);
        return main.stream()
                .filter(v -> v.containsKey("ai"))
                .findFirst()
                .map(v -> (Map<String, Object>) v.get("ai"))
                .orElseThrow();
    }
}
