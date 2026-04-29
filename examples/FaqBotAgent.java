/**
 * FAQ Bot Example -- using the FAQBotAgent prefab.
 *
 * Creates a domain-specific FAQ agent with keyword-based lookup.
 */

import com.signalwire.sdk.prefabs.FAQBotAgent;

import java.util.List;

public class FaqBotAgent {

    public static void main(String[] args) throws Exception {
        var agent = new FAQBotAgent(
                "signalwire-faq",
                List.of(
                        FAQBotAgent.faq(
                                "What is SignalWire?",
                                "SignalWire is a communications platform that provides APIs for voice, video, and messaging.",
                                List.of("signalwire", "platform", "api")),
                        FAQBotAgent.faq(
                                "How do I create an AI Agent?",
                                "Use the SignalWire AI Agent SDK, which provides a framework for building and deploying conversational AI agents.",
                                List.of("agent", "sdk", "create", "build")),
                        FAQBotAgent.faq(
                                "What is SWML?",
                                "SWML (SignalWire Markup Language) is a markup language for defining communications workflows, including AI interactions.",
                                List.of("swml", "markup", "workflow")),
                        FAQBotAgent.faq(
                                "What are your hours?",
                                "We are open Monday through Friday, 9 AM to 5 PM Eastern Time.",
                                List.of("hours", "open", "schedule")),
                        FAQBotAgent.faq(
                                "Do you offer refunds?",
                                "Yes, we offer refunds within 30 days of purchase if you're not satisfied.",
                                List.of("refund", "return", "money back"))
                )
        );

        System.out.println("Starting FAQ Bot on port 3000...");
        agent.run();
    }
}
