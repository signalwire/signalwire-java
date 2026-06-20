/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.*;

/**
 * SWML Document model.
 *
 * <p>A document has a version, sections (each a list of verbs), and a top-level verbs list that
 * maps to the "main" section.
 */
public class Document {

  private static final String VERSION = "1.0.0";

  private final Map<String, List<Map<String, Object>>> sections;
  private final List<Map<String, Object>> verbs;

  public Document() {
    this.sections = new LinkedHashMap<>();
    this.verbs = new ArrayList<>();
    this.sections.put("main", this.verbs);
  }

  /** Reset document to empty state. */
  public void reset() {
    sections.clear();
    verbs.clear();
    sections.put("main", verbs);
  }

  /** Add a named section. If the section already exists, returns the existing list. */
  public List<Map<String, Object>> addSection(String name) {
    return sections.computeIfAbsent(name, k -> new ArrayList<>());
  }

  /** Check whether a section exists. */
  public boolean hasSection(String name) {
    return sections.containsKey(name);
  }

  /** Add a verb to the main section. */
  public void addVerb(String verbName, Object verbData) {
    Map<String, Object> verb = new LinkedHashMap<>();
    verb.put(verbName, verbData);
    verbs.add(verb);
  }

  /** Add a verb to a named section. */
  public void addVerbToSection(String sectionName, String verbName, Object verbData) {
    var section = addSection(sectionName);
    Map<String, Object> verb = new LinkedHashMap<>();
    verb.put(verbName, verbData);
    section.add(verb);
  }

  /** Get the main verbs list. */
  public List<Map<String, Object>> getVerbs() {
    return verbs;
  }

  /** Get verbs for a named section. */
  public List<Map<String, Object>> getSectionVerbs(String name) {
    return sections.get(name);
  }

  /** Convert to a Map suitable for JSON serialization. */
  public Map<String, Object> toMap() {
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("version", VERSION);
    doc.put("sections", sections);
    return doc;
  }

  /** Render as compact JSON. */
  public String render() {
    return new Gson().toJson(toMap());
  }

  /** Render as pretty-printed JSON. */
  public String renderPretty() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
  }
}
