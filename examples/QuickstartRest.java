/**
 * README quickstart — the REST Client block.
 *
 * The `region: quickstart` span below is included byte-for-byte into README.md
 * via `<!-- include: examples/QuickstartRest.java#quickstart -->`, so the doc
 * code is this compiled, gate-run example and can never drift.
 *
 * Requires env vars: SIGNALWIRE_PROJECT_ID, SIGNALWIRE_API_TOKEN, SIGNALWIRE_SPACE.
 *
 * NOTE: play() and datasphere search() take generated, typed request objects
 * (Calling.PlayRequest / DatasphereDocuments.SearchRequest), not raw Maps —
 * create()/list()/phoneNumbers().search() still take Maps.
 */

// region: quickstart
import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.rest.namespaces.generated.Calling;
import com.signalwire.sdk.rest.namespaces.generated.DatasphereDocuments;

import java.util.List;
import java.util.Map;

public class QuickstartRest {
    public static void main(String[] args) throws Exception {
        var client = RestClient.builder()
                .project("your-project-id")
                .token("your-api-token")
                .space("example.signalwire.com")
                .build();

        // Create an AI agent
        client.fabric().aiAgents().create(Map.of(
                "name", "Support Bot",
                "prompt", Map.of("text", "You are a helpful support agent.")
        ));

        // Control a live call
        client.calling().play("call-id", Calling.PlayRequest.builder()
                .play(List.of(Map.of("type", "tts", "text", "Hello!")))
                .build());

        // Search for phone numbers
        client.phoneNumbers().search(Map.of("area_code", "512"));

        // Semantic search across documents
        client.datasphere().documents().search(DatasphereDocuments.SearchRequest.builder()
                .queryString("billing policy")
                .build());
    }
}
// endregion: quickstart
