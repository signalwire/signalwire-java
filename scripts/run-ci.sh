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
#   7. fmt gate                           — spotless google-java-format
#   8. lint gate                          — Error Prone + Checkstyle (zero findings)
#   9. doc-audit gate                     — porting-sdk audit_docs.py
#  10. surface-diff gate                  — porting-sdk diff_port_surface.py
#  11. skill-contract gate                — porting-sdk diff_skill_contracts.py
#   7. fmt gate                           — spotless (local: apply; CI: check)
#   8. lint gate                          — errorprone + checkstyle, zero findings
#   9. doc-audit gate                     — porting-sdk audit_docs.py
#  10. surface-diff gate                  — porting-sdk diff_port_surface.py
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

# Gate 4b: GEN-FRESH — the generated REST resource layer
# (src/main/java/com/signalwire/sdk/rest/namespaces/generated/*) is emitted by
# scripts/generate_rest.py from the porting-sdk REST specs and is DO-NOT-EDIT.
# This gate re-runs the generator in --check mode and fails if any on-disk
# generated file drifts from the generator output (a hand edit, a spec change
# not regenerated, or a leftover file). Mirrors SURFACE-FRESH for the generated
# code. The generator formats through google-java-format so its output is
# byte-identical to what the FMT gate (spotless) produces.
run_gate "GEN-FRESH" "generate_rest.py --check (generated REST files match specs)" \
    python3 scripts/generate_rest.py --check

# Gate 4c/4d/4e: GEN-FRESH for the read-side typed-payload generators (item D).
# Each is DO-NOT-EDIT and byte-compared to its generator output (via the shared
# google-java-format so on-disk == spotlessApply):
#   * SWML-verbs config types    (schema.json $defs → com.…swml.generated)
#   * RELAY protocol wire types  (relay-protocol/*.json → com.…relay.generated)
#   * SWAIG read-side payloads    (swaig-specs/*.yaml → com.…swaig.generated)
run_gate "GEN-FRESH-SWML" "generate_swml_verbs.py --check (SWML-verb types match schema.json)" \
    python3 scripts/generate_swml_verbs.py --check
run_gate "GEN-FRESH-RELAY" "generate_relay_protocol.py --check (RELAY types match relay-protocol)" \
    python3 scripts/generate_relay_protocol.py --check
run_gate "GEN-FRESH-SWAIG" "generate_swaig_payloads.py --check (SWAIG payloads match swaig-specs)" \
    python3 scripts/generate_swaig_payloads.py --check

# Gate 5: no-cheat
run_gate "NO-CHEAT" "audit_no_cheat_tests" \
    python3 "$PORTING_SDK_DIR/scripts/audit_no_cheat_tests.py" --root "$PORT_ROOT"

