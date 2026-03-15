/**
 * Example: Subscriber and SIP endpoint management via the Fabric API.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.agents.rest.SignalWireClient;
import com.signalwire.agents.rest.SignalWireRestError;

import java.util.Map;

public class RestFabricSubscribersAndSip {

    public static void main(String[] args) {
        var client = SignalWireClient.builder().build();

        // 1. Create a subscriber
        System.out.println("Creating subscriber...");
        try {
            var subscriber = client.fabric().subscribers().create(Map.of(
                    "first_name", "Jane",
                    "last_name", "Doe",
                    "email", "jane.doe@example.com"
            ));
            System.out.println("  Subscriber: " + subscriber);
        } catch (SignalWireRestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 2. List subscribers
        System.out.println("\nListing subscribers...");
        try {
            var subscribers = client.fabric().subscribers().list();
            System.out.println("  Subscribers: " + subscribers);
        } catch (SignalWireRestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 3. Create a SIP endpoint
        System.out.println("\nCreating SIP endpoint...");
        try {
            var sipEndpoint = client.fabric().sipEndpoints().create(Map.of(
                    "name", "office-phone",
                    "username", "jane.doe",
                    "password", "secure-password-here"
            ));
            System.out.println("  SIP endpoint: " + sipEndpoint);
        } catch (SignalWireRestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 4. List SIP endpoints
        System.out.println("\nListing SIP endpoints...");
        try {
            var sipEndpoints = client.fabric().sipEndpoints().list();
            System.out.println("  SIP endpoints: " + sipEndpoints);
        } catch (SignalWireRestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }
    }
}
