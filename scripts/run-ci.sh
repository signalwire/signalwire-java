#!/usr/bin/env bash
# run-ci.sh — canonical local-and-CI gate runner for signalwire-java.
#
# Same script invoked locally (`bash scripts/run-ci.sh`) AND by the
# GitHub Actions workflow. No drift between local and CI behavior.
#
# The FMT / LINT / TEST gates are the canonical scripts/run-format.sh,
# scripts/run-lint.sh, scripts/run-tests.sh (each self-bootstraps the Gradle
# wrapper + JAVA_HOME via scripts/_env.sh and runs from any CWD).
#
# GATE SCHEDULING (porting-sdk/scripts/gate_scheduler.sh — CI_PERF S1 + S2):
#   Gates run CONCURRENTLY up to a cap (SW_CI_JOBS, default nproc), scheduled by
#   their DATA dependencies:
#     * S2 concurrent wave: the pure-Python side-effect-free gates (all GEN-FRESH*,
#       DRIFT, NO-CHEAT, SWAIG-COVERAGE, DOC-AUDIT, SURFACE-DIFF) overlap — they
#       share no mutable state and don't touch Gradle.
#     * S1 fail-fast: heavy gates are deferred behind the cheap wave, so a trivial
#       cheap-gate failure surfaces in seconds; --fail-fast aborts before TEST.
#   HARD ordering / mutual exclusion (the ONLY pre-emptive serialization):
#     * DRIFT reads port_signatures.json that SIGNATURES writes → deps=SIGNATURES.
#     * SURFACE-FRESH regenerates port_surface.json in place (and restores it);
#       SURFACE-DIFF regenerates it too, DOC-AUDIT reads it → res=surface (mutex).
#     * res=gradle: every gate that shells out to ./gradlew (TEST, SIGNATURES,
#       REST-COVERAGE, SPEC-PARITY, EMISSION, SKILL-CONTRACT, FMT, LINT) runs
#       MUTUALLY EXCLUSIVE. This is NOT a predicted-cost throttle — concurrent
#       Gradle invocations against one project genuinely contend on the build dir
#       + daemon (a real breakage, per the CI_PERF correction "isolate what
#       ACTUALLY breaks"), so they are serialized. The pure-Python cheap wave still
#       overlaps freely with the single in-flight Gradle gate.
#   Per-gate PASS/FAIL + the FAILED_GATES tally preserved exactly; each gate's output
#   captured + replayed atomically.
#
# The Java adapter requires the SDK jar rebuilt before reflection sees new methods,
# so SIGNATURES does `./gradlew build -x test && enumerate`. EMISSION/SPEC-PARITY/
# SKILL-CONTRACT run their own Gradle tasks (which rebuild incrementally as needed).
#
# Flags:
#   --fail-fast   stop launching new gates at the first failure (local dev loop).

set -u
set -o pipefail

PORT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$PORT_ROOT/.sw-tmp"  # repo-local CI scratch (never /tmp)
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

# Source the shared tool-env so the raw `./gradlew` gate lines honour the SAME
# daemon policy as the run-{format,lint,tests}.sh scripts: $GRADLE_DAEMON_FLAG is
# `--no-daemon` in CI ($CI set) and EMPTY locally (warm daemon reuses one JVM).
# These EXPORTED vars are inherited by every scheduler worker subshell.
# shellcheck source=scripts/_env.sh
source "$PORT_ROOT/scripts/_env.sh"

# shellcheck source=/dev/null
source "$PORTING_SDK_DIR/scripts/gate_scheduler.sh"

cd "$PORT_ROOT"

# Gate-enforcement plan (Part D): java's red list is burned, so its widened
# (wave-A) gate findings BLOCK rather than report-only. Default OFF here; a caller
# may still set SW_WAVE_A_REPORT_ONLY=1 to inspect the report-only view.
export SW_WAVE_A_REPORT_ONLY="${SW_WAVE_A_REPORT_ONLY:-0}"

echo "==> running CI gates for $PORT_NAME (porting-sdk at $PORTING_SDK_DIR)"
echo "==> wave-A gate findings are ${SW_WAVE_A_REPORT_ONLY:+BLOCKING (SW_WAVE_A_REPORT_ONLY=$SW_WAVE_A_REPORT_ONLY)}"

pick_free_port() {
    python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()'
}

