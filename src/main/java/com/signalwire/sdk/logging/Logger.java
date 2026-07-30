/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.logging;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple logging system with level control via environment variables.
 *
 * <p>Levels: DEBUG, INFO, WARN, ERROR, OFF
 *
 * <p>Environment variables:
 *
 * <ul>
 *   <li>SIGNALWIRE_LOG_LEVEL - set log level (debug/info/warn/error/off)
 *   <li>SIGNALWIRE_LOG_MODE - set to "off" to suppress all output
 * </ul>
 */
public final class Logger {

  /**
   * Severity levels, ordered least to most severe. A message is emitted when its level is at least
   * the global level, so {@link #OFF} — the highest — suppresses everything.
   */
  public enum Level {
    DEBUG(0),
    INFO(1),
    WARN(2),
    ERROR(3),
    OFF(4);

    private final int value;

    Level(int value) {
      this.value = value;
    }

    /**
     * The level's ordinal severity, which is what the threshold comparison uses.
     *
     * @return the severity value, {@code 0} for {@link #DEBUG} through {@code 4} for {@link #OFF}.
     */
    public int getValue() {
      return value;
    }
  }

  private static volatile Level globalLevel;

  static {
    globalLevel = resolveLevel();
  }

  private static Level resolveLevel() {
    String mode = System.getenv("SIGNALWIRE_LOG_MODE");
    if ("off".equalsIgnoreCase(mode)) {
      return Level.OFF;
    }
    String envLevel = System.getenv("SIGNALWIRE_LOG_LEVEL");
    if (envLevel != null) {
      switch (envLevel.toLowerCase(java.util.Locale.ROOT)) {
        case "debug":
          return Level.DEBUG;
        case "info":
          return Level.INFO;
        case "warn":
          return Level.WARN;
        case "error":
          return Level.ERROR;
        case "off":
          return Level.OFF;
        default:
          break;
      }
    }
    return Level.INFO;
  }

  private final String name;

  public Logger(String name) {
    this.name = name;
  }

  /**
   * Obtain a logger tagged with this name. The name appears in every line the logger writes.
   *
   * @param name the logger name.
   * @return a logger for that name.
   */
  public static Logger getLogger(String name) {
    return new Logger(name);
  }

  /**
   * Obtain a logger tagged with the class's simple name.
   *
   * @param clazz the class to name the logger after.
   * @return a logger for that class.
   */
  public static Logger getLogger(Class<?> clazz) {
    return new Logger(clazz.getSimpleName());
  }

  /**
   * Set the severity threshold for every logger in the process, overriding whatever {@code
   * SIGNALWIRE_LOG_LEVEL} / {@code SIGNALWIRE_LOG_MODE} resolved at class-load time.
   *
   * @param level the new threshold.
   */
  public static void setGlobalLevel(Level level) {
    globalLevel = level;
  }

  // -------- logging_config module-level configuration helpers --------
  // The Python reference exposes these as free functions in
  // signalwire.core.logging_config; Java groups them as static helpers on
  // Logger (projected to the module-level free-function names by the surface
  // enumerator). configure_logging is idempotent (one-time global setup);
  // reset_logging_configuration clears the guard so it can run again;
  // strip_control_chars sanitizes log messages of ASCII control characters.

  private static volatile boolean loggingConfigured = false;

  /**
   * One-time global logging configuration. Idempotent — a second call is a no-op until {@link
   * #resetLoggingConfiguration()} runs.
   */
  public static synchronized void configureLogging() {
    if (loggingConfigured) {
      return;
    }
    String envLevel = System.getenv("SIGNALWIRE_LOG_LEVEL");
    if (envLevel != null && !envLevel.isEmpty()) {
      try {
        globalLevel = Level.valueOf(envLevel.trim().toUpperCase(java.util.Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        // Unknown level name — keep the current global level.
      }
    }
    loggingConfigured = true;
  }

  /** Reset the one-time configuration guard so {@link #configureLogging()} can run again. */
  public static synchronized void resetLoggingConfiguration() {
    loggingConfigured = false;
    configureLogging();
  }

  /**
   * Remove ASCII control characters (except tab/newline/carriage-return) from a single string.
   *
   * <p>INTERNAL: the public contract is the event-map form ({@link #stripControlChars(Map)}); this
   * is the per-value scrub that form is built out of, and the unit the emitter needs.
   * Package-private, so it is not part of the SDK's public surface.
   *
   * @param value the raw log string (null-safe → returns null)
   * @return the sanitized string
   */
  static String stripControlCharsValue(String value) {
    if (value == null) {
      return null;
    }
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
        out.append(c);
      }
    }
    return out.toString();
  }

