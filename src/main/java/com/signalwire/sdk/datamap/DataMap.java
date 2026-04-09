/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.datamap;

import com.signalwire.sdk.swaig.FunctionResult;

import java.util.*;

/**
 * Fluent builder for creating SWAIG data_map configurations.
 * <p>
 * DataMap tools execute on SignalWire servers without requiring webhook endpoints.
 * Supports variable expansion: {@code ${args.param}}, {@code ${response.field}},
 * {@code ${global_data.key}}, {@code ${foreach.item}}.
 * <p>
 * All methods return {@code this} for fluent chaining.
 */
public class DataMap {

    private final String functionName;
    private String purpose;
    private final Map<String, Object> paramProperties;
    private final List<String> requiredParams;
    private final List<Map<String, Object>> expressions;
    private final List<Map<String, Object>> webhooks;
    private Map<String, Object> fallbackOutput;
    private List<String> topLevelErrorKeys;

    public DataMap(String functionName) {
        this.functionName = functionName;
        this.purpose = "";
        this.paramProperties = new LinkedHashMap<>();
        this.requiredParams = new ArrayList<>();
        this.expressions = new ArrayList<>();
        this.webhooks = new ArrayList<>();
        this.topLevelErrorKeys = new ArrayList<>();
    }

    public String getName() {
        return functionName;
    }

    /**
     * Set the LLM-facing tool description — this is PROMPT ENGINEERING,
     * not developer documentation.
     *
     * <p>The description string is rendered into the OpenAI tool schema
     * {@code description} field on every LLM turn. The model reads it to
     * decide WHEN to call this tool. A vague {@code purpose()} is the #1
     * cause of "the model has the right tool but doesn't call it" failures
     * with data-map tools.
     *
     * <h3>Bad vs good</h3>
     * <pre>{@code
     * BAD : .purpose("weather api")
     * GOOD: .purpose("Get the current weather conditions and forecast "
     *              + "for a specific city. Use this whenever the user "
     *              + "asks about weather, temperature, rain, or similar "
     *              + "conditions in a named location.")
     * }</pre>
     *
     * @param description prompt-engineering description of when to call this tool.
     */
    public DataMap purpose(String description) {
        this.purpose = description;
        return this;
    }

    /**
     * Alias for {@link #purpose(String)}; sets the LLM-facing tool
     * description. This string is read by the model to decide WHEN to
     * call this tool. See {@link #purpose(String)} for bad-vs-good
     * examples.
     */
    public DataMap description(String description) {
        return purpose(description);
    }

    /**
     * Add a parameter to this data-map tool — the {@code description} is
     * LLM-FACING.
     *
     * <p>Each parameter description is rendered into the OpenAI tool schema
     * under {@code parameters.properties.<name>.description} and sent to
     * the model. The model uses it to decide HOW to fill in the argument
     * from user speech. It is prompt engineering, not developer FYI.
     *
     * <h3>Bad vs good</h3>
     * <pre>{@code
     * BAD : .parameter("city", "string", "the city", true, null)
     * GOOD: .parameter("city", "string",
     *           "The name of the city to get weather for, e.g. "
     *           + "\"San Francisco\". Ask the user if they did not "
     *           + "provide one. Include the state or country if the "
     *           + "city name is ambiguous.", true, null)
     * }</pre>
     */
    public DataMap parameter(String name, String paramType, String description,
                             boolean required, List<String> enumValues) {
        Map<String, Object> paramDef = new LinkedHashMap<>();
        paramDef.put("type", paramType);
        paramDef.put("description", description);
        if (enumValues != null && !enumValues.isEmpty()) {
            paramDef.put("enum", enumValues);
        }
        paramProperties.put(name, paramDef);
        if (required && !requiredParams.contains(name)) {
            requiredParams.add(name);
        }
        return this;
    }

    public DataMap parameter(String name, String paramType, String description, boolean required) {
        return parameter(name, paramType, description, required, null);
    }

    public DataMap parameter(String name, String paramType, String description) {
        return parameter(name, paramType, description, false, null);
    }

