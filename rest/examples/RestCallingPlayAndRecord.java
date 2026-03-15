/**
 * Example: Place a call, play audio, and record using REST calling commands.
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

public class RestCallingPlayAndRecord {

    public static void main(String[] args) {
        var client = SignalWireClient.builder().build();

        String callId = null;

        // 1. Dial a number
        System.out.println("Dialing...");
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

        // 2. Play TTS
        System.out.println("Playing TTS...");
        try {
            client.calling().execute("play", Map.of(
                    "call_id", callId,
                    "media", List.of(Map.of(
                            "type", "tts",
                            "params", Map.of("text", "Please leave a message after the beep.")
                    ))
            ));
        } catch (SignalWireRestError e) {
            System.out.println("  Play failed: " + e.getMessage());
        }

        // 3. Record
        System.out.println("Starting recording...");
        try {
            var recordResult = client.calling().execute("record", Map.of(
                    "call_id", callId,
                    "beep", true,
                    "direction", "both",
                    "format", "wav",
                    "end_silence_timeout", 3
            ));
            System.out.println("  Recording: " + recordResult);
        } catch (SignalWireRestError e) {
            System.out.println("  Record failed: " + e.getMessage());
        }

        // 4. Hangup
        System.out.println("Hanging up...");
        try {
            client.calling().execute("hangup", Map.of("call_id", callId));
            System.out.println("  Call ended.");
        } catch (SignalWireRestError e) {
            System.out.println("  Hangup failed: " + e.getMessage());
        }
    }
}
