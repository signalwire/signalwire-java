/**
 * DataSphere Multiple Instance Demo.
 *
 * Loads the datasphere skill multiple times with different knowledge bases
 * and custom tool names. Each instance searches a different document.
 */

import com.signalwire.sdk.agent.AgentBase;

import java.util.Map;

public class DatasphereMultiInstanceDemo {

    public static void main(String[] args) throws Exception {
        var agent = AgentBase.builder()
                .name("multi-datasphere")
                .route("/datasphere-multi")
                .port(3000)
                .build();

        agent.addLanguage("English", "en-US", "en-US-Standard-C");

        agent.promptAddSection("Role",
                "You are an assistant with access to multiple knowledge bases. " +
                "Use the appropriate search tool depending on the topic.");

        agent.addSkill("datetime", Map.of());
        agent.addSkill("math", Map.of());

        // Instance 1: Drinks knowledge
        agent.addSkill("datasphere", Map.of(
                "space_name", "your-space",
                "project_id", "your-project-id",
                "token", "your-token",
                "document_id", "drinks-doc-123",
                "tool_name", "search_drinks_knowledge",
                "count", 2,
                "distance", 5.0));

        // Instance 2: Food knowledge
        agent.addSkill("datasphere", Map.of(
                "space_name", "your-space",
                "project_id", "your-project-id",
                "token", "your-token",
                "document_id", "food-doc-456",
                "tool_name", "search_food_knowledge",
                "count", 3,
                "distance", 4.0));

        // Instance 3: General knowledge (default tool name)
        agent.addSkill("datasphere", Map.of(
                "space_name", "your-space",
                "project_id", "your-project-id",
                "token", "your-token",
                "document_id", "general-doc-789",
                "count", 1,
                "distance", 3.0));

        System.out.println("Loaded skills: " + agent.listSkills());
        System.out.println("Tools: search_drinks_knowledge, search_food_knowledge, search_knowledge");
        System.out.println("Note: Replace credentials with your actual DataSphere details.");
        System.out.println("Starting multi-datasphere agent on port 3000...");
        agent.run();
    }
}
