/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.signalwire.sdk.logging.Logger;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Singleton that loads schema.json from resources and extracts the 38 SWML verb definitions.
 *
 * <p>The extraction algorithm:
 *
 * <ol>
 *   <li>Parse schema.json
 *   <li>For each entry in $defs.SWMLMethod.anyOf, get the $ref
 *   <li>Look up the definition in $defs
 *   <li>The actual verb name is the first key in properties
 * </ol>
 */
public final class Schema {

  private static final Logger log = Logger.getLogger(Schema.class);
  private static volatile Schema instance;

  private final Map<String, JsonObject> verbs; // verbName -> full definition
  private final Set<String> verbNames;

  private Schema() {
    verbs = new LinkedHashMap<>();
    verbNames = new LinkedHashSet<>();
    load();
  }

  public static Schema getInstance() {
    if (instance == null) {
      synchronized (Schema.class) {
        if (instance == null) {
          instance = new Schema();
        }
      }
    }
    return instance;
  }

  private void load() {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream("schema.json")) {
      if (is == null) {
        throw new RuntimeException("schema.json not found in resources");
      }
      var reader = new InputStreamReader(is, StandardCharsets.UTF_8);
      var gson = new Gson();
      var root = gson.fromJson(reader, JsonObject.class);

      var defs = root.getAsJsonObject("$defs");
      if (defs == null) {
        throw new RuntimeException("schema.json missing $defs");
      }

      var swmlMethod = defs.getAsJsonObject("SWMLMethod");
      if (swmlMethod == null) {
        throw new RuntimeException("schema.json missing $defs.SWMLMethod");
      }

      var anyOf = swmlMethod.getAsJsonArray("anyOf");
      if (anyOf == null) {
        throw new RuntimeException("schema.json missing $defs.SWMLMethod.anyOf");
      }

      for (JsonElement entry : anyOf) {
        var refObj = entry.getAsJsonObject();
        var ref = refObj.get("$ref").getAsString();
        // ref is like "#/$defs/SIPRefer"
        var defName = ref.substring(ref.lastIndexOf('/') + 1);
        var definition = defs.getAsJsonObject(defName);
        if (definition == null) {
          log.warn("Schema definition not found: %s", defName);
          continue;
        }

        var properties = definition.getAsJsonObject("properties");
        if (properties == null || properties.size() == 0) {
          log.warn("Schema definition has no properties: %s", defName);
          continue;
        }

        // The actual verb name is the first key in properties
        String verbName = properties.keySet().iterator().next();
        verbs.put(verbName, definition);
        verbNames.add(verbName);
      }

      log.debug("Loaded %d verb definitions from schema.json", verbs.size());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load schema.json", e);
    }
  }

  /** Check if a verb name is valid. */
  public boolean isValidVerb(String name) {
    return verbNames.contains(name);
  }

  /** Get all verb names. */
  public Set<String> getVerbNames() {
    return Collections.unmodifiableSet(verbNames);
  }

  /** Get the full definition for a verb. */
  public JsonObject getVerb(String name) {
    return verbs.get(name);
  }

  /** Get the number of loaded verb definitions. */
  public int verbCount() {
    return verbs.size();
  }
}