# Gate 5b: REST-COVERAGE — every canonical REST route the SDK implements must be
# exercised with BOTH a success (2xx) AND an error (4xx/5xx) response, on the
# correct on-the-wire path (parity). Measured by replaying the mock journal of a
# REST-suite run through porting-sdk's rest_coverage checker. Accepted gaps —
# routes with no SDK method, malformed canonical routes, mock-router collisions —
# are allowlisted: the shared baseline (porting-sdk/REST_COVERAGE_BASELINE.md) +
# this port's REST_COVERAGE_GAPS.md. A stale entry (route now actually covered)
# fails the gate. Self-contained: spins its own mock, runs the rest test classes
# serially against it (MOCK_SIGNALWIRE_PORT so all traffic lands in one journal),
# then checks that journal. Same shape as python's gate.
# Pick a free TCP port on 127.0.0.1 (bind :0, read the OS-assigned port,
# release). Never reuse a hardcoded port — a leftover or concurrent mock
# squatting a fixed port otherwise makes the gate hang on its health poll.
pick_free_port() {
    python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()'
}
rest_coverage_gate() {
    local port
    port="$(pick_free_port)" || { echo "could not allocate a free port" >&2; return 1; }
    local mock_pkg_parent="$PORTING_SDK_DIR/test_harness/mock_signalwire"
    export PYTHONPATH="$mock_pkg_parent${PYTHONPATH:+:$PYTHONPATH}"
    python3 -m mock_signalwire --host 127.0.0.1 --port "$port" --log-level error \
        >/tmp/rest_cov_mock.$$.log 2>&1 &
    local mock_pid=$!
    # shellcheck disable=SC2064
    trap "kill $mock_pid 2>/dev/null" RETURN
    # Fail LOUD if the mock dies mid-startup or never becomes healthy — never hang.
    local i ready=0
    for i in $(seq 1 60); do
        if ! kill -0 "$mock_pid" 2>/dev/null; then
            echo "mock_signalwire died on port $port — log:" >&2
            cat "/tmp/rest_cov_mock.$$.log" >&2
            return 1
        fi
        if python3 -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$port/__mock__/health',timeout=1)" 2>/dev/null; then
            ready=1
            break
        fi
        sleep 0.5
    done
    if [ "$ready" -ne 1 ]; then
        echo "mock_signalwire on port $port not healthy within 30s" >&2
        return 1
    fi
    python3 -c "import urllib.request; urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:$port/__mock__/journal/reset',method='POST'),timeout=5).read()"
    MOCK_SIGNALWIRE_PORT="$port" ./gradlew --no-daemon test --tests "com.signalwire.sdk.rest.*" --rerun-tasks -q || return 1
    python3 -m mock_signalwire.rest_coverage \
        --mock-url "http://127.0.0.1:$port" \
        --spec-root "$PORTING_SDK_DIR/rest-apis" \
        --allowlist "$PORTING_SDK_DIR/REST_COVERAGE_BASELINE.md" \
        --allowlist "$PORT_ROOT/REST_COVERAGE_GAPS.md" \
        --gap-baseline "$PORTING_SDK_DIR/REST_COVERAGE_GAP_BASELINE.md"
}
run_gate "REST-COVERAGE" "every implemented REST route covered success+error (parity + allowlist)" \
    rest_coverage_gate

# Gate 5c: SPEC-PARITY — the routes the SDK actually IMPLEMENTS must equal the
# canonical spec route set, modulo porting-sdk/SPEC_IMPLEMENTATION_GAPS.md. This
# is the spec-first guard REST-COVERAGE can't give: REST-COVERAGE only proves
# *tested* routes match the spec, so a route the SDK implements that the spec
# doesn't define (or a canonical route the SDK never implemented) would slip past
# it. Set B is built by the routeRegistry Gradle task — it drives the live
# RestClient through a recording HttpClient (records (method, path), returns an
# empty map, no network) and reflects over every namespace/sub-resource route
# method, invoking each with sentinel args, so it sees every dispatched route
# whether or not it's tested (not an AST scrape, not the journal). The shared
# porting-sdk diff consumes that JSON via --registry-json. The registry prints
# ONLY JSON to stdout (the SDK logger writes to stderr), captured to a temp file.
# Mirrors go's Gate 5c (cmd/route-registry) and ts's route-registry.ts.
spec_parity_gate() {
    local registry
    registry="$(mktemp)"
    # -q so only the registry JSON reaches stdout; 2>/dev/null so any Gradle/SDK
    # stderr can't pollute it. The task exits non-zero if Set B is incomplete.
    if ! ./gradlew --no-daemon -q routeRegistry >"$registry" 2>/dev/null; then
        rm -f "$registry"
        return 1
    fi
    python3 "$PORTING_SDK_DIR/scripts/diff_spec_implementation.py" \
        --registry-json "$registry" \
        --gaps "$PORTING_SDK_DIR/SPEC_IMPLEMENTATION_GAPS.md"
    local rc=$?
    rm -f "$registry"
    return $rc
}
run_gate "SPEC-PARITY" "implemented routes == canonical spec (modulo SPEC_IMPLEMENTATION_GAPS.md)" \
    spec_parity_gate

# Gate 6: emission — byte-compare FunctionResult.toMap() vs Python's to_dict()
# across the shared 81-entry corpus (pure serialisation; no mocks/network).
# --port-repo keeps the gate self-contained regardless of the invoking cwd.
run_gate "EMISSION" "diff_port_emission vs python oracle" \
    python3 "$PORTING_SDK_DIR/scripts/diff_port_emission.py" \
        --port java --port-repo "$PORT_ROOT"