# SIGNATURES — rebuild the jar first so adapter reflection sees the latest source,
# then regenerate port_signatures.json.
#
# --no-build-cache is LOAD-BEARING here (same reason run-lint.sh forces it): this
# build must MATERIALIZE build/classes + build/libs/*.jar on disk, because both the
# enumerator (SignatureDump over the jar) AND the downstream SWAIG-CLI / DOC-CLI
# gates invoke `java -cp <jar> com.signalwire.sdk.cli.SwaigTest` against that jar.
# With the cross-run Gradle build cache on (org.gradle.caching=true globally +
# --build-cache), gradle/actions/setup-gradle restores a build-cache from a prior
# run; a compileJava entry saved incomplete (a cancelled/mid-pack run) is a HARD
# failure on the next hit — "Failed to load cache entry ...: Could not load from
# local cache: .../CallingLeaveConferenceParams.class (No such file or directory)"
# — so compileJava fails, no jar is produced, and every SwaigTest invocation then
# dies with ClassNotFoundException (the nightly SIGNATURES + SWAIG-CLI red).
# --no-build-cache forces compileJava to actually execute, reliably writing the
# jar; the daemon + Gradle up-to-date checks still apply. Command-line
# --no-build-cache wins over both --build-cache and the gradle.properties setting.
signatures_gate() {
    # shellcheck disable=SC2086
    ./gradlew $GRADLE_DAEMON_FLAG --no-build-cache build -x test && python3 scripts/enumerate_signatures.py
}

# SURFACE-FRESH — regenerate port_surface.json in place (enumerator parses src, not
# the JAR), byte-compare vs the committed copy modulo the generated_from git-sha,
# restore unconditionally.
surface_fresh_gate() {
    local committed="$PORT_ROOT/.sw-tmp/committed_surface_${PORT_NAME}.$$"
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
    python3 "$PORTING_SDK_DIR/scripts/check_surface_freshness.py" \
        --committed "$committed" --fresh port_surface.json
    local rc=$?
    git checkout -- port_surface.json 2>/dev/null || true
    rm -f "$committed"
    return $rc
}

# REST-COVERAGE — spins its own mock, runs the rest test classes serially into one
# journal, then checks it.
rest_coverage_gate() {
    local port
    port="$(pick_free_port)" || { echo "could not allocate a free port" >&2; return 1; }
    local mock_pkg_parent="$PORTING_SDK_DIR/test_harness/mock_signalwire"
    export PYTHONPATH="$mock_pkg_parent${PYTHONPATH:+:$PYTHONPATH}"
    python3 -m mock_signalwire --host 127.0.0.1 --port "$port" --log-level error \
        >"$PORT_ROOT/.sw-tmp/rest_cov_mock.$$.log" 2>&1 &
    local mock_pid=$!
    # shellcheck disable=SC2064
    trap "kill $mock_pid 2>/dev/null" RETURN
    local i ready=0
    for i in $(seq 1 60); do
        if ! kill -0 "$mock_pid" 2>/dev/null; then
            echo "mock_signalwire died on port $port — log:" >&2
            cat "$PORT_ROOT/.sw-tmp/rest_cov_mock.$$.log" >&2
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
    # shellcheck disable=SC2086
    MOCK_SIGNALWIRE_PORT="$port" ./gradlew $GRADLE_DAEMON_FLAG --build-cache test --tests "com.signalwire.sdk.rest.*" --rerun-tasks -q || return 1
    python3 -m mock_signalwire.rest_coverage \
        --mock-url "http://127.0.0.1:$port" \
        --spec-root "$PORTING_SDK_DIR/rest-apis" \
        --allowlist "$PORTING_SDK_DIR/REST_COVERAGE_BASELINE.md" \
        --allowlist "$PORT_ROOT/REST_COVERAGE_GAPS.md" \
        --gap-baseline "$PORTING_SDK_DIR/REST_COVERAGE_GAP_BASELINE.md"
}

