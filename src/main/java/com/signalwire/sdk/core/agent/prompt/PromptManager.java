/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core.agent.prompt;

import com.signalwire.sdk.contexts.ContextBuilder;
import com.signalwire.sdk.pom.PromptObjectModel;
import com.signalwire.sdk.pom.Section;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages prompt building and configuration for an agent.
 *
 * <p>Mirrors Python's {@code signalwire.core.agent.prompt.manager.PromptManager} and the Ruby
 * {@code SignalWire::Core::Agent::Prompt::PromptManager}. It manages a POM-backed prompt (via
 * {@link PromptObjectModel}), an optional raw prompt text, a post-prompt, and a contexts
 * configuration (via {@link ContextBuilder}).
 *
 * <p>The prompt has two mutually exclusive modes: raw text ({@link #setPromptText}) OR POM sections
 * (the {@code promptAdd*} methods). Mixing the two throws. Contexts, when defined, take precedence
 * over both in {@link #getPrompt}.
 */
public class PromptManager {

  private final Object agent;
  private PromptObjectModel pom;
  private String promptText;
  private String postPromptText;
  private Map<String, Object> contexts;

  /** Construct a standalone manager (no parent agent). */
  public PromptManager() {
    this(null);
  }

  /**
   * @param agent optional parent agent instance (kept as a back-reference for parity with the
   *     Python/Ruby managers; may be {@code null} for standalone use).
   */
  public PromptManager(Object agent) {
    this.agent = agent;
    this.pom = new PromptObjectModel();
    this.promptText = null;
    this.postPromptText = null;
    this.contexts = null;
  }

  /**
   * @return the backing POM.
   */
  public PromptObjectModel getPom() {
    return pom;
  }

  /**
   * Set the agent's prompt as raw text.
   *
   * @param text prompt text
   * @return this
   * @throws IllegalArgumentException if POM sections are already in use
   */
  public PromptManager setPromptText(String text) {
    validatePromptModeExclusivity();
    this.promptText = text;
    return this;
  }

  /**
   * Set the post-prompt text.
   *
   * @param text post-prompt text
   * @return this
   */
  public PromptManager setPostPrompt(String text) {
    this.postPromptText = text;
    return this;
  }

  /**
   * Set the prompt from a POM array (list of section maps). Mirrors Python's {@code
   * set_prompt_pom(pom)}.
   *
   * @param pomList POM section descriptors
   * @return this
   */
  public PromptManager setPromptPom(List<Map<String, Object>> pomList) {
    this.promptText = null;
    this.pom = PromptObjectModel.fromJsonMap(pomList);
    return this;
  }

  /**
   * Add a section to the prompt. Mirrors Python's {@code prompt_add_section(title, body="",
   * bullets=None, numbered=False, numbered_bullets=False, subsections=None)}.
   *
   * @param title section title
   * @param body optional body text
   * @param bullets optional bullet points
   * @param numbered number this section
   * @param numberedBullets number the bullets
   * @param subsections optional subsection maps
   * @return this
   */
  public PromptManager promptAddSection(
      String title,
      String body,
      List<String> bullets,
      boolean numbered,
      boolean numberedBullets,
      List<Map<String, Object>> subsections) {
    validatePromptModeExclusivity();
    Section section =
        pom.addSection(
            title,
            body != null ? body : "",
            bullets != null ? bullets : new ArrayList<>(),
            numbered ? Boolean.TRUE : null,
            numberedBullets);
    addSubsections(section, subsections);
    return this;
  }

  /** Convenience: title + body only. */
  public PromptManager promptAddSection(String title, String body) {
    return promptAddSection(title, body, null, false, false, null);
  }

  /**
   * Add content to an existing section (creating it if needed). Mirrors Python's {@code
   * prompt_add_to_section(title, body=None, bullet=None, bullets=None)}.
   *
   * @param title section title
   * @param body text to append to the section body (may be {@code null})
   * @param bullet single bullet to add (may be {@code null})
   * @param bullets bullets to add (may be {@code null})
   * @return this
   */
  public PromptManager promptAddToSection(
      String title, String body, String bullet, List<String> bullets) {
    Section section = pom.findSection(title).orElseGet(() -> pom.addSection(title, ""));
    appendBody(section, body);
    appendBullets(section, bullet, bullets);
    return this;
  }

  /**
   * Add a subsection to an existing section (creating the parent if needed). Mirrors Python's
   * {@code prompt_add_subsection(parent_title, title, body="", bullets=None)}.
   *
   * @param parentTitle parent section title
   * @param title subsection title
   * @param body optional subsection body
   * @param bullets optional bullets
   * @return this
   */
  public PromptManager promptAddSubsection(
      String parentTitle, String title, String body, List<String> bullets) {
    Section parent = pom.findSection(parentTitle).orElseGet(() -> pom.addSection(parentTitle, ""));
    parent.addSubsection(
        title,
        body != null ? body : "",
        bullets != null ? bullets : new ArrayList<>(),
        null,
        false);
    return this;
  }

  /**
   * Check whether a section exists in the prompt.
   *
   * @param title section title
   * @return {@code true} if the section exists
   */
  public boolean promptHasSection(String title) {
    return pom.findSection(title).isPresent();
  }

  /**
   * Define contexts for the agent. Mirrors Python's {@code define_contexts(contexts)} which accepts
   * a {@link ContextBuilder} (materialised via {@code toMap}) or a raw Map.
   *
   * @param contexts a {@link ContextBuilder} or a {@code Map}
   * @return this
   * @throws IllegalArgumentException if not a ContextBuilder or Map
   */
  @SuppressWarnings("unchecked")
  public PromptManager defineContexts(Object contexts) {
    if (contexts instanceof ContextBuilder) {
      this.contexts = ((ContextBuilder) contexts).toMap();
    } else if (contexts instanceof Map) {
      this.contexts = (Map<String, Object>) contexts;
    } else {
      throw new IllegalArgumentException("contexts must be a Map or a ContextBuilder object");
    }
    return this;
  }

  /**
   * Get the prompt configuration.
   *
   * <p>Contexts take precedence (return {@code null} — they render their own sections); otherwise
   * raw text if set, else the POM section list, else {@code null}.
   *
   * @return a {@code String} (raw text), a {@code List<Map<String,Object>>} (POM sections), or
   *     {@code null}
   */
  public Object getPrompt() {
    if (contexts != null) {
      return null;
    }
    if (promptText != null) {
      return promptText;
    }
    List<Map<String, Object>> sections = pom.toMap();
    return sections.isEmpty() ? null : sections;
  }

  /**
   * @return the raw prompt text if set, else {@code null}.
   */
  public String getRawPrompt() {
    return promptText;
  }

  /**
   * @return the post-prompt text, or {@code null}.
   */
  public String getPostPrompt() {
    return postPromptText;
  }

  /**
   * @return the contexts configuration, or {@code null}.
   */
  public Map<String, Object> getContexts() {
    return contexts;
  }

  // ---- internals ----

  /** Throw if both prompt modes (raw text + POM sections) are active. */
  private void validatePromptModeExclusivity() {
    if (promptText != null && !pom.toMap().isEmpty()) {
      throw new IllegalArgumentException(
          "Cannot use both prompt_text and POM sections. Please use either set_prompt_text() OR the"
              + " prompt_add_* methods, not both.");
    }
  }

  private void addSubsections(Section section, List<Map<String, Object>> subsections) {
    if (subsections == null) {
      return;
    }
    for (Map<String, Object> sub : subsections) {
      Object titleObj = sub.get("title");
      if (titleObj == null) {
        continue;
      }
      Object bodyObj = sub.getOrDefault("body", "");
      @SuppressWarnings("unchecked")
      List<String> subBullets =
          sub.get("bullets") instanceof List
              ? (List<String>) sub.get("bullets")
              : new ArrayList<>();
      section.addSubsection(
          String.valueOf(titleObj), bodyObj != null ? String.valueOf(bodyObj) : "", subBullets);
    }
  }

  private void appendBody(Section section, String body) {
    if (body == null) {
      return;
    }
    String existing = section.getBody();
    if (existing == null || existing.isEmpty()) {
      section.addBody(body);
    } else {
      section.addBody(existing + "\n\n" + body);
    }
  }

  private void appendBullets(Section section, String bullet, List<String> bullets) {
    List<String> toAdd = new ArrayList<>();
    if (bullet != null) {
      toAdd.add(bullet);
    }
    if (bullets != null) {
      toAdd.addAll(bullets);
    }
    if (!toAdd.isEmpty()) {
      section.addBullets(toAdd);
    }
  }
}
