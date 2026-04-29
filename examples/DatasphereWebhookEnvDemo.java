/**
 * DataSphere Webhook Environment Demo.
 *
 * Traditional webhook-based DataSphere skill configured from environment
 * variables. Compare with DatasphereServerlessEnv for the serverless approach.
 *
 * Required: SIGNALWIRE_SPACE, SIGNALWIRE_PROJECT_ID, SIGNALWIRE_TOKEN,
 *           DATASPHERE_DOCUMENT_ID
 */

import com.signalwire.sdk.agent.AgentBase;

import java.util.Map;

public class DatasphereWebhookEnvDemo {

    static String requireEnv(String name) {
        String val = System.getenv(name);
        if (val == null || val.isEmpty()) {
            System.err.println("Error: Required environment variable " + name + " is not set.");
            System.exit(1);
        }
        return val;
    }

    public static void main(String[] args) throws Exception {
        String documentId = requireEnv("DATASPHERE_DOCUMENT_ID");
        int count = Integer.parseInt(System.getenv().getOrDefault("DATASPHERE_COUNT", "3"));
        double distance = Double.parseDouble(System.getenv().getOrDefault("DATASPHERE_DISTANCE", "4.0"));

        var agent = AgentBase.builder()
                .name("datasphere-webhook-env")
                .route("/datasphere-webhook")
                .port(3000)
                .build();

        agent.addLanguage("English", "en-US", "en-US-Standard-C");

        agent.promptAddSection("Role",
                "You are a knowledge assistant using webhook-based DataSphere for retrieval.");

        agent.addSkill("datetime", Map.of());
        agent.addSkill("math", Map.of());

        agent.addSkill("datasphere", Map.of(
                "document_id", documentId,
                "count", count,
                "distance", distance,
                "mode", "webhook"));

        System.out.println("DataSphere Webhook Environment Demo");
        System.out.printf("  Document: %s%n", documentId);
        System.out.println("  Execution: Webhook-based (traditional)");
        System.out.println();
        System.out.println("  Webhook:    Full control, custom error handling");
        System.out.println("  Serverless: No webhooks, lower latency, executes on SignalWire");
        System.out.println("Starting agent on port 3000...");
        agent.run();
    }
}
