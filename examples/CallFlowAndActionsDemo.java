/**
 * 5-phase call flow with pre/post answer verbs, recording, and post-AI actions.
 *
 * Demonstrates the full call lifecycle:
 *   Phase 1: Pre-answer verbs (e.g., play hold music before answering)
 *   Phase 2: Answer with recording
 *   Phase 3: Post-answer verbs
 *   Phase 4: AI agent conversation
 *   Phase 5: Post-AI verbs (e.g., cleanup, hangup)
 */

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

public class CallFlowAndActionsDemo {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("callflow-agent")
                .route("/")
                .port(3000)
                .autoAnswer(true)
                .maxDuration(1800)       // 30 minutes max
                .recordCall(true)        // Record the call
                .recordFormat("mp4")
                .recordStereo(true)
                .build();

        // Phase 1: Pre-answer verbs
        // (could play hold music or collect DTMF before answering)

        // Phase 3: Post-answer verbs (after answer, before AI)
        agent.addPostAnswerVerb("play", Map.of(
                "url", "say:Please wait while we connect you with our AI assistant."
        ));

        // Phase 4: AI configuration
        agent.promptAddSection("Role",
                "You are a customer service representative for TechCorp.");
        agent.promptAddSection("Instructions", "", List.of(
                "Be professional and helpful",
                "Collect the customer's issue description",
                "Offer solutions or escalate to a human agent"
        ));

        // Set AI parameters
        agent.setPromptLlmParams(Map.of(
                "temperature", 0.3,
                "top_p", 0.9
        ));

        // Set post-prompt for conversation summary
        agent.setPostPrompt(
                "Summarize the call: what was the issue, what was the resolution.");
        agent.setPostPromptLlmParams(Map.of(
                "temperature", 0.1
        ));

        // Add pronunciations
        agent.addPronunciation("TechCorp", "TekCorp", true);

        // Add speech hints
        agent.addHints(List.of("TechCorp", "support", "technical", "billing"));

        // Add language support
        agent.addLanguage("English", "en-US", "en-US-Standard-C");
        agent.addLanguage("Spanish", "es-US", "es-US-Standard-A");

        // Tools
        agent.defineTool("escalate", "Escalate to a human agent",
                Map.of("type", "object", "properties", Map.of(
                        "reason", Map.of("type", "string", "description", "Reason for escalation")
                ), "required", List.of("reason")),
                (toolArgs, raw) -> new FunctionResult(
                        "Transferring you to a human agent for: " + toolArgs.get("reason"))
                        .connect("+15551234567", true));

        // Phase 5: Post-AI verbs (after AI finishes)
        agent.addPostAiVerb("hangup", Map.of());

        // Summary callback
        agent.onSummary((summary, rawPayload) -> {
            System.out.println("Call summary: " + summary);
        });

        System.out.println("Starting call flow agent on port 3000...");
        agent.run();
    }
}
