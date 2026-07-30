/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core.agent.tools;

import com.signalwire.sdk.swaig.ToolHandler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed-handler schema inference and typed-handler wrapping for SWAIG tools.
 *
 * <p>Java erases lambda parameter names and types at runtime, so a typed tool's parameter contract
 * cannot be reflected off the handler itself. It is instead supplied EXPLICITLY via the {@link
 * com.signalwire.sdk.swaig.ParameterSchema} typed-params builder — the idiomatic way to declare a
 * typed tool in this SDK. {@link #inferSchema} decomposes that built schema into its {@code
 * (parameters, required, description, isTyped, hasRawData)} components.
 *
 * <p>Both capabilities are exposed as static methods on this final utility class; it is never
 * instantiated.
 */
public final class TypeInference {

  private TypeInference() {
    // Static-only utility.
  }

  /**
   * The inferred-schema tuple returned by {@link #inferSchema}: {@code (parameters, required,
   * description, isTyped, hasRawData)}. Mirrors Python's {@code infer_schema} return contract.
   *
   * @param parameters name → JSON-Schema property map (string keys, per-property {type,
   *     description, …} maps).
   * @param required required parameter names, in declaration order.
   * @param description the tool description (or {@code null}).
   * @param isTyped {@code true} when the input declares named, typed parameters (i.e. it is not the
   *     old-style {@code (args, rawData)} untyped handler).
   * @param hasRawData {@code true} when the handler receives the raw SWAIG payload.
   */
  public record InferredSchema(
      Map<String, Map<String, Object>> parameters,
      List<String> required,
      String description,
      boolean isTyped,
      boolean hasRawData) {}

  /**
   * Decompose a typed-params-builder schema into the {@code (parameters, required, description,
   * isTyped, hasRawData)} tuple. The {@code schema} is the built {@link
   * com.signalwire.sdk.swaig.ParameterSchema} Map — the {@code {type, properties[, required]}}
   * envelope — from which the per-parameter property maps and the required list are read.
   *
   * <p>An empty or absent schema is a valid zero-param typed tool ({@code isTyped=true}, no
   * parameters); a schema with properties is typed and its property maps and required list are
   * surfaced. A {@code raw_data} property is treated as the SWAIG raw-payload channel, excluded
   * from the schema, and flagged in {@code hasRawData}.
   *
   * @param schema the built ParameterSchema envelope, or {@code null} for a zero-param typed tool.
   * @param description the tool description (or {@code null}).
   * @return the inferred-schema tuple.
   */
  @SuppressWarnings("unchecked")
  public static InferredSchema inferSchema(Map<String, Object> schema, String description) {
    Map<String, Map<String, Object>> parameters = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();

    if (schema == null || schema.isEmpty()) {
      // Zero-param typed tool.
      return new InferredSchema(parameters, required, description, true, false);
    }

    Object propsObj = schema.get("properties");
    Map<String, Object> props = propsObj instanceof Map ? (Map<String, Object>) propsObj : schema;

    boolean hasRawData = false;
    for (Map.Entry<String, Object> e : props.entrySet()) {
      String key = e.getKey();
      if ("raw_data".equals(key) || "rawData".equals(key)) {
        // Raw-payload channel — excluded from the schema, flagged instead.
        hasRawData = true;
        continue;
      }
      // Skip the envelope's own type/required/properties keys if a bare
      // properties map (not an envelope) was passed.
      if (props == schema && ("type".equals(key) || "required".equals(key))) {
        continue;
      }
      Map<String, Object> prop =
          e.getValue() instanceof Map
              ? new LinkedHashMap<>((Map<String, Object>) e.getValue())
              : new LinkedHashMap<>();
      parameters.put(key, prop);
    }

    Object req = schema.get("required");
    if (req instanceof List) {
      for (Object r : (List<Object>) req) {
        String name = String.valueOf(r);
        if (!"raw_data".equals(name) && !"rawData".equals(name)) {
          required.add(name);
        }
      }
    }

    return new InferredSchema(parameters, required, description, true, hasRawData);
  }

  /**
   * Wrap a typed handler so it can be invoked with the standard SWAIG calling convention {@code
   * (args, rawData)}. Mirrors Python's {@code create_typed_handler_wrapper}: the wrapper passes the
   * raw SWAIG payload to the wrapped handler only when it declared it ({@code hasRawData}),
   * otherwise it is dropped (the wrapped handler sees {@code null} raw data).
   *
   * @param func the typed handler.
   * @param hasRawData pass the raw SWAIG payload through when {@code true}.
   * @return a handler with the standard {@code (args, rawData)} calling convention.
   */
  public static ToolHandler createTypedHandlerWrapper(ToolHandler func, boolean hasRawData) {
    return (args, rawData) -> func.handle(args, hasRawData ? rawData : null);
  }
}
