/**
 * Room and SIP Example.
 *
 * Demonstrates using FunctionResult helpers for:
 * - Joining RELAY rooms for multi-party communication
 * - SIP REFER for call transfers in SIP environments
 * - Joining conferences
 */

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

public class RoomAndSipExample {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("room-sip-agent")
                .route("/")
                .port(3000)
                .build();

        agent.promptAddSection("Role",
                "You are an office communication assistant that can join rooms, " +
                "transfer calls via SIP, and set up conferences.");
        agent.promptAddSection("Instructions", "", List.of(
                "Use join_support_room to join the support team room",
                "Use transfer_to_manager to escalate via SIP",
                "Use start_conference to begin a conference call"
        ));

        // Join a RELAY room
        agent.defineTool("join_support_room", "Join the support team room",
                Map.of("type", "object", "properties", Map.of()),
                (toolArgs, raw) -> new FunctionResult("Joining the support team room")
                        .joinRoom("support_team_room")
                        .setMetadata(Map.of("role", "support_agent"))
                        .say("Welcome to the support team room."));

        // SIP REFER transfer
        agent.defineTool("transfer_to_manager", "Transfer call to manager via SIP",
                Map.of("type", "object", "properties", Map.of()),
                (toolArgs, raw) -> new FunctionResult("Transferring to manager")
                        .say("Let me connect you with a manager.")
                        .sipRefer("sip:manager@company.com")
                        .updateGlobalData(Map.of("escalated", true)));

        // Join a conference
        agent.defineTool("start_conference", "Join a conference call",
                Map.of("type", "object",
                        "properties", Map.of(
                                "name", Map.of("type", "string",
                                        "description", "Conference name")
                        ),
                        "required", List.of("name")),
                (toolArgs, raw) -> {
                    String name = (String) toolArgs.getOrDefault("name", "default");
                    return new FunctionResult("Setting up conference: " + name)
                            .joinConference(name)
                            .say("Welcome to the " + name + " conference.");
                });

        System.out.println("Starting Room & SIP agent on port 3000...");
        agent.run();
    }
}
