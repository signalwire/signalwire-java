package com.signalwire.agents.skills;

import com.signalwire.agents.skills.builtin.DatetimeSkill;
import com.signalwire.agents.swaig.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DatetimeSkillTest {

    @Test
    void testSkillProperties() {
        DatetimeSkill skill = new DatetimeSkill();
        assertEquals("datetime", skill.getName());
        assertNotNull(skill.getDescription());
        assertFalse(skill.supportsMultipleInstances());
    }

    @Test
    void testSetupSucceeds() {
        DatetimeSkill skill = new DatetimeSkill();
        assertTrue(skill.setup(Map.of()));
    }

    @Test
    void testRegistersTwoTools() {
        DatetimeSkill skill = new DatetimeSkill();
        skill.setup(Map.of());
        List<ToolDefinition> tools = skill.registerTools();
        assertEquals(2, tools.size());
        assertEquals("get_current_time", tools.get(0).getName());
        assertEquals("get_current_date", tools.get(1).getName());
    }

    @Test
    void testGetCurrentTimeReturnsUtc() {
        DatetimeSkill skill = new DatetimeSkill();
        skill.setup(Map.of());
        var tools = skill.registerTools();
        var result = tools.get(0).getHandler().handle(Map.of("timezone", "UTC"), Map.of());
        assertNotNull(result);
        assertTrue(result.getResponse().contains("UTC"));
    }

    @Test
    void testGetCurrentDateReturnsUtc() {
        DatetimeSkill skill = new DatetimeSkill();
        skill.setup(Map.of());
        var tools = skill.registerTools();
        var result = tools.get(1).getHandler().handle(Map.of("timezone", "UTC"), Map.of());
        assertNotNull(result);
        assertTrue(result.getResponse().contains("UTC"));
    }

    @Test
    void testInvalidTimezone() {
        DatetimeSkill skill = new DatetimeSkill();
        skill.setup(Map.of());
        var tools = skill.registerTools();
        var result = tools.get(0).getHandler().handle(Map.of("timezone", "Invalid/Zone"), Map.of());
        assertTrue(result.getResponse().contains("Invalid timezone"));
    }

    @Test
    void testPromptSections() {
        DatetimeSkill skill = new DatetimeSkill();
        skill.setup(Map.of());
        var sections = skill.getPromptSections();
        assertFalse(sections.isEmpty());
        assertEquals("Date and Time Information", sections.get(0).get("title"));
    }

    @Test
    void testHintsEmpty() {
        DatetimeSkill skill = new DatetimeSkill();
        skill.setup(Map.of());
        assertTrue(skill.getHints().isEmpty());
    }
}
