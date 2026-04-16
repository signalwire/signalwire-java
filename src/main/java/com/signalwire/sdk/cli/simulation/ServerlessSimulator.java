package com.signalwire.sdk.cli.simulation;

import com.signalwire.sdk.runtime.EnvProvider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Build a layered {@link EnvProvider} that overlays simulated serverless
 * environment values on top of the real process environment.
 *
 * <p>Java cannot mutate {@link System#getenv()} at runtime (the map it
 * returns is an immutable snapshot of the OS env). To implement the
 * {@code swaig-test --simulate-serverless} flag in the same spirit as
 * Python's {@code mock_env.py}, we therefore cannot "set env vars then
 * clean up" — we instead compose an injectable {@link EnvProvider} and
 * thread it through every code path in the SDK that reads env vars.
 *
 * <p>Accepts only the platforms the port actually implements. Ports that
 * add CGI / GCF / Azure support later should extend
 * {@link #presetFor(Platform)}.
 *
 * <p>Mirrors the clear-and-warn semantics of Python's
 * {@code _clear_conflicting_env}: {@code SWML_PROXY_URL_BASE} is masked
 * in the simulated view so that platform-specific URL generation is
 * actually exercised. If the caller's real OS env has
 * {@code SWML_PROXY_URL_BASE} set, the simulator does <b>not</b> mutate
 * the real env — it simply returns {@code null} for that key through the
 * layered {@link EnvProvider}. Callers that want to surface the mismatch
 * to the user can check {@link #proxyUrlBaseMaskedFromRealEnv()}.
 */
public final class ServerlessSimulator {

    /** Platforms the Java port supports simulating today. */
    public enum Platform {
        /** AWS Lambda. */
        LAMBDA
    }

    /**
     * Canonical env keys that {@code SWML_PROXY_URL_BASE}-style URL
     * overrides live under. Always masked during simulation.
     */
    private static final List<String> CLEARED_KEYS =
            List.of("SWML_PROXY_URL_BASE");

    /**
     * Env keys belonging to other platforms' presets. Masked during
     * simulation so a stray {@code FUNCTION_TARGET} doesn't flip mode
     * detection from {@code LAMBDA} to {@code GOOGLE_CLOUD_FUNCTION}.
     */
    private static final List<String> OTHER_PLATFORM_KEYS = List.of(
            "GATEWAY_INTERFACE",
            "FUNCTION_TARGET",
            "K_SERVICE",
            "GOOGLE_CLOUD_PROJECT",
            "AZURE_FUNCTIONS_ENVIRONMENT",
            "FUNCTIONS_WORKER_RUNTIME",
            "AzureWebJobsStorage");

    private final Platform platform;
    private final EnvProvider realEnv;
    private final Map<String, String> simulated;
    private final Set<String> masked;

    /**
     * Parse the CLI-string form of a platform. Accepts only values this
     * port supports; unknown strings throw {@link IllegalArgumentException}
     * so the CLI can surface a clear error instead of silently falling
     * back.
     *
     * @param s user-supplied platform string.
     * @return the matching {@link Platform}.
     * @throws IllegalArgumentException for unsupported platforms.
     */
    public static Platform parsePlatform(String s) {
        if (s == null) {
            throw new IllegalArgumentException("--simulate-serverless requires a platform name");
        }
        String normalised = s.trim().toLowerCase();
        if ("lambda".equals(normalised)) {
            return Platform.LAMBDA;
        }
        throw new IllegalArgumentException(
                "Unsupported platform for --simulate-serverless: '" + s + "'. "
                        + "This Java port only supports: lambda. "
                        + "(gcf / azure / cgi are not yet implemented — no silent fallback.)");
    }

    /**
     * Build a simulator for the given platform using the real process
     * environment as the fallback layer.
     *
     * @param platform platform to simulate.
     */
    public ServerlessSimulator(Platform platform) {
        this(platform, EnvProvider.SYSTEM, Collections.emptyMap());
    }

    /**
     * Build a simulator with explicit env + overrides. The {@code overrides}
     * map supplies the simulated values (e.g. {@code AWS_LAMBDA_FUNCTION_NAME}).
     * If it omits a key the {@link #presetFor(Platform) preset} supplies,
     * the preset's default is used.
     *
     * @param platform platform to simulate.
     * @param realEnv fallback env provider (usually {@link EnvProvider#SYSTEM}).
     * @param overrides user-supplied overrides on top of the platform preset.
     */
    public ServerlessSimulator(Platform platform, EnvProvider realEnv,
                               Map<String, String> overrides) {
        if (platform == null) throw new IllegalArgumentException("platform must not be null");
        if (realEnv == null) throw new IllegalArgumentException("realEnv must not be null");
        this.platform = platform;
        this.realEnv = realEnv;
        this.simulated = new LinkedHashMap<>();
        this.simulated.putAll(presetFor(platform));
        if (overrides != null) this.simulated.putAll(overrides);

        Set<String> m = new LinkedHashSet<>();
        m.addAll(CLEARED_KEYS);
        for (String k : OTHER_PLATFORM_KEYS) {
            // If the preset itself sets a key, don't mask it.
            if (!this.simulated.containsKey(k)) {
                m.add(k);
            }
        }
        this.masked = Collections.unmodifiableSet(m);
    }

    /**
     * Default env-var preset for a platform, mirroring Python's
     * {@code ServerlessSimulator.PLATFORM_PRESETS}.
     *
     * @param platform platform.
     * @return the preset map (never null).
     */
    public static Map<String, String> presetFor(Platform platform) {
        Map<String, String> m = new LinkedHashMap<>();
        if (platform == Platform.LAMBDA) {
            m.put("AWS_LAMBDA_FUNCTION_NAME", "test-agent-function");
            m.put("AWS_LAMBDA_FUNCTION_URL",
                    "https://abc123.lambda-url.us-east-1.on.aws/");
            m.put("AWS_REGION", "us-east-1");
            m.put("LAMBDA_TASK_ROOT", "/var/task");
            m.put("_HANDLER", "lambda_function.lambda_handler");
        }
        return m;
    }

    /**
     * @return the simulated platform.
     */
    public Platform getPlatform() {
        return platform;
    }

    /**
     * @return the map of simulated values layered on top of the real env.
     *     The returned map is a defensive copy.
     */
    public Map<String, String> getSimulatedEnv() {
        return new LinkedHashMap<>(simulated);
    }

    /**
     * @return an immutable view of the keys that are masked (returned as
     *     {@code null}) regardless of what the real env has for them.
     */
    public Set<String> getMaskedKeys() {
        return masked;
    }

    /**
     * @return {@code true} if the real process env has
     *     {@code SWML_PROXY_URL_BASE} set — i.e. the simulated view is
     *     hiding a real value the user might not have intended. The CLI
     *     uses this to print a warning that mirrors Python's behaviour.
     */
    public boolean proxyUrlBaseMaskedFromRealEnv() {
        String real = realEnv.get("SWML_PROXY_URL_BASE");
        return real != null && !real.isEmpty();
    }

    /**
     * Build the layered {@link EnvProvider}. Precedence is:
     * <ol>
     *   <li>{@link #getMaskedKeys() masked keys} — always return {@code null}.</li>
     *   <li>{@link #getSimulatedEnv() simulated values} — overlaid on the real env.</li>
     *   <li>Real env — everything else passes through.</li>
     * </ol>
     *
     * @return layered env provider.
     */
    public EnvProvider buildEnvProvider() {
        final Map<String, String> sim = new LinkedHashMap<>(simulated);
        final Set<String> mk = masked;
        final EnvProvider real = realEnv;
        return name -> {
            if (mk.contains(name)) return null;
            if (sim.containsKey(name)) return sim.get(name);
            return real.get(name);
        };
    }
}
