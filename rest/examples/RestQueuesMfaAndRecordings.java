/**
 * Example: Queue management, MFA verification, and recording operations.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.agents.rest.SignalWireClient;
import com.signalwire.agents.rest.SignalWireRestError;

import java.util.Map;

public class RestQueuesMfaAndRecordings {

    public static void main(String[] args) {
        var client = SignalWireClient.builder().build();

        // 1. List call queues
        System.out.println("Listing queues...");
        try {
            var queues = client.queue().list();
            System.out.println("  Queues: " + queues);
        } catch (SignalWireRestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 2. Create a queue
        System.out.println("\nCreating a queue...");
        try {
            var queue = client.queue().create(Map.of(
                    "name", "support-queue",
                    "max_size", 50
            ));
            System.out.println("  Queue created: " + queue);
        } catch (SignalWireRestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 3. List recordings
        System.out.println("\nListing recordings...");
        try {
            var recordings = client.recording().list();
            System.out.println("  Recordings: " + recordings);
        } catch (SignalWireRestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 4. Send MFA verification
        System.out.println("\nSending MFA verification...");
        try {
            // MFA is typically handled through the calling or messaging namespace
            var result = client.calling().execute("send_digits", Map.of(
                    "call_id", "example-call-id",
                    "digits", "1234#"
            ));
            System.out.println("  MFA sent: " + result);
        } catch (SignalWireRestError e) {
            System.out.println("  MFA failed (expected in demo): " + e.getStatusCode());
        }
    }
}
