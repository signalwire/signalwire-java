/**
 * Example: SWML scripts and call flow management via the REST API.
 *
 * Java routes SWML scripts through {@code client.swml().scripts()} (the
 * top-level SWML namespace) and call-flow resources through the generic
 * {@code client.fabric().resources()} handle — filtered by resource type.
 * The Python SDK's per-subresource accessors on {@code fabric.swml_scripts}
 * and {@code fabric.call_flows} are folded into these two entry points;
 * see {@code PORT_OMISSIONS.md} and {@code rest/docs/fabric.md} for the
 * full mapping.
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

        // 1. Create a SWML script via the top-level SWML namespace.
        System.out.println("Creating SWML script...");
        try {
            var swml = client.swml().scripts().create(Map.of(
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
        } catch (RestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 2. List SWML scripts.
        System.out.println("\nListing SWML scripts...");
        try {
            var scripts = client.swml().scripts().list();
            System.out.println("  Scripts: " + scripts);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 3. Create a call flow via the generic Fabric resources handle
        //    with type="call_flow". Java funnels every Fabric subresource
        //    through this one CrudResource rather than exposing a bespoke
        //    accessor per type.
        System.out.println("\nCreating call flow...");
        try {
            var callFlow = client.fabric().resources().create(Map.of(
                    "type", "call_flow",
                    "name", "main-routing",
                    "description", "Main IVR with AI fallback"
            ));
            System.out.println("  Call flow: " + callFlow);
        } catch (RestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }
    }
}
