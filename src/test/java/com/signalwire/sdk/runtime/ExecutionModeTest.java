package com.signalwire.sdk.runtime;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ExecutionMode#detect(EnvProvider)}.
 *
 * <p>Tests use a map-backed {@link EnvProvider} rather than real OS
 * environment variables, because Java does not permit mutating the
 * process env without reflection hacks.
 */
class ExecutionModeTest {

    private static EnvProvider envOf(Map<String, String> m) {
        return name -> m.get(name);
    }

    @Test
    void emptyEnvReturnsServer() {
        assertEquals(ExecutionMode.SERVER, ExecutionMode.detect(envOf(Map.of())));
    }

    @Test
    void cgiDetectedFromGatewayInterface() {
        var env = envOf(Map.of("GATEWAY_INTERFACE", "CGI/1.1"));
        assertEquals(ExecutionMode.CGI, ExecutionMode.detect(env));
    }

    @Test
    void lambdaDetectedFromFunctionName() {
        var env = envOf(Map.of("AWS_LAMBDA_FUNCTION_NAME", "my-fn"));
        assertEquals(ExecutionMode.LAMBDA, ExecutionMode.detect(env));
    }

    @Test
    void lambdaDetectedFromTaskRoot() {
        var env = envOf(Map.of("LAMBDA_TASK_ROOT", "/var/task"));
        assertEquals(ExecutionMode.LAMBDA, ExecutionMode.detect(env));
    }

    @Test
    void googleCloudFunctionDetected() {
        assertEquals(ExecutionMode.GOOGLE_CLOUD_FUNCTION,
                ExecutionMode.detect(envOf(Map.of("FUNCTION_TARGET", "main"))));
        assertEquals(ExecutionMode.GOOGLE_CLOUD_FUNCTION,
                ExecutionMode.detect(envOf(Map.of("K_SERVICE", "svc"))));
        assertEquals(ExecutionMode.GOOGLE_CLOUD_FUNCTION,
                ExecutionMode.detect(envOf(Map.of("GOOGLE_CLOUD_PROJECT", "proj"))));
    }

    @Test
    void azureFunctionDetected() {
        assertEquals(ExecutionMode.AZURE_FUNCTION,
                ExecutionMode.detect(envOf(Map.of("AZURE_FUNCTIONS_ENVIRONMENT", "Prod"))));
        assertEquals(ExecutionMode.AZURE_FUNCTION,
                ExecutionMode.detect(envOf(Map.of("FUNCTIONS_WORKER_RUNTIME", "java"))));
        assertEquals(ExecutionMode.AZURE_FUNCTION,
                ExecutionMode.detect(envOf(Map.of("AzureWebJobsStorage", "UseDevelopmentStorage"))));
    }

    @Test
    void cgiTakesPrecedenceOverLambda() {
        // Overlapping vars should not change detection order. CGI is
        // checked before Lambda in the reference Python implementation.
        var env = envOf(Map.of(
                "GATEWAY_INTERFACE", "CGI/1.1",
                "AWS_LAMBDA_FUNCTION_NAME", "fn"));
        assertEquals(ExecutionMode.CGI, ExecutionMode.detect(env));
    }

    @Test
    void emptyStringNotTreatedAsSet() {
        // Environment variables explicitly set to empty string should
        // not trigger detection (matches Python's implicit falsy check).
        var env = envOf(Map.of("AWS_LAMBDA_FUNCTION_NAME", ""));
        assertEquals(ExecutionMode.SERVER, ExecutionMode.detect(env));
    }

    @Test
    void nullEnvValueNotTreatedAsSet() {
        // Explicit null mapping must not flip detection. Use a HashMap
        // because Map.of() forbids nulls.
        Map<String, String> m = new HashMap<>();
        m.put("AWS_LAMBDA_FUNCTION_NAME", null);
        EnvProvider env = name -> m.get(name);
        assertEquals(ExecutionMode.SERVER, ExecutionMode.detect(env));
    }

    @Test
    void detectWithoutArgsDelegatesToSystem() {
        // Smoke test: the zero-arg variant must not throw even when
        // the real environment is unpredictable.
        assertDoesNotThrow((ThrowingSupplier<ExecutionMode>) ExecutionMode::detect);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static void assertDoesNotThrow(ThrowingSupplier<?> s) {
        try { s.get(); }
        catch (Exception e) { fail("Unexpected exception: " + e); }
    }
}
