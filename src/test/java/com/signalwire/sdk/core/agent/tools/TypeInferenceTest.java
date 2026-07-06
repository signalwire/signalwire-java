/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core.agent.tools;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ParameterSchema;
import com.signalwire.sdk.swaig.ToolHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TypeInference} — the Java analog of Python's {@code
 * signalwire.core.agent.tools.type_inference} (build a SWAIG parameter schema from a typed tool
 * handler; wrap a typed handler for the standard calling convention).
 */
class TypeInferenceTest {

  @Test
  void inferSchemaFromTypedParamsBuilder() {
    // Build the typed tool's parameter schema via the typed-params builder,
    // then infer the (parameters, required, description, isTyped, hasRawData)
    // tuple from it — the static-port analog of Python reflecting a handler.
    Map<String, Object> schema =
        ParameterSchema.builder()
            .string("service", "The service to book")
            .string("date", "YYYY-MM-DD")
            .integer("guests", "Party size")
            .required("service", "date")
            .build();

    TypeInference.InferredSchema inferred = TypeInference.inferSchema(schema, "Book a service");

    assertTrue(inferred.isTyped(), "a named-param schema is a typed tool");
    assertFalse(inferred.hasRawData());
    assertEquals("Book a service", inferred.description());
    assertEquals(List.of("service", "date"), inferred.required());

    Map<String, Map<String, Object>> params = inferred.parameters();
    assertEquals(3, params.size());
    assertEquals("string", params.get("service").get("type"));
    assertEquals("The service to book", params.get("service").get("description"));
    assertEquals("integer", params.get("guests").get("type"));
  }

  @Test
  void inferSchemaZeroParamTypedTool() {
    TypeInference.InferredSchema inferred = TypeInference.inferSchema(null, "No-arg tool");
    assertTrue(inferred.isTyped());
    assertFalse(inferred.hasRawData());
    assertTrue(inferred.parameters().isEmpty());
    assertTrue(inferred.required().isEmpty());
    assertEquals("No-arg tool", inferred.description());
  }

  @Test
  void inferSchemaFlagsRawDataChannel() {
    // A raw_data property is the SWAIG raw-payload channel: excluded from the
    // schema and reported via hasRawData (parity with Python's infer_schema).
    Map<String, Object> schema =
        ParameterSchema.builder()
            .string("query", "The query")
            .string("raw_data", "raw payload channel")
            .required("query")
            .build();

    TypeInference.InferredSchema inferred = TypeInference.inferSchema(schema, null);
    assertTrue(inferred.hasRawData());
    assertEquals(1, inferred.parameters().size());
    assertTrue(inferred.parameters().containsKey("query"));
    assertFalse(inferred.parameters().containsKey("raw_data"));
    assertEquals(List.of("query"), inferred.required());
  }

  @Test
  void createTypedHandlerWrapperPassesRawDataOnlyWhenDeclared() {
    // With hasRawData=true the wrapped handler receives the raw payload.
    ToolHandler base =
        (args, rawData) ->
            new FunctionResult("raw=" + (rawData == null ? "null" : rawData.get("call_id")));

    ToolHandler withRaw = TypeInference.createTypedHandlerWrapper(base, true);
    FunctionResult r1 = withRaw.handle(Map.of(), Map.of("call_id", "abc"));
    assertEquals("raw=abc", r1.getResponse());

    // With hasRawData=false the raw payload is dropped (handler sees null).
    ToolHandler withoutRaw = TypeInference.createTypedHandlerWrapper(base, false);
    FunctionResult r2 = withoutRaw.handle(Map.of(), Map.of("call_id", "abc"));
    assertEquals("raw=null", r2.getResponse());
  }
}
