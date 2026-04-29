/**
 * RelayAnswerAndWelcome -- answer an inbound call, play a TTS greeting,
 * and hang up. This is the canonical RELAY "hello world": the smallest
 * SDK program that proves authenticated WebSocket connection +
 * inbound-call dispatch + TTS playback + clean hangup all work end to end.
 *
 * <p>Mirror of Python's {@code examples/relay_answer_and_welcome.py}.
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>{@code SIGNALWIRE_PROJECT_ID}  -- SignalWire project ID</li>
 *   <li>{@code SIGNALWIRE_API_TOKEN}   -- SignalWire API token</li>
 *   <li>{@code SIGNALWIRE_SPACE}       -- e.g. {@code example.signalwire.com}</li>
 * </ul>
 *
 * <p>Optional:
 * <ul>
 *   <li>{@code SIGNALWIRE_CONTEXT}     -- defaults to {@code default}</li>
 *   <li>{@code SIGNALWIRE_LOG_LEVEL=debug} for the full RELAY trace</li>
 * </ul>
 */

import com.signalwire.sdk.relay.RelayClient;

import java.util.List;
import java.util.Map;

public class RelayAnswerAndWelcome {

    public static void main(String[] args) throws Exception {
        String project = System.getenv("SIGNALWIRE_PROJECT_ID");
        String token = System.getenv("SIGNALWIRE_API_TOKEN");
        String space = System.getenv("SIGNALWIRE_SPACE");
        String context = System.getenv().getOrDefault("SIGNALWIRE_CONTEXT", "default");

        if (project == null || token == null || space == null) {
            System.err.println(
                    "RelayAnswerAndWelcome: SIGNALWIRE_PROJECT_ID, "
                            + "SIGNALWIRE_API_TOKEN, and SIGNALWIRE_SPACE are required.");
            System.exit(2);
        }

        var client = RelayClient.builder()
                .project(project)
                .token(token)
                .space(space)
                .contexts(List.of(context))
                .build();

        client.onCall(call -> {
            System.out.println("Incoming call: " + call.getCallId());

            // Answer the call.
            call.answer();

            // Play TTS greeting and wait for completion.
            var action = call.play(List.of(Map.of(
                    "type", "tts",
                    "params", Map.of("text", "Welcome to SignalWire!")
            )));
            action.waitForCompletion();

            // Hang up.
            call.hangup();
            System.out.println("Call ended: " + call.getCallId());
        });

        System.out.println("Waiting for inbound calls on context '" + context + "' ...");
        client.run();
    }
}
