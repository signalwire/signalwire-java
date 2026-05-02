package com.signalwire.sdk.pom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a section in the Prompt Object Model.
 *
 * <p>Each section contains a title, optional body text, optional bullet points,
 * and can have any number of nested subsections. Mirrors the Python reference
 * {@code signalwire.pom.pom.Section} (see signalwire-python: pom/pom.py).
 *
 * <p>Idiom mapping:
 * <ul>
 *   <li>Python {@code to_dict()} ↔ Java {@link #toMap()} (returns
 *       {@code Map<String,Object>})</li>
 *   <li>Python {@code render_markdown(level, section_number)} ↔ Java
 *       {@link #renderMarkdown(int, java.util.List)}</li>
 *   <li>Python {@code render_xml(indent, section_number)} ↔ Java
 *       {@link #renderXml(int, java.util.List)}</li>
 * </ul>
 *
 * <p>Output format is byte-identical to Python's renderer so cross-port POMs
 * can round-trip through markdown/XML/JSON.
 */
public class Section {

    private String title;
    private String body;
    private final List<String> bullets;
    private final List<Section> subsections;
    /** Tri-state: {@code null} means unset (Python parity for explicit-False vs. unset). */
    private Boolean numbered;
    private boolean numberedBullets;

    /**
     * Construct a section. All parameters except {@code title} default to
     * empty/null. Mirrors Python's keyword-only constructor.
     *
     * @param title section title (may be {@code null} for an unnamed root)
     * @param body  body text (must not be {@code null})
     * @param bullets bullet points (may be {@code null} → empty list)
     * @param numbered tri-state numbering flag (may be {@code null})
     * @param numberedBullets when {@code true}, bullets render as a numbered list
     * @throws NullPointerException if {@code body} is {@code null}
     */
    public Section(String title, String body, List<String> bullets,
                   Boolean numbered, boolean numberedBullets) {
        this.title = title;
        this.body = Objects.requireNonNull(body, "body must not be null");
        this.bullets = bullets != null ? new ArrayList<>(bullets) : new ArrayList<>();
        this.subsections = new ArrayList<>();
        this.numbered = numbered;
        this.numberedBullets = numberedBullets;
    }

    /** Convenience: title-only section with empty body and no bullets. */
    public Section(String title) {
        this(title, "", null, null, false);
    }

    /** Convenience: title and body, no bullets, no numbering. */
    public Section(String title, String body) {
        this(title, body, null, null, false);
    }

    /** Convenience: title, body, and bullets, no numbering. */
    public Section(String title, String body, List<String> bullets) {
        this(title, body, bullets, null, false);
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public List<String> getBullets() {
        return bullets;
    }

    public List<Section> getSubsections() {
        return subsections;
    }

    public Boolean getNumbered() {
        return numbered;
    }

    public boolean isNumberedBullets() {
        return numberedBullets;
    }

    /**
     * Add or replace the body text for this section. Mirrors Python
     * {@code Section.add_body}.
     */
    public void addBody(String body) {
        this.body = Objects.requireNonNull(body, "body must not be null");
    }

    /**
     * Append bullet points to this section. Mirrors Python
     * {@code Section.add_bullets} (which extends, not replaces).
     */
    public void addBullets(List<String> bullets) {
        Objects.requireNonNull(bullets, "bullets must not be null");
        this.bullets.addAll(bullets);
    }

    /**
     * Add a subsection to this section. Mirrors Python
     * {@code Section.add_subsection}. Subsections must have a title.
     *
     * @return the newly created sub-{@link Section}
     * @throws IllegalArgumentException if {@code title} is {@code null}
     */
    public Section addSubsection(String title, String body, List<String> bullets,
                                 Boolean numbered, boolean numberedBullets) {
        if (title == null) {
            throw new IllegalArgumentException("Subsections must have a title");
        }
        Section sub = new Section(title, body, bullets, numbered, numberedBullets);
        subsections.add(sub);
        return sub;
    }

    /** Convenience: add subsection with title only. */
    public Section addSubsection(String title) {
        return addSubsection(title, "", null, null, false);
    }

    /** Convenience: add subsection with title and body. */
    public Section addSubsection(String title, String body) {
        return addSubsection(title, body, null, null, false);
    }

    /** Convenience: add subsection with title, body, bullets. */
    public Section addSubsection(String title, String body, List<String> bullets) {
        return addSubsection(title, body, bullets, null, false);
    }

    /**
     * Convert this section to an ordered {@link Map} for JSON / YAML
     * serialisation. Mirrors Python {@code Section.to_dict}: keys appear in
     * the same order (title, body, bullets, subsections, numbered,
     * numberedBullets) and empty/false values are omitted.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        if (title != null) {
            data.put("title", title);
        }
        if (body != null && !body.isEmpty()) {
            data.put("body", body);
        }
        if (!bullets.isEmpty()) {
            data.put("bullets", new ArrayList<>(bullets));
        }
        if (!subsections.isEmpty()) {
            List<Map<String, Object>> sub = new ArrayList<>(subsections.size());
            for (Section s : subsections) {
                sub.add(s.toMap());
            }
            data.put("subsections", sub);
        }
        if (Boolean.TRUE.equals(numbered)) {
            data.put("numbered", true);
        }
        if (numberedBullets) {
            data.put("numberedBullets", true);
        }
        return data;
    }

    /**
     * Render this section and its subsections as Markdown. Mirrors Python
     * {@code Section.render_markdown}.
     *
     * @param level         heading level (default 2 → "##")
     * @param sectionNumber current numbering breadcrumb (may be {@code null})
     */
    public String renderMarkdown(int level, List<Integer> sectionNumber) {
        if (sectionNumber == null) {
            sectionNumber = new ArrayList<>();
        }
        List<String> md = new ArrayList<>();

        // Title with optional numbering prefix.
        if (title != null) {
            String prefix = "";
            if (!sectionNumber.isEmpty()) {
                prefix = joinIntDots(sectionNumber) + ". ";
            }
            md.add(repeat('#', level) + " " + prefix + title + "\n");
        }

        // Body.
        if (body != null && !body.isEmpty()) {
            md.add(body + "\n");
        }

        // Bullets.
        for (int i = 0; i < bullets.size(); i++) {
            if (numberedBullets) {
                md.add((i + 1) + ". " + bullets.get(i));
            } else {
                md.add("- " + bullets.get(i));
            }
        }
        if (!bullets.isEmpty()) {
            md.add("");
        }

        // Subsections.
        boolean anySubsectionNumbered = false;
        for (Section sub : subsections) {
            if (Boolean.TRUE.equals(sub.numbered)) {
                anySubsectionNumbered = true;
                break;
            }
        }
        for (int i = 0; i < subsections.size(); i++) {
            Section sub = subsections.get(i);
            List<Integer> newSectionNumber;
            int nextLevel;
            if (title != null || !sectionNumber.isEmpty()) {
                if (anySubsectionNumbered && !Boolean.FALSE.equals(sub.numbered)) {
                    newSectionNumber = new ArrayList<>(sectionNumber);
                    newSectionNumber.add(i + 1);
                } else {
                    newSectionNumber = sectionNumber;
                }
                nextLevel = level + 1;
            } else {
                newSectionNumber = sectionNumber;
                nextLevel = level;
            }
            md.add(sub.renderMarkdown(nextLevel, newSectionNumber));
        }

        return String.join("\n", md);
    }

    /** Convenience: {@code renderMarkdown(2, null)}. */
    public String renderMarkdown() {
        return renderMarkdown(2, null);
    }

    /**
     * Render this section and its subsections as XML. Mirrors Python
     * {@code Section.render_xml}.
     */
    public String renderXml(int indent, List<Integer> sectionNumber) {
        if (sectionNumber == null) {
            sectionNumber = new ArrayList<>();
        }
        String indentStr = repeat(' ', indent * 2);
        List<String> xml = new ArrayList<>();

        xml.add(indentStr + "<section>");

        if (title != null) {
            String prefix = "";
            if (!sectionNumber.isEmpty()) {
                prefix = joinIntDots(sectionNumber) + ". ";
            }
            xml.add(indentStr + "  <title>" + prefix + title + "</title>");
        }

        if (body != null && !body.isEmpty()) {
            xml.add(indentStr + "  <body>" + body + "</body>");
        }

        if (!bullets.isEmpty()) {
            xml.add(indentStr + "  <bullets>");
            for (int i = 0; i < bullets.size(); i++) {
                if (numberedBullets) {
                    xml.add(indentStr + "    <bullet id=\"" + (i + 1) + "\">" + bullets.get(i) + "</bullet>");
                } else {
                    xml.add(indentStr + "    <bullet>" + bullets.get(i) + "</bullet>");
                }
            }
            xml.add(indentStr + "  </bullets>");
        }

        if (!subsections.isEmpty()) {
            xml.add(indentStr + "  <subsections>");
            boolean anySubsectionNumbered = false;
            for (Section sub : subsections) {
                if (Boolean.TRUE.equals(sub.numbered)) {
                    anySubsectionNumbered = true;
                    break;
                }
            }
            for (int i = 0; i < subsections.size(); i++) {
                Section sub = subsections.get(i);
                List<Integer> newSectionNumber;
                if (title != null || !sectionNumber.isEmpty()) {
                    if (anySubsectionNumbered && !Boolean.FALSE.equals(sub.numbered)) {
                        newSectionNumber = new ArrayList<>(sectionNumber);
                        newSectionNumber.add(i + 1);
                    } else {
                        newSectionNumber = sectionNumber;
                    }
                } else {
                    newSectionNumber = sectionNumber;
                }
                xml.add(sub.renderXml(indent + 2, newSectionNumber));
            }
            xml.add(indentStr + "  </subsections>");
        }

        xml.add(indentStr + "</section>");
        return String.join("\n", xml);
    }

    /** Convenience: {@code renderXml(0, null)}. */
    public String renderXml() {
        return renderXml(0, null);
    }

    // ------------------------------------------------------------------
    // Internal helpers (no java.util.stream / String.repeat() so the file
    // compiles without surprises on JDK 17+; we already require JDK 21 but
    // keep these dependency-free for clarity).
    // ------------------------------------------------------------------

    private static String repeat(char ch, int n) {
        if (n <= 0) {
            return "";
        }
        char[] buf = new char[n];
        for (int i = 0; i < n; i++) {
            buf[i] = ch;
        }
        return new String(buf);
    }

    private static String joinIntDots(List<Integer> ints) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ints.size(); i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(ints.get(i));
        }
        return sb.toString();
    }
}
