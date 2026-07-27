package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.swml.Document;
import com.signalwire.sdk.swml.Schema;
import com.signalwire.sdk.swml.Service;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Tests for schema-related utilities: Schema, Document, Service. */
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

  // Assert the schema LOADED and is self-consistent rather than freezing a
  // headcount: a literal here has to be edited by every PR that adds a verb
  // upstream (ai_sidecar took it 38 -> 39) and never caught a real defect. The
  // python reference has no equivalent assertion at all, it only logs the count.
  @Test
  void testSchemaVerbCount() {
    var schema = Schema.getInstance();
    assertEquals(schema.getVerbNames().size(), schema.verbCount());
    assertTrue(schema.verbCount() >= 38, "schema looks truncated: " + schema.verbCount());
  }

  @Test
  void testSchemaVerbNames() {
    Set<String> names = Schema.getInstance().getVerbNames();
    assertTrue(names.size() >= 38, "schema looks truncated: " + names.size());
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
