package com.signalwire.agents;

import com.signalwire.agents.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Logger utility.
 */
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
}
