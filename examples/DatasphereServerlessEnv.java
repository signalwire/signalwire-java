/**
 * DataSphere Serverless Environment Demo.
 *
 * Loads the DataSphere serverless skill with configuration from environment
 * variables for production deployment.
 *
 * Required: SIGNALWIRE_SPACE, SIGNALWIRE_PROJECT_ID, SIGNALWIRE_TOKEN,
 *           DATASPHERE_DOCUMENT_ID
 * Optional: DATASPHERE_COUNT, DATASPHERE_DISTANCE, DATASPHERE_TAGS
 */

import com.signalwire.sdk.agent.AgentBase;

import java.util.HashMap;
import java.util.Map;

public class DatasphereServerlessEnv {

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
                .name("datasphere-serverless-env")
                .route("/datasphere-env")
                .port(3000)
                .build();

        agent.addLanguage("English", "en-US", "en-US-Standard-C");

        agent.promptAddSection("Role",
                "You are a knowledge assistant with access to a document library. " +
                "Search the knowledge base to answer user questions.");

        agent.addSkill("datetime", Map.of());
        agent.addSkill("math", Map.of());

        Map<String, Object> config = new HashMap<>();
        config.put("document_id", documentId);
        config.put("count", count);
        config.put("distance", distance);

        String tags = System.getenv("DATASPHERE_TAGS");
        if (tags != null && !tags.isEmpty()) {
            config.put("tags", java.util.Arrays.asList(tags.split(",")));
        }

        agent.addSkill("datasphere", config);

        System.out.println("DataSphere Serverless Environment Demo");
        System.out.printf("  Document: %s%n", documentId);
        System.out.printf("  Count: %d, Distance: %.1f%n", count, distance);
        System.out.println("Starting agent on port 3000...");
        agent.run();
    }
}
