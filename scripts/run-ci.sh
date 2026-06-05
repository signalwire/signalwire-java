#!/usr/bin/env bash
# run-ci.sh — canonical local-and-CI gate runner for signalwire-java.
#
# Same script invoked locally (`bash scripts/run-ci.sh`) AND by the
# GitHub Actions workflow. No drift between local and CI behavior.
#
# Gates (in order, fail-fast):
#   1. ./gradlew --no-daemon test         — language test runner
#   2. signature regen                    — gradlew build (no test) + python adapter
#   3. drift gate                         — porting-sdk diff_port_signatures.py
#   4. surface-fresh gate                 — porting-sdk check_surface_freshness.py
#   5. no-cheat gate                      — porting-sdk audit_no_cheat_tests.py
#   6. emission gate                      — porting-sdk diff_port_emission.py
#
# The SURFACE-FRESH gate closes the Layer-B-not-gated hole: the drift gate
# only polices Layer A (port_signatures.json), so port_surface.json can
# silently rot when a public symbol is added but only the signature surface
# is regenerated. It regenerates port_surface.json in place via the Java
# surface enumerator (reusing the JAR built in gate 2 — though the surface
# enumerator parses source, not the JAR) and byte-compares it against the
# committed copy MODULO the volatile generated_from git-sha. Any real
# symbol/shape divergence fails loudly; the committed working copy is
# restored afterward so the gate is side-effect free.
#
# The Java adapter requires the SDK jar to be rebuilt before reflection
# can see new methods (per AUDIT_DISCIPLINE.md "Adapter rename tables").
# The emission gate runs the `emitCorpus` Gradle task (com.signalwire.sdk.tools.
# EmitCorpus) and byte-compares its FunctionResult serialisation against
# Python's to_dict() over the shared 81-entry corpus — closing the drift-0
# emission hole the signature/surface gates can't see (IDIOM_PASS_JOURNAL §4
# Tier-0). It rebuilds in gate 2, so the JAR/classes are current here.
#
# Each gate prints `[GATE-NAME] ... PASS` or `[GATE-NAME] ... FAIL: <reason>`
# Final line: `==> CI PASS` or `==> CI FAIL (gates: <list>)`.

set -u
set -o pipefail

PORT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT_NAME="signalwire-java"

resolve_porting_sdk() {
    if [ -n "${PORTING_SDK:-}" ] && [ -d "$PORTING_SDK/scripts" ]; then
        echo "$PORTING_SDK"
        return 0
    fi
    if [ -d "$PORT_ROOT/../porting-sdk/scripts" ]; then
        (cd "$PORT_ROOT/../porting-sdk" && pwd)
        return 0
    fi
    return 1
}

PORTING_SDK_DIR="$(resolve_porting_sdk)" || {
    echo "FATAL: porting-sdk not found, clone it adjacent to this repo" >&2
    echo "       (expected $PORT_ROOT/../porting-sdk or \$PORTING_SDK env var)" >&2
    exit 2
}

FAILED_GATES=""

run_gate() {
    local name="$1"; shift
    local description="$1"; shift
    local logfile
    logfile="$(mktemp)"
    "$@" >"$logfile" 2>&1
    local rc=$?
    if [ "$rc" -eq 0 ]; then
        echo "[$name] $description ... PASS"
        rm -f "$logfile"
        return 0
    fi
    echo "[$name] $description ... FAIL: exit $rc"
    sed 's/^/    /' "$logfile" | tail -40
    rm -f "$logfile"
    FAILED_GATES="$FAILED_GATES $name"
    return $rc
}

cd "$PORT_ROOT"

echo "==> running CI gates for $PORT_NAME (porting-sdk at $PORTING_SDK_DIR)"

# Gate 1: gradle test
run_gate "TEST" "./gradlew --no-daemon test" \
    ./gradlew --no-daemon test

# Gate 2: signature regen — must rebuild jar first so adapter reflection
# sees the latest source.
run_gate "SIGNATURES" "build jar + regenerate port_signatures.json" \
    bash -c "./gradlew --no-daemon build -x test && python3 scripts/enumerate_signatures.py"

# Gate 3: drift gate
run_gate "DRIFT" "diff_port_signatures vs python reference" \
    python3 "$PORTING_SDK_DIR/scripts/diff_port_signatures.py" \
        --reference "$PORTING_SDK_DIR/python_signatures.json" \
        --port-signatures "$PORT_ROOT/port_signatures.json" \
        --surface-omissions "$PORT_ROOT/PORT_OMISSIONS.md" \
        --surface-additions "$PORT_ROOT/PORT_ADDITIONS.md" \
        --omissions "$PORT_ROOT/PORT_SIGNATURE_OMISSIONS.md"

# Gate 4: surface-fresh — regenerate port_surface.json in place and confirm it
# still matches the committed copy (modulo the volatile generated_from sha).
# Closes the Layer-B-rot hole the signature/drift gates can't see. The surface
# enumerator writes to stdout, so we redirect it back over the file in place.
# We snapshot the committed copy (HEAD, falling back to a working-tree cp),
# regenerate, diff, then restore the working tree unconditionally so the gate
# leaves no residue regardless of pass/fail.
surface_fresh_gate() {
    local committed="/tmp/committed_surface_${PORT_NAME}.$$"
    git show HEAD:port_surface.json >"$committed" 2>/dev/null \
        || cp port_surface.json "$committed"
    # Regenerate in place (JAR already built in gate 2; enumerator parses src).
    python3 scripts/enumerate_surface.py >port_surface.json
    local regen_rc=$?
    if [ "$regen_rc" -ne 0 ]; then
        git checkout -- port_surface.json 2>/dev/null || true
        rm -f "$committed"
        echo "surface regen failed (exit $regen_rc)" >&2
        return "$regen_rc"
    fi
    python3 "$PORTING_SDK_DIR/scripts/check_surface_freshness.py" \
        --committed "$committed" --fresh port_surface.json
    local rc=$?
    git checkout -- port_surface.json 2>/dev/null || true
    rm -f "$committed"
    return $rc
}
run_gate "SURFACE-FRESH" "check_surface_freshness vs committed port_surface.json" \
    surface_fresh_gate

# Gate 5: no-cheat
run_gate "NO-CHEAT" "audit_no_cheat_tests" \
    python3 "$PORTING_SDK_DIR/scripts/audit_no_cheat_tests.py" --root "$PORT_ROOT"

# Gate 6: emission — byte-compare FunctionResult.toMap() vs Python's to_dict()
# across the shared 81-entry corpus (pure serialisation; no mocks/network).
# --port-repo keeps the gate self-contained regardless of the invoking cwd.
run_gate "EMISSION" "diff_port_emission vs python oracle" \
    python3 "$PORTING_SDK_DIR/scripts/diff_port_emission.py" \
        --port java --port-repo "$PORT_ROOT"

if [ -z "$FAILED_GATES" ]; then
    echo "==> CI PASS"
    exit 0
else
    echo "==> CI FAIL (gates:$FAILED_GATES )"
    exit 1
fi
