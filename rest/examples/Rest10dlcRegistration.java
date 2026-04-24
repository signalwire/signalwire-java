/**
 * Example: 10DLC campaign registration — brand and campaign management.
 *
 * Java exposes 10DLC resources through sub-CRUD handles on
 * {@code client.campaign()}: {@code brands()}, {@code campaigns()},
 * {@code orders()}, {@code assignments()}. Compliance records live under
 * {@code client.compliance().cnamRegistrations()} and
 * {@code client.compliance().shakenStir()}. There is no flat
 * {@code .create(...)} / {@code .list()} on the namespace itself — pick
 * the specific sub-resource first.
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
            var brand = client.campaign().brands().create(Map.of(
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

        // 2. List campaigns.
        System.out.println("\nListing 10DLC campaigns...");
        try {
            var campaigns = client.campaign().campaigns().list();
            System.out.println("  Campaigns: " + campaigns);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 3. Check compliance status — list CNAM registrations.
        System.out.println("\nChecking compliance (CNAM registrations)...");
        try {
            var cnam = client.compliance().cnamRegistrations().list();
            System.out.println("  CNAM status: " + cnam);
        } catch (RestError e) {
            System.out.println("  Check failed: " + e.getStatusCode());
        }
    }
}
