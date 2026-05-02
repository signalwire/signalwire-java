package com.signalwire.sdk.pom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A structured data format for composing, organising, and rendering prompt
 * instructions for large language models. Java port of the Python reference
 * {@code signalwire.pom.pom.PromptObjectModel} (signalwire-python:
 * {@code pom/pom.py}).
 *
 * <p>The Prompt Object Model provides a tree-based representation of a prompt
 * document composed of nested {@link Section}s. Each section can include a
 * title, body text, bullet points, and arbitrarily nested subsections.
 *
 * <p>Idiom mapping:
 * <ul>
 *   <li>Python {@code to_dict()} ↔ Java {@link #toMap()} (returns
 *       {@code List<Map<String,Object>>})</li>
 *   <li>Python {@code from_json(str|dict)} ↔ Java {@link #fromJson(String)}
 *       and {@link #fromJsonMap(List)}</li>
 *   <li>Python {@code from_yaml(str|dict)} ↔ Java {@link #fromYaml(String)}
 *       and {@link #fromYamlMap(List)}</li>
 * </ul>
 */
public class PromptObjectModel {

    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_OF_MAPS_TYPE =
            new TypeToken<List<Map<String, Object>>>(){}.getType();

    private final List<Section> sections;
    private final boolean debug;

    /** Empty model (Python parity: {@code PromptObjectModel()} with debug=False). */
    public PromptObjectModel() {
        this(false);
    }

    /** Empty model with debug flag. */
    public PromptObjectModel(boolean debug) {
        this.sections = new ArrayList<>();
        this.debug = debug;
    }

    /**
     * Construct from a list of section dicts. Mirrors Python's positional
     * {@code PromptObjectModel(data)} pattern via {@link #fromJsonMap}.
     *
     * @param data list of section maps (each entry is one top-level section)
     */
    public PromptObjectModel(List<Map<String, Object>> data) {
        this.sections = new ArrayList<>();
        this.debug = false;
        if (data != null) {
            populateFromList(this, data);
        }
    }

    public List<Section> getSections() {
        return sections;
    }

    public boolean isDebug() {
        return debug;
    }

    // ------------------------------------------------------------------
    // Static factories.
    // ------------------------------------------------------------------

    /**
     * Parse a JSON string into a model. Mirrors Python
     * {@code PromptObjectModel.from_json(str)}.
     */
    public static PromptObjectModel fromJson(String json) {
        if (json == null) {
            throw new IllegalArgumentException("json must not be null");
        }
        List<Map<String, Object>> data = new Gson().fromJson(json, LIST_OF_MAPS_TYPE);
        return fromJsonMap(data);
    }

    /**
     * Build a model from an already-parsed list-of-maps. Mirrors Python's
     * dict-input branch of {@code from_json}.
     */
    public static PromptObjectModel fromJsonMap(List<Map<String, Object>> data) {
        PromptObjectModel pom = new PromptObjectModel();
        if (data != null) {
            populateFromList(pom, data);
        }
        return pom;
    }

    /** Parse a YAML string into a model. Mirrors Python {@code from_yaml(str)}. */
    public static PromptObjectModel fromYaml(String yaml) {
        if (yaml == null) {
            throw new IllegalArgumentException("yaml must not be null");
        }
        Object loaded = new Yaml().load(yaml);
        if (loaded == null) {
            return new PromptObjectModel();
        }
        if (!(loaded instanceof List)) {
            throw new IllegalArgumentException(
                    "YAML root must be a list of section dicts");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) loaded;
        return fromJsonMap(data);
    }

    /**
     * Build a model from an already-parsed list-of-maps (YAML form).
     * Mirrors Python's dict-input branch of {@code from_yaml}.
     */
    public static PromptObjectModel fromYamlMap(List<Map<String, Object>> data) {
        return fromJsonMap(data);
    }

    // ------------------------------------------------------------------
    // Construction helpers.
    // ------------------------------------------------------------------

    /**
     * Add a top-level section. Mirrors Python {@code add_section(...)}.
     *
     * @return the newly created {@link Section}
     * @throws IllegalArgumentException if a null-title section is added after
     *         the first section
     */
    public Section addSection(String title, String body, List<String> bullets,
                              Boolean numbered, boolean numberedBullets) {
        if (title == null && !sections.isEmpty()) {
            throw new IllegalArgumentException(
                    "Only the first section can have no title");
        }
        Section s = new Section(title, body == null ? "" : body, bullets,
                                numbered, numberedBullets);
        sections.add(s);
        return s;
    }

    /** Convenience: title-only section. */
    public Section addSection(String title) {
        return addSection(title, "", null, null, false);
    }

    /** Convenience: title and body. */
    public Section addSection(String title, String body) {
        return addSection(title, body, null, null, false);
    }

    /** Convenience: title, body, and bullets. */
    public Section addSection(String title, String body, List<String> bullets) {
        return addSection(title, body, bullets, null, false);
    }

    /**
     * Recursively search for a section with the given title. Mirrors Python
     * {@code find_section(title)}.
     */
    public Optional<Section> findSection(String title) {
        return findRecursive(sections, title);
    }

    private static Optional<Section> findRecursive(List<Section> sections, String title) {
        for (Section s : sections) {
            if (title == null ? s.getTitle() == null : title.equals(s.getTitle())) {
                return Optional.of(s);
            }
            Optional<Section> nested = findRecursive(s.getSubsections(), title);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }

    /**
     * Add another POM's top-level sections as subsections of an existing
     * section. Mirrors Python {@code add_pom_as_subsection(target, pom)} where
     * {@code target} is either a title (String) or a {@link Section}.
     *
     * @throws IllegalArgumentException when {@code target} is not a String or
     *         {@link Section}, or when the title is not found
     */
    public void addPomAsSubsection(Object target, PromptObjectModel pomToAdd) {
        Section targetSection;
        if (target instanceof String) {
            targetSection = findSection((String) target).orElseThrow(
                    () -> new IllegalArgumentException(
                            "No section with title '" + target + "' found."));
        } else if (target instanceof Section) {
            targetSection = (Section) target;
        } else {
            throw new IllegalArgumentException(
                    "Target must be a String or a Section object.");
        }
        for (Section s : pomToAdd.sections) {
            targetSection.getSubsections().add(s);
        }
    }

    // ------------------------------------------------------------------
    // Serialisation.
    // ------------------------------------------------------------------

    /**
     * Convert the entire model to a list of maps. Mirrors Python
     * {@code to_dict()} (named {@code toMap} here because the Java return
     * type is {@code List<Map<String,Object>>}).
     */
    public List<Map<String, Object>> toMap() {
        List<Map<String, Object>> out = new ArrayList<>(sections.size());
        for (Section s : sections) {
            out.add(s.toMap());
        }
        return out;
    }

    /** Convert to a JSON string with 2-space indent. Mirrors Python {@code to_json}. */
    public String toJson() {
        return GSON_PRETTY.toJson(toMap());
    }

    /**
     * Convert to a YAML string. Output matches Python's
     * {@code yaml.dump(..., default_flow_style=False, sort_keys=False)}.
     */
    public String toYaml() {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(false);
        opts.setIndent(2);
        opts.setIndicatorIndent(0);
        opts.setIndentWithIndicator(false);
        opts.setSplitLines(false);
        Yaml yaml = new Yaml(opts);
        return yaml.dump(toMap());
    }

    /** Render the entire model as Markdown. Mirrors Python {@code render_markdown()}. */
    public String renderMarkdown() {
        boolean anySectionNumbered = false;
        for (Section s : sections) {
            if (Boolean.TRUE.equals(s.getNumbered())) {
                anySectionNumbered = true;
                break;
            }
        }
        List<String> md = new ArrayList<>();
        int sectionCounter = 0;
        for (Section s : sections) {
            List<Integer> sectionNumber;
            if (s.getTitle() != null) {
                sectionCounter++;
                if (anySectionNumbered && !Boolean.FALSE.equals(s.getNumbered())) {
                    sectionNumber = new ArrayList<>();
                    sectionNumber.add(sectionCounter);
                } else {
                    sectionNumber = new ArrayList<>();
                }
            } else {
                sectionNumber = new ArrayList<>();
            }
            md.add(s.renderMarkdown(2, sectionNumber));
        }
        return String.join("\n", md);
    }

    /** Render the entire model as XML. Mirrors Python {@code render_xml()}. */
    public String renderXml() {
        List<String> xml = new ArrayList<>();
        xml.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.add("<prompt>");

        boolean anySectionNumbered = false;
        for (Section s : sections) {
            if (Boolean.TRUE.equals(s.getNumbered())) {
                anySectionNumbered = true;
                break;
            }
        }

        int sectionCounter = 0;
        for (Section s : sections) {
            List<Integer> sectionNumber;
            if (s.getTitle() != null) {
                sectionCounter++;
                if (anySectionNumbered && !Boolean.FALSE.equals(s.getNumbered())) {
                    sectionNumber = new ArrayList<>();
                    sectionNumber.add(sectionCounter);
                } else {
                    sectionNumber = new ArrayList<>();
                }
            } else {
                sectionNumber = new ArrayList<>();
            }
            xml.add(s.renderXml(1, sectionNumber));
        }
        xml.add("</prompt>");
        return String.join("\n", xml);
    }

    // ------------------------------------------------------------------
    // Internal: build sections from a parsed map structure.
    // ------------------------------------------------------------------

    private static void populateFromList(PromptObjectModel pom,
                                         List<Map<String, Object>> data) {
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> entry = data.get(i);
            if (i > 0 && !entry.containsKey("title")) {
                // Mirror Python: only the first top-level section may be
                // untitled; later untitled entries get a default name.
                Map<String, Object> copy = new LinkedHashMap<>(entry);
                copy.put("title", "Untitled Section");
                entry = copy;
            }
            pom.sections.add(buildSection(entry, false));
        }
    }

    @SuppressWarnings("unchecked")
    private static Section buildSection(Map<String, Object> d, boolean isSubsection) {
        if (d == null) {
            throw new IllegalArgumentException("Each section must be a dictionary.");
        }
        Object titleObj = d.get("title");
        if (titleObj != null && !(titleObj instanceof String)) {
            throw new IllegalArgumentException("'title' must be a string if present.");
        }
        Object subsectionsObj = d.get("subsections");
        if (subsectionsObj != null && !(subsectionsObj instanceof List)) {
            throw new IllegalArgumentException("'subsections' must be a list if provided.");
        }
        Object bulletsObj = d.get("bullets");
        if (bulletsObj != null && !(bulletsObj instanceof List)) {
            throw new IllegalArgumentException("'bullets' must be a list if provided.");
        }
        Object numberedObj = d.get("numbered");
        if (numberedObj != null && !(numberedObj instanceof Boolean)) {
            throw new IllegalArgumentException("'numbered' must be a boolean if provided.");
        }
        Object numberedBulletsObj = d.get("numberedBullets");
        if (numberedBulletsObj != null && !(numberedBulletsObj instanceof Boolean)) {
            throw new IllegalArgumentException("'numberedBullets' must be a boolean if provided.");
        }

        Object bodyObj = d.get("body");
        boolean hasBody = bodyObj instanceof String && !((String) bodyObj).isEmpty();
        boolean hasBullets = bulletsObj instanceof List && !((List<?>) bulletsObj).isEmpty();
        boolean hasSubsections = subsectionsObj instanceof List
                && !((List<?>) subsectionsObj).isEmpty();
        if (!hasBody && !hasBullets && !hasSubsections) {
            throw new IllegalArgumentException(
                    "All sections must have either a non-empty body, "
                    + "non-empty bullets, or subsections");
        }
        if (isSubsection && titleObj == null) {
            throw new IllegalArgumentException("All subsections must have a title");
        }

        String title = (String) titleObj;
        String body = bodyObj == null ? "" : bodyObj.toString();
        List<String> bullets = new ArrayList<>();
        if (bulletsObj != null) {
            for (Object b : (List<Object>) bulletsObj) {
                bullets.add(b == null ? "" : b.toString());
            }
        }
        Boolean numbered = numberedObj == null ? null : (Boolean) numberedObj;
        boolean numberedBullets = numberedBulletsObj != null && (Boolean) numberedBulletsObj;

        Section section = new Section(title, body, bullets, numbered, numberedBullets);

        if (subsectionsObj != null) {
            for (Object sub : (List<Object>) subsectionsObj) {
                if (!(sub instanceof Map)) {
                    throw new IllegalArgumentException("Each section must be a dictionary.");
                }
                section.getSubsections().add(buildSection((Map<String, Object>) sub, true));
            }
        }
        return section;
    }
}
