/**
 * Per-Question Function Whitelist Demo (gather_info)
 *
 * This example exists to teach one specific gotcha: while a step's
 * gather_info is asking questions, ALL of the step's other functions are
 * forcibly deactivated. The only callable tools during a gather question
 * are:
 *
 * <ul>
 *   <li>{@code gather_submit} (the native answer-submission tool, always
 *       active)</li>
 *   <li>Whatever names you list in that question's {@code functions}
 *       argument</li>
 * </ul>
 *
 * <p>{@code next_step} and {@code change_context} are also filtered out —
 * the model literally cannot navigate away until the gather completes. This
 * is by design: it forces a tight ask → submit → next-question loop.
 *
 * <p>If a question needs to call out to a tool — for example, to validate
 * an email format, geocode a ZIP, or look up something from an external
 * service — you must list that tool name in the question's
 * {@code functions} argument. The function is active ONLY for that
 * question.
 *
 * <p>Below: a customer-onboarding gather flow where each question unlocks
 * a different validation tool, and where the step's own non-gather tools
 * ({@code escalate_to_human}, {@code lookup_existing_account}) are LOCKED
 * OUT during gather because they aren't whitelisted on any question.
 *
 * <p>Run this file to see the resulting SWML.
 */

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

public class GatherPerQuestionFunctionsDemo {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("gather_per_question_functions_demo")
                .route("/")
                .port(3000)
                .build();

        // Tools that the step would normally have available — but during
        // gather questioning, they're all locked out unless they appear in
        // a question's `functions` whitelist.
        agent.defineTool("validate_email",
                "Validate that an email address is well-formed and deliverable",
                Map.of("type", "object", "properties", Map.of(
                        "email", Map.of("type", "string"))),
                (a, raw) -> new FunctionResult("valid"));

        agent.defineTool("geocode_zip",
                "Look up the city/state for a US ZIP code",
                Map.of("type", "object", "properties", Map.of(
                        "zip", Map.of("type", "string"))),
                (a, raw) -> new FunctionResult("{\"city\":\"...\",\"state\":\"...\"}"));

        agent.defineTool("check_age_eligibility",
                "Verify the customer is old enough for the product",
                Map.of("type", "object", "properties", Map.of(
                        "age", Map.of("type", "integer"))),
                (a, raw) -> new FunctionResult("eligible"));

        // These tools are NOT whitelisted on any gather question. They are
        // registered on the agent and active outside the gather, but during
        // the gather they cannot be called — gather mode locks them out.
        agent.defineTool("escalate_to_human",
                "Transfer the conversation to a live agent",
                Map.of("type", "object", "properties", Map.of()),
                (a, raw) -> new FunctionResult("transferred"));

        agent.defineTool("lookup_existing_account",
                "Search for an existing account by email",
                Map.of("type", "object", "properties", Map.of(
                        "email", Map.of("type", "string"))),
                (a, raw) -> new FunctionResult("not found"));

        // Build a single-context agent with one onboarding step.
        var cb = agent.defineContexts();
        var ctx = cb.addContext("default");

        var onboard = ctx.addStep("onboard")
                .setText(
                        "Onboard a new customer by collecting their details. "
                                + "Use gather_info to ask one question at a "
                                + "time. Each question may unlock a specific "
                                + "validation tool — only that tool and "
                                + "gather_submit are callable while "
                                + "answering it.")
                .setFunctions(List.of(
                        // Outside of the gather (which is the entire step
                        // here), these would be available. During the
                        // gather they are forcibly hidden in favor of the
                        // per-question whitelists.
                        "escalate_to_human",
                        "lookup_existing_account"))
                .setGatherInfo(
                        "customer",
                        "next_step",
                        "I'll need to collect a few details to set up "
                                + "your account. I'll ask one question at a "
                                + "time.");

        // Question 1: email — only validate_email + gather_submit callable.
        onboard.addGatherQuestion(
                "email",
                "What's your email address?",
                "string",
                /* confirm */ true,
                /* prompt  */ null,
                List.of("validate_email"));

        // Question 2: zip — only geocode_zip + gather_submit callable.
        onboard.addGatherQuestion(
                "zip",
                "What's your ZIP code?",
                "string",
                /* confirm */ false,
                /* prompt  */ null,
                List.of("geocode_zip"));

        // Question 3: age — only check_age_eligibility + gather_submit
        // callable.
        onboard.addGatherQuestion(
                "age",
                "How old are you?",
                "integer",
                /* confirm */ false,
                /* prompt  */ null,
                List.of("check_age_eligibility"));

        // Question 4: referral_source — no functions list → only
        // gather_submit is callable. The model cannot validate, lookup,
        // escalate — nothing. This is the right pattern when a question
        // needs no tools.
        onboard.addGatherQuestion("referral_source", "How did you hear about us?");

        // A simple confirmation step the gather auto-advances into.
        ctx.addStep("confirm")
                .setText(
                        "Read the collected info back to the customer and "
                                + "confirm everything is correct.")
                .setFunctions(List.of())
                .setEnd(true);

        String swml = agent.renderSwmlJson("http://localhost:3000");
        System.out.println(swml);
    }
}
