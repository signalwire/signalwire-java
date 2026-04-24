/**
 * Example: Video room management -- create, list, and manage video rooms.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.RestError;

import java.util.Map;

public class RestVideoRooms {

    public static void main(String[] args) {
        var client = RestClient.builder().build();

        // 1. Create a video room
        System.out.println("Creating video room...");
        try {
            var room = client.video().rooms().create(Map.of(
                    "name", "team-meeting",
                    "max_members", 10,
                    "quality", "1080p",
                    "layout", "grid-responsive"
            ));
            System.out.println("  Room: " + room);
        } catch (RestError e) {
            System.out.println("  Create failed: " + e.getStatusCode());
        }

        // 2. List video rooms
        System.out.println("\nListing video rooms...");
        try {
            var rooms = client.video().rooms().list();
            System.out.println("  Rooms: " + rooms);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 3. List video room sessions (the live / historical room-instance feed).
        System.out.println("\nListing video sessions...");
        try {
            var sessions = client.video().roomSessions().list();
            System.out.println("  Sessions: " + sessions);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }

        // 4. List video recordings
        System.out.println("\nListing video recordings...");
        try {
            var recordings = client.video().recordings().list();
            System.out.println("  Recordings: " + recordings);
        } catch (RestError e) {
            System.out.println("  List failed: " + e.getStatusCode());
        }
    }
}
