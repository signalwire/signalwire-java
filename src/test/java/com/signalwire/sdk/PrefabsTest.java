package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.prefabs.*;
import com.signalwire.sdk.swaig.FunctionResult;
import java.util.*;
import org.junit.jupiter.api.Test;

class PrefabsTest {

  // ======== InfoGathererAgent Tests ========

  @Test
  void testInfoGathererAgentCreation() {
    List<Map<String, Object>> questions =
        List.of(
            InfoGathererAgent.question("name", "What is your name?"),
            InfoGathererAgent.question("email", "What is your email?", true, null));

    InfoGathererAgent prefab = new InfoGathererAgent("test-gatherer", questions);
    assertNotNull(prefab.getAgent());
    assertEquals("test-gatherer", prefab.getAgent().getName());
    assertTrue(prefab.getAgent().hasSkill("info_gatherer"));
  }

  @Test
  void testInfoGathererQuestionHelper() {
    Map<String, Object> q =
        InfoGathererAgent.question("phone", "Phone number?", true, "Must be valid");
    assertEquals("phone", q.get("key_name"));
    assertEquals("Phone number?", q.get("question_text"));
    assertTrue((Boolean) q.get("confirm"));
    assertEquals("Must be valid", q.get("prompt_add"));
  }

  // ======== SurveyAgent Tests ========

  @Test
  void testSurveyAgentCreation() {
    List<Map<String, Object>> questions =
        List.of(
            SurveyAgent.ratingQuestion("How would you rate our service?", 1, 5),
            SurveyAgent.multipleChoiceQuestion(
                "Which feature do you use most?", List.of("Chat", "Voice", "Video")),
            SurveyAgent.yesNoQuestion("Would you recommend us?"),
            SurveyAgent.openEndedQuestion("Any additional comments?"));

    SurveyAgent prefab = new SurveyAgent("test-survey", questions);
    assertNotNull(prefab.getAgent());
    assertEquals("test-survey", prefab.getAgent().getName());
    assertTrue(prefab.getAgent().hasTool("start_survey"));
    assertTrue(prefab.getAgent().hasTool("submit_survey_answer"));
  }

  @Test
  void testSurveyQuestionHelpers() {
    Map<String, Object> rating = SurveyAgent.ratingQuestion("Rate us", 1, 10);
    assertEquals("rating", rating.get("type"));
    assertEquals(1, rating.get("min"));
    assertEquals(10, rating.get("max"));

    Map<String, Object> mc = SurveyAgent.multipleChoiceQuestion("Pick one", List.of("A", "B"));
    assertEquals("multiple_choice", mc.get("type"));

    Map<String, Object> yn = SurveyAgent.yesNoQuestion("Yes or no?");
    assertEquals("yes_no", yn.get("type"));

    Map<String, Object> oe = SurveyAgent.openEndedQuestion("Tell us more");
    assertEquals("open_ended", oe.get("type"));
  }

  // ======== ReceptionistAgent Tests ========

  @Test
  void testReceptionistAgentCreation() {
    Map<String, Map<String, Object>> departments = new LinkedHashMap<>();
    departments.put("sales", ReceptionistAgent.phoneDepartment("Sales Department", "+15551234567"));
    departments.put(
        "support",
        ReceptionistAgent.swmlDepartment("Technical Support", "https://support.example.com/swml"));

    ReceptionistAgent prefab =
        new ReceptionistAgent("test-receptionist", "Welcome to our company!", departments);
    assertNotNull(prefab.getAgent());
    assertEquals("test-receptionist", prefab.getAgent().getName());
    assertTrue(prefab.getAgent().hasSkill("swml_transfer"));
  }

  @Test
  void testDepartmentHelpers() {
    Map<String, Object> phone = ReceptionistAgent.phoneDepartment("Sales", "+15551234567");
    assertEquals("Sales", phone.get("description"));
    assertEquals("+15551234567", phone.get("address"));

    Map<String, Object> swml =
        ReceptionistAgent.swmlDepartment("Support", "https://example.com/swml");
    assertEquals("Support", swml.get("description"));
    assertEquals("https://example.com/swml", swml.get("url"));
  }

  // ======== FAQBotAgent Tests ========