    /**
     * Add an expression pattern for pattern-based responses.
     */
    public DataMap expression(String testValue, String pattern, FunctionResult output,
                              FunctionResult nomatchOutput) {
        Map<String, Object> exprDef = new LinkedHashMap<>();
        exprDef.put("string", testValue);
        exprDef.put("pattern", pattern);
        exprDef.put("output", output.toMap());
        if (nomatchOutput != null) {
            exprDef.put("nomatch-output", nomatchOutput.toMap());
        }
        expressions.add(exprDef);
        return this;
    }

    public DataMap expression(String testValue, String pattern, FunctionResult output) {
        return expression(testValue, pattern, output, null);
    }

    /**
     * Add a webhook API call.
     */
    public DataMap webhook(String method, String url, Map<String, String> headers) {
        Map<String, Object> webhookDef = new LinkedHashMap<>();
        webhookDef.put("url", url);
        webhookDef.put("method", method.toUpperCase());
        if (headers != null && !headers.isEmpty()) {
            webhookDef.put("headers", headers);
        }
        webhooks.add(webhookDef);
        return this;
    }

    public DataMap webhook(String method, String url) {
        return webhook(method, url, null);
    }

    /**
     * Set request body for the last added webhook (POST/PUT requests).
     */
    public DataMap body(Map<String, Object> data) {
        if (webhooks.isEmpty()) {
            throw new IllegalStateException("Must add webhook before setting body");
        }
        webhooks.getLast().put("body", data);
        return this;
    }

    /**
     * Set request params for the last added webhook.
     */
    public DataMap params(Map<String, Object> data) {
        if (webhooks.isEmpty()) {
            throw new IllegalStateException("Must add webhook before setting params");
        }
        webhooks.getLast().put("params", data);
        return this;
    }

    /**
     * Process an array from the webhook response using foreach mechanism.
     */
    public DataMap foreach(Map<String, Object> foreachConfig) {
        if (webhooks.isEmpty()) {
            throw new IllegalStateException("Must add webhook before setting foreach");
        }
        webhooks.getLast().put("foreach", foreachConfig);
        return this;
    }

    /**
     * Set the output result for the most recent webhook.
     */
    public DataMap output(FunctionResult result) {
        if (webhooks.isEmpty()) {
            throw new IllegalStateException("Must add webhook before setting output");
        }
        webhooks.getLast().put("output", result.toMap());
        return this;
    }

    /**
     * Set a fallback output result at the top level (used when all webhooks fail).
     */
    public DataMap fallbackOutput(FunctionResult result) {
        this.fallbackOutput = result.toMap();
        return this;
    }

    /**
     * Set error keys for the most recent webhook (if webhooks exist) or top-level.
     */
    public DataMap errorKeys(List<String> keys) {
        if (!webhooks.isEmpty()) {
            webhooks.getLast().put("error_keys", keys);
        } else {
            this.topLevelErrorKeys = new ArrayList<>(keys);
        }
        return this;
    }

    /**
     * Set top-level error keys (applies to all webhooks).
     */
    public DataMap globalErrorKeys(List<String> keys) {
        this.topLevelErrorKeys = new ArrayList<>(keys);
        return this;
    }

    /**
     * Convert this DataMap to a SWAIG function definition.
     */
    public Map<String, Object> toSwaigFunction() {
        // Build parameter schema
        Map<String, Object> paramSchema = new LinkedHashMap<>();
        paramSchema.put("type", "object");
        paramSchema.put("properties",
                paramProperties.isEmpty() ? Map.of() : new LinkedHashMap<>(paramProperties));
        if (!requiredParams.isEmpty()) {
            paramSchema.put("required", new ArrayList<>(requiredParams));
        }

        // Build data_map structure
        Map<String, Object> dataMap = new LinkedHashMap<>();
        if (!expressions.isEmpty()) {
            dataMap.put("expressions", expressions);
        }
        if (!webhooks.isEmpty()) {
            dataMap.put("webhooks", webhooks);
        }
        if (fallbackOutput != null) {
            dataMap.put("output", fallbackOutput);
        }
        if (!topLevelErrorKeys.isEmpty()) {
            dataMap.put("error_keys", topLevelErrorKeys);
        }

        // Build final function definition
        Map<String, Object> functionDef = new LinkedHashMap<>();
        functionDef.put("function", functionName);
        functionDef.put("description", purpose.isEmpty() ? "Execute " + functionName : purpose);
        functionDef.put("parameters", paramSchema);
        functionDef.put("data_map", dataMap);

        return functionDef;
    }
}
