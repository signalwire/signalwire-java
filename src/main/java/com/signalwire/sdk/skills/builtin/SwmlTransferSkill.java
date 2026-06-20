package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.datamap.DataMap;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

public class SwmlTransferSkill implements SkillBase {

  private String toolName = "transfer_call";
  private String description = "Transfer call based on pattern matching";
  private String parameterName = "transfer_type";
  private String parameterDescription = "The type of transfer to perform";
  private String defaultMessage = "Please hold while I transfer you.";
  private Map<String, Map<String, Object>> transfers = new LinkedHashMap<>();

  @Override
  public String getName() {
    return "swml_transfer";
  }

  @Override
  public String getDescription() {
    return "Transfer calls between agents based on pattern matching";
  }

  @Override
  public boolean supportsMultipleInstances() {
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean setup(Map<String, Object> params) {
    if (params.containsKey("tool_name")) this.toolName = (String) params.get("tool_name");
    if (params.containsKey("description")) this.description = (String) params.get("description");
    if (params.containsKey("parameter_name"))
      this.parameterName = (String) params.get("parameter_name");
    if (params.containsKey("parameter_description"))
      this.parameterDescription = (String) params.get("parameter_description");
    if (params.containsKey("default_message"))
      this.defaultMessage = (String) params.get("default_message");
    if (params.containsKey("transfers")) {
      this.transfers = (Map<String, Map<String, Object>>) params.get("transfers");
    }
    return !transfers.isEmpty();
  }

  @Override
  public List<ToolDefinition> registerTools() {
    return Collections.emptyList();
  }

  @Override
  public List<Map<String, Object>> getSwaigFunctions() {
    List<String> enumValues = new ArrayList<>(transfers.keySet());

    DataMap dm =
        new DataMap(toolName)
            .purpose(description)
            .parameter(parameterName, "string", parameterDescription, true, enumValues);

    // Add expressions for each transfer pattern
    for (Map.Entry<String, Map<String, Object>> entry : transfers.entrySet()) {
      String pattern = entry.getKey();
      Map<String, Object> config = entry.getValue();

      String url = (String) config.get("url");
      String address = (String) config.get("address");
      String message = (String) config.getOrDefault("message", defaultMessage);

      FunctionResult output = new FunctionResult(message);
      if (url != null) {
        output.addAction(
            "SWML",
            Map.of(
                "version",
                "1.0.0",
                "sections",
                Map.of("main", List.of(Map.of("transfer", Map.of("dest", url))))));
      } else if (address != null) {
        output.addAction(
            "SWML",
            Map.of(
                "version",
                "1.0.0",
                "sections",
                Map.of("main", List.of(Map.of("connect", Map.of("to", address))))));
      }

      dm.expression("${args." + parameterName + "}", pattern, output);
    }

    return List.of(dm.toSwaigFunction());
  }

  @Override
  public List<String> getHints() {
    List<String> hints = new ArrayList<>(List.of("transfer", "connect", "speak to", "talk to"));
    for (String pattern : transfers.keySet()) {
      hints.addAll(List.of(pattern.split("[_\\-\\s]+")));
    }
    return hints;
  }

  @Override
  public List<Map<String, Object>> getPromptSections() {
    List<String> destinations = new ArrayList<>();
    for (String key : transfers.keySet()) {
      destinations.add(key);
    }

    Map<String, Object> section = new LinkedHashMap<>();
    section.put("title", "Transferring");
    section.put("body", "Available transfer destinations: " + String.join(", ", destinations));
    section.put(
        "bullets",
        List.of(
            "Use " + toolName + " to transfer the call to the appropriate destination",
            "Always confirm with the caller before transferring"));
    return List.of(section);
  }
}
