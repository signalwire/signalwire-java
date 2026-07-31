package com.signalwire.sdk.skills;

import com.signalwire.sdk.logging.Logger;
import com.signalwire.sdk.skills.builtin.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Static registry of all available skills. Skills are registered by name and can be instantiated on
 * demand.
 *
 * <p>The class also exposes a small per-instance surface: an instance constructor and {@link
 * #addSkillDirectory(String)} let callers register additional external skill directories. The
 * static registry is kept for the common case; instance state is limited to the external skill
 * directories list.
 */
public final class SkillRegistry {

  private static final Logger log = Logger.getLogger(SkillRegistry.class);
  private static final Map<String, Supplier<SkillBase>> registry = new ConcurrentHashMap<>();

  /** External skill directories registered via {@link #addSkillDirectory(String)}. */
  private final List<String> externalPaths = new ArrayList<>();

  /**
   * Per-instance skill registrations, keyed by name. A fresh {@code SkillRegistry()} starts EMPTY,
   * and {@link #registerSkill(String, Supplier)} adds to it idempotently. Distinct from the static
   * built-in {@code registry}, which backs on-demand built-in lookup. Insertion-ordered.
   */
  private final Map<String, Supplier<SkillBase>> instanceSkills = new LinkedHashMap<>();

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
    register("custom_skills", CustomSkillsSkill::new);
    register("mcp_gateway", McpGatewaySkill::new);
  }

  /** Env var seeding external skill-search directories. */
  private static final String ENV_SKILL_PATHS = "SIGNALWIRE_SKILL_PATHS";

  /**
   * Public no-arg constructor so callers can manage their own external-paths list. The static
   * registry is unaffected.
   *
   * <p>Seeds {@code externalPaths} from the {@code SIGNALWIRE_SKILL_PATHS} environment variable — a
   * {@link File#pathSeparator}-delimited list of directories to search for external skills.
   * Non-existent or non-directory entries are skipped: env seeding is best-effort, unlike the
   * explicit {@link #addSkillDirectory(String)}, which validates and throws.
   */
  public SkillRegistry() {
    seedExternalPathsFromEnv();
  }

  private void seedExternalPathsFromEnv() {
    String raw = System.getenv(ENV_SKILL_PATHS);
    if (raw == null || raw.isEmpty()) {
      return;
    }
    // limit 0 == drop trailing empty segments (a PATH ending in the separator
    // contributes no entry); the loop below skips empties anyway.
    for (String path : raw.split(java.util.regex.Pattern.quote(File.pathSeparator), 0)) {
      if (path.isEmpty()) {
        continue;
      }
      File dir = new File(path);
      if (dir.isDirectory() && !externalPaths.contains(path)) {
        externalPaths.add(path);
      }
    }
  }

  /** Register a skill factory. */
  public static void register(String name, Supplier<SkillBase> factory) {
    registry.put(name, factory);
    log.debug("Registered skill: %s", name);
  }

  /** Get a new instance of a skill by name. */
  public static SkillBase get(String name) {
    Supplier<SkillBase> factory = registry.get(name);
    if (factory == null) {
      return null;
    }
    return factory.get();
  }

  /** Check if a skill is registered. */
  public static boolean has(String name) {
    return registry.containsKey(name);
  }

  /** List all registered skill names. */
  public static Set<String> list() {
    return Collections.unmodifiableSet(registry.keySet());
  }

  /** Unregister a skill (for testing). */
  public static void unregister(String name) {
    registry.remove(name);
  }

  /**
   * Add a directory to search for skills.
   *
   * <p>Validates that the path exists and is a directory, then appends it (de-duplicated) to {@code
   * externalPaths}. Throws {@link IllegalArgumentException} for a non-existent path or a
   * non-directory.
   *
   * @param path absolute or relative path to a directory containing skill subdirectories
   * @throws IllegalArgumentException when the path doesn't exist or isn't a directory.
   */
  public synchronized void addSkillDirectory(String path) {
    File dir = new File(path);
    if (!dir.exists()) {
      throw new IllegalArgumentException("Skill directory does not exist: " + path);
    }
    if (!dir.isDirectory()) {
      throw new IllegalArgumentException("Path is not a directory: " + path);
    }
    if (!externalPaths.contains(path)) {
      externalPaths.add(path);
    }
  }

  /** Returns an immutable copy of the registered external skill directories. */
  public synchronized List<String> getExternalPaths() {
    return Collections.unmodifiableList(new ArrayList<>(externalPaths));
  }

  /**
   * List metadata for all available (registered) skills.
   *
   * <p>Returns one entry per skill with its name, description, and version. Skills are registered
   * eagerly (there is no on-demand directory scan), so this enumerates the static registry.
   *
   * @return a list of skill metadata maps
   */
  public List<Map<String, Object>> listSkills() {
    List<Map<String, Object>> out = new ArrayList<>();
    for (String name : new TreeSet<>(registry.keySet())) {
      SkillBase skill = get(name);
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("name", name);
      if (skill != null) {
        entry.put("description", skill.getDescription());
        entry.put("version", skill.getVersion());
        entry.put("supports_multiple_instances", skill.supportsMultipleInstances());
        entry.put("required_packages", skill.getRequiredPackages());
        entry.put("required_env_vars", skill.getRequiredEnvVars());
      }
      out.add(entry);
    }
    return out;
  }

  /**
   * Discover and return all available skills.
   *
   * <p>Returns the same enumeration as {@link #listSkills()} — skills load on demand, so discovery
   * is the same scan.
   *
   * @return a list of skill metadata maps
   */
  public List<Map<String, Object>> discoverSkills() {
    return listSkills();
  }

  /**
   * Get the skill class registered under a name.
   *
   * <p>Returns the concrete {@link SkillBase} subclass registered for {@code skillName}, or {@code
   * null} if none is registered.
   *
   * @param skillName the registered skill name
   * @return the skill's implementation class, or null if not registered
   */
  public Class<? extends SkillBase> getSkillClass(String skillName) {
    SkillBase skill = get(skillName);
    return skill == null ? null : skill.getClass();
  }

  /**
   * Register a skill class directly.
   *
   * <p>Instantiates the class, derives its name from {@link SkillBase#getName()}, and registers a
   * factory for it. Throws {@link IllegalArgumentException} if the class cannot be instantiated
   * with a public no-arg constructor.
   *
   * @param skillClass a concrete {@link SkillBase} subclass with a public no-arg constructor
   * @throws IllegalArgumentException if the class cannot be instantiated
   */
  public void registerSkill(Class<? extends SkillBase> skillClass) {
    try {
      SkillBase probe = skillClass.getDeclaredConstructor().newInstance();
      String name = probe.getName();
      if (name == null || name.isEmpty()) {
        throw new IllegalArgumentException(
            skillClass.getName() + " must define a non-empty skill name");
      }
      register(name, () -> newInstance(skillClass));
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException(
          "Cannot register skill " + skillClass.getName() + ": " + e.getMessage(), e);
    }
  }

  /**
   * Register a skill factory by name into THIS instance's registry, idempotently. Re-registering an
   * already-present name is skipped: the first registration wins and a repeat with the same name is
   * a no-op. Returns {@code true} if this call added the name, {@code false} if it was already
   * present.
   *
   * @param name the skill name
   * @param factory a factory producing a fresh {@link SkillBase} instance
   * @return whether this call registered a new name
   */
  public boolean registerSkill(String name, Supplier<SkillBase> factory) {
    if (instanceSkills.containsKey(name)) {
      return false;
    }
    instanceSkills.put(name, factory);
    return true;
  }

  /**
   * The names registered into THIS instance via {@link #registerSkill(String, Supplier)}, sorted.
   *
   * @return a sorted list of this instance's registered skill names
   */
  public List<String> registeredNames() {
    return new ArrayList<>(new TreeSet<>(instanceSkills.keySet()));
  }

  private static SkillBase newInstance(Class<? extends SkillBase> skillClass) {
    try {
      return skillClass.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot instantiate skill " + skillClass.getName(), e);
    }
  }

  /**
   * List all skill sources and the skills available from each.
   *
   * <p>A map from source type ({@code built-in}, {@code external_paths}, {@code entry_points},
   * {@code registered}) to the skill names from that source. There is no plugin entry-point
   * mechanism here, so all registered skills report as {@code built-in}, external directories are
   * listed under {@code external_paths}, and {@code entry_points} is always empty — the key is
   * still present so the returned shape is stable.
   *
   * @return a map of source name to the list of skill names from that source
   */
  public Map<String, List<String>> listAllSkillSources() {
    Map<String, List<String>> sources = new LinkedHashMap<>();
    sources.put("built-in", new ArrayList<>(new TreeSet<>(registry.keySet())));
    sources.put("external_paths", new ArrayList<>(externalPaths));
    sources.put("entry_points", new ArrayList<>());
    sources.put("registered", new ArrayList<>());
    return sources;
  }

  /**
   * Get complete schema for all registered skills.
   *
   * <p>Returns a map keyed by skill name where each value contains parameter metadata. Skills do
   * not yet carry rich parameter introspection, so each value defaults to a minimal shape with the
   * skill name and an empty {@code parameters} map; built-in skills that expose {@code
   * getSkillDescription} / {@code getSkillVersion} get those merged in.
   *
   * @return ordered map of skill name to schema metadata
   */
  public Map<String, Map<String, Object>> getAllSkillsSchema() {
    Map<String, Map<String, Object>> out = new java.util.LinkedHashMap<>();
    for (String name : new java.util.TreeSet<>(registry.keySet())) {
      Map<String, Object> entry = new java.util.LinkedHashMap<>();
      entry.put("name", name);
      entry.put("parameters", new HashMap<String, Object>());
      try {
        SkillBase skill = get(name);
        if (skill != null) {
          try {
            java.lang.reflect.Method m = skill.getClass().getMethod("getSkillDescription");
            Object v = m.invoke(skill);
            if (v != null) entry.put("description", v.toString());
          } catch (NoSuchMethodException ignore) {
            /* no-op */
          }
          try {
            java.lang.reflect.Method m = skill.getClass().getMethod("getSkillVersion");
            Object v = m.invoke(skill);
            if (v != null) entry.put("version", v.toString());
          } catch (NoSuchMethodException ignore) {
            /* no-op */
          }
        }
      } catch (Exception e) {
        // Fall back to the minimal entry on reflection errors.
      }
      out.put(name, entry);
    }
    return out;
  }
}
