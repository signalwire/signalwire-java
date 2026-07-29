package com.signalwire.sdk.skills;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.skills.builtin.DatasphereServerlessSkill;
import com.signalwire.sdk.skills.builtin.DatasphereSkill;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;
import org.junit.jupiter.api.Test;

class DatasphereSkillTest {

  private Map<String, Object> dsParams() {
    return new LinkedHashMap<>(
        Map.of(
            "space_name", "test.signalwire.com",
            "project_id", "proj-123",
            "token", "tok-456",
            "document_id", "doc-789"));
  }

  // ======== DatasphereSkill ========

  @Test
  void testSkillProperties() {
    DatasphereSkill skill = new DatasphereSkill();
    assertEquals("datasphere", skill.getName());
    assertTrue(skill.supportsMultipleInstances());
  }

  @Test
  void testSetupFailsWithoutRequiredParams() {
    assertFalse(new DatasphereSkill().setup(Map.of()));
  }

  @Test
  void testSetupSucceeds() {
    assertTrue(new DatasphereSkill().setup(dsParams()));
  }

  @Test
  void testRegistersSearchTool() {
    DatasphereSkill skill = new DatasphereSkill();
    skill.setup(dsParams());
    List<ToolDefinition> tools = skill.registerTools();
    assertEquals(1, tools.size());
    assertEquals("search_knowledge", tools.get(0).getName());
  }

  @Test
  void testCustomToolName() {
    var params = dsParams();
    params.put("tool_name", "search_docs");
    DatasphereSkill skill = new DatasphereSkill();
    skill.setup(params);
    assertEquals("search_docs", skill.registerTools().get(0).getName());
  }

  @Test
  void testPromptSections() {
    DatasphereSkill skill = new DatasphereSkill();
    skill.setup(dsParams());
    assertFalse(skill.getPromptSections().isEmpty());
  }

  @Test
  void testGlobalData() {
    DatasphereSkill skill = new DatasphereSkill();
    skill.setup(dsParams());
    var gd = skill.getGlobalData();
    assertTrue((Boolean) gd.get("datasphere_enabled"));
    assertEquals("doc-789", gd.get("document_id"));
  }

  // ======== DatasphereServerlessSkill ========

  @Test
  void testServerlessSkillProperties() {
    DatasphereServerlessSkill skill = new DatasphereServerlessSkill();
    assertEquals("datasphere_serverless", skill.getName());
    assertTrue(skill.supportsMultipleInstances());
  }

  @Test
  void testServerlessSetupFailsWithoutParams() {
    assertFalse(new DatasphereServerlessSkill().setup(Map.of()));
  }

  @Test
  void testServerlessSetupSucceeds() {
    assertTrue(new DatasphereServerlessSkill().setup(dsParams()));
  }

  @Test
  void testServerlessRegisterToolsIsEmpty() {
    DatasphereServerlessSkill skill = new DatasphereServerlessSkill();
    skill.setup(dsParams());
    assertTrue(skill.registerTools().isEmpty());
  }

  @Test
  void testServerlessSwaigFunctionsReturned() {
    DatasphereServerlessSkill skill = new DatasphereServerlessSkill();
    skill.setup(dsParams());
    var fns = skill.getSwaigFunctions();
    assertEquals(1, fns.size());
    assertEquals("search_knowledge", fns.get(0).get("function"));
    assertTrue(fns.get(0).containsKey("data_map"));
  }

  /**
   * The engine reads ONLY "params" and "headers" off a webhook object (mod_openai/actions.c:735-739
   * -- there is no read of "body" anywhere), and it expands ${formatted_results} from the "foreach"
   * block. This asserts on the EMITTED PAYLOAD rather than on construction, because the
   * construction assertions above passed happily while both fields were wrong.
   */
  @Test
  @SuppressWarnings("unchecked")
  void testServerlessWebhookCarriesParamsAndForeach() {
    DatasphereServerlessSkill skill = new DatasphereServerlessSkill();
    skill.setup(dsParams());

    Map<String, Object> dataMap =
        (Map<String, Object>) skill.getSwaigFunctions().get(0).get("data_map");
    List<Map<String, Object>> webhooks = (List<Map<String, Object>>) dataMap.get("webhooks");
    assertEquals(1, webhooks.size());
    Map<String, Object> webhook = webhooks.get(0);

    // The search payload must ride on "params" -- a "body" key is silently dropped by the engine.
    assertFalse(
        webhook.containsKey("body"),
        "webhook must not carry a body key; the engine never reads it");
    Map<String, Object> params = (Map<String, Object>) webhook.get("params");
    assertNotNull(params, "webhook must carry params");
    assertEquals("${args.query}", params.get("query_string"));
    assertEquals("doc-789", params.get("document_id"));
    assertTrue(params.containsKey("count"));
    assertTrue(params.containsKey("distance"));

    // ${formatted_results} in the output is only populated by a foreach block.
    Map<String, Object> foreach = (Map<String, Object>) webhook.get("foreach");
    assertNotNull(foreach, "webhook must carry a foreach block to populate ${formatted_results}");
    assertEquals("chunks", foreach.get("input_key"));
    assertEquals("formatted_results", foreach.get("output_key"));
    assertTrue(foreach.containsKey("max"));
    assertTrue(((String) foreach.get("append")).contains("${this.text}"));
  }

  @Test
  void testServerlessGlobalData() {
    DatasphereServerlessSkill skill = new DatasphereServerlessSkill();
    skill.setup(dsParams());
    var gd = skill.getGlobalData();
    assertTrue((Boolean) gd.get("datasphere_serverless_enabled"));
  }
}
