/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.datamap.DataMap;
import com.signalwire.sdk.swaig.FunctionResult;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Tests for DataMap fluent builder. */
class DataMapTest {

  @Test
  void testBasicDataMap() {
    var dm =
        new DataMap("get_weather")
            .purpose("Get current weather")
            .parameter("city", "string", "City name", true)
            .webhook("GET", "https://api.weather.com/v1?q=${city}")
            .output(new FunctionResult("Weather: ${response.temp}"));

    var func = dm.toSwaigFunction();
    assertEquals("get_weather", func.get("function"));
    assertEquals("Get current weather", func.get("description"));
    assertTrue(func.containsKey("parameters"));
    assertTrue(func.containsKey("data_map"));
  }

  @Test
  void testDataMapParameterSchema() {
    var dm =
        new DataMap("test_func")
            .description("Test function")
            .parameter("name", "string", "User name", true)
            .parameter("age", "number", "User age", false)
            .webhook("GET", "https://example.com")
            .output(new FunctionResult("ok"));

    var func = dm.toSwaigFunction();

    @SuppressWarnings("unchecked")
    var params = (Map<String, Object>) func.get("parameters");
    assertEquals("object", params.get("type"));

    @SuppressWarnings("unchecked")
    var props = (Map<String, Object>) params.get("properties");
    assertTrue(props.containsKey("name"));
    assertTrue(props.containsKey("age"));

    @SuppressWarnings("unchecked")
    var required = (List<String>) params.get("required");
    assertEquals(1, required.size());
    assertTrue(required.contains("name"));
  }

  @Test
  void testDataMapParameterWithEnum() {
    var dm =
        new DataMap("test_func")
            .purpose("Test")
            .parameter("unit", "string", "Temperature unit", true, List.of("celsius", "fahrenheit"))
            .webhook("GET", "https://example.com")
            .output(new FunctionResult("ok"));

    var func = dm.toSwaigFunction();

    @SuppressWarnings("unchecked")
    var params = (Map<String, Object>) func.get("parameters");
    @SuppressWarnings("unchecked")
    var props = (Map<String, Object>) params.get("properties");
    @SuppressWarnings("unchecked")
    var unitDef = (Map<String, Object>) props.get("unit");
    assertTrue(unitDef.containsKey("enum"));
  }

  @Test
  void testDataMapWebhookWithHeaders() {
    var dm =
        new DataMap("test_func")
            .purpose("Test")
            .webhook(
                "POST", "https://api.example.com/search", Map.of("Authorization", "Bearer TOKEN"))
            .output(new FunctionResult("ok"));

    var func = dm.toSwaigFunction();

    @SuppressWarnings("unchecked")
    var dataMap = (Map<String, Object>) func.get("data_map");
    @SuppressWarnings("unchecked")
    var webhooks = (List<Map<String, Object>>) dataMap.get("webhooks");
    assertEquals(1, webhooks.size());
    assertTrue(webhooks.get(0).containsKey("headers"));
  }

  @Test
  void testDataMapWebhookWithBody() {
    var dm =
        new DataMap("search")
            .purpose("Search")
            .webhook("POST", "https://api.example.com/search")
            .body(Map.of("query", "${args.query}", "limit", 3))
            .output(new FunctionResult("Found: ${response.title}"));

    var func = dm.toSwaigFunction();

    @SuppressWarnings("unchecked")
    var dataMap = (Map<String, Object>) func.get("data_map");
    @SuppressWarnings("unchecked")
    var webhooks = (List<Map<String, Object>>) dataMap.get("webhooks");
    assertTrue(webhooks.get(0).containsKey("body"));
  }

  @Test
  void testDataMapBodyRequiresWebhook() {
    assertThrows(
        IllegalStateException.class, () -> new DataMap("test").body(Map.of("key", "value")));
  }

