package com.signalwire.agents.skills;

import com.signalwire.agents.skills.builtin.WeatherApiSkill;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WeatherApiSkillTest {

    @Test
    void testSkillProperties() {
        WeatherApiSkill skill = new WeatherApiSkill();
        assertEquals("weather_api", skill.getName());
        assertNotNull(skill.getDescription());
    }

    @Test
    void testSetupFailsWithoutApiKey() {
        assertFalse(new WeatherApiSkill().setup(Map.of()));
    }

    @Test
    void testSetupSucceedsWithApiKey() {
        assertTrue(new WeatherApiSkill().setup(Map.of("api_key", "test-key")));
    }

    @Test
    void testSwaigFunctionsReturned() {
        WeatherApiSkill skill = new WeatherApiSkill();
        skill.setup(Map.of("api_key", "test-key"));
        var fns = skill.getSwaigFunctions();
        assertFalse(fns.isEmpty());
        assertEquals("get_weather", fns.get(0).get("function"));
    }

    @Test
    void testTemperatureUnitConfig() {
        WeatherApiSkill skill = new WeatherApiSkill();
        skill.setup(Map.of("api_key", "key", "temperature_unit", "celsius"));
        var fns = skill.getSwaigFunctions();
        assertFalse(fns.isEmpty());
    }

    @Test
    void testRegisterToolsEmpty() {
        WeatherApiSkill skill = new WeatherApiSkill();
        skill.setup(Map.of("api_key", "test"));
        assertTrue(skill.registerTools().isEmpty());
    }
}
