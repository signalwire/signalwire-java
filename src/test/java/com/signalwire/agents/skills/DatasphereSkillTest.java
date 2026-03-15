package com.signalwire.agents.skills;

import com.signalwire.agents.skills.builtin.DatasphereSkill;
import com.signalwire.agents.skills.builtin.DatasphereServerlessSkill;
import com.signalwire.agents.swaig.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DatasphereSkillTest {

    private Map<String, Object> dsParams() {
        return new LinkedHashMap<>(Map.of(
                "space_name", "test.signalwire.com",
                "project_id", "proj-123",
                "token", "tok-456",
                "document_id", "doc-789"
        ));
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

    @Test
    void testServerlessGlobalData() {
        DatasphereServerlessSkill skill = new DatasphereServerlessSkill();
        skill.setup(dsParams());
        var gd = skill.getGlobalData();
        assertTrue((Boolean) gd.get("datasphere_serverless_enabled"));
    }
}
