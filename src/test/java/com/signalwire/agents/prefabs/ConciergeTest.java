package com.signalwire.agents.prefabs;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ConciergeTest {

    @Test
    void testCreation() {
        List<Map<String, Object>> amenities = List.of(
                ConciergeAgent.amenity("Pool", "Olympic pool", "6-10", "1F", "Free")
        );
        ConciergeAgent prefab = new ConciergeAgent("test-concierge", "Grand Hotel", amenities);
        assertNotNull(prefab.getAgent());
        assertEquals("test-concierge", prefab.getAgent().getName());
    }

    @Test
    void testHasTools() {
        List<Map<String, Object>> amenities = List.of(
                ConciergeAgent.amenity("Pool", "Pool", "6-10", "1F", "Free")
        );
        ConciergeAgent prefab = new ConciergeAgent("test", "Hotel", amenities);
        assertTrue(prefab.getAgent().hasTool("get_amenity_info"));
        assertTrue(prefab.getAgent().hasTool("check_availability"));
    }

    @Test
    void testAmenityHelper() {
        Map<String, Object> amenity = ConciergeAgent.amenity("Gym", "Fitness center", "24/7", "B1", "Free");
        assertEquals("Gym", amenity.get("name"));
        assertEquals("Fitness center", amenity.get("description"));
        assertEquals("24/7", amenity.get("hours"));
        assertEquals("B1", amenity.get("location"));
        assertEquals("Free", amenity.get("price"));
    }

    @Test
    void testGetAmenityInfoExecution() {
        List<Map<String, Object>> amenities = List.of(
                ConciergeAgent.amenity("Pool", "Olympic pool", "6-10", "1F", "Free")
        );
        ConciergeAgent prefab = new ConciergeAgent("test", "Hotel", amenities);
        var result = prefab.getAgent().onFunctionCall("get_amenity_info",
                Map.of("amenity", "Pool"), Map.of());
        assertNotNull(result);
        assertTrue(result.getResponse().contains("Pool"));
        assertTrue(result.getResponse().contains("Olympic pool"));
    }

    @Test
    void testCheckAvailabilityExecution() {
        List<Map<String, Object>> amenities = List.of(
                ConciergeAgent.amenity("Pool", "Pool", "6-10", "1F", "Free")
        );
        ConciergeAgent prefab = new ConciergeAgent("test", "Hotel", amenities);
        var result = prefab.getAgent().onFunctionCall("check_availability",
                Map.of("amenity", "Pool"), Map.of());
        assertNotNull(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSwmlRendering() {
        List<Map<String, Object>> amenities = List.of(
                ConciergeAgent.amenity("Pool", "Pool", "6-10", "1F", "Free")
        );
        ConciergeAgent prefab = new ConciergeAgent("test", "Hotel", amenities);
        Map<String, Object> swml = prefab.getAgent().renderSwml("http://localhost:3000");
        assertNotNull(swml);
        assertEquals("1.0.0", swml.get("version"));
    }
}