  @Test
  void testDataMapExpression() {
    var dm =
        new DataMap("file_control")
            .purpose("Control file playback")
            .parameter("command", "string", "Playback command")
            .expression("${args.command}", "start.*", new FunctionResult("Starting playback"))
            .expression("${args.command}", "stop.*", new FunctionResult("Stopping playback"));

    var func = dm.toSwaigFunction();

    @SuppressWarnings("unchecked")
    var dataMap = (Map<String, Object>) func.get("data_map");
    @SuppressWarnings("unchecked")
    var expressions = (List<Map<String, Object>>) dataMap.get("expressions");
    assertEquals(2, expressions.size());
    assertEquals("${args.command}", expressions.get(0).get("string"));
    assertEquals("start.*", expressions.get(0).get("pattern"));
  }

  @Test
  void testDataMapExpressionWithNomatch() {
    var dm =
        new DataMap("test")
            .purpose("Test")
            .expression(
                "${args.value}",
                "yes",
                new FunctionResult("Matched yes"),
                new FunctionResult("Did not match"));

    var func = dm.toSwaigFunction();

    @SuppressWarnings("unchecked")
    var dataMap = (Map<String, Object>) func.get("data_map");
    @SuppressWarnings("unchecked")
    var expressions = (List<Map<String, Object>>) dataMap.get("expressions");
    assertTrue(expressions.get(0).containsKey("nomatch-output"));
  }

  @Test
  void testDataMapWebhookExpressions() {
    // The bulk form attaches an expressions list to the most recent webhook.
    // Build each entry with the same shape a single expression(...) call produces,
    // then assert the bulk-added list is wire-identical.
    var startOutput = new FunctionResult("Starting playback");
    var stopOutput = new FunctionResult("Stopping playback");

    List<Map<String, Object>> exprList = new ArrayList<>();
    Map<String, Object> e1 = new LinkedHashMap<>();
    e1.put("string", "${args.command}");
    e1.put("pattern", "start.*");
    e1.put("output", startOutput.toMap());
    exprList.add(e1);
    Map<String, Object> e2 = new LinkedHashMap<>();
    e2.put("string", "${args.command}");
    e2.put("pattern", "stop.*");
    e2.put("output", stopOutput.toMap());
    exprList.add(e2);

    var dm =
        new DataMap("file_control")
            .purpose("Control file playback")
            .parameter("command", "string", "Playback command")
            .webhook("GET", "https://api.example.com/control")
            .webhookExpressions(exprList);

    var func = dm.toSwaigFunction();

    @SuppressWarnings("unchecked")
    var dataMap = (Map<String, Object>) func.get("data_map");
    @SuppressWarnings("unchecked")
    var webhooks = (List<Map<String, Object>>) dataMap.get("webhooks");
    assertEquals(1, webhooks.size());
    assertTrue(webhooks.get(0).containsKey("expressions"));

    @SuppressWarnings("unchecked")
    var webhookExprs = (List<Map<String, Object>>) webhooks.get(0).get("expressions");
    assertEquals(2, webhookExprs.size());

    // Wire-identical to building each expression map individually.
    assertEquals(exprList, webhookExprs);
    assertEquals("${args.command}", webhookExprs.get(0).get("string"));
    assertEquals("start.*", webhookExprs.get(0).get("pattern"));
    assertEquals(startOutput.toMap(), webhookExprs.get(0).get("output"));
    assertEquals("stop.*", webhookExprs.get(1).get("pattern"));
  }

  @Test
  void testDataMapWebhookExpressionsRequiresWebhook() {
    assertThrows(
        IllegalStateException.class, () -> new DataMap("test").webhookExpressions(List.of()));
  }

