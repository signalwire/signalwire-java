package com.signalwire.agents;

import com.signalwire.agents.agent.AgentBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for prompt-related methods on AgentBase (POM, text, sections, post-prompt).
 */
class PromptTest {

    private AgentBase agent;

    @BeforeEach
    void setUp() {
        agent = AgentBase.builder()
                .name("prompt-test")
                .authUser("u")
                .authPassword("p")
                .build();
    }

    // ======== setPromptText ========

    @Test
    void testSetPromptTextReturnsTextMap() {
        agent.setPromptText("Be helpful.");
        @SuppressWarnings("unchecked")
        Map<String, Object> prompt = (Map<String, Object>) agent.getPrompt();
        assertEquals("Be helpful.", prompt.get("text"));
        assertFalse(prompt.containsKey("pom"));
    }

    @Test
    void testSetPromptTextOverwritesPrevious() {
        agent.setPromptText("First");
        agent.setPromptText("Second");
        @SuppressWarnings("unchecked")
        Map<String, Object> prompt = (Map<String, Object>) agent.getPrompt();
        assertEquals("Second", prompt.get("text"));
    }

    @Test
    void testDefaultPromptIsEmptyText() {
        @SuppressWarnings("unchecked")
        Map<String, Object> prompt = (Map<String, Object>) agent.getPrompt();
        assertEquals("", prompt.get("text"));
    }

    // ======== POM Sections ========

