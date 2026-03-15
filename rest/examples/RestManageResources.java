/**
 * Example: Create an AI agent, assign a phone number, and place a test call.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space (e.g. example.signalwire.com)
 *
 * For full HTTP debug output:
 *   SIGNALWIRE_LOG_LEVEL=debug
 */

import com.signalwire.agents.rest.SignalWireClient;
import com.signalwire.agents.rest.SignalWireRestError;

import java.util.Map;

public class RestManageResources {

    public static void main(String[] args) {
        var client = SignalWireClient.builder().build();

        // 1. Create an AI agent
        System.out.println("Creating AI agent...");
        var agent = client.fabric().aiAgents().create(Map.of(
                "name", "Demo Support Bot",
                "prompt", Map.of("text", "You are a friendly support agent for Acme Corp.")
        ));
        String agentId = (String) agent.get("id");
        System.out.println("  Created agent: " + agentId);

        // 2. List all AI agents
        System.out.println("\nListing AI agents...");
        var agents = client.fabric().aiAgents().list();
        System.out.println("  Found agents: " + agents);

        // 3. Search for a phone number
        System.out.println("\nSearching for available phone numbers...");
        var available = client.phoneNumbers().search(Map.of(
                "area_code", "512",
                "max_results", 3
        ));
        System.out.println("  Available: " + available);

        // 4. Place a test call (requires valid numbers)
        System.out.println("\nPlacing a test call...");
        try {
            var result = client.calling().execute("dial", Map.of(
                    "from", "+15559876543",
                    "to", "+15551234567",
                    "url", "https://example.com/call-handler"
            ));
            System.out.println("  Call initiated: " + result);
        } catch (SignalWireRestError e) {
            System.out.println("  Call failed (expected in demo): " + e.getStatusCode());
        }

        // 5. Clean up: delete the agent
        System.out.println("\nDeleting agent " + agentId + "...");
        client.fabric().aiAgents().delete(agentId);
        System.out.println("  Deleted.");
    }
}
