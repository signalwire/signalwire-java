/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.logging;

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

  public static Logger getLogger(String name) {
    return new Logger(name);
  }

  public static Logger getLogger(Class<?> clazz) {
    return new Logger(clazz.getSimpleName());
  }

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
   * #resetLoggingConfiguration()} runs. Mirrors logging_config.configure_logging.
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

  /**
   * Reset the one-time configuration guard so {@link #configureLogging()} can run again. Mirrors
   * logging_config.reset_logging_configuration.
   */
  public static synchronized void resetLoggingConfiguration() {
    loggingConfigured = false;
    configureLogging();
  }

  /**
   * Remove ASCII control characters (except tab/newline/carriage-return) from a log string. Mirrors
   * logging_config.strip_control_chars (a structlog processor in Python).
   *
   * @param value the raw log string (null-safe → returns null)
   * @return the sanitized string
   */
  public static String stripControlChars(String value) {
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

  public static Level getGlobalLevel() {
    return globalLevel;
  }

  public boolean isEnabled(Level level) {
    return level.getValue() >= globalLevel.getValue();
  }

  public void debug(String message) {
    log(Level.DEBUG, message);
  }

  public void debug(String format, Object... args) {
    log(Level.DEBUG, format, args);
  }

  public void info(String message) {
    log(Level.INFO, message);
  }

  public void info(String format, Object... args) {
    log(Level.INFO, format, args);
  }

  public void warn(String message) {
    log(Level.WARN, message);
  }

  public void warn(String format, Object... args) {
    log(Level.WARN, format, args);
  }

  public void error(String message) {
    log(Level.ERROR, message);
  }

  public void error(String format, Object... args) {
    log(Level.ERROR, format, args);
  }

  public void error(String message, Throwable t) {
    if (isEnabled(Level.ERROR)) {
      System.err.printf("[%s] [%s] %s%n", Level.ERROR, name, message);
      t.printStackTrace(System.err);
    }
  }

  private void log(Level level, String message) {
    if (isEnabled(level)) {
      var stream = (level == Level.ERROR || level == Level.WARN) ? System.err : System.out;
      stream.printf("[%s] [%s] %s%n", level, name, message);
    }
  }

  private void log(Level level, String format, Object... args) {
    if (isEnabled(level)) {
      var stream = (level == Level.ERROR || level == Level.WARN) ? System.err : System.out;
      stream.printf("[%s] [%s] %s%n", level, name, String.format(format, args));
    }
  }
}