  @Test
  void testDataMapForeach() {
    var dm =
        new DataMap("search")
            .purpose("Search")
            .webhook("GET", "https://api.example.com/search")
            .output(new FunctionResult("Results"))
            .foreach(
                Map.of(
                    "input_key", "results",
                    "output_key", "formatted",
                    "max", 3,
                    "append", "${this.title}\n"));

    var func = dm.toSwaigFunction();

    @SuppressWarnings("unchecked")
    var dataMap = (Map<String, Object>) func.get("data_map");
    @SuppressWarnings("unchecked")
    var webhooks = (List<Map<String, Object>>) dataMap.get("webhooks");
    assertTrue(webhooks.get(0).containsKey("foreach"));
  }

  @Test
  void testDataMapFallbackOutput() {
    var dm =
        new DataMap("search")
            .purpose("Search")
            .webhook("GET", "https://primary.example.com")
            .output(new FunctionResult("Primary result"))
            .webhook("GET", "https://fallback.example.com")
            .output(new FunctionResult("Fallback result"))
            .fallbackOutput(new FunctionResult("All APIs failed"));

    var func = dm.toSwaigFunction();

    @SuppressWarnings("unchecked")
    var dataMap = (Map<String, Object>) func.get("data_map");
    assertTrue(dataMap.containsKey("output"));
    @SuppressWarnings("unchecked")
    var webhooks = (List<Map<String, Object>>) dataMap.get("webhooks");
    assertEquals(2, webhooks.size());
  }

  @Test
  void testDataMapErrorKeys() {
    var dm =
        new DataMap("test")
            .purpose("Test")
            .webhook("GET", "https://example.com")
            .output(new FunctionResult("ok"))
            .errorKeys(List.of("error", "message"));

    var func = dm.toSwaigFunction();

    @SuppressWarnings("unchecked")
    var dataMap = (Map<String, Object>) func.get("data_map");
    @SuppressWarnings("unchecked")
    var webhooks = (List<Map<String, Object>>) dataMap.get("webhooks");
    assertTrue(webhooks.get(0).containsKey("error_keys"));
  }

  @Test
  void testDataMapGlobalErrorKeys() {
    var dm =
        new DataMap("test")
            .purpose("Test")
            .globalErrorKeys(List.of("error", "fault"))
            .webhook("GET", "https://example.com")
            .output(new FunctionResult("ok"));

    var func = dm.toSwaigFunction();

    @SuppressWarnings("unchecked")
    var dataMap = (Map<String, Object>) func.get("data_map");
    assertTrue(dataMap.containsKey("error_keys"));
  }

  @Test
  void testDataMapDefaultPurpose() {
    var dm = new DataMap("my_func");
    var func = dm.toSwaigFunction();
    assertEquals("Execute my_func", func.get("description"));
  }

  @Test
  void testDataMapMultipleWebhooks() {
    var dm =
        new DataMap("multi")
            .purpose("Multi-webhook test")
            .webhook("GET", "https://api1.example.com")
            .output(new FunctionResult("Result 1"))
            .webhook("GET", "https://api2.example.com")
            .output(new FunctionResult("Result 2"));

    var func = dm.toSwaigFunction();

    @SuppressWarnings("unchecked")
    var dataMap = (Map<String, Object>) func.get("data_map");
    @SuppressWarnings("unchecked")
    var webhooks = (List<Map<String, Object>>) dataMap.get("webhooks");
    assertEquals(2, webhooks.size());
  }

  @Test
  void testDataMapParams() {
    var dm =
        new DataMap("test")
            .purpose("Test")
            .webhook("POST", "https://example.com")
            .params(Map.of("key", "value"))
            .output(new FunctionResult("ok"));

    var func = dm.toSwaigFunction();

    @SuppressWarnings("unchecked")
    var dataMap = (Map<String, Object>) func.get("data_map");
    @SuppressWarnings("unchecked")
    var webhooks = (List<Map<String, Object>>) dataMap.get("webhooks");
    assertTrue(webhooks.get(0).containsKey("params"));
  }

  @Test
  void testGetName() {
    var dm = new DataMap("my_tool");
    assertEquals("my_tool", dm.getName());
  }
}
