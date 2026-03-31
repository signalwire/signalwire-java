/**
 * Dynamic InfoGatherer Example.
 *
 * Uses a callback to dynamically select questions based on request parameters.
 * Test with: ?set=support, ?set=medical, ?set=onboarding
 */

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.prefabs.InfoGathererAgent;

import java.util.List;
import java.util.Map;

public class DynamicInfoGatherer {

    public static void main(String[] args) throws Exception {
        Map<String, List<Map<String, Object>>> questionSets = Map.of(
                "default", List.of(
                        Map.of("name", "name", "question", "What is your full name?"),
                        Map.of("name", "phone", "question", "What is your phone number?", "confirm", true),
                        Map.of("name", "reason", "question", "How can I help you today?")),
                "support", List.of(
                        Map.of("name", "customer_name", "question", "What is your name?"),
                        Map.of("name", "account_number", "question", "What is your account number?", "confirm", true),
                        Map.of("name", "issue", "question", "What issue are you experiencing?"),
                        Map.of("name", "priority", "question", "How urgent is this? (Low, Medium, High)")),
                "medical", List.of(
                        Map.of("name", "patient_name", "question", "What is the patient's full name?"),
                        Map.of("name", "symptoms", "question", "What symptoms are you experiencing?", "confirm", true),
                        Map.of("name", "duration", "question", "How long have you had these symptoms?"),
                        Map.of("name", "medications", "question", "Are you currently taking any medications?")),
                "onboarding", List.of(
                        Map.of("name", "full_name", "question", "What is your full name?"),
                        Map.of("name", "email", "question", "What is your email address?", "confirm", true),
                        Map.of("name", "company", "question", "What company do you work for?"),
                        Map.of("name", "department", "question", "What department?"),
                        Map.of("name", "start_date", "question", "What is your start date?"))
        );

        var agent = InfoGathererAgent.builder()
                .name("dynamic-intake")
                .route("/contact")
                .port(3000)
                .build();

        agent.setQuestionCallback((queryParams) -> {
            String set = queryParams.getOrDefault("set", "default");
            System.out.println("Dynamic question set: " + set);
            return questionSets.getOrDefault(set, questionSets.get("default"));
        });

        agent.setOnComplete((data) -> {
            System.out.println("All fields collected: " + data);
        });

        System.out.println("Starting Dynamic InfoGatherer on port 3000...");
        System.out.println("  /contact            (default)");
        System.out.println("  /contact?set=support (customer support)");
        System.out.println("  /contact?set=medical (medical intake)");
        System.out.println("  /contact?set=onboarding (employee onboarding)");
        agent.run();
    }
}
