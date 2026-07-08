package com.signalwire.sdk.skills;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.skills.builtin.ClaudeSkillsSkill;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

  // ======== Slice #75 — SKILL.md directory discovery ========

  /**
   * Python-parity discovery model (registry _discover_skills / claude_skills._discover_skills):
   * each immediate SUBDIRECTORY that declares a SKILL.md is one discovered skill; a subdirectory
   * WITHOUT a SKILL.md is not a valid skill declaration and is rejected (warned), not registered.
   * This test builds a temp skills dir with one VALID skill dir (has SKILL.md) and one INVALID dir
   * (no SKILL.md) and asserts only the valid one registers a tool.
   */
  @Test
  void testDiscoversSkillMdSubdirectoriesAndRejectsInvalid(@TempDir Path tempDir)
      throws IOException {
    // Valid skill: subdir with a SKILL.md.
    Path valid = Files.createDirectory(tempDir.resolve("greeter"));
    Files.writeString(valid.resolve("SKILL.md"), "# Greeter\nSay hello.");

    // Invalid skill: subdir with markdown but NO SKILL.md → must be rejected.
    Path invalid = Files.createDirectory(tempDir.resolve("not_a_skill"));
    Files.writeString(invalid.resolve("README.md"), "# Not a skill\nJust docs.");

    ClaudeSkillsSkill skill = new ClaudeSkillsSkill();
    assertTrue(skill.setup(Map.of("skills_path", tempDir.toString())));

    var tools = skill.registerTools();
    // Exactly the valid SKILL.md subdirectory registered a tool; the invalid one did not.
    assertEquals(1, tools.size());
    assertEquals("claude_greeter", tools.get(0).getName());
  }

  /** A subdir with SKILL.md AND a top-level single-file skill both register (back-compat). */
  @Test
  void testDiscoversBothSubdirAndTopLevelMd(@TempDir Path tempDir) throws IOException {
    Path sub = Files.createDirectory(tempDir.resolve("weather"));
    Files.writeString(sub.resolve("SKILL.md"), "# Weather\nForecast.");
    Files.writeString(tempDir.resolve("standalone.md"), "# Standalone\nOne-file skill.");

    ClaudeSkillsSkill skill = new ClaudeSkillsSkill();
    assertTrue(skill.setup(Map.of("skills_path", tempDir.toString())));

    var names =
        skill.registerTools().stream()
            .map(t -> t.getName())
            .collect(java.util.stream.Collectors.toSet());
    assertTrue(names.contains("claude_weather"));
    assertTrue(names.contains("claude_standalone"));
  }

  /**
   * A skills_path with ONLY invalid subdirectories (none declaring SKILL.md) and no top-level .md
   * discovers nothing → setup returns false.
   */
  @Test
  void testAllInvalidSubdirectoriesDiscoversNothing(@TempDir Path tempDir) throws IOException {
    Path invalid = Files.createDirectory(tempDir.resolve("nope"));
    Files.writeString(invalid.resolve("random.txt"), "not markdown");

    ClaudeSkillsSkill skill = new ClaudeSkillsSkill();
    assertFalse(skill.setup(Map.of("skills_path", tempDir.toString())));
    assertTrue(skill.registerTools().isEmpty());
  }
}
