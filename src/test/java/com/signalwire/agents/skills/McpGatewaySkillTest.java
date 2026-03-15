package com.signalwire.agents.skills;

import com.signalwire.agents.skills.builtin.McpGatewaySkill;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class McpGatewaySkillTest {

    @Test
    void testSkillProperties() {
        McpGatewaySkill skill = new McpGatewaySkill();
        assertEquals("mcp_gateway", skill.getName());
        assertNotNull(skill.getDescription());
    }

    @Test
    void testSetupFailsWithoutGatewayUrl() {
        assertFalse(new McpGatewaySkill().setup(Map.of()));
    }

    @Test
    void testSetupSucceedsWithUrl() {
        assertTrue(new McpGatewaySkill().setup(Map.of("gateway_url", "http://localhost:8080")));
    }

    @Test
    void testSetupWithServices() {
        List<Map<String, Object>> services = List.of(
                Map.of("name", "calendar",
                        "tools", List.of(
                                Map.of("name", "list_events", "description", "List events")
                        ))
        );
        McpGatewaySkill skill = new McpGatewaySkill();
        assertTrue(skill.setup(Map.of("gateway_url", "http://localhost:8080", "services", services)));
    }

    @Test
    void testRegistersToolsFromServices() {
        List<Map<String, Object>> services = List.of(
                Map.of("name", "calendar",
                        "tools", List.of(
                                Map.of("name", "list_events", "description", "List events"),
                                Map.of("name", "create_event", "description", "Create event")
                        ))
        );
        McpGatewaySkill skill = new McpGatewaySkill();
        skill.setup(Map.of("gateway_url", "http://localhost:8080", "services", services));
        var tools = skill.registerTools();
        assertEquals(2, tools.size());
        assertTrue(tools.get(0).getName().startsWith("mcp_"));
    }

    @Test
    void testCustomToolPrefix() {
        List<Map<String, Object>> services = List.of(
                Map.of("name", "svc", "tools", List.of(Map.of("name", "tool1", "description", "T1")))
        );
        McpGatewaySkill skill = new McpGatewaySkill();
        skill.setup(Map.of("gateway_url", "http://localhost:8080",
                "services", services, "tool_prefix", "gw_"));
        var tools = skill.registerTools();
        assertTrue(tools.get(0).getName().startsWith("gw_"));
    }

    @Test
    void testHints() {
        List<Map<String, Object>> services = List.of(
                Map.of("name", "calendar", "tools", List.of())
        );
        McpGatewaySkill skill = new McpGatewaySkill();
        skill.setup(Map.of("gateway_url", "http://localhost:8080", "services", services));
        var hints = skill.getHints();
        assertTrue(hints.contains("MCP"));
        assertTrue(hints.contains("calendar"));
    }

    @Test
    void testGlobalData() {
        McpGatewaySkill skill = new McpGatewaySkill();
        skill.setup(Map.of("gateway_url", "http://localhost:8080"));
        var gd = skill.getGlobalData();
        assertEquals("http://localhost:8080", gd.get("mcp_gateway_url"));
    }

    @Test
    void testPromptSections() {
        McpGatewaySkill skill = new McpGatewaySkill();
        skill.setup(Map.of("gateway_url", "http://localhost:8080"));
        assertFalse(skill.getPromptSections().isEmpty());
    }
}
