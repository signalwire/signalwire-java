/**
 * Example: Queue and recording management via the REST API.
 *
 * The Java SDK exposes queue and recording CRUD through dedicated
 * namespaces: {@code client.queues().queues()} and
 * {@code client.recordings().recordings()}. MFA verification is not
 * surfaced on the Java port — see {@code PORT_OMISSIONS.md}; it is
 * typically invoked via the messaging or voice flows.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;

import java.util.Map;

public class RestQueuesMfaAndRecordings {

    public static void main(String[] args) {
        var client = RestClient.builder().build();

        // 1. List call queues via the queues namespace.
        System.out.println("Listing queues...");
        try {
            var queues = client.queues().queues().list();
            System.out.println("  Queues: " + queues);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 2. Create a queue.
        System.out.println("\nCreating a queue...");
        try {
            var queue = client.queues().queues().create(Map.of(
                    "name", "support-queue",
                    "max_size", 50
            ));
            System.out.println("  Queue created: " + queue);
        } catch (RestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 3. List recordings via the recordings namespace.
        System.out.println("\nListing recordings...");
        try {
            var recordings = client.recordings().recordings().list();
            System.out.println("  Recordings: " + recordings);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 4. Fetch a specific recording by ID (demo).
        System.out.println("\nFetching a recording by ID (demo)...");
        try {
            var recording = client.recordings().recordings().get("example-recording-id");
            System.out.println("  Recording: " + recording);
        } catch (RestError e) {
            System.out.println("  Fetch failed (expected in demo): " + e.getStatusCode());
        }
    }
}
