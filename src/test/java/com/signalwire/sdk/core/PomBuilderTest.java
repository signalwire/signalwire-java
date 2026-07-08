/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real-behavior tests for {@link PomBuilder} (parity with Python pom_builder.PomBuilder). */
class PomBuilderTest {

  private PomBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new PomBuilder();
  }

  @Test
  void addSectionReturnsSelfForChaining() {
    PomBuilder result = builder.addSection("Identity", "You are helpful");
    assertSame(builder, result);
    assertTrue(builder.hasSection("Identity"));
  }

  @Test
  void renderMarkdownContainsSection() {
    builder.addSection("Rules", "Follow them", List.of("one", "two"));
    String md = builder.renderMarkdown();

    assertTrue(md.contains("Rules"));
    assertTrue(md.contains("Follow them"));
    assertTrue(md.contains("one"));
    assertTrue(md.contains("two"));
  }

  @Test
  void renderXmlIsWellFormed() {
    builder.addSection("Task", "Do work");
    String xml = builder.renderXml();

    assertTrue(xml.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
    assertTrue(xml.contains("<prompt>"));
    assertTrue(xml.contains("Task"));
    assertTrue(xml.contains("</prompt>"));
  }

  @Test
  void addToSectionAutovivifiesAndAppendsBody() {
    builder.addToSection("New", "first", null, null);
    builder.addToSection("New", "second", null, null);
    assertEquals("first\n\nsecond", builder.getSection("New").getBody());
  }

  @Test
  void addToSectionAppendsBullets() {
    builder.addSection("L", "", List.of("a"));
    builder.addToSection("L", null, "b", null);
    builder.addToSection("L", null, null, List.of("c", "d"));

    assertEquals(List.of("a", "b", "c", "d"), builder.getSection("L").getBullets());
  }

  @Test
  void addSubsectionAutovivifiesParent() {
    builder.addSubsection("Parent", "Child", "nested");
    var parent = builder.getSection("Parent");

    assertTrue(builder.hasSection("Parent"));
    assertEquals(1, parent.getSubsections().size());
    assertEquals("Child", parent.getSubsections().get(0).getTitle());
    assertEquals("nested", parent.getSubsections().get(0).getBody());
  }

  @Test
  void addSectionWithSubsectionsList() {
    builder.addSection(
        "Top",
        "b",
        null,
        null,
        false,
        List.of(Map.of("title", "Sub", "body", "sb", "bullets", List.of("x"))));
    var sub = builder.getSection("Top").getSubsections().get(0);

    assertEquals("Sub", sub.getTitle());
    assertEquals("sb", sub.getBody());
    assertEquals(List.of("x"), sub.getBullets());
  }

  @Test
  void hasSectionFalseWhenAbsent() {
    assertFalse(builder.hasSection("Nope"));
    assertNull(builder.getSection("Nope"));
  }

  @Test
  void toMapReturnsSectionArray() {
    builder.addSection("One", "body-one");
    List<Map<String, Object>> dict = builder.toMap();

    assertEquals("One", dict.get(0).get("title"));
    assertEquals("body-one", dict.get(0).get("body"));
  }

  @Test
  void toJsonRoundTrips() {
    builder.addSection("J", "jb");
    Type listOfMaps = new TypeToken<List<Map<String, Object>>>() {}.getType();
    List<Map<String, Object>> parsed = new Gson().fromJson(builder.toJson(), listOfMaps);

    assertEquals("J", parsed.get(0).get("title"));
    assertEquals("jb", parsed.get(0).get("body"));
  }

  @Test
  void fromSectionsClassMethod() {
    PomBuilder b =
        PomBuilder.fromSections(
            List.of(Map.of("title", "A", "body", "ba"), Map.of("title", "B", "body", "bb")));

    assertTrue(b.hasSection("A"));
    assertTrue(b.hasSection("B"));
    assertEquals("ba", b.getSection("A").getBody());
  }
}
