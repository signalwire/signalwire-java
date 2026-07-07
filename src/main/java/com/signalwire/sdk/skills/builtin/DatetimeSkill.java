package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DatetimeSkill implements SkillBase {

  @Override
  public String getName() {
    return "datetime";
  }

  @Override
  public String getDescription() {
    return "Get current date, time, and timezone information";
  }

  @Override
  public boolean supportsMultipleInstances() {
    return false;
  }

  @Override
  public boolean setup(Map<String, Object> params) {
    return true;
  }

  @Override
  public List<ToolDefinition> registerTools() {
    Map<String, Object> tzParam = new LinkedHashMap<>();
    tzParam.put("type", "object");
    tzParam.put(
        "properties",
        Map.of(
            "timezone",
            Map.of("type", "string", "description", "Timezone (e.g. America/New_York, UTC)")));

    ToolDefinition getTime =
        new ToolDefinition(
            "get_current_time",
            "Get the current time, optionally in a specific timezone",
            tzParam,
            (args, raw) -> {
              String tz = (String) args.getOrDefault("timezone", "UTC");
              try {
                ZonedDateTime now = ZonedDateTime.now(ZoneId.of(tz));
                return new FunctionResult(
                    "Current time in "
                        + tz
                        + ": "
                        + now.format(DateTimeFormatter.ofPattern("HH:mm:ss z")));
              } catch (Exception e) {
                return new FunctionResult("Invalid timezone: " + tz);
              }
            });

    ToolDefinition getDate =
        new ToolDefinition(
            "get_current_date",
            "Get the current date",
            tzParam,
            (args, raw) -> {
              String tz = (String) args.getOrDefault("timezone", "UTC");
              try {
                ZonedDateTime now = ZonedDateTime.now(ZoneId.of(tz));
                return new FunctionResult(
                    "Current date in "
                        + tz
                        + ": "
                        + now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")));
              } catch (Exception e) {
                return new FunctionResult("Invalid timezone: " + tz);
              }
            });

    return List.of(getTime, getDate);
  }

  /** Returns an empty hint list. */
  @Override
  public List<String> getHints() {
    return Collections.emptyList();
  }

  /** Returns only the base schema — the datetime skill declares no custom parameters. */
  @Override
  public Map<String, Object> getParameterSchema() {
    return SkillParams.base(supportsMultipleInstances(), getName());
  }

  @Override
  public List<Map<String, Object>> getPromptSections() {
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("title", "Date and Time Information");
    section.put("body", "You have access to current date and time information.");
    section.put(
        "bullets",
        List.of(
            "Use get_current_time to check the current time in any timezone",
            "Use get_current_date to check today's date"));
    return List.of(section);
  }
}
