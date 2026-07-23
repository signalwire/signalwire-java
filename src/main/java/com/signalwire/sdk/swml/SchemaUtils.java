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
 * <p>Loads the SWML JSON Schema, extracts verb metadata, and validates either a single verb config
 * or a complete SWML document.
 *
 * <p>Construction rules mirror Python:
 *
 * <ul>
 *   <li>Pass {@code schemaPath=null} to use the embedded {@code schema.json}.
 *   <li>{@code schemaValidation=false} disables validation (returns valid=true for every {@code
 *       validateVerb} call).
 *   <li>The env var {@code SWML_SKIP_SCHEMA_VALIDATION=1/true/yes} also disables validation
 *       regardless of the constructor argument.
 * </ul>
 *
 * <p>The Java port currently ships only the lightweight validator (verb existence +
 * required-property check). Full JSON Schema validation can be wired in by extending {@link
 * #initFullValidator()}. The lightweight contract matches Python's {@code
 * _validate_verb_lightweight()} exactly.
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
   * Construct a SchemaUtils. Mirrors Python's {@code SchemaUtils(schema_path=None,
   * schema_validation=True)}.
   *
   * @param schemaPath optional path to a schema.json file; pass null to use the embedded resource
   *     bundled with the SDK jar.
   * @param schemaValidation enables/disables schema validation. Honors {@code
   *     SWML_SKIP_SCHEMA_VALIDATION=1} env override.
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
    String s = v.trim().toLowerCase(java.util.Locale.ROOT);
    return "1".equals(s) || "true".equals(s) || "yes".equals(s);
  }

  /** Read and parse the JSON Schema. Mirrors Python's {@code load_schema()}. */
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
   * Initialize the full JSON Schema validator. The Java port currently leaves this empty — extend
   * by wiring {@code com.networknt:json-schema-validator} or similar here.
   */
  private void initFullValidator() {
    // The focused draft-2020-12 subset validator (validateVerbFull) operates
    // directly on the loaded schema — no external validator object is needed.
    // Mark full validation available whenever the schema carries verb defs so
    // validateVerb() routes closed-key / type / required checks through it
    // (falls back to the lightweight required-only check when the schema is
    // empty, e.g. a mocked/partial schema).
    this.fullValidator = (schema != null && !verbs.isEmpty()) ? this : null;
  }

  /**
   * Whether full JSON Schema validation is wired up. Mirrors Python's {@code
   * full_validation_available} property.
   */
  public boolean isFullValidationAvailable() {
    return this.fullValidator != null;
  }

  /** Sorted list of all known verb names. Mirrors Python's {@code get_all_verb_names()}. */
  public List<String> getAllVerbNames() {
    return new ArrayList<>(new TreeSet<>(verbs.keySet()));
  }

  /**
   * The {@code properties[verb_name]} block for a verb, or an empty map when the verb is unknown.
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
   * The {@code required} list for a verb, or an empty list when the verb is unknown or has no
   * required properties. Mirrors Python's {@code get_verb_required_properties(verb_name)}.
   */
  public List<String> getVerbRequiredProperties(String verbName) {
    VerbInfo v = verbs.get(verbName);
    if (v == null) return new ArrayList<>();
    if (!v.definition.has("properties")) return new ArrayList<>();
    JsonObject outerProps = v.definition.getAsJsonObject("properties");
    if (!outerProps.has(verbName)) return new ArrayList<>();
    if (!outerProps.get(verbName).isJsonObject()) return new ArrayList<>();
    JsonObject inner = outerProps.getAsJsonObject(verbName);
    if (!inner.has("required")) return new ArrayList<>();
    if (!inner.get("required").isJsonArray()) return new ArrayList<>();
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
   * Parameter-definition block used by code-gen tooling. Mirrors Python's {@code
   * get_verb_parameters(verb_name)}.
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
   * Validate a verb config against the schema. Mirrors Python's {@code validate_verb(verb_name,
   * verb_config)}.
   *
   * @return ({@code valid}, {@code errors}) entry; mirrors Python's {@code Tuple[bool, List[str]]}
   *     return.
   */
  public Map.Entry<Boolean, List<String>> validateVerb(
      String verbName, Map<String, Object> verbConfig) {
    if (!validationEnabled) {
      return new AbstractMap.SimpleImmutableEntry<>(true, Collections.emptyList());
    }
    if (!verbs.containsKey(verbName)) {
      return new AbstractMap.SimpleImmutableEntry<>(
          false, Collections.singletonList("Unknown verb: " + verbName));
    }
    if (fullValidator != null) {
      return validateVerbFull(verbName, verbConfig);
    }
    return validateVerbLightweight(verbName, verbConfig);
  }

  private Map.Entry<Boolean, List<String>> validateVerbFull(
      String verbName, Map<String, Object> verbConfig) {
    // Full structural validation against the vendored SWML JSON Schema
    // (draft 2020-12). Python delegates this to jsonschema-rs by wrapping the
    // verb in a minimal document; the Java port ships a focused validator over
    // the exact keyword subset the SWML schema uses (type, properties,
    // required, unevaluatedProperties/additionalProperties closed-key checks,
    // $ref, oneOf/anyOf/allOf/not, const, enum, pattern, if/then/else, items).
    // This enforces the strict-render contract — unknown/misspelled keys on a
    // closed verb, wrong-typed config, and missing required keys all raise —
    // matching Python's add_verb choke point.
    VerbInfo v = verbs.get(verbName);
    if (v == null) {
      return new AbstractMap.SimpleImmutableEntry<>(
          false, Collections.singletonList("Unknown verb: " + verbName));
    }
    // The inner verb schema (definition.properties[verbName]) is what
    // constrains the config object (e.g. Answer.properties.answer). Validate
    // the config directly against it. If the shape is missing, fall back to the
    // lightweight required-only check rather than over-raise.
    if (!v.definition.has("properties")) {
      return validateVerbLightweight(verbName, verbConfig);
    }
    JsonObject outerProps = v.definition.getAsJsonObject("properties");
    if (!outerProps.has(verbName) || !outerProps.get(verbName).isJsonObject()) {
      return validateVerbLightweight(verbName, verbConfig);
    }
    JsonObject innerSchema = outerProps.getAsJsonObject(verbName);
    JsonElement configEl = new Gson().toJsonTree(verbConfig);
    List<String> errors = new ArrayList<>();
    // The ai verb is validated TOP-LEVEL-KEYS ONLY (reject unknown/misspelled
    // top-level keys + require `prompt`; ai.params stays open). Its deep
    // sub-schema (prompt.pom / SWAIG function shapes) legitimately renders
    // shapes the bundled JSON-schema does not fully accept — e.g. an empty
    // prompt.pom [] for a promptless agent, or SWAIG defaults/webhook fields —
    // so full-deep-validating the ai verb would FALSE-REJECT valid documents.
    // The reference (jsonschema-rs on the same schema) does not surface these as
    // errors at add_verb for the ai verb in practice; the strict-render contract
    // for ai is only the top-level-key check. Match that here.
    JsonObject aiObject = aiTopLevelSchema(verbName, innerSchema);
    if (aiObject != null) {
      validateTopLevelOnly(aiObject, configEl, errors);
    } else {
      validateAgainst(innerSchema, configEl, "$", errors);
    }
    if (errors.isEmpty()) {
      return new AbstractMap.SimpleImmutableEntry<>(true, Collections.emptyList());
    }
    String joined = String.join("; ", errors);
    if (joined.length() > 500) {
      joined = joined.substring(0, 500) + "...";
    }
    return new AbstractMap.SimpleImmutableEntry<>(
        false,
        Collections.singletonList("Schema validation error for '" + verbName + "': " + joined));
  }

  /**
   * If {@code verbName} is the {@code ai} verb and its inner schema is a {@code $ref} to a closed
   * object (AIObject), return that resolved object so the ai verb can be validated top-level-only.
   * Returns {@code null} for every other verb (which gets full validation).
   */
  private JsonObject aiTopLevelSchema(String verbName, JsonObject innerSchema) {
    if (!"ai".equals(verbName)) {
      return null;
    }
    if (innerSchema.has("$ref") && innerSchema.get("$ref").isJsonPrimitive()) {
      return resolveRef(innerSchema.get("$ref").getAsString());
    }
    return innerSchema;
  }

  /**
   * Top-level-key validation of an object against {@code objSchema}: enforce {@code required} and
   * the closed-key check ({@code unevaluatedProperties:{"not":{}}}) over the object's own keys, but
   * do NOT descend into property values. Used for the ai verb (see validateVerbFull).
   */
  private void validateTopLevelOnly(JsonObject objSchema, JsonElement value, List<String> errors) {
    if (!value.isJsonObject()) {
      errors.add("$ must be an object");
      return;
    }
    JsonObject obj = value.getAsJsonObject();
    JsonObject props =
        objSchema.has("properties") && objSchema.get("properties").isJsonObject()
            ? objSchema.getAsJsonObject("properties")
            : null;
    if (objSchema.has("required") && objSchema.get("required").isJsonArray()) {
      for (JsonElement r : objSchema.getAsJsonArray("required")) {
        if (r.isJsonPrimitive() && r.getAsJsonPrimitive().isString() && !obj.has(r.getAsString())) {
          errors.add("$ is missing required property '" + r.getAsString() + "'");
        }
      }
    }
    boolean closed = false;
    if (objSchema.has("unevaluatedProperties")
        && objSchema.get("unevaluatedProperties").isJsonObject()) {
      JsonObject up = objSchema.getAsJsonObject("unevaluatedProperties");
      closed = up.has("not") && up.getAsJsonObject("not").size() == 0;
    }
    if (objSchema.has("additionalProperties")
        && objSchema.get("additionalProperties").isJsonPrimitive()
        && objSchema.get("additionalProperties").getAsJsonPrimitive().isBoolean()) {
      closed = closed || !objSchema.get("additionalProperties").getAsBoolean();
    }
    if (closed) {
      for (String key : obj.keySet()) {
        if (props == null || !props.has(key)) {
          errors.add("$ has unknown property '" + key + "'");
        }
      }
    }
  }

  // ---- focused draft-2020-12 subset validator -------------------------------
  //
  // Handles exactly the keywords the SWML schema uses. Unknown keywords are
  // ignored (never over-raise). The `unevaluatedProperties` keyword is treated
  // as a local closed/open-key control: the SWML schema never mixes it with
  // sibling-property-adding composition inside the same object (verified), so a
  // closed object ({"not":{}}) allows only its own `properties` keys while an
  // open one ({}) allows extras — matching jsonschema-rs on this schema.

  private static final int MAX_DEPTH = 64;

  private void validateAgainst(
      JsonElement schemaEl, JsonElement value, String path, List<String> errors) {
    validateAgainst(schemaEl, value, path, errors, 0);
  }

  private void validateAgainst(
      JsonElement schemaEl, JsonElement value, String path, List<String> errors, int depth) {
    if (depth > MAX_DEPTH || !schemaEl.isJsonObject()) {
      return;
    }
    JsonObject schema = schemaEl.getAsJsonObject();

    // $ref — resolve and validate against the referenced def.
    if (schema.has("$ref") && schema.get("$ref").isJsonPrimitive()) {
      JsonObject resolved = resolveRef(schema.get("$ref").getAsString());
      if (resolved != null) {
        validateAgainst(resolved, value, path, errors, depth + 1);
      }
      // A $ref node in this schema carries no sibling constraints worth
      // enforcing beyond the target (only description/title), so we're done.
      return;
    }

    // allOf — must satisfy every subschema.
    if (schema.has("allOf") && schema.get("allOf").isJsonArray()) {
      for (JsonElement sub : schema.getAsJsonArray("allOf")) {
        validateAgainst(sub, value, path, errors, depth + 1);
      }
    }

    // oneOf — exactly one subschema must match.
    if (schema.has("oneOf") && schema.get("oneOf").isJsonArray()) {
      int matches = 0;
      for (JsonElement sub : schema.getAsJsonArray("oneOf")) {
        if (matchesQuiet(sub, value, depth + 1)) {
          matches++;
        }
      }
      if (matches != 1) {
        errors.add(path + " must match exactly one schema (matched " + matches + ")");
      }
    }

    // anyOf — at least one subschema must match.
    if (schema.has("anyOf") && schema.get("anyOf").isJsonArray()) {
      boolean any = false;
      for (JsonElement sub : schema.getAsJsonArray("anyOf")) {
        if (matchesQuiet(sub, value, depth + 1)) {
          any = true;
          break;
        }
      }
      if (!any) {
        errors.add(path + " does not match any allowed schema");
      }
    }

    // not — the subschema must NOT match.
    if (schema.has("not") && schema.get("not").isJsonObject()) {
      if (matchesQuiet(schema.getAsJsonObject("not"), value, depth + 1)) {
        errors.add(path + " must not match the forbidden schema");
      }
    }

    // if/then/else.
    if (schema.has("if") && schema.get("if").isJsonObject()) {
      boolean cond = matchesQuiet(schema.getAsJsonObject("if"), value, depth + 1);
      if (cond && schema.has("then")) {
        validateAgainst(schema.get("then"), value, path, errors, depth + 1);
      } else if (!cond && schema.has("else")) {
        validateAgainst(schema.get("else"), value, path, errors, depth + 1);
      }
    }

    // const.
    if (schema.has("const")) {
      if (!schema.get("const").equals(value)) {
        errors.add(path + " must equal the required constant");
      }
    }

    // enum.
    if (schema.has("enum") && schema.get("enum").isJsonArray()) {
      boolean found = false;
      for (JsonElement e : schema.getAsJsonArray("enum")) {
        if (e.equals(value)) {
          found = true;
          break;
        }
      }
      if (!found) {
        errors.add(path + " is not one of the allowed values");
      }
    }

    // type.
    if (schema.has("type")) {
      checkType(schema.get("type"), value, path, errors);
    }

    // pattern (strings only).
    if (schema.has("pattern") && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
      String pat = schema.get("pattern").getAsString();
      try {
        if (!java.util.regex.Pattern.compile(pat).matcher(value.getAsString()).find()) {
          errors.add(path + " does not match the required pattern");
        }
      } catch (java.util.regex.PatternSyntaxException ignored) {
        // Unenforceable pattern — do not over-raise.
      }
    }

    // Object-shape keywords.
    if (value.isJsonObject()) {
      JsonObject obj = value.getAsJsonObject();

      // required.
      if (schema.has("required") && schema.get("required").isJsonArray()) {
        for (JsonElement r : schema.getAsJsonArray("required")) {
          if (r.isJsonPrimitive() && r.getAsJsonPrimitive().isString()) {
            String key = r.getAsString();
            if (!obj.has(key)) {
              errors.add(path + " is missing required property '" + key + "'");
            }
          }
        }
      }

      // properties — validate present values against their subschemas.
      JsonObject props =
          schema.has("properties") && schema.get("properties").isJsonObject()
              ? schema.getAsJsonObject("properties")
              : null;
      if (props != null) {
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
          if (props.has(e.getKey())) {
            validateAgainst(
                props.get(e.getKey()), e.getValue(), path + "." + e.getKey(), errors, depth + 1);
          }
        }
      }

      // additionalProperties / unevaluatedProperties handling for keys not
      // named in `properties`. The SWML schema uses:
      //   - {"not":{}} or false  -> CLOSED: any undeclared key is an error
      //     (the closed-verb / closed-AI-object case the strict-render contract
      //     turns on).
      //   - {} (empty schema)     -> OPEN: undeclared keys allowed (ai.params).
      //   - {"type":...}          -> undeclared keys must VALIDATE against that
      //     subschema (e.g. Section allows arbitrary section names whose value
      //     is an array of SWMLMethod). NOT a close.
      JsonElement extraCtl =
          schema.has("unevaluatedProperties")
              ? schema.get("unevaluatedProperties")
              : (schema.has("additionalProperties") ? schema.get("additionalProperties") : null);
      if (extraCtl != null) {
        boolean closed = false;
        JsonObject extraSchema = null;
        if (extraCtl.isJsonPrimitive() && extraCtl.getAsJsonPrimitive().isBoolean()) {
          closed = !extraCtl.getAsBoolean();
        } else if (extraCtl.isJsonObject()) {
          JsonObject ec = extraCtl.getAsJsonObject();
          if (ec.size() == 0) {
            closed = false; // {} = open
          } else if (ec.has("not") && ec.getAsJsonObject("not").size() == 0) {
            closed = true; // {"not":{}} = closed
          } else {
            extraSchema = ec; // real subschema for extras
          }
        }
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
          if (props != null && props.has(e.getKey())) {
            continue;
          }
          if (closed) {
            errors.add(path + " has unknown property '" + e.getKey() + "'");
          } else if (extraSchema != null) {
            validateAgainst(extraSchema, e.getValue(), path + "." + e.getKey(), errors, depth + 1);
          }
        }
      }
    }

    // Array-shape keywords.
    if (value.isJsonArray()) {
      JsonArray arr = value.getAsJsonArray();
      if (schema.has("items") && schema.get("items").isJsonObject()) {
        JsonObject items = schema.getAsJsonObject("items");
        for (int i = 0; i < arr.size(); i++) {
          validateAgainst(items, arr.get(i), path + "[" + i + "]", errors, depth + 1);
        }
      }
      if (schema.has("minItems") && schema.get("minItems").isJsonPrimitive()) {
        if (arr.size() < schema.get("minItems").getAsInt()) {
          errors.add(path + " has too few items");
        }
      }
    }
  }

  /** True when {@code value} validates cleanly against {@code schemaEl} (no errors collected). */
  private boolean matchesQuiet(JsonElement schemaEl, JsonElement value, int depth) {
    List<String> local = new ArrayList<>();
    validateAgainst(schemaEl, value, "$", local, depth);
    return local.isEmpty();
  }

  /** Resolve a local {@code #/$defs/Name} reference against the loaded schema. */
  private JsonObject resolveRef(String ref) {
    final String prefix = "#/$defs/";
    if (!ref.startsWith(prefix) || schema == null || !schema.has("$defs")) {
      return null;
    }
    String name = ref.substring(prefix.length());
    JsonObject defs = schema.getAsJsonObject("$defs");
    if (defs.has(name) && defs.get(name).isJsonObject()) {
      return defs.getAsJsonObject(name);
    }
    return null;
  }

  /** JSON-Schema {@code type} check (string or array of strings). */
  private void checkType(JsonElement typeEl, JsonElement value, String path, List<String> errors) {
    List<String> allowed = new ArrayList<>();
    if (typeEl.isJsonPrimitive() && typeEl.getAsJsonPrimitive().isString()) {
      allowed.add(typeEl.getAsString());
    } else if (typeEl.isJsonArray()) {
      for (JsonElement t : typeEl.getAsJsonArray()) {
        if (t.isJsonPrimitive() && t.getAsJsonPrimitive().isString()) {
          allowed.add(t.getAsString());
        }
      }
    }
    if (allowed.isEmpty()) {
      return;
    }
    for (String t : allowed) {
      if (typeMatches(t, value)) {
        return;
      }
    }
    errors.add(path + " has the wrong type (expected " + allowed + ")");
  }

  private boolean typeMatches(String type, JsonElement value) {
    switch (type) {
      case "object":
        return value.isJsonObject();
      case "array":
        return value.isJsonArray();
      case "null":
        return value.isJsonNull();
      case "string":
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
      case "boolean":
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
      case "number":
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
      case "integer":
        if (!(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())) {
          return false;
        }
        double d = value.getAsDouble();
        return d == Math.rint(d) && !Double.isInfinite(d);
      default:
        return true; // unknown type token — do not over-raise
    }
  }

  private Map.Entry<Boolean, List<String>> validateVerbLightweight(
      String verbName, Map<String, Object> verbConfig) {
    List<String> errors = new ArrayList<>();
    for (String prop : getVerbRequiredProperties(verbName)) {
      if (!verbConfig.containsKey(prop)) {
        errors.add("Missing required property '" + prop + "' for verb '" + verbName + "'");
      }
    }
    return new AbstractMap.SimpleImmutableEntry<>(errors.isEmpty(), errors);
  }

  /**
   * Validate a complete SWML document. Mirrors Python's {@code validate_document(document)}.
   * Returns {@code (false, ["Schema validator not initialized"])} when no full validator is wired
   * in — same contract as Python.
   */
  public Map.Entry<Boolean, List<String>> validateDocument(Map<String, Object> document) {
    if (fullValidator == null) {
      return new AbstractMap.SimpleImmutableEntry<>(
          false, Collections.singletonList("Schema validator not initialized"));
    }
    // Validate the whole document against the root schema with the focused
    // draft-2020-12 validator. Mirrors Python's full-validator path.
    JsonElement docEl = new Gson().toJsonTree(document);
    List<String> errors = new ArrayList<>();
    validateAgainst(schema, docEl, "$", errors);
    if (errors.isEmpty()) {
      return new AbstractMap.SimpleImmutableEntry<>(true, Collections.emptyList());
    }
    String joined = String.join("; ", errors);
    if (joined.length() > 500) {
      joined = joined.substring(0, 500) + "...";
    }
    return new AbstractMap.SimpleImmutableEntry<>(
        false, Collections.singletonList("Document validation error: " + joined));
  }

  /**
   * Generate a Python-style method signature string for a verb. Mirrors Python's {@code
   * generate_method_signature(verb_name)}.
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
    doc.append("\"\"\"\n        Add the ")
        .append(verbName)
        .append(" verb to the current document\n        \n");
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
    doc.append(
        "        \n        Returns:\n            True if the verb was added successfully, False otherwise\n        \"\"\"\n");
    return "def " + verbName + "(" + String.join(", ", parts) + ") -> bool:\n" + doc;
  }

  /**
   * Generate a Python-style method body string for a verb. Mirrors Python's {@code
   * generate_method_body(verb_name)}.
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
