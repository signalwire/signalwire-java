/**
 * Step Function Inheritance Demo
 *
 * This example exists to teach one specific gotcha: the per-step
 * {@code functions} whitelist INHERITS from the previous step when omitted.
 *
 * <h2>Why this matters</h2>
 *
 * A common mistake when building multi-step agents is to assume each step
 * starts with a fresh tool set. It does not. The runtime only resets the
 * active set when a step explicitly declares its {@code functions} field.
 * If you forget {@code setFunctions()} on a later step, the previous step's
 * tools quietly remain available.
 *
 * <p>This file shows four step-shaped patterns side by side:
 *
 * <ol>
 *   <li>{@code step_lookup}  — explicitly whitelists {@code lookup_account}</li>
 *   <li>{@code step_inherit} — has NO {@code setFunctions()} call. Inherits
 *       step_lookup's whitelist, so {@code lookup_account} is still
 *       callable here. This is rarely what you want.</li>
 *   <li>{@code step_explicit} — explicitly whitelists {@code process_payment}.
 *       The previously inherited {@code lookup_account} is now disabled,
 *       and only {@code process_payment} is active.</li>
 *   <li>{@code step_disabled} — explicitly disables ALL user functions with
 *       an empty list (or {@code "none"}). Internal tools like
 *       {@code next_step} still work.</li>
 * </ol>
 *
 * <h2>Best practice</h2>
 *
 * Call {@code setFunctions()} on EVERY step that should differ from the
 * previous one. Treat omission as an explicit decision to inherit, not a
 * default.
 *
 * <p>Run this file to see the rendered SWML — there are no real webhook
 * endpoints behind the tools, this is purely a documentation example.
 */

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;

import java.util.List;
import java.util.Map;

public class StepFunctionInheritanceDemo {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("step_function_inheritance_demo")
                .route("/")
                .port(3000)
                .build();

        // Register three SWAIG tools so we have something to whitelist.
        // In a real agent these would call out to webhooks; here they're stubs.
        agent.defineTool("lookup_account",
                "Look up customer account details by account number",
                Map.of("type", "object", "properties", Map.of(
                        "account_number", Map.of("type", "string"))),
                (a, raw) -> new FunctionResult("looked up"));

        agent.defineTool("process_payment",
                "Process a payment for the current customer",
                Map.of("type", "object", "properties", Map.of(
                        "amount", Map.of("type", "number"))),
                (a, raw) -> new FunctionResult("payment processed"));

        agent.defineTool("send_receipt",
                "Email a receipt to the customer",
                Map.of("type", "object", "properties", Map.of(
                        "email", Map.of("type", "string"))),
                (a, raw) -> new FunctionResult("sent"));

        // Build the contexts.
        var cb = agent.defineContexts();
        var ctx = cb.addContext("default");

        // -- Step 1: explicit whitelist --
        // `lookup_account` is the only tool active in this step.
        ctx.addStep("step_lookup")
                .setText(
                        "Greet the customer and ask for their account number. "
                                + "Use lookup_account to fetch their details.")
                .setFunctions(List.of("lookup_account"))
                .setValidSteps(List.of("step_inherit"));

        // -- Step 2: NO setFunctions() call → inheritance --
        // Because we didn't call setFunctions(), this step inherits the
        // active set from step_lookup. `lookup_account` is STILL callable
        // here, even though we never asked for it. Most of the time this
        // is a bug. To break the inheritance, call setFunctions() with an
        // explicit list (even if it's empty).
        ctx.addStep("step_inherit")
                .setText(
                        "Confirm the customer's identity. (No setFunctions() "
                                + "here, so lookup_account is still active — "
                                + "this is the inheritance trap.)")
                .setValidSteps(List.of("step_explicit"));

        // -- Step 3: explicit replacement --
        // Whitelist replaces the inherited set. lookup_account is now
        // inactive; only process_payment is active.
        ctx.addStep("step_explicit")
                .setText(
                        "Take the customer's payment. Use process_payment. "
                                + "lookup_account is no longer available.")
                .setFunctions(List.of("process_payment"))
                .setValidSteps(List.of("step_disabled"));

        // -- Step 4: explicit disable-all --
        // Pass an empty list (or "none") to lock out every user-defined
        // tool. Internal navigation tools (next_step) are unaffected.
        ctx.addStep("step_disabled")
                .setText(
                        "Thank the customer and wrap up. No tools are needed "
                                + "here, so we lock everything down with "
                                + "setFunctions(List.of()).")
                .setFunctions(List.of())
                .setEnd(true);

        // Render the SWML document so you can see exactly which steps have
        // a `functions` key in the output and which don't.
        String swml = agent.renderSwmlJson("http://localhost:3000");
        System.out.println(swml);
    }
}
