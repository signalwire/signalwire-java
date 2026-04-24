/**
 * Example: Subscriber and SIP endpoint management via the REST API.
 *
 * Java routes Fabric subscribers through {@code client.fabric().subscribers()}
 * and top-level SIP endpoints through the dedicated SIP namespace at
 * {@code client.sip().endpoints()}. Python's
 * {@code fabric.sip_endpoints} per-subresource accessor is not ported;
 * SIP endpoints are owned by the SIP namespace in Java, while
 * {@code fabric().resources()} is the generic handle for other Fabric
 * subresource types. See {@code PORT_OMISSIONS.md}.
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

        // 3. Create a SIP endpoint via the SIP namespace.
        System.out.println("\nCreating SIP endpoint...");
        try {
            var sipEndpoint = client.sip().endpoints().create(Map.of(
                    "name", "office-phone",
                    "username", "jane.doe",
                    "password", "secure-password-here"
            ));
            System.out.println("  SIP endpoint: " + sipEndpoint);
        } catch (RestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 4. List SIP endpoints.
        System.out.println("\nListing SIP endpoints...");
        try {
            var endpoints = client.sip().endpoints().list();
            System.out.println("  SIP endpoints: " + endpoints);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }
    }
}
