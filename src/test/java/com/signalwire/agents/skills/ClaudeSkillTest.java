package com.signalwire.agents.skills;

import com.signalwire.agents.skills.builtin.ClaudeSkillsSkill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeSkillTest {

    @Test
    void testSkillProperties() {
        ClaudeSkillsSkill skill = new ClaudeSkillsSkill();
        assertEquals("claude_skills", skill.getName());
        assertTrue(skill.supportsMultipleInstances());
    }

    @Test
    void testSetupFailsWithoutPath() {
        assertFalse(new ClaudeSkillsSkill().setup(Map.of()));
    }

    @Test
    void testSetupFailsWithInvalidPath() {
        assertFalse(new ClaudeSkillsSkill().setup(Map.of("skills_path", "/nonexistent/path")));
    }

    @Test
    void testSetupSucceedsWithMdFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("greeting.md"), "# Greeting Skill\nSay hello to users.");
        ClaudeSkillsSkill skill = new ClaudeSkillsSkill();
        assertTrue(skill.setup(Map.of("skills_path", tempDir.toString())));
    }

    @Test
    void testRegistersToolsFromMdFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("greeting.md"), "# Greeting Skill\nSay hello to users.");
        Files.writeString(tempDir.resolve("farewell.md"), "# Farewell Skill\nSay goodbye.");
        ClaudeSkillsSkill skill = new ClaudeSkillsSkill();
        skill.setup(Map.of("skills_path", tempDir.toString()));
        var tools = skill.registerTools();
        assertEquals(2, tools.size());
    }

    @Test
    void testToolNamesArePrefixed(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("greeting.md"), "# Greeting\nHello");
        ClaudeSkillsSkill skill = new ClaudeSkillsSkill();
        skill.setup(Map.of("skills_path", tempDir.toString()));
        var tools = skill.registerTools();
        assertTrue(tools.get(0).getName().startsWith("claude_"));
    }

    @Test
    void testCustomToolPrefix(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("greeting.md"), "# Greeting\nHello");
        ClaudeSkillsSkill skill = new ClaudeSkillsSkill();
        skill.setup(Map.of("skills_path", tempDir.toString(), "tool_prefix", "custom_"));
        var tools = skill.registerTools();
        assertTrue(tools.get(0).getName().startsWith("custom_"));
    }

    @Test
    void testPromptSectionsGenerated(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("greeting.md"), "# Greeting\nHello");
        ClaudeSkillsSkill skill = new ClaudeSkillsSkill();
        skill.setup(Map.of("skills_path", tempDir.toString()));
        assertFalse(skill.getPromptSections().isEmpty());
    }

    @Test
    void testHintsGenerated(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("greeting.md"), "# Greeting\nHello");
        ClaudeSkillsSkill skill = new ClaudeSkillsSkill();
        skill.setup(Map.of("skills_path", tempDir.toString()));
        assertFalse(skill.getHints().isEmpty());
    }

    @Test
    void testSetupFailsWithEmptyDirectory(@TempDir Path tempDir) {
        ClaudeSkillsSkill skill = new ClaudeSkillsSkill();
        assertFalse(skill.setup(Map.of("skills_path", tempDir.toString())));
    }
}
