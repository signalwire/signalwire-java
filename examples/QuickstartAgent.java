/**
 * README quickstart — the AI Agents block.
 *
 * The `region: quickstart` span below is included byte-for-byte into README.md
 * via `<!-- include: examples/QuickstartAgent.java#quickstart -->`, so the doc
 * code is this compiled, gate-run example and can never drift.
 *
 * Test locally without running a server:
 *   bin/swaig-test --url http://user:pass@localhost:3000 --list-tools
 *   bin/swaig-test --url http://user:pass@localhost:3000 --exec get_time
 */

// region: quickstart
import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class QuickstartAgent {
    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("my-agent")
                .route("/")
                .port(3000)
                .build();

        agent.addLanguage("English", "en-US", "inworld.Mark");
        agent.promptAddSection("Role", "You are a helpful assistant.");
        agent.promptAddSection("Rules", "", List.of(
                "Always answer concisely",
                "Use the get_time tool when asked about the time"
        ));

        agent.defineTool(
                "get_time",
                "Get the current time",
                Map.of(),
                (toolArgs, rawData) ->
                        new FunctionResult("The time is " + LocalTime.now())
        );

        agent.run();
    }
}
// endregion: quickstart
