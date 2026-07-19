/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.swml.Document;
import com.signalwire.sdk.swml.Schema;
import com.signalwire.sdk.swml.Service;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Tests for SWML Document, Schema, and Service. */
class SwmlTest {

  // ======== Document Tests ========

  @Test
  void testDocumentInitialization() {
    var doc = new Document();
    assertNotNull(doc.getVerbs());
    assertTrue(doc.getVerbs().isEmpty());
    assertTrue(doc.hasSection("main"));
  }

  @Test
  void testDocumentAddVerb() {
    var doc = new Document();
    doc.addVerb("answer", Map.of("max_duration", 3600));

    assertEquals(1, doc.getVerbs().size());
    var verb = doc.getVerbs().get(0);
    assertTrue(verb.containsKey("answer"));
  }

  @Test
  void testDocumentMultipleVerbs() {
    var doc = new Document();
    doc.addVerb("answer", Map.of("max_duration", 3600));
    doc.addVerb("hangup", Map.of());

    assertEquals(2, doc.getVerbs().size());
  }

  @Test
  void testDocumentAddSection() {
    var doc = new Document();
    var section = doc.addSection("error_handler");

    assertNotNull(section);
    assertTrue(doc.hasSection("error_handler"));
    assertFalse(doc.hasSection("nonexistent"));
  }

  @Test
  void testDocumentAddVerbToSection() {
    var doc = new Document();
    doc.addVerbToSection("error_handler", "hangup", Map.of());

    assertTrue(doc.hasSection("error_handler"));
    var sectionVerbs = doc.getSectionVerbs("error_handler");
    assertEquals(1, sectionVerbs.size());
    assertTrue(sectionVerbs.get(0).containsKey("hangup"));
  }

  @Test
  void testDocumentToMap() {
    var doc = new Document();
    doc.addVerb("answer", Map.of("max_duration", 3600));

    var map = doc.toMap();
    assertEquals("1.0.0", map.get("version"));
    assertTrue(map.containsKey("sections"));

    @SuppressWarnings("unchecked")
    var sections = (Map<String, Object>) map.get("sections");
    assertTrue(sections.containsKey("main"));
  }

  @Test
  void testDocumentRender() {
    var doc = new Document();
    doc.addVerb("answer", Map.of("max_duration", 3600));

    String json = doc.render();
    assertNotNull(json);
    assertTrue(json.contains("\"version\":\"1.0.0\""));
    assertTrue(json.contains("\"answer\""));
    assertTrue(json.contains("\"max_duration\""));
  }

  @Test
  void testDocumentRenderPretty() {
    var doc = new Document();
    doc.addVerb("hangup", Map.of());

    String prettyJson = doc.renderPretty();
    assertNotNull(prettyJson);
    assertTrue(prettyJson.contains("\n"));
    assertTrue(prettyJson.contains("hangup"));
  }

  @Test
  void testDocumentReset() {
    var doc = new Document();
    doc.addVerb("answer", Map.of());
    doc.addSection("error_handler");
    assertEquals(1, doc.getVerbs().size());

    doc.reset();
    assertTrue(doc.getVerbs().isEmpty());
    assertTrue(doc.hasSection("main"));
    assertFalse(doc.hasSection("error_handler"));
  }

  // ======== Schema Tests ========

  @Test
  void testSchemaLoadedSuccessfully() {
    var schema = Schema.getInstance();
    assertEquals(38, schema.verbCount(), "Schema should have 38 verb definitions");
  }

  @Test
  void testSchemaIsSingleton() {
    var s1 = Schema.getInstance();
    var s2 = Schema.getInstance();
    assertSame(s1, s2);
  }

  @Test
  void testSchemaContainsExpectedVerbs() {
    var schema = Schema.getInstance();

    // Test a sampling of the 38 verbs
    assertTrue(schema.isValidVerb("answer"));
    assertTrue(schema.isValidVerb("ai"));
    assertTrue(schema.isValidVerb("hangup"));
    assertTrue(schema.isValidVerb("connect"));
    assertTrue(schema.isValidVerb("play"));
    assertTrue(schema.isValidVerb("record"));
    assertTrue(schema.isValidVerb("transfer"));
    assertTrue(schema.isValidVerb("sip_refer"));
    assertTrue(schema.isValidVerb("send_sms"));
    assertTrue(schema.isValidVerb("sleep"));
    assertTrue(schema.isValidVerb("detect_machine"));
    assertTrue(schema.isValidVerb("amazon_bedrock"));
    assertTrue(schema.isValidVerb("live_transcribe"));
    assertTrue(schema.isValidVerb("live_translate"));
    assertTrue(schema.isValidVerb("user_event"));
  }

  @Test
  void testSchemaInvalidVerb() {
    var schema = Schema.getInstance();
    assertFalse(schema.isValidVerb("nonexistent_verb"));
    assertFalse(schema.isValidVerb(""));
  }

  @Test
  void testSchemaGetVerb() {
    var schema = Schema.getInstance();
    var verbDef = schema.getVerb("answer");
    assertNotNull(verbDef);
    assertNull(schema.getVerb("nonexistent"));
  }

  @Test
  void testSchemaVerbNames() {
    var schema = Schema.getInstance();
    var names = schema.getVerbNames();
    assertEquals(38, names.size());
    assertTrue(names.contains("answer"));
    assertTrue(names.contains("hangup"));
    assertTrue(names.contains("sleep"));
  }

