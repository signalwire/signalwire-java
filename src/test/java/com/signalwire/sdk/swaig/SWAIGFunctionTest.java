/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swaig;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

/** Tests for {@link SWAIGFunction} — call, execute, validateArgs, toSwaig, wire keys. */
class SWAIGFunctionTest {

  private SWAIGFunction.Builder base() {
    return SWAIGFunction.builder()
        .name("get_weather")
        .description("Get the weather")
        .handler((args, raw) -> new FunctionResult("ok"));
  }

  @Test
  void testBuilderRequiresNameHandlerDescription() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SWAIGFunction.builder().description("d").handler((a, r) -> null).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> SWAIGFunction.builder().name("n").handler((a, r) -> null).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> SWAIGFunction.builder().name("n").description("d").build());
  }

  @Test
  void testCallInvokesHandler() {
    List<String> seen = new ArrayList<>();
    SWAIGFunction fn =
        base()
            .handler(
                (args, raw) -> {
                  seen.add("called");
                  return args.get("city");
                })
            .build();
    Object result = fn.call(Map.of("city", "NYC"), Map.of());
    assertEquals("NYC", result);
    assertEquals(List.of("called"), seen);
  }

  @Test
  void testExecuteCoercesFunctionResult() {
    SWAIGFunction fn = base().handler((args, raw) -> new FunctionResult("hi there")).build();
    Map<String, Object> out = fn.execute(Map.of(), null);
    assertEquals("hi there", out.get("response"));
  }

  @Test
  void testExecuteCoercesStringReturn() {
    SWAIGFunction fn = base().handler((args, raw) -> "plain string").build();
    Map<String, Object> out = fn.execute(Map.of(), Map.of());
    assertEquals("plain string", out.get("response"));
  }

  @Test
  void testExecutePassesThroughDictWithResponse() {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("response", "custom");
    raw.put("action", List.of());
    SWAIGFunction fn = base().handler((args, r) -> raw).build();
    Map<String, Object> out = fn.execute(Map.of(), Map.of());
    assertEquals("custom", out.get("response"));
    assertSame(raw, out);
  }

  @Test
  void testExecuteDictWithoutResponseBecomesGenericSuccess() {
    SWAIGFunction fn = base().handler((args, r) -> Map.of("foo", "bar")).build();
    Map<String, Object> out = fn.execute(Map.of(), Map.of());
    assertEquals("Function completed successfully", out.get("response"));
  }

  @Test
  void testExecuteCatchesExceptionReturnsGenericError() {
    SWAIGFunction fn =
        base()
            .handler(
                (args, r) -> {
                  throw new RuntimeException("boom");
                })
            .build();
    Map<String, Object> out = fn.execute(Map.of(), Map.of());
    assertEquals(SWAIGFunction.EXECUTE_ERROR_RESPONSE, out.get("response"));
  }

  @Test
  void testValidateArgsNoPropertiesPasses() {
    SWAIGFunction fn = base().build();
    SWAIGFunction.ValidationResult r = fn.validateArgs(Map.of("anything", 1));
    assertTrue(r.isValid());
    assertTrue(r.getErrors().isEmpty());
  }

  @Test
  void testValidateArgsMissingRequired() {
    Map<String, Object> params = new LinkedHashMap<>();
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("city", Map.of("type", "string"));
    params.put("type", "object");
    params.put("properties", props);
    params.put("required", List.of("city"));
    SWAIGFunction fn = base().parameters(params).build();

    SWAIGFunction.ValidationResult r = fn.validateArgs(new LinkedHashMap<>());
    assertFalse(r.isValid());
    assertEquals(1, r.getErrors().size());
    assertTrue(r.getErrors().get(0).contains("city"));
  }

  @Test
  void testValidateArgsTypeMismatch() {
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("age", Map.of("type", "integer"));
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("type", "object");
    params.put("properties", props);
    SWAIGFunction fn = base().parameters(params).build();

    SWAIGFunction.ValidationResult r = fn.validateArgs(Map.of("age", "not-an-int"));
    assertFalse(r.isValid());
    assertTrue(r.getErrors().get(0).contains("integer"));
  }

  @Test
  void testValidateArgsTypeMatchPasses() {
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("age", Map.of("type", "integer"));
    props.put("name", Map.of("type", "string"));
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("type", "object");
    params.put("properties", props);
    SWAIGFunction fn = base().parameters(params).build();

    SWAIGFunction.ValidationResult r = fn.validateArgs(Map.of("age", 30, "name", "Ann"));
    assertTrue(r.isValid());
  }

  @Test
  void testToSwaigWireKeys() {
    SWAIGFunction fn = base().build();
    Map<String, Object> swaig = fn.toSwaig("https://example.com");
    assertEquals("get_weather", swaig.get("function"));
    assertEquals("Get the weather", swaig.get("description"));
    assertEquals("https://example.com/swaig", swaig.get("web_hook_url"));
    @SuppressWarnings("unchecked")
    Map<String, Object> params = (Map<String, Object>) swaig.get("parameters");
    assertEquals("object", params.get("type"));
    assertTrue(params.containsKey("properties"));
  }

  @Test
  void testToSwaigWithTokenAndCallId() {
    SWAIGFunction fn = base().build();
    Map<String, Object> swaig = fn.toSwaig("https://example.com", "T", "C123", true);
    assertEquals("https://example.com/swaig?token=T&call_id=C123", swaig.get("web_hook_url"));
  }

  @Test
  void testToSwaigIncludesFillersAndExtraFields() {
    Map<String, Object> fillers = Map.of("en-US", List.of("one moment"));
    SWAIGFunction fn =
        base().fillers(fillers).extraSwaigFields(Map.of("meta_data_token", "abc")).build();
    Map<String, Object> swaig = fn.toSwaig("https://example.com");
    assertEquals(fillers, swaig.get("fillers"));
    assertEquals("abc", swaig.get("meta_data_token"));
  }

  @Test
  void testEnsureParameterStructureWrapsLooseProps() {
    Map<String, Object> looseProps = new LinkedHashMap<>();
    looseProps.put("city", Map.of("type", "string"));
    SWAIGFunction fn = base().parameters(looseProps).required(List.of("city")).build();
    @SuppressWarnings("unchecked")
    Map<String, Object> params = (Map<String, Object>) fn.toSwaig("https://x").get("parameters");
    assertEquals("object", params.get("type"));
    assertEquals(looseProps, params.get("properties"));
    assertEquals(List.of("city"), params.get("required"));
  }

  @Test
  void testIsExternalWhenWebhookUrlSet() {
    assertFalse(base().build().isExternal());
    assertTrue(base().webhookUrl("https://hook").build().isExternal());
  }
}
