/**
 * Example: SWML scripts and call flow management via the Fabric API.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.agents.rest.SignalWireClient;
import com.signalwire.agents.rest.SignalWireRestError;

import java.util.List;
import java.util.Map;

public class RestFabricSwmlAndCallflows {

    public static void main(String[] args) {
        var client = SignalWireClient.builder().build();

        // 1. Create a SWML script
        System.out.println("Creating SWML script...");
        try {
            var swml = client.fabric().swmlScripts().create(Map.of(
                    "name", "greeting-script",
                    "content", Map.of(
                            "version", "1.0.0",
                            "sections", Map.of("main", List.of(
                                    Map.of("answer", Map.of()),
                                    Map.of("play", Map.of("url", "say:Welcome to our service")),
                                    Map.of("hangup", Map.of())
                            ))
                    )
            ));
            System.out.println("  SWML script: " + swml);
        } catch (SignalWireRestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 2. List SWML scripts
        System.out.println("\nListing SWML scripts...");
        try {
            var scripts = client.fabric().swmlScripts().list();
            System.out.println("  Scripts: " + scripts);
        } catch (SignalWireRestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 3. Create a call flow
        System.out.println("\nCreating call flow...");
        try {
            var callFlow = client.fabric().callFlows().create(Map.of(
                    "name", "main-routing",
                    "description", "Main IVR with AI fallback"
            ));
            System.out.println("  Call flow: " + callFlow);
        } catch (SignalWireRestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }
    }
}