  @Test
  void testSchemaAllExpectedVerbs() {
    var schema = Schema.getInstance();
    // All 38 verbs from the porting guide
    var expected =
        List.of(
            "answer",
            "ai",
            "amazon_bedrock",
            "cond",
            "connect",
            "denoise",
            "detect_machine",
            "enter_queue",
            "execute",
            "goto",
            "hangup",
            "join_conference",
            "join_room",
            "label",
            "live_transcribe",
            "live_translate",
            "pay",
            "play",
            "prompt",
            "receive_fax",
            "record",
            "record_call",
            "request",
            "return",
            "sip_refer",
            "send_digits",
            "send_fax",
            "send_sms",
            "set",
            "sleep",
            "stop_denoise",
            "stop_record_call",
            "stop_tap",
            "switch",
            "tap",
            "transfer",
            "unset",
            "user_event");
    for (String verb : expected) {
      assertTrue(schema.isValidVerb(verb), "Missing verb: " + verb);
    }
  }

  // ======== Service Tests ========

  @Test
  void testServiceCreation() {
    var service = new Service("test-service");
    assertNotNull(service.getDocument());
    assertNotNull(service.getAuthUser());
    assertNotNull(service.getAuthPassword());
  }

  @Test
  void testServiceVerbMethods() {
    var service = new Service("test-service");

    // Test a few verb methods
    service.answer(Map.of("max_duration", 3600));
    service.hangup();

    var verbs = service.getDocument().getVerbs();
    assertEquals(2, verbs.size());

    var firstVerb = verbs.get(0);
    assertTrue(firstVerb.containsKey("answer"));

    var secondVerb = verbs.get(1);
    assertTrue(secondVerb.containsKey("hangup"));
  }

  @Test
  void testServiceSleepTakesInteger() {
    var service = new Service("test-service");
    service.sleep(1000);

    var verbs = service.getDocument().getVerbs();
    assertEquals(1, verbs.size());
    var verb = verbs.get(0);
    assertTrue(verb.containsKey("sleep"));
    assertEquals(1000, verb.get("sleep"));
  }

  @Test
  void testServiceChainingReturnsService() {
    var service = new Service("test-service");
    var result = service.answer(Map.of("max_duration", 3600)).sleep(500).hangup();

    assertSame(service, result);
    assertEquals(3, service.getDocument().getVerbs().size());
  }

  /**
   * D9-java typed-verb slice: the typed {@code connect(ConnectConfig)} overload must render the
   * SAME SWML (same keys/values — JSON object key order is not wire-significant) as the equivalent
   * {@code connect(Map)} call. Proves the generated ConnectConfig DTO is a wire-faithful typed view
   * of the connect params (unset fields omitted, set fields carry the exact snake wire key), so
   * consuming the DTO changes no wire.
   */
  @Test
  void testTypedConnectRendersIdenticalWireToMap() {
    var typed = new Service("svc");
    var cfg = new com.signalwire.sdk.swml.generated.ConnectConfig();
    cfg.from = "+15551112222";
    cfg.to = "+15553334444";
    cfg.codecs = "PCMU,OPUS";
    typed.connect(cfg);

    var mapped = new Service("svc");
    var m = new java.util.LinkedHashMap<String, Object>();
    m.put("from", "+15551112222");
    m.put("to", "+15553334444");
    m.put("codecs", "PCMU,OPUS");
    mapped.connect(m);

    // Parse both renders and compare as JSON trees — key order is not wire-significant.
    var gson = new com.google.gson.Gson();
    var typedTree = gson.fromJson(typed.getDocument().render(), java.util.Map.class);
    var mappedTree = gson.fromJson(mapped.getDocument().render(), java.util.Map.class);
    assertEquals(
        mappedTree,
        typedTree,
        "typed connect(ConnectConfig) must render the same SWML (keys/values) as connect(Map)");
  }

  @Test
  void testServiceAll38Verbs() {
    var service = new Service("test-service");

    // Call all 38 verb methods
    service.answer(Map.of());
    service.ai(Map.of());
    service.amazonBedrock(Map.of());
    service.cond(List.of());
    service.connect(Map.of());
    service.denoise(Map.of());
    service.detectMachine(Map.of());
    service.enterQueue(Map.of());
    service.execute(Map.of());
    service.gotoLabel(Map.of());
    service.hangup(Map.of());
    service.joinConference(Map.of());
    service.joinRoom(Map.of());
    service.label(Map.of());
    service.liveTranscribe(Map.of());
    service.liveTranslate(Map.of());
    service.pay(Map.of());
    service.play(Map.of());
    service.prompt(Map.of());
    service.receiveFax(Map.of());
    service.record(Map.of());
    service.recordCall(Map.of());
    service.request(Map.of());
    service.returnVerb(Map.of());
    service.sipRefer(Map.of());
    service.sendDigits(Map.of());
    service.sendFax(Map.of());
    service.sendSms(Map.of());
    service.set(Map.of());
    service.sleep(100);
    service.stopDenoise(Map.of());
    service.stopRecordCall(Map.of());
    service.stopTap(Map.of());
    service.switchVerb(Map.of());
    service.tap(Map.of());
    service.transfer(Map.of());
    service.unset(Map.of());
    service.userEvent(Map.of());

    assertEquals(38, service.getDocument().getVerbs().size());
  }

  @Test
  void testServiceAuthAutoGenerated() {
    var service = new Service("test-service");
    // Auth user is either "test-service" (default) or env var SWML_BASIC_AUTH_USER if set
    String envUser = System.getenv("SWML_BASIC_AUTH_USER");
    String expectedUser = (envUser != null && !envUser.isEmpty()) ? envUser : "test-service";
    assertEquals(expectedUser, service.getAuthUser());
    assertNotNull(service.getAuthPassword());
    assertFalse(service.getAuthPassword().isEmpty());
  }

  @Test
  void testServiceNullParamsHandled() {
    var service = new Service("test-service");
    // Should not throw
    service.answer(null);
    service.ai(null);

    var verbs = service.getDocument().getVerbs();
    assertEquals(2, verbs.size());
  }
}
