package com.signalwire.sdk.prefabs;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

/** Pre-built agent for keyword-based FAQ matching with optional related suggestions. */
public class FAQBotAgent {

  private final AgentBase agent;

  public FAQBotAgent(String name, List<Map<String, Object>> faqs) {
    this(name, faqs, "/", 3000);
  }

  public FAQBotAgent(String name, List<Map<String, Object>> faqs, String route, int port) {
    this.agent = AgentBase.builder().name(name).route(route).port(port).build();

    agent.promptAddSection(
        "Role",
        "You are a helpful FAQ assistant. Answer questions using the knowledge base of frequently asked questions.");

    // Build FAQ content for the prompt
    List<String> faqBullets = new ArrayList<>();
    for (Map<String, Object> faq : faqs) {
      String question = (String) faq.get("question");
      faqBullets.add("Q: " + question);
    }
    agent.promptAddSection("Available Topics", "", faqBullets);

    agent.promptAddSection(
        "Instructions",
        "",
        List.of(
            "Use the lookup_faq tool to find answers to user questions",
            "If no exact match is found, suggest related topics",
            "Be concise but thorough in your answers",
            "If the question is not in the FAQ, let the user know"));

    // Register FAQ lookup tool
    Map<String, Object> toolParams = new LinkedHashMap<>();
    toolParams.put("type", "object");
    toolParams.put(
        "properties",
        Map.of(
            "query",
            Map.of("type", "string", "description", "The user's question or keywords to search")));
    toolParams.put("required", List.of("query"));

    agent.defineTool(
        new ToolDefinition(
            "lookup_faq",
            "Look up an answer in the FAQ knowledge base",
            toolParams,
            (args, raw) -> {
              String query = ((String) args.get("query")).toLowerCase();

              // Simple keyword matching
              Map<String, Object> bestMatch = null;
              int bestScore = 0;
              List<String> relatedTopics = new ArrayList<>();

              for (Map<String, Object> faq : faqs) {
                String question = ((String) faq.get("question")).toLowerCase();
                @SuppressWarnings("unchecked")
                List<String> keywords = (List<String>) faq.get("keywords");
                int score = 0;

                // Check question text overlap
                for (String word : query.split("\\s+")) {
                  if (question.contains(word)) score += 2;
                }

                // Check keywords
                if (keywords != null) {
                  for (String kw : keywords) {
                    if (query.contains(kw.toLowerCase())) score += 3;
                  }
                }

                if (score > bestScore) {
                  bestScore = score;
                  bestMatch = faq;
                }
                if (score > 0) {
                  relatedTopics.add((String) faq.get("question"));
                }
              }

              if (bestMatch != null && bestScore >= 2) {
                StringBuilder sb = new StringBuilder();
                sb.append("Answer: ").append(bestMatch.get("answer"));
                if (relatedTopics.size() > 1) {
                  sb.append("\n\nRelated topics: ");
                  sb.append(
                      String.join(
                          ", ", relatedTopics.subList(0, Math.min(3, relatedTopics.size()))));
                }
                return new FunctionResult(sb.toString());
              }

              return new FunctionResult(
                  "No matching FAQ found for: "
                      + query
                      + ". Available topics include: "
                      + String.join(
                          ", ",
                          relatedTopics.isEmpty()
                              ? faqBullets.subList(0, Math.min(3, faqBullets.size()))
                              : relatedTopics));
            }));
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

  public static Map<String, Object> faq(String question, String answer, List<String> keywords) {
    Map<String, Object> f = new LinkedHashMap<>();
    f.put("question", question);
    f.put("answer", answer);
    if (keywords != null) f.put("keywords", keywords);
    return f;
  }
}
