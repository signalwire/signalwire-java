package com.signalwire.agents.skills;

import com.signalwire.agents.skills.builtin.WebSearchSkill;
import com.signalwire.agents.swaig.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchSkillTest {

    @Test
    void testSkillProperties() {
        WebSearchSkill skill = new WebSearchSkill();
        assertEquals("web_search", skill.getName());
        assertNotNull(skill.getDescription());
        assertTrue(skill.supportsMultipleInstances());
    }

    @Test
    void testSetupFailsWithoutCredentials() {
        assertFalse(new WebSearchSkill().setup(Map.of()));
    }

    @Test
    void testSetupSucceeds() {
        assertTrue(new WebSearchSkill().setup(Map.of(
                "api_key", "test-key",
                "search_engine_id", "test-cx"
        )));
    }

    @Test
    void testRegistersSearchTool() {
        WebSearchSkill skill = new WebSearchSkill();
        skill.setup(Map.of("api_key", "key", "search_engine_id", "cx"));
        List<ToolDefinition> tools = skill.registerTools();
        assertEquals(1, tools.size());
        assertEquals("web_search", tools.get(0).getName());
    }

    @Test
    void testCustomToolName() {
        WebSearchSkill skill = new WebSearchSkill();
        skill.setup(Map.of("api_key", "key", "search_engine_id", "cx", "tool_name", "search_web"));
        List<ToolDefinition> tools = skill.registerTools();
        assertEquals("search_web", tools.get(0).getName());
    }

    @Test
    void testPromptSections() {
        WebSearchSkill skill = new WebSearchSkill();
        skill.setup(Map.of("api_key", "key", "search_engine_id", "cx"));
        var sections = skill.getPromptSections();
        assertFalse(sections.isEmpty());
    }

    @Test
    void testGlobalData() {
        WebSearchSkill skill = new WebSearchSkill();
        skill.setup(Map.of("api_key", "key", "search_engine_id", "cx"));
        var gd = skill.getGlobalData();
        assertTrue((Boolean) gd.get("web_search_enabled"));
    }

    @Test
    void testEmptyQueryReturnsError() {
        WebSearchSkill skill = new WebSearchSkill();
        skill.setup(Map.of("api_key", "key", "search_engine_id", "cx"));
        var tools = skill.registerTools();
        var result = tools.get(0).getHandler().handle(Map.of("query", ""), Map.of());
        assertTrue(result.getResponse().contains("No query"));
    }
}
