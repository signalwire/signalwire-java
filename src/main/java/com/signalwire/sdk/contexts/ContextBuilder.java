/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.contexts;

import java.util.*;

/**
 * Builder for multi-step, multi-context AI agent workflows.
 *
 * <p>A ContextBuilder owns one or more {@link Context}s; each context owns an
 * ordered list of {@link Step}s. Only one context and one step is active at a
 * time. Per chat turn, the runtime injects the current step's instructions as
 * a system message, then asks the LLM for a response.
 *
 * <h2>Native tools auto-injected by the runtime</h2>
 *
 * <p>When a step (or its enclosing context) declares valid_steps or
 * valid_contexts, the runtime auto-injects two native tools so the model can
 * navigate the flow:
 * <ul>
 *   <li>{@code next_step(step: enum)}        — present when valid_steps is set</li>
 *   <li>{@code change_context(context: enum)} — present when valid_contexts is set</li>
 * </ul>
 *
 * <p>A third native tool — {@code gather_submit} — is injected during
 * gather_info questioning. These three names are <b>reserved</b>:
 * {@link #validate()} rejects any agent that defines a SWAIG tool with one of
 * these names. See {@link #RESERVED_NATIVE_TOOL_NAMES}.
 *
 * <h2>Function whitelisting (Step.setFunctions)</h2>
 *
 * <p>Each step may declare a functions whitelist. The whitelist is applied
 * in-memory at the start of each LLM turn. CRITICALLY: if a step does NOT
 * declare a functions field, it INHERITS the previous step's active set.
 * See {@link Step#setFunctions(Object)} for details and examples.
 */
public class ContextBuilder {

    static final int MAX_CONTEXTS = 50;

