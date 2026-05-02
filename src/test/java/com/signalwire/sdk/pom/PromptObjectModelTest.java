package com.signalwire.sdk.pom;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java parity tests for {@link PromptObjectModel}, mirroring
 * {@code signalwire-python/tests/unit/pom/test_pom_object_model.py} and
 * extending with byte-exact rendered-shape assertions taken directly from
 * Python's {@code render_markdown} / {@code render_xml} / {@code to_yaml}.
 */
class PromptObjectModelTest {

    // ============================================================
    // Mirror of TestPromptObjectModelBasics
    // ============================================================

    @Test
    void emptyPomHasNoSections() {
        PromptObjectModel pom = new PromptObjectModel();
        assertTrue(pom.getSections().isEmpty());
    }

    @Test
    void addSectionReturnsSectionInstance() {
        PromptObjectModel pom = new PromptObjectModel();
        Section s = pom.addSection("Greeting");
        assertNotNull(s);
        assertEquals("Greeting", s.getTitle());
    }

    @Test
    void addSectionAppearsInSections() {
        PromptObjectModel pom = new PromptObjectModel();
        pom.addSection("A");
        pom.addSection("B");
        assertEquals(2, pom.getSections().size());
        assertEquals("A", pom.getSections().get(0).getTitle());
        assertEquals("B", pom.getSections().get(1).getTitle());
    }

    @Test
    void findSectionReturnsMatch() {
        PromptObjectModel pom = new PromptObjectModel();
        pom.addSection("Greeting", "Hello");
        Optional<Section> got = pom.findSection("Greeting");
        assertTrue(got.isPresent());
        assertEquals("Greeting", got.get().getTitle());
    }

    @Test
    void findSectionReturnsEmptyWhenAbsent() {
        PromptObjectModel pom = new PromptObjectModel();
        assertTrue(pom.findSection("Nope").isEmpty());
    }

    @Test
    void findSectionRecursesIntoSubsections() {
        // Python's find_section walks subsections too — verify Java mirrors it.
        PromptObjectModel pom = new PromptObjectModel();
        Section parent = pom.addSection("Parent", "p");
        parent.addSubsection("Child", "c");
        assertTrue(pom.findSection("Child").isPresent());
        assertEquals("Child", pom.findSection("Child").get().getTitle());
    }

    @Test
    void renderMarkdownIncludesTitleAndBody() {
        PromptObjectModel pom = new PromptObjectModel();
        pom.addSection("Greeting", "Hello world");
        String md = pom.renderMarkdown();
        assertTrue(md.contains("Greeting"));
        assertTrue(md.contains("Hello world"));
    }

    @Test
    void renderXmlReturnsXmlString() {
        PromptObjectModel pom = new PromptObjectModel();
        pom.addSection("Greeting", "Hi");
        String xml = pom.renderXml();
        assertTrue(xml.contains("<"));
        assertTrue(xml.contains(">"));
    }

    @Test
    void toMapReturnsList() {
        PromptObjectModel pom = new PromptObjectModel();
        pom.addSection("A", "body-A");
        List<Map<String, Object>> as = pom.toMap();
        assertNotNull(as);
        assertEquals(1, as.size());
    }

    @Test
    void toJsonReturnsStringWithSectionTitle() {
        PromptObjectModel pom = new PromptObjectModel();
        pom.addSection("A", "body-A");
        String json = pom.toJson();
        assertTrue(json.contains("A"));
        assertTrue(json.contains("body-A"));
    }

    @Test
    void fromJsonRoundTrip() {
        PromptObjectModel pom = new PromptObjectModel();
        pom.addSection("A", "body-A");
        String json = pom.toJson();
        PromptObjectModel restored = PromptObjectModel.fromJson(json);
        assertEquals(1, restored.getSections().size());
        assertEquals("A", restored.getSections().get(0).getTitle());
        assertEquals("body-A", restored.getSections().get(0).getBody());
    }

    // ============================================================
    // YAML mirroring TestPromptObjectModelYaml
    // ============================================================

    @Test
    void toYamlReturnsStringWithSectionTitle() {
        PromptObjectModel pom = new PromptObjectModel();
        pom.addSection("Greeting", "Hello");
        String y = pom.toYaml();
        assertTrue(y.contains("Greeting"), y);
        assertTrue(y.contains("Hello"), y);
    }

    @Test
    void fromYamlRoundTrip() {
        PromptObjectModel pom = new PromptObjectModel();
        pom.addSection("A", "body-A", List.of("x", "y"));
        String y = pom.toYaml();
        PromptObjectModel restored = PromptObjectModel.fromYaml(y);
        Optional<Section> a = restored.findSection("A");
        assertTrue(a.isPresent());
        assertEquals(List.of("x", "y"), a.get().getBullets());
    }