# SPEC-PARITY — routeRegistry Gradle task builds Set B; diff vs canonical spec.
spec_parity_gate() {
    local registry
    registry="$(mktemp)"
    # -q so only the registry JSON reaches stdout; 2>/dev/null so any Gradle/SDK
    # stderr can't pollute it. The task exits non-zero if Set B is incomplete.
    # shellcheck disable=SC2086
    if ! ./gradlew $GRADLE_DAEMON_FLAG --build-cache -q routeRegistry >"$registry" 2>/dev/null; then
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

# ROUTE-COLLISION — the routeRegistry Gradle task supplies the operation→(method,
# path) map; the gate cross-references it with the surface to find split routes /
# duplicated CRUD bases / consumerless generated types. Java HAS a registry (same
# task SPEC-PARITY uses), so the gate runs with --registry-json. Approved
# allowlist entries (ROUTE_COLLISION_ALLOW.md) are honored by the gate.
route_collision_gate() {
    local registry
    registry="$(mktemp)"
    # shellcheck disable=SC2086
    if ! ./gradlew $GRADLE_DAEMON_FLAG --build-cache -q routeRegistry >"$registry" 2>/dev/null; then
        rm -f "$registry"
        return 1
    fi
    python3 "$PORTING_SDK_DIR/scripts/route_collision.py" \
        --port java --repo . \
        --registry-json "$registry"
    local rc=$?
    rm -f "$registry"
    return $rc
}

# SURFACE-DIFF — regenerate the surface in place, diff vs Python, restore.
surface_diff_gate() {
    local committed="$PORT_ROOT/.sw-tmp/committed_surface_diff_${PORT_NAME}.$$"
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

emission_gate() {
    # shellcheck disable=SC2086
    python3 "$PORTING_SDK_DIR/scripts/diff_port_emission.py" \
        --port java --port-repo "$PORT_ROOT" \
        --dump-cmd "./gradlew $GRADLE_DAEMON_FLAG --build-cache -q emitCorpus"
}

skill_contract_gate() {
    # shellcheck disable=SC2086
    python3 "$PORTING_SDK_DIR/scripts/diff_skill_contracts.py" \
        --dump-cmd "./gradlew $GRADLE_DAEMON_FLAG --build-cache -q emitSkills" \
        --port-repo "$PORT_ROOT"
}

# ARTIFACT-DENY (authoritative --listing mode) — the PUBLISHED Maven artifact is the
# `jar` task output (build/libs/signalwire-sdk-<ver>.jar). List its real contents with
# `jar tf` and feed that to artifact_deny.py, rather than the git-ls-files proxy (which
# over-reports in-repo files that never enter the jar).
dayone_artifact_deny() {
    # shellcheck disable=SC2086
    ./gradlew $GRADLE_DAEMON_FLAG --build-cache -q jar || return 1
    local jar
    jar="$(ls -t "$PORT_ROOT"/build/libs/signalwire-sdk-*.jar 2>/dev/null | grep -v -- '-sources\.jar' | grep -v -- '-javadoc\.jar' | head -1)"
    if [ -z "$jar" ]; then
        echo "publish jar not found under build/libs/" >&2
        return 1
    fi
    "${JAVA_HOME:+$JAVA_HOME/bin/}jar" tf "$jar" \
        | python3 "$PORTING_SDK_DIR/scripts/artifact_deny.py" --port java --listing -
}

# ---- register gates ----------------------------------------------------------
sched_init "$@"

# Gradle-invoking gates share res=gradle (mutually exclusive — see header). TEST is
# also deferred so the pure-Python cheap wave gets a head start.
sched_gate TEST res=gradle defer=1 desc="run-tests.sh (gradle test)" \
    -- bash scripts/run-tests.sh

# SIGNATURES builds the JAR (gradle) → res=gradle; DRIFT deps on it. Not deferred
# (a cheap gate depends on it).
sched_gate SIGNATURES res=gradle desc="build jar + regenerate port_signatures.json" \
    --fn signatures_gate

sched_gate DRIFT deps=SIGNATURES desc="diff_port_signatures vs python reference" \
    -- python3 "$PORTING_SDK_DIR/scripts/diff_port_signatures.py" \
        --reference "$PORTING_SDK_DIR/python_signatures.json" \
        --port-signatures "$PORT_ROOT/port_signatures.json" \
        --surface-omissions "$PORT_ROOT/PORT_OMISSIONS.md" \
        --surface-additions "$PORT_ROOT/PORT_ADDITIONS.md" \
        --omissions "$PORT_ROOT/PORT_SIGNATURE_OMISSIONS.md"

sched_gate SURFACE-FRESH res=surface desc="check_surface_freshness vs committed port_surface.json" \
    --fn surface_fresh_gate

sched_gate GEN-FRESH desc="generate_rest.py --check (generated REST files match specs)" \
    -- python3 scripts/generate_rest.py --check

sched_gate GEN-FRESH-SWML desc="generate_swml_verbs.py --check (SWML-verb types match schema.json)" \
    -- python3 scripts/generate_swml_verbs.py --check

sched_gate GEN-FRESH-RELAY desc="generate_relay_protocol.py --check (RELAY types match relay-protocol)" \
    -- python3 scripts/generate_relay_protocol.py --check

sched_gate GEN-FRESH-SWAIG desc="generate_swaig_payloads.py --check (SWAIG payloads match swaig-specs)" \
    -- python3 scripts/generate_swaig_payloads.py --check

sched_gate GEN-FRESH-TESTS desc="generate_rest_tests.py --check (generated REST wire tests match oracle)" \
    -- python3 scripts/generate_rest_tests.py --check

sched_gate NO-CHEAT desc="audit_no_cheat_tests" \
    -- python3 "$PORTING_SDK_DIR/scripts/audit_no_cheat_tests.py" --root "$PORT_ROOT"

sched_gate REST-COVERAGE res=gradle defer=1 desc="every implemented REST route covered success+error (parity + allowlist)" \
    --fn rest_coverage_gate

sched_gate SPEC-PARITY res=gradle defer=1 desc="implemented routes == canonical spec (modulo SPEC_IMPLEMENTATION_GAPS.md)" \
    --fn spec_parity_gate

sched_gate EMISSION res=gradle desc="diff_port_emission vs python oracle" \
    --fn emission_gate

# BEHAVIORAL-* (Layer D) — each runs a fixed behavioral corpus through this port's
# Gradle dump task and structurally byte-compares the JSON against the golden
# signalwire-python oracle the differ builds from `import signalwire`. The dumps use
# `--quiet --console=plain` so ONLY JSON reaches stdout (Gradle log noise → stderr).
# The SDK Logger writes to stderr, so stdout stays pure JSON even with ambient
# SIGNALWIRE_LOG_LEVEL=debug / LOG_MODE=on — the gate does not depend on the caller's
# env. --python-sdk is intentionally omitted: the
# oracle is imported from the pip-installed signalwire-python (same as EMISSION), so CI
# resolves it via the installed package, not a hardcoded path. res=gradle: the dumps
# shell out to ./gradlew and must be mutually exclusive with the other Gradle gates.
sched_gate BEHAVIORAL-WIRE res=gradle desc="diff_port_wire vs python oracle (Layer D)" \
    -- python3 "$PORTING_SDK_DIR/scripts/diff_port_wire.py" \
        --port java \
        --dump-cmd "./gradlew --quiet --console=plain wireDump"

sched_gate BEHAVIORAL-SWML res=gradle desc="diff_port_swml vs python oracle (Layer D)" \
    -- python3 "$PORTING_SDK_DIR/scripts/diff_port_swml.py" \
        --port java \
        --dump-cmd "./gradlew --quiet --console=plain swmlDump"

sched_gate BEHAVIORAL-STATE res=gradle desc="diff_port_state vs python oracle (Layer D)" \
    -- python3 "$PORTING_SDK_DIR/scripts/diff_port_state.py" \
        --port java \
        --dump-cmd "./gradlew --quiet --console=plain stateDump"

sched_gate BEHAVIORAL-HTTP res=gradle desc="diff_port_http vs python oracle (Layer D)" \
    -- python3 "$PORTING_SDK_DIR/scripts/diff_port_http.py" \
        --port java \
        --dump-cmd "./gradlew --quiet --console=plain httpDump"

sched_gate BEHAVIORAL-WIRE-RELAY res=gradle desc="diff_port_wire_relay vs python oracle (Layer D)" \
    -- python3 "$PORTING_SDK_DIR/scripts/diff_port_wire_relay.py" \
        --port java \
        --dump-cmd "./gradlew --quiet --console=plain wireRelayDump"

sched_gate SWAIG-COVERAGE desc="every engine SWAIG action emittable (modulo allowlist)" \
    -- python3 "$PORTING_SDK_DIR/scripts/swaig_coverage.py" --check \
        --emission "$PORT_ROOT/src/main/java/com/signalwire/sdk/swaig/FunctionResult.java"

sched_gate FMT res=gradle defer=1 desc="run-format.sh (local: apply; CI: --check)" \
    -- bash scripts/run-format.sh ${CI:+--check}

sched_gate LINT res=gradle defer=1 desc="run-lint.sh (errorprone warnings-as-errors + checkstyle, zero findings)" \
    -- bash scripts/run-lint.sh

sched_gate DOC-AUDIT res=surface desc="audit_docs vs port_surface.json" \
    -- python3 "$PORTING_SDK_DIR/scripts/audit_docs.py" \
        --root "$PORT_ROOT" \
        --surface "$PORT_ROOT/port_surface.json" \
        --ignore "$PORT_ROOT/DOC_AUDIT_IGNORE.md"

# DOC-WIRE (§A1) — the documented REST fixtures are wire-clean against the spec
# (flag-mode mock journals wire_violations; the docWireRun Gradle task replays the
# documented REST calls). Shells out to ./gradlew for the runner → res=gradle.
sched_gate DOC-WIRE res=gradle desc="documented REST doc fixtures put the spec wire shape on the wire (areacode/number_type)" \
    -- python3 "$PORTING_SDK_DIR/scripts/doc_wire.py" --port java --repo "$PORT_ROOT" \
        --runner "./gradlew $GRADLE_DAEMON_FLAG --console=plain -q docWireRun"

# STATUS-CLAIM (§C2) — no false capability/status claims in docs (e.g. a
# "virtual-thread" claim over a platform-thread pool). Pure-python, cheap.
sched_gate STATUS-CLAIM desc="doc status/capability claims match the shipped surface" \
    -- python3 "$PORTING_SDK_DIR/scripts/status_claim.py" --port java --repo "$PORT_ROOT" \
        --surface "$PORT_ROOT/port_surface.json"

sched_gate SURFACE-DIFF res=surface desc="diff_port_surface vs python reference" \
    --fn surface_diff_gate

sched_gate SKILL-CONTRACT res=gradle desc="diff_skill_contracts vs python reference" \
    --fn skill_contract_gate

# SWAIG-CLI invokes bin/swaig-test, which needs the compiled SDK jar
# (build/libs/*.jar). On a fresh CI checkout no jar exists yet, so this gate must
# wait for SIGNATURES (which runs `./gradlew build`) to produce it — otherwise the
# launcher prints "No SDK classes found" and every --help verb reads as missing
# and the serverless-reject probe errors before it can name the bad platform.
# (Locally a stale jar from a prior build masked this ordering gap.)
sched_gate SWAIG-CLI deps=SIGNATURES desc="swaig-test shared mini-contract (verbs/serverless-reject/default-action)" \
    -- python3 "$PORTING_SDK_DIR/scripts/audit_swaig_cli_contract.py" \
        --port java \
        --cmd "bash $PORT_ROOT/bin/swaig-test" \
        --require-url-model \
        --default-action-argv='--url|http://user:pass@127.0.0.1:1/' \
        --has-serverless \
        --serverless-argv='DummyAgentClass|--simulate-serverless|bogus-platform-xyz|--dump-swml'

# ---- §C1 doc/example/CLI execution gates ------------------------------------
# SNIPPET-COMPILE typechecks every documented java fence WITH the SDK jar + deps on
# the classpath (deleted/renamed SDK symbols fail); it shells out to ./gradlew to
# build the jar → res=gradle, mutually exclusive with the other Gradle gates. It is
# otherwise cheap → NOT deferred. DOC-CLI line-detects documented swaig-test
# invocations (java's AOT CLI is not built/probed by the gate) → pure-python, cheap.
sched_gate SNIPPET-COMPILE tier=nightly res=gradle desc="documented code snippets compile against the real SDK jar" \
    -- python3 "$PORTING_SDK_DIR/scripts/snippet_compile.py" --port java --repo "$PORT_ROOT"

sched_gate DOC-CLI desc="documented swaig-test invocations parse against the real CLI" \
    -- python3 "$PORTING_SDK_DIR/scripts/doc_cli.py" --port java --repo "$PORT_ROOT"

# Wave-3 doc/API-truth gates — deterministic source/doc analysis (no build, no
# mock, ~1.3s for all six). Per-PR tier: cheap enough to catch doc/API drift at
# PR time rather than a day later in nightly.
sched_gate ERROR-ENVELOPE desc="REST error carries the full (status,body,url,method) envelope + raised on >=400" \
    -- python3 "$PORTING_SDK_DIR/scripts/error_envelope.py" --port java --repo "$PORT_ROOT"
sched_gate DEAD-PUBLIC-ERROR desc="exported error types are raised/caught/user-signalled (no dead error surface)" \
    -- python3 "$PORTING_SDK_DIR/scripts/dead_public_error.py" --port java --repo "$PORT_ROOT"
sched_gate PAGINATION-WIRED desc="shipped iterator-protocol paginator is wired into list()" \
    -- python3 "$PORTING_SDK_DIR/scripts/pagination_wired.py" --port java --repo "$PORT_ROOT"
sched_gate DOC-ENV desc="documented SIGNALWIRE_*/SWML_* env vars <=> code-read vars agree" \
    -- python3 "$PORTING_SDK_DIR/scripts/doc_env.py" --port java --repo "$PORT_ROOT"
sched_gate COUNT-CLAIM desc="numeric doc claims (skills/namespaces) match reality" \
    -- python3 "$PORTING_SDK_DIR/scripts/count_claim.py" --port java --repo "$PORT_ROOT"
sched_gate ACCESSOR-TRUTH desc="documented backtick method() refs exist in source" \
    -- python3 "$PORTING_SDK_DIR/scripts/accessor_truth.py" --port java --repo "$PORT_ROOT"

# EXAMPLES-RUN builds + runs every shipped examples/*.java via `gradle runExample`
# against the shared mock (modulo EXAMPLES_RUN_ALLOW.md) → res=gradle, deferred
# behind the cheap wave (heavy: one JVM per example). STRICT-MOCKS (nightly):
# MOCK_RELAY_STRICT=1 makes the RELAY mock 400 on any off-spec frame, so an example
# that puts a wrong wire shape on the RELAY socket fails instead of being silently
# accepted.
sched_gate EXAMPLES-RUN tier=nightly res=gradle defer=1 desc="shipped examples build+run against the mock (modulo EXAMPLES_RUN_ALLOW.md; STRICT-MOCKS: MOCK_RELAY_STRICT=1)" \
    -- env MOCK_RELAY_STRICT=1 python3 "$PORTING_SDK_DIR/scripts/examples_run.py" --port java --repo "$PORT_ROOT"

# SNIPPET-RUN is dynamic-ports-only; for java (compiled/heavy) it self-skips —
# SNIPPET-COMPILE covers it — but is wired report-only so the self-skip never fails.
# STRICT-MOCKS (nightly): MOCK_RELAY_STRICT=1 carried for parity with the other ports
# (a no-op while java self-skips).
sched_gate SNIPPET-RUN tier=nightly defer=1 desc="dynamic-port doc snippets run to a zero exit (java: self-skips, SNIPPET-COMPILE covers it; STRICT-MOCKS: MOCK_RELAY_STRICT=1)" \
    -- env MOCK_RELAY_STRICT=1 python3 "$PORTING_SDK_DIR/scripts/snippet_run.py" --port java --repo "$PORT_ROOT" --report-only

# WAIT-LIVENESS (§2.4) — drives play/record verbs against an embedded RELAY mock,
# arms a delayed completing event, and diffs the measured liveness classification
# against the python golden (a no-op wait that returns at t~0 → RED; a hung wait
# that blows the deadline → RED). Spawns a JVM dump program → res=gradle, deferred,
# nightly (heavy + timing-sensitive).
sched_gate WAIT-LIVENESS tier=nightly res=gradle defer=1 desc="wait() blocks until the completing event then returns (liveness classification vs golden)" \
    -- python3 "$PORTING_SDK_DIR/scripts/diff_port_wait_liveness.py" --port java \
        --dump-cmd "./gradlew $GRADLE_DAEMON_FLAG --console=plain -q waitLivenessDump"

# ---- §G anti-laundering ledger ----------------------------------------------
sched_gate SUPPRESSION-LEDGER res=dayone desc="no un-ledgered analyzer suppressions" \
    -- python3 "$PORTING_SDK_DIR/scripts/suppression_ledger.py" --port java --repo "$PORT_ROOT"

# ---- §D1 packaging ----------------------------------------------------------
# PACKAGE-SMOKE runs `gradle publishToMavenLocal` into a private m2, then resolves +
# compiles + runs a tiny consumer against the published jar (SDK jar + its runtime
# deps, sourced from the port's `printRuntimeClasspath` task). Shells out to
# ./gradlew → res=gradle, deferred behind the cheap wave (heavy: real publish build).
sched_gate PACKAGE-SMOKE res=gradle defer=1 desc="the real publishable jar builds + resolves + imports from a clean env" \
    -- python3 "$PORTING_SDK_DIR/scripts/package_smoke.py" --port java --repo "$PORT_ROOT"

# Day-one deterministic gates (enforced, non-report-only).
sched_gate DOC-LANG-PURITY res=dayone desc="no python-verbatim docs in a non-python port" \
    -- python3 "$PORTING_SDK_DIR/scripts/doc_lang_purity.py" --port java --repo .
sched_gate DOC-LINKS res=dayone desc="every relative markdown link resolves to a tracked file" \
    -- python3 "$PORTING_SDK_DIR/scripts/doc_links.py" --port java --repo .

sched_gate README-INCLUDE res=dayone desc="doc code blocks are byte-identical to their gate-compiled fixture regions" \
    -- python3 "$PORTING_SDK_DIR/scripts/readme_include.py" --port java --repo .
sched_gate ROOT-HYGIENE res=dayone desc="no audit/scratch clutter tracked at repo root (allowlist ROOT_HYGIENE_ALLOW.md)" \
    -- python3 "$PORTING_SDK_DIR/scripts/root_hygiene.py" --port java --repo .
# IGNORE-LEDGER-VERIFY READS port_surface.json → res=surface (mutex with the
# SURFACE-* regenerators that truncate the file in place while enumerating; a
# res=dayone slot could read it mid-truncation → empty-file JSONDecodeError).
sched_gate IGNORE-LEDGER-VERIFY res=surface desc="no laundered false-absence entries in DOC_AUDIT_IGNORE.md (strict: reason/approver/date required)" \
    -- python3 "$PORTING_SDK_DIR/scripts/ignore_ledger_verify.py" --port java --repo . --require-fields
sched_gate META-CONSISTENT res=dayone desc="package metadata consistency" \
    -- python3 "$PORTING_SDK_DIR/scripts/meta_consistent.py" --port java --repo .
sched_gate ARTIFACT-DENY res=gradle desc="no porting artifacts in the PUBLISHED package (authoritative listing)" \
    --fn dayone_artifact_deny

# Expansion gates (Tier 5 / release) — enforced (non-report-only). Backlog burned
# to zero + GEN_TYPE_DEGENERACY_ALLOW.md / ROUTE_COLLISION_ALLOW.md user-approved,
# so each passes enforcing. ROUTE-COLLISION consumes the routeRegistry task (java
# has a registry) → res=gradle via route_collision_gate.
sched_gate GEN-TYPE-DEGENERACY res=dayone desc="generated types are not bare loose escape hatches (allowlist GEN_TYPE_DEGENERACY_ALLOW.md)" \
    -- python3 "$PORTING_SDK_DIR/scripts/gen_type_degeneracy.py" --port java --repo .
sched_gate PUBLIC-JARGON res=dayone desc="no internal porting jargon in public doc comments" \
    -- python3 "$PORTING_SDK_DIR/scripts/public_jargon.py" --port java --repo .
sched_gate ROUTE-COLLISION res=gradle desc="no split routes / duplicated CRUD bases (routeRegistry × surface; allowlist ROUTE_COLLISION_ALLOW.md)" \
    --fn route_collision_gate
sched_gate GEN-IDIOM res=dayone desc="generated code is not lint-excluded (idiom linter runs over it)" \
    -- python3 "$PORTING_SDK_DIR/scripts/gen_idiom.py" --port java --repo .
sched_gate RELEASE-FRESH res=dayone desc="publish workflow runs gates BEFORE publishing" \
    -- python3 "$PORTING_SDK_DIR/scripts/release_fresh.py" --port java --repo .
# SEMVER-DIFF — the version bump must match the API surface change vs the
# committed port_signatures.baseline.json floor (baseline_version 3.0.2). Blocking.
sched_gate SEMVER-DIFF res=dayone desc="version bump matches API surface change vs baseline floor" \
    -- python3 "$PORTING_SDK_DIR/scripts/semver_diff.py" --port java --repo "$PORT_ROOT"

sched_run
rc=$?
if [ "$rc" -eq 0 ]; then
    echo "==> CI PASS"
else
    echo "==> CI FAIL (gates:$FAILED_GATES )"
fi
exit "$rc"
