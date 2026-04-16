package com.signalwire.sdk.runtime;

/**
 * Runtime deployment environments the SDK knows about.
 *
 * <p>Detected from well-known environment variables set by the hosting
 * platform. Mirrors {@code get_execution_mode()} in the Python SDK and
 * {@code detectPlatform()} in the TypeScript SDK.
 *
 * <p>Use {@link #detect()} to read the actual process environment, or
 * {@link #detect(EnvProvider)} to pass a test double.
 */
public enum ExecutionMode {

    /** Normal long-running HTTP server process (the default). */
    SERVER,

    /** AWS Lambda function (container or zip deployment). */
    LAMBDA,

    /** Legacy CGI script behind a web server. */
    CGI,

    /** Google Cloud Functions (1st or 2nd gen / Cloud Run). */
    GOOGLE_CLOUD_FUNCTION,

    /** Azure Functions. */
    AZURE_FUNCTION;

    /**
     * Detect the execution mode from the real process environment.
     *
     * @return the detected mode, or {@link #SERVER} as fallback.
     */
    public static ExecutionMode detect() {
        return detect(EnvProvider.SYSTEM);
    }

    /**
     * Detect the execution mode from the given {@link EnvProvider}.
     *
     * <p>Detection order matches the Python reference: CGI, Lambda,
     * Google Cloud Functions, Azure Functions, then fall back to
     * {@link #SERVER}. Order matters because some environments set
     * overlapping variables (e.g. a container running under API
     * Gateway in "Lambda container mode").
     *
     * @param env environment variable source (injectable for tests).
     * @return the detected mode.
     */
    public static ExecutionMode detect(EnvProvider env) {
        if (env.isSet("GATEWAY_INTERFACE")) {
            return CGI;
        }
        if (env.isSet("AWS_LAMBDA_FUNCTION_NAME") || env.isSet("LAMBDA_TASK_ROOT")) {
            return LAMBDA;
        }
        if (env.isSet("FUNCTION_TARGET")
                || env.isSet("K_SERVICE")
                || env.isSet("GOOGLE_CLOUD_PROJECT")) {
            return GOOGLE_CLOUD_FUNCTION;
        }
        if (env.isSet("AZURE_FUNCTIONS_ENVIRONMENT")
                || env.isSet("FUNCTIONS_WORKER_RUNTIME")
                || env.isSet("AzureWebJobsStorage")) {
            return AZURE_FUNCTION;
        }
        return SERVER;
    }
}
