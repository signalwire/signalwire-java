/**
 * Example: Conference and routing management via the Fabric API.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.agents.rest.SignalWireClient;
import com.signalwire.agents.rest.SignalWireRestError;

import java.util.Map;

public class RestFabricConferencesAndRouting {

    public static void main(String[] args) {
        var client = SignalWireClient.builder().build();

        // 1. Create a conference resource
        System.out.println("Creating conference resource...");
        try {
            var conference = client.fabric().conferences().create(Map.of(
                    "name", "team-standup",
                    "max_members", 25,
                    "record", true
            ));
            System.out.println("  Conference: " + conference);
        } catch (SignalWireRestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 2. List conferences
        System.out.println("\nListing conferences...");
        try {
            var conferences = client.fabric().conferences().list();
            System.out.println("  Conferences: " + conferences);
        } catch (SignalWireRestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 3. Create a call flow for routing
        System.out.println("\nCreating call flow...");
        try {
            var callFlow = client.fabric().callFlows().create(Map.of(
                    "name", "main-ivr",
                    "description", "Main IVR routing"
            ));
            System.out.println("  Call flow: " + callFlow);
        } catch (SignalWireRestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 4. List call flows
        System.out.println("\nListing call flows...");
        try {
            var callFlows = client.fabric().callFlows().list();
            System.out.println("  Call flows: " + callFlows);
        } catch (SignalWireRestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }
    }
}
