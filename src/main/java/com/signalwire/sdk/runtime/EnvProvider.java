package com.signalwire.sdk.runtime;

/**
 * Simple read-only view of process environment variables.
 *
 * <p>Extracted as an interface so tests can inject a deterministic map
 * instead of relying on actual OS environment variables (which Java
 * cannot mutate at runtime without native hackery).
 *
 * <p>The default implementation delegates to {@link System#getenv(String)}.
 */
@FunctionalInterface
public interface EnvProvider {

    /**
     * Return the value of the given environment variable, or {@code null} if
     * it is not set.
     *
     * @param name the environment variable name.
     * @return the value, or {@code null}.
     */
    String get(String name);

    /** Default provider backed by {@link System#getenv(String)}. */
    EnvProvider SYSTEM = System::getenv;

    /**
     * Return true iff the env var is set and non-empty.
     *
     * @param name the environment variable name.
     * @return true if the value is set and non-empty.
     */
    default boolean isSet(String name) {
        String v = get(name);
        return v != null && !v.isEmpty();
    }
}
