package com.signalwire.sdk.prefabs;

import com.signalwire.sdk.agent.AgentBase;
import java.util.*;

/**
 * Pre-built agent for sequential question collection with key/value answers. Wraps the
 * info_gatherer skill with agent-level configuration.
 */
public class InfoGathererAgent {

  private final AgentBase agent;

  public InfoGathererAgent(String name, List<Map<String, Object>> questions) {
    this(name, questions, "/", 3000);
  }

  public InfoGathererAgent(
      String name, List<Map<String, Object>> questions, String route, int port) {
    this.agent = AgentBase.builder().name(name).route(route).port(port).build();

    agent.promptAddSection(
        "Role",
        "You are an information gathering assistant. "
            + "Your job is to collect specific information from the user by asking a series of questions.");

    agent.promptAddSection(
        "Instructions",
        "",
        List.of(
            "Ask one question at a time",
            "Wait for the user's complete answer before moving to the next question",
            "If the answer is unclear, ask for clarification",
            "Confirm important answers with the user before proceeding",
            "Be polite and professional throughout the conversation"));

    Map<String, Object> skillParams = new LinkedHashMap<>();
    skillParams.put("questions", questions);
    agent.addSkill("info_gatherer", skillParams);
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

  /** Convenience builder for questions. */
  public static Map<String, Object> question(String keyName, String questionText) {
    return question(keyName, questionText, false, null);
  }

  public static Map<String, Object> question(
      String keyName, String questionText, boolean confirm, String promptAdd) {
    Map<String, Object> q = new LinkedHashMap<>();
    q.put("key_name", keyName);
    q.put("question_text", questionText);
    if (confirm) q.put("confirm", true);
    if (promptAdd != null) q.put("prompt_add", promptAdd);
    return q;
  }
}
