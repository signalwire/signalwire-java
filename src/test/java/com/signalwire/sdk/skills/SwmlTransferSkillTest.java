package com.signalwire.sdk.skills;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.skills.builtin.SwmlTransferSkill;
import java.util.*;
import org.junit.jupiter.api.Test;

class SwmlTransferSkillTest {

  @Test
  void testSkillProperties() {
    SwmlTransferSkill skill = new SwmlTransferSkill();
    assertEquals("swml_transfer", skill.getName());
    assertTrue(skill.supportsMultipleInstances());
  }

  @Test
  void testSetupWithTransfers() {
    Map<String, Map<String, Object>> transfers = new LinkedHashMap<>();
    transfers.put(
        "sales",
        Map.of("url", "https://sales.example.com/swml", "message", "Transferring to sales"));
    transfers.put("support", Map.of("address", "+15551234567"));
    assertTrue(new SwmlTransferSkill().setup(Map.of("transfers", transfers)));
  }

  @Test
  void testSwaigFunctionsGenerated() {
    SwmlTransferSkill skill = new SwmlTransferSkill();
    Map<String, Map<String, Object>> transfers = new LinkedHashMap<>();
    transfers.put("sales", Map.of("url", "https://sales.example.com/swml"));
    skill.setup(Map.of("transfers", transfers));
    var fns = skill.getSwaigFunctions();
    assertFalse(fns.isEmpty());
  }

  @Test
  void testHintsContainTransferAndDepartment() {
    SwmlTransferSkill skill = new SwmlTransferSkill();
    Map<String, Map<String, Object>> transfers = new LinkedHashMap<>();
    transfers.put("sales", Map.of("url", "https://sales.example.com/swml"));
    skill.setup(Map.of("transfers", transfers));
    var hints = skill.getHints();
    assertTrue(hints.contains("transfer"));
    assertTrue(hints.contains("sales"));
  }

  @Test
  void testRegisterToolsEmpty() {
    SwmlTransferSkill skill = new SwmlTransferSkill();
    Map<String, Map<String, Object>> transfers = new LinkedHashMap<>();
    transfers.put("sales", Map.of("url", "https://sales.example.com/swml"));
    skill.setup(Map.of("transfers", transfers));
    assertTrue(skill.registerTools().isEmpty());
  }
}
