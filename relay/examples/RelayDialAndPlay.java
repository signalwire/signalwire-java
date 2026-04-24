/**
 * Dial a number and play "Welcome to SignalWire" using the RELAY client.
 *
 * The Java RELAY client runs a blocking event loop inside
 * {@link com.signalwire.sdk.relay.RelayClient#run()} (it owns the WebSocket
 * and JSON-RPC dispatcher). To place an outbound call without inheriting
 * the lifecycle of an inbound handler, start {@code run()} in a background
 * thread, then issue the dial from the main thread. Hang-up is driven by
 * observing the call's state via {@code getState()} or by waiting on a
 * specific action (such as the TTS playback).
 *
 * Requires env vars:
 *     SIGNALWIRE_PROJECT_ID
 *     SIGNALWIRE_API_TOKEN
 *     SIGNALWIRE_SPACE
 *     RELAY_FROM_NUMBER   - a number on your SignalWire project
 *     RELAY_TO_NUMBER     - destination to call
 */

import com.signalwire.sdk.relay.RelayClient;

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

        // Start the event loop in a background thread so the main thread
        // can drive dial → play → hangup.
        Thread loop = new Thread(client::run, "relay-client");
        loop.setDaemon(true);
        loop.start();

        // Give the WebSocket a moment to connect and negotiate protocol.
        Thread.sleep(1000);

        // Dial an outbound call.
        var devices = List.of(List.of(Map.of(
                "type", "phone",
                "params", Map.of(
                        "to_number", toNumber,
                        "from_number", fromNumber
                )
        )));

        var call = client.dial(devices);
        System.out.println("Dialing " + toNumber + " from " + fromNumber
                + " - call_id: " + call.getCallId()
                + " - state: " + call.getState());

        // Play TTS — blocks until playback finishes via the action handle.
        System.out.println("Playing TTS...");
        var playAction = call.play(List.of(Map.of(
                "type", "tts",
                "params", Map.of("text", "Welcome to SignalWire")
        )));
        playAction.waitForCompletion(15_000);
        System.out.println("Playback finished, hanging up");

        call.hangup();
        System.out.println("Hung up - final state: " + call.getState());

        client.disconnect();
        loop.join(5_000);
        System.out.println("Disconnected");
    }
}
