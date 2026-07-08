package com.signalwire.sdk.prefabs;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

/** Pre-built agent for venue concierge with amenity info and availability checking. */
public class ConciergeAgent {

  private final AgentBase agent;
  private final String venueName;
  private final List<Map<String, Object>> amenities;
  private java.util.function.BiConsumer<Map<String, Object>, Map<String, Object>> summaryHandler;

  public ConciergeAgent(String name, String venueName, List<Map<String, Object>> amenities) {
    this(name, venueName, amenities, "/", 3000);
  }

  public ConciergeAgent(
      String name, String venueName, List<Map<String, Object>> amenities, String route, int port) {
    this.venueName = venueName;
    this.amenities = amenities;
    this.agent = AgentBase.builder().name(name).route(route).port(port).build();

    agent.promptAddSection(
        "Role",
        "You are a friendly and helpful concierge for "
            + venueName
            + ". "
            + "Help guests with information about amenities, services, and availability.");

    List<String> amenityBullets = new ArrayList<>();
    for (Map<String, Object> amenity : amenities) {
      String amenityName = (String) amenity.get("name");
      String desc = (String) amenity.getOrDefault("description", amenityName);
      amenityBullets.add(amenityName + " - " + desc);
    }
    agent.promptAddSection("Available Amenities", "", amenityBullets);

    agent.promptAddSection(
        "Instructions",
        "",
        List.of(
            "Provide detailed information about amenities when asked",
            "Check availability when guests want to use a service",
            "Be warm, welcoming, and helpful",
            "If something is unavailable, suggest alternatives",
            "Provide hours of operation and location details when relevant"));

    // Register amenity lookup tool
    List<String> amenityNames = new ArrayList<>();
    for (Map<String, Object> a : amenities) {
      amenityNames.add((String) a.get("name"));
    }

    Map<String, Object> lookupParams = new LinkedHashMap<>();
    lookupParams.put("type", "object");
    lookupParams.put(
        "properties",
        Map.of(
            "amenity",
            Map.of(
                "type",
                "string",
                "description",
                "Name of the amenity to look up",
                "enum",
                amenityNames)));
    lookupParams.put("required", List.of("amenity"));

    agent.defineTool(
        new ToolDefinition(
            "get_amenity_info",
            "Get detailed information about a venue amenity or service",
            lookupParams,
            (args, raw) -> {
              String requested = (String) args.get("amenity");
              for (Map<String, Object> amenity : amenities) {
                if (requested.equalsIgnoreCase((String) amenity.get("name"))) {
                  StringBuilder sb = new StringBuilder();
                  sb.append(amenity.get("name")).append(": ");
                  sb.append(amenity.getOrDefault("description", ""));
                  if (amenity.containsKey("hours")) {
                    sb.append(" Hours: ").append(amenity.get("hours"));
                  }
                  if (amenity.containsKey("location")) {
                    sb.append(" Location: ").append(amenity.get("location"));
                  }
                  if (amenity.containsKey("price")) {
                    sb.append(" Price: ").append(amenity.get("price"));
                  }
                  return new FunctionResult(sb.toString());
                }
              }
              return new FunctionResult("Amenity not found: " + requested);
            }));

    // Register availability check tool
    Map<String, Object> availParams = new LinkedHashMap<>();
    availParams.put("type", "object");
    availParams.put(
        "properties",
        Map.of(
            "amenity",
                Map.of("type", "string", "description", "Amenity to check", "enum", amenityNames),
            "date", Map.of("type", "string", "description", "Date to check (YYYY-MM-DD)"),
            "time", Map.of("type", "string", "description", "Time to check (HH:MM)")));
    availParams.put("required", List.of("amenity"));

    agent.defineTool(
        new ToolDefinition(
            "check_availability",
            "Check availability of an amenity or service",
            availParams,
            this::checkAvailability));

    // Register directions tool (SWAIG handler -> getDirections)
    Map<String, Object> dirParams = new LinkedHashMap<>();
    dirParams.put("type", "object");
    dirParams.put(
        "properties",
        Map.of(
            "location",
            Map.of(
                "type", "string", "description", "The location or amenity to get directions to")));
    dirParams.put("required", List.of("location"));

    agent.defineTool(
        new ToolDefinition(
            "get_directions",
            "Get directions to a specific location or amenity",
            dirParams,
            this::getDirections));

    agent.updateGlobalData(Map.of("venue_name", venueName));
  }

