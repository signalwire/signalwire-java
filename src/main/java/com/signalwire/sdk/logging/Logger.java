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
