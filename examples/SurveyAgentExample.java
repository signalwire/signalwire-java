/**
 * Pre-built survey agent with typed questions.
 *
 * Uses the SurveyAgent prefab which supports rating, multiple_choice,
 * yes_no, and open_ended question types with validation.
 */

import com.signalwire.sdk.prefabs.SurveyAgent;

import java.util.List;

public class SurveyAgentExample {

    public static void main(String[] args) throws Exception {
        var agent = new SurveyAgent(
                "customer-satisfaction",
                List.of(
                        SurveyAgent.ratingQuestion(
                                "On a scale of 1 to 10, how satisfied are you with our service?",
                                1, 10),
                        SurveyAgent.multipleChoiceQuestion(
                                "Which department did you interact with?",
                                List.of("Sales", "Support", "Billing", "Other")),
                        SurveyAgent.yesNoQuestion(
                                "Would you recommend our service to others?"),
                        SurveyAgent.openEndedQuestion(
                                "Is there anything else you would like to share?"),
                        SurveyAgent.ratingQuestion(
                                "How likely are you to use our service again? (1-5)",
                                1, 5)
                ),
                "Thank you for completing our survey! Your feedback is valuable.",
                "/",
                3000
        );

        System.out.println("Starting customer satisfaction survey on port 3000...");
        agent.run();
    }
}
