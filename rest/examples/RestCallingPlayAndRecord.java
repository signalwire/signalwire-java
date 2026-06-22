/**
 * Example: Place a call via the REST API and inspect the result.
 *
 * The Java SDK surfaces native calling via the command API on
 * {@code client.calling()} ({@code dial}, {@code play}, {@code end}, ...),
 * mirroring Python's per-command methods, and the CXML/Twilio-compat call
 * CRUD on {@code client.compat().calls()}. See {@code rest/docs/calling.md}.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;

import java.util.Map;

public class RestCallingPlayAndRecord {

    public static void main(String[] args) {
        var client = RestClient.builder().build();

        String callId = null;

        // 1. Dial a number via the calling command API.
        System.out.println("Dialing...");
        try {
            var result = client.calling().dial(Map.of(
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

        // 2. Play a TTS prompt via the play command.
        System.out.println("Sending play...");
        try {
            client.calling().play(callId, Map.of(
                    "play", Map.of("text", "Please leave a message after the beep."),
                    "beep", true
            ));
        } catch (RestError e) {
            System.out.println("  Play failed: " + e.getStatusCode());
        }

        // 3. Start recording the call via the record command.
        System.out.println("Starting recording...");
        try {
            client.calling().record(callId, Map.of(
                    "format", "mp3",
                    "stereo", true
            ));
        } catch (RestError e) {
            System.out.println("  Record failed: " + e.getStatusCode());
        }

        // 4. Hang up via the end command.
        System.out.println("Hanging up...");
        try {
            client.calling().end(callId, Map.of());
            System.out.println("  Call ended.");
        } catch (RestError e) {
            System.out.println("  Hangup failed: " + e.getStatusCode());
        }
    }
}