  @Test
  void testFAQBotAgentCreation() {
    List<Map<String, Object>> faqs =
        List.of(
            FAQBotAgent.faq(
                "What are your hours?",
                "We are open 9-5 M-F",
                List.of("hours", "open", "schedule")),
            FAQBotAgent.faq(
                "How do I reset my password?",
                "Go to Settings > Security > Reset Password",
                List.of("password", "reset", "security")));

    FAQBotAgent prefab = new FAQBotAgent("test-faq", faqs);
    assertNotNull(prefab.getAgent());
    assertEquals("test-faq", prefab.getAgent().getName());
    assertTrue(prefab.getAgent().hasTool("lookup_faq"));
  }

  @Test
  void testFAQBotToolExecution() {
    List<Map<String, Object>> faqs =
        List.of(
            FAQBotAgent.faq(
                "What are your hours?", "We are open 9-5 M-F", List.of("hours", "open")));

    FAQBotAgent prefab = new FAQBotAgent("test-faq", faqs);
    var result =
        prefab
            .getAgent()
            .onFunctionCall("lookup_faq", Map.of("query", "what are your hours"), Map.of());
    assertNotNull(result);
    assertTrue(result.getResponse().contains("9-5"));
  }

  @Test
  void testFAQHelper() {
    Map<String, Object> faq = FAQBotAgent.faq("Q?", "A.", List.of("k1", "k2"));
    assertEquals("Q?", faq.get("question"));
    assertEquals("A.", faq.get("answer"));
    @SuppressWarnings("unchecked")
    List<String> keywords = (List<String>) faq.get("keywords");
    assertEquals(2, keywords.size());
  }

  // ======== ConciergeAgent Tests ========

  @Test
  void testConciergeAgentCreation() {
    List<Map<String, Object>> amenities =
        List.of(
            ConciergeAgent.amenity(
                "Pool", "Olympic-size swimming pool", "6am-10pm", "Ground Floor", "Free"),
            ConciergeAgent.amenity(
                "Spa", "Full-service spa", "9am-9pm", "3rd Floor", "$50/session"));

    ConciergeAgent prefab = new ConciergeAgent("test-concierge", "Grand Hotel", amenities);
    assertNotNull(prefab.getAgent());
    assertEquals("test-concierge", prefab.getAgent().getName());
    assertTrue(prefab.getAgent().hasTool("get_amenity_info"));
    assertTrue(prefab.getAgent().hasTool("check_availability"));
  }

  @Test
  void testConciergeToolExecution() {
    List<Map<String, Object>> amenities =
        List.of(ConciergeAgent.amenity("Pool", "Olympic pool", "6-10", "1F", "Free"));

    ConciergeAgent prefab = new ConciergeAgent("test", "Hotel", amenities);
    var result =
        prefab.getAgent().onFunctionCall("get_amenity_info", Map.of("amenity", "Pool"), Map.of());
    assertNotNull(result);
    assertTrue(result.getResponse().contains("Pool"));
    assertTrue(result.getResponse().contains("Olympic pool"));
  }

  @Test
  void testAmenityHelper() {
    Map<String, Object> amenity =
        ConciergeAgent.amenity("Gym", "Fitness center", "24/7", "Basement", "Free");
    assertEquals("Gym", amenity.get("name"));
    assertEquals("Fitness center", amenity.get("description"));
    assertEquals("24/7", amenity.get("hours"));
    assertEquals("Basement", amenity.get("location"));
    assertEquals("Free", amenity.get("price"));
  }

  // ======== SWML Rendering Tests for Prefabs ========

  @Test
  @SuppressWarnings("unchecked")
  void testPrefabSwmlRendering() {
    List<Map<String, Object>> questions = List.of(InfoGathererAgent.question("name", "Name?"));
    InfoGathererAgent prefab = new InfoGathererAgent("test", questions);

    Map<String, Object> swml = prefab.getAgent().renderSwml("http://localhost:3000");
    assertNotNull(swml);
    assertEquals("1.0.0", swml.get("version"));

    Map<String, Object> sections = (Map<String, Object>) swml.get("sections");
    List<Map<String, Object>> main = (List<Map<String, Object>>) sections.get("main");
    assertTrue(main.stream().anyMatch(v -> v.containsKey("ai")));
  }

  // ======== Concierge: checkAvailability / getDirections / onSummary ========

  private static ConciergeAgent concierge() {
    List<Map<String, Object>> amenities =
        List.of(
            ConciergeAgent.amenity("Spa", "Relaxing spa", "9-5", "Level 2", "$50"),
            ConciergeAgent.amenity("Pool", "Outdoor pool", "8-8", "Rooftop", "Free"));
    return new ConciergeAgent("concierge", "Grand Hotel", amenities);
  }

