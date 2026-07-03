package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Load SKILL.md files as agent tools. Reads .md files from a directory, extracts YAML frontmatter,
 * and creates tools.
 */
public class ClaudeSkillsSkill implements SkillBase {

  private static final Logger log = Logger.getLogger(ClaudeSkillsSkill.class);

  private String skillsPath;
  private String toolPrefix = "claude_";
  private final List<ToolDefinition> discoveredTools = new ArrayList<>();
  private final List<Map<String, Object>> discoveredSections = new ArrayList<>();
  private final List<String> discoveredHints = new ArrayList<>();

  @Override
  public String getName() {
    return "claude_skills";
  }

  @Override
  public String getDescription() {
    return "Load Claude SKILL.md files as agent tools";
  }

  @Override
  public boolean supportsMultipleInstances() {
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean setup(Map<String, Object> params) {
    this.skillsPath = (String) params.get("skills_path");
    if (params.containsKey("tool_prefix")) this.toolPrefix = (String) params.get("tool_prefix");

    if (skillsPath == null || skillsPath.isEmpty()) {
      log.error("claude_skills requires 'skills_path' parameter");
      return false;
    }

    Path path = Paths.get(skillsPath);
    if (!Files.isDirectory(path)) {
      log.error("skills_path is not a directory: %s", skillsPath);
      return false;
    }

    try (Stream<Path> stream = Files.walk(path)) {
      List<Path> mdFiles =
          stream
              .filter(p -> p.toString().endsWith(".md") || p.toString().endsWith(".MD"))
              .collect(Collectors.toList());

      for (Path mdFile : mdFiles) {
        try {
          String content = Files.readString(mdFile);
          String fileName = mdFile.getFileName().toString();
          String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
          String sanitized =
              baseName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "_");
          String toolName = toolPrefix + sanitized;

          // Extract description from first line or frontmatter
          String desc = "Execute " + baseName + " skill";
          String[] lines = content.split("\n", 3);
          if (lines.length > 0 && lines[0].startsWith("# ")) {
            desc = lines[0].substring(2).trim();
          }

          Map<String, Object> toolParams = new LinkedHashMap<>();
          toolParams.put("type", "object");
          toolParams.put(
              "properties",
              Map.of(
                  "arguments", Map.of("type", "string", "description", "Arguments for the skill")));
          toolParams.put("required", List.of("arguments"));

          String skillContent = content;
          discoveredTools.add(
              new ToolDefinition(
                  toolName,
                  desc,
                  toolParams,
                  (args, raw) ->
                      new FunctionResult("Skill content for " + baseName + ":\n" + skillContent)));

          // Add prompt section
          Map<String, Object> section = new LinkedHashMap<>();
          section.put("title", baseName);
          section.put("body", desc);
          discoveredSections.add(section);

          // Add hints from name
          for (String word : baseName.split("[-_]", 0)) {
            if (!word.isEmpty()) discoveredHints.add(word.toLowerCase(java.util.Locale.ROOT));
          }

        } catch (IOException e) {
          log.error("Error reading skill file: %s", mdFile);
        }
      }
    } catch (IOException e) {
      log.error("Error scanning skills directory", e);
      return false;
    }

    return !discoveredTools.isEmpty();
  }

  @Override
  public List<ToolDefinition> registerTools() {
    return discoveredTools;
  }

  @Override
  public List<String> getHints() {
    return discoveredHints;
  }

  @Override
  public List<Map<String, Object>> getPromptSections() {
    return discoveredSections;
  }

  /**
   * Python parity: {@code claude_skills/skill.py get_instance_key} -- {@code skills_path =
   * params.get("skills_path", "default"); return f"{SKILL_NAME}_{hash(skills_path) % 10000}"}. Java
   * uses {@code Math.floorMod(skillsPath.hashCode(), 10000)} for a deterministic composite key.
   */
  @Override
  public String getInstanceKey() {
    String path = (skillsPath == null || skillsPath.isEmpty()) ? "default" : skillsPath;
    return getName() + "_" + Math.floorMod(path.hashCode(), 10000);
  }

  /**
   * Python parity: {@code claude_skills/skill.py get_parameter_schema} -- base schema plus the
   * Claude-skills loader params (skills_path, include, exclude, prompt_title, prompt_intro,
   * skill_descriptions, tool_prefix, response_prefix, response_postfix, allow_shell_injection,
   * allow_script_execution, ignore_invocation_control, shell_timeout).
   */
  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = SkillParams.base(true, getName());

