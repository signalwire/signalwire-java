package com.signalwire.agents.skills;

import com.signalwire.agents.skills.builtin.ApiNinjaTriviaSkill;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TriviaSkillTest {

    @Test
    void testSkillProperties() {
        ApiNinjaTriviaSkill skill = new ApiNinjaTriviaSkill();
        assertEquals("api_ninjas_trivia", skill.getName());
        assertTrue(skill.supportsMultipleInstances());
    }

    @Test
    void testSetupFailsWithoutApiKey() {
        assertFalse(new ApiNinjaTriviaSkill().setup(Map.of()));
    }

    @Test
    void testSetupSucceeds() {
        assertTrue(new ApiNinjaTriviaSkill().setup(Map.of("api_key", "test-key")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSwaigFunctionsGenerated() {
        ApiNinjaTriviaSkill skill = new ApiNinjaTriviaSkill();
        skill.setup(Map.of("api_key", "test-key"));
        var fns = skill.getSwaigFunctions();
        assertEquals(1, fns.size());
        assertEquals("get_trivia", fns.get(0).get("function"));
        assertTrue(fns.get(0).containsKey("data_map"));
    }

    @Test
    void testCustomToolName() {
        ApiNinjaTriviaSkill skill = new ApiNinjaTriviaSkill();
        skill.setup(Map.of("api_key", "key", "tool_name", "trivia_question"));
        assertEquals("trivia_question", skill.getSwaigFunctions().get(0).get("function"));
    }

    @Test
    void testCustomCategories() {
        ApiNinjaTriviaSkill skill = new ApiNinjaTriviaSkill();
        skill.setup(Map.of("api_key", "key", "categories", List.of("music", "geography")));
        assertNotNull(skill.getSwaigFunctions());
    }

    @Test
    void testRegisterToolsEmpty() {
        ApiNinjaTriviaSkill skill = new ApiNinjaTriviaSkill();
        skill.setup(Map.of("api_key", "key"));
        assertTrue(skill.registerTools().isEmpty());
    }
}
