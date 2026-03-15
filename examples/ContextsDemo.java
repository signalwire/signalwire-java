/**
 * Structured workflows with contexts and steps.
 *
 * Demonstrates a multi-step onboarding flow where each step has
 * specific criteria, valid navigation targets, and function restrictions.
 */

import com.signalwire.agents.agent.AgentBase;
import com.signalwire.agents.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

public class ContextsDemo {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("onboarding-agent")
                .route("/")
                .port(3000)
                .build();

        agent.promptAddSection("Role",
                "You are an onboarding assistant. Guide users through setup.");

        // Define contexts and steps
        var ctxBuilder = agent.defineContexts();

        var defaultCtx = ctxBuilder.addContext("default");

        // Step 1: Welcome
        var welcomeStep = defaultCtx.addStep("welcome");
        welcomeStep.setStepInstructions(
                "Greet the user and ask for their name and email.");
        welcomeStep.setStepCriteria(
                "User has provided their name and email address.");
        welcomeStep.setValidSteps(List.of("next", "preferences"));
        welcomeStep.setFunctions(List.of("collect_info"));

        // Step 2: Preferences
        var prefsStep = defaultCtx.addStep("preferences");
        prefsStep.setStepInstructions(
                "Ask about the user's preferences and interests.");
        prefsStep.setStepCriteria(
                "User has shared at least 2 preferences.");
        prefsStep.setValidSteps(List.of("next", "welcome"));
        prefsStep.setFunctions(List.of("save_preference"));

        // Step 3: Confirmation
        var confirmStep = defaultCtx.addStep("confirmation");
        confirmStep.setStepInstructions(
                "Summarize the collected information and confirm with the user.");
        confirmStep.setStepCriteria(
                "User has confirmed their information is correct.");
        confirmStep.setFunctions(List.of("finalize_onboarding"));

        // Define tools
        agent.defineTool("collect_info", "Collect user info",
                Map.of("type", "object", "properties", Map.of(
                        "name", Map.of("type", "string", "description", "User name"),
                        "email", Map.of("type", "string", "description", "User email")
                ), "required", List.of("name", "email")),
                (toolArgs, raw) -> new FunctionResult(
                        "Collected: " + toolArgs.get("name") + " (" + toolArgs.get("email") + ")")
                        .updateGlobalData(Map.of(
                                "user_name", toolArgs.get("name"),
                                "user_email", toolArgs.get("email")
                        )));

        agent.defineTool("save_preference", "Save a user preference",
                Map.of("type", "object", "properties", Map.of(
                        "preference", Map.of("type", "string", "description", "User preference")
                ), "required", List.of("preference")),
                (toolArgs, raw) -> new FunctionResult(
                        "Saved preference: " + toolArgs.get("preference")));

        agent.defineTool("finalize_onboarding", "Complete the onboarding process",
                Map.of("type", "object", "properties", Map.of()),
                (toolArgs, raw) -> new FunctionResult(
                        "Onboarding complete! Welcome aboard."));

        System.out.println("Starting onboarding agent on port 3000...");
        agent.run();
    }
}
