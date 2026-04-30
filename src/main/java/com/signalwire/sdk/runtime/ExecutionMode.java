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

    /**
     * Cross-language SDK contract: return the execution mode as the
     * canonical lower-case-with-underscores string used by every port.
     * Mirrors {@code signalwire.core.logging_config.get_execution_mode}
     * in Python: one of {@code "cgi"}, {@code "lambda"},
     * {@code "google_cloud_function"}, {@code "azure_function"}, or
     * {@code "server"}.
     *
     * @return canonical mode string detected from the process environment
     */
    public static String getExecutionMode() {
        return getExecutionMode(EnvProvider.SYSTEM);
    }

    /**
     * Same as {@link #getExecutionMode()} but reads from the supplied
     * {@link EnvProvider}, allowing tests to drive every branch
     * deterministically.
     *
     * @param env environment variable source (injectable for tests).
     * @return canonical mode string.
     */
    public static String getExecutionMode(EnvProvider env) {
        switch (detect(env)) {
            case CGI:                    return "cgi";
            case LAMBDA:                 return "lambda";
            case GOOGLE_CLOUD_FUNCTION:  return "google_cloud_function";
            case AZURE_FUNCTION:         return "azure_function";
            case SERVER:
            default:                     return "server";
        }
    }

    /**
     * Cross-language SDK contract: true when the process is running
     * inside any serverless / short-lived environment (i.e. anything
     * other than {@code "server"}). Mirrors
     * {@code signalwire.utils.is_serverless_mode} in Python.
     *
     * @return {@code true} unless the detected mode is {@code "server"}.
     */
    public static boolean isServerlessMode() {
        return isServerlessMode(EnvProvider.SYSTEM);
    }

    /**
     * @param env environment variable source (injectable for tests).
     * @return true unless the detected mode is {@code "server"}.
     */
    public static boolean isServerlessMode(EnvProvider env) {
        return !"server".equals(getExecutionMode(env));
    }
}
