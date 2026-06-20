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
}
