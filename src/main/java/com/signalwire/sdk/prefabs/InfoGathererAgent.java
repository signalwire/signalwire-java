package com.signalwire.sdk.prefabs;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

/**
 * Pre-built agent for sequential question collection with key/value answers. Supports both static
 * mode (questions supplied at construction) and dynamic mode (questions produced per-request by a
 * {@link QuestionCallback} via {@link #onSwmlRequest}). Wraps the info_gatherer skill with
 * agent-level configuration and drives the question flow through the {@code start_questions} and
 * {@code submit_answer} SWAIG tools.
 */
public class InfoGathererAgent {

  /**
   * Callback that produces the question list per-request in dynamic mode. Mirrors the Python
   * InfoGathererAgent question callback signature {@code (query_params, body_params, headers) ->
   * questions}.
   */
  @FunctionalInterface
  public interface QuestionCallback {
    List<Map<String, Object>> apply(
        Map<String, Object> queryParams,
        Map<String, Object> bodyParams,
        Map<String, Object> headers);
  }

  private final AgentBase agent;
  private final List<Map<String, Object>> staticQuestions;
  private QuestionCallback questionCallback;

  public InfoGathererAgent(String name, List<Map<String, Object>> questions) {
    this(name, questions, "/", 3000);
  }

  public InfoGathererAgent(
      String name, List<Map<String, Object>> questions, String route, int port) {
    this.staticQuestions = questions;
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

    // In dynamic mode (questions == null) feed the skill an empty list -- the real
    // questions are supplied per-request by onSwmlRequest / the question callback. The
    // info_gatherer skill's setup() dereferences the list, so a null would NPE.
    Map<String, Object> skillParams = new LinkedHashMap<>();
    skillParams.put("questions", questions != null ? questions : new ArrayList<>());
    agent.addSkill("info_gatherer", skillParams);

    if (questions != null) {
      Map<String, Object> gd = new LinkedHashMap<>();
      gd.put("questions", questions);
      gd.put("question_index", 0);
      gd.put("answers", new ArrayList<>());
      agent.updateGlobalData(gd);
    }

    // start_questions tool -> startQuestions
    agent.defineTool(
        new ToolDefinition(
            "start_questions",
            "Start the question sequence with the first question",
            Map.of("type", "object", "properties", Map.of()),
            this::startQuestions));

    // submit_answer tool -> submitAnswer
    Map<String, Object> submitParams = new LinkedHashMap<>();
    submitParams.put("type", "object");
    submitParams.put(
        "properties",
        Map.of(
            "answer",
            Map.of("type", "string", "description", "The user's answer to the current question")));
    submitParams.put("required", List.of("answer"));
    agent.defineTool(
        new ToolDefinition(
            "submit_answer",
            "Submit an answer to the current question and move to the next one",
            submitParams,
            this::submitAnswer));
  }

  /**
   * Set a callback that produces the question list per-request (dynamic mode). Ported from Python
   * InfoGathererAgent.set_question_callback: when set and no static questions were supplied, {@link
   * #onSwmlRequest} invokes it and seeds global_data with the returned questions.
   *
   * @param callback the per-request question producer
   * @return this prefab for chaining
   */
  public InfoGathererAgent setQuestionCallback(QuestionCallback callback) {
    this.questionCallback = callback;
    return this;
  }

  /**
   * Dynamic-configuration hook invoked when SWML is requested. Ported from Python
   * InfoGathererAgent.on_swml_request: in static mode returns {@code null} (no override); in
   * dynamic mode it calls the registered {@link QuestionCallback} (or a name/message fallback if
   * none is set) and returns a {@code {"global_data": {questions, question_index, answers}}}
   * override map.
   *
   * @param requestData parsed request body (body params), or {@code null}
   * @param queryParams request query parameters, or {@code null}
   * @param headers request headers, or {@code null}
   * @return a global-data override map, or {@code null} to leave configuration unchanged
   */
  public Map<String, Object> onSwmlRequest(
      Map<String, Object> requestData,
      Map<String, Object> queryParams,
      Map<String, Object> headers) {
    // Static mode: no dynamic override.
    if (staticQuestions != null) {
      return null;
    }

    if (questionCallback == null) {
      return globalDataOverride(fallbackQuestions());
    }

    Map<String, Object> qp = queryParams != null ? queryParams : new LinkedHashMap<>();
    Map<String, Object> bp = requestData != null ? requestData : new LinkedHashMap<>();
    Map<String, Object> hd = headers != null ? headers : new LinkedHashMap<>();

    try {
      List<Map<String, Object>> questions = questionCallback.apply(qp, bp, hd);
      validateQuestions(questions);
      return globalDataOverride(questions);
    } catch (RuntimeException e) {
      return globalDataOverride(fallbackQuestions());
    }
  }

  private static void validateQuestions(List<Map<String, Object>> questions) {
    if (questions == null || questions.isEmpty()) {
      throw new IllegalArgumentException("At least one question is required");
    }
    for (int i = 0; i < questions.size(); i++) {
      Map<String, Object> q = questions.get(i);
      if (!q.containsKey("key_name")) {
        throw new IllegalArgumentException("Question " + (i + 1) + " is missing 'key_name' field");
      }
      if (!q.containsKey("question_text")) {
        throw new IllegalArgumentException(
            "Question " + (i + 1) + " is missing 'question_text' field");
      }
    }
  }

