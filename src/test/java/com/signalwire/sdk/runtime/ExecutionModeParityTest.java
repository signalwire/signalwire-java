package com.signalwire.sdk.runtime;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-language parity tests for
 * {@link ExecutionMode#getExecutionMode()} and
 * {@link ExecutionMode#isServerlessMode()}.
 *
 * <p>Mirrors the Python reference at
 * {@code tests/unit/utils/test_execution_mode.py}: every branch of the
 * env-var detection ladder must match the same precedence and return
 * the same canonical string.
 *
 * <p>Java cannot mutate {@link System#getenv()} from a test, so we
 * exercise the env-driven branches via the {@link EnvProvider}-taking
 * overloads. The branchless {@code System.getenv}-based variants are
 * smoke-tested in {@link #defaultProviderRoundtrips()}.
 */
class ExecutionModeParityTest {

    /** Env provider backed by an in-memory map. */
    private static EnvProvider provider(Map<String, String> map) {
        return name -> map.get(name);
    }

    private static EnvProvider provider(String... kvs) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put(kvs[i], kvs[i + 1]);
        }
        return provider(m);
    }

    // ------------------------------------------------------------------
    // getExecutionMode — every branch.
    // ------------------------------------------------------------------

    @Test
    void defaultIsServer() {
        assertEquals("server", ExecutionMode.getExecutionMode(provider()));
    }

    @Test
    void cgiViaGatewayInterface() {
        assertEquals(
                "cgi",
                ExecutionMode.getExecutionMode(provider("GATEWAY_INTERFACE", "CGI/1.1")));
    }

    @Test
    void lambdaViaFunctionName() {
        assertEquals(
                "lambda",
                ExecutionMode.getExecutionMode(provider("AWS_LAMBDA_FUNCTION_NAME", "my-fn")));
    }

    @Test
    void lambdaViaTaskRoot() {
        assertEquals(
                "lambda",
                ExecutionMode.getExecutionMode(provider("LAMBDA_TASK_ROOT", "/var/task")));
    }

    @Test
    void googleCloudFunctionViaFunctionTarget() {
        assertEquals(
                "google_cloud_function",
                ExecutionMode.getExecutionMode(provider("FUNCTION_TARGET", "my_handler")));
    }

    @Test
    void googleCloudFunctionViaKService() {
        assertEquals(
                "google_cloud_function",
                ExecutionMode.getExecutionMode(provider("K_SERVICE", "svc")));
    }

    @Test
    void googleCloudFunctionViaProject() {
        assertEquals(
                "google_cloud_function",
                ExecutionMode.getExecutionMode(provider("GOOGLE_CLOUD_PROJECT", "proj")));
    }

    @Test
    void azureFunctionViaEnvironment() {
        assertEquals(
                "azure_function",
                ExecutionMode.getExecutionMode(provider("AZURE_FUNCTIONS_ENVIRONMENT", "Production")));
    }

    @Test
    void azureFunctionViaWorkerRuntime() {
        assertEquals(
                "azure_function",
                ExecutionMode.getExecutionMode(provider("FUNCTIONS_WORKER_RUNTIME", "java")));
    }

    @Test
    void azureFunctionViaWebJobsStorage() {
        assertEquals(
                "azure_function",
                ExecutionMode.getExecutionMode(provider("AzureWebJobsStorage", "DefaultEndpoint")));
    }

    /** CGI must beat Lambda — cross-language precedence contract. */
    @Test
    void cgiBeatsLambda() {
        assertEquals(
                "cgi",
                ExecutionMode.getExecutionMode(provider(
                        "GATEWAY_INTERFACE", "CGI/1.1",
                        "AWS_LAMBDA_FUNCTION_NAME", "my-fn")));
    }

    @Test
    void lambdaBeatsGoogleCloud() {
        assertEquals(
                "lambda",
                ExecutionMode.getExecutionMode(provider(
                        "AWS_LAMBDA_FUNCTION_NAME", "my-fn",
                        "FUNCTION_TARGET", "h")));
    }

    @Test
    void googleCloudBeatsAzure() {
        assertEquals(
                "google_cloud_function",
                ExecutionMode.getExecutionMode(provider(
                        "FUNCTION_TARGET", "h",
                        "AZURE_FUNCTIONS_ENVIRONMENT", "Production")));
    }

    // ------------------------------------------------------------------
    // isServerlessMode.
    // ------------------------------------------------------------------

    @Test
    void serverIsNotServerless() {
        assertFalse(ExecutionMode.isServerlessMode(provider()));
    }

    @Test
    void lambdaIsServerless() {
        assertTrue(ExecutionMode.isServerlessMode(provider("AWS_LAMBDA_FUNCTION_NAME", "my-fn")));
    }

    @Test
    void cgiIsServerless() {
        // CGI is short-lived per request — counts as serverless.
        assertTrue(ExecutionMode.isServerlessMode(provider("GATEWAY_INTERFACE", "CGI/1.1")));
    }

    @Test
    void azureIsServerless() {
        assertTrue(ExecutionMode.isServerlessMode(provider("AZURE_FUNCTIONS_ENVIRONMENT", "Production")));
    }

    // ------------------------------------------------------------------
    // Smoke test: default System.getenv-based overloads return one of
    // the five canonical strings without throwing.
    // ------------------------------------------------------------------

    @Test
    void defaultProviderRoundtrips() {
        String mode = ExecutionMode.getExecutionMode();
        assertTrue(
                mode.equals("server") || mode.equals("cgi") || mode.equals("lambda")
                        || mode.equals("google_cloud_function") || mode.equals("azure_function"),
                "Unexpected mode: " + mode);
        // isServerlessMode() must agree with the string accessor.
        assertEquals(!"server".equals(mode), ExecutionMode.isServerlessMode());
    }
}
