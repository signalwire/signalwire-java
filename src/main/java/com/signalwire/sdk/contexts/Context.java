/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.contexts;

import java.util.*;

/**
 * Represents a single context containing multiple steps.
 *
 * <p>All setter methods return {@code this} for fluent chaining.
 */
public class Context {

  static final int MAX_STEPS_PER_CONTEXT = 100;

  private final String name;
  private final Map<String, Step> steps;
  private final List<String> stepOrder;
  private List<String> validContexts;
  private List<String> validSteps;
  private String initialStep;

  // Context entry parameters
  private String postPrompt;
  private String systemPrompt;
  private boolean consolidate;
  private boolean fullReset;
  private String userPrompt;
  private boolean isolated;

  // Context prompt
  private String promptText;
  private final List<Map<String, Object>> promptSections;

  // Context fillers
  private Map<String, List<String>> enterFillers;
  private Map<String, List<String>> exitFillers;

  // Default history visibility mode for every step in this context
  private String history;

  public Context(String name) {
    this.name = name;
    this.steps = new LinkedHashMap<>();
    this.stepOrder = new ArrayList<>();
    this.promptSections = new ArrayList<>();
  }

  /**
   * The context's name — how {@code change_context} and {@link #setValidContexts(List)} refer to
   * it.
   *
   * @return the context name.
   */
  public String getName() {
    return name;
  }

  /** Add a new step to this context. */
  public Step addStep(String stepName) {
    if (steps.containsKey(stepName)) {
      throw new IllegalStateException(
          "Step '" + stepName + "' already exists in context '" + name + "'");
    }
    if (steps.size() >= MAX_STEPS_PER_CONTEXT) {
      throw new IllegalStateException(
          "Maximum steps per context (" + MAX_STEPS_PER_CONTEXT + ") exceeded");
    }
    Step step = new Step(stepName);
    steps.put(stepName, step);
    stepOrder.add(stepName);
    return step;
  }

  /** Add a fully configured step in one call. */
  public Step addStep(
      String stepName,
      String task,
      List<String> bullets,
      String criteria,
      Object functions,
      List<String> validSteps) {
    Step step = addStep(stepName);
    if (task != null) step.addSection("Task", task);
    if (bullets != null) step.addBullets("Process", bullets);
    if (criteria != null) step.setStepCriteria(criteria);
    if (functions != null) step.setFunctions(functions);
    if (validSteps != null) step.setValidSteps(validSteps);
    return step;
  }

  /**
   * Look up a step already added to this context.
   *
   * @param stepName the step name.
   * @return the step, or {@code null} when this context has no step by that name.
   */
  public Step getStep(String stepName) {
    return steps.get(stepName);
  }

  /**
   * Remove a step from this context, dropping it from the flow order too. A name that is not
   * present is silently ignored.
   *
   * @param stepName the step name.
   * @return this context, for chaining.
   */
  public Context removeStep(String stepName) {
    if (steps.containsKey(stepName)) {
      steps.remove(stepName);
      stepOrder.remove(stepName);
    }
    return this;
  }

  /**
   * Move an existing step to a new place in the flow order, which is what {@code next_step}
   * follows.
   *
   * @param stepName the step name; must already exist in this context.
   * @param position the target index in the flow order.
   * @return this context, for chaining.
   * @throws IllegalStateException if this context has no step by that name.
   */
  public Context moveStep(String stepName, int position) {
    if (!steps.containsKey(stepName)) {
      throw new IllegalStateException(
          "Step '" + stepName + "' not found in context '" + name + "'");
    }
    stepOrder.remove(stepName);
    stepOrder.add(position, stepName);
    return this;
  }

  /**
   * Set which step the context starts on when entered.
   *
   * <p>By default, a context starts on its first step (index 0). Use this to skip a preamble step
   * on re-entry via {@code change_context}.
   *
   * @param stepName name of the step to start on (must exist in this context).
   * @return this context for chaining.
   */
  public Context setInitialStep(String stepName) {
    this.initialStep = stepName;
    return this;
  }

  // Package-private accessor for validation
  String getInitialStep() {
    return initialStep;
  }

  /**
   * Which contexts the model may move to from here. Declaring them is what makes the native {@code
   * change_context} tool available — the model cannot leave for a context not listed.
   *
   * @param contexts the reachable context names.
   * @return this context, for chaining.
   */
  public Context setValidContexts(List<String> contexts) {
    this.validContexts = contexts;
    return this;
  }

