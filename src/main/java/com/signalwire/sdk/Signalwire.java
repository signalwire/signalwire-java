/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import com.signalwire.sdk.rest.RestClient;
import com.signalwire.sdk.skills.SkillBase;
import com.signalwire.sdk.skills.SkillRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Top-level convenience entry points for the SDK: {@link #RestClient(List, Map) RestClient}, {@link
 * #registerSkill}, {@link #addSkillDirectory}, {@link #listSkills}, and {@link
 * #listSkillsWithParams}.
 *
 * <p>This is a static-only utility class; it cannot be instantiated. Every helper delegates to the
 * underlying class ({@link RestClient}, {@link SkillRegistry}), so it is a strict thin layer that
 * adds no behaviour of its own — reach for the underlying class directly whenever you need more
 * control than the one-line form gives you.
 */
public final class Signalwire {

  private Signalwire() {
    // Static-only utility class
  }

  /**
   * Singleton {@link SkillRegistry} instance shared by the helpers below. Lazily instantiated so
   * the static initializer doesn't block on classpath validation.
   */
  private static volatile SkillRegistry singletonRegistry;

  private static SkillRegistry getSingletonRegistry() {
    SkillRegistry r = singletonRegistry;
    if (r == null) {
      synchronized (Signalwire.class) {
        r = singletonRegistry;
        if (r == null) {
          r = new SkillRegistry();
          singletonRegistry = r;
        }
      }
    }
    return r;
  }

  /**
   * Construct a {@link RestClient} from positional or named credentials.
   *
   * <p>Each credential is resolved independently, in this order: the positional {@code args} slot
   * (project, token, space), then the matching {@code kwargs} key, then the environment variable
   * ({@code SIGNALWIRE_PROJECT_ID} / {@code SIGNALWIRE_API_TOKEN} / {@code SIGNALWIRE_SPACE}). So
   * {@code args} may be empty, partial, or complete, and the remaining fields fall through. Both
   * arguments accept {@code null} as "not supplied".
   *
   * @param args positional credentials in the order {@code [project, token, space]}; may be empty,
   *     shorter than three entries, or {@code null}
   * @param kwargs named credentials — recognised keys are {@code project} / {@code project_id},
   *     {@code token}, and {@code space} / {@code host}
   * @return a fully wired {@link RestClient} instance
   * @throws IllegalArgumentException if project, token, or space is still missing or empty after
   *     all three sources have been consulted
   */
  public static RestClient RestClient(List<String> args, Map<String, String> kwargs) {
    if (args == null) args = java.util.Collections.emptyList();
    if (kwargs == null) kwargs = java.util.Collections.emptyMap();
    String project =
        args.size() > 0
            ? args.get(0)
            : kwargs.getOrDefault(
                "project",
                kwargs.getOrDefault("project_id", orEmpty(System.getenv("SIGNALWIRE_PROJECT_ID"))));
    String token =
        args.size() > 1
            ? args.get(1)
            : kwargs.getOrDefault("token", orEmpty(System.getenv("SIGNALWIRE_API_TOKEN")));
    String space =
        args.size() > 2
            ? args.get(2)
            : kwargs.getOrDefault(
                "space", kwargs.getOrDefault("host", orEmpty(System.getenv("SIGNALWIRE_SPACE"))));
    if (project == null
        || project.isEmpty()
        || token == null
        || token.isEmpty()
        || space == null
        || space.isEmpty()) {
      throw new IllegalArgumentException(
          "project, token, and space are required. "
              + "Provide them as args/kwargs or set SIGNALWIRE_PROJECT_ID, "
              + "SIGNALWIRE_API_TOKEN, and SIGNALWIRE_SPACE environment variables.");
    }
    return RestClient.builder().project(project).token(token).space(space).build();
  }

  /**
   * Register a custom skill class with the global {@link SkillRegistry}.
   *
   * <p>Skills are constructed via a no-arg constructor (the registry stores {@code
   * Supplier<SkillBase>} factories), so the supplied class reference is adapted into a factory
   * using reflection. The registration name comes from a public static {@code SKILL_NAME} field,
   * else a static {@code getSkillName()}, else the lower-cased simple class name.
   *
   * @param skillClass a {@link SkillBase} subclass
   * @throws IllegalArgumentException if {@code skillClass} is {@code null}. Instantiation failure
   *     is reported later, from the registered factory, as the same exception type.
   */
  public static void registerSkill(Class<? extends SkillBase> skillClass) {
    if (skillClass == null) {
      throw new IllegalArgumentException("skillClass is required");
    }
    // Derive the registration name. Java skills don't share a
    // canonical static accessor, so try a small list of candidates
    // before falling back to the simple class name.
    String name;
    try {
      // Many skills expose a static getName() / SKILL_NAME field
      try {
        java.lang.reflect.Field f = skillClass.getField("SKILL_NAME");
        Object v = f.get(null);
        name = v == null ? skillClass.getSimpleName() : v.toString();
      } catch (NoSuchFieldException e) {
        java.lang.reflect.Method m = skillClass.getMethod("getSkillName");
        Object v = m.invoke(null);
        name = v == null ? skillClass.getSimpleName() : v.toString();
      }
    } catch (Exception e) {
      // Fallback: lower-cased simple name
      name = skillClass.getSimpleName().toLowerCase(java.util.Locale.ROOT);
    }
    Supplier<SkillBase> factory =
        () -> {
          try {
            return skillClass.getDeclaredConstructor().newInstance();
          } catch (Exception ex) {
            throw new IllegalArgumentException(
                "skill class " + skillClass + " could not be instantiated", ex);
          }
        };
    SkillRegistry.register(name, factory);
  }

  /**
   * Add a directory to search for skills.
   *
   * <p>Delegates to the singleton {@link SkillRegistry} instance so third-party skill collections
   * can be registered by path. Subsequent calls accumulate (de-duplicated) into a shared external
   * paths list.
   *
   * @param path absolute or relative path to a directory containing skill subdirectories
   * @throws IllegalArgumentException when the path doesn't exist or isn't a directory
   */
  public static void addSkillDirectory(String path) {
    getSingletonRegistry().addSkillDirectory(path);
  }

  /**
   * Get complete schema for all available skills.
   *
   * <p>Returns a map keyed by skill name where each value carries that skill's metadata. Useful for
   * GUI configuration tools, API documentation, or programmatic skill discovery.
   *
   * <p>Every entry carries {@code name} and a {@code parameters} map, plus {@code description} and
   * {@code version} for skills that expose {@code getSkillDescription()} / {@code
   * getSkillVersion()}. The {@code parameters} map is currently empty for every skill; built-in
   * skills that expose {@code parameterSchema()} are the place to read a real schema from.
   *
   * @return map of skill name to schema metadata
   */
  public static Map<String, Map<String, Object>> listSkillsWithParams() {
    Map<String, Map<String, Object>> out = new LinkedHashMap<>();
    for (String name : SkillRegistry.list()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("name", name);
      entry.put("parameters", new HashMap<String, Object>());
      try {
        SkillBase skill = SkillRegistry.get(name);
        if (skill != null) {
          // Try to pull description/version off the instance
          try {
            java.lang.reflect.Method m = skill.getClass().getMethod("getSkillDescription");
            Object v = m.invoke(skill);
            if (v != null) entry.put("description", v.toString());
          } catch (NoSuchMethodException ignore) {
            /* no method */
          }
          try {
            java.lang.reflect.Method m = skill.getClass().getMethod("getSkillVersion");
            Object v = m.invoke(skill);
            if (v != null) entry.put("version", v.toString());
          } catch (NoSuchMethodException ignore) {
            /* no method */
          }
        }
      } catch (Exception e) {
        // Fall back to the minimal entry on reflection errors.
      }
      out.put(name, entry);
    }
    return out;
  }

  /**
   * List all registered skills as a flat list of metadata maps — the same entries {@link
   * #listSkillsWithParams()} returns, without the skill-name keys.
   *
   * @return one metadata map ({@code name} + optional {@code description}/{@code version}) per
   *     skill
   */
  public static List<Map<String, Object>> listSkills() {
    return new ArrayList<>(listSkillsWithParams().values());
  }

  /** Null-safe env fallback: returns {@code ""} when the variable is unset. */
  private static String orEmpty(String v) {
    return v != null ? v : "";
  }
}
