/**
 * Example: Search, buy, update, and release phone numbers.
 *
 * Java's {@code client.phoneNumbers()} namespace exposes CRUD (list / get /
 * create / update / delete) plus a {@code search} helper. Python's
 * {@code purchase} / {@code release} convenience aliases are folded into
 * {@code create} (body includes the number to buy) and {@code delete}
 * (releases by ID). Search takes {@code Map<String,String>} — pass every
 * value as a string.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;

import java.util.Map;

public class RestPhoneNumberManagement {

    public static void main(String[] args) {
        var client = RestClient.builder().build();

        // 1. Search for available numbers (all values as strings).
        System.out.println("Searching for available phone numbers in area code 512...");
        try {
            var results = client.phoneNumbers().search(Map.of(
                    "area_code", "512",
                    "max_results", "5"
            ));
            System.out.println("  Available: " + results);
        } catch (RestError e) {
            System.out.println("  Search failed: " + e.getStatusCode());
        }

        // 2. List owned numbers.
        System.out.println("\nListing owned phone numbers...");
        try {
            var owned = client.phoneNumbers().list();
            System.out.println("  Owned numbers: " + owned);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 3. Purchase a number via create().
        System.out.println("\nPurchasing a phone number...");
        try {
            var purchased = client.phoneNumbers().create(Map.of(
                    "number", "+15125551234"
            ));
            String numberId = (String) purchased.get("id");
            System.out.println("  Purchased: " + numberId);

            // 4. Update the number (e.g. rename).
            System.out.println("\nUpdating phone number...");
            client.phoneNumbers().update(numberId, Map.of(
                    "name", "Main Office Line"
            ));
            System.out.println("  Updated.");

            // 5. Release the number via delete().
            System.out.println("\nReleasing phone number...");
            client.phoneNumbers().delete(numberId);
            System.out.println("  Released.");

        } catch (RestError e) {
            System.out.println("  Operation failed (expected in demo): " + e.getStatusCode());
        }
    }
}
