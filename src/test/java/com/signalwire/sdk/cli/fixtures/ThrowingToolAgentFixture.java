package com.signalwire.sdk.cli.fixtures;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.runtime.EnvProvider;

import java.util.Map;

/**
 * Test-only agent whose {@code explode} tool throws. Used to confirm
 * the simulator's {@code try/finally} restoration runs on the exception
 * path as well as the success path.
 */
public class ThrowingToolAgentFixture {

    public static AgentBase createAgent(EnvProvider env) {
        AgentBase agent = AgentBase.builder()
                .name("throwing-fixture")
                .route("/")
                .authUser("u")
                .authPassword("p")
                .envProvider(env)
                .build();
        agent.setPromptText("Test prompt.");
        agent.defineTool("explode", "Always throws",
                Map.of("type", "object", "properties", Map.of()),
                (args, raw) -> {
                    throw new RuntimeException("boom — simulated tool failure");
                });
        return agent;
    }
}
