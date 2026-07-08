package com.signalwire.sdk.prefabs;

import com.signalwire.sdk.agent.AgentBase;
import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import java.util.*;

/** Pre-built agent for keyword-based FAQ matching with optional related suggestions. */
public class FAQBotAgent {

  private final AgentBase agent;
  private final List<Map<String, Object>> faqs;
  private java.util.function.BiConsumer<Map<String, Object>, Map<String, Object>> summaryHandler;

  public FAQBotAgent(String name, List<Map<String, Object>> faqs) {
    this(name, faqs, "/", 3000);
  }

  public FAQBotAgent(String name, List<Map<String, Object>> faqs, String route, int port) {
    this.faqs = faqs;
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
              String query = ((String) args.get("query")).toLowerCase(java.util.Locale.ROOT);

              // Simple keyword matching
              Map<String, Object> bestMatch = null;
              int bestScore = 0;
              List<String> relatedTopics = new ArrayList<>();

              for (Map<String, Object> faq : faqs) {
                String question = ((String) faq.get("question")).toLowerCase(java.util.Locale.ROOT);
                @SuppressWarnings("unchecked")
                List<String> keywords = (List<String>) faq.get("keywords");
                int score = 0;

                // Check question text overlap
                for (String word : query.split("\\s+", 0)) {
                  if (question.contains(word)) score += 2;
                }

                // Check keywords
                if (keywords != null) {
                  for (String kw : keywords) {
                    if (query.contains(kw.toLowerCase(java.util.Locale.ROOT))) score += 3;
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

    // Register search_faqs tool (SWAIG handler -> searchFaqs)
    Map<String, Object> searchParams = new LinkedHashMap<>();
    searchParams.put("type", "object");
    searchParams.put(
        "properties",
        Map.of(
            "query",
            Map.of("type", "string", "description", "The search query"),
            "category",
            Map.of("type", "string", "description", "Optional category to filter by")));
    searchParams.put("required", List.of("query"));

    agent.defineTool(
        new ToolDefinition(
            "search_faqs",
            "Search for FAQs matching a specific query or category",
            searchParams,
            this::searchFaqs));
  }

  /**
   * SWAIG tool handler: search FAQs matching a query and/or category. Ported from the Python
   * FAQBotAgent.search_faqs -- scores each FAQ (substring match on the question, prefix boost, and
   * category match), sorts by score descending, and returns the top 3 matching questions. Reads the
   * optional {@code categories} list on each FAQ entry.
   */
  public FunctionResult searchFaqs(Map<String, Object> args, Map<String, Object> rawData) {
    String query = ((String) args.getOrDefault("query", "")).toLowerCase(java.util.Locale.ROOT);
    String category =
        ((String) args.getOrDefault("category", "")).toLowerCase(java.util.Locale.ROOT);

    List<Map<String, Object>> results = new ArrayList<>();

    for (Map<String, Object> faq : faqs) {
      String question =
          ((String) faq.getOrDefault("question", "")).toLowerCase(java.util.Locale.ROOT);
      List<String> categories = new ArrayList<>();
      @SuppressWarnings("unchecked")
      List<String> rawCategories = (List<String>) faq.get("categories");
      if (rawCategories != null) {
        for (String c : rawCategories) {
          categories.add(c.toLowerCase(java.util.Locale.ROOT));
        }
      }

      int matchScore = 0;

      if (!query.isEmpty() && question.contains(query)) {
        if (query.equals(question)) {
          matchScore += 100;
        } else {
          matchScore += 50;
        }
        if (question.startsWith(query)) {
          matchScore += 25;
        }
      }

      if (!category.isEmpty() && categories.contains(category)) {
        matchScore += 30;
      }

      if (matchScore > 0) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("question", faq.get("question"));
        r.put("score", matchScore);
        results.add(r);
      }
    }

    results.sort((a, b) -> ((Integer) b.get("score")) - ((Integer) a.get("score")));
    List<Map<String, Object>> topResults = results.subList(0, Math.min(3, results.size()));

    if (!topResults.isEmpty()) {
      StringBuilder sb = new StringBuilder("Here are the most relevant FAQs:\n\n");
      int i = 1;
      for (Map<String, Object> r : topResults) {
        sb.append(i).append(". ").append(r.get("question")).append("\n");
        i++;
      }
      return new FunctionResult(sb.toString());
    }
    return new FunctionResult("No matching FAQs found.");
  }

  /**
   * Register a post-prompt summary callback. Ported from the Python FAQBotAgent.on_summary hook:
   * invoked with the parsed summary and the raw post-prompt payload once the conversation
   * completes. Wires through to {@link AgentBase#onSummary}.
   *
   * @param handler callback receiving (summary, rawData); {@code null} clears any handler
   * @return this prefab for chaining
   */
  public FAQBotAgent onSummary(
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

  /**
   * Build an FAQ entry carrying {@code categories} (used by {@link #searchFaqs} for category
   * filtering), mirroring the Python prefab's category-tagged FAQ items.
   */
  public static Map<String, Object> faqWithCategories(
      String question, String answer, List<String> keywords, List<String> categories) {
    Map<String, Object> f = faq(question, answer, keywords);
    if (categories != null) f.put("categories", categories);
    return f;
  }
}
