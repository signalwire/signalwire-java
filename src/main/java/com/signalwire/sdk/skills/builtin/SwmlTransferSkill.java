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
    // No enum on transfer_type — Python's swml_transfer passes none
    // (swml_transfer/skill.py:186); the transfer keys drive the DataMap
    // expression() pattern-matching, not the param's enum. Emitting an enum
    // here leaked the corpus's test destination ("sales") into the wire
    // contract and diverged from every other port. required stays true.
    DataMap dm =
        new DataMap(toolName)
            .purpose(description)
            .parameter(parameterName, "string", parameterDescription, true);

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

  /**
   * Instance key: the skill name plus the tool name (default {@code "transfer_call"}), joined as
   * {@code <skill>_<tool>}.
   */
  @Override
  public String getInstanceKey() {
    return getName() + "_" + toolName;
  }

  /** Parameter schema: base params plus custom fields. */
  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = SkillParams.base(true, getName());

    Map<String, Object> urlProp =
        Map.of("type", "string", "description", "SWML endpoint URL for agent transfer");
    Map<String, Object> addressProp =
        Map.of("type", "string", "description", "Phone number or SIP address for direct connect");
    Map<String, Object> messageProp = new LinkedHashMap<>();
    messageProp.put("type", "string");
    messageProp.put("description", "Message to say before transferring");
    messageProp.put("default", "Transferring you now...");
    Map<String, Object> returnMessageProp = new LinkedHashMap<>();
    returnMessageProp.put("type", "string");
    returnMessageProp.put("description", "Message when returning from transfer");
    returnMessageProp.put("default", "The transfer is complete. How else can I help you?");
    Map<String, Object> postProcessProp = new LinkedHashMap<>();
    postProcessProp.put("type", "boolean");
    postProcessProp.put("description", "Whether to process message with AI before saying");
    postProcessProp.put("default", true);
    Map<String, Object> finalProp = new LinkedHashMap<>();
    finalProp.put("type", "boolean");
    finalProp.put("description", "Whether transfer is permanent (true) or temporary (false)");
    finalProp.put("default", true);
    Map<String, Object> fromAddrProp =
        Map.of("type", "string", "description", "Caller ID for connect action (optional)");

    Map<String, Object> transferProps = new LinkedHashMap<>();
    transferProps.put("url", urlProp);
    transferProps.put("address", addressProp);
    transferProps.put("message", messageProp);
    transferProps.put("return_message", returnMessageProp);
    transferProps.put("post_process", postProcessProp);
    transferProps.put("final", finalProp);
    transferProps.put("from_addr", fromAddrProp);

    Map<String, Object> transferValueSchema = new LinkedHashMap<>();
    transferValueSchema.put("type", "object");
    transferValueSchema.put("properties", transferProps);

    Map<String, Object> transfers = new LinkedHashMap<>();
    transfers.put("type", "object");
    transfers.put("description", "Transfer configurations mapping patterns to destinations");
    transfers.put("required", true);
    transfers.put("additionalProperties", transferValueSchema);
    schema.put("transfers", transfers);

    Map<String, Object> description = new LinkedHashMap<>();
    description.put("type", "string");
    description.put("description", "Description for the transfer tool");
    description.put("default", "Transfer call based on pattern matching");
    description.put("required", false);
    schema.put("description", description);

    Map<String, Object> parameterName = new LinkedHashMap<>();
    parameterName.put("type", "string");
    parameterName.put("description", "Name of the parameter that accepts the transfer type");
    parameterName.put("default", "transfer_type");
    parameterName.put("required", false);
    schema.put("parameter_name", parameterName);

    Map<String, Object> parameterDescription = new LinkedHashMap<>();
    parameterDescription.put("type", "string");
    parameterDescription.put("description", "Description for the transfer type parameter");
    parameterDescription.put("default", "The type of transfer to perform");
    parameterDescription.put("required", false);
    schema.put("parameter_description", parameterDescription);

    Map<String, Object> defaultMessage = new LinkedHashMap<>();
    defaultMessage.put("type", "string");
    defaultMessage.put("description", "Message when no pattern matches");
    defaultMessage.put("default", "Please specify a valid transfer type.");
    defaultMessage.put("required", false);
    schema.put("default_message", defaultMessage);

    Map<String, Object> defaultPostProcess = new LinkedHashMap<>();
    defaultPostProcess.put("type", "boolean");
    defaultPostProcess.put("description", "Whether to process default message with AI");
    defaultPostProcess.put("default", false);
    defaultPostProcess.put("required", false);
    schema.put("default_post_process", defaultPostProcess);

    Map<String, Object> requiredFields = new LinkedHashMap<>();
    requiredFields.put("type", "object");
    requiredFields.put("description", "Additional required fields to collect before transfer");
    requiredFields.put("default", new LinkedHashMap<>());
    requiredFields.put("required", false);
    requiredFields.put(
        "additionalProperties", Map.of("type", "string", "description", "Field description"));
    schema.put("required_fields", requiredFields);

    return schema;
  }
}
