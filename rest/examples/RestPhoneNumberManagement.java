/**
 * Example: Search, purchase, update, and release phone numbers.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.agents.rest.SignalWireClient;
import com.signalwire.agents.rest.SignalWireRestError;

import java.util.Map;

public class RestPhoneNumberManagement {

    public static void main(String[] args) {
        var client = SignalWireClient.builder().build();

        // 1. Search for available numbers
        System.out.println("Searching for available phone numbers in area code 512...");
        try {
            var results = client.phoneNumbers().search(Map.of(
                    "area_code", "512",
                    "max_results", 5
            ));
            System.out.println("  Available: " + results);
        } catch (SignalWireRestError e) {
            System.out.println("  Search failed: " + e.getStatusCode());
        }

        // 2. List owned numbers
        System.out.println("\nListing owned phone numbers...");
        try {
            var owned = client.phoneNumbers().list();
            System.out.println("  Owned numbers: " + owned);
        } catch (SignalWireRestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 3. Purchase a number (using a test number)
        System.out.println("\nPurchasing a phone number...");
        try {
            var purchased = client.phoneNumbers().purchase(Map.of(
                    "number", "+15125551234"
            ));
            String numberId = (String) purchased.get("id");
            System.out.println("  Purchased: " + numberId);

            // 4. Update the number
            System.out.println("\nUpdating phone number...");
            client.phoneNumbers().update(numberId, Map.of(
                    "name", "Main Office Line"
            ));
            System.out.println("  Updated.");

            // 5. Release the number
            System.out.println("\nReleasing phone number...");
            client.phoneNumbers().release(numberId);
            System.out.println("  Released.");

        } catch (SignalWireRestError e) {
            System.out.println("  Operation failed (expected in demo): " + e.getStatusCode());
        }
    }
}
