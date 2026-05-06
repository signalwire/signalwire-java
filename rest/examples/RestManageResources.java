/**
 * Example: Create an AI agent, search phone numbers, and place a test call.
 *
 * Java funnels AI-agent CRUD through the generic Fabric resources handle
 * with {@code type="ai_agent"} — Python's dedicated {@code fabric.ai_agents}
 * accessor is not ported (see {@code PORT_OMISSIONS.md}). Phone-number
 * search uses the {@code Map<String,String>} query-param contract; pass
 * all values as strings.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space (e.g. example.signalwire.com)
 *
 * For full HTTP debug output:
 *   SIGNALWIRE_LOG_LEVEL=debug
 */

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;

import java.util.Map;

public class RestManageResources {

    public static void main(String[] args) {
        var client = RestClient.builder().build();

        // 1. Create an AI agent via the typed sub-namespace
        //    (Python parity: client.fabric.ai_agents.create(...)).
        System.out.println("Creating AI agent...");
        var agent = client.fabric().aiAgents().create(Map.of(
                "name", "Demo Support Bot",
                "prompt", Map.of("text", "You are a friendly support agent for Acme Corp.")
        ));
        String agentId = (String) agent.get("id");
        System.out.println("  Created agent: " + agentId);

        // 2. List AI agents via the typed sub-namespace.
        System.out.println("\nListing AI agents...");
        var resources = client.fabric().aiAgents().list();
        System.out.println("  Found: " + resources);

        // 3. Search for a phone number. search() takes Map<String,String>.
        System.out.println("\nSearching for available phone numbers...");
        var available = client.phoneNumbers().search(Map.of(
                "area_code", "512",
                "max_results", "3"
        ));
        System.out.println("  Available: " + available);

        // 4. Place a test call via the native calling CRUD resource.
        System.out.println("\nPlacing a test call...");
        try {
            var result = client.calling().calls().create(Map.of(
                    "from", "+15559876543",
                    "to", "+15551234567",
                    "url", "https://example.com/call-handler"
            ));
            System.out.println("  Call initiated: " + result);
        } catch (RestError e) {
            System.out.println("  Call failed (expected in demo): " + e.getStatusCode());
        }

        // 5. Clean up: delete the agent.
        System.out.println("\nDeleting agent " + agentId + "...");
        client.fabric().aiAgents().delete(agentId);
        System.out.println("  Deleted.");
    }
}
