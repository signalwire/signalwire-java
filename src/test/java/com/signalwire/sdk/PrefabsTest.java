package com.signalwire.sdk;

import static org.junit.jupiter.api.Assertions.*;

import com.signalwire.sdk.prefabs.*;
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
}
