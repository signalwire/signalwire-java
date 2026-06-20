package com.signalwire.sdk.skills;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.skills.builtin.WikipediaSearchSkill;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;
import org.junit.jupiter.api.Test;

class WikipediaSkillTest {

  @Test
  void testSkillProperties() {
    WikipediaSearchSkill skill = new WikipediaSearchSkill();
    assertEquals("wikipedia_search", skill.getName());
    assertNotNull(skill.getDescription());
    assertFalse(skill.supportsMultipleInstances());
  }

  @Test
  void testSetupAlwaysSucceeds() {
    assertTrue(new WikipediaSearchSkill().setup(Map.of()));
  }

  @Test
  void testRegistersSearchWikiTool() {
    WikipediaSearchSkill skill = new WikipediaSearchSkill();
    skill.setup(Map.of());
    List<ToolDefinition> tools = skill.registerTools();
    assertEquals(1, tools.size());
    assertEquals("search_wiki", tools.get(0).getName());
  }

  @Test
  void testPromptSections() {
    WikipediaSearchSkill skill = new WikipediaSearchSkill();
    skill.setup(Map.of());
    var sections = skill.getPromptSections();
    assertFalse(sections.isEmpty());
    assertEquals("Wikipedia Search", sections.get(0).get("title"));
  }

  @Test
  void testEmptyQueryReturnsError() {
    WikipediaSearchSkill skill = new WikipediaSearchSkill();
    skill.setup(Map.of());
    var tools = skill.registerTools();
    var result = tools.get(0).getHandler().handle(Map.of("query", ""), Map.of());
    assertTrue(result.getResponse().contains("No query"));
  }

  @Test
  void testCustomNumResults() {
    WikipediaSearchSkill skill = new WikipediaSearchSkill();
    skill.setup(Map.of("num_results", 3));
    // Just verify setup works with custom param
    assertNotNull(skill.registerTools());
  }
}
