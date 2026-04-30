package com.signalwire.sdk.skills;

import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.skills.builtin.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Static registry of all available skills.
 * Skills are registered by name and can be instantiated on demand.
 *
 * <p>The class also exposes a small per-instance surface for
 * Python-parity: an instance constructor and {@link #addSkillDirectory(String)}
 * mirror Python's {@code SkillRegistry().add_skill_directory(path)}. The
 * static registry is kept for the existing Java idiom; instance state is
 * limited to the external skill directories list.
 */
public final class SkillRegistry {

    private static final Logger log = Logger.getLogger(SkillRegistry.class);
    private static final Map<String, Supplier<SkillBase>> registry = new ConcurrentHashMap<>();

    /** External skill directories registered via {@link #addSkillDirectory(String)}. */
    private final List<String> externalPaths = new ArrayList<>();

    static {
        // Register all 18 built-in skills
        register("datetime", DatetimeSkill::new);
        register("math", MathSkill::new);
        register("joke", JokeSkill::new);
        register("weather_api", WeatherApiSkill::new);
        register("web_search", WebSearchSkill::new);
        register("wikipedia_search", WikipediaSearchSkill::new);
        register("google_maps", GoogleMapsSkill::new);
        register("spider", SpiderSkill::new);
        register("datasphere", DatasphereSkill::new);
        register("datasphere_serverless", DatasphereServerlessSkill::new);
        register("swml_transfer", SwmlTransferSkill::new);
        register("play_background_file", PlayBackgroundFileSkill::new);
        register("api_ninjas_trivia", ApiNinjaTriviaSkill::new);
        register("native_vector_search", NativeVectorSearchSkill::new);
        register("info_gatherer", InfoGathererSkill::new);
        register("claude_skills", ClaudeSkillsSkill::new);
        register("mcp_gateway", McpGatewaySkill::new);
        register("custom_skills", CustomSkillsSkill::new);
    }

    /** Public no-arg constructor so callers can manage their own
     *  external-paths list. The static registry is unaffected. */
    public SkillRegistry() {}

    /**
     * Register a skill factory.
     */
    public static void register(String name, Supplier<SkillBase> factory) {
        registry.put(name, factory);
        log.debug("Registered skill: %s", name);
    }

    /**
     * Get a new instance of a skill by name.
     */
    public static SkillBase get(String name) {
        Supplier<SkillBase> factory = registry.get(name);
        if (factory == null) {
            return null;
        }
        return factory.get();
    }

    /**
     * Check if a skill is registered.
     */
    public static boolean has(String name) {
        return registry.containsKey(name);
    }

    /**
     * List all registered skill names.
     */
    public static Set<String> list() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    /**
     * Unregister a skill (for testing).
     */
    public static void unregister(String name) {
        registry.remove(name);
    }

    /**
     * Add a directory to search for skills.
     *
     * <p>Mirrors Python's
     * {@code signalwire.skills.registry.SkillRegistry.add_skill_directory}:
     * validate that the path exists and is a directory, then append it
     * (de-duplicated) to {@code externalPaths}. Throws
     * {@link IllegalArgumentException} (the Java analog of Python's
     * {@code ValueError}) for non-existent paths or non-directories.
     *
     * @param path absolute or relative path to a directory containing
     *             skill subdirectories
     * @throws IllegalArgumentException when the path doesn't exist or
     *                                  isn't a directory.
     */
    public synchronized void addSkillDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            throw new IllegalArgumentException(
                    "Skill directory does not exist: " + path);
        }
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException(
                    "Path is not a directory: " + path);
        }
        if (!externalPaths.contains(path)) {
            externalPaths.add(path);
        }
    }

    /**
     * Returns an immutable copy of the registered external skill
     * directories. Parity surface for Python's {@code _external_paths}.
     */
    public synchronized List<String> getExternalPaths() {
        return Collections.unmodifiableList(new ArrayList<>(externalPaths));
    }
}
