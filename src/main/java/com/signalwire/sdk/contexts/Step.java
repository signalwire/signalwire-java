/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.contexts;

import java.util.*;

/**
 * Represents a single step within a context.
 * <p>
 * Steps can use either raw text or POM-style sections for their prompt content.
 * All setter methods return {@code this} for fluent chaining.
 */
public class Step {

    private final String name;
    private String text;
    private String stepCriteria;
    private Object functions; // String "none" or List<String>
    private List<String> validSteps;
    private List<String> validContexts;
    private final List<Map<String, Object>> sections;
    private GatherInfo gatherInfo;

    // Step behavior flags
    private boolean end;
    private boolean skipUserTurn;
    private boolean skipToNextStep;

    // Reset object for context switching from steps
    private String resetSystemPrompt;
    private String resetUserPrompt;
    private boolean resetConsolidate;
    private boolean resetFullReset;

    public Step(String name) {
        this.name = name;
        this.sections = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    /**
     * Set the step's prompt text directly.
     */
    public Step setText(String text) {
        if (!sections.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot use setText() when POM sections have been added. Use one approach or the other.");
        }
        this.text = text;
        return this;
    }

    /**
     * Add a POM section to the step.
     */
    public Step addSection(String title, String body) {
        if (text != null) {
            throw new IllegalStateException(
                    "Cannot add POM sections when setText() has been used. Use one approach or the other.");
        }
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", title);
        section.put("body", body);
        sections.add(section);
        return this;
    }

    /**
     * Add a POM section with bullet points.
     */
    public Step addBullets(String title, List<String> bullets) {
        if (text != null) {
            throw new IllegalStateException(
                    "Cannot add POM sections when setText() has been used. Use one approach or the other.");
        }
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", title);
        section.put("bullets", bullets);
        sections.add(section);
        return this;
    }

    public Step setStepCriteria(String criteria) {
        this.stepCriteria = criteria;
        return this;
    }

    /**
     * Set which non-internal functions are callable while this step is active.
     *
     * <p><b>IMPORTANT — inheritance behavior:</b> If you do NOT call this method,
     * the step inherits whichever function set was active on the previous step
     * (or the previous context's last step). The server-side runtime only
     * resets the active set when a step explicitly declares its {@code functions}
     * field. This is the most common source of bugs in multi-step agents:
     * forgetting {@code setFunctions()} on a later step lets the previous step's
     * tools leak through. Best practice is to call {@code setFunctions()}
     * explicitly on every step that should differ from the previous one.
     *
     * <p>Keep the per-step active set small: LLM tool selection accuracy
     * degrades noticeably past ~7-8 simultaneously-active tools per call.
     * Use per-step whitelisting to partition large tool collections.
     *
     * <p>Accepts:
     * <ul>
     *   <li>{@code List<String>} — whitelist of function names allowed in this
     *       step. Functions not in the list become inactive.</li>
     *   <li>{@code List.of()} (empty list) — explicit disable-all.</li>
     *   <li>The string {@code "none"} — synonym for an empty list.</li>
     * </ul>
     *
     * <p>Internal functions (e.g. {@code gather_submit}, hangup hook) are
     * ALWAYS protected and cannot be deactivated by this whitelist. The native
     * navigation tools {@code next_step} and {@code change_context} are
     * injected automatically when {@code setValidSteps}/{@code setValidContexts}
     * is used; they are not affected by this list and do not need to appear in
     * it.
     *
     * @param functions a {@code List<String>} whitelist, an empty list, or
     *     the string {@code "none"}.
     * @return this step for chaining.
     */
    public Step setFunctions(Object functions) {
        this.functions = functions;
        return this;
    }

    public Step setValidSteps(List<String> steps) {
        this.validSteps = steps;
        return this;
    }

    public Step setValidContexts(List<String> contexts) {
        this.validContexts = contexts;
        return this;
    }

    /**
     * Mark this step as terminal for the step flow.
     *
     * <p><b>IMPORTANT:</b> {@code end=true} does NOT end the conversation or
     * hang up the call. It exits step mode entirely after this step executes
     * — clearing the steps list, current step index, valid_steps, and
     * valid_contexts. The agent keeps running, but operates only under the
     * base system prompt and the context-level prompt; no more step
     * instructions are injected and no more {@code next_step} tool is offered.
     *
     * <p>To actually end the call, call a hangup tool or define a
     * hangup hook.
     *
     * @param end true to exit step mode after this step.
     * @return this step for chaining.
     */
    public Step setEnd(boolean end) {
        this.end = end;
        return this;
    }

    public Step setSkipUserTurn(boolean skip) {
        this.skipUserTurn = skip;
        return this;
    }

    public Step setSkipToNextStep(boolean skip) {
        this.skipToNextStep = skip;
        return this;
    }

    /**
     * Enable info gathering for this step.
     */
    public Step setGatherInfo(String outputKey, String completionAction, String prompt) {
        this.gatherInfo = new GatherInfo(outputKey, completionAction, prompt);
        return this;
    }

    /**
     * Add a question to this step's gather_info configuration.
     * {@link #setGatherInfo(String, String, String)} must be called first.
     *
     * <p><b>IMPORTANT — gather mode locks function access:</b> While the model
     * is asking gather questions, the runtime forcibly deactivates ALL of the
     * step's other functions. The only callable tools during a gather
     * question are:
     * <ul>
     *   <li>{@code gather_submit} (the native answer-submission tool)</li>
     *   <li>Whatever names you pass in this question's {@code functions}
     *       argument</li>
     * </ul>
     *
     * <p>{@code next_step} and {@code change_context} are also filtered out —
     * the model cannot navigate away until the gather completes. This is by
     * design: it forces a tight ask → submit → next-question loop.
     *
     * <p>If a question needs to call out to a tool (e.g. validate an email,
     * geocode a ZIP), list that tool name in this question's {@code functions}
     * argument. Functions listed here are active ONLY for this question.
     */
    public Step addGatherQuestion(String key, String question, String type,
                                  boolean confirm, String prompt, List<String> functions) {
        if (gatherInfo == null) {
            throw new IllegalStateException("Must call setGatherInfo() before addGatherQuestion()");
        }
        gatherInfo.addQuestion(key, question, type, confirm, prompt, functions);
        return this;
    }

    public Step addGatherQuestion(String key, String question) {
        return addGatherQuestion(key, question, "string", false, null, null);
    }

    public Step clearSections() {
        sections.clear();
        text = null;
        return this;
    }

    public Step setResetSystemPrompt(String systemPrompt) {
        this.resetSystemPrompt = systemPrompt;
        return this;
    }

    public Step setResetUserPrompt(String userPrompt) {
        this.resetUserPrompt = userPrompt;
        return this;
    }

    public Step setResetConsolidate(boolean consolidate) {
        this.resetConsolidate = consolidate;
        return this;
    }

    public Step setResetFullReset(boolean fullReset) {
        this.resetFullReset = fullReset;
        return this;
    }

    // Package-private accessors for validation
    List<String> getValidSteps() {
        return validSteps;
    }

    List<String> getValidContexts() {
        return validContexts;
    }

    GatherInfo getGatherInfo() {
        return gatherInfo;
    }

    /**
     * Render the step's prompt text from either raw text or POM sections.
     */
    String renderText() {
        if (text != null) {
            return text;
        }
        if (sections.isEmpty()) {
            throw new IllegalStateException("Step '" + name + "' has no text or POM sections defined");
        }
        var sb = new StringBuilder();
        for (int i = 0; i < sections.size(); i++) {
            var section = sections.get(i);
            String title = (String) section.get("title");
            sb.append("## ").append(title).append('\n');
            if (section.containsKey("bullets")) {
                @SuppressWarnings("unchecked")
                List<String> bullets = (List<String>) section.get("bullets");
                for (String bullet : bullets) {
                    sb.append("- ").append(bullet).append('\n');
                }
            } else {
                sb.append(section.get("body")).append('\n');
            }
            if (i < sections.size() - 1) {
                sb.append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("text", renderText());

        if (stepCriteria != null) map.put("step_criteria", stepCriteria);
        if (functions != null) map.put("functions", functions);
        if (validSteps != null) map.put("valid_steps", validSteps);
        if (validContexts != null) map.put("valid_contexts", validContexts);
        if (end) map.put("end", true);
        if (skipUserTurn) map.put("skip_user_turn", true);
        if (skipToNextStep) map.put("skip_to_next_step", true);

        // Reset object
        Map<String, Object> resetObj = new LinkedHashMap<>();
        if (resetSystemPrompt != null) resetObj.put("system_prompt", resetSystemPrompt);
        if (resetUserPrompt != null) resetObj.put("user_prompt", resetUserPrompt);
        if (resetConsolidate) resetObj.put("consolidate", true);
        if (resetFullReset) resetObj.put("full_reset", true);
        if (!resetObj.isEmpty()) map.put("reset", resetObj);

        if (gatherInfo != null) map.put("gather_info", gatherInfo.toMap());

        return map;
    }
}
