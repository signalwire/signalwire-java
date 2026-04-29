/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import com.signalwire.sdk.cli.SwaigTest;
import com.signalwire.sdk.cli.fixtures.EmptyServiceFixture;
import com.signalwire.sdk.cli.fixtures.NotAServiceFixture;
import com.signalwire.sdk.cli.fixtures.StandaloneServiceFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@code SwaigTest --class <FQCN> --list-tools}, the in-process
 * file-loader path that introspects a {@link com.signalwire.sdk.swml.Service}
 * subclass without starting an HTTP server. This is the path used to list
 * SWAIG tools on a non-AgentBase Service (sidecar / standalone SWAIG host)
 * whose tools are invisible to {@code --list-tools --url} because the
 * SWML doc has no {@code <ai>} verb to walk.
 */
class SwaigTestCliTest {

    private ByteArrayOutputStream stdout;
    private ByteArrayOutputStream stderr;
    private PrintStream realOut;
    private PrintStream realErr;

    @BeforeEach
    void setUp() {
        stdout = new ByteArrayOutputStream();
        stderr = new ByteArrayOutputStream();
        realOut = System.out;
        realErr = System.err;
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        if (realOut != null) System.setOut(realOut);
        if (realErr != null) System.setErr(realErr);
    }

    private String streamsSnapshot() {
        return "stdout=[" + stdout.toString(StandardCharsets.UTF_8) + "]"
                + " stderr=[" + stderr.toString(StandardCharsets.UTF_8) + "]";
    }

    // -----------------------------------------------------------------
    // Happy path: --class --list-tools on a Service with one SWAIG tool
    // -----------------------------------------------------------------

    @Test
    void listToolsOnStandaloneServicePrintsRegisteredTool() {
        int code = SwaigTest.run(new String[]{
                "--class", StandaloneServiceFixture.class.getName(),
                "--list-tools"
        });
        String out = stdout.toString(StandardCharsets.UTF_8);
        String snap = streamsSnapshot();

        assertEquals(0, code, "exit code should be 0. " + snap);
        assertTrue(out.contains("Tools (1)"),
                "should print tool count header. " + snap);
        assertTrue(out.contains("- lookup_competitor"),
                "should list the 'lookup_competitor' tool name. " + snap);
        assertTrue(out.contains("description: Look up competitor pricing"),
                "should print the tool description. " + snap);
        assertTrue(out.contains("competitor"),
                "should print the 'competitor' parameter. " + snap);
    }

    @Test
    void listToolsDoesNotStartHttpServer() {
        // The --class path must not bind a socket. We can't directly
        // observe socket binding from inside the test JVM in a portable
        // way, but we can assert the run is fast and exits cleanly —
        // a serve() call would block forever or fail on port 3000
        // already bound by another test.
        long start = System.nanoTime();
        int code = SwaigTest.run(new String[]{
                "--class", StandaloneServiceFixture.class.getName(),
                "--list-tools"
        });
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertEquals(0, code, "list-tools should not error. " + streamsSnapshot());
        assertTrue(elapsedMs < 5_000,
                "list-tools should return promptly (took " + elapsedMs + " ms)");
    }

    // -----------------------------------------------------------------
    // Empty registry
    // -----------------------------------------------------------------

    @Test
    void listToolsOnServiceWithNoToolsReportsNoneFound() {
        int code = SwaigTest.run(new String[]{
                "--class", EmptyServiceFixture.class.getName(),
                "--list-tools"
        });
        String out = stdout.toString(StandardCharsets.UTF_8);
        String snap = streamsSnapshot();

        assertEquals(0, code, snap);
        assertTrue(out.contains("No tools found"),
                "should report no tools. " + snap);
    }

    // -----------------------------------------------------------------
    // Argument validation
    // -----------------------------------------------------------------

    @Test
    void classFlagWithoutListToolsRejected() {
        int code = SwaigTest.run(new String[]{
                "--class", StandaloneServiceFixture.class.getName()
        });
        String err = stderr.toString(StandardCharsets.UTF_8);
        assertNotEquals(0, code, "missing --list-tools should error");
        assertTrue(err.contains("--list-tools"),
                "error should mention --list-tools. err=" + err);
    }

    @Test
    void classFlagWithUrlRejected() {
        int code = SwaigTest.run(new String[]{
                "--class", StandaloneServiceFixture.class.getName(),
                "--list-tools",
                "--url", "http://localhost:3000"
        });
        String err = stderr.toString(StandardCharsets.UTF_8);
        assertNotEquals(0, code, "combining --class with --url should error");
        assertTrue(err.contains("--url") && err.contains("--class"),
                "error should mention both flags. err=" + err);
    }

    @Test
    void classFlagWithSimulateServerlessRejected() {
        int code = SwaigTest.run(new String[]{
                "--class", StandaloneServiceFixture.class.getName(),
                "--list-tools",
                "--simulate-serverless", "lambda"
        });
        String err = stderr.toString(StandardCharsets.UTF_8);
        assertNotEquals(0, code, "combining --class with --simulate-serverless should error");
        assertTrue(err.contains("--simulate-serverless"),
                "error should mention --simulate-serverless. err=" + err);
    }

    @Test
    void classFlagWithDumpSwmlRejected() {
        int code = SwaigTest.run(new String[]{
                "--class", StandaloneServiceFixture.class.getName(),
                "--dump-swml"
        });
        String err = stderr.toString(StandardCharsets.UTF_8);
        assertNotEquals(0, code, "--dump-swml not supported in --class mode");
        assertTrue(err.contains("--dump-swml") || err.contains("--list-tools"),
                "error should explain. err=" + err);
    }

    @Test
    void classFlagWithExecRejected() {
        int code = SwaigTest.run(new String[]{
                "--class", StandaloneServiceFixture.class.getName(),
                "--exec", "lookup_competitor"
        });
        String err = stderr.toString(StandardCharsets.UTF_8);
        assertNotEquals(0, code, "--exec not supported in --class mode");
        assertTrue(err.contains("--exec") || err.contains("--list-tools"),
                "error should explain. err=" + err);
    }

    @Test
    void unknownClassProducesClearError() {
        int code = SwaigTest.run(new String[]{
                "--class", "com.does.not.Exist",
                "--list-tools"
        });
        String err = stderr.toString(StandardCharsets.UTF_8);
        assertNotEquals(0, code, "unknown class should error");
        assertTrue(err.contains("not found") || err.contains("classpath"),
                "error should mention classpath / not found. err=" + err);
    }

    @Test
    void nonServiceClassRejected() {
        int code = SwaigTest.run(new String[]{
                "--class", NotAServiceFixture.class.getName(),
                "--list-tools"
        });
        String err = stderr.toString(StandardCharsets.UTF_8);
        assertNotEquals(0, code, "non-Service class should error");
        assertTrue(err.contains("Service"),
                "error should mention Service. err=" + err);
    }

    @Test
    void classFlagRequiresArgument() {
        int code = SwaigTest.run(new String[]{"--class"});
        String err = stderr.toString(StandardCharsets.UTF_8);
        assertNotEquals(0, code, "--class with no value should error");
        assertTrue(err.contains("--class"),
                "error should mention --class. err=" + err);
    }
}