# Gate 6b: SWAIG-COVERAGE — every engine response action in the vendored
# swaig-specs/swaig-response.yaml must be emittable by this port's FunctionResult
# (SWAIG_PIPELINE §5). The shared checker reads the action vocabulary from the spec
# and diffs it against what src/main/java/.../swaig/FunctionResult.java can put on
# the wire (addAction("key", …) + the actions.add(swmlAction) form whose
# swmlAction.put("transfer", …) top-level key is the action); a non-allowlisted gap
# fails. Signed-off gaps live in porting-sdk/SWAIG_COVERAGE_ALLOWLIST.md
# (back_to_back_functions, user_event). The SWAIG analogue of REST-COVERAGE; same
# gate go/TS run, pointed at the Java FunctionResult source.
run_gate "SWAIG-COVERAGE" "every engine SWAIG action emittable (modulo allowlist)" \
    python3 "$PORTING_SDK_DIR/scripts/swaig_coverage.py" --check \
        --emission "$PORT_ROOT/src/main/java/com/signalwire/sdk/swaig/FunctionResult.java"

# Gate 7: FMT — the language format gate (java: Spotless + google-java-format).
# google-java-format is the canonical Java formatter (analogous to gofmt/
# rustfmt) — no style to bikeshed. Source-style only and proven surface/
# emission-neutral (a reformat leaves port_surface.json byte-identical and
# EMISSION 81/81 — verified during the burndown). Mirrors the go/ruby fmt_gate:
#   * LOCAL ($CI unset)  → `spotlessApply`: reformats your working tree in place
#     so you never hand-run it; notes if it changed files.
#   * CI ($CI=true)      → `spotlessCheck` (read-only): FAILS if any unformatted
#     source reached CI.
fmt_gate() {
    if [ -n "${CI:-}" ]; then
        ./gradlew --no-daemon -q spotlessCheck
    else
        ./gradlew --no-daemon -q spotlessApply
        if ! git diff --quiet 2>/dev/null; then
            echo "    (FMT auto-applied formatting to your working tree — review & stage)"
        fi
        # A residual issue spotlessApply can't fix must still fail the gate.
        ./gradlew --no-daemon -q spotlessCheck
    fi
}
run_gate "FMT" "spotless google-java-format (local: apply; CI: check)" fmt_gate

# Gate 8: LINT — the language lint gate (java), two blocking layers burned to
# zero: Error Prone (compile-time bug patterns, warnings-as-errors) + Checkstyle
# (config/checkstyle/checkstyle.xml). A check is turned off ONLY when obeying it
# would force an API/contract change or police LLM-irrelevant javadoc prose —
# never to hide a wire/type-shape issue; every OFF carries a one-line rationale
# at its config site. The `build -x test` compile drives Error Prone; the
# checkstyle{Main,Test} tasks drive Checkstyle. Mirrors the go vet+golangci /
# ruby rubocop blocking-lint gate.
run_gate "LINT" "errorprone (warnings-as-errors) + checkstyle, zero findings" \
    ./gradlew --no-daemon -q clean build -x test checkstyleMain checkstyleTest

# Gate 9: DOC-AUDIT — every method/class referenced in docs/ + examples/ fenced
# code blocks must resolve to a real symbol in the port surface (catches
# phantom-API doc promises). Uses the committed port_surface.json (the
# SURFACE-FRESH gate above already proved it is fresh) + DOC_AUDIT_IGNORE.md for
# intentional non-symbol references.
run_gate "DOC-AUDIT" "audit_docs vs port_surface.json" \
    python3 "$PORTING_SDK_DIR/scripts/audit_docs.py" \
        --root "$PORT_ROOT" \
        --surface "$PORT_ROOT/port_surface.json" \
        --ignore "$PORT_ROOT/DOC_AUDIT_IGNORE.md"

