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
    // Python parity: setup() gates on validate_packages() first.
    if (!validatePackages()) {
      return false;
    }

    this.skillsPath = (String) params.get("skills_path");
    if (params.containsKey("tool_prefix")) this.toolPrefix = (String) params.get("tool_prefix");

    if (skillsPath == null || skillsPath.isEmpty()) {
      log.error("claude_skills requires 'skills_path' parameter");
      return false;
    }

    // Load-path validation (Python parity: skills_path must exist AND be a directory).
    Path path = Paths.get(skillsPath);
    if (!Files.exists(path)) {
      log.error("skills_path does not exist: %s", skillsPath);
      return false;
    }
    if (!Files.isDirectory(path)) {
      log.error("skills_path is not a directory: %s", skillsPath);
      return false;
    }

    // Primary discovery model (Python parity): each immediate SUBDIRECTORY that
    // declares a SKILL.md is one discovered skill. Mirrors registry
    // SkillRegistry._discover_skills / claude_skills._discover_skills.
    int discovered = discoverSkillMdDirectories(path);

    // Back-compat: also register top-level *.md files placed directly in
    // skills_path (a skill declared as a single markdown file, no subdirectory).
    discovered += discoverTopLevelMdFiles(path);

    if (discovered == 0) {
      log.warn("claude_skills: no skills found in %s", skillsPath);
    }
    return !discoveredTools.isEmpty();
  }

  /**
   * Walk the immediate subdirectories of {@code root}; each subdirectory that declares a {@code
   * SKILL.md} is registered as one skill (its directory name is the skill name). Mirrors Python's
   * {@code _discover_skills}, which iterates {@code skills_path.iterdir()} and requires a {@code
   * SKILL.md} in each candidate directory. Returns the number of valid skills discovered.
   */
  private int discoverSkillMdDirectories(Path root) {
    int count = 0;
    try (Stream<Path> children = Files.list(root)) {
      List<Path> dirs = children.filter(Files::isDirectory).sorted().collect(Collectors.toList());
      for (Path dir : dirs) {
        Path skillFile = dir.resolve("SKILL.md");
        // Case-insensitive SKILL.md match (Python compares name.upper() == "SKILL.MD").
        if (!Files.exists(skillFile)) {
          skillFile = findCaseInsensitive(dir, "SKILL.MD");
        }
        if (skillFile == null || !Files.exists(skillFile)) {
          // A subdirectory WITHOUT a SKILL.md is not a valid skill declaration — skip (warn).
          log.warn("claude_skills: %s has no SKILL.md — not a valid skill, skipping", dir);
          continue;
        }
        String skillName = dir.getFileName().toString();
        if (registerSkillFromFile(skillName, skillFile)) {
          count++;
        }
      }
    } catch (IOException e) {
      log.error("Error scanning skills directory: %s", root);
    }
    return count;
  }

  /**
   * Register top-level {@code *.md} files placed directly in {@code root} (single-file skill
   * declaration). This is the port's back-compat convenience alongside the Python subdirectory
   * model. Returns the number of skills registered.
   */
  private int discoverTopLevelMdFiles(Path root) {
    int count = 0;
    try (Stream<Path> children = Files.list(root)) {
      List<Path> mdFiles =
          children
              .filter(Files::isRegularFile)
              .filter(
                  p -> {
                    String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    return n.endsWith(".md");
                  })
              .sorted()
              .collect(Collectors.toList());
      for (Path mdFile : mdFiles) {
        String fileName = mdFile.getFileName().toString();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        if (registerSkillFromFile(baseName, mdFile)) {
          count++;
        }
      }
    } catch (IOException e) {
      log.error("Error scanning skills directory: %s", root);
    }
    return count;
  }

  /** Find a file in {@code dir} whose name matches {@code upperName} case-insensitively. */
  private Path findCaseInsensitive(Path dir, String upperName) {
    try (Stream<Path> children = Files.list(dir)) {
      return children
          .filter(Files::isRegularFile)
          .filter(
              p -> p.getFileName().toString().toUpperCase(java.util.Locale.ROOT).equals(upperName))
          .findFirst()
          .orElse(null);
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Parse a single SKILL.md (or single-file skill) and register its tool, prompt section, and
   * hints. The tool name is {@code toolPrefix + sanitized(skillName)}. Returns {@code true} on
   * success.
   */
  private boolean registerSkillFromFile(String skillName, Path skillFile) {
    try {
      String content = Files.readString(skillFile);
      String sanitized = skillName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "_");
      String toolName = toolPrefix + sanitized;

      // Extract description from the first "# " heading line (else a default).
      String desc = "Execute " + skillName + " skill";
      String[] lines = content.split("\n", 3);
      if (lines.length > 0 && lines[0].startsWith("# ")) {
        desc = lines[0].substring(2).trim();
      }

      Map<String, Object> toolParams = new LinkedHashMap<>();
      toolParams.put("type", "object");
      toolParams.put(
          "properties",
          Map.of("arguments", Map.of("type", "string", "description", "Arguments for the skill")));
      toolParams.put("required", List.of("arguments"));

      String skillContent = content;
      discoveredTools.add(
          new ToolDefinition(
              toolName,
              desc,
              toolParams,
              (args, raw) ->
                  new FunctionResult("Skill content for " + skillName + ":\n" + skillContent)));

      Map<String, Object> section = new LinkedHashMap<>();
      section.put("title", skillName);
      section.put("body", desc);
      discoveredSections.add(section);

      for (String word : skillName.split("[-_]", 0)) {
        if (!word.isEmpty()) discoveredHints.add(word.toLowerCase(java.util.Locale.ROOT));
      }
      return true;
    } catch (IOException e) {
      log.error("Error reading skill file: %s", skillFile);
      return false;
    }
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
   * Instance key: the skill name plus a deterministic composite of the {@code skills_path} (default
   * {@code "default"}), computed as {@code Math.floorMod(skillsPath.hashCode(), 10000)}.
   */
  @Override
  public String getInstanceKey() {
    String path = (skillsPath == null || skillsPath.isEmpty()) ? "default" : skillsPath;
    return getName() + "_" + Math.floorMod(path.hashCode(), 10000);
  }

  /**
   * Parameter schema: base schema plus the Claude-skills loader params (skills_path, include,
   * exclude, prompt_title, prompt_intro, skill_descriptions, tool_prefix, response_prefix,
   * response_postfix, allow_shell_injection, allow_script_execution, ignore_invocation_control,
   * shell_timeout).
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
