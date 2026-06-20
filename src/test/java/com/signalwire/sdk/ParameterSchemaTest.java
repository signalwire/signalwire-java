package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ParameterSchema;
import com.signalwire.sdk.swaig.ToolDefinition;
import com.signalwire.sdk.swml.Codec;
import com.signalwire.sdk.swml.RecordFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ParameterSchema} — the Tier-2 flagship typed SWAIG tool-parameter builder.
 *
 * <p>Two real-behavior properties, no mocks:
 *
 * <ol>
 *   <li><b>Byte-identical wire output.</b> For every property kind (string, number, integer,
 *       boolean, enum, array, nested object) — including an <em>enum</em> property fed from a
 *       Tier-1 {@link RecordFormat}/{@link Codec} — the builder's {@code build()} Map is compared
 *       to the equivalent hand-written {@code LinkedHashMap<String,Object>} both structurally
 *       ({@code Map.equals}) AND as serialized JSON (Gson string equality). The JSON comparison is
 *       the load-bearing byte-identity proof: it would fail on any key-order, key-name, or value
 *       difference.
 *   <li><b>Real defineTool → render/invoke.</b> Builder-built params are passed to a real {@link
 *       AgentBase#defineTool}, the agent renders real SWML, and the test digs the parameters back
 *       out of the generated {@code ai.SWAIG.functions[].argument} and asserts they survived the
 *       round-trip; the handler is then actually invoked.
 * </ol>
 */
class ParameterSchemaTest {

  private static final Gson GSON = new Gson();

  // ===================================================================
  // (a) Byte-identical to the hand-written Map<String,Object>.
  // ===================================================================

  @Test
  void everyScalarKindIsByteIdenticalToHandWritten() {
    // Builder form.
    Map<String, Object> built =
        ParameterSchema.builder()
            .string("service", "The service")
            .number("amount", "Dollar amount")
            .integer("count", "How many")
            .bool("urgent", "Is it urgent")
            .required("service", "amount")
            .build();

    // Hand-written form — exact key order the builder emits:
    // type → properties(insertion order) → required.
    Map<String, Object> hand = new LinkedHashMap<>();
    hand.put("type", "object");
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("service", orderedProp("string", "The service"));
    props.put("amount", orderedProp("number", "Dollar amount"));
    props.put("count", orderedProp("integer", "How many"));
    props.put("urgent", orderedProp("boolean", "Is it urgent"));
    hand.put("properties", props);
    hand.put("required", List.of("service", "amount"));

    // Structural equality.
    assertEquals(hand, built);
    // Byte-identity of the serialized JSON (order-sensitive).
    assertEquals(GSON.toJson(hand), GSON.toJson(built));
  }

  @Test
  void enumPropertyFromTier1EnumIsByteIdenticalToHandWritten() {
    // The enum property is the headline integration: RecordFormat.values()
    // must serialize to the exact same enum:[mp3,wav,mp4] a hand-written
    // string list produces.
    Map<String, Object> built =
        ParameterSchema.builder()
            .string("date", "YYYY-MM-DD")
            .enumOf("fmt", RecordFormat.values(), "format")
            .enumOf("codec", Codec.values(), "tap codec")
            .required("date", "fmt")
            .build();

    Map<String, Object> hand = new LinkedHashMap<>();
    hand.put("type", "object");
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("date", orderedProp("string", "YYYY-MM-DD"));
    Map<String, Object> fmt = orderedProp("string", "format");
    // getValue() wire strings, in enum declaration order.
    fmt.put("enum", List.of("mp3", "wav", "mp4"));
    props.put("fmt", fmt);
    Map<String, Object> codec = orderedProp("string", "tap codec");
    codec.put("enum", List.of("PCMU", "PCMA"));
    props.put("codec", codec);
    hand.put("properties", props);
    hand.put("required", List.of("date", "fmt"));

    assertEquals(hand, built);
    assertEquals(GSON.toJson(hand), GSON.toJson(built));

    // And pin the actual wire strings landed (not the enum NAMES "MP3").
    @SuppressWarnings("unchecked")
    List<String> fmtEnum =
        (List<String>) ((Map<String, Object>) props(built).get("fmt")).get("enum");
    assertEquals(List.of("mp3", "wav", "mp4"), fmtEnum);
  }

  @Test
  void enumOfStringSetIsByteIdenticalToHandWritten() {
    // The non-enum closed-set path (e.g. a venue's amenity names) must also
    // match a hand-written enum list exactly.
    Map<String, Object> built =
        ParameterSchema.builder()
            .enumOf("amenity", "Amenity to look up", "pool", "spa", "gym")
            .required("amenity")
            .build();

    Map<String, Object> hand = new LinkedHashMap<>();
    hand.put("type", "object");
    Map<String, Object> props = new LinkedHashMap<>();
    Map<String, Object> amenity = orderedProp("string", "Amenity to look up");
    amenity.put("enum", List.of("pool", "spa", "gym"));
    props.put("amenity", amenity);
    hand.put("properties", props);
    hand.put("required", List.of("amenity"));

    assertEquals(hand, built);
    assertEquals(GSON.toJson(hand), GSON.toJson(built));
  }

  @Test
  void arrayKindsAreByteIdenticalToHandWritten() {
    Map<String, Object> itemObj =
        ParameterSchema.builder().string("name", "Item name").integer("qty", "Quantity").build();

    Map<String, Object> built =
        ParameterSchema.builder()
            .array("tags", "string", "List of tags")
            .arrayOfObjects("items", itemObj, "Line items")
            .build();

    Map<String, Object> hand = new LinkedHashMap<>();
    hand.put("type", "object");
    Map<String, Object> props = new LinkedHashMap<>();

    Map<String, Object> tags = orderedProp("array", "List of tags");
    Map<String, Object> tagItems = new LinkedHashMap<>();
    tagItems.put("type", "string");
    tags.put("items", tagItems);
    props.put("tags", tags);

    Map<String, Object> items = orderedProp("array", "Line items");
    Map<String, Object> lineSchema = new LinkedHashMap<>();
    lineSchema.put("type", "object");
    Map<String, Object> lineProps = new LinkedHashMap<>();
    lineProps.put("name", orderedProp("string", "Item name"));
    lineProps.put("qty", orderedProp("integer", "Quantity"));
    lineSchema.put("properties", lineProps);
    items.put("items", lineSchema);
    props.put("items", items);

    hand.put("properties", props);
    // No required() called → "required" key must be ABSENT.

    assertEquals(hand, built);
    assertEquals(GSON.toJson(hand), GSON.toJson(built));
    assertFalse(built.containsKey("required"), "empty required must be omitted");
  }

  @Test
  void nestedObjectIsByteIdenticalToHandWritten() {
    Map<String, Object> addressSchema =
        ParameterSchema.builder()
            .string("street", "Street")
            .string("zip", "ZIP code")
            .required("street")
            .build();

    Map<String, Object> built =
        ParameterSchema.builder()
            .string("name", "Full name")
            .object("address", addressSchema, "Mailing address")
            .required("name")
            .build();

    Map<String, Object> hand = new LinkedHashMap<>();
    hand.put("type", "object");
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("name", orderedProp("string", "Full name"));
    Map<String, Object> address = new LinkedHashMap<>();
    address.put("type", "object");
    address.put("description", "Mailing address");
    Map<String, Object> addrProps = new LinkedHashMap<>();
    addrProps.put("street", orderedProp("string", "Street"));
    addrProps.put("zip", orderedProp("string", "ZIP code"));
    address.put("properties", addrProps);
    address.put("required", List.of("street"));
    props.put("address", address);
    hand.put("properties", props);
    hand.put("required", List.of("name"));

    assertEquals(hand, built);
    assertEquals(GSON.toJson(hand), GSON.toJson(built));
  }

  @Test
  void defaultAndFormatModifiersAreByteIdenticalToHandWritten() {
    Map<String, Object> built =
        ParameterSchema.builder()
            .string("date", "Booking date")
            .format("date")
            .integer("seats", "Number of seats")
            .defaultValue(2)
            .build();

    Map<String, Object> hand = new LinkedHashMap<>();
    hand.put("type", "object");
    Map<String, Object> props = new LinkedHashMap<>();
    Map<String, Object> date = orderedProp("string", "Booking date");
    date.put("format", "date");
    props.put("date", date);
    Map<String, Object> seats = orderedProp("integer", "Number of seats");
    seats.put("default", 2);
    props.put("seats", seats);
    hand.put("properties", props);

    assertEquals(hand, built);
    assertEquals(GSON.toJson(hand), GSON.toJson(built));
  }

  @Test
  void builtSchemaEqualsTheExactHandWrittenFaqBotForm() {
    // The literal params FAQBotAgent hand-writes today
    // (FAQBotAgent.java:46-51) must be reproducible byte-for-byte.
    Map<String, Object> hand = new LinkedHashMap<>();
    hand.put("type", "object");
    hand.put(
        "properties",
        Map.of(
            "query",
            Map.of("type", "string", "description", "The user's question or keywords to search")));
    hand.put("required", List.of("query"));

    Map<String, Object> built =
        ParameterSchema.builder()
            .string("query", "The user's question or keywords to search")
            .required("query")
            .build();

    // Map.of() is unordered, so the load-bearing check here is structural
    // equality (the JSON-string check in the other tests pins ordering for
    // the deterministic LinkedHashMap side).
    assertEquals(hand, built);
    // Re-serialize through a canonical (sorted) form to also compare bytes
    // independent of Map.of()'s arbitrary order.
    assertEquals(canonicalJson(hand), canonicalJson(built));
  }

  // ===================================================================
  // (b) Real defineTool → render SWML → invoke.
  // ===================================================================

  @Test
  @SuppressWarnings("unchecked")
  void builderParamsFlowThroughDefineToolIntoRenderedSwml() {
    AgentBase agent =
        AgentBase.builder().name("param-schema-test").authUser("u").authPassword("p").build();
    agent.setPromptText("Test");

    Map<String, Object> params =
        ParameterSchema.builder()
            .string("service", "The service to book")
            .enumOf("fmt", RecordFormat.values(), "recording format")
            .integer("seats", "Seat count")
            .defaultValue(1)
            .required("service")
            .build();

    final List<String> seen = new ArrayList<>();
    agent.defineTool(
        new ToolDefinition(
            "book_service",
            "Book a service",
            params,
            (args, raw) -> {
              seen.add((String) args.get("service"));
              return new FunctionResult("Booked " + args.get("service"));
            }));

    // Render REAL SWML and dig out the function's "argument" (where
    // ToolDefinition.toSwaigFunction puts the parameters Map).
    Map<String, Object> swml = agent.renderSwml("http://localhost:3000");
    Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
    List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
    Map<String, Object> ai =
        main.stream()
            .filter(v -> v.containsKey("ai"))
            .findFirst()
            .map(v -> (Map<String, Object>) v.get("ai"))
            .orElseThrow();
    Map<String, Object> swaig = (Map<String, Object>) ai.get("SWAIG");
    List<Map<String, Object>> functions = (List<Map<String, Object>>) swaig.get("functions");
    Map<String, Object> fn =
        functions.stream()
            .filter(f -> "book_service".equals(f.get("function")))
            .findFirst()
            .orElseThrow();

    Map<String, Object> argument = (Map<String, Object>) fn.get("argument");
    assertNotNull(argument, "builder params must render under the function's 'argument'");
    // The rendered argument is byte-identical to what the builder built.
    assertEquals(GSON.toJson(params), GSON.toJson(argument));

    // Content-shaped checks on the rendered schema.
    assertEquals("object", argument.get("type"));
    Map<String, Object> renderedProps = (Map<String, Object>) argument.get("properties");
    assertTrue(renderedProps.containsKey("service"));
    assertEquals(
        "The service to book",
        ((Map<String, Object>) renderedProps.get("service")).get("description"));
    // The Tier-1 enum surfaced as the wire-string enum list.
    assertEquals(
        List.of("mp3", "wav", "mp4"), ((Map<String, Object>) renderedProps.get("fmt")).get("enum"));
    assertEquals(1, ((Map<String, Object>) renderedProps.get("seats")).get("default"));
    assertEquals(List.of("service"), argument.get("required"));

    // And the function is really invocable with an arg the schema describes.
    FunctionResult result =
        agent.onFunctionCall("book_service", Map.of("service", "spa"), Map.of());
    assertEquals("Booked spa", result.getResponse());
    assertEquals(List.of("spa"), seen);
  }

  @Test
  void builderParamsAppearInRenderedSwmlJsonString() {
    AgentBase agent =
        AgentBase.builder().name("param-json-test").authUser("u").authPassword("p").build();
    agent.setPromptText("Test");

    Map<String, Object> params =
        ParameterSchema.builder()
            .string("city", "City name to look up weather for")
            .required("city")
            .build();
    agent.defineTool(
        new ToolDefinition(
            "get_weather", "Look up weather", params, (a, r) -> new FunctionResult("Sunny")));

    // The whole rendered SWML JSON string must literally contain the
    // builder-produced property name + description (proves it serialized).
    String json = agent.renderSwmlJson("http://localhost:3000");
    assertTrue(json.contains("\"city\""), "property name missing from SWML JSON");
    assertTrue(
        json.contains("City name to look up weather for"),
        "property description missing from SWML JSON");
    assertTrue(json.contains("get_weather"), "function name missing from SWML JSON");
  }

  // ===================================================================
  // Helpers.
  // ===================================================================

  /** A property map in the exact key order the builder emits: type, description. */
  private static Map<String, Object> orderedProp(String type, String description) {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("type", type);
    p.put("description", description);
    return p;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> props(Map<String, Object> schema) {
    return (Map<String, Object>) schema.get("properties");
  }

  /**
   * Serialize through a deep key-sorted copy so byte comparison ignores Map.of()'s arbitrary
   * iteration order (used only where the reference side is a Map.of).
   */
  @SuppressWarnings("unchecked")
  private static String canonicalJson(Object o) {
    if (o instanceof Map) {
      Map<String, Object> sorted = new java.util.TreeMap<>();
      for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
        sorted.put(e.getKey(), sortValue(e.getValue()));
      }
      return GSON.toJson(sorted);
    }
    return GSON.toJson(o);
  }

  @SuppressWarnings("unchecked")
  private static Object sortValue(Object v) {
    if (v instanceof Map) {
      Map<String, Object> sorted = new java.util.TreeMap<>();
      for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
        sorted.put(e.getKey(), sortValue(e.getValue()));
      }
      return sorted;
    }
    if (v instanceof List) {
      List<Object> out = new ArrayList<>();
      for (Object item : (List<Object>) v) {
        out.add(sortValue(item));
      }
      return out;
    }
    return v;
  }
}
