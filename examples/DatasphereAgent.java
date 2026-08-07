/*
 * DataSphere Agent with Multiple Instances.
 *
 * <p>Demonstrates loading the datasphere skill multiple times with different configurations and
 * tool names for separate knowledge bases.
 *
 * <p>Replace the example credentials with your actual DataSphere details.
 */
import com.signalwire.sdk.agent.AgentBase;
import java.util.Map;

public class DatasphereAgent {

  public static void main(String[] args) throws Exception {
    var agent =
        AgentBase.builder()
            .name("datasphere-assistant")
            .route("/datasphere-demo")
            .port(3000)
            .build();

    agent.addLanguage("English", "en-US", "en-US-Standard-C");

    agent.promptAddSection(
        "Role",
        "You are an assistant with access to multiple knowledge bases. "
            + "Use the appropriate search tool depending on the topic.");

    // Add utility skills
    agent.addSkill("datetime", Map.of());
    agent.addSkill("math", Map.of());

    // Instance 1: Drinks knowledge base
    agent.addSkill(
        "datasphere",
        Map.of(
            "space_name", "your-space",
            "project_id", "your-project-id",
            "token", "your-token",
            "document_id", "drinks-doc-123",
            "tool_name", "search_drinks_knowledge",
            "count", 2,
            "distance", 5.0));

    // Instance 2: Food knowledge base
    agent.addSkill(
        "datasphere",
        Map.of(
            "space_name", "your-space",
            "project_id", "your-project-id",
            "token", "your-token",
            "document_id", "food-doc-456",
            "tool_name", "search_food_knowledge",
            "count", 3,
            "distance", 4.0));

    // Instance 3: General knowledge (default tool name)
    agent.addSkill(
        "datasphere",
        Map.of(
            "space_name", "your-space",
            "project_id", "your-project-id",
            "token", "your-token",
            "document_id", "general-doc-789",
            "count", 1,
            "distance", 3.0));

    System.out.println("Loaded skills: " + agent.listSkills());
    System.out.println("Starting DataSphere agent on port 3000...");
    System.out.println("Note: Replace credentials with your actual DataSphere details.");
    agent.run();
  }
}
