/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.signalwire.sdk.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * SchemaUtils — Java port of {@code signalwire.utils.schema_utils.SchemaUtils}.
 *
 * <p>Loads the SWML JSON Schema, extracts verb metadata, and validates either a
 * single verb config or a complete SWML document.</p>
 *
 * <p>Construction rules mirror Python:</p>
 * <ul>
 *   <li>Pass {@code schemaPath=null} to use the embedded {@code schema.json}.</li>
 *   <li>{@code schemaValidation=false} disables validation (returns valid=true
 *       for every {@code validateVerb} call).</li>
 *   <li>The env var {@code SWML_SKIP_SCHEMA_VALIDATION=1/true/yes} also disables
 *       validation regardless of the constructor argument.</li>
 * </ul>
 *
 * <p>The Java port currently ships only the lightweight validator (verb existence
 * + required-property check). Full JSON Schema validation can be wired in by
 * extending {@link #initFullValidator()}. The lightweight contract matches
 * Python's {@code _validate_verb_lightweight()} exactly.</p>
 */
public class SchemaUtils {

    private static final Logger log = Logger.getLogger(SchemaUtils.class);

    private final JsonObject schema;
    private final String schemaPath;
    private final boolean validationEnabled;
    private final Map<String, VerbInfo> verbs;
    private Object fullValidator; // reserved for future JSON Schema validator wiring

    /** A verb extracted from the schema. */
    public static final class VerbInfo {
        public final String name;
        public final String schemaName;
        public final JsonObject definition;

        VerbInfo(String name, String schemaName, JsonObject definition) {
            this.name = name;
            this.schemaName = schemaName;
            this.definition = definition;
        }
    }

    /**
     * Construct a SchemaUtils.
     * Mirrors Python's {@code SchemaUtils(schema_path=None, schema_validation=True)}.
     *
     * @param schemaPath optional path to a schema.json file; pass null to use the
     *                   embedded resource bundled with the SDK jar.
     * @param schemaValidation enables/disables schema validation. Honors
     *                         {@code SWML_SKIP_SCHEMA_VALIDATION=1} env override.
     */
    public SchemaUtils(String schemaPath, boolean schemaValidation) {
        boolean envSkip = isEnvBoolish(System.getenv("SWML_SKIP_SCHEMA_VALIDATION"));
        this.schemaPath = schemaPath;
        this.validationEnabled = schemaValidation && !envSkip;
        this.schema = loadSchema();
        this.verbs = new LinkedHashMap<>();
        extractVerbs();
        if (this.validationEnabled && this.schema != null) {
            initFullValidator();
        }
    }

    private static boolean isEnvBoolish(String v) {
        if (v == null) return false;
        String s = v.trim().toLowerCase();
        return s.equals("1") || s.equals("true") || s.equals("yes");
    }

    /**
     * Read and parse the JSON Schema.
     * Mirrors Python's {@code load_schema()}.
     */
    public JsonObject loadSchema() {
        try {
            InputStream is;
            if (schemaPath != null && !schemaPath.isEmpty()) {
                is = Files.newInputStream(Path.of(schemaPath));
            } else {
                is = SchemaUtils.class.getClassLoader().getResourceAsStream("schema.json");
                if (is == null) {
                    log.warn("schema.json not found in classpath resources");
                    return new JsonObject();
                }
            }
            try (var reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                Gson gson = new Gson();
                JsonElement root = gson.fromJson(reader, JsonElement.class);
                if (root != null && root.isJsonObject()) {
                    return root.getAsJsonObject();
                }
                return new JsonObject();
            }
        } catch (IOException e) {
            log.warn("failed to load schema: %s", e.getMessage());
            return new JsonObject();
        }
    }

    private void extractVerbs() {
        if (schema == null || !schema.has("$defs")) return;
        JsonObject defs = schema.getAsJsonObject("$defs");
        if (!defs.has("SWMLMethod")) return;
        JsonObject swmlMethod = defs.getAsJsonObject("SWMLMethod");
        if (!swmlMethod.has("anyOf")) return;
        JsonArray anyOf = swmlMethod.getAsJsonArray("anyOf");
        for (JsonElement entry : anyOf) {
            if (!entry.isJsonObject()) continue;
            JsonObject ref = entry.getAsJsonObject();
            if (!ref.has("$ref")) continue;
            String refStr = ref.get("$ref").getAsString();
            final String prefix = "#/$defs/";
            if (!refStr.startsWith(prefix)) continue;
            String schemaName = refStr.substring(prefix.length());
            if (!defs.has(schemaName)) continue;
            JsonObject defn = defs.getAsJsonObject(schemaName);
            if (!defn.has("properties")) continue;
            JsonObject props = defn.getAsJsonObject("properties");
            if (props.size() == 0) continue;
            String actualVerb = props.keySet().iterator().next();
            verbs.put(actualVerb, new VerbInfo(actualVerb, schemaName, defn));
        }
    }

    /**
     * Initialize the full JSON Schema validator. The Java port currently leaves
     * this empty — extend by wiring {@code com.networknt:json-schema-validator}
     * or similar here.
     */
    private void initFullValidator() {
        // Reserved for full-validator integration.
        this.fullValidator = null;
    }

    /**
     * Whether full JSON Schema validation is wired up.
     * Mirrors Python's {@code full_validation_available} property.
     */
    public boolean isFullValidationAvailable() {
        return this.fullValidator != null;
    }

    /**
     * Sorted list of all known verb names.
     * Mirrors Python's {@code get_all_verb_names()}.
     */
    public List<String> getAllVerbNames() {
        return new ArrayList<>(new TreeSet<>(verbs.keySet()));
    }

    /**
     * The {@code properties[verb_name]} block for a verb, or an empty map when
     * the verb is unknown.
     * Mirrors Python's {@code get_verb_properties(verb_name)}.
     */
    public Map<String, Object> getVerbProperties(String verbName) {
        VerbInfo v = verbs.get(verbName);
        if (v == null) return Collections.emptyMap();
        if (!v.definition.has("properties")) return Collections.emptyMap();
        JsonObject props = v.definition.getAsJsonObject("properties");
        if (!props.has(verbName)) return Collections.emptyMap();
        if (!props.get(verbName).isJsonObject()) return Collections.emptyMap();
        return jsonObjectToMap(props.getAsJsonObject(verbName));
    }

    /**
     * The {@code required} list for a verb, or an empty list when the verb is
     * unknown or has no required properties.
     * Mirrors Python's {@code get_verb_required_properties(verb_name)}.
     */
    public List<String> getVerbRequiredProperties(String verbName) {
        VerbInfo v = verbs.get(verbName);
        if (v == null) return Collections.emptyList();
        if (!v.definition.has("properties")) return Collections.emptyList();
        JsonObject outerProps = v.definition.getAsJsonObject("properties");
        if (!outerProps.has(verbName)) return Collections.emptyList();
        if (!outerProps.get(verbName).isJsonObject()) return Collections.emptyList();
        JsonObject inner = outerProps.getAsJsonObject(verbName);
        if (!inner.has("required")) return Collections.emptyList();
        if (!inner.get("required").isJsonArray()) return Collections.emptyList();
        JsonArray req = inner.getAsJsonArray("required");
        List<String> out = new ArrayList<>(req.size());
        for (JsonElement e : req) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                out.add(e.getAsString());
            }
        }
        return out;
    }

    /**
     * Parameter-definition block used by code-gen tooling.
     * Mirrors Python's {@code get_verb_parameters(verb_name)}.
     */
    public Map<String, Object> getVerbParameters(String verbName) {
        Map<String, Object> inner = getVerbProperties(verbName);
        Object props = inner.get("properties");
        if (props instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) props;
            return typed;
        }
        return Collections.emptyMap();
    }

    /**
     * Validate a verb config against the schema.
     * Mirrors Python's {@code validate_verb(verb_name, verb_config)}.
     *
     * @return ({@code valid}, {@code errors}) entry; mirrors Python's
     *         {@code Tuple[bool, List[str]]} return.
     */
    public Map.Entry<Boolean, List<String>> validateVerb(String verbName, Map<String, Object> verbConfig) {
        if (!validationEnabled) {
            return new AbstractMap.SimpleImmutableEntry<>(true, Collections.emptyList());
        }
        if (!verbs.containsKey(verbName)) {
            return new AbstractMap.SimpleImmutableEntry<>(false,
                    Collections.singletonList("Unknown verb: " + verbName));
        }
        if (fullValidator != null) {
            return validateVerbFull(verbName, verbConfig);
        }
        return validateVerbLightweight(verbName, verbConfig);
    }

    private Map.Entry<Boolean, List<String>> validateVerbFull(String verbName, Map<String, Object> verbConfig) {
        // Reserved for full-validator wiring; falls back to lightweight check.
        return validateVerbLightweight(verbName, verbConfig);
    }

    private Map.Entry<Boolean, List<String>> validateVerbLightweight(String verbName, Map<String, Object> verbConfig) {
        List<String> errors = new ArrayList<>();
        for (String prop : getVerbRequiredProperties(verbName)) {
            if (!verbConfig.containsKey(prop)) {
                errors.add("Missing required property '" + prop + "' for verb '" + verbName + "'");
            }
        }
        return new AbstractMap.SimpleImmutableEntry<>(errors.isEmpty(), errors);
    }

    /**
     * Validate a complete SWML document.
     * Mirrors Python's {@code validate_document(document)}. Returns
     * {@code (false, ["Schema validator not initialized"])} when no full
     * validator is wired in — same contract as Python.
     */
    public Map.Entry<Boolean, List<String>> validateDocument(Map<String, Object> document) {
        if (fullValidator == null) {
            return new AbstractMap.SimpleImmutableEntry<>(false,
                    Collections.singletonList("Schema validator not initialized"));
        }
        // Reserved for full-validator wiring.
        return new AbstractMap.SimpleImmutableEntry<>(true, Collections.emptyList());
    }

    /**
     * Generate a Python-style method signature string for a verb.
     * Mirrors Python's {@code generate_method_signature(verb_name)}.
     */
    public String generateMethodSignature(String verbName) {
        Map<String, Object> params = getVerbParameters(verbName);
        Set<String> required = new LinkedHashSet<>(getVerbRequiredProperties(verbName));
        List<String> parts = new ArrayList<>();
        parts.add("self");
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        for (String name : keys) {
            String t = pythonTypeAnnotation(params.get(name));
            if (required.contains(name)) {
                parts.add(name + ": " + t);
            } else {
                parts.add(name + ": Optional[" + t + "] = None");
            }
        }
        parts.add("**kwargs");
        StringBuilder doc = new StringBuilder();
        doc.append("\"\"\"\n        Add the ").append(verbName).append(" verb to the current document\n        \n");
        for (String name : keys) {
            String desc = "";
            Object def = params.get(name);
            if (def instanceof Map<?, ?>) {
                Object d = ((Map<?, ?>) def).get("description");
                if (d != null) {
                    desc = d.toString().replace('\n', ' ').trim();
                }
            }
            doc.append("        Args:\n            ").append(name).append(": ").append(desc).append("\n");
        }
        doc.append("        \n        Returns:\n            True if the verb was added successfully, False otherwise\n        \"\"\"\n");
        return "def " + verbName + "(" + String.join(", ", parts) + ") -> bool:\n" + doc;
    }

    /**
     * Generate a Python-style method body string for a verb.
     * Mirrors Python's {@code generate_method_body(verb_name)}.
     */
    public String generateMethodBody(String verbName) {
        Map<String, Object> params = getVerbParameters(verbName);
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        List<String> lines = new ArrayList<>();
        lines.add("        # Prepare the configuration");
        lines.add("        config = {}");
        for (String name : keys) {
            lines.add("        if " + name + " is not None:");
            lines.add("            config['" + name + "'] = " + name);
        }
        lines.add("        # Add any additional parameters from kwargs");
        lines.add("        for key, value in kwargs.items():");
        lines.add("            if value is not None:");
        lines.add("                config[key] = value");
        lines.add("");
        lines.add("        # Add the " + verbName + " verb");
        lines.add("        return self.add_verb('" + verbName + "', config)");
        return String.join("\n", lines);
    }

    private static String pythonTypeAnnotation(Object def) {
        if (!(def instanceof Map<?, ?> d)) return "Any";
        Object t = d.get("type");
        if ("string".equals(t)) return "str";
        if ("integer".equals(t)) return "int";
        if ("number".equals(t)) return "float";
        if ("boolean".equals(t)) return "bool";
        if ("array".equals(t)) {
            String item = "Any";
            Object items = d.get("items");
            if (items != null) {
                item = pythonTypeAnnotation(items);
            }
            return "List[" + item + "]";
        }
        if ("object".equals(t)) return "Dict[str, Any]";
        if (d.containsKey("anyOf") || d.containsKey("oneOf") || d.containsKey("$ref")) {
            return "Any";
        }
        return "Any";
    }

    private static Map<String, Object> jsonObjectToMap(JsonObject obj) {
        Gson gson = new Gson();
        @SuppressWarnings("unchecked")
        Map<String, Object> m = gson.fromJson(obj, Map.class);
        return m == null ? Collections.emptyMap() : m;
    }
}
