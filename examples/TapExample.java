/*
 * Tap Example.
 *
 * <p>Demonstrates using the FunctionResult tap helpers for: - WebSocket and RTP audio streaming -
 * Compliance and quality monitoring - Starting and stopping tap sessions by control ID
 */
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import java.util.List;
import java.util.Map;

public class TapExample {

  public static void main(String[] args) throws Exception {
    var agent = AgentBase.builder().name("tap-agent").route("/").port(3000).build();

    agent.promptAddSection(
        "Role", "You are a call center supervisor assistant that manages call monitoring.");
    agent.promptAddSection(
        "Instructions",
        "",
        List.of(
            "Use start_monitoring to begin monitoring a call",
            "Use stop_monitoring to end monitoring",
            "Use start_compliance_tap for compliance recording"));

    // Start WebSocket tap
    agent.defineTool(
        "start_monitoring",
        "Start monitoring a call via WebSocket",
        Map.of("type", "object", "properties", Map.of()),
        (toolArgs, raw) ->
            new FunctionResult("Starting call monitoring")
                .tap("wss://monitoring.company.com/audio-stream", "monitor_001", "both", "PCMU")
                .setMetadata(Map.of("monitoring", true))
                .say("Call monitoring is now active."));

    // Stop tap
    agent.defineTool(
        "stop_monitoring",
        "Stop the active monitoring session",
        Map.of("type", "object", "properties", Map.of()),
        (toolArgs, raw) ->
            new FunctionResult("Ending monitoring session")
                .stopTap("monitor_001")
                .setMetadata(Map.of("monitoring", false))
                .say("Call monitoring has been stopped."));

    // Compliance tap
    agent.defineTool(
        "start_compliance_tap",
        "Start compliance monitoring with RTP streaming",
        Map.of("type", "object", "properties", Map.of()),
        (toolArgs, raw) ->
            new FunctionResult("Setting up compliance monitoring")
                .tap("rtp://compliance.company.com:6000", "compliance_001", "both", "PCMA")
                .setMetadata(
                    Map.of(
                        "compliance_session", true, "recording_purpose", "regulatory_compliance"))
                .say("This call is being monitored for compliance purposes."));

    System.out.println("Starting Tap monitoring agent on port 3000...");
    agent.run();
  }
}
