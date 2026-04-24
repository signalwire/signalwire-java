/**
 * Example: Conference and call-flow resources via the Fabric API.
 *
 * Java routes Fabric subresources (conferences, call flows, SWML scripts,
 * AI agents, SIP endpoints) through the generic
 * {@code client.fabric().resources()} CRUD handle, distinguished by the
 * {@code type} field in the body. Python's per-subresource accessors
 * ({@code fabric.conferences}, {@code fabric.call_flows}, ...) are folded
 * into this one entry point. Top-level conference participants live
 * under the dedicated {@code client.conferences()} namespace.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;

import java.util.Map;

public class RestFabricConferencesAndRouting {

    public static void main(String[] args) {
        var client = RestClient.builder().build();

        // 1. Create a conference Fabric resource via the generic handle.
        System.out.println("Creating conference resource...");
        try {
            var conference = client.fabric().resources().create(Map.of(
                    "type", "conference_room",
                    "name", "team-standup",
                    "max_members", 25,
                    "record", true
            ));
            System.out.println("  Conference: " + conference);
        } catch (RestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 2. List Fabric resources (filter client-side to type="conference_room").
        System.out.println("\nListing Fabric resources...");
        try {
            var items = client.fabric().resources().list();
            System.out.println("  Items: " + items);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 3. Create a call-flow resource for routing.
        System.out.println("\nCreating call flow...");
        try {
            var callFlow = client.fabric().resources().create(Map.of(
                    "type", "call_flow",
                    "name", "main-ivr",
                    "description", "Main IVR routing"
            ));
            System.out.println("  Call flow: " + callFlow);
        } catch (RestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 4. List live conference sessions under the dedicated namespace.
        System.out.println("\nListing live conferences...");
        try {
            var liveConferences = client.conferences().conferences().list();
            System.out.println("  Live: " + liveConferences);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }
    }
}
