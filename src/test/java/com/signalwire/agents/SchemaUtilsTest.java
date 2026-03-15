package com.signalwire.agents;

import com.signalwire.agents.swml.Schema;
import com.signalwire.agents.swml.Document;
import com.signalwire.agents.swml.Service;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for schema-related utilities: Schema, Document, Service.
 */
class SchemaUtilsTest {

    // ======== Schema Utilities ========

    @Test
    void testSchemaIsSingleton() {
        assertSame(Schema.getInstance(), Schema.getInstance());
    }

    @Test
    void testSchemaValidVerbReturnsTrue() {
        assertTrue(Schema.getInstance().isValidVerb("answer"));
        assertTrue(Schema.getInstance().isValidVerb("hangup"));
        assertTrue(Schema.getInstance().isValidVerb("ai"));
    }

    @Test
    void testSchemaInvalidVerbReturnsFalse() {
        assertFalse(Schema.getInstance().isValidVerb("nonexistent"));
        assertFalse(Schema.getInstance().isValidVerb(""));
    }

    @Test
    void testSchemaGetVerbReturnsDefinition() {
        assertNotNull(Schema.getInstance().getVerb("answer"));
        assertNull(Schema.getInstance().getVerb("nonexistent"));
    }

    @Test
    void testSchemaVerbCount() {
        assertEquals(38, Schema.getInstance().verbCount());
    }

    @Test
    void testSchemaVerbNames() {
        Set<String> names = Schema.getInstance().getVerbNames();
        assertEquals(38, names.size());
        assertTrue(names.contains("answer"));
        assertTrue(names.contains("hangup"));
        assertTrue(names.contains("ai"));
        assertTrue(names.contains("play"));
        assertTrue(names.contains("record"));
    }

    // ======== Document Utilities ========

    @Test
    void testDocumentEmptyByDefault() {
        Document doc = new Document();
        assertTrue(doc.getVerbs().isEmpty());
        assertTrue(doc.hasSection("main"));
    }

    @Test
    void testDocumentAddVerbAndRender() {
        Document doc = new Document();
        doc.addVerb("answer", Map.of("max_duration", 3600));
        doc.addVerb("hangup", Map.of());
        String json = doc.render();
        assertTrue(json.contains("answer"));
        assertTrue(json.contains("hangup"));
    }

    @Test
    void testDocumentToMap() {
        Document doc = new Document();
        doc.addVerb("answer", Map.of());
        Map<String, Object> map = doc.toMap();
        assertEquals("1.0.0", map.get("version"));
        assertTrue(map.containsKey("sections"));
    }

    @Test
    void testDocumentReset() {
        Document doc = new Document();
        doc.addVerb("answer", Map.of());
        doc.addSection("error");
        doc.reset();
        assertTrue(doc.getVerbs().isEmpty());
        assertTrue(doc.hasSection("main"));
        assertFalse(doc.hasSection("error"));
    }

    // ======== Service Utilities ========

    @Test
    void testServiceCreation() {
        Service svc = new Service("test");
        assertNotNull(svc.getDocument());
        assertNotNull(svc.getAuthUser());
        assertNotNull(svc.getAuthPassword());
    }

    @Test
    void testServiceVerbChaining() {
        Service svc = new Service("test");
        Service result = svc.answer(Map.of()).sleep(500).hangup();
        assertSame(svc, result);
        assertEquals(3, svc.getDocument().getVerbs().size());
    }

    @Test
    void testServiceSleepInteger() {
        Service svc = new Service("test");
        svc.sleep(1000);
        var verbs = svc.getDocument().getVerbs();
        assertEquals(1000, verbs.get(0).get("sleep"));
    }
}
