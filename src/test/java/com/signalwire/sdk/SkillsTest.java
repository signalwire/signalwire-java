package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.skills.SkillName;
import com.signalwire.sdk.skills.SkillRegistry;
import com.signalwire.sdk.skills.builtin.*;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillsTest {

  private AgentBase agent;

  @BeforeEach
  void setUp() {
    agent =
        AgentBase.builder().name("skills-test-agent").authUser("user").authPassword("pass").build();
  }

  // ======== Registry Tests ========

  @Test
  void testRegistryHasAllSkills() {
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
    assertEquals(18, skills.size());
  }

  @Test
  void testRegistryGetExisting() {
    SkillBase skill = SkillRegistry.get("datetime");
    assertNotNull(skill);
    assertEquals("datetime", skill.getName());
  }

  @Test
  void testRegistryGetNonexistent() {
    SkillBase skill = SkillRegistry.get("nonexistent");
    assertNull(skill);
  }

  @Test
  void testRegistryHas() {
    assertTrue(SkillRegistry.has("math"));
    assertFalse(SkillRegistry.has("nonexistent"));
  }

  // ======== Datetime Skill Tests ========

  @Test
  void testDatetimeSkill() {
    DatetimeSkill skill = new DatetimeSkill();
    assertEquals("datetime", skill.getName());
    assertFalse(skill.supportsMultipleInstances());
    assertTrue(skill.setup(Map.of()));

    List<ToolDefinition> tools = skill.registerTools();
    assertEquals(2, tools.size());
    assertEquals("get_current_time", tools.get(0).getName());
    assertEquals("get_current_date", tools.get(1).getName());

    // Test execution
    var timeResult = tools.get(0).getHandler().handle(Map.of("timezone", "UTC"), Map.of());
    assertNotNull(timeResult);
    assertTrue(timeResult.getResponse().contains("UTC"));

    var dateResult = tools.get(1).getHandler().handle(Map.of("timezone", "UTC"), Map.of());
    assertNotNull(dateResult);
    assertTrue(dateResult.getResponse().contains("UTC"));
  }

  @Test
  void testDatetimeInvalidTimezone() {
    DatetimeSkill skill = new DatetimeSkill();
    skill.setup(Map.of());
    var tools = skill.registerTools();
    var result = tools.get(0).getHandler().handle(Map.of("timezone", "Invalid/Zone"), Map.of());
    assertTrue(result.getResponse().contains("Invalid timezone"));
  }

  // ======== Math Skill Tests ========

  @Test
  void testMathSkill() {
    MathSkill skill = new MathSkill();
    assertEquals("math", skill.getName());
    assertTrue(skill.setup(Map.of()));

    List<ToolDefinition> tools = skill.registerTools();
    assertEquals(1, tools.size());
    assertEquals("calculate", tools.get(0).getName());

    // Test basic operations
    var result = tools.get(0).getHandler().handle(Map.of("expression", "2+3"), Map.of());
    assertEquals("Result: 5", result.getResponse());

    result = tools.get(0).getHandler().handle(Map.of("expression", "10*5"), Map.of());
    assertEquals("Result: 50", result.getResponse());

    result = tools.get(0).getHandler().handle(Map.of("expression", "100/4"), Map.of());
    assertEquals("Result: 25", result.getResponse());
  }

  @Test
  void testMathExponentiation() {
    MathSkill skill = new MathSkill();
    skill.setup(Map.of());
    var tools = skill.registerTools();
    var result = tools.get(0).getHandler().handle(Map.of("expression", "2**3"), Map.of());
    assertEquals("Result: 8", result.getResponse());
  }

  @Test
  void testMathParentheses() {
    MathSkill skill = new MathSkill();
    skill.setup(Map.of());
    var tools = skill.registerTools();
    var result = tools.get(0).getHandler().handle(Map.of("expression", "(2+3)*4"), Map.of());
    assertEquals("Result: 20", result.getResponse());
  }

  @Test
  void testMathInvalidExpression() {
    MathSkill skill = new MathSkill();
    skill.setup(Map.of());
    var tools = skill.registerTools();
    var result = tools.get(0).getHandler().handle(Map.of("expression", "abc"), Map.of());
    assertTrue(result.getResponse().contains("Error"));
  }

  // ======== Joke Skill Tests ========

  @Test
  void testJokeSkillSetup() {
    JokeSkill skill = new JokeSkill();
    assertEquals("joke", skill.getName());

    // Should fail without api_key
    assertFalse(skill.setup(Map.of()));

    // Should succeed with api_key
    assertTrue(skill.setup(Map.of("api_key", "test-key")));
  }

  @Test
  void testJokeSkillSwaigFunctions() {
    JokeSkill skill = new JokeSkill();
    skill.setup(Map.of("api_key", "test-key"));
    var functions = skill.getSwaigFunctions();
    assertFalse(functions.isEmpty());
    assertEquals("get_joke", functions.get(0).get("function"));
  }

  @Test
  void testJokeSkillCustomToolName() {
    JokeSkill skill = new JokeSkill();
    skill.setup(Map.of("api_key", "key", "tool_name", "tell_joke"));
    var functions = skill.getSwaigFunctions();
    assertEquals("tell_joke", functions.get(0).get("function"));
  }

  // ======== Weather API Skill Tests ========

  @Test
  void testWeatherApiSkillSetup() {
    WeatherApiSkill skill = new WeatherApiSkill();
    assertFalse(skill.setup(Map.of()));
    assertTrue(skill.setup(Map.of("api_key", "test-key")));
  }

  @Test
  void testWeatherApiSkillSwaigFunctions() {
    WeatherApiSkill skill = new WeatherApiSkill();
    skill.setup(Map.of("api_key", "test-key", "temperature_unit", "celsius"));
    var functions = skill.getSwaigFunctions();
    assertFalse(functions.isEmpty());
    assertEquals("get_weather", functions.get(0).get("function"));
  }

  // ======== Skill Integration with Agent ========

  @Test
  void testAddDatetimeSkill() {
    agent.addSkill("datetime", Map.of());
    assertTrue(agent.hasSkill("datetime"));
    assertTrue(agent.hasTool("get_current_time"));
    assertTrue(agent.hasTool("get_current_date"));
  }

  @Test
  void testAddMathSkill() {
    agent.addSkill("math", Map.of());
    assertTrue(agent.hasSkill("math"));
    assertTrue(agent.hasTool("calculate"));
  }

  @Test
  void testListSkills() {
    agent.addSkill("datetime", Map.of());
    agent.addSkill("math", Map.of());
    List<String> skills = agent.listSkills();
    assertEquals(2, skills.size());
    assertTrue(skills.contains("datetime"));
    assertTrue(skills.contains("math"));
  }

  @Test
  void testRemoveSkill() {
    agent.addSkill("datetime", Map.of());
    assertTrue(agent.hasSkill("datetime"));
    agent.removeSkill("datetime");
    assertFalse(agent.hasSkill("datetime"));
  }

  @Test
  void testSkillNotFound() {
    // Should not throw, just log error
    agent.addSkill("nonexistent_skill", Map.of());
    assertFalse(agent.hasSkill("nonexistent_skill"));
  }

  @Test
  void testAddSkillAcceptsSkillNameEnumOrString() {
    // The enum constant's value is the canonical wire string.
    assertEquals("datetime", SkillName.DATETIME.getValue());

    // addSkill() via the typed enum loads the IDENTICAL skill as the bare
    // string: the real datetime tools register, and hasSkill()/removeSkill()
    // accept the enum too (no mocks — real SkillManager + SkillRegistry).
    agent.addSkill(SkillName.DATETIME, Map.of());
    assertTrue(agent.hasSkill("datetime")); // string lookup
    assertTrue(agent.hasSkill(SkillName.DATETIME)); // enum lookup — same skill
    assertTrue(agent.hasTool("get_current_time")); // real skill actually loaded
    assertTrue(agent.hasTool("get_current_date"));

    agent.removeSkill(SkillName.DATETIME); // remove via the enum
    assertFalse(agent.hasSkill("datetime"));
    assertFalse(agent.hasSkill(SkillName.DATETIME));

    // Parity: the bare string still works identically (Python uses str).
    AgentBase stringAgent =
        AgentBase.builder()
            .name("skillname-string-agent")
            .authUser("user")
            .authPassword("pass")
            .build();
    stringAgent.addSkill("datetime", Map.of());
    assertTrue(stringAgent.hasSkill(SkillName.DATETIME));
    assertTrue(stringAgent.hasTool("get_current_time"));
  }

  // ======== Info Gatherer Skill Tests ========

  @Test
  void testInfoGathererSkill() {
    InfoGathererSkill skill = new InfoGathererSkill();
    assertEquals("info_gatherer", skill.getName());
    assertTrue(skill.supportsMultipleInstances());

    List<Map<String, Object>> questions =
        List.of(
            Map.of("key_name", "name", "question_text", "What is your name?"),
            Map.of("key_name", "email", "question_text", "What is your email?"));

    assertTrue(skill.setup(Map.of("questions", questions)));
    var tools = skill.registerTools();
    assertEquals(2, tools.size());
  }

  @Test
  void testInfoGathererNoQuestions() {
    InfoGathererSkill skill = new InfoGathererSkill();
    assertFalse(skill.setup(Map.of("questions", List.of())));
  }

  // ======== Spider Skill Tests ========

  @Test
  void testSpiderSkill() {
    SpiderSkill skill = new SpiderSkill();
    assertEquals("spider", skill.getName());
    assertTrue(skill.supportsMultipleInstances());
    assertTrue(skill.setup(Map.of()));

    var tools = skill.registerTools();
    assertEquals(3, tools.size());
    assertEquals("scrape_url", tools.get(0).getName());
    assertEquals("crawl_site", tools.get(1).getName());
    assertEquals("extract_structured_data", tools.get(2).getName());
  }

  // ======== Custom Skills Tests ========

  @Test
  void testCustomSkills() {
    CustomSkillsSkill skill = new CustomSkillsSkill();
    assertEquals("custom_skills", skill.getName());
    assertTrue(skill.supportsMultipleInstances());

    List<Map<String, Object>> toolDefs =
        List.of(Map.of("name", "my_tool", "description", "My custom tool"));
    assertTrue(skill.setup(Map.of("tools", toolDefs)));

    var tools = skill.registerTools();
    assertEquals(1, tools.size());
    assertEquals("my_tool", tools.get(0).getName());
  }

  @Test
  void testCustomSkillsEmpty() {
    CustomSkillsSkill skill = new CustomSkillsSkill();
    assertFalse(skill.setup(Map.of("tools", List.of())));
  }

  // ======== Swml Transfer Skill Tests ========

  @Test
  void testSwmlTransferSkill() {
    SwmlTransferSkill skill = new SwmlTransferSkill();
    assertTrue(skill.supportsMultipleInstances());

    Map<String, Map<String, Object>> transfers = new LinkedHashMap<>();
    transfers.put(
        "sales",
        Map.of("url", "https://sales.example.com/swml", "message", "Transferring to sales"));
    transfers.put("support", Map.of("address", "+15551234567"));

    assertTrue(skill.setup(Map.of("transfers", transfers)));

    var functions = skill.getSwaigFunctions();
    assertFalse(functions.isEmpty());

    var hints = skill.getHints();
    assertTrue(hints.contains("transfer"));
    assertTrue(hints.contains("sales"));
  }

  // ======== Skill Prompt Sections ========

  @Test
  void testSkillPromptSections() {
    DatetimeSkill skill = new DatetimeSkill();
    skill.setup(Map.of());
    var sections = skill.getPromptSections();
    assertFalse(sections.isEmpty());
    assertEquals("Date and Time Information", sections.get(0).get("title"));
  }

  // ======== Skill Global Data ========

  @Test
  void testSkillGlobalData() {
    JokeSkill skill = new JokeSkill();
    skill.setup(Map.of("api_key", "test"));
    var globalData = skill.getGlobalData();
    assertTrue((Boolean) globalData.get("joke_skill_enabled"));
  }
}
