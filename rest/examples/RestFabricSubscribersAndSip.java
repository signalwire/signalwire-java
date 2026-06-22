/**
 * Example: Subscriber and SIP endpoint management via the REST API.
 *
 * Java routes Fabric subscribers through {@code client.fabric().subscribers()};
 * SIP endpoints are subscriber-scoped Fabric resources reached via
 * {@code client.fabric().subscribers().createSipEndpoint(...)} /
 * {@code listSipEndpoints(...)} (matching python/go — there is no standalone
 * relay-rest SIP-endpoints namespace).
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;

import java.util.Map;

public class RestFabricSubscribersAndSip {

    public static void main(String[] args) {
        var client = RestClient.builder().build();

        // 1. Create a Fabric subscriber.
        System.out.println("Creating subscriber...");
        try {
            var subscriber = client.fabric().subscribers().create(Map.of(
                    "first_name", "Jane",
                    "last_name", "Doe",
                    "email", "jane.doe@example.com"
            ));
            System.out.println("  Subscriber: " + subscriber);
        } catch (RestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 2. List Fabric subscribers.
        System.out.println("\nListing subscribers...");
        try {
            var subscribers = client.fabric().subscribers().list();
            System.out.println("  Subscribers: " + subscribers);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 3. Create a SIP endpoint for the subscriber (Fabric-scoped).
        System.out.println("\nCreating SIP endpoint...");
        try {
            var sipEndpoint = client.fabric().subscribers().createSipEndpoint("subscriber-id", Map.of(
                    "name", "office-phone",
                    "username", "jane.doe",
                    "password", "secure-password-here"
            ));
            System.out.println("  SIP endpoint: " + sipEndpoint);
        } catch (RestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 4. List the subscriber's SIP endpoints.
        System.out.println("\nListing SIP endpoints...");
        try {
            var sipEndpoints = client.fabric().subscribers().listSipEndpoints("subscriber-id");
            System.out.println("  SIP endpoints: " + sipEndpoints);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }
    }
}
