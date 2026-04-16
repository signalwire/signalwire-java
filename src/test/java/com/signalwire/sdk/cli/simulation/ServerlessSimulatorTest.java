package com.signalwire.sdk.cli.simulation;

import com.signalwire.sdk.runtime.EnvProvider;
import com.signalwire.sdk.runtime.ExecutionMode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ServerlessSimulator}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Platform parsing — rejects unimplemented platforms with a clear error.</li>
 *   <li>Layered {@link EnvProvider} — simulated keys win, real env is fallback,
 *       masked keys always return null.</li>
 *   <li>{@code SWML_PROXY_URL_BASE} is masked even if the real env has it.</li>
 *   <li>The layered env makes {@link ExecutionMode#detect(EnvProvider)} see LAMBDA.</li>
 * </ul>
 */
class ServerlessSimulatorTest {

    private static EnvProvider envOf(Map<String, String> m) {
        Map<String, String> copy = new LinkedHashMap<>(m);
        return copy::get;
    }

    @Test
    void parsePlatformAcceptsLambda() {
        assertEquals(ServerlessSimulator.Platform.LAMBDA,
                ServerlessSimulator.parsePlatform("lambda"));
        assertEquals(ServerlessSimulator.Platform.LAMBDA,
                ServerlessSimulator.parsePlatform("LAMBDA"));
        assertEquals(ServerlessSimulator.Platform.LAMBDA,
                ServerlessSimulator.parsePlatform(" lambda "));
    }

    @Test
    void parsePlatformRejectsUnimplementedPlatforms() {
        for (String unsupported : new String[]{"gcf", "azure", "cgi", "cloud_function",
                "azure_function", "gcp", "unknown"}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ServerlessSimulator.parsePlatform(unsupported),
                    "expected failure for platform: " + unsupported);
            assertTrue(ex.getMessage().contains("Unsupported platform"),
                    "error message should mention 'Unsupported platform'; got: " + ex.getMessage());
            assertTrue(ex.getMessage().contains("lambda"),
                    "error message should list 'lambda' as the supported value; got: " + ex.getMessage());
        }
    }

    @Test
    void parsePlatformRejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> ServerlessSimulator.parsePlatform(null));
    }

    @Test
    void lambdaPresetHasExpectedKeys() {
        Map<String, String> preset = ServerlessSimulator.presetFor(
                ServerlessSimulator.Platform.LAMBDA);
        assertTrue(preset.containsKey("AWS_LAMBDA_FUNCTION_NAME"));
        assertTrue(preset.containsKey("AWS_LAMBDA_FUNCTION_URL"));
        assertTrue(preset.containsKey("AWS_REGION"));
        assertTrue(preset.containsKey("LAMBDA_TASK_ROOT"));
    }

    @Test
    void layeredEnvProviderOverlaysSimulatedOnTopOfRealEnv() {
        // Real env has some unrelated key; simulator should pass it through.
        EnvProvider real = envOf(Map.of(
                "PATH", "/usr/bin",
                "HOME", "/home/user"));
        ServerlessSimulator sim = new ServerlessSimulator(
                ServerlessSimulator.Platform.LAMBDA, real, Map.of());
        EnvProvider layered = sim.buildEnvProvider();

        assertEquals("/usr/bin", layered.get("PATH"));
        assertEquals("/home/user", layered.get("HOME"));
        assertNotNull(layered.get("AWS_LAMBDA_FUNCTION_NAME"));
        assertNotNull(layered.get("AWS_REGION"));
    }

    @Test
    void layeredEnvMasksProxyUrlBaseEvenIfRealEnvHasIt() {
        EnvProvider real = envOf(Map.of(
                "SWML_PROXY_URL_BASE", "https://real-proxy.example.com"));
        ServerlessSimulator sim = new ServerlessSimulator(
                ServerlessSimulator.Platform.LAMBDA, real, Map.of());

        assertTrue(sim.proxyUrlBaseMaskedFromRealEnv(),
                "simulator should report that it's masking the real SWML_PROXY_URL_BASE");

        EnvProvider layered = sim.buildEnvProvider();
        assertNull(layered.get("SWML_PROXY_URL_BASE"),
                "simulated view must hide the real SWML_PROXY_URL_BASE");
    }

    @Test
    void layeredEnvDoesNotMutateRealEnv() {
        String realValue = System.getenv("PATH");
        EnvProvider real = envOf(Map.of("SOME_KEY", "some_real_value"));
        ServerlessSimulator sim = new ServerlessSimulator(
                ServerlessSimulator.Platform.LAMBDA, real, Map.of());
        sim.buildEnvProvider().get("AWS_LAMBDA_FUNCTION_NAME");

        // System.getenv() must be untouched.
        assertEquals(realValue, System.getenv("PATH"));
        // The simulator's builder must not have cleared the real env's
        // explicit value for other keys.
        assertEquals("some_real_value", real.get("SOME_KEY"));
    }

    @Test
    void layeredEnvUserOverridesWinOverPreset() {
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("AWS_LAMBDA_FUNCTION_NAME", "custom-fn");
        overrides.put("AWS_REGION", "us-west-2");
        ServerlessSimulator sim = new ServerlessSimulator(
                ServerlessSimulator.Platform.LAMBDA, EnvProvider.SYSTEM, overrides);

        EnvProvider layered = sim.buildEnvProvider();
        assertEquals("custom-fn", layered.get("AWS_LAMBDA_FUNCTION_NAME"));
        assertEquals("us-west-2", layered.get("AWS_REGION"));
    }

    @Test
    void executionModeDetectsLambdaThroughLayeredEnv() {
        // Confirm the layered provider plugs into ExecutionMode.detect()
        // correctly — this is the load-bearing integration point.
        EnvProvider real = envOf(Map.of()); // nothing in real env
        ServerlessSimulator sim = new ServerlessSimulator(
                ServerlessSimulator.Platform.LAMBDA, real, Map.of());
        EnvProvider layered = sim.buildEnvProvider();

        assertEquals(ExecutionMode.LAMBDA, ExecutionMode.detect(layered));
    }

    @Test
    void executionModeDetectsLambdaEvenWithOtherPlatformKeysInRealEnv() {
        // Real env has stale GCF keys from a previous simulation. The
        // layered env must mask them so detection still returns LAMBDA.
        EnvProvider real = envOf(Map.of(
                "FUNCTION_TARGET", "old-gcf-fn",
                "K_SERVICE", "old-gcf-service",
                "GATEWAY_INTERFACE", "CGI/1.1"));
        ServerlessSimulator sim = new ServerlessSimulator(
                ServerlessSimulator.Platform.LAMBDA, real, Map.of());
        EnvProvider layered = sim.buildEnvProvider();

        assertEquals(ExecutionMode.LAMBDA, ExecutionMode.detect(layered),
                "stale GCF/CGI keys in real env must not leak into simulated LAMBDA view");
    }

    @Test
    void proxyUrlBaseMaskedFromRealEnvIsFalseWhenRealEnvIsUnset() {
        EnvProvider real = envOf(Map.of("PATH", "/usr/bin"));
        ServerlessSimulator sim = new ServerlessSimulator(
                ServerlessSimulator.Platform.LAMBDA, real, Map.of());
        assertFalse(sim.proxyUrlBaseMaskedFromRealEnv());
    }
}
