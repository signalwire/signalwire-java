/**
 * Gather Info Mode Demo.
 *
 * Demonstrates the contexts system's gather_info mode for structured
 * data collection. Questions are presented one at a time and answers
 * are stored in global_data under the configured output key.
 */

import com.signalwire.sdk.agent.AgentBase;

import java.util.List;

public class GatherInfoDemo {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("patient-intake")
                .route("/patient-intake")
                .port(3000)
                .build();

        agent.addLanguage("English", "en-US", "en-US-Standard-C");

        agent.promptAddSection("Role",
                "You are a friendly medical office intake assistant. " +
                "Collect patient information accurately and professionally.");

        // Define contexts with gather info steps
        var ctxBuilder = agent.defineContexts();
        var ctx = ctxBuilder.addContext("default");

        // Step 1: Demographics
        var step1 = ctx.addStep("demographics");
        step1.setText("Collect the patient's basic information.");
        step1.setGatherInfo("patient_demographics", null,
                "Please collect the following patient information.");
        step1.addGatherQuestion("full_name", "What is your full name?");
        step1.addGatherQuestion("date_of_birth", "What is your date of birth?");
        step1.addGatherQuestion("phone_number", "What is your phone number?",
                "string", true, null, null);
        step1.addGatherQuestion("email", "What is your email address?");
        step1.setValidSteps(List.of("symptoms"));

        // Step 2: Symptoms
        var step2 = ctx.addStep("symptoms");
        step2.setText("Ask about the patient's current symptoms and reason for visit.");
        step2.setGatherInfo("patient_symptoms", null,
                "Now let's talk about why you're visiting today.");
        step2.addGatherQuestion("reason_for_visit",
                "What is the main reason for your visit today?");
        step2.addGatherQuestion("symptom_duration",
                "How long have you been experiencing these symptoms?");
        step2.addGatherQuestion("pain_level",
                "On a scale of 1 to 10, how would you rate your discomfort?");
        step2.setValidSteps(List.of("confirmation"));

        // Step 3: Confirmation (normal mode)
        var step3 = ctx.addStep("confirmation");
        step3.setText("Summarize all collected information and confirm with the patient.");
        step3.setStepCriteria("Patient has confirmed all information is correct");

        System.out.println("Starting patient intake agent on port 3000...");
        agent.run();
    }
}
