package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swml.Codec;
import com.signalwire.sdk.swml.RecordDirection;
import com.signalwire.sdk.swml.TapDirection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves the three Tier-1 SWAIG {@link FunctionResult} closed-set enums — {@link RecordDirection}
 * ({@code record_call} direction), {@link TapDirection} ({@code tap} direction) and {@link Codec}
 * ({@code tap} codec) — each behave identically to the equivalent bare string they keep parity
 * with.
 *
 * <p>For every enum: (a) each constant's wire value; (b) the enum overload and the equivalent
 * String produce the BYTE-IDENTICAL {@code record_call}/{@code tap} action (driving the real method
 * → the built Map, asserting the serialized SWML params); (c) every value round-trips through
 * {@code fromWire}; (d) an out-of-set String is still rejected by the method's existing validation.
 *
 * <p>No mocks: real {@link FunctionResult} rendering the real SWML document.
 *
 * <p>The three sets are deliberately DISTINCT vocabularies and are never shared (the 3-vocabulary
 * trap): {@code record_call} uses {@code listen} where {@code tap} uses {@code hear}; the SWAIG tap
 * {@code Codec} is a 2-value set, not the larger RELAY connect/stream codec superset. The
 * cross-rejection tests below pin that the {@code fromWire} parsers refuse the other set's member.
 */
class MediaEnumTest {

  // Helper: drill into the SWML-wrapped verb params for a single-action result.
  @SuppressWarnings("unchecked")
  private static Map<String, Object> verbParams(FunctionResult fr, String verb) {
    Map<String, Object> swml = (Map<String, Object>) fr.getActions().get(0).get("SWML");
    Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
    List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
    return (Map<String, Object>) main.get(0).get(verb);
  }

  // ===================== RecordDirection {speak, listen, both} =====================

  @Test
  void recordDirectionConstantWireValues() {
    assertEquals("speak", RecordDirection.SPEAK.getValue());
    assertEquals("listen", RecordDirection.LISTEN.getValue());
    assertEquals("both", RecordDirection.BOTH.getValue());
  }

  @Test
  void recordDirectionEnumEqualsStringByteIdentical() {
    // Drive the REAL recordCall both ways: typed enum vs the bare string.
    FunctionResult viaEnum =
        new FunctionResult("Recording").recordCall("ctrl-1", true, "mp3", RecordDirection.LISTEN);
    FunctionResult viaString =
        new FunctionResult("Recording").recordCall("ctrl-1", true, "mp3", "listen");

    // Entire serialized result must be byte-for-byte identical.
    assertEquals(viaString.toMap(), viaEnum.toMap());

    // And the direction that actually lands in the SWML record_call verb is
    // the canonical wire string "listen" (not the enum's name "LISTEN").
    assertEquals("listen", verbParams(viaEnum, "record_call").get("direction"));
  }

  @Test
  void recordDirectionEnumEqualsStringForEveryValue() {
    for (RecordDirection d : RecordDirection.values()) {
      FunctionResult viaEnum = new FunctionResult("Recording").recordCall("c", false, "wav", d);
      FunctionResult viaString =
          new FunctionResult("Recording").recordCall("c", false, "wav", d.getValue());
      assertEquals(
          viaString.toMap(),
          viaEnum.toMap(),
          "enum vs string diverged for RecordDirection." + d.name());
      assertEquals(d.getValue(), verbParams(viaEnum, "record_call").get("direction"));
    }
  }

  @Test
  void recordDirectionFullyTypedOverloadMatchesStrings() {
    // Both format AND direction typed must equal both-bare-string.
    FunctionResult viaEnum =
        new FunctionResult("Recording")
            .recordCall("c", true, com.signalwire.sdk.swml.RecordFormat.MP3, RecordDirection.SPEAK);
    FunctionResult viaString =
        new FunctionResult("Recording").recordCall("c", true, "mp3", "speak");
    assertEquals(viaString.toMap(), viaEnum.toMap());
  }

  @Test
  void recordDirectionFromWireRoundTripsEveryValue() {
    for (RecordDirection d : RecordDirection.values()) {
      assertSame(d, RecordDirection.fromWire(d.getValue()), "round-trip failed for " + d.name());
    }
  }

  @Test
  void recordDirectionFromWireRejectsOutOfSet() {
    // "hear" belongs to TapDirection, NOT record_call (the 3-vocab trap).
    assertNull(RecordDirection.fromWire("hear"));
    assertNull(RecordDirection.fromWire("listenn"));
    assertNull(RecordDirection.fromWire("SPEAK")); // case-sensitive
    assertNull(RecordDirection.fromWire(""));
    assertNull(RecordDirection.fromWire(null));
  }

