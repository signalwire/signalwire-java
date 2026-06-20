package com.signalwire.sdk.prefabs;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

/**
 * Pre-built agent for conducting typed surveys. Supports rating, multiple_choice, yes_no, and
 * open_ended question types.
 */
public class SurveyAgent {

  private final AgentBase agent;
  private final List<Map<String, Object>> questions;
  private final String completionMessage;

  public SurveyAgent(String name, List<Map<String, Object>> questions) {
    this(name, questions, "Thank you for completing the survey!", "/", 3000);
  }

  public SurveyAgent(
      String name,
      List<Map<String, Object>> questions,
      String completionMessage,
      String route,
      int port) {
    this.questions = questions;
    this.completionMessage = completionMessage;

    this.agent = AgentBase.builder().name(name).route(route).port(port).build();

    agent.promptAddSection(
        "Role",
        "You are a survey conductor. "
            + "Your job is to ask survey questions and record responses accurately.");

    List<String> bullets = new ArrayList<>();
    bullets.add("Ask one question at a time in order");
    bullets.add("Validate answers match the expected type");
    bullets.add("For rating questions, ensure answers are within the specified range");
    bullets.add("For multiple_choice, only accept listed options");
    bullets.add("For yes_no, only accept yes or no");
    bullets.add("Be encouraging and thank users for their responses");
    agent.promptAddSection("Instructions", "", bullets);

    // Register survey tools
    agent.defineTool(
        new ToolDefinition(
            "start_survey",
            "Start the survey and get the first question",
            Map.of("type", "object", "properties", Map.of()),
            (args, raw) -> {
              if (this.questions.isEmpty()) {
                return new FunctionResult("No survey questions configured");
              }
              Map<String, Object> q = this.questions.get(0);
              return new FunctionResult(formatQuestion(q, 0))
                  .updateGlobalData(
                      Map.of(
                          "survey",
                          Map.of(
                              "question_index",
                              0,
                              "answers",
                              new ArrayList<>(),
                              "total_questions",
                              this.questions.size())));
            }));

    Map<String, Object> answerParams = new LinkedHashMap<>();
    answerParams.put("type", "object");
    answerParams.put(
        "properties",
        Map.of("answer", Map.of("type", "string", "description", "The survey response")));
    answerParams.put("required", List.of("answer"));

    agent.defineTool(
        new ToolDefinition(
            "submit_survey_answer",
            "Submit an answer to the current survey question",
            answerParams,
            (args, raw) -> {
              String answer = (String) args.get("answer");
              @SuppressWarnings("unchecked")
              Map<String, Object> gd = (Map<String, Object>) raw.get("global_data");
              @SuppressWarnings("unchecked")
              Map<String, Object> survey =
                  gd != null ? (Map<String, Object>) gd.get("survey") : null;

              int idx =
                  survey != null
                      ? ((Number) survey.getOrDefault("question_index", 0)).intValue()
                      : 0;

              if (idx >= this.questions.size()) {
                return new FunctionResult(this.completionMessage);
              }

              Map<String, Object> currentQ = this.questions.get(idx);
              String validation = validateAnswer(currentQ, answer);
              if (validation != null) {
                return new FunctionResult(validation);
              }

              int nextIdx = idx + 1;
              if (nextIdx >= this.questions.size()) {
                return new FunctionResult(this.completionMessage)
                    .updateGlobalData(Map.of("survey", Map.of("question_index", nextIdx)));
              }

              return new FunctionResult(
                      "Answer recorded. " + formatQuestion(this.questions.get(nextIdx), nextIdx))
                  .updateGlobalData(Map.of("survey", Map.of("question_index", nextIdx)));
            }));

    agent.updateGlobalData(
        Map.of("survey", Map.of("total_questions", questions.size(), "question_index", 0)));
  }

  private String formatQuestion(Map<String, Object> q, int index) {
    String text = (String) q.get("question");
    String type = (String) q.getOrDefault("type", "open_ended");
    StringBuilder sb = new StringBuilder();
    sb.append("Question ").append(index + 1).append(": ").append(text);

    if ("rating".equals(type)) {
      int min = ((Number) q.getOrDefault("min", 1)).intValue();
      int max = ((Number) q.getOrDefault("max", 5)).intValue();
      sb.append(" (Rate from ").append(min).append(" to ").append(max).append(")");
    } else if ("multiple_choice".equals(type)) {
      @SuppressWarnings("unchecked")
      List<String> options = (List<String>) q.get("options");
      if (options != null) {
        sb.append(" Options: ").append(String.join(", ", options));
      }
    } else if ("yes_no".equals(type)) {
      sb.append(" (Yes or No)");
    }

    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  private String validateAnswer(Map<String, Object> q, String answer) {
    String type = (String) q.getOrDefault("type", "open_ended");
    switch (type) {
      case "rating" -> {
        try {
          int val = Integer.parseInt(answer.trim());
          int min = ((Number) q.getOrDefault("min", 1)).intValue();
          int max = ((Number) q.getOrDefault("max", 5)).intValue();
          if (val < min || val > max) {
            return "Please provide a rating between " + min + " and " + max;
          }
        } catch (NumberFormatException e) {
          return "Please provide a numeric rating";
        }
      }
      case "multiple_choice" -> {
        List<String> options = (List<String>) q.get("options");
        if (options != null && !options.contains(answer.trim())) {
          return "Please choose from: " + String.join(", ", options);
        }
      }
      case "yes_no" -> {
        String lower = answer.trim().toLowerCase();
        if (!lower.equals("yes") && !lower.equals("no")) {
          return "Please answer yes or no";
        }
      }
    }
    return null;
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

  public static Map<String, Object> ratingQuestion(String question, int min, int max) {
    return Map.of("question", question, "type", "rating", "min", min, "max", max);
  }

  public static Map<String, Object> multipleChoiceQuestion(String question, List<String> options) {
    return Map.of("question", question, "type", "multiple_choice", "options", options);
  }

  public static Map<String, Object> yesNoQuestion(String question) {
    return Map.of("question", question, "type", "yes_no");
  }

  public static Map<String, Object> openEndedQuestion(String question) {
    return Map.of("question", question, "type", "open_ended");
  }
}
