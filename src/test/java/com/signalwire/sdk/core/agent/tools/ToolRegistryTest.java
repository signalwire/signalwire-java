/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core.agent.tools;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.SWAIGFunction;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Tests for {@link ToolRegistry}. */
class ToolRegistryTest {

  @Test
  @SuppressWarnings("unchecked")
  void testDefineToolWireShape() {
    ToolRegistry reg = new ToolRegistry();
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("city", Map.of("type", "string"));
    Map<String, Object> def = reg.defineTool("get_weather", "Get weather", props, null);

    assertEquals("get_weather", def.get("function"));
    assertEquals("Get weather", def.get("description"));
    Map<String, Object> params = (Map<String, Object>) def.get("parameters");
    assertEquals("object", params.get("type"));
    assertEquals(props, params.get("properties"));
    // defineTool default secure=true (Python default).
    assertEquals(true, def.get("secure"));
  }

  @Test
  void testDefineToolStoresAndRetrieves() {
    ToolRegistry reg = new ToolRegistry();
    reg.defineTool("t", "d", new LinkedHashMap<>(), null);
    assertTrue(reg.hasFunction("t"));
    assertNotNull(reg.getFunction("t"));
    assertNull(reg.getFunction("missing"));
    assertEquals(1, reg.getAllFunctions().size());
  }

  @Test
  void testDefineToolRejectsDuplicate() {
    ToolRegistry reg = new ToolRegistry();
    reg.defineTool("dup", "d", new LinkedHashMap<>(), null);
    assertThrows(
        IllegalArgumentException.class,
        () -> reg.defineTool("dup", "d2", new LinkedHashMap<>(), null));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testDefineToolOptionalFieldsAndRequired() {
    ToolRegistry reg = new ToolRegistry();
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("city", Map.of("type", "string"));
    Map<String, Object> fillers = Map.of("en-US", List.of("one sec"));
    Map<String, Object> def =
        reg.defineTool(
            "t",
            "d",
            props,
            null,
            false,
            fillers,
            "https://wait.mp3",
            3,
            "https://hook",
            List.of("city"),
            true,
            Map.of("meta_data_token", "abc"));

    assertEquals(false, def.get("secure"));
    assertEquals(fillers, def.get("fillers"));
    assertEquals("https://wait.mp3", def.get("wait_file"));
    assertEquals(3, def.get("wait_file_loops"));
    assertEquals("https://hook", def.get("webhook_url"));
    assertEquals(true, def.get("is_typed_handler"));
    assertEquals("abc", def.get("meta_data_token"));
    Map<String, Object> params = (Map<String, Object>) def.get("parameters");
    assertEquals(List.of("city"), params.get("required"));
  }

  @Test
  void testDefineToolStoresHandlerWhenPresent() {
    ToolRegistry reg = new ToolRegistry();
    SWAIGFunction.Handler handler = (args, raw) -> new FunctionResult("ok");
    Map<String, Object> def =
        reg.defineTool(
            "t",
            "d",
            new LinkedHashMap<>(),
            handler,
            true,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
    assertSame(handler, def.get("handler"));
  }

  @Test
  void testRegisterSwaigFunction() {
    ToolRegistry reg = new ToolRegistry();
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("function", "data_map_tool");
    raw.put("description", "server-side");
    Map<String, Object> stored = reg.registerSwaigFunction(raw);
    assertTrue(reg.hasFunction("data_map_tool"));
    assertEquals("server-side", stored.get("description"));
  }

  @Test
  void testRegisterSwaigFunctionRequiresFunctionField() {
    ToolRegistry reg = new ToolRegistry();
    assertThrows(
        IllegalArgumentException.class,
        () -> reg.registerSwaigFunction(Map.of("description", "no name")));
  }

  @Test
  void testRegisterSwaigFunctionRejectsDuplicate() {
    ToolRegistry reg = new ToolRegistry();
    reg.registerSwaigFunction(new LinkedHashMap<>(Map.of("function", "x")));
    assertThrows(
        IllegalArgumentException.class,
        () -> reg.registerSwaigFunction(new LinkedHashMap<>(Map.of("function", "x"))));
  }

  @Test
  void testRemoveFunction() {
    ToolRegistry reg = new ToolRegistry();
    reg.defineTool("t", "d", new LinkedHashMap<>(), null);
    assertTrue(reg.removeFunction("t"));
    assertFalse(reg.hasFunction("t"));
    assertFalse(reg.removeFunction("t"));
  }

  @Test
  void testGetAllFunctionsIsCopy() {
    ToolRegistry reg = new ToolRegistry();
    reg.defineTool("t", "d", new LinkedHashMap<>(), null);
    Map<String, Map<String, Object>> all = reg.getAllFunctions();
    all.clear();
    assertTrue(reg.hasFunction("t"), "getAllFunctions must return a copy");
  }

  @Test
  void testRegisterClassDecoratedToolsNoAgentNoOp() {
    ToolRegistry reg = new ToolRegistry();
    reg.registerClassDecoratedTools();
    assertTrue(reg.getAllFunctions().isEmpty());
  }

  @Test
  void testRegisterClassDecoratedToolsScansAnnotatedMethods() {
    AnnotatedAgent agent = new AnnotatedAgent();
    ToolRegistry reg = new ToolRegistry(agent);
    reg.registerClassDecoratedTools();

    assertTrue(reg.hasFunction("say_hi"));
    Map<String, Object> def = reg.getFunction("say_hi");
    assertEquals("Greet the caller", def.get("description"));
    // Un-annotated methods are not registered.
    assertFalse(reg.hasFunction("notATool"));
  }

  @Test
  void testRegisterClassDecoratedToolsInvokesMethod() {
    AnnotatedAgent agent = new AnnotatedAgent();
    ToolRegistry reg = new ToolRegistry(agent);
    reg.registerClassDecoratedTools();
    SWAIGFunction.Handler handler =
        (SWAIGFunction.Handler) reg.getFunction("say_hi").get("handler");
    Object result = handler.apply(Map.of("name", "Ann"), Map.of());
    assertEquals("hi Ann", ((FunctionResult) result).getResponse());
  }

  /** Fixture agent with an annotated tool method (Java analog of Python's @AgentBase.tool). */
  public static class AnnotatedAgent {
    @ToolRegistry.Tool(name = "say_hi", description = "Greet the caller")
    public FunctionResult sayHi(Map<String, Object> args, Map<String, Object> rawData) {
      return new FunctionResult("hi " + args.get("name"));
    }

    public void notATool() {}
  }
}
