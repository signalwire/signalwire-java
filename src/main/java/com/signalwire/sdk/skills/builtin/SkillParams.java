package com.signalwire.sdk.skills.builtin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Package-private helper for building built-in skill parameter schemas.
 *
 * <p>Kept out of the public surface intentionally: it hosts the Java analog of Python's {@code
 * SkillBase.get_parameter_schema()} classmethod (which skills call via {@code super()}), without
 * being a public method that the cross-language audit would flag as an addition. Skills override
 * {@code getParameterSchema()} and start from {@link #base}, then add their own params.
 */
final class SkillParams {

  private SkillParams() {}

  /**
   * The base parameter schema shared by all skills, mirroring Python {@code
   * SkillBase.get_parameter_schema}: {@code swaig_fields} and {@code skip_prompt} for every skill,
   * plus {@code tool_name} when the skill supports multiple instances.
   *
   * @param supportsMultipleInstances whether the skill supports multiple instances
   * @param skillName the skill's name (used as the {@code tool_name} default)
   * @return a mutable base parameter-schema map
   */
  static Map<String, Object> base(boolean supportsMultipleInstances, String skillName) {
    Map<String, Object> schema = new LinkedHashMap<>();

    Map<String, Object> swaigFields = new LinkedHashMap<>();
    swaigFields.put("type", "object");
    swaigFields.put(
        "description", "Additional SWAIG function metadata to merge into tool definitions");
    swaigFields.put("default", new LinkedHashMap<>());
    swaigFields.put("required", false);
    schema.put("swaig_fields", swaigFields);

    Map<String, Object> skipPrompt = new LinkedHashMap<>();
    skipPrompt.put("type", "boolean");
    skipPrompt.put(
        "description",
        "If true, the skill will not inject its default prompt section into the POM");
    skipPrompt.put("default", false);
    skipPrompt.put("required", false);
    schema.put("skip_prompt", skipPrompt);

    if (supportsMultipleInstances) {
      Map<String, Object> toolName = new LinkedHashMap<>();
      toolName.put("type", "string");
      toolName.put("description", "Custom name for this skill instance (for multiple instances)");
      toolName.put("default", skillName);
      toolName.put("required", false);
      schema.put("tool_name", toolName);
    }

    return schema;
  }

  /**
   * Add a single string parameter to a schema map. Convenience for skills adding one field.
   *
   * @param schema the schema to mutate
   * @param name the parameter name
   * @param description the parameter description
   * @param required whether the parameter is required
   * @param hidden whether the parameter is hidden (secrets)
   * @param envVar the backing environment variable, or null
   * @return {@code schema}, for chaining
   */
  static Map<String, Object> addString(
      Map<String, Object> schema,
      String name,
      String description,
      boolean required,
      boolean hidden,
      String envVar) {
    Map<String, Object> field = new LinkedHashMap<>();
    field.put("type", "string");
    field.put("description", description);
    field.put("required", required);
    if (hidden) {
      field.put("hidden", true);
    }
    if (envVar != null) {
      field.put("env_var", envVar);
    }
    schema.put(name, field);
    return schema;
  }
}
