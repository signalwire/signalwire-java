/**
 * Using the REST client to manage SignalWire resources.
 *
 * Requires env vars:
 *   SIGNALWIRE_PROJECT_ID
 *   SIGNALWIRE_API_TOKEN
 *   SIGNALWIRE_SPACE
 */

import com.signalwire.agents.rest.SignalWireClient;
import com.signalwire.agents.rest.SignalWireRestError;

import java.util.Map;

public class RestDemo {

    public static void main(String[] args) {
        // Build REST client (reads env vars automatically)
        var client = SignalWireClient.builder().build();

        // 1. Create an AI agent via the Fabric API
        System.out.println("Creating AI agent...");
        try {
            var agent = client.fabric().aiAgents().create(Map.of(
                    "name", "Demo Bot",
                    "prompt", Map.of("text", "You are a helpful assistant.")
            ));
            System.out.println("  Agent created: " + agent.get("id"));

            // Clean up
            client.fabric().aiAgents().delete((String) agent.get("id"));
            System.out.println("  Agent deleted.");
        } catch (SignalWireRestError e) {
            System.out.println("  Failed (expected in demo): " + e.getMessage());
        }

        // 2. Search for phone numbers
        System.out.println("\nSearching for available numbers...");
        try {
            var numbers = client.phoneNumbers().search(Map.of(
                    "area_code", "512",
                    "max_results", 3
            ));
            System.out.println("  Available: " + numbers);
        } catch (SignalWireRestError e) {
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
        } catch (SignalWireRestError e) {
            System.out.println("  Create failed: " + e.getMessage());
        }

        // 4. List video rooms
        System.out.println("\nListing video rooms...");
        try {
            var rooms = client.video().rooms().list();
            System.out.println("  Rooms: " + rooms);
        } catch (SignalWireRestError e) {
            System.out.println("  List failed: " + e.getMessage());
        }

        System.out.println("\nREST demo complete.");
    }
}