  /**
   * Strip control characters from log event values to prevent log injection.
   *
   * <p>Takes the log event map, scrubs every STRING value, and returns the map. Non-string values
   * pass through untouched.
   *
   * <p>{@link #log} calls this on every emission, so the scrub sits on the real logging path rather
   * than merely being available to callers who remember to invoke it.
   *
   * @param eventDict the log event map (null-safe → returns null)
   * @return a map with every string value sanitized
   */
  public static Map<String, Object> stripControlChars(Map<String, Object> eventDict) {
    if (eventDict == null) {
      return null;
    }
    Map<String, Object> out = new LinkedHashMap<>(eventDict);
    for (Map.Entry<String, Object> e : out.entrySet()) {
      if (e.getValue() instanceof String s) {
        e.setValue(stripControlCharsValue(s));
      }
    }
    return out;
  }

  /**
   * The current process-wide severity threshold, resolved at class-load time from {@code
   * SIGNALWIRE_LOG_MODE} then {@code SIGNALWIRE_LOG_LEVEL}, defaulting to {@link Level#INFO}.
   *
   * @return the current threshold.
   */
  public static Level getGlobalLevel() {
    return globalLevel;
  }

  /**
   * Whether a message at this level would be emitted. Worth checking before building an expensive
   * message that would then be discarded.
   *
   * @param level the level to test.
   * @return {@code true} when the level meets the global threshold.
   */
  public boolean isEnabled(Level level) {
    return level.getValue() >= globalLevel.getValue();
  }

  /**
   * Log at {@link Level#DEBUG}. Control characters in the message are stripped before output, so a
   * value carrying newlines or escape sequences cannot forge log lines.
   *
   * @param message the message.
   */
  public void debug(String message) {
    log(Level.DEBUG, message);
  }

  /**
   * Log a formatted message at {@link Level#DEBUG}.
   *
   * @param format a {@link String#format} pattern.
   * @param args the format arguments.
   */
  public void debug(String format, Object... args) {
    log(Level.DEBUG, format, args);
  }

  /**
   * Log at {@link Level#INFO}, the default threshold. Control characters are stripped before
   * output.
   *
   * @param message the message.
   */
  public void info(String message) {
    log(Level.INFO, message);
  }

  /**
   * Log a formatted message at {@link Level#INFO}.
   *
   * @param format a {@link String#format} pattern.
   * @param args the format arguments.
   */
  public void info(String format, Object... args) {
    log(Level.INFO, format, args);
  }

  /**
   * Log at {@link Level#WARN}. Control characters are stripped before output.
   *
   * @param message the message.
   */
  public void warn(String message) {
    log(Level.WARN, message);
  }

  /**
   * Log a formatted message at {@link Level#WARN}.
   *
   * @param format a {@link String#format} pattern.
   * @param args the format arguments.
   */
  public void warn(String format, Object... args) {
    log(Level.WARN, format, args);
  }

  /**
   * Log at {@link Level#ERROR}. Control characters are stripped before output.
   *
   * @param message the message.
   */
  public void error(String message) {
    log(Level.ERROR, message);
  }

  /**
   * Log a formatted message at {@link Level#ERROR}.
   *
   * @param format a {@link String#format} pattern.
   * @param args the format arguments.
   */
  public void error(String format, Object... args) {
    log(Level.ERROR, format, args);
  }

  /**
   * Log at {@link Level#ERROR} with a stack trace. The message is written to standard error with
   * control characters stripped, followed by the throwable's trace.
   *
   * @param message the message.
   * @param t the throwable whose stack trace to print.
   */
  public void error(String message, Throwable t) {
    if (isEnabled(Level.ERROR)) {
      System.err.printf("[%s] [%s] %s%n", Level.ERROR, name, stripControlCharsValue(message));
      t.printStackTrace(System.err);
    }
  }

  private void log(Level level, String message) {
    if (isEnabled(level)) {
      var stream = (level == Level.ERROR || level == Level.WARN) ? System.err : System.out;
      stream.printf("[%s] [%s] %s%n", level, name, stripControlCharsValue(message));
    }
  }

  private void log(Level level, String format, Object... args) {
    if (isEnabled(level)) {
      var stream = (level == Level.ERROR || level == Level.WARN) ? System.err : System.out;
      stream.printf(
          "[%s] [%s] %s%n", level, name, stripControlCharsValue(String.format(format, args)));
    }
  }
}
