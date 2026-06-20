package com.signalwire.sdk.skills;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.skills.builtin.NativeVectorSearchSkill;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;
import org.junit.jupiter.api.Test;

class VectorSearchSkillTest {

  @Test
  void testSkillProperties() {
    NativeVectorSearchSkill skill = new NativeVectorSearchSkill();
    assertEquals("native_vector_search", skill.getName());
    assertTrue(skill.supportsMultipleInstances());
  }

  @Test
  void testSetupFailsWithoutRemoteUrl() {
    assertFalse(new NativeVectorSearchSkill().setup(Map.of()));
  }

  @Test
  void testSetupSucceeds() {
    assertTrue(
        new NativeVectorSearchSkill().setup(Map.of("remote_url", "https://search.example.com")));
  }

  @Test
  void testRegistersSearchTool() {
    NativeVectorSearchSkill skill = new NativeVectorSearchSkill();
    skill.setup(Map.of("remote_url", "https://search.example.com"));
    List<ToolDefinition> tools = skill.registerTools();
    assertEquals(1, tools.size());
    assertEquals("search_knowledge", tools.get(0).getName());
  }

  @Test
  void testCustomToolName() {
    NativeVectorSearchSkill skill = new NativeVectorSearchSkill();
    skill.setup(Map.of("remote_url", "https://search.example.com", "tool_name", "search_docs"));
    assertEquals("search_docs", skill.registerTools().get(0).getName());
  }

  @Test
  void testHints() {
    NativeVectorSearchSkill skill = new NativeVectorSearchSkill();
    skill.setup(Map.of("remote_url", "https://search.example.com"));
    var hints = skill.getHints();
    assertTrue(hints.contains("search"));
    assertTrue(hints.contains("knowledge base"));
  }

  @Test
  void testCustomHints() {
    NativeVectorSearchSkill skill = new NativeVectorSearchSkill();
    skill.setup(
        Map.of("remote_url", "https://search.example.com", "hints", List.of("custom1", "custom2")));
    var hints = skill.getHints();
    assertTrue(hints.contains("custom1"));
    assertTrue(hints.contains("custom2"));
  }
}
