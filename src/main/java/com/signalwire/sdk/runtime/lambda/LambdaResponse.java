package com.signalwire.sdk.runtime.lambda;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Response shape returned by {@link LambdaAgentHandler} and compatible
 * with AWS API Gateway (REST v1 and HTTP v2) and Lambda Function URL
 * response envelopes.
 *
 * <p>The fields map 1:1 to {@code APIGatewayProxyResponseEvent}:
 * {@code statusCode}, {@code headers}, {@code body},
 * {@code isBase64Encoded}. Returned as a plain {@link Map} so the SDK
 * does not require a runtime dependency on {@code aws-lambda-java-events}.
 * Users who want the typed variant can trivially copy the fields over.
 */
public final class LambdaResponse {

    private final int statusCode;
    private final Map<String, String> headers;
    private final String body;
    private final boolean isBase64Encoded;

    /**
     * Create a Lambda response with the given fields.
     *
     * @param statusCode HTTP status code.
     * @param headers response headers (must not be null — pass an empty
     *     map for no headers).
     * @param body raw response body as a string.
     * @param isBase64Encoded whether the body is base64-encoded binary.
     */
    public LambdaResponse(int statusCode, Map<String, String> headers,
                          String body, boolean isBase64Encoded) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
        this.isBase64Encoded = isBase64Encoded;
    }

    /** @return HTTP status code. */
    public int getStatusCode() { return statusCode; }

    /** @return response headers. */
    public Map<String, String> getHeaders() { return headers; }

    /** @return response body. */
    public String getBody() { return body; }

    /** @return whether the body is base64-encoded. */
    public boolean isBase64Encoded() { return isBase64Encoded; }

    /**
     * Serialise this response into the AWS Lambda Function URL /
     * API Gateway payload format (v1 and v2 are structurally identical
     * for response shape).
     *
     * @return a Map suitable for returning from a Lambda handler.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("statusCode", statusCode);
        m.put("headers", headers);
        m.put("body", body);
        m.put("isBase64Encoded", isBase64Encoded);
        return m;
    }

    /**
     * Convenience builder for a 200 JSON response.
     *
     * @param body raw JSON string.
     * @return LambdaResponse.
     */
    public static LambdaResponse json(String body) {
        return json(200, body);
    }

    /**
     * Convenience builder for a JSON response with a custom status.
     *
     * @param statusCode HTTP status.
     * @param body raw JSON string.
     * @return LambdaResponse.
     */
    public static LambdaResponse json(int statusCode, String body) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Content-Type", "application/json");
        return new LambdaResponse(statusCode, h, body, false);
    }
}
