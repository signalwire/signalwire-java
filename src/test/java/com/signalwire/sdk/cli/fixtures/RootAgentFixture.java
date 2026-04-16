package com.signalwire.sdk.cli.fixtures;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.runtime.EnvProvider;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.Map;

/**
 * Minimal test-only agent at route "/" used by the CLI simulation tests.
 *
 * <p>Exposes both a no-arg factory and an {@link EnvProvider}-aware
 * factory so we can verify both reflection signatures the CLI loader
 * accepts.
 */
public class RootAgentFixture {

    /** Last {@link EnvProvider} received by {@link #createAgent(EnvProvider)}. */
    public static volatile EnvProvider lastEnvSeen;

    /** How many times a tool's handler was invoked during the last test. */
    public static volatile int toolInvocations;

    /** Reset all visible state before a test. */
    public static void reset() {
        lastEnvSeen = null;
        toolInvocations = 0;
    }

    /**
     * EnvProvider-aware factory — the preferred signature. Exercises
     * the path where simulated values are visible to the builder.
     */
    public static AgentBase createAgent(EnvProvider env) {
        lastEnvSeen = env;
        AgentBase agent = AgentBase.builder()
                .name("root-fixture")
                .route("/")
                .authUser("u")
                .authPassword("p")
                .envProvider(env)
                .build();
        agent.setPromptText("Test prompt.");
        agent.defineTool("echo", "Echo back the input",
                Map.of("type", "object",
                        "properties", Map.of(
                                "msg", Map.of("type", "string"))),
                (args, raw) -> {
                    toolInvocations++;
                    return new FunctionResult("echo: " + args.getOrDefault("msg", ""));
                });
        return agent;
    }
}
