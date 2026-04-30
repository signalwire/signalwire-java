package com.signalwire.sdk;

import com.signalwire.sdk.agent.AgentBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parity with signalwire-python:
 *   tests/unit/core/test_agent_base.py::TestAgentBaseTokenMethods::test_validate_tool_token
 *   tests/unit/core/test_agent_base.py::TestAgentBaseTokenMethods::test_create_tool_token
 *
 * Python's StateMixin._create_tool_token catches all exceptions and returns ""
 * on failure. validate_tool_token rejects unknown function names up front.
 */
class ToolTokenTest {

    private static AgentBase agentWithTool() {
        AgentBase a = AgentBase.builder().name("test_agent").build();
        a.defineTool("test_tool", "t", java.util.Map.of(), (args, raw) -> null);
        return a;
    }

    @Test
    void testCreateToolTokenRoundTrip() {
        AgentBase a = agentWithTool();
        String token = a.createToolToken("test_tool", "call_123");
        assertNotNull(token);
        assertFalse(token.isEmpty(), "expected SessionManager-issued token, got empty");
        assertTrue(a.validateToolToken("test_tool", token, "call_123"),
                "ValidateToolToken rejected the token we just created");
    }

    @Test
    void testValidateToolTokenRejectsUnknownFunction() {
        AgentBase a = AgentBase.builder().name("test_agent").build();
        assertFalse(a.validateToolToken("not_registered", "any_token", "call_123"),
                "expected false for unregistered function");
    }

    @Test
    void testValidateToolTokenRejectsBadToken() {
        AgentBase a = agentWithTool();
        assertFalse(a.validateToolToken("test_tool", "garbage_token_value", "call_123"),
                "expected false for garbage token");
    }

    @Test
    void testValidateToolTokenRejectsWrongCallId() {
        AgentBase a = agentWithTool();
        String token = a.createToolToken("test_tool", "call_A");
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertFalse(a.validateToolToken("test_tool", token, "call_B"),
                "expected false when token bound to different call_id");
    }
}
