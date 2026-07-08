/**
 * Example: IVR with AI hand-off via REST.
 *
 * Java drives REST calls through the command API on
 * {@code client.calling()}, mirroring Python's per-command methods:
 * {@code dial(...)} starts a call, then {@code collect(callId, ...)},
 * {@code aiMessage(callId, ...)}, {@code end(callId, ...)} etc. issue
 * the individual call commands.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;
import com.signalwire.sdk.rest.namespaces.generated.Calling;

import java.util.List;
import java.util.Map;

public class RestCallingIvrAndAi {

    public static void main(String[] args) {
        var client = RestClient.builder().build();

        // 1. Dial — start a call via the calling command API.
        System.out.println("Dialing...");
        String callId;
        try {
            var result = client.calling().dial(
                    Calling.DialRequest.builder()
                            .from("+15559876543")
                            .to("+15551234567")
                            .extras(Map.of("timeout", 30))
                            .build());
            callId = (String) result.get("call_id");
            System.out.println("  Call started: " + callId);
        } catch (RestError e) {
            System.out.println("  Dial failed (expected in demo): " + e.getStatusCode());
            return;
        }

        // 2. Play an IVR menu and collect digits via the collect command.
        System.out.println("Playing IVR menu and collecting digits...");
        try {
            var collectResult = client.calling().collect(callId,
                    Calling.CollectRequest.builder()
                            .initialTimeout(10.0)
                            .digits(Map.of("max", 1, "digit_timeout", 5.0))
                            .extras(Map.of(
                                    "media", List.of(Map.of(
                                            "type", "tts",
                                            "params", Map.of("text",
                                                    "Press 1 for sales. Press 2 for support. Press 3 for AI assistant.")
                                    ))
                            ))
                            .build());
            System.out.println("  Collect result: " + collectResult);
        } catch (RestError e) {
            System.out.println("  Collect failed: " + e.getStatusCode());
        }

        // 3. Hand off to an AI agent on the call.
        System.out.println("Starting AI agent on call...");
        try {
            client.calling().aiMessage(callId,
                    Calling.AiMessageRequest.builder()
                            .extras(Map.of(
                                    "prompt", Map.of("text", "You are a helpful support agent."),
                                    "post_prompt", Map.of("text", "Summarize the conversation."),
                                    "params", Map.of("temperature", 0.3)
                            ))
                            .build());
            System.out.println("  AI agent started.");
        } catch (RestError e) {
            System.out.println("  AI failed: " + e.getStatusCode());
        }
    }
}
