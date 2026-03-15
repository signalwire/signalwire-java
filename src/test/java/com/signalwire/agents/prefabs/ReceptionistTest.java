package com.signalwire.agents.prefabs;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ReceptionistTest {

    @Test
    void testCreation() {
        Map<String, Map<String, Object>> departments = new LinkedHashMap<>();
        departments.put("sales", ReceptionistAgent.phoneDepartment("Sales", "+15551234567"));
        departments.put("support", ReceptionistAgent.swmlDepartment("Support", "https://support.example.com/swml"));
        ReceptionistAgent prefab = new ReceptionistAgent("test-receptionist",
                "Welcome!", departments);
        assertNotNull(prefab.getAgent());
        assertEquals("test-receptionist", prefab.getAgent().getName());
    }

    @Test
    void testHasSwmlTransferSkill() {
        Map<String, Map<String, Object>> departments = new LinkedHashMap<>();
        departments.put("sales", ReceptionistAgent.phoneDepartment("Sales", "+15551234567"));
        ReceptionistAgent prefab = new ReceptionistAgent("test", "Welcome!", departments);
        assertTrue(prefab.getAgent().hasSkill("swml_transfer"));
    }

    @Test
    void testPhoneDepartmentHelper() {
        Map<String, Object> dept = ReceptionistAgent.phoneDepartment("Sales", "+15551234567");
        assertEquals("Sales", dept.get("description"));
        assertEquals("+15551234567", dept.get("address"));
    }

    @Test
    void testSwmlDepartmentHelper() {
        Map<String, Object> dept = ReceptionistAgent.swmlDepartment("Support", "https://example.com/swml");
        assertEquals("Support", dept.get("description"));
        assertEquals("https://example.com/swml", dept.get("url"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSwmlRendering() {
        Map<String, Map<String, Object>> departments = new LinkedHashMap<>();
        departments.put("sales", ReceptionistAgent.phoneDepartment("Sales", "+15551234567"));
        ReceptionistAgent prefab = new ReceptionistAgent("test", "Welcome!", departments);
        Map<String, Object> swml = prefab.getAgent().renderSwml("http://localhost:3000");
        assertNotNull(swml);
        assertEquals("1.0.0", swml.get("version"));
    }
}
