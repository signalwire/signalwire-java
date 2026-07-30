/**
 * Example: Subscriber and SIP endpoint management via the REST API.
 *
 * <p>Java routes Fabric subscribers through {@code client.fabric().subscribers()}; SIP endpoints
 * are subscriber-scoped Fabric resources reached via {@code
 * client.fabric().subscribers().createSipEndpoint(...)} / {@code listSipEndpoints(...)} (matching
 * python/go — there is no standalone relay-rest SIP-endpoints namespace).
 *
 * <p>Set these env vars: SIGNALWIRE_PROJECT_ID - your SignalWire project ID SIGNALWIRE_API_TOKEN -
 * your SignalWire API token SIGNALWIRE_SPACE - your SignalWire space
 */
import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;
import com.signalwire.sdk.rest.namespaces.generated.Subscribers;
import java.util.Map;

public class RestFabricSubscribersAndSip {

  public static void main(String[] args) {
    var client = RestClient.builder().build();

    // 1. Create a Fabric subscriber.
    System.out.println("Creating subscriber...");
    try {
      var subscriber =
          client
              .fabric()
              .subscribers()
              .create(
                  Map.of(
                      "first_name", "Jane",
                      "last_name", "Doe",
                      "email", "jane.doe@example.com"));
      System.out.println("  Subscriber: " + subscriber);
    } catch (RestError e) {
      System.out.println("  Create failed: " + e.getStatusCode());
    }

    // 2. List Fabric subscribers.
    System.out.println("\nListing subscribers...");
    try {
      var subscribers = client.fabric().subscribers().list();
      System.out.println("  Subscribers: " + subscribers);
    } catch (RestError e) {
      System.out.println("  List failed: " + e.getStatusCode());
    }

    // 3. Create a SIP endpoint for the subscriber (Fabric-scoped).
    System.out.println("\nCreating SIP endpoint...");
    try {
      var sipEndpoint =
          client
              .fabric()
              .subscribers()
              .createSipEndpoint(
                  "subscriber-id",
                  Subscribers.CreateSipEndpointRequest.builder()
                      .username("jane.doe")
                      .password("secure-password-here")
                      // `caller_id` is a spec-declared optional field; extras carries
                      // any additional spec key not surfaced on the builder.
                      .extras(Map.of("caller_id", "Jane Doe"))
                      .build());
      // SubscriberSIPEndpoint is a pure-data DTO with no toString(); concatenating it
      // printed the identity hash ("SubscriberSIPEndpoint@4488aabb") instead of the
      // endpoint. Print the fields the demo is about.
      System.out.println("  SIP endpoint: " + sipEndpoint.id + " " + sipEndpoint.username);
    } catch (RestError e) {
      System.out.println("  Create failed: " + e.getStatusCode());
    }

    // 4. List the subscriber's SIP endpoints.
    System.out.println("\nListing SIP endpoints...");
    try {
      var sipEndpoints = client.fabric().subscribers().listSipEndpoints("subscriber-id", Map.of());
      // SubscriberSipEndpointListResponse is a pure-data DTO with no toString(); concatenating it
      // printed the
      // identity hash ("SubscriberSipEndpointListResponse@4488aabb") instead of any result. The
      // results live on
      // `data`.
      System.out.println("  SIP endpoints: " + sipEndpoints.data);
    } catch (RestError e) {
      System.out.println("  List failed: " + e.getStatusCode());
    }
  }
}
