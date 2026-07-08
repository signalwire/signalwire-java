package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.datamap.DataMap;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

public class PlayBackgroundFileSkill implements SkillBase {

  private String toolName = "play_background_file";
  private List<Map<String, Object>> files = new ArrayList<>();

  @Override
  public String getName() {
    return "play_background_file";
  }

  @Override
  public String getDescription() {
    return "Control background file playback";
  }

  @Override
  public boolean supportsMultipleInstances() {
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean setup(Map<String, Object> params) {
    if (params.containsKey("tool_name")) this.toolName = (String) params.get("tool_name");
    if (params.containsKey("files")) {
      this.files = (List<Map<String, Object>>) params.get("files");
    }
    return !files.isEmpty();
  }

  @Override
  public List<ToolDefinition> registerTools() {
    return Collections.emptyList();
  }

  @Override
  public List<Map<String, Object>> getSwaigFunctions() {
    List<String> actionValues = new ArrayList<>();
    for (Map<String, Object> file : files) {
      String key = (String) file.get("key");
      actionValues.add("start_" + key);
    }
    actionValues.add("stop");

    DataMap dm =
        new DataMap(toolName)
            .purpose("Control background file playback for " + toolName)
            .parameter("action", "string", "Action to perform", true, actionValues);

    // Add expression for stop
    dm.expression(
        "${args.action}",
        "stop",
        new FunctionResult("Stopping background playback").addAction("stop_playback_bg", true));

    // Add expressions for each file
    for (Map<String, Object> file : files) {
      String key = (String) file.get("key");
      String url = (String) file.get("url");
      boolean wait = Boolean.TRUE.equals(file.get("wait"));

      FunctionResult output = new FunctionResult("Playing " + file.get("description"));
      if (wait) {
        output.addAction("playback_bg", Map.of("file", url, "wait", true));
      } else {
        output.addAction("playback_bg", url);
      }

      dm.expression("${args.action}", "start_" + key, output);
    }

    return List.of(dm.toSwaigFunction());
  }

  /** Instance key: the skill name plus the tool name, joined as {@code <skill>_<tool>}. */
  @Override
  public String getInstanceKey() {
    return getName() + "_" + toolName;
  }

  /**
   * Returns this skill's tools. The DataMap tool is built in {@link #getSwaigFunctions()}, so
   * {@code getTools()} returns exactly that list.
   */
  public List<Map<String, Object>> getTools() {
    return getSwaigFunctions();
  }

  /**
   * Parameter schema: base schema plus a {@code files} array (each file: key, description, url,
   * wait).
   */
  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = SkillParams.base(true, getName());

    Map<String, Object> fileProps = new LinkedHashMap<>();
    fileProps.put("key", Map.of("type", "string", "description", "Unique identifier for the file"));
    fileProps.put(
        "description",
        Map.of("type", "string", "description", "Human-readable description of the file"));
    fileProps.put(
        "url", Map.of("type", "string", "description", "URL of the audio/video file to play"));
    fileProps.put(
        "wait",
        Map.of(
            "type",
            "boolean",
            "description",
            "Whether to wait for file to finish playing",
            "default",
            false));

    Map<String, Object> fileItems = new LinkedHashMap<>();
    fileItems.put("type", "object");
    fileItems.put("properties", fileProps);
    fileItems.put("required", List.of("key", "description", "url"));

    Map<String, Object> filesField = new LinkedHashMap<>();
    filesField.put("type", "array");
    filesField.put("description", "Array of file configurations to make available for playback");
    filesField.put("required", true);
    filesField.put("items", fileItems);
    schema.put("files", filesField);

    return schema;
  }
}
