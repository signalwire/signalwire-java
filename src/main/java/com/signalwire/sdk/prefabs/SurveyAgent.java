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

  /** Default brand name when the caller supplies none. */
  private static final String DEFAULT_BRAND_NAME = "Our Company";

  /** Default number of re-ask attempts for an invalid answer. */
  private static final int DEFAULT_MAX_RETRIES = 2;

  private final AgentBase agent;
  private final List<Map<String, Object>> questions;
  private final String completionMessage;
  private final String surveyName;
  private final String brandName;
  private final int maxRetries;
  private final String introduction;
  private java.util.function.BiConsumer<Map<String, Object>, Map<String, Object>> summaryHandler;

  public SurveyAgent(String name, List<Map<String, Object>> questions) {
    this(name, questions, "Thank you for completing the survey!", "/", 3000);
  }

  public SurveyAgent(
      String name,
      List<Map<String, Object>> questions,
      String completionMessage,
      String route,
      int port) {
    this(name, questions, completionMessage, route, port, name, null, DEFAULT_MAX_RETRIES, null);
  }

  /**
   * Full construction contract: every configurable aspect of the survey in one call.
   *
   * @param name agent name
   * @param questions the survey questions (the {@code questions} param)
   * @param completionMessage closing message, spoken after the last question (the {@code
   *     conclusion} param)
   * @param route HTTP route for this agent
   * @param port HTTP port for this agent
   * @param surveyName human-readable survey title (the {@code survey_name} param)
   * @param brandName brand conducting the survey (the {@code brand_name} param); defaults to {@code
   *     "Our Company"} when {@code null}
   * @param maxRetries re-ask attempts for an invalid answer (the {@code max_retries} param)
   * @param introduction opening message (the {@code introduction} param); defaults to a stock
   *     welcome text built from {@code surveyName} when {@code null}
   */
  public SurveyAgent(
      String name,
      List<Map<String, Object>> questions,
      String completionMessage,
      String route,
      int port,
      String surveyName,
      String brandName,
      int maxRetries,
      String introduction) {
    this.questions = questions;
    this.completionMessage = completionMessage;
    this.surveyName = surveyName;
    this.brandName = brandName != null ? brandName : DEFAULT_BRAND_NAME;
    this.maxRetries = maxRetries;
    this.introduction =
        introduction != null
            ? introduction
            : "Welcome to our " + surveyName + ". We appreciate your participation.";

    this.agent = AgentBase.builder().name(name).route(route).port(port).build();

    agent.promptAddSection(
        "Role",
        "You are a survey conductor for "
            + this.brandName
            + ". "
            + "Your job is to ask survey questions and record responses accurately.");

    agent.promptAddSection("Introduction", "Begin with this introduction: " + this.introduction);

    List<String> bullets = new ArrayList<>();
    bullets.add("Ask one question at a time in order");
    bullets.add("Validate answers match the expected type");
    bullets.add("For rating questions, ensure answers are within the specified range");
    bullets.add("For multiple_choice, only accept listed options");
    bullets.add("For yes_no, only accept yes or no");
    bullets.add("Be encouraging and thank users for their responses");
    bullets.add(
        "If an answer is invalid, re-ask up to "
            + this.maxRetries
            + " time(s) before moving on to the next question");
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

    // validate_response tool -> validateResponse
    Map<String, Object> validateParams = new LinkedHashMap<>();
    validateParams.put("type", "object");
    validateParams.put(
        "properties",
        Map.of(
            "question_id",
            Map.of("type", "string", "description", "The ID of the question"),
            "response",
            Map.of("type", "string", "description", "The user's response to validate")));
    validateParams.put("required", List.of("question_id", "response"));
    agent.defineTool(
        new ToolDefinition(
            "validate_response",
            "Validate if a response meets the requirements for a specific question",
            validateParams,
            this::validateResponse));

    // log_response tool -> logResponse
    Map<String, Object> logParams = new LinkedHashMap<>();
    logParams.put("type", "object");
    logParams.put(
        "properties",
        Map.of(
            "question_id",
            Map.of("type", "string", "description", "The ID of the question"),
            "response",
            Map.of("type", "string", "description", "The user's validated response")));
    logParams.put("required", List.of("question_id", "response"));
    agent.defineTool(
        new ToolDefinition(
            "log_response",
            "Log a validated response to a survey question",
            logParams,
            this::logResponse));

    agent.updateGlobalData(
        Map.of("survey", Map.of("total_questions", questions.size(), "question_index", 0)));
  }

  private Map<String, Object> findQuestionById(String questionId) {
    for (Map<String, Object> q : questions) {
      if (questionId.equals(q.get("id"))) {
        return q;
      }
    }
    return null;
  }

  /**
   * SWAIG tool handler: validate a response against a question's constraints. Looks the question up
   * by {@code id} and checks rating range (1..scale), multiple_choice membership, yes/no answers,
   * and required open_ended responses. Returns a validity message.
   */
  @SuppressWarnings("unchecked")
  public FunctionResult validateResponse(Map<String, Object> args, Map<String, Object> rawData) {
    String questionId = (String) args.getOrDefault("question_id", "");
    String response = (String) args.getOrDefault("response", "");

    Map<String, Object> question = findQuestionById(questionId);
    if (question == null) {
      return new FunctionResult("Error: Question with ID '" + questionId + "' not found.");
    }

    String type = (String) question.getOrDefault("type", "open_ended");
    String message = "Response to '" + questionId + "' is valid.";

    switch (type) {
      case "rating" -> {
        int scale = ((Number) question.getOrDefault("scale", 5)).intValue();
        try {
          int rating = Integer.parseInt(response.trim());
          if (rating < 1 || rating > scale) {
            message = "Invalid rating. Please provide a number between 1 and " + scale + ".";
          }
        } catch (NumberFormatException e) {
          message = "Invalid rating. Please provide a number between 1 and " + scale + ".";
        }
      }
      case "multiple_choice" -> {
        List<String> options = (List<String>) question.getOrDefault("options", List.of());
        boolean matched = false;
        for (String option : options) {
          if (response
              .toLowerCase(java.util.Locale.ROOT)
              .strip()
              .equals(option.toLowerCase(java.util.Locale.ROOT))) {
            matched = true;
            break;
          }
        }
        if (!matched) {
          message = "Invalid choice. Please select one of: " + String.join(", ", options) + ".";
        }
      }
      case "yes_no" -> {
        String lower = response.toLowerCase(java.util.Locale.ROOT).strip();
        if (!List.of("yes", "no", "y", "n").contains(lower)) {
          message = "Please answer with 'yes' or 'no'.";
        }
      }
      case "open_ended" -> {
        boolean required = !Boolean.FALSE.equals(question.getOrDefault("required", true));
        if (response.strip().isEmpty() && required) {
          message = "A response is required for this question.";
        }
      }
      default -> {
        // Unknown type: treat as valid.
      }
    }

    return new FunctionResult(message);
  }

  /**
   * SWAIG tool handler: acknowledge a validated response. Looks the question up by {@code id} for a
   * friendlier message and confirms the response was recorded (a real deployment would persist it).
   */
  public FunctionResult logResponse(Map<String, Object> args, Map<String, Object> rawData) {
    String questionId = (String) args.getOrDefault("question_id", "");
    Map<String, Object> question = findQuestionById(questionId);
    String questionText = question != null ? (String) question.getOrDefault("text", "") : "";
    return new FunctionResult("Response to '" + questionText + "' has been recorded.");
  }

  /**
   * Register a post-prompt summary callback: invoked with the parsed survey summary and the raw
   * post-prompt payload once the survey completes. Wires through to {@link AgentBase#onSummary}.
   *
   * @param handler callback receiving (summary, rawData); {@code null} clears any handler
   * @return this prefab for chaining
   */
  public SurveyAgent onSummary(
      java.util.function.BiConsumer<Map<String, Object>, Map<String, Object>> handler) {
    this.summaryHandler = handler;
    agent.onSummary(handler);
    return this;
  }

  /** The registered summary callback, or {@code null} if none set. */
  public java.util.function.BiConsumer<Map<String, Object>, Map<String, Object>>
      getSummaryHandler() {
    return summaryHandler;
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
        String lower = answer.trim().toLowerCase(java.util.Locale.ROOT);
        if (!"yes".equals(lower) && !"no".equals(lower)) {
          return "Please answer yes or no";
        }
      }
      default -> {
        // open_ended and any unrecognized type need no validation.
      }
    }
    return null;
  }

  /**
   * The underlying agent this prefab configured. Use it to add tools, prompt sections, or skills
   * beyond what the prefab sets up.
   *
   * @return the wrapped agent.
   */
  public AgentBase getAgent() {
    return agent;
  }

  // Read side of the construction params (the reference's public self.survey_name /
  // self.questions / self.brand_name / self.max_retries / self.introduction /
  // self.conclusion, survey.py:91-103).

  /** The survey questions (the {@code questions} construction param). */
  public List<Map<String, Object>> getQuestions() {
    return questions;
  }

  /** The survey title (the {@code survey_name} construction param). */
  public String getSurveyName() {
    return surveyName;
  }

  /** The brand conducting the survey (the {@code brand_name} construction param). */
  public String getBrandName() {
    return brandName;
  }

  /** Re-ask attempts for an invalid answer (the {@code max_retries} construction param). */
  public int getMaxRetries() {
    return maxRetries;
  }

  /** The opening message (the {@code introduction} construction param). */
  public String getIntroduction() {
    return introduction;
  }

  /**
   * The closing message (the {@code conclusion} construction param, supplied to the constructor as
   * {@code completionMessage}).
   */
  public String getConclusion() {
    return completionMessage;
  }

  /**
   * Start the agent's HTTP server and serve until stopped.
   *
   * @throws Exception if the server cannot be started.
   */
  public void serve() throws Exception {
    agent.serve();
  }

  /**
   * Start the agent's HTTP server. Equivalent to {@link #serve()}.
   *
   * @throws Exception if the server cannot be started.
   */
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

  /**
   * Build an id-keyed survey question ({@code id}/{@code text}/{@code type}/{@code scale}/{@code
   * options}) usable with {@link #validateResponse} and {@link #logResponse}, which look questions
   * up by {@code id}.
   */
  public static Map<String, Object> idQuestion(
      String id, String text, String type, Integer scale, List<String> options) {
    Map<String, Object> q = new LinkedHashMap<>();
    q.put("id", id);
    q.put("text", text);
    q.put("type", type);
    if (scale != null) q.put("scale", scale);
    if (options != null) q.put("options", options);
    return q;
  }
}
