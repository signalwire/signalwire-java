package com.signalwire.sdk.swaig;

import com.signalwire.sdk.swml.WireEnum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Typed, fluent builder for the JSON-Schema {@code parameters} blob that a
 * {@link ToolDefinition} (and {@link com.signalwire.sdk.agent.AgentBase#defineTool}) takes.
 *
 * <p>Today the {@code parameters} argument is a hand-written, untyped
 * {@code Map<String,Object>} of nested maps — the JSON Schema spelled out by
 * hand:
 *
 * <pre>{@code
 * Map<String, Object> params = new LinkedHashMap<>();
 * params.put("type", "object");
 * params.put("properties", Map.of(
 *         "service", Map.of("type", "string", "description", "The service"),
 *         "date",    Map.of("type", "string", "description", "YYYY-MM-DD")));
 * params.put("required", List.of("service", "date"));
 * }</pre>
 *
 * <p>That is easy to typo (a wrong {@code "type"} value, a misspelled key, a
 * required name that doesn't match a property) and only fails at runtime on the
 * server. This builder constructs the <strong>exact same wire shape</strong>
 * type-safely:
 *
 * <pre>{@code
 * Map<String, Object> params = ParameterSchema.builder()
 *         .string("service", "The service")
 *         .string("date", "YYYY-MM-DD")
 *         .enumOf("fmt", RecordFormat.values(), "format")   // Tier-1 enum → enum:[mp3,wav,mp4]
 *         .required("service", "date")
 *         .build();
 *
 * agent.defineTool(new ToolDefinition("book", "Book a service", params, handler));
 * }</pre>
 *
 * <p><strong>This is a typed convenience over the SAME output, not a new
 * format.</strong> {@link #build()} returns a plain {@code Map<String,Object>}
 * that is byte-for-byte identical to the hand-written form above and is used
 * exactly the same way as the {@code parameters} argument — the untyped Map
 * path keeps working unchanged. The builder is purely additive.
 *
 * <p>Supported property kinds: {@code string}, {@code number}, {@code integer},
 * {@code boolean}, {@code enum} (a closed set — either the Tier-1
 * {@link WireEnum} enums via {@link Builder#enumOf(String, WireEnum[], String)},
 * each contributing its {@link WireEnum#getValue()}, or a bare string set),
 * {@code array} (of a kind), and {@code object} (a nested schema built with
 * another {@code ParameterSchema}). Every property carries a {@code description}
 * and may also set {@code default}, {@code format}, or {@code enum}.
 *
 * <p>Insertion order is preserved: {@code properties} keeps the order the
 * properties were declared, each property map orders its keys
 * {@code type → description → enum → default → format → items → properties},
 * and {@code required} keeps declaration order (de-duplicated). The
 * {@code required} key is omitted entirely when no property is marked required,
 * matching the hand-written convention of not emitting an empty list.
 */
public final class ParameterSchema {

    private ParameterSchema() {
        // Static factory only — instances are the built Map, not this class.
    }

    /**
     * Start a new schema builder.
     *
     * @return a fresh {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder accumulating properties and required names, then emitting
     * the JSON-Schema {@code parameters} Map via {@link #build()}.
     */
    public static final class Builder {

        // Insertion-ordered so the emitted "properties" map matches declaration order.
        private final Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
        // Insertion-ordered + de-duplicated required names.
        private final LinkedHashSet<String> required = new LinkedHashSet<>();

        private Builder() {
        }

        // ---------------------------------------------------------------
        // Scalar property kinds.
        // ---------------------------------------------------------------

        /**
         * Add a {@code string} property.
         *
         * @param name        property name (the JSON object key).
         * @param description LLM-facing description of the argument.
         * @return this builder.
         */
        public Builder string(String name, String description) {
            return property(name, "string", description, null);
        }

        /**
         * Add a {@code string} property with a JSON-Schema {@code format} hint
         * (e.g. {@code "date"}, {@code "email"}, {@code "uri"}).
         *
         * @param name        property name.
         * @param description LLM-facing description.
         * @param format      the JSON-Schema {@code format} value.
         * @return this builder.
         */
        public Builder string(String name, String description, String format) {
            Map<String, Object> prop = newProp("string", description);
            if (format != null) {
                prop.put("format", format);
            }
            properties.put(name, prop);
            return this;
        }

        /**
         * Add a {@code number} (floating-point) property.
         *
         * @param name        property name.
         * @param description LLM-facing description.
         * @return this builder.
         */
        public Builder number(String name, String description) {
            return property(name, "number", description, null);
        }

        /**
         * Add an {@code integer} property.
         *
         * @param name        property name.
         * @param description LLM-facing description.
         * @return this builder.
         */
        public Builder integer(String name, String description) {
            return property(name, "integer", description, null);
        }

        /**
         * Add a {@code boolean} property.
         *
         * @param name        property name.
         * @param description LLM-facing description.
         * @return this builder.
         */
        public Builder bool(String name, String description) {
            return property(name, "boolean", description, null);
        }

        // ---------------------------------------------------------------
        // Enum (closed set).
        // ---------------------------------------------------------------

        /**
         * Add a closed-set {@code string} property whose allowed values come
         * from a Tier-1 {@link WireEnum} array — typically {@code
         * RecordFormat.values()} / {@code Codec.values()} etc. Each constant
         * contributes its {@link WireEnum#getValue()} wire string, producing a
         * JSON-Schema {@code enum:[...]} of those exact strings (so it is
         * byte-identical to hand-writing the same string list).
         *
         * @param name        property name.
         * @param values      the enum constants (e.g. {@code RecordFormat.values()}).
         * @param description LLM-facing description.
         * @return this builder.
         */
        public Builder enumOf(String name, WireEnum[] values, String description) {
            List<String> wire = new ArrayList<>(values.length);
            for (WireEnum v : values) {
                wire.add(v.getValue());
            }
            return enumOf(name, wire, description);
        }

        /**
         * Add a closed-set {@code string} property with an explicit list of
         * allowed string values (for closed sets that are not modelled as a
         * Tier-1 enum — e.g. a venue's amenity names). Produces a JSON-Schema
         * {@code enum:[...]} in the given order.
         *
         * @param name        property name.
         * @param values      the allowed values.
         * @param description LLM-facing description.
         * @return this builder.
         */
        public Builder enumOf(String name, Collection<String> values, String description) {
            Map<String, Object> prop = newProp("string", description);
            prop.put("enum", new ArrayList<>(values));
            properties.put(name, prop);
            return this;
        }

        /**
         * Convenience varargs form of {@link #enumOf(String, Collection, String)}
         * where the description comes first so the trailing varargs read as the
         * value set.
         *
         * @param name        property name.
         * @param description LLM-facing description.
         * @param values      the allowed values.
         * @return this builder.
         */
        public Builder enumOf(String name, String description, String... values) {
            return enumOf(name, Arrays.asList(values), description);
        }

        // ---------------------------------------------------------------
        // Array.
        // ---------------------------------------------------------------

        /**
         * Add an {@code array} property whose items are a scalar JSON-Schema
         * kind ({@code "string"}, {@code "number"}, {@code "integer"},
         * {@code "boolean"}). Produces {@code {"type":"array","description":…,
         * "items":{"type":itemType}}}.
         *
         * @param name        property name.
         * @param itemType    the scalar item kind.
         * @param description LLM-facing description.
         * @return this builder.
         */
        public Builder array(String name, String itemType, String description) {
            Map<String, Object> prop = newProp("array", description);
            Map<String, Object> items = new LinkedHashMap<>();
            items.put("type", itemType);
            prop.put("items", items);
            properties.put(name, prop);
            return this;
        }

        /**
         * Add an {@code array} property whose items are a nested object schema
         * built with another {@link ParameterSchema}. Produces
         * {@code {"type":"array","description":…,"items":{<nested schema>}}}.
         *
         * @param name        property name.
         * @param itemSchema  the built schema for each array element (from
         *                    {@link Builder#build()}).
         * @param description LLM-facing description.
         * @return this builder.
         */
        public Builder arrayOfObjects(String name, Map<String, Object> itemSchema, String description) {
            Map<String, Object> prop = newProp("array", description);
            prop.put("items", itemSchema);
            properties.put(name, prop);
            return this;
        }

        // ---------------------------------------------------------------
        // Nested object.
        // ---------------------------------------------------------------

        /**
         * Add a nested {@code object} property whose schema is built with
         * another {@link ParameterSchema}. The nested schema's {@code type},
         * {@code properties} (and {@code required}, if any) are merged in
         * alongside this property's {@code description}, producing
         * {@code {"type":"object","description":…,"properties":{…}}}.
         *
         * @param name        property name.
         * @param objectSchema the built nested schema (from {@link Builder#build()}).
         * @param description LLM-facing description.
         * @return this builder.
         */
        @SuppressWarnings("unchecked")
        public Builder object(String name, Map<String, Object> objectSchema, String description) {
            // Re-order so "type" → "description" lead, then the nested schema's
            // own keys (properties, required) follow — keeping the same key
            // order a hand-written nested object would use.
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", "object");
            prop.put("description", description);
            Object nestedProps = objectSchema.get("properties");
            if (nestedProps != null) {
                prop.put("properties", nestedProps);
            }
            Object nestedRequired = objectSchema.get("required");
            if (nestedRequired != null) {
                prop.put("required", nestedRequired);
            }
            properties.put(name, prop);
            return this;
        }

        // ---------------------------------------------------------------
        // Per-property modifiers (applied to the most-recently-added property).
        // ---------------------------------------------------------------

        /**
         * Set a JSON-Schema {@code default} on the most-recently-added
         * property. The value is emitted verbatim into the property map.
         *
         * @param value the default value.
         * @return this builder.
         * @throws IllegalStateException if no property has been added yet.
         */
        public Builder defaultValue(Object value) {
            lastProperty().put("default", value);
            return this;
        }

        /**
         * Set a JSON-Schema {@code format} on the most-recently-added property.
         *
         * @param format the {@code format} value.
         * @return this builder.
         * @throws IllegalStateException if no property has been added yet.
         */
        public Builder format(String format) {
            lastProperty().put("format", format);
            return this;
        }

        // ---------------------------------------------------------------
        // Required.
        // ---------------------------------------------------------------

        /**
         * Mark one or more properties as required. Names are kept in
         * declaration order and de-duplicated; calling this multiple times
         * accumulates. If no name is ever marked required, the built schema
         * omits the {@code required} key entirely.
         *
         * @param names property names to require.
         * @return this builder.
         */
        public Builder required(String... names) {
            for (String n : names) {
                required.add(n);
            }
            return this;
        }

        // ---------------------------------------------------------------
        // Build.
        // ---------------------------------------------------------------

        /**
         * Build the JSON-Schema {@code parameters} Map. The result is a
         * {@code LinkedHashMap} ordered {@code type → properties → required}
         * (with {@code required} omitted when empty) — byte-for-byte identical
         * to the hand-written nested-map form. Each call returns a fresh,
         * independent Map; the builder may be reused.
         *
         * @return the parameters Map, ready to pass to
         *         {@link ToolDefinition} / {@code defineTool}.
         */
        public Map<String, Object> build() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("type", "object");
            Map<String, Object> props = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> e : properties.entrySet()) {
                // Defensive copy so a later mutation of the builder doesn't
                // leak into an already-built Map.
                props.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
            }
            out.put("properties", props);
            if (!required.isEmpty()) {
                out.put("required", new ArrayList<>(required));
            }
            return out;
        }

        // ---------------------------------------------------------------
        // Internals.
        // ---------------------------------------------------------------

        private Builder property(String name, String type, String description, String format) {
            Map<String, Object> prop = newProp(type, description);
            if (format != null) {
                prop.put("format", format);
            }
            properties.put(name, prop);
            return this;
        }

        private static Map<String, Object> newProp(String type, String description) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", type);
            if (description != null) {
                prop.put("description", description);
            }
            return prop;
        }

        private Map<String, Object> lastProperty() {
            if (properties.isEmpty()) {
                throw new IllegalStateException(
                        "no property to modify — call a property kind (string/number/…) first");
            }
            Map<String, Object> last = null;
            for (Map<String, Object> p : properties.values()) {
                last = p;
            }
            return last;
        }
    }
}
