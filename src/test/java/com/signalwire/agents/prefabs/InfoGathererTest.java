package com.signalwire.agents.prefabs;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class InfoGathererTest {

    @Test
    void testCreation() {
        List<Map<String, Object>> questions = List.of(
                InfoGathererAgent.question("name", "What is your name?"),
                InfoGathererAgent.question("email", "What is your email?")
        );
        InfoGathererAgent prefab = new InfoGathererAgent("test-gatherer", questions);
        assertNotNull(prefab.getAgent());
        assertEquals("test-gatherer", prefab.getAgent().getName());
    }

    @Test
    void testHasInfoGathererSkill() {
        List<Map<String, Object>> questions = List.of(
                InfoGathererAgent.question("name", "What is your name?")
        );
        InfoGathererAgent prefab = new InfoGathererAgent("test", questions);
        assertTrue(prefab.getAgent().hasSkill("info_gatherer"));
    }

    @Test
    void testQuestionHelper() {
        Map<String, Object> q = InfoGathererAgent.question("name", "Name?");
        assertEquals("name", q.get("key_name"));
        assertEquals("Name?", q.get("question_text"));
    }

    @Test
    void testQuestionHelperWithConfirm() {
        Map<String, Object> q = InfoGathererAgent.question("phone", "Phone?", true, null);
        assertTrue((Boolean) q.get("confirm"));
    }

    @Test
    void testQuestionHelperWithPromptAdd() {
        Map<String, Object> q = InfoGathererAgent.question("phone", "Phone?", false, "Must be valid");
        assertEquals("Must be valid", q.get("prompt_add"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSwmlRendering() {
        List<Map<String, Object>> questions = List.of(
                InfoGathererAgent.question("name", "Name?")
        );
        InfoGathererAgent prefab = new InfoGathererAgent("test", questions);
        Map<String, Object> swml = prefab.getAgent().renderSwml("http://localhost:3000");
        assertNotNull(swml);
        assertEquals("1.0.0", swml.get("version"));
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
        assertTrue(main.stream().anyMatch(v -> v.containsKey("ai")));
    }

    @Test
    void testCustomRouteAndPort() {
        List<Map<String, Object>> questions = List.of(
                InfoGathererAgent.question("name", "Name?")
        );
        InfoGathererAgent prefab = new InfoGathererAgent("test", questions, "/custom", 4000);
        assertEquals("/custom", prefab.getAgent().getRoute());
    }
}
