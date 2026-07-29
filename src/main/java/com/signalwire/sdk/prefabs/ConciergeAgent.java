package com.signalwire.sdk.prefabs;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

/** Pre-built agent for venue concierge with amenity info and availability checking. */
public class ConciergeAgent {

  /** Default operating hours when the caller supplies none — the reference's fallback. */
  private static final Map<String, String> DEFAULT_HOURS = Map.of("default", "9 AM - 5 PM");

  private final AgentBase agent;
  private final String venueName;
  private final List<Map<String, Object>> amenities;
  private final List<String> services;
  private final Map<String, String> hoursOfOperation;
  private final List<String> specialInstructions;
  private java.util.function.BiConsumer<Map<String, Object>, Map<String, Object>> summaryHandler;

  public ConciergeAgent(String name, String venueName, List<Map<String, Object>> amenities) {
    this(name, venueName, amenities, "/", 3000);
  }

  public ConciergeAgent(
      String name, String venueName, List<Map<String, Object>> amenities, String route, int port) {
    this(name, venueName, amenities, route, port, null, null, null);
  }

  /**
   * Full construction contract, mirroring the reference {@code ConciergeAgent(venue_name, services,
   * amenities, hours_of_operation, special_instructions, ..., name, route)}.
   *
   * @param name agent name
   * @param venueName name of the venue or business (the {@code venue_name} param)
   * @param amenities amenities with details (the {@code amenities} param)
   * @param route HTTP route for this agent
   * @param port HTTP port for this agent
   * @param services services offered (the {@code services} param); empty when {@code null}
   * @param hoursOfOperation operating hours (the {@code hours_of_operation} param); defaults to
   *     {@code {"default": "9 AM - 5 PM"}} when {@code null}, as the reference does
   * @param specialInstructions extra instruction bullets appended to the Instructions section (the
   *     {@code special_instructions} param); empty when {@code null}
   */
  public ConciergeAgent(
      String name,
      String venueName,
      List<Map<String, Object>> amenities,
      String route,
      int port,
      List<String> services,
      Map<String, String> hoursOfOperation,
      List<String> specialInstructions) {
    this.venueName = venueName;
    this.amenities = amenities;
    this.services = services != null ? List.copyOf(services) : List.of();
    this.hoursOfOperation =
        hoursOfOperation != null && !hoursOfOperation.isEmpty()
            ? Map.copyOf(hoursOfOperation)
            : DEFAULT_HOURS;
    this.specialInstructions =
        specialInstructions != null ? List.copyOf(specialInstructions) : List.of();
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

    if (!this.services.isEmpty()) {
      agent.promptAddSection(
          "Available Services",
          "The following services are available: " + String.join(", ", this.services));
    }

    List<String> instructionBullets =
        new ArrayList<>(
            List.of(
                "Provide detailed information about amenities when asked",
                "Check availability when guests want to use a service",
                "Be warm, welcoming, and helpful",
                "If something is unavailable, suggest alternatives",
                "Provide hours of operation and location details when relevant"));
    // Caller-supplied instructions extend the built-in list, as the reference does.
    instructionBullets.addAll(this.specialInstructions);
    agent.promptAddSection("Instructions", "", instructionBullets);

    List<String> hourLines = new ArrayList<>();
    for (Map.Entry<String, String> e : this.hoursOfOperation.entrySet()) {
      hourLines.add(e.getKey() + ": " + e.getValue());
    }
    agent.promptAddSection("Hours of Operation", String.join("\n", hourLines));

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

  /**
   * The underlying agent this prefab configured. Use it to add tools, prompt sections, or skills
   * beyond what the prefab sets up.
   *
   * @return the wrapped agent.
   */
  public AgentBase getAgent() {
    return agent;
  }

  // Read side of the construction params. The reference stores each as public state
  // (concierge.py:75-79) so a caller can read back what it configured.

  /** The venue or business name (the {@code venue_name} construction param). */
  public String getVenueName() {
    return venueName;
  }

  /** The configured amenities (the {@code amenities} construction param). */
  public List<Map<String, Object>> getAmenities() {
    return amenities;
  }

  /** The services offered (the {@code services} construction param). */
  public List<String> getServices() {
    return services;
  }

  /** The operating hours (the {@code hours_of_operation} construction param). */
  public Map<String, String> getHoursOfOperation() {
    return hoursOfOperation;
  }

  /** The extra instruction bullets (the {@code special_instructions} construction param). */
  public List<String> getSpecialInstructions() {
    return specialInstructions;
  }

  /**
   * Start the agent's HTTP server and serve until stopped.
   *
   * @throws Exception if the server cannot be started.
   */
  public void serve() throws Exception {
    agent.serve();
  }

  /**
   * Start the agent's HTTP server. Equivalent to {@link #serve()}.
   *
   * @throws Exception if the server cannot be started.
   */
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