    @Test
    void testPromptAddSectionCreatesPom() {
        agent.promptAddSection("Title", "Body text");
        @SuppressWarnings("unchecked")
        Map<String, Object> prompt = (Map<String, Object>) agent.getPrompt();
        assertTrue(prompt.containsKey("pom"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pom = (List<Map<String, Object>>) prompt.get("pom");
        assertEquals(1, pom.size());
        assertEquals("Title", pom.get(0).get("title"));
        assertEquals("Body text", pom.get(0).get("body"));
    }

    @Test
    void testPromptAddSectionWithBullets() {
        agent.promptAddSection("Rules", "Follow these:", List.of("Rule A", "Rule B"));
        @SuppressWarnings("unchecked")
        Map<String, Object> prompt = (Map<String, Object>) agent.getPrompt();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pom = (List<Map<String, Object>>) prompt.get("pom");
        @SuppressWarnings("unchecked")
        List<String> bullets = (List<String>) pom.get(0).get("bullets");
        assertEquals(2, bullets.size());
        assertEquals("Rule A", bullets.get(0));
        assertEquals("Rule B", bullets.get(1));
    }

    @Test
    void testPromptAddSectionNullBulletsOmitted() {
        agent.promptAddSection("Title", "Body", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> prompt = (Map<String, Object>) agent.getPrompt();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pom = (List<Map<String, Object>>) prompt.get("pom");
        assertFalse(pom.get(0).containsKey("bullets"));
    }

    @Test
    void testPromptAddSectionEmptyBulletsOmitted() {
        agent.promptAddSection("Title", "Body", List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> prompt = (Map<String, Object>) agent.getPrompt();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pom = (List<Map<String, Object>>) prompt.get("pom");
        assertFalse(pom.get(0).containsKey("bullets"));
    }

    @Test
    void testPromptAddMultipleSections() {
        agent.promptAddSection("Section1", "Body1");
        agent.promptAddSection("Section2", "Body2");
        agent.promptAddSection("Section3", "Body3");
        @SuppressWarnings("unchecked")
        Map<String, Object> prompt = (Map<String, Object>) agent.getPrompt();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pom = (List<Map<String, Object>>) prompt.get("pom");
        assertEquals(3, pom.size());
    }

    // ======== promptHasSection ========

    @Test
    void testPromptHasSectionTrue() {
        agent.promptAddSection("Exists", "Body");
        assertTrue(agent.promptHasSection("Exists"));
    }

    @Test
    void testPromptHasSectionFalse() {
        assertFalse(agent.promptHasSection("DoesNotExist"));
    }

    // ======== promptAddSubsection ========

    @Test
    void testPromptAddSubsection() {
        agent.promptAddSection("Parent", "Parent body");
        agent.promptAddSubsection("Parent", "Child", "Child body");
        @SuppressWarnings("unchecked")
        Map<String, Object> prompt = (Map<String, Object>) agent.getPrompt();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pom = (List<Map<String, Object>>) prompt.get("pom");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subs = (List<Map<String, Object>>) pom.get(0).get("subsections");
        assertNotNull(subs);
        assertEquals(1, subs.size());
        assertEquals("Child", subs.get(0).get("title"));
    }

    @Test
    void testPromptAddSubsectionToNonexistentParent() {
        // Should not throw, just silently return
        agent.promptAddSubsection("Missing", "Child", "Body");
        assertFalse(agent.promptHasSection("Missing"));
    }

    // ======== promptAddToSection ========

    @Test
    void testPromptAddToSection() {
        agent.promptAddSection("Rules", "Rules body", List.of("R1"));
        agent.promptAddToSection("Rules", List.of("R2", "R3"));
        @SuppressWarnings("unchecked")
        Map<String, Object> prompt = (Map<String, Object>) agent.getPrompt();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pom = (List<Map<String, Object>>) prompt.get("pom");
        @SuppressWarnings("unchecked")
        List<String> bullets = (List<String>) pom.get(0).get("bullets");
        assertEquals(3, bullets.size());
    }

    @Test
    void testPromptAddToSectionCreatesNewBulletsList() {
        agent.promptAddSection("Section", "Body"); // no bullets initially
        agent.promptAddToSection("Section", List.of("B1"));
        @SuppressWarnings("unchecked")
        Map<String, Object> prompt = (Map<String, Object>) agent.getPrompt();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pom = (List<Map<String, Object>>) prompt.get("pom");
        @SuppressWarnings("unchecked")
        List<String> bullets = (List<String>) pom.get(0).get("bullets");
        assertEquals(1, bullets.size());
    }

    @Test
    void testPromptAddToNonexistentSection() {
        // Should not throw
        agent.promptAddToSection("Missing", List.of("B1"));
        assertFalse(agent.promptHasSection("Missing"));
    }

    // ======== setPostPrompt ========

    @Test
    @SuppressWarnings("unchecked")
    void testSetPostPrompt() {
        agent.setPromptText("Main prompt");
        agent.setPostPrompt("Summarize the conversation.");
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
        Map<String, Object> aiVerb = main.stream()
                .filter(v -> v.containsKey("ai"))
                .findFirst()
                .map(v -> (Map<String, Object>) v.get("ai"))
                .orElseThrow();
        Map<String, Object> pp = (Map<String, Object>) aiVerb.get("post_prompt");
        assertNotNull(pp);
        assertEquals("Summarize the conversation.", pp.get("text"));
    }

    // ======== Prompt rendered in SWML ========

    @Test
    @SuppressWarnings("unchecked")
    void testPomRenderedInSwml() {
        agent.promptAddSection("Role", "You are a helpful assistant.");
        agent.promptAddSection("Rules", "", List.of("Be concise", "Be accurate"));
        Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
        Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
        List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
        Map<String, Object> aiVerb = main.stream()
                .filter(v -> v.containsKey("ai"))
                .findFirst()
                .map(v -> (Map<String, Object>) v.get("ai"))
                .orElseThrow();
        Map<String, Object> prompt = (Map<String, Object>) aiVerb.get("prompt");
        assertNotNull(prompt.get("pom"));
    }

    // ======== setPromptText overrides POM ========

    @Test
    @SuppressWarnings("unchecked")
    void testSetPromptTextOverridesPom() {
        agent.promptAddSection("Section", "Body");
        agent.setPromptText("Plain text override");
        Map<String, Object> prompt = (Map<String, Object>) agent.getPrompt();
        assertEquals("Plain text override", prompt.get("text"));
        assertFalse(prompt.containsKey("pom"));
    }

    // ======== Method chaining ========

    @Test
    void testPromptMethodsReturnAgent() {
        AgentBase result = agent.setPromptText("test");
        assertSame(agent, result);

        result = agent.setPostPrompt("post");
        assertSame(agent, result);

        result = agent.promptAddSection("T", "B");
        assertSame(agent, result);

        result = agent.promptAddSubsection("T", "ST", "SB");
        assertSame(agent, result);

        result = agent.promptAddToSection("T", List.of("x"));
        assertSame(agent, result);
    }
}