  @Test
  void testConciergeCheckAvailabilityKnownAmenity() {
    ConciergeAgent c = concierge();
    Map<String, Object> args = Map.of("amenity", "Spa", "date", "2026-07-04", "time", "14:00");
    FunctionResult r = c.checkAvailability(args, Map.of());
    String text = (String) r.toMap().get("response");
    assertTrue(text.contains("Spa is available on 2026-07-04 at 14:00"), text);
    assertTrue(text.contains("Would you like to make a reservation?"));
  }

  @Test
  void testConciergeCheckAvailabilityUnknownAmenity() {
    ConciergeAgent c = concierge();
    FunctionResult r = c.checkAvailability(Map.of("amenity", "Casino"), Map.of());
    String text = (String) r.toMap().get("response");
    assertTrue(text.contains("we don't offer Casino"), text);
    // Lists the real amenities as alternatives.
    assertTrue(text.contains("Spa"));
    assertTrue(text.contains("Pool"));
  }

  @Test
  void testConciergeGetDirectionsKnownLocation() {
    ConciergeAgent c = concierge();
    FunctionResult r = c.getDirections(Map.of("location", "Pool"), Map.of());
    String text = (String) r.toMap().get("response");
    assertTrue(text.contains("The Pool is located at Rooftop"), text);
  }

  @Test
  void testConciergeGetDirectionsUnknownLocation() {
    ConciergeAgent c = concierge();
    FunctionResult r = c.getDirections(Map.of("location", "Parking"), Map.of());
    String text = (String) r.toMap().get("response");
    assertTrue(text.contains("I don't have specific directions to Parking"), text);
    assertTrue(text.contains("front desk"));
  }

  @Test
  void testConciergeRegistersAvailabilityAndDirectionTools() {
    ConciergeAgent c = concierge();
    assertTrue(c.getAgent().hasTool("check_availability"));
    assertTrue(c.getAgent().hasTool("get_directions"));
  }

  @Test
  void testConciergeOnSummaryStoresCallback() {
    ConciergeAgent c = concierge();
    assertNull(c.getSummaryHandler());
    java.util.concurrent.atomic.AtomicReference<Map<String, Object>> captured =
        new java.util.concurrent.atomic.AtomicReference<>();
    c.onSummary((summary, raw) -> captured.set(summary));
    assertNotNull(c.getSummaryHandler());
    Map<String, Object> summary = Map.of("topic", "spa");
    c.getSummaryHandler().accept(summary, Map.of());
    assertEquals(summary, captured.get());
  }

  // ======== FAQBot: searchFaqs / onSummary ========

  private static FAQBotAgent faqBot() {
    List<Map<String, Object>> faqs =
        List.of(
            FAQBotAgent.faqWithCategories(
                "What is SignalWire?",
                "A cloud communications platform.",
                null,
                List.of("general")),
            FAQBotAgent.faqWithCategories(
                "How much does it cost?", "Pay-as-you-go.", null, List.of("billing", "pricing")),
            FAQBotAgent.faqWithCategories(
                "What is SIP?", "Session Initiation Protocol.", null, List.of("technical")));
    return new FAQBotAgent("faq", faqs);
  }

  @Test
  void testFaqSearchByQueryReturnsMatch() {
    FAQBotAgent bot = faqBot();
    FunctionResult r = bot.searchFaqs(Map.of("query", "what is signalwire?"), Map.of());
    String text = (String) r.toMap().get("response");
    assertTrue(text.contains("Here are the most relevant FAQs"), text);
    assertTrue(text.contains("What is SignalWire?"), text);
  }

  @Test
  void testFaqSearchNoMatch() {
    FAQBotAgent bot = faqBot();
    FunctionResult r = bot.searchFaqs(Map.of("query", "refund policy"), Map.of());
    assertEquals("No matching FAQs found.", r.toMap().get("response"));
  }

  @Test
  void testFaqSearchByCategory() {
    FAQBotAgent bot = faqBot();
    FunctionResult r = bot.searchFaqs(Map.of("query", "", "category", "billing"), Map.of());
    String text = (String) r.toMap().get("response");
    assertTrue(text.contains("How much does it cost?"), text);
  }

  @Test
  void testFaqRegistersSearchTool() {
    assertTrue(faqBot().getAgent().hasTool("search_faqs"));
  }

  @Test
  void testFaqOnSummaryStoresCallback() {
    FAQBotAgent bot = faqBot();
    assertNull(bot.getSummaryHandler());
    bot.onSummary((s, raw) -> {});
    assertNotNull(bot.getSummaryHandler());
  }

  // ======== InfoGatherer: startQuestions / submitAnswer / callback / onSwmlRequest ========