  private static List<Map<String, Object>> fallbackQuestions() {
    List<Map<String, Object>> fallback = new ArrayList<>();
    fallback.add(question("name", "What is your name?"));
    fallback.add(question("message", "How can I help you today?"));
    return fallback;
  }

  private static Map<String, Object> globalDataOverride(List<Map<String, Object>> questions) {
    Map<String, Object> gd = new LinkedHashMap<>();
    gd.put("questions", questions);
    gd.put("question_index", 0);
    gd.put("answers", new ArrayList<>());
    return Map.of("global_data", gd);
  }

  private static String generateQuestionInstruction(
      String questionText, boolean needsConfirmation, boolean isFirstQuestion) {
    StringBuilder sb = new StringBuilder();
    if (isFirstQuestion) {
      sb.append("Ask the user to answer the following question: ")
          .append(questionText)
          .append("\n\n");
    } else {
      sb.append("Previous Answer recorded. Now ask the user to answer the following question: ")
          .append(questionText)
          .append("\n\n");
    }
    sb.append(
        "Make sure the answer fits the scope and context of the question before submitting it. ");
    if (needsConfirmation) {
      sb.append(
          "Insist that the user confirms the answer as many times as needed until they say it is correct.");
    } else {
      sb.append("You don't need the user to confirm the answer to this question.");
    }
    return sb.toString();
  }

  /**
   * SWAIG tool handler: return the first question. Ported from Python
   * InfoGathererAgent.start_questions -- reads {@code questions} / {@code question_index} from
   * global_data and returns the instruction to ask the current question.
   */
  @SuppressWarnings("unchecked")
  public FunctionResult startQuestions(Map<String, Object> args, Map<String, Object> rawData) {
    Map<String, Object> globalData =
        (Map<String, Object>) rawData.getOrDefault("global_data", new LinkedHashMap<>());
    List<Map<String, Object>> questions =
        (List<Map<String, Object>>) globalData.getOrDefault("questions", new ArrayList<>());
    int questionIndex = ((Number) globalData.getOrDefault("question_index", 0)).intValue();

    if (questions.isEmpty() || questionIndex >= questions.size()) {
      return new FunctionResult("I don't have any questions to ask.");
    }

    Map<String, Object> currentQuestion = questions.get(questionIndex);
    String questionText = (String) currentQuestion.getOrDefault("question_text", "");
    boolean needsConfirmation = Boolean.TRUE.equals(currentQuestion.get("confirm"));

    String instruction = generateQuestionInstruction(questionText, needsConfirmation, true);
    FunctionResult result = new FunctionResult(instruction);
    result.replaceInHistory("Welcome! Let me ask you a few questions.");
    return result;
  }

  /**
   * SWAIG tool handler: record the current answer and advance. Ported from Python
   * InfoGathererAgent.submit_answer -- stores {@code {key_name, answer}} into the answers list,
   * increments question_index, and returns either the next question instruction or a completion
   * message, updating global_data accordingly.
   */
  @SuppressWarnings("unchecked")
  public FunctionResult submitAnswer(Map<String, Object> args, Map<String, Object> rawData) {
    String answer = (String) args.getOrDefault("answer", "");

    Map<String, Object> globalData =
        (Map<String, Object>) rawData.getOrDefault("global_data", new LinkedHashMap<>());
    List<Map<String, Object>> questions =
        (List<Map<String, Object>>) globalData.getOrDefault("questions", new ArrayList<>());
    int questionIndex = ((Number) globalData.getOrDefault("question_index", 0)).intValue();
    List<Map<String, Object>> answers =
        (List<Map<String, Object>>) globalData.getOrDefault("answers", new ArrayList<>());

    if (questionIndex >= questions.size()) {
      return new FunctionResult("All questions have already been answered.");
    }

    Map<String, Object> currentQuestion = questions.get(questionIndex);
    String keyName = (String) currentQuestion.getOrDefault("key_name", "");

    List<Map<String, Object>> newAnswers = new ArrayList<>(answers);
    Map<String, Object> newAnswer = new LinkedHashMap<>();
    newAnswer.put("key_name", keyName);
    newAnswer.put("answer", answer);
    newAnswers.add(newAnswer);

    int newQuestionIndex = questionIndex + 1;

    if (newQuestionIndex < questions.size()) {
      Map<String, Object> nextQuestion = questions.get(newQuestionIndex);
      String nextQuestionText = (String) nextQuestion.getOrDefault("question_text", "");
      boolean needsConfirmation = Boolean.TRUE.equals(nextQuestion.get("confirm"));
      String instruction = generateQuestionInstruction(nextQuestionText, needsConfirmation, false);

      FunctionResult result = new FunctionResult(instruction);
      result.replaceInHistory(true);
      Map<String, Object> update = new LinkedHashMap<>();
      update.put("answers", newAnswers);
      update.put("question_index", newQuestionIndex);
      result.updateGlobalData(update);
      return result;
    }

    FunctionResult result =
        new FunctionResult(
            "Thank you! All questions have been answered. You can now summarize the information"
                + " collected or ask if there's anything else the user would like to discuss.");
    result.replaceInHistory(true);
    Map<String, Object> update = new LinkedHashMap<>();
    update.put("answers", newAnswers);
    update.put("question_index", newQuestionIndex);
    result.updateGlobalData(update);
    return result;
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
