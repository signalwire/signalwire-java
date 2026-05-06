/**
 * Using the RELAY WebSocket client for real-time call control.
 *
 * Requires env vars:
 *   SIGNALWIRE_PROJECT_ID
 *   SIGNALWIRE_API_TOKEN
 *   SIGNALWIRE_SPACE
 */

import com.signalwire.sdk.relay.RelayClient;

import java.util.List;
import java.util.Map;

public class RelayDemo {

    public static void main(String[] args) throws Exception {
        // Build RELAY client (reads env vars automatically)
        var client = RelayClient.builder()
                .contexts(List.of("default"))
                .build();

        // Register inbound call handler
        client.onCall(call -> {
            System.out.println("Incoming call: " + call.getCallId());

            // Answer the call
            call.answer();

            // Play a greeting
            var playAction = call.play(List.of(Map.of(
                    "type", "tts",
                    "params", Map.of("text",
                            "Welcome to SignalWire! This is a demo of the Java RELAY client.")
            )));
            playAction.waitForCompletion();

            // Collect a digit
            var collectAction = call.playAndCollect(
                    List.of(Map.of(
                            "type", "tts",
                            "params", Map.of("text", "Press 1 to hear a joke, or press 2 to hang up.")
                    )),
                    Map.of(
                            "digits", Map.of("max", 1, "digit_timeout", 5.0),
                            "initial_timeout", 10.0
                    ),
                    (Map<String, Object>) null
            );

            var result = collectAction.waitForCompletion();
            // Extract collected digits from the event params
            String digits = result != null ? result.getStringParam("digits", "") : "";

            if ("1".equals(digits)) {
                var jokeAction = call.play(List.of(Map.of(
                        "type", "tts",
                        "params", Map.of("text",
                                "Why do Java developers wear glasses? Because they don't C sharp!")
                )));
                jokeAction.waitForCompletion();
            }

            // Say goodbye and hang up
            var byeAction = call.play(List.of(Map.of(
                    "type", "tts",
                    "params", Map.of("text", "Goodbye!")
            )));
            byeAction.waitForCompletion();

            call.hangup();
            System.out.println("Call ended: " + call.getCallId());
        });

        System.out.println("RELAY client listening for inbound calls...");
        client.run();
    }
}
