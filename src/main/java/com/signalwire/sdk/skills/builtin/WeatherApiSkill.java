package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.datamap.DataMap;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

public class WeatherApiSkill implements SkillBase {

  private String apiKey;
  private String toolName = "get_weather";
  private String temperatureUnit = "fahrenheit";

  @Override
  public String getName() {
    return "weather_api";
  }

  @Override
  public String getDescription() {
    return "Get current weather information from WeatherAPI.com";
  }

  @Override
  public boolean setup(Map<String, Object> params) {
    this.apiKey = (String) params.get("api_key");
    if (params.containsKey("tool_name")) {
      this.toolName = (String) params.get("tool_name");
    }
    if (params.containsKey("temperature_unit")) {
      this.temperatureUnit = (String) params.get("temperature_unit");
    }
    return apiKey != null && !apiKey.isEmpty();
  }

  @Override
  public List<ToolDefinition> registerTools() {
    return Collections.emptyList();
  }

  @Override
  public List<Map<String, Object>> getSwaigFunctions() {
    String url =
        "https://api.weatherapi.com/v1/current.json?key="
            + apiKey
            + "&q=${lc:enc:args.location}&aqi=no";

    String outputTemplate;
    if ("celsius".equals(temperatureUnit)) {
      outputTemplate =
          "Temperature: ${response.current.temp_c}C, "
              + "Feels like: ${response.current.feelslike_c}C, "
              + "Condition: ${response.current.condition.text}, "
              + "Humidity: ${response.current.humidity}%, "
              + "Wind: ${response.current.wind_kph} kph";
    } else {
      outputTemplate =
          "Temperature: ${response.current.temp_f}F, "
              + "Feels like: ${response.current.feelslike_f}F, "
              + "Condition: ${response.current.condition.text}, "
              + "Humidity: ${response.current.humidity}%, "
              + "Wind: ${response.current.wind_mph} mph";
    }

    DataMap dm =
        new DataMap(toolName)
            .purpose("Get current weather information for any location")
            .parameter("location", "string", "The location to get weather for", true)
            .webhook("GET", url)
            .output(new FunctionResult(outputTemplate));

    return List.of(dm.toSwaigFunction());
  }

  /**
   * Python parity: {@code weather_api/skill.py get_tools} -- the reference builds the DataMap tool
   * in {@code get_tools()}. In Java that tool is built in {@link #getSwaigFunctions()}, so {@code
   * getTools()} returns exactly that list.
   */
  public List<Map<String, Object>> getTools() {
    return getSwaigFunctions();
  }

  /**
   * Python parity: {@code weather_api/skill.py get_parameter_schema} -- base schema
   * (single-instance skill) plus {@code api_key} (hidden, required, env_var WEATHER_API_KEY),
   * {@code tool_name} (default get_weather), and {@code temperature_unit} (enum
   * fahrenheit/celsius).
   */
  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = SkillParams.base(false, getName());

    SkillParams.addString(
        schema, "api_key", "WeatherAPI.com API key", true, true, "WEATHER_API_KEY");

    Map<String, Object> toolNameField = new LinkedHashMap<>();
    toolNameField.put("type", "string");
    toolNameField.put("description", "Custom name for the weather tool");
    toolNameField.put("default", "get_weather");
    toolNameField.put("required", false);
    schema.put("tool_name", toolNameField);

    Map<String, Object> temperatureUnitField = new LinkedHashMap<>();
    temperatureUnitField.put("type", "string");
    temperatureUnitField.put("description", "Temperature unit to display");
    temperatureUnitField.put("default", "fahrenheit");
    temperatureUnitField.put("required", false);
    temperatureUnitField.put("enum", List.of("fahrenheit", "celsius"));
    schema.put("temperature_unit", temperatureUnitField);

    return schema;
  }
}