# Gate 10: SURFACE-DIFF — diff the port's public surface against the Python
# reference (omissions + additions). The signature DRIFT gate (Layer A) checks
# method *signatures*; this checks surface *membership* — public symbols the
# port has that Python doesn't and vice-versa. Regenerate the surface in place
# (the JAR is built in gate 2 SIGNATURES and again in the LINT gate above; the
# enumerator parses source, so it is current), diff, then restore the committed
# copy unconditionally so the gate is side-effect free.
surface_diff_gate() {
    local committed="/tmp/committed_surface_diff_${PORT_NAME}.$$"
    git show HEAD:port_surface.json >"$committed" 2>/dev/null \
        || cp port_surface.json "$committed"
    python3 scripts/enumerate_surface.py >port_surface.json
    local regen_rc=$?
    if [ "$regen_rc" -ne 0 ]; then
        git checkout -- port_surface.json 2>/dev/null || true
        rm -f "$committed"
        echo "surface regen failed (exit $regen_rc)" >&2
        return "$regen_rc"
    fi
    python3 "$PORTING_SDK_DIR/scripts/diff_port_surface.py" \
        --reference "$PORTING_SDK_DIR/python_surface.json" \
        --port-surface port_surface.json \
        --omissions "$PORT_ROOT/PORT_OMISSIONS.md" \
        --additions "$PORT_ROOT/PORT_ADDITIONS.md"
    local rc=$?
    git checkout -- port_surface.json 2>/dev/null || true
    rm -f "$committed"
    return $rc
}
run_gate "SURFACE-DIFF" "diff_port_surface vs python reference" \
    surface_diff_gate

# Gate 11: SKILL-CONTRACT — the surface/drift/emission gates see signatures +
# symbol names + FunctionResult.toMap(); NONE sees a built-in skill's SWAIG tool
# contract ({name, parameters, required, enum} each skill registers). This differ
# closes that gap: it builds the Python oracle by instantiating each covered
# reference skill, runs the Java skill-dump program (the `emitSkills` Gradle task
# → com.signalwire.sdk.tools.EmitSkills, which reads the SAME shared corpus), and
# structurally compares the two. DESCRIPTIONS + implementation (handler vs
# DataMap) are not compared — only name/param-name/param-type/enum/required.
# Mirrors the go/ruby SKILL-CONTRACT gate. Same prereqs as EMISSION (signalwire-
# python adjacent; no network); the JAR built in gate 2 is current here.
run_gate "SKILL-CONTRACT" "diff_skill_contracts vs python reference" \
    python3 "$PORTING_SDK_DIR/scripts/diff_skill_contracts.py" \
        --dump-cmd "./gradlew --no-daemon -q emitSkills" \
        --port-repo "$PORT_ROOT"

# Gate 14: SWAIG-CLI — lightweight shared swaig-test mini-contract (NOT python
# parity; python's in-process simulator surface is reference-only). Black-box:
# invokes `bin/swaig-test --help` + golden invocations and asserts the shared
# verbs are documented, an unknown --simulate-serverless platform errors (no
# silent fallback), and no-action errors (the cross-port majority default). Java
# accepts --simulate-serverless (lambda only), so --has-serverless applies; the
# probe passes a dummy agent-class positional since platform validation happens
# before the class is resolved. The SDK JAR built in gate 2 (SIGNATURES) is what
# bin/swaig-test puts on the classpath.
run_gate "SWAIG-CLI" "swaig-test shared mini-contract (verbs/serverless-reject/default-action)" \
    python3 "$PORTING_SDK_DIR/scripts/audit_swaig_cli_contract.py" \
        --port java \
        --cmd "bash $PORT_ROOT/bin/swaig-test" \
        --require-url-model \
        --default-action-argv='--url|http://user:pass@127.0.0.1:1/' \
        --has-serverless \
        --serverless-argv='DummyAgentClass|--simulate-serverless|bogus-platform-xyz|--dump-swml'

if [ -z "$FAILED_GATES" ]; then
    echo "==> CI PASS"
    exit 0
else
    echo "==> CI FAIL (gates:$FAILED_GATES )"
    exit 1
fi
