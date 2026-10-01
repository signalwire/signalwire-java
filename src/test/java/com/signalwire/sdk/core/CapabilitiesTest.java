/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Port of the reference tests/unit/core/test_capabilities.py: absence and malformation both mean
 * "not declared".
 */
class CapabilitiesTest {

  private static final Map<String, Object> BODY =
      Map.of(
          "vars",
          Map.of(
              "userVariables",
              Map.of(
                  "capabilities",
                  Map.of("display_content", true, "transcript", true, "chat_handoff", false),
                  "metadata",
                  Map.of("widget", Map.of("opened_at", "2026-01-01T00:00:00Z")))));

  private static Map<String, Object> nullVal(String key) {
    Map<String, Object> m = new HashMap<>();
    m.put(key, null);
    return m;
  }

  private static List<Object> userVariablesJunk() {
    return Arrays.asList(
        null,
        Map.of(),
        "nonsense",
        42,
        nullVal("vars"),
        Map.of("vars", Map.of()),
        Map.of("vars", nullVal("userVariables")),
        Map.of("vars", Map.of("userVariables", "not a dict")));
  }

  @Test
  void extractsFromTheNestedShape() {
    assertTrue(Capabilities.userVariables(BODY).containsKey("capabilities"));
  }

  @Test
  void missingLevelsYieldAnEmptyMap() {
    for (Object junk : userVariablesJunk()) {
      assertEquals(Map.of(), Capabilities.userVariables(junk), "junk: " + junk);
    }
  }

  @Test
  void onlyTruthyNamesAreReturned() {
    assertEquals(Set.of("display_content", "transcript"), Capabilities.declaredCapabilities(BODY));
  }

  @Test
  void falseIsNotADeclaration() {
    assertFalse(Capabilities.declaredCapabilities(BODY).contains("chat_handoff"));
  }

  @Test
  void acceptsAlreadyExtractedUserVariables() {
    assertEquals(
        Set.of("a"), Capabilities.declaredCapabilities(Map.of("capabilities", Map.of("a", true))));
  }

  @Test
  void aNameThisSdkHasNeverHeardOfStillPassesThrough() {
    assertTrue(
        Capabilities.hasCapability(
            Map.of("capabilities", Map.of("future_thing", true)), "future_thing"));
  }

  @Test
  void absenceAndMalformationBothMeanNo() {
    List<Object> junks =
        Arrays.asList(
            null,
            Map.of(),
            "nonsense",
            42,
            Map.of("vars", Map.of("userVariables", Map.of("capabilities", "not a dict"))),
            Map.of("vars", Map.of("userVariables", nullVal("capabilities"))),
            Map.of("vars", Map.of("userVariables", Map.of())));
    for (Object junk : junks) {
      assertEquals(Set.of(), Capabilities.declaredCapabilities(junk), "junk: " + junk);
      assertFalse(Capabilities.hasCapability(junk, "display_content"), "junk: " + junk);
    }
  }

  @Test
  void hasCapabilityDeclaredFalseAndNeverMentioned() {
    assertTrue(Capabilities.hasCapability(BODY, "display_content"));
    assertFalse(Capabilities.hasCapability(BODY, "chat_handoff"));
    assertFalse(Capabilities.hasCapability(BODY, "telepathy"));
  }
}
