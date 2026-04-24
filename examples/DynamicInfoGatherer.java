/**
 * Dynamic InfoGatherer Example.
 *
 * Selects a question set from the CLI argument at agent-startup time, then
 * spins up the InfoGathererAgent prefab with that set. Demonstrates how to
 * swap question packs without recompiling: the same agent binary serves
 * "support", "medical", or "onboarding" depending on how it's invoked.
 *
 * Usage:
 *   java DynamicInfoGatherer              # default set
 *   java DynamicInfoGatherer support      # customer support questions
 *   java DynamicInfoGatherer medical      # medical intake
 *   java DynamicInfoGatherer onboarding   # employee onboarding
 */

import com.signalwire.sdk.prefabs.InfoGathererAgent;

import java.util.List;
import java.util.Map;

public class DynamicInfoGatherer {

    public static void main(String[] args) throws Exception {
        Map<String, List<Map<String, Object>>> questionSets = Map.of(
                "default", List.of(
                        InfoGathererAgent.question("name", "What is your full name?"),
                        InfoGathererAgent.question("phone", "What is your phone number?", true, null),
                        InfoGathererAgent.question("reason", "How can I help you today?")),
                "support", List.of(
                        InfoGathererAgent.question("customer_name", "What is your name?"),
                        InfoGathererAgent.question("account_number", "What is your account number?", true, null),
                        InfoGathererAgent.question("issue", "What issue are you experiencing?"),
                        InfoGathererAgent.question("priority", "How urgent is this? (Low, Medium, High)")),
                "medical", List.of(
                        InfoGathererAgent.question("patient_name", "What is the patient's full name?"),
                        InfoGathererAgent.question("symptoms", "What symptoms are you experiencing?", true, null),
                        InfoGathererAgent.question("duration", "How long have you had these symptoms?"),
                        InfoGathererAgent.question("medications", "Are you currently taking any medications?")),
                "onboarding", List.of(
                        InfoGathererAgent.question("full_name", "What is your full name?"),
                        InfoGathererAgent.question("email", "What is your email address?", true, null),
                        InfoGathererAgent.question("company", "What company do you work for?"),
                        InfoGathererAgent.question("department", "What department?"),
                        InfoGathererAgent.question("start_date", "What is your start date?"))
        );

        String set = args.length > 0 ? args[0] : "default";
        List<Map<String, Object>> questions = questionSets.getOrDefault(set, questionSets.get("default"));
        System.out.println("Loading question set: " + set);

        var agent = new InfoGathererAgent("dynamic-intake", questions, "/contact", 3000);

        System.out.println("Starting InfoGatherer (" + set + ") on port 3000 at /contact...");
        agent.run();
    }
}
