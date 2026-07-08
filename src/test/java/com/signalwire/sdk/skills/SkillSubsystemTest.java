package com.signalwire.sdk.skills;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.skills.builtin.MathSkill;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * Tests for the newly-implemented SKILLS-subsystem parity methods: {@link SkillBase} helpers
 * ({@code defineTool}, {@code getSkillData}, {@code updateSkillData}, {@code validateEnvVars},
 * {@code validatePackages}), {@link SkillManager} lifecycle ({@code loadSkill}/{@code
 * unloadSkill}/{@code getSkill}/{@code listLoadedSkills}), and {@link SkillRegistry} instance
 * methods.
 */
class SkillSubsystemTest {

  // ---- SkillBase.getInstanceKey / namespace-based data round-trip ----

  @Test
  void mathSkillInstanceKeyIsSkillName() {
    MathSkill math = new MathSkill();
    // Single-instance skill: instance key is the skill name.
    assertEquals("math", math.getInstanceKey());
  }

  @Test
  void getSkillDataReadsNamespacedSlice() {
    MathSkill math = new MathSkill();
    Map<String, Object> slice = new LinkedHashMap<>();
    slice.put("count", 3);
    Map<String, Object> globalData = new LinkedHashMap<>();
    globalData.put("skill:math", slice);
    Map<String, Object> rawData = new LinkedHashMap<>();
    rawData.put("global_data", globalData);

    Map<String, Object> read = math.getSkillData(rawData);
    assertEquals(3, read.get("count"));
  }

  @Test
  void getSkillDataReturnsEmptyWhenAbsent() {
    MathSkill math = new MathSkill();
    assertTrue(math.getSkillData(null).isEmpty());
    assertTrue(math.getSkillData(new LinkedHashMap<>()).isEmpty());
    Map<String, Object> noSlice = new LinkedHashMap<>();
    noSlice.put("global_data", new LinkedHashMap<>());
    assertTrue(math.getSkillData(noSlice).isEmpty());
  }

  @Test
  void updateSkillDataWrapsUnderNamespaceAndChains() {
    MathSkill math = new MathSkill();
    FunctionResult result = new FunctionResult("ok");
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("answers", List.of("a", "b"));

    FunctionResult returned = math.updateSkillData(result, data);
    assertSame(result, returned, "updateSkillData returns the same result for chaining");

    // The update becomes a set_global_data action namespaced under skill:math.
    List<Map<String, Object>> actions = result.getActions();
    assertFalse(actions.isEmpty());
    boolean found = false;
    for (Map<String, Object> action : actions) {
      Object payload = action.get("set_global_data");
      if (payload instanceof Map<?, ?> map && map.containsKey("skill:math")) {
        Object slice = map.get("skill:math");
        assertTrue(slice instanceof Map);
        assertEquals(data, slice);
        found = true;
      }
    }
    assertTrue(found, "expected a set_global_data action namespaced under skill:math");
  }

  @Test
  void getSkillDataRoundTripsWithUpdateSkillData() {
    MathSkill math = new MathSkill();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("visited", true);

    FunctionResult result = new FunctionResult("ok");
    math.updateSkillData(result, data);

    // Reconstruct the global_data the wire would carry, then read it back.
    Map<String, Object> setGlobal = null;
    for (Map<String, Object> action : result.getActions()) {
      Object payload = action.get("set_global_data");
      if (payload instanceof Map<?, ?>) {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) payload;
        setGlobal = m;
      }
    }
    assertNotNull(setGlobal);
    Map<String, Object> rawData = new LinkedHashMap<>();
    rawData.put("global_data", setGlobal);

