package com.signalwire.sdk.pom;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java parity tests for {@link Section}, mirroring
 * {@code signalwire-python/tests/unit/pom/test_pom_object_model.py::TestSectionBasics}
 * and the byte-exact rendered shapes from {@code signalwire/pom/pom.py}.
 */
class SectionTest {

    @Test
    void titleOnlySection() {
        Section s = new Section("Hello");
        assertEquals("Hello", s.getTitle());
        assertEquals("", s.getBody());
        assertTrue(s.getBullets().isEmpty());
        assertTrue(s.getSubsections().isEmpty());
    }

    @Test
    void addBodyReplacesPreviousBody() {
        // Python's Section.add_body explicitly REPLACES existing body.
        Section s = new Section("X", "initial");
        s.addBody("replacement");
        String md = s.renderMarkdown();
        assertTrue(md.contains("replacement"), md);
        assertFalse(md.contains("initial"), md);
    }

    @Test
    void addBulletsAppends() {
        Section s = new Section("X");
        s.addBullets(List.of("one", "two"));
        s.addBullets(List.of("three"));
        assertEquals(List.of("one", "two", "three"), s.getBullets());
    }

    @Test
    void addSubsectionRequiresTitle() {
        Section parent = new Section("P");
        assertThrows(IllegalArgumentException.class, () -> parent.addSubsection(null));
    }

    @Test
    void addSubsectionReturnsAndAppendsChild() {
        Section parent = new Section("P");
        Section child = parent.addSubsection("C", "cb");
        assertEquals("C", child.getTitle());
        assertEquals(1, parent.getSubsections().size());
        assertSame(child, parent.getSubsections().get(0));
    }

    @Test
    void renderMarkdownIncludesTitleBodyBullets() {
        Section s = new Section("T", "b", List.of("x"));
        String md = s.renderMarkdown();
        assertTrue(md.contains("T"));
        assertTrue(md.contains("b"));
        assertTrue(md.contains("x"));
    }

    @Test
    void renderMarkdownTitleHasLevel2HeadingByDefault() {
        // Python defaults level=2 → "## Title\n".
        Section s = new Section("Greeting", "Hello world");
        String md = s.renderMarkdown();
        assertTrue(md.startsWith("## Greeting\n"), "expected ## prefix, got: " + md);
    }

    @Test
    void renderMarkdownBulletPrefixIsDash() {
        Section s = new Section("Steps", "", List.of("a", "b"));
        String md = s.renderMarkdown();
        assertTrue(md.contains("- a\n- b"), md);
    }

    @Test
    void renderMarkdownNumberedBulletsUseOneIndexed() {
        Section s = new Section("Steps", "", List.of("a", "b"), null, true);
        String md = s.renderMarkdown();
        assertTrue(md.contains("1. a\n2. b"), md);
    }

    @Test
    void toMapOmitsEmptyKeys() {
        Section s = new Section("OnlyTitle");
        Map<String, Object> map = s.toMap();
        assertEquals(1, map.size(), "title-only Section should serialise to a 1-key map");
        assertEquals("OnlyTitle", map.get("title"));
    }

    @Test
    void toMapKeyOrderMatchesPython() {
        // Python's Section.to_dict emits in this exact order.
        Section s = new Section("X", "b", List.of("a"));
        Section sub = s.addSubsection("Y", "yb");
        Map<String, Object> map = s.toMap();
        List<String> keys = new ArrayList<>(map.keySet());
        assertEquals(List.of("title", "body", "bullets", "subsections"), keys);
    }

    @Test
    void toMapNumberedFlagsOnlyAppearWhenSet() {
        Section s = new Section("X", "y", null, null, false);
        assertFalse(s.toMap().containsKey("numbered"));
        assertFalse(s.toMap().containsKey("numberedBullets"));

        Section t = new Section("X", "y", null, true, true);
        Map<String, Object> map = t.toMap();
        assertEquals(true, map.get("numbered"));
        assertEquals(true, map.get("numberedBullets"));
    }

    @Test
    void renderXmlSimpleSectionMatchesPythonShape() {
        // Python's Section.render_xml(indent=0) for a simple section.
        Section s = new Section("Greeting", "Hi");
        String xml = s.renderXml();
        // Exact lines emitted by Python.
        assertEquals(
                "<section>\n"
                + "  <title>Greeting</title>\n"
                + "  <body>Hi</body>\n"
                + "</section>",
                xml);
    }

    @Test
    void renderXmlBulletsBlock() {
        Section s = new Section("S", "", List.of("a", "b"));
        String xml = s.renderXml();
        assertTrue(xml.contains("<bullets>"), xml);
        assertTrue(xml.contains("<bullet>a</bullet>"), xml);
        assertTrue(xml.contains("<bullet>b</bullet>"), xml);
        assertTrue(xml.contains("</bullets>"), xml);
    }

    @Test
    void renderXmlNumberedBulletsHaveIdAttribute() {
        Section s = new Section("S", "", List.of("a", "b"), null, true);
        String xml = s.renderXml();
        assertTrue(xml.contains("<bullet id=\"1\">a</bullet>"), xml);
        assertTrue(xml.contains("<bullet id=\"2\">b</bullet>"), xml);
    }
}
