/**
 * Example: IVR with AI hand-off via REST.
 *
 * Java drives REST calls through the CRUD surface on
 * {@code client.calling().calls()}. Python's calling.execute("...")
 * RPC shim is not ported — dial is a {@code create}, update operations
 * (play, play_and_collect, ai) flow through {@code update}, and hang-up
 * is {@code delete}. See {@code PORT_OMISSIONS.md}.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;

import java.util.List;
import java.util.Map;

public class RestCallingIvrAndAi {

    public static void main(String[] args) {
        var client = RestClient.builder().build();

        // 1. Dial — create a call via the native calling CRUD resource.
        System.out.println("Dialing...");
        String callId;
        try {
            var result = client.calling().calls().create(Map.of(
                    "from", "+15559876543",
                    "to", "+15551234567",
                    "timeout", 30
            ));
            callId = (String) result.get("call_id");
            System.out.println("  Call started: " + callId);
        } catch (RestError e) {
            System.out.println("  Dial failed (expected in demo): " + e.getStatusCode());
            return;
        }

        // 2. Update the call with a play_and_collect action.
        System.out.println("Playing IVR menu and collecting digits...");
        try {
            var collectResult = client.calling().calls().update(callId, Map.of(
                    "action", "play_and_collect",
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
        } catch (RestError e) {
            System.out.println("  Collect failed: " + e.getStatusCode());
        }

        // 3. Hand off to an AI agent by updating the call.
        System.out.println("Starting AI agent on call...");
        try {
            client.calling().calls().update(callId, Map.of(
                    "action", "ai",
                    "prompt", Map.of("text", "You are a helpful support agent."),
                    "post_prompt", Map.of("text", "Summarize the conversation."),
                    "params", Map.of("temperature", 0.3)
            ));
            System.out.println("  AI agent started.");
        } catch (RestError e) {
            System.out.println("  AI failed: " + e.getStatusCode());
        }
    }
}
