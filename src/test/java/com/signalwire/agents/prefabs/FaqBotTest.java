package com.signalwire.agents.prefabs;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class FaqBotTest {

    @Test
    void testCreation() {
        List<Map<String, Object>> faqs = List.of(
                FAQBotAgent.faq("Hours?", "9-5 M-F", List.of("hours", "open"))
        );
        FAQBotAgent prefab = new FAQBotAgent("test-faq", faqs);
        assertNotNull(prefab.getAgent());
        assertEquals("test-faq", prefab.getAgent().getName());
    }

    @Test
    void testHasLookupFaqTool() {
        List<Map<String, Object>> faqs = List.of(
                FAQBotAgent.faq("Hours?", "9-5 M-F", List.of("hours"))
        );
        FAQBotAgent prefab = new FAQBotAgent("test", faqs);
        assertTrue(prefab.getAgent().hasTool("lookup_faq"));
    }

    @Test
    void testFaqHelper() {
        Map<String, Object> faq = FAQBotAgent.faq("Q?", "A.", List.of("k1", "k2"));
        assertEquals("Q?", faq.get("question"));
        assertEquals("A.", faq.get("answer"));
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) faq.get("keywords");
        assertEquals(2, keywords.size());
    }

    @Test
    void testLookupFaqExecution() {
        List<Map<String, Object>> faqs = List.of(
                FAQBotAgent.faq("What are your hours?", "We are open 9-5 M-F", List.of("hours", "open"))
        );
        FAQBotAgent prefab = new FAQBotAgent("test", faqs);
        var result = prefab.getAgent().onFunctionCall("lookup_faq",
                Map.of("query", "hours"), Map.of());
        assertNotNull(result);
        assertTrue(result.getResponse().contains("9-5"));
    }

    @Test
    void testLookupFaqNoMatch() {
        List<Map<String, Object>> faqs = List.of(
                FAQBotAgent.faq("Hours?", "9-5", List.of("hours"))
        );
        FAQBotAgent prefab = new FAQBotAgent("test", faqs);
        var result = prefab.getAgent().onFunctionCall("lookup_faq",
                Map.of("query", "zzz_nothing"), Map.of());
        assertNotNull(result);
        // Should return something even with no match
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSwmlRendering() {
        List<Map<String, Object>> faqs = List.of(
                FAQBotAgent.faq("Q?", "A.", List.of("k"))
        );
        FAQBotAgent prefab = new FAQBotAgent("test", faqs);
        Map<String, Object> swml = prefab.getAgent().renderSwml("http://localhost:3000");
        assertNotNull(swml);
        assertEquals("1.0.0", swml.get("version"));
    }
}