    /**
     * Reserved tool names auto-injected by the runtime when contexts/steps
     * are in use. User-defined SWAIG tools must not collide with these names.
     *
     * <ul>
     *   <li>{@code next_step} / {@code change_context} are injected when
     *       valid_steps or valid_contexts is set so the model can navigate
     *       the flow.</li>
     *   <li>{@code gather_submit} is injected while a step's gather_info is
     *       collecting answers.</li>
     * </ul>
     */
    public static final Set<String> RESERVED_NATIVE_TOOL_NAMES =
            Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                    "next_step", "change_context", "gather_submit")));

    private final Map<String, Context> contexts;
    private final List<String> contextOrder;
    /**
     * Optional supplier returning the names of registered SWAIG tools, used
     * by {@link #validate()} to detect collisions with reserved native names.
     * Wired by {@code AgentBase.defineContexts()}.
     */
    private java.util.function.Supplier<Collection<String>> toolNameSupplier;

    public ContextBuilder() {
        this.contexts = new LinkedHashMap<>();
        this.contextOrder = new ArrayList<>();
    }

    /**
     * Attach a supplier that returns registered SWAIG tool names so
     * {@link #validate()} can check for collisions with
     * {@link #RESERVED_NATIVE_TOOL_NAMES}. Called internally by
     * {@code AgentBase.defineContexts()}.
     *
     * @param supplier returns the current set of registered tool names.
     * @return this builder for chaining.
     */
    public ContextBuilder attachToolNameSupplier(java.util.function.Supplier<Collection<String>> supplier) {
        this.toolNameSupplier = supplier;
        return this;
    }

    /**
     * Add a new context.
     */
    public Context addContext(String name) {
        if (contexts.containsKey(name)) {
            throw new IllegalStateException("Context '" + name + "' already exists");
        }
        if (contexts.size() >= MAX_CONTEXTS) {
            throw new IllegalStateException("Maximum number of contexts (" + MAX_CONTEXTS + ") exceeded");
        }
        Context context = new Context(name);
        contexts.put(name, context);
        contextOrder.add(name);
        return context;
    }

    /**
     * Get an existing context by name.
     */
    public Context getContext(String name) {
        return contexts.get(name);
    }

    public boolean isEmpty() {
        return contexts.isEmpty();
    }

    /**
     * Validate the contexts configuration.
     *
     * @throws IllegalStateException if validation fails
     */
    public void validate() {
        if (contexts.isEmpty()) {
            throw new IllegalStateException("At least one context must be defined");
        }

        // Single context must be named "default"
        if (contexts.size() == 1) {
            String contextName = contexts.keySet().iterator().next();
            if (!"default".equals(contextName)) {
                throw new IllegalStateException("When using a single context, it must be named 'default'");
            }
        }

        // Validate each context has at least one step
        for (var entry : contexts.entrySet()) {
            String contextName = entry.getKey();
            Context context = entry.getValue();
            if (context.getSteps().isEmpty()) {
                throw new IllegalStateException("Context '" + contextName + "' must have at least one step");
            }
        }

        // Validate step references in valid_steps
        for (var ctxEntry : contexts.entrySet()) {
            String contextName = ctxEntry.getKey();
            Context context = ctxEntry.getValue();
            for (var stepEntry : context.getSteps().entrySet()) {
                String stepName = stepEntry.getKey();
                Step step = stepEntry.getValue();
                if (step.getValidSteps() != null) {
                    for (String validStep : step.getValidSteps()) {
                        if (!"next".equals(validStep) && !context.getSteps().containsKey(validStep)) {
                            throw new IllegalStateException(
                                    "Step '" + stepName + "' in context '" + contextName
                                            + "' references unknown step '" + validStep + "'");
                        }
                    }
                }
            }
        }

        // Validate context references in valid_contexts (context-level)
        for (var ctxEntry : contexts.entrySet()) {
            String contextName = ctxEntry.getKey();
            Context context = ctxEntry.getValue();
            if (context.getValidContexts() != null) {
                for (String validCtx : context.getValidContexts()) {
                    if (!contexts.containsKey(validCtx)) {
                        throw new IllegalStateException(
                                "Context '" + contextName + "' references unknown context '" + validCtx + "'");
                    }
                }
            }
        }

        // Validate context references in valid_contexts (step-level)
        for (var ctxEntry : contexts.entrySet()) {
            String contextName = ctxEntry.getKey();
            Context context = ctxEntry.getValue();
            for (var stepEntry : context.getSteps().entrySet()) {
                String stepName = stepEntry.getKey();
                Step step = stepEntry.getValue();
                if (step.getValidContexts() != null) {
                    for (String validCtx : step.getValidContexts()) {
                        if (!contexts.containsKey(validCtx)) {
                            throw new IllegalStateException(
                                    "Step '" + stepName + "' in context '" + contextName
                                            + "' references unknown context '" + validCtx + "'");
                        }
                    }
                }
            }
        }

        // Validate gather_info configurations
        for (var ctxEntry : contexts.entrySet()) {
            String contextName = ctxEntry.getKey();
            Context context = ctxEntry.getValue();
            for (var stepEntry : context.getSteps().entrySet()) {
                String stepName = stepEntry.getKey();
                Step step = stepEntry.getValue();
                GatherInfo gi = step.getGatherInfo();
                if (gi != null) {
                    if (gi.getQuestions().isEmpty()) {
                        throw new IllegalStateException(
                                "Step '" + stepName + "' in context '" + contextName
                                        + "' has gather_info with no questions");
                    }
                    // Check for duplicate keys
                    Set<String> seenKeys = new HashSet<>();
                    for (GatherQuestion q : gi.getQuestions()) {
                        if (!seenKeys.add(q.getKey())) {
                            throw new IllegalStateException(
                                    "Step '" + stepName + "' in context '" + contextName
                                            + "' has duplicate gather_info question key '" + q.getKey() + "'");
                        }
                    }
                    // Validate completion_action
                    String action = gi.getCompletionAction();
                    if (action != null) {
                        if ("next_step".equals(action)) {
                            int stepIdx = context.getStepOrder().indexOf(stepName);
                            if (stepIdx >= context.getStepOrder().size() - 1) {
                                throw new IllegalStateException(
                                        "Step '" + stepName + "' in context '" + contextName
                                                + "' has gather_info completion_action='next_step' "
                                                + "but it is the last step in the context. Either "
                                                + "(1) add another step after '" + stepName + "', "
                                                + "(2) set completion_action to the name of an "
                                                + "existing step in this context to jump to it, or "
                                                + "(3) set completion_action=null (default) to stay "
                                                + "in '" + stepName + "' after gathering completes.");
                            }
                        } else if (!context.getSteps().containsKey(action)) {
                            List<String> available = new ArrayList<>(context.getSteps().keySet());
                            Collections.sort(available);
                            throw new IllegalStateException(
                                    "Step '" + stepName + "' in context '" + contextName
                                            + "' has gather_info completion_action='" + action
                                            + "' but '" + action + "' is not a step in this context. "
                                            + "Valid options: 'next_step' (advance to the next "
                                            + "sequential step), null (stay in the current step), "
                                            + "or one of " + available + ".");
                        }
                    }
                }
            }
        }

        // Validate that user-defined tools do not collide with reserved native
        // tool names. The runtime auto-injects next_step / change_context /
        // gather_submit when contexts/steps are present, so user tools sharing
        // those names would never be called.
        if (toolNameSupplier != null) {
            Collection<String> registered = toolNameSupplier.get();
            if (registered != null) {
                List<String> colliding = new ArrayList<>();
                for (String name : registered) {
                    if (RESERVED_NATIVE_TOOL_NAMES.contains(name)) {
                        colliding.add(name);
                    }
                }
                if (!colliding.isEmpty()) {
                    Collections.sort(colliding);
                    List<String> reserved = new ArrayList<>(RESERVED_NATIVE_TOOL_NAMES);
                    Collections.sort(reserved);
                    throw new IllegalStateException(
                            "Tool name(s) " + colliding + " collide with reserved "
                                    + "native tools auto-injected by contexts/steps. "
                                    + "The names " + reserved + " are reserved and "
                                    + "cannot be used for user-defined SWAIG tools "
                                    + "when contexts/steps are in use. Rename your "
                                    + "tool(s) to avoid the collision.");
                }
            }
        }
    }

    /**
     * Convert all contexts to a Map for SWML generation.
     * Validates before converting.
     */
    public Map<String, Object> toMap() {
        validate();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : contextOrder) {
            result.put(name, contexts.get(name).toMap());
        }
        return result;
    }
}