  private static Map<String, Object> gathererGlobalData(
      List<Map<String, Object>> questions, int index, List<Map<String, Object>> answers) {
    Map<String, Object> gd = new HashMap<>();
    gd.put("questions", questions);
    gd.put("question_index", index);
    gd.put("answers", answers);
    return Map.of("global_data", gd);
  }

  @Test
  void testInfoGathererRegistersFlowTools() {
    InfoGathererAgent g =
        new InfoGathererAgent("g", List.of(InfoGathererAgent.question("name", "Name?")));
    assertTrue(g.getAgent().hasTool("start_questions"));
    assertTrue(g.getAgent().hasTool("submit_answer"));
  }

  @Test
  void testInfoGathererStartQuestionsReturnsFirst() {
    List<Map<String, Object>> questions =
        List.of(
            InfoGathererAgent.question("name", "What is your name?"),
            InfoGathererAgent.question("email", "What is your email?"));
    InfoGathererAgent g = new InfoGathererAgent("g", questions);
    FunctionResult r = g.startQuestions(Map.of(), gatherRaw(questions, 0, new ArrayList<>()));
    String text = (String) r.toMap().get("response");
    assertTrue(text.contains("What is your name?"), text);
    assertTrue(text.contains("Ask the user to answer"), text);
  }

