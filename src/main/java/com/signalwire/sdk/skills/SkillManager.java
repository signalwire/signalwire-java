package com.signalwire.sdk.skills;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

/** Manages skill lifecycle: instantiation, validation, registration with the agent. */
public class SkillManager {

  private static final Logger log = Logger.getLogger(SkillManager.class);

  private final AgentBase agent;
  private final Map<String, SkillBase> activeSkills = new LinkedHashMap<>();

  public SkillManager(AgentBase agent) {
    this.agent = agent;
  }

  /**
   * Add a skill to the agent. 1. Get skill factory from registry 2. Create instance 3. Check for
   * duplicates 4. Validate env vars 5. Call setup() 6. Register tools 7. Merge hints 8. Merge
   * global data 9. Add prompt sections
   */
  public void addSkill(String skillName, Map<String, Object> params) {
    if (params == null) params = new LinkedHashMap<>();

    SkillBase skill = SkillRegistry.get(skillName);
    if (skill == null) {
      log.error("Skill not found: %s", skillName);
      return;
    }

    // Check for duplicate instances
    String instanceKey = skillName;
    if (params.containsKey("tool_name")) {
      instanceKey = skillName + ":" + params.get("tool_name");
    }

    if (!skill.supportsMultipleInstances() && activeSkills.containsKey(instanceKey)) {
      log.warn("Skill '%s' does not support multiple instances, skipping duplicate", skillName);
      return;
    }

    // Validate required env vars
    for (String envVar : skill.getRequiredEnvVars()) {
      if (System.getenv(envVar) == null) {
        log.error("Skill '%s' requires env var '%s' which is not set", skillName, envVar);
        return;
      }
    }

    // Setup
    if (!skill.setup(params)) {
      log.error("Skill '%s' setup failed", skillName);
      return;
    }

    // Register tools
    List<ToolDefinition> toolDefs = skill.registerTools();
    if (toolDefs != null) {
      Map<String, Object> extraFields = skill.getExtraFields();
      // Check for swaig_fields in params
      @SuppressWarnings("unchecked")
      Map<String, Object> swaigFields = (Map<String, Object>) params.get("swaig_fields");

      for (ToolDefinition td : toolDefs) {
        if (extraFields != null) {
          td.setExtraFields(extraFields);
        }
        if (swaigFields != null) {
          if (td.getExtraFields() == null) {
            td.setExtraFields(new LinkedHashMap<>(swaigFields));
          } else {
            td.getExtraFields().putAll(swaigFields);
          }
        }
        agent.defineTool(td);
      }
    }

    // Register DataMap/SWAIG functions
    List<Map<String, Object>> swaigFunctions = skill.getSwaigFunctions();
    if (swaigFunctions != null) {
      for (Map<String, Object> func : swaigFunctions) {
        agent.registerSwaigFunction(func);
      }
    }

    // Merge hints
    List<String> hints = skill.getHints();
    if (hints != null && !hints.isEmpty()) {
      agent.addHints(hints);
    }

    // Merge global data
    Map<String, Object> globalData = skill.getGlobalData();
    if (globalData != null && !globalData.isEmpty()) {
      agent.updateGlobalData(globalData);
    }

    // Add prompt sections (unless skip_prompt is set)
    boolean skipPrompt = Boolean.TRUE.equals(params.get("skip_prompt"));
    if (!skipPrompt) {
      List<Map<String, Object>> sections = skill.getPromptSections();
      if (sections != null) {
        for (Map<String, Object> section : sections) {
          String title = (String) section.get("title");
          String body = (String) section.get("body");
          @SuppressWarnings("unchecked")
          List<String> bullets = (List<String>) section.get("bullets");
          agent.promptAddSection(title, body, bullets);
        }
      }
    }

    activeSkills.put(instanceKey, skill);
    log.info("Added skill '%s' (instance: %s)", skillName, instanceKey);
  }

  /** Remove a skill from the agent. */
  public void removeSkill(String skillName) {
    SkillBase removed = activeSkills.remove(skillName);
    if (removed != null) {
      removed.cleanup();
      log.info("Removed skill: %s", skillName);
    }
  }

  /** List active skill instance keys. */
  public List<String> listSkills() {
    return new ArrayList<>(activeSkills.keySet());
  }

  /** Check if a skill is active. */
  public boolean hasSkill(String skillName) {
    return activeSkills.containsKey(skillName);
  }

  /** Cleanup all skills. */
  public void cleanup() {
    for (SkillBase skill : activeSkills.values()) {
      try {
        skill.cleanup();
      } catch (Exception e) {
        log.error("Error cleaning up skill: %s", skill.getName());
      }
    }
    activeSkills.clear();
  }
}
