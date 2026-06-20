package com.signalwire.sdk.prefabs;

import com.signalwire.sdk.agent.AgentBase;
import java.util.*;

/**
 * Pre-built agent for department routing with call transfer. Routes callers to appropriate
 * departments via phone numbers or SWML URLs.
 */
public class ReceptionistAgent {

  private final AgentBase agent;

  public ReceptionistAgent(
      String name, String greeting, Map<String, Map<String, Object>> departments) {
    this(name, greeting, departments, "/", 3000);
  }

  public ReceptionistAgent(
      String name,
      String greeting,
      Map<String, Map<String, Object>> departments,
      String route,
      int port) {
    this.agent = AgentBase.builder().name(name).route(route).port(port).build();

    agent.promptAddSection("Role", "You are a professional receptionist. " + greeting);

    List<String> deptBullets = new ArrayList<>();
    for (Map.Entry<String, Map<String, Object>> entry : departments.entrySet()) {
      String deptName = entry.getKey();
      Map<String, Object> config = entry.getValue();
      String desc = (String) config.getOrDefault("description", deptName);
      deptBullets.add(deptName + " - " + desc);
    }
    agent.promptAddSection("Available Departments", "", deptBullets);

    agent.promptAddSection(
        "Instructions",
        "",
        List.of(
            "Greet the caller warmly",
            "Ask how you can help or which department they need",
            "Transfer to the appropriate department when requested",
            "If unsure, ask clarifying questions",
            "Always confirm before transferring"));

    // Setup transfer skill
    Map<String, Object> transferParams = new LinkedHashMap<>();
    Map<String, Map<String, Object>> transfers = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, Object>> entry : departments.entrySet()) {
      transfers.put(entry.getKey(), entry.getValue());
    }
    transferParams.put("transfers", transfers);
    transferParams.put("description", "Transfer caller to a department");
    agent.addSkill("swml_transfer", transferParams);
  }

  public AgentBase getAgent() {
    return agent;
  }

  public void serve() throws Exception {
    agent.serve();
  }

  public void run() throws Exception {
    agent.run();
  }

  /** Create a phone-based department config. */
  public static Map<String, Object> phoneDepartment(String description, String phoneNumber) {
    Map<String, Object> dept = new LinkedHashMap<>();
    dept.put("description", description);
    dept.put("address", phoneNumber);
    dept.put("message", "Transferring you to " + description + ". Please hold.");
    return dept;
  }

  /** Create a SWML-based department config. */
  public static Map<String, Object> swmlDepartment(String description, String swmlUrl) {
    Map<String, Object> dept = new LinkedHashMap<>();
    dept.put("description", description);
    dept.put("url", swmlUrl);
    dept.put("message", "Connecting you to " + description + ".");
    return dept;
  }
}
