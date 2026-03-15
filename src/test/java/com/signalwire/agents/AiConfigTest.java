package com.signalwire.agents;

import com.signalwire.agents.agent.AgentBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AI configuration: hints, languages, pronunciations, params,
 * global data, native functions, fillers, debug events, function includes,
 * and LLM params.
 */
class AiConfigTest {

    private AgentBase agent;

    @BeforeEach
    void setUp() {
        agent = AgentBase.builder()
                .name("aiconfig-test")
                .authUser("u")
                .authPassword("p")
                .build();
        agent.setPromptText("Test");
    }

    // ======== Hints ========

    @Test
    @SuppressWarnings("unchecked")
    void testAddHintRenderedInSwml() {
        agent.addHint("SignalWire");
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        Map<String, Object> ai = extractAi(swml);
        List<String> hints = (List<String>) ai.get("hints");
        assertNotNull(hints);
        assertTrue(hints.contains("SignalWire"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAddHintsMultiple() {
        agent.addHints(List.of("SWML", "agent", "SDK"));
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        List<String> hints = (List<String>) ai.get("hints");
        assertEquals(3, hints.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAddPatternHint() {
        agent.addPatternHint("*signalwire*");
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        List<String> hints = (List<String>) ai.get("hints");
        assertTrue(hints.contains("*signalwire*"));
    }

    @Test
    void testNoHintsOmitted() {
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        assertFalse(ai.containsKey("hints"));
    }

    // ======== Languages ========

    @Test
    @SuppressWarnings("unchecked")
    void testAddLanguage() {
        agent.addLanguage("English", "en-US", "rachel");
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        List<Map<String, Object>> langs = (List<Map<String, Object>>) ai.get("languages");
        assertNotNull(langs);
        assertEquals(1, langs.size());
        assertEquals("English", langs.get(0).get("name"));
        assertEquals("en-US", langs.get(0).get("code"));
        assertEquals("rachel", langs.get(0).get("voice"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAddLanguageWithOptionalParams() {
        agent.addLanguage("Spanish", "es-ES", "jorge", "nova-2", "um", "google");
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        List<Map<String, Object>> langs = (List<Map<String, Object>>) ai.get("languages");
        Map<String, Object> lang = langs.get(0);
        assertEquals("nova-2", lang.get("speech_model"));
        assertEquals("um", lang.get("filler_word"));
        assertEquals("google", lang.get("engine"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSetLanguagesReplacesAll() {
        agent.addLanguage("English", "en-US", "rachel");
        agent.setLanguages(List.of(
                Map.of("name", "French", "code", "fr-FR", "voice", "amelie")
        ));
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        List<Map<String, Object>> langs = (List<Map<String, Object>>) ai.get("languages");
        assertEquals(1, langs.size());
        assertEquals("French", langs.get(0).get("name"));
    }

    @Test
    void testNoLanguagesOmitted() {
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        assertFalse(ai.containsKey("languages"));
    }

    // ======== Pronunciations ========

    @Test
    @SuppressWarnings("unchecked")
    void testAddPronunciation() {
        agent.addPronunciation("SW", "SignalWire", true);
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        List<Map<String, Object>> prns = (List<Map<String, Object>>) ai.get("pronounce");
        assertNotNull(prns);
        assertEquals(1, prns.size());
        assertEquals("SW", prns.get(0).get("replace"));
        assertEquals("SignalWire", prns.get(0).get("with"));
        assertEquals(true, prns.get(0).get("ignore_case"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSetPronunciationsReplacesAll() {
        agent.addPronunciation("A", "Alpha", true);
        agent.setPronunciations(List.of(
                Map.of("replace", "B", "with", "Bravo", "ignore_case", false)
        ));
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        List<Map<String, Object>> prns = (List<Map<String, Object>>) ai.get("pronounce");
        assertEquals(1, prns.size());
        assertEquals("B", prns.get(0).get("replace"));
    }

    // ======== Params ========

    @Test
    @SuppressWarnings("unchecked")
    void testSetParam() {
        agent.setParam("temperature", 0.7);
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        Map<String, Object> params = (Map<String, Object>) ai.get("params");
        assertNotNull(params);
        assertEquals(0.7, params.get("temperature"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSetParams() {
        agent.setParams(Map.of("temperature", 0.5, "top_p", 0.9));
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        Map<String, Object> params = (Map<String, Object>) ai.get("params");
        assertEquals(0.5, params.get("temperature"));
        assertEquals(0.9, params.get("top_p"));
    }

    @Test
    void testNoParamsOmitted() {
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        assertFalse(ai.containsKey("params"));
    }

    // ======== Global Data ========

    @Test
    void testSetGlobalData() {
        agent.setGlobalData(Map.of("key1", "val1"));
        assertEquals("val1", agent.getGlobalData().get("key1"));
    }

    @Test
    void testSetGlobalDataReplacesAll() {
        agent.setGlobalData(Map.of("a", "1"));
        agent.setGlobalData(Map.of("b", "2"));
        assertFalse(agent.getGlobalData().containsKey("a"));
        assertEquals("2", agent.getGlobalData().get("b"));
    }

    @Test
    void testUpdateGlobalDataMerges() {
        agent.updateGlobalData(Map.of("a", "1"));
        agent.updateGlobalData(Map.of("b", "2"));
        assertEquals("1", agent.getGlobalData().get("a"));
        assertEquals("2", agent.getGlobalData().get("b"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGlobalDataRenderedInSwml() {
        agent.updateGlobalData(Map.of("test_key", "test_val"));
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        Map<String, Object> gd = (Map<String, Object>) ai.get("global_data");
        assertNotNull(gd);
        assertEquals("test_val", gd.get("test_key"));
    }

    // ======== Native Functions ========

    @Test
    @SuppressWarnings("unchecked")
    void testSetNativeFunctions() {
        agent.setNativeFunctions(List.of("check_for_input"));
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
        assertNotNull(swaig);
        List<String> nf = (List<String>) swaig.get("native_functions");
        assertNotNull(nf);
        assertTrue(nf.contains("check_for_input"));
    }

    // ======== Internal Fillers ========

    @Test
    @SuppressWarnings("unchecked")
    void testAddInternalFiller() {
        agent.addInternalFiller("Thinking...", null);
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        List<Map<String, Object>> fillers = (List<Map<String, Object>>) ai.get("internal_fillers");
        assertNotNull(fillers);
        assertEquals("Thinking...", fillers.get(0).get("text"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSetInternalFillersReplacesAll() {
        agent.addInternalFiller("First", null);
        agent.setInternalFillers(List.of(Map.of("text", "Second")));
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        List<Map<String, Object>> fillers = (List<Map<String, Object>>) ai.get("internal_fillers");
        assertEquals(1, fillers.size());
        assertEquals("Second", fillers.get(0).get("text"));
    }

    // ======== Debug Events ========

    @Test
    @SuppressWarnings("unchecked")
    void testEnableDebugEvents() {
        agent.enableDebugEvents();
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        Map<String, Object> debug = (Map<String, Object>) ai.get("debug");
        assertNotNull(debug);
        assertEquals(true, debug.get("events"));
    }

    @Test
    void testDebugEventsOffByDefault() {
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        assertFalse(ai.containsKey("debug"));
    }

    // ======== Function Includes ========

    @Test
    @SuppressWarnings("unchecked")
    void testAddFunctionInclude() {
        agent.addFunctionInclude("https://example.com/tools", null);
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
        assertNotNull(swaig);
        List<Map<String, Object>> includes = (List<Map<String, Object>>) swaig.get("includes");
        assertNotNull(includes);
        assertEquals("https://example.com/tools", includes.get(0).get("url"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSetFunctionIncludesReplacesAll() {
        agent.addFunctionInclude("https://first.com", null);
        agent.setFunctionIncludes(List.of(Map.of("url", "https://second.com")));
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
        List<Map<String, Object>> includes = (List<Map<String, Object>>) swaig.get("includes");
        assertEquals(1, includes.size());
        assertEquals("https://second.com", includes.get(0).get("url"));
    }

    // ======== Prompt LLM Params ========

    @Test
    @SuppressWarnings("unchecked")
    void testSetPromptLlmParams() {
        agent.setPromptLlmParams(Map.of("temperature", 0.3));
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        Map<String, Object> prompt = (Map<String, Object>) ai.get("prompt");
        assertEquals(0.3, prompt.get("temperature"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSetPostPromptLlmParams() {
        agent.setPostPrompt("Summarize");
        agent.setPostPromptLlmParams(Map.of("temperature", 0.1));
        Map<String, Object> ai = extractAi(agent.renderSwml("http://localhost:3000"));
        Map<String, Object> pp = (Map<String, Object>) ai.get("post_prompt");
        assertEquals(0.1, pp.get("temperature"));
    }

    // ======== Method chaining ========

    @Test
    void testAiConfigMethodChaining() {
        AgentBase result = agent
                .addHint("test")
                .addHints(List.of("a", "b"))
                .addPatternHint("*x*")
                .addLanguage("English", "en-US", "rachel")
                .addPronunciation("SW", "SignalWire", true)
                .setParam("temperature", 0.5)
                .setParams(Map.of("top_p", 0.9))
                .setGlobalData(Map.of("k", "v"))
                .updateGlobalData(Map.of("k2", "v2"))
                .setNativeFunctions(List.of("check_for_input"))
                .addInternalFiller("Hold on", null)
                .enableDebugEvents()
                .addFunctionInclude("https://example.com", null)
                .setPromptLlmParams(Map.of("temperature", 0.3))
                .setPostPromptLlmParams(Map.of("temperature", 0.1));
        assertSame(agent, result);
    }

    // ======== Helper ========

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractAi(Map<String, Object> swml) {
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
        return main.stream()
                .filter(v -> v.containsKey("ai"))
                .findFirst()
                .map(v -> (Map<String, Object>) v.get("ai"))
                .orElseThrow();
    }
}