    Map<String, Object> skillsPathField = new LinkedHashMap<>();
    skillsPathField.put("type", "string");
    skillsPathField.put(
        "description", "Path to directory containing Claude skill folders (each with SKILL.md)");
    skillsPathField.put("required", true);
    schema.put("skills_path", skillsPathField);

    Map<String, Object> includeField = new LinkedHashMap<>();
    includeField.put("type", "array");
    includeField.put("description", "Glob patterns for skills to include (default: ['*'])");
    includeField.put("default", List.of("*"));
    includeField.put("required", false);
    schema.put("include", includeField);

    Map<String, Object> excludeField = new LinkedHashMap<>();
    excludeField.put("type", "array");
    excludeField.put("description", "Glob patterns for skills to exclude");
    excludeField.put("default", new ArrayList<>());
    excludeField.put("required", false);
    schema.put("exclude", excludeField);

    Map<String, Object> promptTitleField = new LinkedHashMap<>();
    promptTitleField.put("type", "string");
    promptTitleField.put("description", "Title for the prompt section listing skills");
    promptTitleField.put("default", "Claude Skills");
    promptTitleField.put("required", false);
    schema.put("prompt_title", promptTitleField);

    Map<String, Object> promptIntroField = new LinkedHashMap<>();
    promptIntroField.put("type", "string");
    promptIntroField.put("description", "Introductory text for the prompt section");
    promptIntroField.put(
        "default",
        "You have access to specialized skills. Call the appropriate tool when the user's question"
            + " matches:");
    promptIntroField.put("required", false);
    schema.put("prompt_intro", promptIntroField);

    Map<String, Object> skillDescriptionsField = new LinkedHashMap<>();
    skillDescriptionsField.put("type", "object");
    skillDescriptionsField.put(
        "description", "Override descriptions for specific skills (skill_name -> description)");
    skillDescriptionsField.put("default", new LinkedHashMap<>());
    skillDescriptionsField.put("required", false);
    schema.put("skill_descriptions", skillDescriptionsField);

    Map<String, Object> toolPrefixField = new LinkedHashMap<>();
    toolPrefixField.put("type", "string");
    toolPrefixField.put(
        "description",
        "Prefix for generated tool names (default: 'claude_'). Use empty string for no prefix.");
    toolPrefixField.put("default", "claude_");
    toolPrefixField.put("required", false);
    schema.put("tool_prefix", toolPrefixField);

    Map<String, Object> responsePrefixField = new LinkedHashMap<>();
    responsePrefixField.put("type", "string");
    responsePrefixField.put(
        "description", "Text to prepend to skill results (e.g., instructions for the AI)");
    responsePrefixField.put("default", "");
    responsePrefixField.put("required", false);
    schema.put("response_prefix", responsePrefixField);

    Map<String, Object> responsePostfixField = new LinkedHashMap<>();
    responsePostfixField.put("type", "string");
    responsePostfixField.put(
        "description", "Text to append to skill results (e.g., reminders or constraints)");
    responsePostfixField.put("default", "");
    responsePostfixField.put("required", false);
    schema.put("response_postfix", responsePostfixField);

    Map<String, Object> allowShellField = new LinkedHashMap<>();
    allowShellField.put("type", "boolean");
    allowShellField.put(
        "description",
        "Enable !`command` preprocessing in skill bodies. DANGEROUS: allows arbitrary shell"
            + " execution.");
    allowShellField.put("default", false);
    allowShellField.put("required", false);
    schema.put("allow_shell_injection", allowShellField);

    Map<String, Object> allowScriptField = new LinkedHashMap<>();
    allowScriptField.put("type", "boolean");
    allowScriptField.put(
        "description", "Discover and list scripts/, assets/ files in prompt sections");
    allowScriptField.put("default", false);
    allowScriptField.put("required", false);
    schema.put("allow_script_execution", allowScriptField);

    Map<String, Object> ignoreInvocationField = new LinkedHashMap<>();
    ignoreInvocationField.put("type", "boolean");
    ignoreInvocationField.put(
        "description",
        "Override disable-model-invocation and user-invocable flags, register everything");
    ignoreInvocationField.put("default", false);
    ignoreInvocationField.put("required", false);
    schema.put("ignore_invocation_control", ignoreInvocationField);

    Map<String, Object> shellTimeoutField = new LinkedHashMap<>();
    shellTimeoutField.put("type", "integer");
    shellTimeoutField.put("description", "Timeout in seconds for shell injection commands");
    shellTimeoutField.put("default", 30);
    shellTimeoutField.put("required", false);
    schema.put("shell_timeout", shellTimeoutField);

    return schema;
  }
}
