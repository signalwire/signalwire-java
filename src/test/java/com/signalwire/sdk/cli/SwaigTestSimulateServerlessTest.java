package com.signalwire.sdk.cli;

import com.signalwire.sdk.cli.fixtures.RootAgentFixture;
import com.signalwire.sdk.cli.fixtures.RoutedAgentFixture;
import com.signalwire.sdk.cli.fixtures.ThrowingToolAgentFixture;
import com.signalwire.sdk.runtime.EnvProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@code SwaigTest --simulate-serverless}.
 *
 * <p>Covers the Phase 10 invariants:
 * <ul>
 *   <li>Simulated env vars visible through the injected {@link EnvProvider}.</li>
 *   <li>Real {@link System#getenv()} is untouched at every step.</li>
 *   <li>{@code SWML_PROXY_URL_BASE} in the real env is masked by the
 *       simulated view so Lambda URL generation is actually exercised.</li>
 *   <li>Restoration works on exception paths (try/finally).</li>
 *   <li>Unimplemented platforms error out cleanly — no silent server fallback.</li>
 *   <li>Non-root routes appear in dumped SWML webhook URLs (regression for
 *       the Lambda route-preservation bug).</li>
 * </ul>
 */
class SwaigTestSimulateServerlessTest {

    private ByteArrayOutputStream stdout;
    private ByteArrayOutputStream stderr;
    private PrintStream realOut;
    private PrintStream realErr;

    @BeforeEach
    void setUp() {
        RootAgentFixture.reset();
        stdout = new ByteArrayOutputStream();
        stderr = new ByteArrayOutputStream();
        realOut = System.out;
        realErr = System.err;
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        // Always restore System.out / System.err so a failing test
        // doesn't wedge subsequent ones.
        if (realOut != null) System.setOut(realOut);
        if (realErr != null) System.setErr(realErr);
    }

    private String restoreStreams() {
        // No-op now (handled by @AfterEach) but kept so callers can
        // emit a diagnostic snapshot if an assertion fails.
        return "stdout=[" + stdout.toString(StandardCharsets.UTF_8) + "]"
                + " stderr=[" + stderr.toString(StandardCharsets.UTF_8) + "]";
    }

    /**
     * Drive {@link SwaigTest#run(String[])} with a simulator backed by
     * the given "real env" view. Uses reflection to inject the real-env
     * override into a fresh {@code SwaigTest} instance, since we can't
     * actually mutate {@link System#getenv()}.
     */
    private static int runWithRealEnv(String[] args, EnvProvider realEnv) throws Exception {
        // We can't call SwaigTest.run(args) directly because it
        // constructs its own instance. Instead, mirror its logic but
        // inject the override first.
        SwaigTest cli = new SwaigTest();
        cli.setRealEnvOverrideForTests(realEnv);

        Method parseArgs = SwaigTest.class.getDeclaredMethod("parseArgs", String[].class);
        parseArgs.setAccessible(true);
        Method runMethod = SwaigTest.class.getDeclaredMethod("run");
        runMethod.setAccessible(true);

        try {
            parseArgs.invoke(cli, (Object) args);
            runMethod.invoke(cli);
            return 0;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause.getClass().getSimpleName().equals("HelpRequested")) {
                return 0;
            }
            if (cause instanceof IllegalArgumentException) {
                System.err.println("Error: " + cause.getMessage());
                return 1;
            }
            System.err.println("Error: " + cause.getMessage());
            return 1;
        }
    }

    // -----------------------------------------------------------------
    // Platform validation
    // -----------------------------------------------------------------

    @Test
    void rejectsUnimplementedPlatformGcf() throws Exception {
        int code = SwaigTest.run(new String[]{
                RootAgentFixture.class.getName(),
                "--simulate-serverless", "gcf",
                "--dump-swml"
        });
        String streams = restoreStreams();
        assertNotEquals(0, code, "unimplemented platform should exit non-zero. " + streams);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Unsupported platform"),
                "stderr should contain 'Unsupported platform'. " + streams);
    }

    @Test
    void rejectsUnimplementedPlatformAzure() throws Exception {
        int code = SwaigTest.run(new String[]{
                RootAgentFixture.class.getName(),
                "--simulate-serverless", "azure",
                "--dump-swml"
        });
        String streams = restoreStreams();
        assertNotEquals(0, code);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Unsupported platform"),
                "stderr=[" + streams + "]");
    }

    @Test
    void rejectsUnimplementedPlatformCgi() throws Exception {
        int code = SwaigTest.run(new String[]{
                RootAgentFixture.class.getName(),
                "--simulate-serverless", "cgi",
                "--dump-swml"
        });
        String streams = restoreStreams();
        assertNotEquals(0, code);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Unsupported platform"),
                "stderr=[" + streams + "]");
    }

    // -----------------------------------------------------------------
    // Simulated env visibility
    // -----------------------------------------------------------------

    @Test
    void simulatedEnvIsVisibleToAgentViaInjectedProvider() throws Exception {
        int code = SwaigTest.run(new String[]{
                RootAgentFixture.class.getName(),
                "--simulate-serverless", "lambda",
                "--dump-swml"
        });
        String streams = restoreStreams();
        assertEquals(0, code, "dump-swml through lambda should succeed. " + streams);

        // The fixture stashes the EnvProvider it received; simulated
        // Lambda keys should be visible through it.
        EnvProvider seen = RootAgentFixture.lastEnvSeen;
        assertNotNull(seen, "fixture never received an EnvProvider");
        assertNotNull(seen.get("AWS_LAMBDA_FUNCTION_NAME"),
                "simulated env should expose AWS_LAMBDA_FUNCTION_NAME");
        assertNotNull(seen.get("AWS_REGION"),
                "simulated env should expose AWS_REGION");
    }

    @Test
    void realSystemGetenvIsUntouchedDuringSimulation() throws Exception {
        // Snapshot a handful of real env vars before/after a simulation.
        String pathBefore = System.getenv("PATH");
        String lambdaBefore = System.getenv("AWS_LAMBDA_FUNCTION_NAME");

        int code = SwaigTest.run(new String[]{
                RootAgentFixture.class.getName(),
                "--simulate-serverless", "lambda",
                "--dump-swml"
        });
        String streams = restoreStreams();
        assertEquals(0, code, streams);

        assertEquals(pathBefore, System.getenv("PATH"),
                "PATH must not be mutated by the simulator");
        assertEquals(lambdaBefore, System.getenv("AWS_LAMBDA_FUNCTION_NAME"),
                "AWS_LAMBDA_FUNCTION_NAME must not be mutated in the real process env");
    }

    // -----------------------------------------------------------------
    // Output shape
    // -----------------------------------------------------------------

    @Test
    void dumpSwmlEmitsAiVerbWithLambdaStyleWebhookUrl() throws Exception {
        int code = SwaigTest.run(new String[]{
                RootAgentFixture.class.getName(),
                "--simulate-serverless", "lambda",
                "--dump-swml",
                "--raw"
        });
        String out = stdout.toString(StandardCharsets.UTF_8);
        String streams = restoreStreams();
        assertEquals(0, code, streams);
        assertTrue(out.contains("\"sections\""),
                "dumped SWML should contain 'sections'. out=" + out);
        assertTrue(out.contains("web_hook_url"),
                "dumped SWML should contain a web_hook_url. out=" + out);
        assertTrue(out.contains(".lambda-url.") || out.contains("lambda-url."),
                "webhook URL should be Lambda-style. out=" + out);
    }

    @Test
    void bareSimulationRendersSwmlAndExitsZero() throws Exception {
        int code = SwaigTest.run(new String[]{
                RootAgentFixture.class.getName(),
                "--simulate-serverless", "lambda"
        });
        String streams = restoreStreams();
        assertEquals(0, code, streams);
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("sections"),
                "bare simulation should print SWML. " + streams);
    }

    @Test
    void execToolDispatchesThroughLambdaAdapter() throws Exception {
        RootAgentFixture.reset();
        int code = SwaigTest.run(new String[]{
                RootAgentFixture.class.getName(),
                "--simulate-serverless", "lambda",
                "--exec", "echo",
                "--param", "msg=hi",
                "--raw"
        });
        String out = stdout.toString(StandardCharsets.UTF_8);
        String streams = restoreStreams();
        assertEquals(0, code, streams);
        assertEquals(1, RootAgentFixture.toolInvocations,
                "fixture tool handler should have been invoked exactly once. " + streams);
        assertTrue(out.contains("echo: hi"),
                "adapter response should echo 'hi'. out=" + out);
    }

    // -----------------------------------------------------------------
    // SWML_PROXY_URL_BASE masking
    // -----------------------------------------------------------------

    @Test
    void simulatedProxyUrlBaseIsMaskedFromRealEnv() throws Exception {
        // Pretend the real OS env has SWML_PROXY_URL_BASE set. The
        // simulator must mask it and synthesize Lambda-style webhook URLs.
        Map<String, String> realEnv = new LinkedHashMap<>();
        realEnv.put("SWML_PROXY_URL_BASE", "https://real-proxy.example.com");
        EnvProvider fakeReal = realEnv::get;

        int code = runWithRealEnv(new String[]{
                RootAgentFixture.class.getName(),
                "--simulate-serverless", "lambda",
                "--dump-swml",
                "--raw"
        }, fakeReal);

        String out = stdout.toString(StandardCharsets.UTF_8);
        String err = stderr.toString(StandardCharsets.UTF_8);
        String streams = restoreStreams();
        assertEquals(0, code, streams);

        assertFalse(out.contains("real-proxy.example.com"),
                "the real-env proxy base must NOT leak into simulated SWML. out=" + out);
        assertTrue(out.contains(".lambda-url.") || out.contains("lambda-url."),
                "simulated webhook URLs must use Lambda-style origins. out=" + out);

        assertTrue(err.contains("SWML_PROXY_URL_BASE") && err.contains("masking"),
                "stderr should warn about masked SWML_PROXY_URL_BASE. err=" + err);
    }

    @Test
    void routePreservedInDumpedSwmlForNonRootAgent() throws Exception {
        // Load-bearing regression: a routed agent must emit webhook URLs
        // with its route prefix, even through the simulator.
        int code = SwaigTest.run(new String[]{
                RoutedAgentFixture.class.getName(),
                "--simulate-serverless", "lambda",
                "--dump-swml",
                "--raw"
        });
        String out = stdout.toString(StandardCharsets.UTF_8);
        String streams = restoreStreams();
        assertEquals(0, code, streams);

        assertTrue(out.contains("/my-agent/swaig"),
                "dumped SWML must contain '/my-agent/swaig' (route preserved). out=" + out);
        assertFalse(out.contains("\"web_hook_url\":\"https://")
                        && out.matches("(?s).*lambda-url\\.[^\"]*\\.on\\.aws/swaig.*"),
                "webhook URL must not drop the route prefix. out=" + out);
    }

    @Test
    void routePreservedWhenSwmlProxyUrlBaseMasked() throws Exception {
        // Combination of the two regressions: route prefix must survive
        // even when SWML_PROXY_URL_BASE is masked from the real env.
        Map<String, String> realEnv = new LinkedHashMap<>();
        realEnv.put("SWML_PROXY_URL_BASE", "https://real-proxy.example.com");

        int code = runWithRealEnv(new String[]{
                RoutedAgentFixture.class.getName(),
                "--simulate-serverless", "lambda",
                "--dump-swml",
                "--raw"
        }, realEnv::get);

        String out = stdout.toString(StandardCharsets.UTF_8);
        String streams = restoreStreams();
        assertEquals(0, code, streams);

        assertTrue(out.contains("/my-agent/swaig"),
                "route prefix must survive SWML_PROXY_URL_BASE masking. out=" + out);
        assertFalse(out.contains("real-proxy.example.com"),
                "real-env proxy base must not appear. out=" + out);
    }

    // -----------------------------------------------------------------
    // Restoration on success + failure paths
    // -----------------------------------------------------------------

    @Test
    void realEnvSurvivesExceptionPath() throws Exception {
        // Capture a snapshot of the real process env before running
        // a simulation whose tool handler throws.
        Map<String, String> before = snapshotRelevantEnv();

        int code = SwaigTest.run(new String[]{
                ThrowingToolAgentFixture.class.getName(),
                "--simulate-serverless", "lambda",
                "--exec", "explode",
                "--raw"
        });
        String streams = restoreStreams();
        // The Lambda handler returns a 500 JSON response for tool
        // exceptions, so the CLI exits 0 (the non-error-code path)
        // but the response body encodes the error. The invariant we're
        // testing is restoration — not the specific exit code.
        assertTrue(code == 0 || code == 1, "unexpected exit code " + code + ": " + streams);

        Map<String, String> after = snapshotRelevantEnv();
        assertEquals(before, after,
                "real env must survive the tool-exception path. " + streams);
    }

    @Test
    void realEnvSurvivesSuccessPath() throws Exception {
        Map<String, String> before = snapshotRelevantEnv();

        int code = SwaigTest.run(new String[]{
                RootAgentFixture.class.getName(),
                "--simulate-serverless", "lambda",
                "--dump-swml",
                "--raw"
        });
        String streams = restoreStreams();
        assertEquals(0, code, streams);

        Map<String, String> after = snapshotRelevantEnv();
        assertEquals(before, after,
                "real env must survive the success path. " + streams);
    }

    /**
     * Snapshot of process env vars relevant to simulation. If simulation
     * ever mutates {@link System#getenv()} these values would diverge.
     */
    private static Map<String, String> snapshotRelevantEnv() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String key : new String[]{
                "AWS_LAMBDA_FUNCTION_NAME",
                "AWS_LAMBDA_FUNCTION_URL",
                "AWS_REGION",
                "LAMBDA_TASK_ROOT",
                "SWML_PROXY_URL_BASE",
                "SWML_BASIC_AUTH_USER",
                "SWML_BASIC_AUTH_PASSWORD",
                "_HANDLER"
        }) {
            m.put(key, System.getenv(key));
        }
        return m;
    }

    // -----------------------------------------------------------------
    // Argument validation
    // -----------------------------------------------------------------

    @Test
    void simulateServerlessRequiresAgentClass() throws Exception {
        int code = SwaigTest.run(new String[]{
                "--simulate-serverless", "lambda",
                "--dump-swml"
        });
        String streams = restoreStreams();
        assertNotEquals(0, code);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("agent-class"),
                "stderr should explain agent-class is required. " + streams);
    }

    @Test
    void simulateServerlessRejectsCombinationWithUrl() throws Exception {
        int code = SwaigTest.run(new String[]{
                RootAgentFixture.class.getName(),
                "--simulate-serverless", "lambda",
                "--url", "http://localhost:3000"
        });
        String streams = restoreStreams();
        assertNotEquals(0, code);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("--url")
                && stderr.toString(StandardCharsets.UTF_8).contains("simulate-serverless"),
                "stderr should flag the incompatible combination. " + streams);
    }

    @Test
    void simulateServerlessRejectsListToolsForNow() throws Exception {
        int code = SwaigTest.run(new String[]{
                RootAgentFixture.class.getName(),
                "--simulate-serverless", "lambda",
                "--list-tools"
        });
        String streams = restoreStreams();
        assertNotEquals(0, code);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("--list-tools"),
                "stderr should explain --list-tools is not supported in simulation. " + streams);
    }

    @Test
    void missingAgentClassProducesClearError() throws Exception {
        int code = SwaigTest.run(new String[]{
                "com.does.not.Exist",
                "--simulate-serverless", "lambda",
                "--dump-swml"
        });
        String streams = restoreStreams();
        assertNotEquals(0, code);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("not found")
                        || stderr.toString(StandardCharsets.UTF_8).contains("classpath"),
                "stderr should mention classpath / not found. " + streams);
    }
}
