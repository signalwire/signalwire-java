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
}
