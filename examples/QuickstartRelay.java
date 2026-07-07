/**
 * README quickstart — the RELAY Client block.
 *
 * The `region: quickstart` span below is included byte-for-byte into README.md
 * via `<!-- include: examples/QuickstartRelay.java#quickstart -->`, so the doc
 * code is this compiled, gate-run example and can never drift.
 *
 * Requires env vars: SIGNALWIRE_PROJECT_ID, SIGNALWIRE_API_TOKEN, SIGNALWIRE_SPACE
 * (or pass them explicitly to the builder as shown).
 */

// region: quickstart
import com.signalwire.sdk.relay.RelayClient;

import java.util.List;
import java.util.Map;

public class QuickstartRelay {
    public static void main(String[] args) throws Exception {
        var client = RelayClient.builder()
                .project("your-project-id")
                .token("your-api-token")
                .space("example.signalwire.com")
                .contexts(List.of("default"))
                .build();

        client.onCall(call -> {
            call.answer();
            var action = call.play(List.of(Map.of(
                    "type", "tts",
                    "params", Map.of("text", "Welcome to SignalWire!")
            )));
            action.waitForCompletion();
            call.hangup();
        });

        client.run();
    }
}
// endregion: quickstart