  @Test
  void recordCallRejectsOutOfSetDirectionString() {
    // The method's OWN existing validation still rejects a bad string,
    // independent of the enum. "hear" is valid for tap, never record_call.
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new FunctionResult("Recording").recordCall("c", false, "wav", "hear"));
    assertTrue(
        ex.getMessage().contains("speak")
            && ex.getMessage().contains("listen")
            && ex.getMessage().contains("both"),
        "unexpected message: " + ex.getMessage());
    // And a pure typo.
    assertThrows(
        IllegalArgumentException.class,
        () -> new FunctionResult("Recording").recordCall("c", false, "wav", "sideways"));
  }

  // ===================== TapDirection {speak, hear, both} =====================

  @Test
  void tapDirectionConstantWireValues() {
    assertEquals("speak", TapDirection.SPEAK.getValue());
    assertEquals("hear", TapDirection.HEAR.getValue());
    assertEquals("both", TapDirection.BOTH.getValue());
  }

  @Test
  void tapDirectionEnumEqualsStringByteIdentical() {
    // direction != "both" so it is actually emitted (the full method only
    // emits direction when it differs from the "both" default).
    FunctionResult viaEnum =
        new FunctionResult("Tapping").tap("wss://x.example", "t1", TapDirection.HEAR, "PCMU");
    FunctionResult viaString =
        new FunctionResult("Tapping").tap("wss://x.example", "t1", "hear", "PCMU");

    assertEquals(viaString.toMap(), viaEnum.toMap());
    assertEquals("hear", verbParams(viaEnum, "tap").get("direction"));
  }

  @Test
  void tapDirectionEnumEqualsStringForEveryValue() {
    for (TapDirection d : TapDirection.values()) {
      FunctionResult viaEnum =
          new FunctionResult("Tapping").tap("wss://x.example", "t1", d, "PCMU");
      FunctionResult viaString =
          new FunctionResult("Tapping").tap("wss://x.example", "t1", d.getValue(), "PCMU");
      assertEquals(
          viaString.toMap(),
          viaEnum.toMap(),
          "enum vs string diverged for TapDirection." + d.name());
    }
  }

  @Test
  void tapDirectionFromWireRoundTripsEveryValue() {
    for (TapDirection d : TapDirection.values()) {
      assertSame(d, TapDirection.fromWire(d.getValue()), "round-trip failed for " + d.name());
    }
  }

  @Test
  void tapDirectionFromWireRejectsOutOfSet() {
    // "listen" belongs to RecordDirection, NOT tap (the 3-vocab trap).
    assertNull(TapDirection.fromWire("listen"));
    assertNull(TapDirection.fromWire("haer"));
    assertNull(TapDirection.fromWire("HEAR"));
    assertNull(TapDirection.fromWire(null));
  }

  @Test
  void tapRejectsOutOfSetDirectionString() {
    // "listen" is valid for record_call, never tap — method validation rejects it.
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new FunctionResult("Tapping").tap("wss://x.example", "t1", "listen", "PCMU"));
    assertTrue(
        ex.getMessage().contains("speak")
            && ex.getMessage().contains("hear")
            && ex.getMessage().contains("both"),
        "unexpected message: " + ex.getMessage());
  }

  // ===================== Codec {PCMU, PCMA} =====================

  @Test
  void codecConstantWireValues() {
    assertEquals("PCMU", Codec.PCMU.getValue());
    assertEquals("PCMA", Codec.PCMA.getValue());
  }

  @Test
  void codecEnumEqualsStringByteIdentical() {
    // codec != "PCMU" default so PCMA is actually emitted.
    FunctionResult viaEnum =
        new FunctionResult("Tapping").tap("wss://x.example", "t1", "both", Codec.PCMA);
    FunctionResult viaString =
        new FunctionResult("Tapping").tap("wss://x.example", "t1", "both", "PCMA");

    assertEquals(viaString.toMap(), viaEnum.toMap());
    assertEquals("PCMA", verbParams(viaEnum, "tap").get("codec"));
  }

  @Test
  void codecEnumEqualsStringForEveryValue() {
    for (Codec c : Codec.values()) {
      FunctionResult viaEnum =
          new FunctionResult("Tapping").tap("wss://x.example", "t1", "speak", c);
      FunctionResult viaString =
          new FunctionResult("Tapping").tap("wss://x.example", "t1", "speak", c.getValue());
      assertEquals(
          viaString.toMap(), viaEnum.toMap(), "enum vs string diverged for Codec." + c.name());
    }
  }

  @Test
  void tapFullyTypedOverloadMatchesStrings() {
    // Both direction AND codec typed must equal both-bare-string.
    FunctionResult viaEnum =
        new FunctionResult("Tapping").tap("wss://x.example", "t1", TapDirection.SPEAK, Codec.PCMA);
    FunctionResult viaString =
        new FunctionResult("Tapping").tap("wss://x.example", "t1", "speak", "PCMA");
    assertEquals(viaString.toMap(), viaEnum.toMap());
  }

  @Test
  void codecFromWireRoundTripsEveryValue() {
    for (Codec c : Codec.values()) {
      assertSame(c, Codec.fromWire(c.getValue()), "round-trip failed for " + c.name());
    }
  }

  @Test
  void codecFromWireRejectsOutOfSet() {
    assertNull(Codec.fromWire("pcmu")); // case-sensitive, mirrors reference
    assertNull(Codec.fromWire("PCMX"));
    assertNull(Codec.fromWire("OPUS")); // a RELAY codec, not in the SWAIG tap set
    assertNull(Codec.fromWire(null));
  }

  @Test
  void tapRejectsOutOfSetCodecString() {
    // OPUS is a valid RELAY connect codec but NOT a SWAIG tap codec — the
    // method's own validation rejects it (the two codec vocabularies are distinct).
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new FunctionResult("Tapping").tap("wss://x.example", "t1", "both", "OPUS"));
    assertTrue(
        ex.getMessage().contains("PCMU") && ex.getMessage().contains("PCMA"),
        "unexpected message: " + ex.getMessage());
  }
}
