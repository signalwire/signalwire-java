/**
 * Example: Conference and call-flow resources via the Fabric API.
 *
 * Fabric subresources have dedicated accessors that mirror Python's
 * ({@code client.fabric().callFlows()}, {@code .swmlScripts()},
 * {@code .aiAgents()}, {@code .subscribers().sipEndpoints()}, ...). Live
 * video conference sessions are listed via {@code client.video().conferences()}.
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

        // 1. Create a conference room via the typed sub-namespace
        //    (Python parity: client.fabric.conference_rooms.create(...)).
        System.out.println("Creating conference resource...");
        try {
            var conference = client.fabric().conferenceRooms().create(Map.of(
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
            var items = client.fabric().resources().list(Map.of());
            System.out.println("  Items: " + items);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 3. Create a call-flow resource via the typed sub-namespace
        //    (Python parity: client.fabric.call_flows.create(...)).
        System.out.println("\nCreating call flow...");
        try {
            var callFlow = client.fabric().callFlows().create(Map.of(
                    "name", "main-ivr",
                    "description", "Main IVR routing"
            ));
            System.out.println("  Call flow: " + callFlow);
        } catch (RestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 4. List live conference sessions via the video namespace.
        System.out.println("\nListing live conferences...");
        try {
            var liveConferences = client.video().conferences().list();
            System.out.println("  Live: " + liveConferences);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }
    }
}
