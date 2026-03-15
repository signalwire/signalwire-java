/**
 * Example: 10DLC campaign registration -- brand and campaign management.
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

public class Rest10dlcRegistration {

    public static void main(String[] args) {
        var client = SignalWireClient.builder().build();

        // 1. Create a brand
        System.out.println("Creating 10DLC brand...");
        try {
            var brand = client.campaign().create(Map.of(
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
        } catch (SignalWireRestError e) {
            System.out.println("  Create failed (expected in demo): " + e.getStatusCode());
        }

        // 2. List brands/campaigns
        System.out.println("\nListing 10DLC campaigns...");
        try {
            var campaigns = client.campaign().list();
            System.out.println("  Campaigns: " + campaigns);
        } catch (SignalWireRestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 3. Check compliance status
        System.out.println("\nChecking compliance...");
        try {
            var compliance = client.compliance().list();
            System.out.println("  Compliance status: " + compliance);
        } catch (SignalWireRestError e) {
            System.out.println("  Check failed: " + e.getStatusCode());
        }
    }
}
