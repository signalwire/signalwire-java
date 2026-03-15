package com.signalwire.agents.skills;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegistryTest {

    @Test
    void testRegistryHas18Skills() {
        assertEquals(18, SkillRegistry.list().size());
    }

    @Test
    void testRegistryContainsAllExpectedSkills() {
        Set<String> skills = SkillRegistry.list();
        assertTrue(skills.contains("datetime"));
        assertTrue(skills.contains("math"));
        assertTrue(skills.contains("joke"));
        assertTrue(skills.contains("weather_api"));
        assertTrue(skills.contains("web_search"));
        assertTrue(skills.contains("wikipedia_search"));
        assertTrue(skills.contains("google_maps"));
        assertTrue(skills.contains("spider"));
        assertTrue(skills.contains("datasphere"));
        assertTrue(skills.contains("datasphere_serverless"));
        assertTrue(skills.contains("swml_transfer"));
        assertTrue(skills.contains("play_background_file"));
        assertTrue(skills.contains("api_ninjas_trivia"));
        assertTrue(skills.contains("native_vector_search"));
        assertTrue(skills.contains("info_gatherer"));
        assertTrue(skills.contains("claude_skills"));
        assertTrue(skills.contains("mcp_gateway"));
        assertTrue(skills.contains("custom_skills"));
    }

    @Test
    void testGetExistingSkill() {
        SkillBase skill = SkillRegistry.get("datetime");
        assertNotNull(skill);
        assertEquals("datetime", skill.getName());
    }

    @Test
    void testGetNonexistentSkillReturnsNull() {
        assertNull(SkillRegistry.get("nonexistent"));
    }

    @Test
    void testHasSkill() {
        assertTrue(SkillRegistry.has("math"));
        assertFalse(SkillRegistry.has("nonexistent"));
    }

    @Test
    void testGetCreatesNewInstanceEachTime() {
        SkillBase s1 = SkillRegistry.get("datetime");
        SkillBase s2 = SkillRegistry.get("datetime");
        assertNotNull(s1);
        assertNotNull(s2);
        assertNotSame(s1, s2);
    }

    @Test
    void testListReturnsUnmodifiable() {
        Set<String> skills = SkillRegistry.list();
        assertThrows(UnsupportedOperationException.class, () -> skills.add("new_skill"));
    }

    @Test
    void testEachSkillHasNameAndDescription() {
        for (String name : SkillRegistry.list()) {
            SkillBase skill = SkillRegistry.get(name);
            assertNotNull(skill, "Failed to instantiate: " + name);
            assertEquals(name, skill.getName());
            assertNotNull(skill.getDescription(), "Missing description for: " + name);
            assertFalse(skill.getDescription().isEmpty(), "Empty description for: " + name);
        }
    }
}
