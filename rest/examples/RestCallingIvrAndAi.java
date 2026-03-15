/**
 * Example: IVR with collect and AI agent handoff via REST.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.agents.rest.SignalWireClient;
import com.signalwire.agents.rest.SignalWireRestError;

import java.util.List;
import java.util.Map;

public class RestCallingIvrAndAi {

    public static void main(String[] args) {
        var client = SignalWireClient.builder().build();

        // 1. Dial
        System.out.println("Dialing...");
        String callId;
        try {
            var result = client.calling().execute("dial", Map.of(
                    "from", "+15559876543",
                    "to", "+15551234567",
                    "timeout", 30
            ));
            callId = (String) result.get("call_id");
            System.out.println("  Call started: " + callId);
        } catch (SignalWireRestError e) {
            System.out.println("  Dial failed (expected in demo): " + e.getStatusCode());
            return;
        }

        // 2. Play and collect a digit
        System.out.println("Playing IVR menu and collecting digits...");
        try {
            var collectResult = client.calling().execute("play_and_collect", Map.of(
                    "call_id", callId,
                    "media", List.of(Map.of(
                            "type", "tts",
                            "params", Map.of("text",
                                    "Press 1 for sales. Press 2 for support. Press 3 for AI assistant.")
                    )),
                    "collect", Map.of(
                            "digits", Map.of("max", 1, "digit_timeout", 5.0),
                            "initial_timeout", 10.0
                    )
            ));
            System.out.println("  Collect result: " + collectResult);
        } catch (SignalWireRestError e) {
            System.out.println("  Collect failed: " + e.getMessage());
        }

        // 3. Hand off to AI agent
        System.out.println("Starting AI agent on call...");
        try {
            client.calling().execute("ai", Map.of(
                    "call_id", callId,
                    "prompt", Map.of("text", "You are a helpful support agent."),
                    "post_prompt", Map.of("text", "Summarize the conversation."),
                    "params", Map.of("temperature", 0.3)
            ));
            System.out.println("  AI agent started.");
        } catch (SignalWireRestError e) {
            System.out.println("  AI failed: " + e.getMessage());
        }
    }
}
