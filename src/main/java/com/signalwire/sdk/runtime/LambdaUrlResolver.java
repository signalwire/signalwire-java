package com.signalwire.sdk.runtime;

/**
 * Resolve the base URL of a SignalWire agent running as an AWS Lambda
 * function, from standard Lambda environment variables.
 *
 * <p>Precedence — highest first:
 * <ol>
 *   <li>{@code AWS_LAMBDA_FUNCTION_URL} — the Function URL explicitly
 *       assigned to this Lambda (the only variant that is guaranteed
 *       correct; synthetic fallbacks below assume the Function URL
 *       service was used and the function name matches the public
 *       subdomain, which is typically true but not guaranteed).</li>
 *   <li>Synthesised {@code https://{AWS_LAMBDA_FUNCTION_NAME}.lambda-url.{AWS_REGION}.on.aws}
 *       — built when the Function URL env var is not present but the
 *       standard Lambda identity variables are.</li>
 * </ol>
 *
 * <p>This class returns a bare origin (scheme + host). It intentionally
 * does NOT append any route — callers (e.g. {@code AgentBase.buildWebhookUrl})
 * are responsible for appending the agent's route + endpoint path. This
 * keeps the route-preservation invariant intact regardless of which
 * source produced the base URL.
 *
 * <p>Mirrors the Lambda branch of {@code get_full_url()} in the Python SDK.
 */
public final class LambdaUrlResolver {

    private final EnvProvider env;

    /**
     * Create a resolver backed by the real process environment.
     */
    public LambdaUrlResolver() {
        this(EnvProvider.SYSTEM);
    }

    /**
     * Create a resolver backed by the given {@link EnvProvider}. Used in
     * tests to avoid real env-var mutation.
     *
     * @param env environment variable source.
     */
    public LambdaUrlResolver(EnvProvider env) {
        this.env = env;
    }

    /**
     * Return the base URL for the Lambda, without any trailing slash.
     *
     * <p>Returns {@code null} if none of the expected env vars are set
     * (which typically means we're not actually running on Lambda). The
     * caller should then fall back to its normal base-URL detection.
     *
     * @return bare origin (e.g. {@code https://xyz.lambda-url.us-east-1.on.aws}),
     *     or {@code null}.
     */
    public String resolveBaseUrl() {
        String functionUrl = env.get("AWS_LAMBDA_FUNCTION_URL");
        if (functionUrl != null && !functionUrl.isEmpty()) {
            return stripTrailingSlash(functionUrl);
        }

        String functionName = env.get("AWS_LAMBDA_FUNCTION_NAME");
        if (functionName == null || functionName.isEmpty()) {
            return null;
        }
        String region = env.get("AWS_REGION");
        if (region == null || region.isEmpty()) {
            region = "us-east-1";
        }
        return "https://" + functionName + ".lambda-url." + region + ".on.aws";
    }

    private static String stripTrailingSlash(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') {
            end--;
        }
        return s.substring(0, end);
    }
}