  /**
   * SWAIG tool handler: check availability of an amenity/service on a date and time. Ported from
   * the Python ConciergeAgent.check_availability -- returns an availability confirmation when the
   * requested amenity is offered, otherwise lists the available amenities.
   */
  public FunctionResult checkAvailability(Map<String, Object> args, Map<String, Object> rawData) {
    String amenityName = (String) args.getOrDefault("amenity", "");
    String date = (String) args.getOrDefault("date", "today");
    String time = (String) args.getOrDefault("time", "now");

    for (Map<String, Object> amenity : amenities) {
      if (amenityName.equalsIgnoreCase((String) amenity.get("name"))) {
        return new FunctionResult(
            amenityName
                + " is available on "
                + date
                + " at "
                + time
                + ". Would you like to make a reservation?");
      }
    }

    List<String> names = new ArrayList<>();
    for (Map<String, Object> amenity : amenities) {
      names.add((String) amenity.get("name"));
    }
    return new FunctionResult(
        "I'm sorry, we don't offer "
            + amenityName
            + " at "
            + venueName
            + ". Our available amenities are: "
            + String.join(", ", names)
            + ".");
  }

  /**
   * SWAIG tool handler: provide directions to a location or amenity. Ported from the Python
   * ConciergeAgent.get_directions -- if the location is a known amenity with a "location" field it
   * returns that; otherwise it points the guest at the front desk.
   */
  public FunctionResult getDirections(Map<String, Object> args, Map<String, Object> rawData) {
    String location = (String) args.getOrDefault("location", "");

    for (Map<String, Object> amenity : amenities) {
      if (location.equalsIgnoreCase((String) amenity.get("name"))
          && amenity.containsKey("location")) {
        Object amenityLocation = amenity.get("location");
        return new FunctionResult(
            "The "
                + location
                + " is located at "
                + amenityLocation
                + ". From the main entrance, follow the signs to "
                + amenityLocation
                + ".");
      }
    }
    return new FunctionResult(
        "I don't have specific directions to "
            + location
            + ". You can ask our staff at the front desk for assistance.");
  }

  /**
   * Register a post-prompt summary callback. Ported from the Python ConciergeAgent.on_summary hook:
   * the callback is invoked with the parsed summary and the raw post-prompt payload after the
   * conversation completes. Wires through to {@link AgentBase#onSummary}.
   *
   * @param handler callback receiving (summary, rawData); {@code null} clears any handler
   * @return this prefab for chaining
   */
  public ConciergeAgent onSummary(
      java.util.function.BiConsumer<Map<String, Object>, Map<String, Object>> handler) {
    this.summaryHandler = handler;
    agent.onSummary(handler);
    return this;
  }

  /** The registered summary callback, or {@code null} if none set. */
  public java.util.function.BiConsumer<Map<String, Object>, Map<String, Object>>
      getSummaryHandler() {
    return summaryHandler;
  }

  public AgentBase getAgent() {
    return agent;
  }

  public void serve() throws Exception {
    agent.serve();
  }

  public void run() throws Exception {
    agent.run();
  }

  public static Map<String, Object> amenity(
      String name, String description, String hours, String location, String price) {
    Map<String, Object> a = new LinkedHashMap<>();
    a.put("name", name);
    a.put("description", description);
    if (hours != null) a.put("hours", hours);
    if (location != null) a.put("location", location);
    if (price != null) a.put("price", price);
    return a;
  }
}
