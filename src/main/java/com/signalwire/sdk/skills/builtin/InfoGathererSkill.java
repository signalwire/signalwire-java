package com.signalwire.sdk.skills.builtin;

import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

public class InfoGathererSkill implements SkillBase {

  private String prefix = "";
  private String completionMessage = "All questions have been answered. Thank you!";
  private List<Map<String, Object>> questions = new ArrayList<>();
  private String namespace = "info_gatherer";

  @Override
  public String getName() {
    return "info_gatherer";
  }

  @Override
  public String getDescription() {
    return "Gather answers to a configurable list of questions";
  }

  @Override
  public boolean supportsMultipleInstances() {
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean setup(Map<String, Object> params) {
    if (params.containsKey("prefix")) this.prefix = (String) params.get("prefix");
    if (params.containsKey("completion_message"))
      this.completionMessage = (String) params.get("completion_message");
    if (params.containsKey("questions")) {
      this.questions = (List<Map<String, Object>>) params.get("questions");
    }
    if (!prefix.isEmpty()) {
      this.namespace = "info_gatherer_" + prefix;
    }
    return !questions.isEmpty();
  }

  @Override
  public List<ToolDefinition> registerTools() {
    String startName = prefix.isEmpty() ? "start_questions" : prefix + "_start_questions";
    String submitName = prefix.isEmpty() ? "submit_answer" : prefix + "_submit_answer";

    ToolDefinition start =
        new ToolDefinition(
            startName,
            "Start the questionnaire and get the first question",
            Map.of("type", "object", "properties", Map.of()),
            (args, raw) -> {
              if (questions.isEmpty()) {
                return new FunctionResult("No questions configured");
              }
              Map<String, Object> firstQ = questions.get(0);
              return new FunctionResult("Please ask the user: " + firstQ.get("question_text"))
                  .updateGlobalData(
                      Map.of(
                          namespace,
                          Map.of(
                              "questions",
                              questions,
                              "question_index",
                              0,
                              "answers",
                              new ArrayList<>())));
            });

    Map<String, Object> answerParams = new LinkedHashMap<>();
    answerParams.put("type", "object");
    answerParams.put(
        "properties",
        Map.of(
            "answer", Map.of("type", "string", "description", "The user's answer"),
            "confirmed_by_user",
                Map.of(
                    "type", "boolean", "description", "Whether the user confirmed this answer")));
    // No `required` — Python's info_gatherer passes none (info_gatherer/
    // skill.py:170); submit_answer advances state regardless. Matches reference.

    ToolDefinition submit =
        new ToolDefinition(
            submitName,
            "Submit an answer to the current question",
            answerParams,
            (args, raw) -> {
              String answer = (String) args.get("answer");
              @SuppressWarnings("unchecked")
              Map<String, Object> gd = (Map<String, Object>) raw.get("global_data");
              @SuppressWarnings("unchecked")
              Map<String, Object> ns = gd != null ? (Map<String, Object>) gd.get(namespace) : null;

              if (ns == null) {
                return new FunctionResult("Session not started. Use " + startName + " first.");
              }

              int idx = ((Number) ns.getOrDefault("question_index", 0)).intValue();

              if (idx >= questions.size()) {
                return new FunctionResult(completionMessage);
              }

              Map<String, Object> currentQ = questions.get(idx);
              boolean needsConfirm = Boolean.TRUE.equals(currentQ.get("confirm"));
              boolean confirmed = Boolean.TRUE.equals(args.get("confirmed_by_user"));

              if (needsConfirm && !confirmed) {
                return new FunctionResult("Please confirm the answer with the user: " + answer);
              }

              int nextIdx = idx + 1;

              // Store answer and advance
              Map<String, Object> updateData = new LinkedHashMap<>();
              updateData.put(
                  namespace,
                  Map.of(
                      "question_index", nextIdx,
                      "last_answer_key", currentQ.get("key_name"),
                      "last_answer_value", answer));

              if (nextIdx >= questions.size()) {
                return new FunctionResult(completionMessage).updateGlobalData(updateData);
              }

              Map<String, Object> nextQ = questions.get(nextIdx);
              return new FunctionResult(
                      "Answer recorded. Next question: " + nextQ.get("question_text"))
                  .updateGlobalData(updateData);
            });

    return List.of(start, submit);
  }

  @Override
  public List<Map<String, Object>> getPromptSections() {
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("title", "Info Gatherer" + (prefix.isEmpty() ? "" : " (" + prefix + ")"));
    section.put("body", "You need to gather information by asking a series of questions.");
    section.put(
        "bullets",
        List.of(
            "Start by calling the start_questions function",
            "Ask each question one at a time",
            "Submit each answer using submit_answer",
            "If confirmation is required, confirm with the user before submitting"));
    return List.of(section);
  }

  @Override
  public Map<String, Object> getGlobalData() {
    return Map.of(
        namespace,
        Map.of("questions", questions, "question_index", 0, "answers", new ArrayList<>()));
  }

  /**
   * Instance key: {@code info_gatherer_<prefix>} when a {@code prefix} is set, otherwise {@code
   * info_gatherer}.
   */
  @Override
  public String getInstanceKey() {
    if (prefix != null && !prefix.isEmpty()) {
      return getName() + "_" + prefix;
    }
    return getName();
  }

  /**
   * Parameter schema: base schema plus {@code questions}, {@code prefix}, {@code
   * completion_message}.
   */
  @Override
  public Map<String, Object> getParameterSchema() {
    Map<String, Object> schema = SkillParams.base(true, getName());

    Map<String, Object> questionItems = new LinkedHashMap<>();
    questionItems.put("type", "object");
    questionItems.put(
        "properties",
        Map.of(
            "key_name", Map.of("type", "string"),
            "question_text", Map.of("type", "string"),
            "confirm", Map.of("type", "boolean"),
            "prompt_add", Map.of("type", "string")));

    Map<String, Object> questionsField = new LinkedHashMap<>();
    questionsField.put("type", "array");
    questionsField.put(
        "description",
        "List of question objects. Each must have 'key_name' (str) and 'question_text' (str)."
            + " Optional 'confirm' (bool) asks the agent to confirm the answer before proceeding.");
    questionsField.put("required", true);
    questionsField.put("items", questionItems);
    schema.put("questions", questionsField);

    Map<String, Object> prefixField = new LinkedHashMap<>();
    prefixField.put("type", "string");
    prefixField.put(
        "description",
        "Optional prefix for tool names and namespace. When set, tools are named"
            + " <prefix>_start_questions / <prefix>_submit_answer and state is stored under"
            + " 'skill:<prefix>' in global_data.");
    prefixField.put("required", false);
    schema.put("prefix", prefixField);

    Map<String, Object> completionField = new LinkedHashMap<>();
    completionField.put("type", "string");
    completionField.put("description", "Message returned after all questions are answered");
    completionField.put(
        "default",
        "Thank you! All questions have been answered. You can now summarize the information"
            + " collected or ask if there's anything else the user would like to discuss.");
    completionField.put("required", false);
    schema.put("completion_message", completionField);

    return schema;
  }
}
