/**
 * Dial a number and play "Welcome to SignalWire" using the RELAY client.
 *
 * Requires env vars:
 *     SIGNALWIRE_PROJECT_ID
 *     SIGNALWIRE_API_TOKEN
 *     RELAY_FROM_NUMBER   - a number on your SignalWire project
 *     RELAY_TO_NUMBER     - destination to call
 */

import com.signalwire.agents.relay.RelayClient;

import java.util.List;
import java.util.Map;

public class RelayDialAndPlay {

    public static void main(String[] args) throws Exception {
        String fromNumber = System.getenv("RELAY_FROM_NUMBER");
        String toNumber = System.getenv("RELAY_TO_NUMBER");

        if (fromNumber == null || toNumber == null) {
            System.err.println("Set RELAY_FROM_NUMBER and RELAY_TO_NUMBER env vars");
            System.exit(1);
        }

        var client = RelayClient.builder().build();
        client.connect();
        System.out.println("Connected - protocol: " + client.getProtocol());

        // Dial the number
        var devices = List.of(List.of(Map.of(
                "type", "phone",
                "params", Map.of(
                        "to_number", toNumber,
                        "from_number", fromNumber
                )
        )));

        var call = client.dial(devices);
        System.out.println("Dialing " + toNumber + " from " + fromNumber +
                " - call_id: " + call.getCallId());

        // Wait for the call to be answered
        boolean answered = call.waitForAnswered(30_000);
        if (!answered) {
            System.out.println("No answer - timed out");
            client.disconnect();
            return;
        }

        System.out.println("Call answered - playing TTS");

        // Play TTS
        var playAction = call.play(List.of(Map.of(
                "type", "tts",
                "params", Map.of("text", "Welcome to SignalWire")
        )));

        // Wait for playback to finish
        playAction.waitForCompletion(15_000);
        System.out.println("Playback finished - hanging up");

        call.hangup();
        call.waitForEnded(10_000);
        System.out.println("Call ended");

        client.disconnect();
        System.out.println("Disconnected");
    }
}
