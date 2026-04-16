package com.signalwire.sdk.cli.fixtures;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.runtime.EnvProvider;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.Map;

/**
 * Test-only agent mounted at a non-root route. Used to verify the
 * load-bearing regression: {@code --simulate-serverless lambda --dump-swml}
 * against a routed agent must produce SWML whose webhook URLs contain
 * the agent's route before {@code /swaig}.
 */
public class RoutedAgentFixture {

    public static AgentBase createAgent(EnvProvider env) {
        AgentBase agent = AgentBase.builder()
                .name("routed-fixture")
                .route("/my-agent")
                .authUser("u")
                .authPassword("p")
                .envProvider(env)
                .build();
        agent.setPromptText("Test prompt.");
        agent.defineTool("ping", "Health check",
                Map.of("type", "object", "properties", Map.of()),
                (args, raw) -> new FunctionResult("pong"));
        return agent;
    }
}
