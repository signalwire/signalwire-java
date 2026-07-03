/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core;

import com.signalwire.sdk.pom.PromptObjectModel;
import com.signalwire.sdk.pom.Section;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for creating structured prompts using the Prompt Object Model.
 *
 * <p>Java port of the Python reference {@code signalwire.core.pom_builder.PomBuilder}. A flexible
 * wrapper around {@link PromptObjectModel} that allows dynamic creation of sections on demand,
 * adding content to existing sections, nesting subsections, and rendering to Markdown or XML. There
 * are no predefined section types. All mutator methods return {@code this} for fluent chaining.
 */
public class PomBuilder {

  private PromptObjectModel pom;
  private final Map<String, Section> sections = new LinkedHashMap<>();

  /** Initialize a new POM builder with an empty POM. */
  public PomBuilder() {
    this.pom = new PromptObjectModel();
  }

  public PromptObjectModel getPom() {
    return pom;
  }

  /**
   * Add a new section to the POM.
   *
   * @param title section title
   * @param body section body (may be {@code null} -> empty)
   * @param bullets bullet points (may be {@code null})
   * @param numbered tri-state numbering flag (may be {@code null})
   * @param numberedBullets whether bullets render as a numbered list
   * @param subsections optional list of subsection descriptor maps with keys {@code title}, {@code
   *     body}, {@code bullets}
   * @return {@code this} for chaining
   */
  @SuppressWarnings("unchecked")
  public PomBuilder addSection(
      String title,
      String body,
      List<String> bullets,
      Boolean numbered,
      boolean numberedBullets,
      List<Map<String, Object>> subsections) {
    Section section =
        pom.addSection(
            title,
            body == null ? "" : body,
            bullets != null ? bullets : new ArrayList<>(),
            numbered,
            numberedBullets);
    sections.put(title, section);

    if (subsections != null) {
      for (Map<String, Object> sub : subsections) {
        if (sub == null || !sub.containsKey("title")) {
          continue;
        }
        Object subBullets = sub.get("bullets");
        List<String> bl = new ArrayList<>();
        if (subBullets instanceof List) {
          for (Object b : (List<Object>) subBullets) {
            bl.add(String.valueOf(b));
          }
        }
        Object subBody = sub.get("body");
        section.addSubsection(
            String.valueOf(sub.get("title")), subBody == null ? "" : String.valueOf(subBody), bl);
      }
    }
    return this;
  }

  /** Convenience: title only. */
  public PomBuilder addSection(String title) {
    return addSection(title, "", null, null, false, null);
  }

  /** Convenience: title + body. */
  public PomBuilder addSection(String title, String body) {
    return addSection(title, body, null, null, false, null);
  }

  /** Convenience: title + body + bullets. */
  public PomBuilder addSection(String title, String body, List<String> bullets) {
    return addSection(title, body, bullets, null, false, null);
  }

  /**
   * Add content to an existing section, creating it if it doesn't exist (auto-vivification).
   *
   * @param body appended to any existing body (separated by a blank line)
   * @param bullet a single bullet to append (may be {@code null})
   * @param bullets a list of bullets to append (may be {@code null})
   * @return {@code this} for chaining
   */
  public PomBuilder addToSection(String title, String body, String bullet, List<String> bullets) {
    if (!sections.containsKey(title)) {
      addSection(title);
    }
    Section section = sections.get(title);
    if (body != null && !body.isEmpty()) {
      appendBody(section, body);
    }
    if (bullet != null) {
      section.getBullets().add(bullet);
    }
    if (bullets != null) {
      section.getBullets().addAll(bullets);
    }
    return this;
  }

  /**
   * Add a subsection to an existing section, creating the parent if needed (auto-vivification).
   *
   * @return {@code this} for chaining
   */
  public PomBuilder addSubsection(
      String parentTitle, String title, String body, List<String> bullets) {
    if (!sections.containsKey(parentTitle)) {
      addSection(parentTitle);
    }
    Section parent = sections.get(parentTitle);
    parent.addSubsection(
        title, body == null ? "" : body, bullets != null ? bullets : new ArrayList<>());
    return this;
  }

  /** Convenience: subsection with title + body. */
  public PomBuilder addSubsection(String parentTitle, String title, String body) {
    return addSubsection(parentTitle, title, body, null);
  }

  /** Check if a section with the given title exists. */
  public boolean hasSection(String title) {
    return sections.containsKey(title);
  }

  /** Get a section by title, or {@code null} if not found. */
  public Section getSection(String title) {
    return sections.get(title);
  }

  /** Render the POM as Markdown. */
  public String renderMarkdown() {
    return pom.renderMarkdown();
  }

  /** Render the POM as XML. */
  public String renderXml() {
    return pom.renderXml();
  }

  /**
   * Convert the POM to a list of section maps. (Named {@code toMap} because the Java return type is
   * {@code List<Map<String,Object>>}; mirrors Python {@code to_dict}.)
   */
  public List<Map<String, Object>> toMap() {
    return pom.toMap();
  }

  /** Convert the POM to a JSON string. */
  public String toJson() {
    return pom.toJson();
  }

  /**
   * Create a {@link PomBuilder} from a list of section maps. Mirrors Python {@code from_sections}.
   */
  public static PomBuilder fromSections(List<Map<String, Object>> sections) {
    PomBuilder builder = new PomBuilder();
    builder.pom = PromptObjectModel.fromJsonMap(sections);
    for (Section section : builder.pom.getSections()) {
      if (section.getTitle() != null) {
        builder.sections.put(section.getTitle(), section);
      }
    }
    return builder;
  }

  private void appendBody(Section section, String body) {
    String existing = section.getBody();
    if (existing != null && !existing.isEmpty()) {
      section.addBody(existing + "\n\n" + body);
    } else {
      section.addBody(body);
    }
  }
}
