package com.signalwire.agents.skills;

import com.signalwire.agents.skills.builtin.JokeSkill;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class JokeSkillTest {

    @Test
    void testSkillProperties() {
        JokeSkill skill = new JokeSkill();
        assertEquals("joke", skill.getName());
        assertNotNull(skill.getDescription());
    }

    @Test
    void testSetupFailsWithoutApiKey() {
        assertFalse(new JokeSkill().setup(Map.of()));
    }

    @Test
    void testSetupSucceedsWithApiKey() {
        assertTrue(new JokeSkill().setup(Map.of("api_key", "test-key")));
    }

    @Test
    void testSwaigFunctionsReturned() {
        JokeSkill skill = new JokeSkill();
        skill.setup(Map.of("api_key", "test-key"));
        var fns = skill.getSwaigFunctions();
        assertFalse(fns.isEmpty());
        assertEquals("get_joke", fns.get(0).get("function"));
    }

    @Test
    void testCustomToolName() {
        JokeSkill skill = new JokeSkill();
        skill.setup(Map.of("api_key", "key", "tool_name", "tell_joke"));
        var fns = skill.getSwaigFunctions();
        assertEquals("tell_joke", fns.get(0).get("function"));
    }

    @Test
    void testGlobalData() {
        JokeSkill skill = new JokeSkill();
        skill.setup(Map.of("api_key", "test"));
        var gd = skill.getGlobalData();
        assertTrue((Boolean) gd.get("joke_skill_enabled"));
    }

    @Test
    void testRegisterToolsEmpty() {
        JokeSkill skill = new JokeSkill();
        skill.setup(Map.of("api_key", "test"));
        assertTrue(skill.registerTools().isEmpty());
    }
}
