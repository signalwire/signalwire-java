/**
 * Example: IVR menu with DTMF collection, playback, and call connect.
 *
 * Answers an inbound call, plays a greeting, collects a digit, and
 * routes the caller based on their choice:
 *   1 - Hear a sales message
 *   2 - Hear a support message
 *   0 - Connect to a live agent at +19184238080
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 *
 * For full WebSocket / JSON-RPC debug output:
 *   SIGNALWIRE_LOG_LEVEL=debug
 */

import com.signalwire.agents.relay.RelayClient;

import java.util.List;
import java.util.Map;

public class RelayIvrConnect {

    private static final String AGENT_NUMBER = "+19184238080";

    /**
     * Helper to build a TTS play element.
     */
    private static Map<String, Object> tts(String text) {
        return Map.of("type", "tts", "params", Map.of("text", text));
    }

    public static void main(String[] args) throws Exception {
        var client = RelayClient.builder()
                .contexts(List.of("default"))
                .build();

        client.onCall(call -> {
            System.out.println("Incoming call: " + call);
            call.answer();

            // Play greeting and collect a single digit
            var collectAction = call.playAndCollect(
                    List.of(
                            tts("Welcome to SignalWire!"),
                            tts("Press 1 for sales. Press 2 for support. Press 0 to speak with an agent.")
                    ),
                    Map.of(
                            "digits", Map.of(
                                    "max", 1,
                                    "digit_timeout", 5.0
                            ),
                            "initial_timeout", 10.0
                    )
            );

            var resultEvent = collectAction.waitForCompletion();
            @SuppressWarnings("unchecked")
            var result = (Map<String, Object>) resultEvent.getOrDefault("result", Map.of());
            String resultType = (String) result.getOrDefault("type", "");
            @SuppressWarnings("unchecked")
            var resultParams = (Map<String, Object>) result.getOrDefault("params", Map.of());
            String digits = (String) resultParams.getOrDefault("digits", "");

            System.out.println("Collect result: type=" + resultType + " digits=" + digits);

            if ("digit".equals(resultType) && "1".equals(digits)) {
                // Sales
                var action = call.play(List.of(
                        tts("Thank you for your interest! A sales representative will be with you shortly.")));
                action.waitForCompletion();

            } else if ("digit".equals(resultType) && "2".equals(digits)) {
                // Support
                var action = call.play(List.of(
                        tts("Please hold while we connect you to our support team.")));
                action.waitForCompletion();

            } else if ("digit".equals(resultType) && "0".equals(digits)) {
                // Connect to live agent
                var action = call.play(List.of(
                        tts("Connecting you to an agent now. Please hold.")));
                action.waitForCompletion();

                @SuppressWarnings("unchecked")
                var device = (Map<String, Object>) call.getDevice();
                @SuppressWarnings("unchecked")
                var deviceParams = (Map<String, Object>) device.getOrDefault("params", Map.of());
                String fromNumber = (String) deviceParams.getOrDefault("to_number", "");

                System.out.println("Connecting to " + AGENT_NUMBER + " from " + fromNumber);

                call.connect(
                        List.of(List.of(Map.of(
                                "type", "phone",
                                "params", Map.of(
                                        "to_number", AGENT_NUMBER,
                                        "from_number", fromNumber,
                                        "timeout", 30
                                )
                        ))),
                        List.of(tts("Please wait while we connect your call."))
                );

                // Stay on the call until the bridge ends
                call.waitForEnded();
                System.out.println("Connected call ended: " + call.getCallId());
                return;

            } else {
                // No input or invalid
                var action = call.play(List.of(
                        tts("We didn't receive a valid selection.")));
                action.waitForCompletion();
            }

            call.hangup();
            System.out.println("Call ended: " + call.getCallId());
        });

        System.out.println("Waiting for inbound calls on context 'default' ...");
        client.run();
    }
}