    assertEquals(data, math.getSkillData(rawData));
  }

  // ---- SkillBase.validateEnvVars / validatePackages ----

  @Test
  void validateEnvVarsTrueWhenNoneRequired() {
    // MathSkill requires no env vars.
    assertTrue(new MathSkill().validateEnvVars());
  }

  @Test
  void validateEnvVarsFalseWhenRequiredButUnset() {
    SkillBase needsEnv =
        new MathSkill() {
          @Override
          public List<String> getRequiredEnvVars() {
            return List.of("DEFINITELY_UNSET_ENV_VAR_" + UUID.randomUUID());
          }
        };
    assertFalse(needsEnv.validateEnvVars());
  }

  @Test
  void validatePackagesTrueForInformationalNames() {
    // Bare, non-class package names are informational in Java → satisfied.
    SkillBase skill =
        new MathSkill() {
          @Override
          public List<String> getRequiredPackages() {
            return List.of("requests", "bs4");
          }
        };
    assertTrue(skill.validatePackages());
  }

  @Test
  void validatePackagesFalseForMissingClass() {
    SkillBase skill =
        new MathSkill() {
          @Override
          public List<String> getRequiredPackages() {
            return List.of("com.example.NoSuchClass");
          }
        };
    assertFalse(skill.validatePackages());
  }

  // ---- SkillBase.defineTool merges extra fields ----

  @Test
  void defineToolMergesExtraFields() {
    Map<String, Object> extras = new LinkedHashMap<>();
    extras.put("fillers", List.of("one moment"));
    SkillBase skill =
        new MathSkill() {
          @Override
          public Map<String, Object> getExtraFields() {
            return extras;
          }
        };
    ToolDefinition td =
        skill.defineTool(
            "t", "desc", Map.of("type", "object"), (args, raw) -> new FunctionResult("x"));
    assertEquals("t", td.getName());
    assertNotNull(td.getExtraFields());
    assertEquals(List.of("one moment"), td.getExtraFields().get("fillers"));
  }

  // ---- SkillManager lifecycle ----

  private AgentBase newAgent() {
    return AgentBase.builder().name("sub-test").authUser("u").authPassword("p").build();
  }

  @Test
  void loadSkillThenListAndGetThenUnload() {
    SkillManager mgr = new SkillManager(newAgent());
    assertTrue(mgr.listLoadedSkills().isEmpty());

    boolean loaded = mgr.loadSkill("math", null);
    assertTrue(loaded, "math skill should load");
    assertTrue(mgr.listLoadedSkills().contains("math"));
    assertEquals(1, mgr.loadedSkills().size());

    SkillBase got = mgr.getSkill("math");
    assertNotNull(got);
    assertEquals("math", got.getName());

    boolean unloaded = mgr.unloadSkill("math");
    assertTrue(unloaded);
    assertNull(mgr.getSkill("math"));
    assertTrue(mgr.listLoadedSkills().isEmpty());
  }

  @Test
  void loadSkillUnknownReturnsFalse() {
    SkillManager mgr = new SkillManager(newAgent());
    assertFalse(mgr.loadSkill("no_such_skill", null));
  }

  @Test
  void unloadSkillNotLoadedReturnsFalse() {
    SkillManager mgr = new SkillManager(newAgent());
    assertFalse(mgr.unloadSkill("math"));
  }

  // ---- SkillRegistry instance methods ----

  @Test
  void listSkillsAndDiscoverSkillsAgree() {
    SkillRegistry registry = new SkillRegistry();
    List<Map<String, Object>> listed = registry.listSkills();
    List<Map<String, Object>> discovered = registry.discoverSkills();
    assertEquals(listed, discovered);
    assertEquals(17, listed.size());
    // Each entry carries a name and description.
    Map<String, Object> mathEntry =
        listed.stream().filter(e -> "math".equals(e.get("name"))).findFirst().orElseThrow();
    assertEquals("Perform basic mathematical calculations", mathEntry.get("description"));
  }

  @Test
  void getSkillClassReturnsImplementationClass() {
    SkillRegistry registry = new SkillRegistry();
    Class<? extends SkillBase> cls = registry.getSkillClass("math");
    assertEquals(MathSkill.class, cls);
    assertNull(registry.getSkillClass("no_such_skill"));
  }

  @Test
  void listAllSkillSourcesGroupsBuiltIns() {
    SkillRegistry registry = new SkillRegistry();
    Map<String, List<String>> sources = registry.listAllSkillSources();
    assertTrue(sources.containsKey("built-in"));
    assertTrue(sources.get("built-in").contains("math"));
    assertTrue(sources.containsKey("external_paths"));
    assertTrue(sources.containsKey("entry_points"));
    assertTrue(sources.containsKey("registered"));
  }

  @Test
  void registerSkillAddsFactory() {
    SkillRegistry registry = new SkillRegistry();
    // MathSkill is already registered; registering it again is a no-op-safe path
    // that exercises the reflective instantiation + name derivation.
    assertDoesNotThrow(() -> registry.registerSkill(MathSkill.class));
    assertTrue(SkillRegistry.has("math"));
  }
}
