/**
 * Example: Place a call via the REST API and inspect the result.
 *
 * The Java SDK surfaces calling via a CRUD resource on
 * {@code client.calling().calls()} (native API) and on
 * {@code client.compat().calls()} (CXML/Twilio-compat API). Python's
 * {@code calling.execute("dial", ...)} RPC shim is intentionally not ported
 * — Java callers POST to the create endpoint with the standard parameters;
 * see {@code PORT_OMISSIONS.md} and {@code rest/docs/calling.md}.
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

        // 1. Dial a number — create a call via the native calling namespace.
        System.out.println("Dialing...");
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

        // 2. Update the call to play TTS (SWML-driven updates flow through
        //    the same CRUD handle using the standard PATCH verb).
        System.out.println("Sending play update...");
        try {
            client.calling().calls().update(callId, Map.of(
                    "play", Map.of("text", "Please leave a message after the beep."),
                    "beep", true
            ));
        } catch (RestError e) {
            System.out.println("  Play failed: " + e.getStatusCode());
        }

        // 3. Fetch the current call state to inspect recording metadata.
        System.out.println("Fetching call...");
        try {
            var call = client.calling().calls().get(callId);
            System.out.println("  Call: " + call);
        } catch (RestError e) {
            System.out.println("  Fetch failed: " + e.getStatusCode());
        }

        // 4. Hang up — delete the call.
        System.out.println("Hanging up...");
        try {
            client.calling().calls().delete(callId);
            System.out.println("  Call ended.");
        } catch (RestError e) {
            System.out.println("  Hangup failed: " + e.getStatusCode());
        }
    }
}
