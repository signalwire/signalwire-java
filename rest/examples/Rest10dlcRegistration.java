/**
 * Example: 10DLC campaign registration — brand and campaign management.
 *
 * Java exposes 10DLC / TCR registration through {@code client.registry()}
 * (the canonical {@code /api/relay/rest/registry/beta} API): {@code brands()},
 * {@code campaigns()}, {@code orders()}, {@code numbers()}. Pick the specific
 * sub-resource first — there is no flat {@code .create(...)} / {@code .list()}
 * on the namespace itself.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;

import java.util.Map;

public class Rest10dlcRegistration {

    public static void main(String[] args) {
        var client = RestClient.builder().build();

        // 1. Create a 10DLC brand via the brands sub-resource.
        System.out.println("Creating 10DLC brand...");
        try {
            var brand = client.registry().brands().create(Map.of(
                    "entity_type", "PRIVATE_PROFIT",
                    "company_name", "Acme Corp",
                    "ein", "12-3456789",
                    "phone", "+15551234567",
                    "street", "123 Main St",
                    "city", "Austin",
                    "state", "TX",
                    "postal_code", "78701",
                    "country", "US",
                    "vertical", "TECHNOLOGY"
            ));
            System.out.println("  Brand: " + brand);
        } catch (RestError e) {
            System.out.println("  Create failed (expected in demo): " + e.getStatusCode());
        }

        // 2. List brands.
        System.out.println("\nListing 10DLC brands...");
        try {
            var brands = client.registry().brands().list();
            System.out.println("  Brands: " + brands);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 3. Inspect a campaign by id.
        System.out.println("\nRetrieving a 10DLC campaign...");
        try {
            var campaign = client.registry().campaigns().get("campaign-id");
            System.out.println("  Campaign: " + campaign);
        } catch (RestError e) {
            System.out.println("  Retrieve failed: " + e.getStatusCode());
        }
    }
}
