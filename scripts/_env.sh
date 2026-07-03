#!/usr/bin/env bash
# _env.sh — shared tool-environment bootstrap for signalwire-java's FMT + LINT +
# TEST scripts (and run-ci.sh). Sourced, never executed. Holds the Gradle-wrapper
# + JDK bootstrap in ONE place so scripts/run-format.sh, scripts/run-lint.sh,
# scripts/run-tests.sh, and scripts/run-ci.sh all resolve the toolchain
# identically no matter the caller's CWD or shell setup (see
# porting-sdk/RUN_LINT_FORMAT_SPEC.md).
#
# Contract: after sourcing this file,
#   * $REPO_ROOT   — absolute path to the repo root (resolved from THIS file's
#                    own location, so it is correct from any CWD).
#   * $GRADLEW     — absolute path to the Gradle WRAPPER (./gradlew), resolved
#                    relative to $REPO_ROOT, NOT the caller's CWD.
#   * $JAVA_HOME   — exported and pointing at a resolvable JDK (already-set value
#                    honoured; otherwise best-effort discovery). `java` is on PATH.
#   * `sw_gradle …` — runs the wrapper from $REPO_ROOT with --no-daemon, having
#                    verified the wrapper + JDK are present. Fails loud with a
#                    clear install hint if either is missing.
#
# Per-port tools (RUN_LINT_FORMAT_SPEC.md): FMT = gradle spotlessApply /
# spotlessCheck (google-java-format); LINT = errorprone (warnings-as-errors) +
# checkstyle; TEST = gradle test.

# Resolve the repo root from this script's OWN path (CWD-independent). This file
# lives at <repo>/scripts/_env.sh, so the repo root is its parent's parent.
_SW_ENV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$_SW_ENV_DIR")"
export REPO_ROOT

# The Gradle WRAPPER, resolved relative to the repo root (never the caller CWD).
# Using the wrapper (not a stray global `gradle`) pins the Gradle version from
# gradle/wrapper/gradle-wrapper.properties.
GRADLEW="$REPO_ROOT/gradlew"
export GRADLEW

# Ensure JAVA_HOME resolves to a usable JDK. Honour an already-set JAVA_HOME; else
# try a small set of common locations. Export it and put its bin on PATH so the
# wrapper (which respects JAVA_HOME) picks the right JDK regardless of the shell.
_sw_resolve_java_home() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
        return 0
    fi
    local candidate
    for candidate in \
        /opt/homebrew/opt/openjdk@21 \
        /usr/local/opt/openjdk@21 \
        /home/devuser/jdk-21 \
        /usr/lib/jvm/java-21-openjdk \
        /usr/lib/jvm/java-21-openjdk-amd64 \
        /Library/Java/JavaVirtualMachines/*/Contents/Home; do
        if [ -x "$candidate/bin/java" ]; then
            JAVA_HOME="$candidate"
            export JAVA_HOME
            return 0
        fi
    done
    # macOS java_home helper as a last resort.
    if [ -x /usr/libexec/java_home ]; then
        local jh
        if jh="$(/usr/libexec/java_home -v 21 2>/dev/null)" && [ -x "$jh/bin/java" ]; then
            JAVA_HOME="$jh"
            export JAVA_HOME
            return 0
        fi
    fi
    # Fall back to whatever `java` is on PATH (derive JAVA_HOME from it).
    if command -v java >/dev/null 2>&1; then
        return 0
    fi
    return 1
}

# _sw_bootstrap_gradle — verify the wrapper + JDK are present; fail LOUD otherwise.
_sw_bootstrap_gradle() {
    if [ ! -x "$GRADLEW" ]; then
        echo "FATAL: Gradle wrapper not found or not executable at: $GRADLEW" >&2
        echo "       The repo ships ./gradlew; ensure it exists and: chmod +x gradlew" >&2
        return 1
    fi
    if ! _sw_resolve_java_home; then
        echo "FATAL: no JDK found (JAVA_HOME unset and no java on PATH)." >&2
        echo "       Install a JDK 21+ and set JAVA_HOME, e.g.:" >&2
        echo "         brew install openjdk@21   # macOS" >&2
        echo "         export JAVA_HOME=/opt/homebrew/opt/openjdk@21" >&2
        return 1
    fi
    if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
        case ":$PATH:" in
            *":${JAVA_HOME}/bin:"*) : ;;
            *) PATH="${JAVA_HOME}/bin:$PATH"; export PATH ;;
        esac
    fi
    return 0
}

# sw_gradle <gradle-args…> — run the wrapper from the repo root with --no-daemon.
# Bootstraps (verifies wrapper + JDK) on every call; fails loud on a missing tool.
sw_gradle() {
    _sw_bootstrap_gradle || return 1
    (cd "$REPO_ROOT" && "$GRADLEW" --no-daemon "$@")
}
