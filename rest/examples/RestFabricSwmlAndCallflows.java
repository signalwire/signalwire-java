/**
 * Example: SWML scripts and call flow management via the REST API.
 *
 * SWML scripts live under Fabric at {@code client.fabric().swmlScripts()}
 * (matching python/go). Call flows live under {@code client.fabric().callFlows()}
 * — the typed sub-namespace that mirrors Python's {@code fabric.call_flows}.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;

import java.util.List;
import java.util.Map;

public class RestFabricSwmlAndCallflows {

    public static void main(String[] args) {
        var client = RestClient.builder().build();

        // 1. Create a SWML script via the Fabric swml_scripts resource.
        System.out.println("Creating SWML script...");
        try {
            // `contents` is the SWML document as a string (the spec declares it a
            // string, not a nested object).
            var swmlScript = client.fabric().swmlScripts().create(Map.of(
                    "name", "greeting-script",
                    "contents",
                    "{\"version\":\"1.0.0\",\"sections\":{\"main\":["
                            + "{\"answer\":{}},"
                            + "{\"play\":{\"url\":\"say:Welcome to our service\"}},"
                            + "{\"hangup\":{}}]}}"
            ));
            System.out.println("  SWML script: " + swmlScript);
        } catch (RestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 2. List SWML scripts.
        System.out.println("\nListing SWML scripts...");
        try {
            var swmlScripts = client.fabric().swmlScripts().list();
            System.out.println("  Scripts: " + swmlScripts);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 3. Create a call flow via the typed sub-namespace
        //    (Python parity: client.fabric.call_flows.create(...)).
        System.out.println("\nCreating call flow...");
        try {
            var callFlow = client.fabric().callFlows().create(Map.of(
                    "title", "main-routing"
            ));
            System.out.println("  Call flow: " + callFlow);
        } catch (RestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }
    }
}
