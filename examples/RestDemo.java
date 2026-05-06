/**
 * Using the REST client to manage SignalWire resources.
 *
 * Requires env vars:
 *   SIGNALWIRE_PROJECT_ID
 *   SIGNALWIRE_API_TOKEN
 *   SIGNALWIRE_SPACE
 */

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;

import java.util.Map;

public class RestDemo {

    public static void main(String[] args) {
        // Build REST client (reads env vars automatically)
        var client = RestClient.builder().build();

        // 1. Create an AI agent via the typed sub-namespace
        //    (Python parity: client.fabric.ai_agents.create(...))
        System.out.println("Creating Fabric resource...");
        try {
            var resource = client.fabric().aiAgents().create(Map.of(
                    "name", "Demo Bot"
            ));
            System.out.println("  Resource created: " + resource.get("id"));

            // Clean up
            client.fabric().aiAgents().delete((String) resource.get("id"));
            System.out.println("  Resource deleted.");
        } catch (RestError e) {
            System.out.println("  Failed (expected in demo): " + e.getMessage());
        }

        // 2. Search for phone numbers
        System.out.println("\nSearching for available numbers...");
        try {
            var numbers = client.phoneNumbers().search(Map.of(
                    "area_code", "512",
                    "max_results", "3"
            ));
            System.out.println("  Available: " + numbers);
        } catch (RestError e) {
            System.out.println("  Search failed: " + e.getMessage());
        }

        // 3. Upload a document to Datasphere
        System.out.println("\nCreating Datasphere document...");
        try {
            var doc = client.datasphere().documents().create(Map.of(
                    "name", "FAQ",
                    "content", "SignalWire provides voice, video, and messaging APIs."
            ));
            System.out.println("  Document created: " + doc.get("id"));
        } catch (RestError e) {
            System.out.println("  Create failed: " + e.getMessage());
        }

        // 4. List video rooms
        System.out.println("\nListing video rooms...");
        try {
            var rooms = client.video().rooms().list();
            System.out.println("  Rooms: " + rooms);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getMessage());
        }

        System.out.println("\nREST demo complete.");
    }
}
