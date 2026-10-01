package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.logging.Logger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tests for the Logger utility. */
class LoggingTest {

  private Logger.Level savedLevel;

  @AfterEach
  void restoreLevel() {
    if (savedLevel != null) {
      Logger.setGlobalLevel(savedLevel);
    }
  }

  @Test
  void testLoggerCreation() {
    Logger log = Logger.getLogger("test");
    assertNotNull(log);
  }

  @Test
  void testLoggerFromClass() {
    Logger log = Logger.getLogger(LoggingTest.class);
    assertNotNull(log);
  }

  @Test
  void testLevelOrdering() {
    assertTrue(Logger.Level.DEBUG.getValue() < Logger.Level.INFO.getValue());
    assertTrue(Logger.Level.INFO.getValue() < Logger.Level.WARN.getValue());
    assertTrue(Logger.Level.WARN.getValue() < Logger.Level.ERROR.getValue());
    assertTrue(Logger.Level.ERROR.getValue() < Logger.Level.OFF.getValue());
  }

  @Test
  void testSetGlobalLevel() {
    savedLevel = Logger.getGlobalLevel();
    Logger.setGlobalLevel(Logger.Level.ERROR);
    assertEquals(Logger.Level.ERROR, Logger.getGlobalLevel());
  }

  @Test
  void testIsEnabledAtInfoLevel() {
    savedLevel = Logger.getGlobalLevel();
    Logger.setGlobalLevel(Logger.Level.INFO);
    Logger log = Logger.getLogger("test");
    assertFalse(log.isEnabled(Logger.Level.DEBUG));
    assertTrue(log.isEnabled(Logger.Level.INFO));
    assertTrue(log.isEnabled(Logger.Level.WARN));
    assertTrue(log.isEnabled(Logger.Level.ERROR));
  }

  @Test
  void testIsEnabledAtDebugLevel() {
    savedLevel = Logger.getGlobalLevel();
    Logger.setGlobalLevel(Logger.Level.DEBUG);
    Logger log = Logger.getLogger("test");
    assertTrue(log.isEnabled(Logger.Level.DEBUG));
    assertTrue(log.isEnabled(Logger.Level.INFO));
  }

  @Test
  void testIsEnabledAtOffLevel() {
    savedLevel = Logger.getGlobalLevel();
    Logger.setGlobalLevel(Logger.Level.OFF);
    Logger log = Logger.getLogger("test");
    assertFalse(log.isEnabled(Logger.Level.DEBUG));
    assertFalse(log.isEnabled(Logger.Level.INFO));
    assertFalse(log.isEnabled(Logger.Level.WARN));
    assertFalse(log.isEnabled(Logger.Level.ERROR));
  }

  @Test
  void testLogMethodsDoNotThrow() {
    savedLevel = Logger.getGlobalLevel();
    Logger.setGlobalLevel(Logger.Level.DEBUG);
    Logger log = Logger.getLogger("test");
    // Just verify these don't throw
    log.debug("debug message");
    log.debug("formatted %s", "arg");
    log.info("info message");
    log.info("formatted %s", "arg");
    log.warn("warn message");
    log.warn("formatted %s", "arg");
    log.error("error message");
    log.error("formatted %s", "arg");
    log.error("with throwable", new RuntimeException("test"));
  }

  @Test
  void testSuppressedLogging() {
    savedLevel = Logger.getGlobalLevel();
    Logger.setGlobalLevel(Logger.Level.OFF);
    Logger log = Logger.getLogger("test");
    // Just verify no crash when suppressed
    log.debug("suppressed");
    log.info("suppressed");
    log.warn("suppressed");
    log.error("suppressed");
  }

  // --- control-char scrub: contract + WIRING -------------------------------

  @Test
  void stripControlCharsScrubsStringValuesInTheEventDict() {
    // The PUBLIC contract, matching the reference: an event map in, the same map
    // out with every STRING value scrubbed.
    Map<String, Object> ev = new LinkedHashMap<>();
    ev.put("event", "hello\u0000world");
    ev.put("field", "a\u0007b\u001fc");
    ev.put("n", 42);

    Map<String, Object> out = Logger.stripControlChars(ev);

    assertEquals("helloworld", out.get("event"));
    assertEquals("abc", out.get("field"));
    // Non-string values pass through untouched (the reference's
    // `isinstance(value, str)` guard).
    assertEquals(42, out.get("n"));
  }

  /**
   * The scrub must be ON THE EMISSION PATH, not merely available. This captures what the logger
   * ACTUALLY writes, so deleting the scrub from the emitter turns it RED. A test that called the
   * scrub helper directly would pass even with the wiring removed — which is exactly how this
   * shipped unprotected: the method was public, correct, and called by nothing.
   */
  @Test
  void logOutputHasControlCharsStripped() {
    PrintStream savedOut = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
      Logger.getLogger("inject.test").info("user\u0000said\u001b[31mRED\u0007");
    } finally {
      System.setOut(savedOut);
    }

    String line = captured.toString(StandardCharsets.UTF_8);
    assertFalse(line.contains("\u0000"), "NUL survived into the emitted line: " + line);
    assertFalse(line.contains("\u001b"), "ESC survived into the emitted line: " + line);
    assertFalse(line.contains("\u0007"), "BEL survived into the emitted line: " + line);
    assertTrue(line.contains("usersaid[31mRED"), "unexpected line: " + line);
  }

  /**
   * Tab/newline/CR are LEGAL in a log line and must survive — a scrub that ate them would mangle
   * multi-line messages while still passing the assertion above.
   */
  @Test
  void logOutputKeepsLegalWhitespace() {
    PrintStream savedOut = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
      Logger.getLogger("inject.test").info("line1\tcol\nline2\r end");
    } finally {
      System.setOut(savedOut);
    }
    assertTrue(captured.toString(StandardCharsets.UTF_8).contains("line1\tcol\nline2\r end"));
  }
}
