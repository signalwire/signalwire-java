package com.signalwire.sdk.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LambdaUrlResolverTest {

    private static EnvProvider envOf(Map<String, String> m) {
        return name -> m.get(name);
    }

    @Test
    void functionUrlTakesPrecedence() {
        var env = envOf(Map.of(
                "AWS_LAMBDA_FUNCTION_URL", "https://abc.lambda-url.us-west-2.on.aws",
                "AWS_LAMBDA_FUNCTION_NAME", "other-fn",
                "AWS_REGION", "us-east-1"));
        assertEquals("https://abc.lambda-url.us-west-2.on.aws",
                new LambdaUrlResolver(env).resolveBaseUrl());
    }

    @Test
    void trailingSlashStrippedFromFunctionUrl() {
        var env = envOf(Map.of(
                "AWS_LAMBDA_FUNCTION_URL", "https://abc.lambda-url.us-west-2.on.aws/"));
        assertEquals("https://abc.lambda-url.us-west-2.on.aws",
                new LambdaUrlResolver(env).resolveBaseUrl());
    }

    @Test
    void synthesisedFromNameAndRegion() {
        var env = envOf(Map.of(
                "AWS_LAMBDA_FUNCTION_NAME", "my-agent",
                "AWS_REGION", "eu-west-1"));
        assertEquals("https://my-agent.lambda-url.eu-west-1.on.aws",
                new LambdaUrlResolver(env).resolveBaseUrl());
    }

    @Test
    void synthesisedUsesDefaultRegionWhenMissing() {
        var env = envOf(Map.of("AWS_LAMBDA_FUNCTION_NAME", "my-agent"));
        assertEquals("https://my-agent.lambda-url.us-east-1.on.aws",
                new LambdaUrlResolver(env).resolveBaseUrl());
    }

    @Test
    void nullWhenNothingSet() {
        var env = envOf(Map.of());
        assertNull(new LambdaUrlResolver(env).resolveBaseUrl());
    }

    @Test
    void emptyFunctionNameTreatedAsUnset() {
        var env = envOf(Map.of(
                "AWS_LAMBDA_FUNCTION_NAME", "",
                "AWS_REGION", "us-east-1"));
        assertNull(new LambdaUrlResolver(env).resolveBaseUrl());
    }

    @Test
    void defaultConstructorUsesSystemEnv() {
        // Smoke test: the zero-arg constructor must not throw.
        assertDoesNotThrow(() -> new LambdaUrlResolver().resolveBaseUrl());
    }
}
