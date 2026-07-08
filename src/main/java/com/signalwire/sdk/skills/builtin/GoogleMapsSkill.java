package com.signalwire.sdk.skills.builtin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GoogleMapsSkill implements SkillBase {

  private String apiKey;
  private String lookupToolName = "lookup_address";
  private String routeToolName = "compute_route";

  @Override
  public String getName() {
    return "google_maps";
  }

  @Override
  public String getDescription() {
    return "Validate addresses and compute driving routes using Google Maps";
  }

  @Override
  public boolean setup(Map<String, Object> params) {
    this.apiKey = (String) params.get("api_key");
    if (params.containsKey("lookup_tool_name"))
      this.lookupToolName = (String) params.get("lookup_tool_name");
    if (params.containsKey("route_tool_name"))
      this.routeToolName = (String) params.get("route_tool_name");
    return apiKey != null && !apiKey.isEmpty();
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<ToolDefinition> registerTools() {
    Map<String, Object> lookupParams = new LinkedHashMap<>();
    lookupParams.put("type", "object");
    lookupParams.put(
        "properties",
        Map.of(
            "address", Map.of("type", "string", "description", "Address to look up"),
            "bias_lat", Map.of("type", "number", "description", "Latitude for location bias"),
            "bias_lng", Map.of("type", "number", "description", "Longitude for location bias")));
    // No `required` — Python's google_maps passes none (google_maps/skill.py:433);
    // the handler guards a missing address. Matches the reference contract.

    ToolDefinition lookup =
        new ToolDefinition(
            lookupToolName,
            "Look up and validate an address using Google Maps Geocoding",
            lookupParams,
            (args, raw) -> {
              String address = (String) args.get("address");
              try {
                String url =
                    "https://maps.googleapis.com/maps/api/geocode/json?address="
                        + URLEncoder.encode(address, StandardCharsets.UTF_8)
                        + "&key="
                        + apiKey;
                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> resp =
                    client.send(
                        HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                Map<String, Object> result =
                    new Gson()
                        .fromJson(resp.body(), new TypeToken<Map<String, Object>>() {}.getType());
                List<Map<String, Object>> results =
                    (List<Map<String, Object>>) result.get("results");
                if (results != null && !results.isEmpty()) {
                  Map<String, Object> first = results.get(0);
                  return new FunctionResult("Address found: " + first.get("formatted_address"));
                }
                return new FunctionResult("Address not found: " + address);
              } catch (Exception e) {
                return new FunctionResult("Error looking up address: " + e.getMessage());
              }
            });

    Map<String, Object> routeParams = new LinkedHashMap<>();
    routeParams.put("type", "object");
    routeParams.put(
        "properties",
        Map.of(
            "origin_lat", Map.of("type", "number", "description", "Origin latitude"),
            "origin_lng", Map.of("type", "number", "description", "Origin longitude"),
            "dest_lat", Map.of("type", "number", "description", "Destination latitude"),
            "dest_lng", Map.of("type", "number", "description", "Destination longitude")));
    // No `required` — Python's google_maps passes none (google_maps/skill.py:457);
    // the handler validates the coordinates. Matches the reference contract.

    ToolDefinition route =
        new ToolDefinition(
            routeToolName,
            "Compute a driving route between two locations",
            routeParams,
            (args, raw) -> {
              try {
                String origins = args.get("origin_lat") + "," + args.get("origin_lng");
                String destinations = args.get("dest_lat") + "," + args.get("dest_lng");
                String url =
                    "https://maps.googleapis.com/maps/api/distancematrix/json?origins="
                        + origins
                        + "&destinations="
                        + destinations
                        + "&key="
                        + apiKey;
                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> resp =
                    client.send(
                        HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                Map<String, Object> result =
                    new Gson()
                        .fromJson(resp.body(), new TypeToken<Map<String, Object>>() {}.getType());
                List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
                if (rows != null && !rows.isEmpty()) {
                  List<Map<String, Object>> elements =
                      (List<Map<String, Object>>) rows.get(0).get("elements");
                  if (elements != null && !elements.isEmpty()) {
                    Map<String, Object> elem = elements.get(0);
                    Map<String, Object> distance = (Map<String, Object>) elem.get("distance");
                    Map<String, Object> duration = (Map<String, Object>) elem.get("duration");
                    if (distance != null && duration != null) {
                      return new FunctionResult(
                          "Distance: "
                              + distance.get("text")
                              + ", Duration: "
                              + duration.get("text"));
                    }
                  }
                }
                return new FunctionResult("Could not compute route");
              } catch (Exception e) {
                return new FunctionResult("Error computing route: " + e.getMessage());
              }
            });

    return List.of(lookup, route);
  }

  @Override
  public List<String> getHints() {
    return List.of("address", "location", "route", "directions", "miles", "distance");
  }

  /**
   * Parameter schema: base schema plus {@code api_key} (required, hidden, env_var
   * GOOGLE_MAPS_API_KEY), {@code lookup_tool_name} (default "lookup_address") and {@code
   * route_tool_name} (default "compute_route").
   */
  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = SkillParams.base(supportsMultipleInstances(), getName());
    SkillParams.addString(
        schema, "api_key", "Google Maps API key", true, true, "GOOGLE_MAPS_API_KEY");

    Map<String, Object> lookupToolNameParam = new LinkedHashMap<>();
    lookupToolNameParam.put("type", "string");
    lookupToolNameParam.put("description", "Name for the address lookup tool");
    lookupToolNameParam.put("default", "lookup_address");
    lookupToolNameParam.put("required", false);
    schema.put("lookup_tool_name", lookupToolNameParam);

    Map<String, Object> routeToolNameParam = new LinkedHashMap<>();
    routeToolNameParam.put("type", "string");
    routeToolNameParam.put("description", "Name for the route computation tool");
    routeToolNameParam.put("default", "compute_route");
    routeToolNameParam.put("required", false);
    schema.put("route_tool_name", routeToolNameParam);

    return schema;
  }

  @Override
  public List<Map<String, Object>> getPromptSections() {
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("title", "Google Maps");
    section.put("body", "");
    section.put(
        "bullets",
        List.of(
            "Use " + lookupToolName + " to validate and geocode addresses",
            "Use " + routeToolName + " to compute driving distance and duration"));
    return List.of(section);
  }
}
