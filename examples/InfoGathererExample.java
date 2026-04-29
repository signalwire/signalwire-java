/**
 * Pre-built info-gathering agent with sequential questions.
 *
 * Uses the InfoGathererAgent prefab which wraps the info_gatherer skill
 * for sequential question collection with key/value answers.
 */

import com.signalwire.sdk.prefabs.InfoGathererAgent;

import java.util.List;

public class InfoGathererExample {

    public static void main(String[] args) throws Exception {
        var agent = new InfoGathererAgent(
                "patient-intake",
                List.of(
                        InfoGathererAgent.question("full_name",
                                "What is your full name?"),
                        InfoGathererAgent.question("date_of_birth",
                                "What is your date of birth?",
                                true, null),  // confirm=true
                        InfoGathererAgent.question("phone_number",
                                "What is the best phone number to reach you?"),
                        InfoGathererAgent.question("reason_for_visit",
                                "What is the reason for your visit today?",
                                false, "Be empathetic and understanding"),
                        InfoGathererAgent.question("insurance_provider",
                                "Who is your insurance provider?")
                )
        );

        System.out.println("Starting patient intake agent on port 3000...");
        agent.run();
    }
}