  private static Map<String, Object> gatherRaw(
      List<Map<String, Object>> questions, int index, List<Map<String, Object>> answers) {
    return gathererGlobalData(questions, index, answers);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInfoGathererSubmitAnswerAdvancesAndStores() {
    List<Map<String, Object>> questions =
        List.of(
            InfoGathererAgent.question("name", "What is your name?"),
            InfoGathererAgent.question("email", "What is your email?"));
    InfoGathererAgent g = new InfoGathererAgent("g", questions);

    FunctionResult r =
        g.submitAnswer(Map.of("answer", "Alice"), gatherRaw(questions, 0, new ArrayList<>()));
    Map<String, Object> map = r.toMap();
    String text = (String) map.get("response");
    // Moves to the next question.
    assertTrue(text.contains("What is your email?"), text);

    // The set_global_data action carries the stored answer + advanced index.
    List<Map<String, Object>> actions = (List<Map<String, Object>>) map.get("action");
    assertNotNull(actions, "expected SWAIG actions carrying global_data update");
    Map<String, Object> gd = extractGlobalDataUpdate(actions);
    assertEquals(1, ((Number) gd.get("question_index")).intValue());
    List<Map<String, Object>> answers = (List<Map<String, Object>>) gd.get("answers");
    assertEquals(1, answers.size());
    assertEquals("name", answers.get(0).get("key_name"));
    assertEquals("Alice", answers.get(0).get("answer"));
  }

  @Test
  void testInfoGathererSubmitAnswerLastQuestionCompletes() {
    List<Map<String, Object>> questions =
        List.of(InfoGathererAgent.question("name", "What is your name?"));
    InfoGathererAgent g = new InfoGathererAgent("g", questions);
    FunctionResult r =
        g.submitAnswer(Map.of("answer", "Bob"), gatherRaw(questions, 0, new ArrayList<>()));
    String text = (String) r.toMap().get("response");
    assertTrue(text.contains("All questions have been answered"), text);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> extractGlobalDataUpdate(List<Map<String, Object>> actions) {
    for (Map<String, Object> a : actions) {
      if (a.containsKey("set_global_data")) {
        return (Map<String, Object>) a.get("set_global_data");
      }
    }
    throw new AssertionError("no set_global_data action found");
  }

  @Test
  void testInfoGathererStaticModeOnSwmlRequestReturnsNull() {
    List<Map<String, Object>> questions = List.of(InfoGathererAgent.question("name", "Name?"));
    InfoGathererAgent g = new InfoGathererAgent("g", questions);
    // Static mode: no dynamic override.
    assertNull(g.onSwmlRequest(Map.of(), Map.of(), Map.of()));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInfoGathererDynamicModeUsesCallback() {
    InfoGathererAgent g = new InfoGathererAgent("g", null);
    g.setQuestionCallback(
        (query, body, headers) ->
            List.of(
                InfoGathererAgent.question("q1", "Question one?"),
                InfoGathererAgent.question("q2", "Question two?")));
    Map<String, Object> override = g.onSwmlRequest(Map.of(), Map.of(), Map.of());
    assertNotNull(override);
    Map<String, Object> gd = (Map<String, Object>) override.get("global_data");
    List<Map<String, Object>> qs = (List<Map<String, Object>>) gd.get("questions");
    assertEquals(2, qs.size());
    assertEquals("q1", qs.get(0).get("key_name"));
    assertEquals(0, ((Number) gd.get("question_index")).intValue());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testInfoGathererDynamicModeFallbackWhenNoCallback() {
    InfoGathererAgent g = new InfoGathererAgent("g", null);
    Map<String, Object> override = g.onSwmlRequest(Map.of(), Map.of(), Map.of());
    assertNotNull(override);
    Map<String, Object> gd = (Map<String, Object>) override.get("global_data");
    List<Map<String, Object>> qs = (List<Map<String, Object>>) gd.get("questions");
    assertEquals(2, qs.size());
    assertEquals("name", qs.get(0).get("key_name"));
    assertEquals("message", qs.get(1).get("key_name"));
  }

  // ======== Receptionist: onSummary ========

  @Test
  void testReceptionistOnSummaryStoresCallback() {
    Map<String, Map<String, Object>> departments =
        Map.of("sales", ReceptionistAgent.phoneDepartment("Sales", "+15551235555"));
    ReceptionistAgent r = new ReceptionistAgent("recept", "Hi!", departments);
    assertNull(r.getSummaryHandler());
    r.onSummary((s, raw) -> {});
    assertNotNull(r.getSummaryHandler());
  }

  // ======== Survey: validateResponse / logResponse / onSummary ========

  private static SurveyAgent survey() {
    List<Map<String, Object>> questions =
        List.of(
            SurveyAgent.idQuestion("sat", "How satisfied?", "rating", 5, null),
            SurveyAgent.idQuestion(
                "feature", "Which feature?", "multiple_choice", null, List.of("Chat", "Voice")),
            SurveyAgent.idQuestion("rec", "Recommend?", "yes_no", null, null),
            SurveyAgent.idQuestion("comments", "Comments?", "open_ended", null, null));
    return new SurveyAgent("survey", questions);
  }

  @Test
  void testSurveyRegistersValidateAndLogTools() {
    SurveyAgent s = survey();
    assertTrue(s.getAgent().hasTool("validate_response"));
    assertTrue(s.getAgent().hasTool("log_response"));
  }

  @Test
  void testSurveyValidateRatingInRange() {
    FunctionResult r =
        survey().validateResponse(Map.of("question_id", "sat", "response", "4"), Map.of());
    assertTrue(((String) r.toMap().get("response")).contains("is valid"));
  }

  @Test
  void testSurveyValidateRatingOutOfRange() {
    FunctionResult r =
        survey().validateResponse(Map.of("question_id", "sat", "response", "9"), Map.of());
    String text = (String) r.toMap().get("response");
    assertTrue(text.contains("between 1 and 5"), text);
  }

  @Test
  void testSurveyValidateMultipleChoiceRejectsUnknown() {
    FunctionResult r =
        survey().validateResponse(Map.of("question_id", "feature", "response", "Fax"), Map.of());
    String text = (String) r.toMap().get("response");
    assertTrue(text.contains("Invalid choice"), text);
    assertTrue(text.contains("Chat"));
  }

  @Test
  void testSurveyValidateYesNo() {
    SurveyAgent s = survey();
    assertTrue(
        ((String)
                s.validateResponse(Map.of("question_id", "rec", "response", "Yes"), Map.of())
                    .toMap()
                    .get("response"))
            .contains("is valid"));
    assertTrue(
        ((String)
                s.validateResponse(Map.of("question_id", "rec", "response", "maybe"), Map.of())
                    .toMap()
                    .get("response"))
            .contains("'yes' or 'no'"));
  }

  @Test
  void testSurveyValidateUnknownQuestion() {
    FunctionResult r =
        survey().validateResponse(Map.of("question_id", "nope", "response", "x"), Map.of());
    assertTrue(((String) r.toMap().get("response")).contains("not found"));
  }

  @Test
  void testSurveyLogResponseAcknowledges() {
    FunctionResult r =
        survey().logResponse(Map.of("question_id", "sat", "response", "5"), Map.of());
    String text = (String) r.toMap().get("response");
    assertTrue(text.contains("How satisfied?"), text);
    assertTrue(text.contains("has been recorded"), text);
  }

  @Test
  void testSurveyOnSummaryStoresCallback() {
    SurveyAgent s = survey();
    assertNull(s.getSummaryHandler());
    s.onSummary((sum, raw) -> {});
    assertNotNull(s.getSummaryHandler());
  }
}
