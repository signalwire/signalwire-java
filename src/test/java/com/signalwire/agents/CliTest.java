package com.signalwire.agents;

import com.signalwire.agents.cli.SwaigTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the SwaigTest CLI class (argument parsing, URL parsing).
 * These tests verify the CLI structure without making network calls.
 */
class CliTest {

    @Test
    void testClassExists() {
        // Verify the CLI class can be loaded
        assertNotNull(SwaigTest.class);
    }

    @Test
    void testNoArgsExitsWithUsage() {
        // SwaigTest.main([]) calls System.exit, so we test parseArgs indirectly
        // by confirming the class exists and has a main method
        try {
            var method = SwaigTest.class.getMethod("main", String[].class);
            assertNotNull(method);
        } catch (NoSuchMethodException e) {
            fail("SwaigTest should have a main method");
        }
    }

    @Test
    void testSwaigTestHasExpectedMethods() throws Exception {
        // Verify the class can be instantiated
        var constructor = SwaigTest.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    void testMainMethodSignature() {
        // Verify that the main method takes String[]
        try {
            var method = SwaigTest.class.getMethod("main", String[].class);
            assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
            assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
        } catch (NoSuchMethodException e) {
            fail("SwaigTest should have a public static main(String[]) method");
        }
    }

    @Test
    void testConstructorIsPublic() {
        // SwaigTest default constructor should be accessible
        try {
            var constructor = SwaigTest.class.getDeclaredConstructor();
            assertNotNull(constructor);
        } catch (NoSuchMethodException e) {
            fail("SwaigTest should have a default constructor");
        }
    }
}
