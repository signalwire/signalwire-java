/**
 * Example: Answer an inbound call and say "Welcome to SignalWire!"
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space (e.g. example.signalwire.com)
 *
 * For full WebSocket / JSON-RPC debug output:
 *   SIGNALWIRE_LOG_LEVEL=debug
 */

import com.signalwire.agents.relay.RelayClient;

import java.util.List;
import java.util.Map;

public class RelayAnswerAndWelcome {

    public static void main(String[] args) throws Exception {
        var client = RelayClient.builder()
                .contexts(List.of("default"))
                .build();

        client.onCall(call -> {
            System.out.println("Incoming call: " + call);

            // Answer the call
            call.answer();

            // Play TTS greeting
            var action = call.play(List.of(Map.of(
                    "type", "tts",
                    "params", Map.of("text", "Welcome to SignalWire!")
            )));
            action.waitForCompletion();

            // Hang up
            call.hangup();
            System.out.println("Call ended: " + call.getCallId());
        });

        System.out.println("Waiting for inbound calls on context 'default' ...");
        client.run();
    }
}