    @Test
    void fromYamlAcceptsParsedListInput() {
        // Python's from_yaml accepts a parsed dict — Java exposes
        // fromYamlMap for the same contract.
        Map<String, Object> entry = Map.of("title", "B", "body", "y");
        PromptObjectModel pom = PromptObjectModel.fromYamlMap(List.of(entry));
        assertTrue(pom.findSection("B").isPresent());
    }

    // ============================================================
    // Byte-exact rendered-shape parity (taken from Python output).
    // ============================================================

    @Test
    void renderMarkdownSimpleSectionMatchesPythonExact() {
        // Python output: '## Greeting\n\nHello world\n'
        PromptObjectModel pom = new PromptObjectModel();
        pom.addSection("Greeting", "Hello world");
        assertEquals("## Greeting\n\nHello world\n", pom.renderMarkdown());
    }

    @Test
    void renderMarkdownNumberedTopLevelSectionsMatchesPythonExact() {
        // Python output: '## 1. A\n\nabody\n\n## 2. B\n\nbbody\n'
        PromptObjectModel pom = new PromptObjectModel();
        pom.addSection("A", "abody", null, true, false);
        pom.addSection("B", "bbody");
        assertEquals("## 1. A\n\nabody\n\n## 2. B\n\nbbody\n", pom.renderMarkdown());
    }

    @Test
    void renderMarkdownNestedSubsectionMatchesPythonExact() {
        // Python output: '## Parent\n\np\n\n### Child\n\nc\n'
        PromptObjectModel pom = new PromptObjectModel();
        Section parent = pom.addSection("Parent", "p");
        parent.addSubsection("Child", "c");
        assertEquals("## Parent\n\np\n\n### Child\n\nc\n", pom.renderMarkdown());
    }

    @Test
    void renderXmlEnvelopeMatchesPythonExact() {
        // Python output is the full <?xml … ?> + <prompt> envelope.
        PromptObjectModel pom = new PromptObjectModel();
        pom.addSection("Greeting", "Hello world");
        String expected =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<prompt>\n"
                + "  <section>\n"
                + "    <title>Greeting</title>\n"
                + "    <body>Hello world</body>\n"
                + "  </section>\n"
                + "</prompt>";
        assertEquals(expected, pom.renderXml());
    }

    // ============================================================
    // Validation parity
    // ============================================================

    @Test
    void onlyFirstSectionMayHaveNullTitle() {
        PromptObjectModel pom = new PromptObjectModel();
        pom.addSection(null, "first body");
        assertThrows(IllegalArgumentException.class,
                () -> pom.addSection(null, "second body"));
    }

    // ============================================================
    // addPomAsSubsection
    // ============================================================

    @Test
    void addPomAsSubsectionByTitleNests() {
        PromptObjectModel host = new PromptObjectModel();
        host.addSection("Host", "hostbody");
        PromptObjectModel guest = new PromptObjectModel();
        guest.addSection("Guest", "guestbody");

        host.addPomAsSubsection("Host", guest);

        Section h = host.findSection("Host").orElseThrow();
        assertEquals(1, h.getSubsections().size());
        assertEquals("Guest", h.getSubsections().get(0).getTitle());
    }

    @Test
    void addPomAsSubsectionBySectionRefNests() {
        PromptObjectModel host = new PromptObjectModel();
        Section h = host.addSection("Host", "hostbody");
        PromptObjectModel guest = new PromptObjectModel();
        guest.addSection("Guest", "guestbody");

        host.addPomAsSubsection(h, guest);
        assertEquals("Guest", h.getSubsections().get(0).getTitle());
    }

    @Test
    void addPomAsSubsectionUnknownTitleThrows() {
        PromptObjectModel host = new PromptObjectModel();
        host.addSection("Host", "x");
        PromptObjectModel guest = new PromptObjectModel();
        guest.addSection("Guest", "y");
        assertThrows(IllegalArgumentException.class,
                () -> host.addPomAsSubsection("Missing", guest));
    }

    // ============================================================
    // Constructor-from-data parity (Python's _from_dict path)
    // ============================================================

    @Test
    void constructorFromDataPopulatesSections() {
        Map<String, Object> entry = Map.of("title", "A", "body", "a-body");
        PromptObjectModel pom = new PromptObjectModel(List.of(entry));
        assertEquals(1, pom.getSections().size());
        assertEquals("A", pom.getSections().get(0).getTitle());
        assertEquals("a-body", pom.getSections().get(0).getBody());
    }
}
