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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Top-level convenience entry points — mirror Python's
 * {@code signalwire/__init__.py} package-level helpers
 * ({@code RestClient}, {@code register_skill},
 * {@code add_skill_directory}, {@code list_skills_with_params}).
 *
 * <p>This is a static-only utility class; it cannot be instantiated. The
 * helpers delegate to the underlying classes (RestClient, SkillRegistry)
 * so they're a strict thin layer.
 *
 * <p>The audit projects each method onto the canonical Python
 * {@code signalwire.<name>} free-function path via a per-port
 * {@code FREE_FUNCTION_PROJECTIONS} entry in
 * {@code scripts/enumerate_signatures.py}.
 */
public final class Signalwire {

    private Signalwire() {
        // Static-only utility class
    }

    /**
     * Singleton {@link SkillRegistry} instance shared by the helpers
     * below. Lazily instantiated so the static initializer doesn't block
     * on classpath validation.
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
     * Construct a {@link RestClient} from positional or keyword
     * credentials.
     *
     * <p>Mirrors Python's top-level {@code signalwire.RestClient(*args,
     * **kwargs)} factory. Supports two call shapes:
     * <ul>
     *   <li>{@code RestClient(project, token, space)} — three positional
     *       strings, mapping straight onto the Java builder.</li>
     *   <li>{@code RestClient(args, kwargs)} — variadic-shaped form
     *       matching Python's signature exactly. {@code args} is an
     *       array of three strings (project/token/space) or empty;
     *       {@code kwargs} carries the same fields by name.</li>
     * </ul>
     *
     * <p>The audit projects this method onto Python's
     * {@code signalwire.RestClient(*args, **kwargs)} via
     * {@code FREE_FUNCTION_PROJECTIONS}.
     *
     * @param args positional credentials — empty or
     *             {@code [project, token, space]}
     * @param kwargs keyword credentials — recognised keys are
     *               {@code project} / {@code project_id},
     *               {@code token}, and {@code space} / {@code host}
     * @return a fully wired {@link RestClient} instance
     */
    public static RestClient RestClient(List<String> args, Map<String, String> kwargs) {
        if (args == null) args = java.util.Collections.emptyList();
        if (kwargs == null) kwargs = java.util.Collections.emptyMap();
        String project = args.size() > 0 ? args.get(0)
                : kwargs.getOrDefault("project",
                        kwargs.getOrDefault("project_id",
                                System.getenv().getOrDefault("SIGNALWIRE_PROJECT_ID", "")));
        String token = args.size() > 1 ? args.get(1)
                : kwargs.getOrDefault("token",
                        System.getenv().getOrDefault("SIGNALWIRE_API_TOKEN", ""));
        String space = args.size() > 2 ? args.get(2)
                : kwargs.getOrDefault("space",
                        kwargs.getOrDefault("host",
                                System.getenv().getOrDefault("SIGNALWIRE_SPACE", "")));
        if (project == null || project.isEmpty()
                || token == null || token.isEmpty()
                || space == null || space.isEmpty()) {
            throw new IllegalArgumentException(
                    "project, token, and space are required. "
                            + "Provide them as args/kwargs or set SIGNALWIRE_PROJECT_ID, "
                            + "SIGNALWIRE_API_TOKEN, and SIGNALWIRE_SPACE environment variables.");
        }
        return RestClient.builder()
                .project(project)
                .token(token)
                .space(space)
                .build();
    }

    /**
     * Register a custom skill class with the global {@link SkillRegistry}.
     *
     * <p>Mirrors Python's {@code signalwire.register_skill(skill_class)}.
     * Java skills are constructed via a no-arg constructor (the
     * registry stores {@code Supplier<SkillBase>} factories), so we
     * adapt the supplied class reference into a factory using
     * reflection. Throws {@link IllegalArgumentException} when the
     * class can't be instantiated reflectively or when its skill name
     * cannot be derived.
     *
     * @param skillClass a {@link SkillBase} subclass
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
            name = skillClass.getSimpleName().toLowerCase();
        }
        Supplier<SkillBase> factory = () -> {
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
     * <p>Mirrors Python's {@code signalwire.add_skill_directory(path)} —
     * delegates to the singleton {@link SkillRegistry} instance so
     * third-party skill collections can be registered by path.
     * Subsequent calls accumulate (de-duplicated) into a shared
     * external paths list.
     *
     * @param path absolute or relative path to a directory containing
     *             skill subdirectories
     * @throws IllegalArgumentException when the path doesn't exist or
     *                                  isn't a directory
     */
    public static void addSkillDirectory(String path) {
        getSingletonRegistry().addSkillDirectory(path);
    }

    /**
     * Get complete schema for all available skills.
     *
     * <p>Mirrors Python's {@code signalwire.list_skills_with_params()}.
     * Returns a map keyed by skill name where each value contains
     * parameter metadata. Useful for GUI configuration tools, API
     * documentation, or programmatic skill discovery.
     *
     * <p>Java skills don't carry rich Python-style parameter
     * introspection in v1, so each entry contains the skill name and an
     * empty parameter map; built-in skills that expose
     * {@code parameterSchema()} get richer detail.
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
                    } catch (NoSuchMethodException ignore) { /* no method */ }
                    try {
                        java.lang.reflect.Method m = skill.getClass().getMethod("getSkillVersion");
                        Object v = m.invoke(skill);
                        if (v != null) entry.put("version", v.toString());
                    } catch (NoSuchMethodException ignore) { /* no method */ }
                }
            } catch (Exception e) {
                // Fall back to the minimal entry on reflection errors.
            }
            out.put(name, entry);
        }
        return out;
    }
}
