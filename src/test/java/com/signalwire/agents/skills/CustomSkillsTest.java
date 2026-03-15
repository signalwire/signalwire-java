package com.signalwire.agents.skills;

import com.signalwire.agents.skills.builtin.CustomSkillsSkill;
import com.signalwire.agents.swaig.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CustomSkillsTest {

    @Test
    void testSkillProperties() {
        CustomSkillsSkill skill = new CustomSkillsSkill();
        assertEquals("custom_skills", skill.getName());
        assertTrue(skill.supportsMultipleInstances());
    }

    @Test
    void testSetupFailsWithEmptyTools() {
        assertFalse(new CustomSkillsSkill().setup(Map.of("tools", List.of())));
    }

    @Test
    void testSetupSucceeds() {
        List<Map<String, Object>> toolDefs = List.of(
                Map.of("name", "my_tool", "description", "My custom tool")
        );
        assertTrue(new CustomSkillsSkill().setup(Map.of("tools", toolDefs)));
    }

    @Test
    void testRegistersTools() {
        List<Map<String, Object>> toolDefs = List.of(
                Map.of("name", "tool_a", "description", "Tool A"),
                Map.of("name", "tool_b", "description", "Tool B")
        );
        CustomSkillsSkill skill = new CustomSkillsSkill();
        skill.setup(Map.of("tools", toolDefs));
        List<ToolDefinition> tools = skill.registerTools();
        assertEquals(2, tools.size());
        assertEquals("tool_a", tools.get(0).getName());
        assertEquals("tool_b", tools.get(1).getName());
    }

    @Test
    void testToolsHaveHandlers() {
        List<Map<String, Object>> toolDefs = List.of(
                Map.of("name", "tool", "description", "desc")
        );
        CustomSkillsSkill skill = new CustomSkillsSkill();
        skill.setup(Map.of("tools", toolDefs));
        for (var td : skill.registerTools()) {
            assertTrue(td.hasHandler());
        }
    }

    @Test
    void testToolDescriptions() {
        List<Map<String, Object>> toolDefs = List.of(
                Map.of("name", "tool", "description", "Custom description")
        );
        CustomSkillsSkill skill = new CustomSkillsSkill();
        skill.setup(Map.of("tools", toolDefs));
        assertEquals("Custom description", skill.registerTools().get(0).getDescription());
    }
}
