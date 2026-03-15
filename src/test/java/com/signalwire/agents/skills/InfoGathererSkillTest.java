package com.signalwire.agents.skills;

import com.signalwire.agents.skills.builtin.InfoGathererSkill;
import com.signalwire.agents.swaig.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class InfoGathererSkillTest {

    @Test
    void testSkillProperties() {
        InfoGathererSkill skill = new InfoGathererSkill();
        assertEquals("info_gatherer", skill.getName());
        assertTrue(skill.supportsMultipleInstances());
    }

    @Test
    void testSetupFailsWithEmptyQuestions() {
        assertFalse(new InfoGathererSkill().setup(Map.of("questions", List.of())));
    }

    @Test
    void testSetupSucceedsWithQuestions() {
        List<Map<String, Object>> questions = List.of(
                Map.of("key_name", "name", "question_text", "What is your name?"),
                Map.of("key_name", "email", "question_text", "What is your email?")
        );
        assertTrue(new InfoGathererSkill().setup(Map.of("questions", questions)));
    }

    @Test
    void testRegistersTwoTools() {
        InfoGathererSkill skill = new InfoGathererSkill();
        List<Map<String, Object>> questions = List.of(
                Map.of("key_name", "name", "question_text", "What is your name?"),
                Map.of("key_name", "email", "question_text", "What is your email?")
        );
        skill.setup(Map.of("questions", questions));
        List<ToolDefinition> tools = skill.registerTools();
        assertEquals(2, tools.size());
    }

    @Test
    void testToolsHaveHandlers() {
        InfoGathererSkill skill = new InfoGathererSkill();
        List<Map<String, Object>> questions = List.of(
                Map.of("key_name", "name", "question_text", "Name?")
        );
        skill.setup(Map.of("questions", questions));
        for (var td : skill.registerTools()) {
            assertTrue(td.hasHandler());
        }
    }

    @Test
    void testPromptSections() {
        InfoGathererSkill skill = new InfoGathererSkill();
        List<Map<String, Object>> questions = List.of(
                Map.of("key_name", "name", "question_text", "Name?")
        );
        skill.setup(Map.of("questions", questions));
        assertFalse(skill.getPromptSections().isEmpty());
    }
}
