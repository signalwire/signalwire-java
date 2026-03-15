/**
 * Example: Upload a document to Datasphere and run a semantic search.
 *
 * Set these env vars:
 *   SIGNALWIRE_PROJECT_ID   - your SignalWire project ID
 *   SIGNALWIRE_API_TOKEN    - your SignalWire API token
 *   SIGNALWIRE_SPACE        - your SignalWire space
 */

import com.signalwire.agents.rest.SignalWireClient;

import java.util.Map;

public class RestDatasphereSearch {

    public static void main(String[] args) {
        var client = SignalWireClient.builder().build();

        // 1. Create a document
        System.out.println("Creating document...");
        var doc = client.datasphere().documents().create(Map.of(
                "name", "FAQ Document",
                "content", "SignalWire is a cloud communications platform. " +
                        "It provides APIs for voice, video, and messaging. " +
                        "The platform supports SWML for call control."
        ));
        String docId = (String) doc.get("id");
        System.out.println("  Created document: " + docId);

        // 2. Search
        System.out.println("\nSearching for 'voice API'...");
        var results = client.datasphere().search(Map.of(
                "query", "voice API",
                "max_results", 5
        ));
        System.out.println("  Results: " + results);

        // 3. List all documents
        System.out.println("\nListing documents...");
        var docs = client.datasphere().documents().list();
        System.out.println("  Documents: " + docs);

        // 4. Clean up
        System.out.println("\nDeleting document " + docId + "...");
        client.datasphere().documents().delete(docId);
        System.out.println("  Deleted.");
    }
}
