package com.signalwire.agents.skills;

import com.signalwire.agents.skills.builtin.MathSkill;
import com.signalwire.agents.swaig.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MathSkillTest {

    @Test
    void testSkillProperties() {
        MathSkill skill = new MathSkill();
        assertEquals("math", skill.getName());
        assertNotNull(skill.getDescription());
    }

    @Test
    void testSetupSucceeds() {
        assertTrue(new MathSkill().setup(Map.of()));
    }

    @Test
    void testRegistersOneCalculateTool() {
        MathSkill skill = new MathSkill();
        skill.setup(Map.of());
        List<ToolDefinition> tools = skill.registerTools();
        assertEquals(1, tools.size());
        assertEquals("calculate", tools.get(0).getName());
    }

    @Test
    void testAddition() {
        var result = evaluate("2+3");
        assertEquals("Result: 5", result);
    }

    @Test
    void testSubtraction() {
        var result = evaluate("10-4");
        assertEquals("Result: 6", result);
    }

    @Test
    void testMultiplication() {
        var result = evaluate("7*6");
        assertEquals("Result: 42", result);
    }

    @Test
    void testDivision() {
        var result = evaluate("100/4");
        assertEquals("Result: 25", result);
    }

    @Test
    void testExponentiation() {
        var result = evaluate("2**3");
        assertEquals("Result: 8", result);
    }

    @Test
    void testParentheses() {
        var result = evaluate("(2+3)*4");
        assertEquals("Result: 20", result);
    }

    @Test
    void testDecimalResult() {
        var result = evaluate("7/2");
        assertEquals("Result: 3.5", result);
    }

    @Test
    void testInvalidExpression() {
        var result = evaluate("abc");
        assertTrue(result.contains("Error"));
    }

    private String evaluate(String expression) {
        MathSkill skill = new MathSkill();
        skill.setup(Map.of());
        var tools = skill.registerTools();
        return tools.get(0).getHandler().handle(Map.of("expression", expression), Map.of()).getResponse();
    }
}