  /**
   * Default set of steps reachable from any step in this context. A step's own {@link
   * Step#setValidSteps(List)} overrides it.
   *
   * @param steps the reachable step names.
   * @return this context, for chaining.
   */
  public Context setValidSteps(List<String> steps) {
    this.validSteps = steps;
    return this;
  }

  /**
   * Post-prompt used for this context's summary, overriding the agent-level one.
   *
   * @param postPrompt the post-prompt text.
   * @return this context, for chaining.
   */
  public Context setPostPrompt(String postPrompt) {
    this.postPrompt = postPrompt;
    return this;
  }

  /**
   * System prompt installed when this context is entered, replacing the agent's for as long as the
   * context is active. This is what makes a context a distinct persona rather than a step group.
   *
   * @param systemPrompt the system prompt.
   * @return this context, for chaining.
   */
  public Context setSystemPrompt(String systemPrompt) {
    this.systemPrompt = systemPrompt;
    return this;
  }

  /**
   * On entry, summarise the prior conversation into a single message instead of carrying it forward
   * in full. Note that configuring a reset this way also SUPPRESSES the history wipe {@link
   * #setIsolated(boolean)} would otherwise do.
   *
   * @param consolidate whether to consolidate history on entry.
   * @return this context, for chaining.
   */
  public Context setConsolidate(boolean consolidate) {
    this.consolidate = consolidate;
    return this;
  }

  /**
   * On entry, reset the conversation completely rather than continuing it. Like {@link
   * #setConsolidate(boolean)}, configuring a reset suppresses the {@link #setIsolated(boolean)}
   * wipe in favour of this behaviour.
   *
   * @param fullReset whether to fully reset on entry.
   * @return this context, for chaining.
   */
  public Context setFullReset(boolean fullReset) {
    this.fullReset = fullReset;
    return this;
  }

  /**
   * A user-role message injected when this context is entered, seeding the new context with an
   * opening turn.
   *
   * @param userPrompt the user prompt.
   * @return this context, for chaining.
   */
  public Context setUserPrompt(String userPrompt) {
    this.userPrompt = userPrompt;
    return this;
  }

  /**
   * Mark this context as isolated — entering it wipes conversation history.
   *
   * <p>When {@code isolated=true} and the context is entered via change_context, the runtime wipes
   * the conversation array. The model starts fresh with only the new context's system_prompt + step
   * instructions, with no memory of prior turns.
   *
   * <p><b>EXCEPTION — reset overrides the wipe:</b> If the context also has a reset configuration
   * (via {@link #setConsolidate(boolean)} or {@link #setFullReset(boolean)}), the wipe is skipped
   * in favor of the reset behavior. Use reset with consolidate=true to summarize prior history into
   * a single message instead of dropping it entirely.
   *
   * <p>Use cases: switching to a sensitive billing flow that should not see prior small-talk;
   * handing off to a different agent persona; resetting after a long off-topic detour.
   *
   * @param isolated true to wipe conversation history on context entry (subject to the reset
   *     exception above).
   * @return this context for chaining.
   */
  public Context setIsolated(boolean isolated) {
    this.isolated = isolated;
    return this;
  }

  /**
   * Set this context's prompt as raw text. Mutually exclusive with the POM section methods — pick
   * one shape per context.
   *
   * @param prompt the raw prompt text.
   * @return this context, for chaining.
   * @throws IllegalStateException if {@link #addSection(String, String)} or {@link
   *     #addBullets(String, List)} has already been called on this context.
   */
  public Context setPrompt(String prompt) {
    if (!promptSections.isEmpty()) {
      throw new IllegalStateException("Cannot use setPrompt() when POM sections have been added.");
    }
    this.promptText = prompt;
    return this;
  }

  /**
   * Append a prose POM section to this context's prompt. Mutually exclusive with {@link
   * #setPrompt(String)}.
   *
   * @param title the section heading.
   * @param body the section prose.
   * @return this context, for chaining.
   * @throws IllegalStateException if {@link #setPrompt(String)} has already been called.
   */
  public Context addSection(String title, String body) {
    if (promptText != null) {
      throw new IllegalStateException("Cannot add POM sections when setPrompt() has been used.");
    }
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("title", title);
    section.put("body", body);
    promptSections.add(section);
    return this;
  }

