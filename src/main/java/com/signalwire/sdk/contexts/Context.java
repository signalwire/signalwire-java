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
 * <p>
 * All setter methods return {@code this} for fluent chaining.
 */
public class Context {

    static final int MAX_STEPS_PER_CONTEXT = 100;

    private final String name;
    private final Map<String, Step> steps;
    private final List<String> stepOrder;
    private List<String> validContexts;
    private List<String> validSteps;

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

    public Context(String name) {
        this.name = name;
        this.steps = new LinkedHashMap<>();
        this.stepOrder = new ArrayList<>();
        this.promptSections = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    /**
     * Add a new step to this context.
     */
    public Step addStep(String stepName) {
        if (steps.containsKey(stepName)) {
            throw new IllegalStateException("Step '" + stepName + "' already exists in context '" + name + "'");
        }
        if (steps.size() >= MAX_STEPS_PER_CONTEXT) {
            throw new IllegalStateException("Maximum steps per context (" + MAX_STEPS_PER_CONTEXT + ") exceeded");
        }
        Step step = new Step(stepName);
        steps.put(stepName, step);
        stepOrder.add(stepName);
        return step;
    }

    /**
     * Add a fully configured step in one call.
     */
    public Step addStep(String stepName, String task, List<String> bullets,
                        String criteria, Object functions, List<String> validSteps) {
        Step step = addStep(stepName);
        if (task != null) step.addSection("Task", task);
        if (bullets != null) step.addBullets("Process", bullets);
        if (criteria != null) step.setStepCriteria(criteria);
        if (functions != null) step.setFunctions(functions);
        if (validSteps != null) step.setValidSteps(validSteps);
        return step;
    }

    public Step getStep(String stepName) {
        return steps.get(stepName);
    }

    public Context removeStep(String stepName) {
        if (steps.containsKey(stepName)) {
            steps.remove(stepName);
            stepOrder.remove(stepName);
        }
        return this;
    }

    public Context moveStep(String stepName, int position) {
        if (!steps.containsKey(stepName)) {
            throw new IllegalStateException("Step '" + stepName + "' not found in context '" + name + "'");
        }
        stepOrder.remove(stepName);
        stepOrder.add(position, stepName);
        return this;
    }

    public Context setValidContexts(List<String> contexts) {
        this.validContexts = contexts;
        return this;
    }

    public Context setValidSteps(List<String> steps) {
        this.validSteps = steps;
        return this;
    }

    public Context setPostPrompt(String postPrompt) {
        this.postPrompt = postPrompt;
        return this;
    }

    public Context setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    public Context setConsolidate(boolean consolidate) {
        this.consolidate = consolidate;
        return this;
    }

    public Context setFullReset(boolean fullReset) {
        this.fullReset = fullReset;
        return this;
    }

    public Context setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
        return this;
    }

    /**
     * Mark this context as isolated — entering it wipes conversation history.
     *
     * <p>When {@code isolated=true} and the context is entered via
     * change_context, the runtime wipes the conversation array. The model
     * starts fresh with only the new context's system_prompt + step
     * instructions, with no memory of prior turns.
     *
     * <p><b>EXCEPTION — reset overrides the wipe:</b> If the context also has
     * a reset configuration (via {@link #setConsolidate(boolean)} or
     * {@link #setFullReset(boolean)}), the wipe is skipped in favor of the
     * reset behavior. Use reset with consolidate=true to summarize prior
     * history into a single message instead of dropping it entirely.
     *
     * <p>Use cases: switching to a sensitive billing flow that should not see
     * prior small-talk; handing off to a different agent persona; resetting
     * after a long off-topic detour.
     *
     * @param isolated true to wipe conversation history on context entry
     *     (subject to the reset exception above).
     * @return this context for chaining.
     */
    public Context setIsolated(boolean isolated) {
        this.isolated = isolated;
        return this;
    }

    public Context setPrompt(String prompt) {
        if (!promptSections.isEmpty()) {
            throw new IllegalStateException("Cannot use setPrompt() when POM sections have been added.");
        }
        this.promptText = prompt;
        return this;
    }

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

    public Context setEnterFillers(Map<String, List<String>> fillers) {
        if (fillers != null) {
            this.enterFillers = new LinkedHashMap<>(fillers);
        }
        return this;
    }

    public Context setExitFillers(Map<String, List<String>> fillers) {
        if (fillers != null) {
            this.exitFillers = new LinkedHashMap<>(fillers);
        }
        return this;
    }

    public Context addEnterFiller(String languageCode, List<String> fillers) {
        if (languageCode != null && fillers != null) {
            if (enterFillers == null) enterFillers = new LinkedHashMap<>();
            enterFillers.put(languageCode, fillers);
        }
        return this;
    }

    public Context addExitFiller(String languageCode, List<String> fillers) {
        if (languageCode != null && fillers != null) {
            if (exitFillers == null) exitFillers = new LinkedHashMap<>();
            exitFillers.put(languageCode, fillers);
        }
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

        return map;
    }
}
