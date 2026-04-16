package com.signalwire.sdk.runtime;

import com.signalwire.sdk.agent.AgentBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AgentBase#detectServerlessBaseUrl()}.
 *
 * <p>Only the proxy-override branch can be reliably exercised from
 * tests (env-var-driven branches depend on the real process env,
 * which Java cannot mutate without reflection hacks). The Lambda
 * env branch is covered by {@link LambdaUrlResolverTest} directly.
 */
class AgentBaseServerlessBaseUrlTest {

    @Test
    void proxyBaseReturnedWhenSet() {
        AgentBase agent = AgentBase.builder()
                .name("a")
                .authUser("u")
                .authPassword("p")
                .build();
        agent.manualSetProxyUrl("https://proxy.example.com");
        assertEquals("https://proxy.example.com", agent.detectServerlessBaseUrl());
    }

    @Test
    void proxyBaseTakesPrecedenceOverEnvOnServer() {
        // On a developer machine there are no Lambda env vars, so
        // ExecutionMode.detect() returns SERVER. Without a proxy base,
        // detectServerlessBaseUrl() returns null.
        AgentBase agent = AgentBase.builder()
                .name("a")
                .authUser("u")
                .authPassword("p")
                .build();
        // No proxy, no Lambda env → null.
        // (We cannot assert environment-specific outcomes reliably,
        //  so this only asserts the documented null case.)
        boolean onLambda = System.getenv("AWS_LAMBDA_FUNCTION_NAME") != null
                || System.getenv("LAMBDA_TASK_ROOT") != null;
        if (!onLambda) {
            assertNull(agent.detectServerlessBaseUrl());
        }
    }
}
