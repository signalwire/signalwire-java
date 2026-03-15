package com.signalwire.agents.skills;

import com.signalwire.agents.skills.builtin.SpiderSkill;
import com.signalwire.agents.swaig.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SpiderSkillTest {

    @Test
    void testSkillProperties() {
        SpiderSkill skill = new SpiderSkill();
        assertEquals("spider", skill.getName());
        assertNotNull(skill.getDescription());
        assertTrue(skill.supportsMultipleInstances());
    }

    @Test
    void testSetupSucceeds() {
        assertTrue(new SpiderSkill().setup(Map.of()));
    }

    @Test
    void testRegistersThreeTools() {
        SpiderSkill skill = new SpiderSkill();
        skill.setup(Map.of());
        List<ToolDefinition> tools = skill.registerTools();
        assertEquals(3, tools.size());
        assertEquals("scrape_url", tools.get(0).getName());
        assertEquals("crawl_site", tools.get(1).getName());
        assertEquals("extract_structured_data", tools.get(2).getName());
    }

    @Test
    void testToolsHaveHandlers() {
        SpiderSkill skill = new SpiderSkill();
        skill.setup(Map.of());
        var tools = skill.registerTools();
        for (ToolDefinition td : tools) {
            assertTrue(td.hasHandler());
        }
    }

    @Test
    void testToolsHaveDescriptions() {
        SpiderSkill skill = new SpiderSkill();
        skill.setup(Map.of());
        var tools = skill.registerTools();
        for (ToolDefinition td : tools) {
            assertNotNull(td.getDescription());
            assertFalse(td.getDescription().isEmpty());
        }
    }
}
