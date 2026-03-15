/**
 * Example: Twilio-compatible LAML API for calls and messages.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.agents.rest.SignalWireClient;
import com.signalwire.agents.rest.SignalWireRestError;

import java.util.Map;

public class RestCompatLaml {

    public static void main(String[] args) {
        var client = SignalWireClient.builder().build();

        // 1. Create a LAML call
        System.out.println("Creating LAML call...");
        try {
            var call = client.compat().calls().create(Map.of(
                    "From", "+15559876543",
                    "To", "+15551234567",
                    "Url", "https://example.com/laml-handler"
            ));
            System.out.println("  Call SID: " + call.get("sid"));
        } catch (SignalWireRestError e) {
            System.out.println("  Call failed (expected in demo): " + e.getStatusCode());
        }

        // 2. Send an SMS via LAML
        System.out.println("\nSending SMS via LAML...");
        try {
            var message = client.compat().messages().create(Map.of(
                    "From", "+15559876543",
                    "To", "+15551234567",
                    "Body", "Hello from SignalWire Java SDK!"
            ));
            System.out.println("  Message SID: " + message.get("sid"));
        } catch (SignalWireRestError e) {
            System.out.println("  SMS failed: " + e.getStatusCode());
        }

        // 3. List recent calls
        System.out.println("\nListing recent LAML calls...");
        try {
            var calls = client.compat().calls().list();
            System.out.println("  Calls: " + calls);
        } catch (SignalWireRestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 4. List recent messages
        System.out.println("\nListing recent LAML messages...");
        try {
            var messages = client.compat().messages().list();
            System.out.println("  Messages: " + messages);
        } catch (SignalWireRestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }
    }
}