  /**
   * Append a bulleted POM section to this context's prompt. Mutually exclusive with {@link
   * #setPrompt(String)}.
   *
   * @param title the section heading.
   * @param bullets the bullet points.
   * @return this context, for chaining.
   * @throws IllegalStateException if {@link #setPrompt(String)} has already been called.
   */
  public Context addBullets(String title, List<String> bullets) {
    if (promptText != null) {
      throw new IllegalStateException("Cannot add POM sections when setPrompt() has been used.");
    }
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("title", title);
    section.put("bullets", bullets);
    promptSections.add(section);
    return this;
  }

  /**
   * Replace the phrases spoken on entering this context, keyed by language code, so the transition
   * is not silent. A {@code null} map leaves the existing fillers alone rather than clearing them.
   *
   * @param fillers language code to phrases.
   * @return this context, for chaining.
   */
  public Context setEnterFillers(Map<String, List<String>> fillers) {
    if (fillers != null) {
      this.enterFillers = new LinkedHashMap<>(fillers);
    }
    return this;
  }

  /**
   * Replace the phrases spoken on leaving this context, keyed by language code. A {@code null} map
   * leaves the existing fillers alone rather than clearing them.
   *
   * @param fillers language code to phrases.
   * @return this context, for chaining.
   */
  public Context setExitFillers(Map<String, List<String>> fillers) {
    if (fillers != null) {
      this.exitFillers = new LinkedHashMap<>(fillers);
    }
    return this;
  }

  /**
   * Set the entry phrases for one language, leaving other languages untouched. A {@code null}
   * language code or phrase list makes the call a no-op.
   *
   * @param languageCode BCP-47 language code, e.g. {@code "en-US"}.
   * @param fillers the phrases for that language.
   * @return this context, for chaining.
   */
  public Context addEnterFiller(String languageCode, List<String> fillers) {
    if (languageCode != null && fillers != null) {
      if (enterFillers == null) enterFillers = new LinkedHashMap<>();
      enterFillers.put(languageCode, fillers);
    }
    return this;
  }

  /**
   * Set the exit phrases for one language, leaving other languages untouched. A {@code null}
   * language code or phrase list makes the call a no-op.
   *
   * @param languageCode BCP-47 language code, e.g. {@code "en-US"}.
   * @param fillers the phrases for that language.
   * @return this context, for chaining.
   */
  public Context addExitFiller(String languageCode, List<String> fillers) {
    if (languageCode != null && fillers != null) {
      if (exitFillers == null) exitFillers = new LinkedHashMap<>();
      exitFillers.put(languageCode, fillers);
    }
    return this;
  }

  /**
   * Set the default {@code history} visibility mode for every step in this context.
   *
   * <p>A step's own {@link Step#setHistory(String)} overrides this. See {@link
   * Step#setHistory(String)} for what each mode does.
   *
   * @param history one of {@code "keep"}, {@code "default"}, or {@code "hide"}.
   * @return this context for chaining.
   * @throws IllegalArgumentException if history is not one of the three modes.
   */
  public Context setHistory(String history) {
    this.history = Step.validateHistory(history);
    return this;
  }

  // Package-private accessors for validation
  Map<String, Step> getSteps() {
    return steps;
  }

  List<String> getStepOrder() {
    return stepOrder;
  }

  List<String> getValidContexts() {
    return validContexts;
  }

  public Map<String, Object> toMap() {
    if (steps.isEmpty()) {
      throw new IllegalStateException("Context '" + name + "' has no steps defined");
    }

    Map<String, Object> map = new LinkedHashMap<>();

    // Steps in order
    List<Map<String, Object>> stepMaps = new ArrayList<>();
    for (String stepName : stepOrder) {
      stepMaps.add(steps.get(stepName).toMap());
    }
    map.put("steps", stepMaps);

    if (validContexts != null) map.put("valid_contexts", validContexts);
    if (validSteps != null) map.put("valid_steps", validSteps);
    if (initialStep != null) map.put("initial_step", initialStep);
    if (postPrompt != null) map.put("post_prompt", postPrompt);
    if (systemPrompt != null) map.put("system_prompt", systemPrompt);
    if (consolidate) map.put("consolidate", true);
    if (fullReset) map.put("full_reset", true);
    if (userPrompt != null) map.put("user_prompt", userPrompt);
    if (isolated) map.put("isolated", true);

    // Context prompt
    if (!promptSections.isEmpty()) {
      map.put("pom", promptSections);
    } else if (promptText != null) {
      map.put("prompt", promptText);
    }

    if (enterFillers != null) map.put("enter_fillers", enterFillers);
    if (exitFillers != null) map.put("exit_fillers", exitFillers);
    if (history != null) map.put("history", history);

    return map;
  }
}
