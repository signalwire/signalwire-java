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
 * <p>Parity with the Python reference module {@code signalwire.core.agent.tools.type_inference}
 * ({@code infer_schema} / {@code create_typed_handler_wrapper}). Where Python inspects a handler's
 * {@code inspect.signature} + type hints at runtime (and Ruby/.NET reflect the delegate's parameter
 * list), Java erases lambda parameter names and types at runtime, so the typed-parameter contract
 * is supplied EXPLICITLY via the {@link com.signalwire.sdk.swaig.ParameterSchema} typed-params
 * builder — the idiomatic "typed tool" declaration in this port. {@link #inferSchema} decomposes
 * that built schema into the same {@code (parameters, required, description, isTyped, hasRawData)}
 * tuple Python's {@code infer_schema} returns; the schema is BUILT from the typed params-builder
 * rather than reflected off the callable, which is the static-port idiom (mirrors .NET/Ruby taking
 * a {@code types} override map and building from it).
 *
 * <p>Java has no module-level free functions; the two capabilities are hosted as static methods
 * here and projected onto the Python module-level names
 *
 * <ul>
 *   <li>{@code signalwire.core.agent.tools.type_inference.infer_schema}
 *   <li>{@code signalwire.core.agent.tools.type_inference.create_typed_handler_wrapper}
 * </ul>
 *
 * via {@code scripts/enumerate_signatures.py} FREE_FUNCTION_PROJECTIONS (mirrors the {@code
 * url_validator.validate_url} / {@code security_utils} host precedent). The reflected native
 * signatures (a {@code ParameterSchema}/{@code ToolHandler} in, a record out) are recorded as the
 * canonical oracle shapes via FREE_FUNCTION_SIGNATURE_OVERRIDES.
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
   * <p>Parity with Python's {@code infer_schema}: an empty/absent schema is a valid zero-param
   * typed tool ({@code isTyped=true}, no parameters); a schema with properties is typed and its
   * property maps + required list are surfaced. A {@code raw_data} property is treated as the SWAIG
   * raw-payload channel, excluded from the schema, and flagged in {@code hasRawData}.
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
