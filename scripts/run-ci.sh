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
#     * S2 concurrent wave: the pure-Python side-effect-free gates (the GEN suite,
#       NO-CHEAT, LEDGER, the per-PR PACKAGE members, etc.) overlap — they share no
#       mutable state and don't touch Gradle.
#     * S1 fail-fast: heavy gates are deferred behind the cheap wave, so a trivial
#       cheap-gate failure surfaces in seconds; --fail-fast aborts before TEST.
#   HARD ordering / mutual exclusion (the ONLY pre-emptive serialization):
#     * res=gradle: every gate that shells out to ./gradlew runs MUTUALLY
#       EXCLUSIVE. This is NOT a predicted-cost throttle — concurrent Gradle
#       invocations against one project genuinely contend on the build dir +
#       daemon (a real breakage, per the CI_PERF correction "isolate what ACTUALLY
#       breaks"), so they are serialized. The pure-Python cheap wave still overlaps
#       freely with the single in-flight Gradle gate. For java this res=gradle
#       group is: TEST, FMT, LINT, SNIPPET-COMPILE, EXAMPLES-RUN, and the SUITE
#       lines that internally shell to Gradle — SURFACE (SIGNATURES rebuilds the
#       jar; ROUTE-COLLISION runs the routeRegistry task), BEHAVIORAL / BEHAVIORAL-
#       NIGHTLY (the *Dump tasks + the REST-COVERAGE test run), PACKAGE (ARTIFACT-
#       DENY runs `./gradlew jar`) and PACKAGE-NIGHTLY (PACKAGE-SMOKE publishes).
#     * The SURFACE suite ALSO regenerates port_surface.json in place (and restores
#       it); DOC-TRUTH's DOC-AUDIT / STATUS-CLAIM read that file, so DOC-TRUTH
#       joins res=gradle too — a single label serializes it behind SURFACE's regen
#       so a concurrent read can never see a truncated file (the old per-gate
#       SURFACE-FRESH/SURFACE-DIFF/DOC-AUDIT res=surface mutex, folded into the
#       gradle label because the SURFACE suite now also carries the jar rebuild).
#   Per-gate PASS/FAIL + the FAILED_GATES tally preserved exactly; each gate's output
#   captured + replayed atomically.
#
# The Java adapter requires the SDK jar rebuilt before reflection sees new methods,
# so the SURFACE suite's SIGNATURES rule does `./gradlew build -x test && enumerate`.
# The BEHAVIORAL suite's Gradle dump tasks rebuild incrementally as needed.
#
# ---- Part 5 gate SUITES ------------------------------------------------------
# The former per-gate SIGNATURES/DRIFT/SURFACE-*/SEMVER-DIFF/GEN-TYPE-DEGENERACY/
# GEN-IDIOM/ROUTE-COLLISION/GEN-FRESH*/BEHAVIORAL-*/EMISSION/ERROR-ENVELOPE/
# PAGINATION-WIRED/DOC-WIRE/REST-COVERAGE/SPEC-PARITY/SKILL-CONTRACT/SWAIG-*/
# WAIT-LIVENESS/DOC-*/COUNT-CLAIM/ACCESSOR-TRUTH/STATUS-CLAIM/README-INCLUDE/
# *-LEDGER/PACKAGE-SMOKE/META-CONSISTENT/ARTIFACT-DENY/RELEASE-FRESH gates now run
# under 6 SUITE engines. Each suite emits every original gate NAME as a
# `[SUITE:RULE] ... PASS/FAIL` rule ID (failure identity + allowlists + finding
# output unchanged). A suite exits nonzero iff any of its rules fails. Byte-identity
# vs the old per-gate path is proven by porting-sdk/tests/test_suite_parity*.py.
#
# The `--fn` helpers the old gates used (signatures_gate, surface_fresh_gate,
# surface_diff_gate, rest_coverage_gate, spec_parity_gate, route_collision_gate,
# emission_gate, skill_contract_gate, dayone_artifact_deny, pick_free_port) are now
# reproduced INSIDE the Part-5 suites (scripts/suites/*.py + _*.py members), so they
# are no longer defined here.
#
# Former single-gate scheduler features preserved by the suites internally:
#   * SIGNATURES→DRIFT ordering, the SEMVER-DIFF-reads-SIGNATURES data dep, and the
#     SURFACE-FRESH/SURFACE-DIFF regenerate-then-restore all live inside the SURFACE
#     suite; the jar rebuild before enumeration is the SURFACE suite's java SIGNATURES
#     member.
#   * mixed tiers are split with --rules: PACKAGE + BEHAVIORAL each schedule a
#     per-PR line and a nightly line (nightly members broken out below).
# JAVA-SPECIFIC: java's behavioral RELAY rule keeps java's hyphen spelling
# BEHAVIORAL-WIRE-RELAY, and java's SURFACE suite carries ROUTE-COLLISION (java HAS a
# routeRegistry Gradle task the gate consumes).
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
# These EXPORTED vars are inherited by every scheduler worker subshell (and by the
# SURFACE / BEHAVIORAL / PACKAGE suites, which reference $GRADLE_DAEMON_FLAG in the
# reproduced java gradle command bodies).
# shellcheck source=scripts/_env.sh
source "$PORT_ROOT/scripts/_env.sh"

# shellcheck source=/dev/null
source "$PORTING_SDK_DIR/scripts/gate_scheduler.sh"

cd "$PORT_ROOT"

# Gate-enforcement plan (Part D): java's red list is burned, so its widened
# (wave-A) gate findings BLOCK rather than report-only. Default OFF here; a caller
# may still set SW_WAVE_A_REPORT_ONLY=1 to inspect the report-only view.
export SW_WAVE_A_REPORT_ONLY="${SW_WAVE_A_REPORT_ONLY:-0}"

# STRICT-MOCKS default (D3 / plan 1.2): make the REST mock 400 any wire violation
# (unknown body key / malformed value) by default, so a wrong wire key surfaces at
# PR time in the TEST gate's own mock and any test/gate that spawns one — not only in
# the REST-COVERAGE journal post-pass. EXPORTED so every scheduler worker subshell (and
# every mock they spawn) inherits it. This is a WIRED MODE — see WIRED_MODES.md;
# check_wired_modes.py fails the WIRED-MODES gate if this line is ever silently dropped.
export MOCK_SIGNALWIRE_STRICT="${MOCK_SIGNALWIRE_STRICT:-1}"

# BARE-KEY BAN armed (plan 2.12): make DOC-AUDIT's bare-word ignore-key ban BLOCKING —
# any DOC_AUDIT_IGNORE.md key that is a bare, unqualified token (suppressing its
# identifier EVERYWHERE instead of a specific Class#member) reds DOC-AUDIT. java's keys
# are all qualified; this keeps a future over-broad ignore from silently re-appearing.
# WIRED MODE — see WIRED_MODES.md.
export SW_BAN_BARE_IGNORE_KEYS="${SW_BAN_BARE_IGNORE_KEYS:-1}"

echo "==> running CI gates for $PORT_NAME (porting-sdk at $PORTING_SDK_DIR)"
echo "==> wave-A gate findings are ${SW_WAVE_A_REPORT_ONLY:+BLOCKING (SW_WAVE_A_REPORT_ONLY=$SW_WAVE_A_REPORT_ONLY)}"

# ---- register gates ----------------------------------------------------------
sched_init "$@"

# Gradle-invoking gates share res=gradle (mutually exclusive — see header). TEST is
# also deferred so the pure-Python cheap wave gets a head start.
sched_gate TEST res=gradle defer=1 desc="run-tests.sh (gradle test)" \
    -- bash scripts/run-tests.sh

# ---- Part 5 gate SUITES ------------------------------------------------------
# SURFACE (parity spine): SIGNATURES (java: rebuild jar then enumerate) → DRIFT
# ordered, SURFACE-FRESH/SURFACE-DIFF (re-enumerate in place + restore), SEMVER-DIFF,
# GEN-TYPE-DEGENERACY, GEN-IDIOM, ROUTE-COLLISION (routeRegistry Gradle task) — all
# read the one enumeration. res=gradle: the SIGNATURES jar rebuild + ROUTE-COLLISION's
# routeRegistry task shell to ./gradlew (mutex with the other Gradle gates), AND the
# in-place port_surface.json regen must not overlap DOC-TRUTH's read (DOC-TRUTH also
# res=gradle). Not deferred (parity spine; runs in the cheap wave modulo the gradle lock).
sched_gate SURFACE res=gradle desc="surface parity suite (SIGNATURES/DRIFT/SURFACE-FRESH/SURFACE-DIFF/SEMVER-DIFF/GEN-TYPE-DEGENERACY/GEN-IDIOM/ROUTE-COLLISION)" \
    -- python3 "$PORTING_SDK_DIR/scripts/suites/surface.py" --port java --repo "$PORT_ROOT"

# GEN (regen-from-specs family): the 5 GEN-FRESH rules. Most are pure-python
# (--check against the on-disk generated tree), but GEN-FRESH-TESTS's
# generate_rest_tests.py shells to `./gradlew --no-daemon routeRegistry` /
# `routeTestPlan` to capture the route oracle — a real Gradle invocation against
# the shared build/ dir. res=gradle: without this, GEN races the other
# Gradle-touching gates (TEST/SURFACE/BEHAVIORAL/etc.) and can classload a
# truncated .class file mid-recompile (proven: reproduces in the full run,
# passes standalone). Still deferred behind the cheap wave for fail-fast.
sched_gate GEN res=gradle defer=1 desc="generated-code freshness suite (GEN-FRESH/-TESTS/-RELAY/-SWAIG/-SWML)" \
    -- python3 "$PORTING_SDK_DIR/scripts/suites/gen.py" --port java --repo "$PORT_ROOT"

# BEHAVIORAL (one Layer-D pass per rule): the per-PR rules. WAIT-LIVENESS (nightly)
# is the separate line below. res=gradle: the *Dump tasks + REST-COVERAGE test run
# shell to ./gradlew. NOTE java's hyphen spelling BEHAVIORAL-WIRE-RELAY.
sched_gate BEHAVIORAL res=gradle defer=1 desc="behavioral suite (BEHAVIORAL-*/EMISSION/ERROR-ENVELOPE/PAGINATION-WIRED/PAGINATION-CORPUS/DOC-WIRE/REST-COVERAGE/SPEC-PARITY/SKILL-CONTRACT/SWAIG-COVERAGE/SWAIG-CLI/SECURE-DEFAULT/CA-VAR/SECRET-SCRUB)" \
    -- python3 "$PORTING_SDK_DIR/scripts/suites/behavioral.py" --port java --repo "$PORT_ROOT" \
        --rules BEHAVIORAL-WIRE,BEHAVIORAL-SWML,BEHAVIORAL-STATE,BEHAVIORAL-HTTP,BEHAVIORAL-WIRE-RELAY,EMISSION,ERROR-ENVELOPE,PAGINATION-WIRED,PAGINATION-CORPUS,DOC-WIRE,REST-COVERAGE,SPEC-PARITY,SKILL-CONTRACT,SWAIG-COVERAGE,SWAIG-CLI,SECURE-DEFAULT,CA-VAR,SECRET-SCRUB

sched_gate BEHAVIORAL-NIGHTLY tier=nightly res=gradle defer=1 desc="behavioral suite, nightly rules (WAIT-LIVENESS/RELAY-LIVENESS)" \
    -- python3 "$PORTING_SDK_DIR/scripts/suites/behavioral.py" --port java --repo "$PORT_ROOT" \
        --rules WAIT-LIVENESS,RELAY-LIVENESS

# DOC-TRUTH (one markdown walk): DOC-AUDIT/DOC-LINKS/DOC-LANG-PURITY/DOC-ENV/
# COUNT-CLAIM/ACCESSOR-TRUTH/STATUS-CLAIM/README-INCLUDE. res=gradle: DOC-AUDIT +
# STATUS-CLAIM read the on-disk port_surface.json that the SURFACE suite
# regenerates+restores, so DOC-TRUTH must not overlap SURFACE (single gradle label
# serializes it behind the regen).
sched_gate DOC-TRUTH res=gradle desc="doc-truth suite (DOC-AUDIT/DOC-LINKS/DOC-LANG-PURITY/DOC-ENV/COUNT-CLAIM/ACCESSOR-TRUTH/STATUS-CLAIM/README-INCLUDE)" \
    -- python3 "$PORTING_SDK_DIR/scripts/suites/doc_truth.py" --port java --repo "$PORT_ROOT"

# LEDGER: SUPPRESSION-LEDGER + IGNORE-LEDGER-VERIFY (pure-python governance).
sched_gate LEDGER res=dayone desc="ledger governance suite (SUPPRESSION-LEDGER/IGNORE-LEDGER-VERIFY)" \
    -- python3 "$PORTING_SDK_DIR/scripts/suites/ledger.py" --port java --repo "$PORT_ROOT"

# PACKAGE: per-PR rules (ARTIFACT-DENY/RELEASE-FRESH); nightly rules (PACKAGE-SMOKE/
# META-CONSISTENT) on the separate line below. res=gradle: ARTIFACT-DENY runs
# `./gradlew jar` to list the real published artifact.
sched_gate PACKAGE res=gradle desc="package suite, per-PR rules (ARTIFACT-DENY/RELEASE-FRESH)" \
    -- python3 "$PORTING_SDK_DIR/scripts/suites/package.py" --port java --repo "$PORT_ROOT" \
        --rules ARTIFACT-DENY,RELEASE-FRESH

# PACKAGE-SMOKE runs `gradle publishToMavenLocal` + a consumer build → res=gradle,
# nightly + deferred (heavy: real publish build).
sched_gate PACKAGE-NIGHTLY tier=nightly res=gradle defer=1 desc="package suite, nightly rules (PACKAGE-SMOKE/META-CONSISTENT)" \
    -- python3 "$PORTING_SDK_DIR/scripts/suites/package.py" --port java --repo "$PORT_ROOT" \
        --rules PACKAGE-SMOKE,META-CONSISTENT

# ---- gates that stay standalone (native toolchains + singletons) -------------
sched_gate NO-CHEAT desc="audit_no_cheat_tests" \
    -- python3 "$PORTING_SDK_DIR/scripts/audit_no_cheat_tests.py" --root "$PORT_ROOT"

sched_gate FMT res=gradle defer=1 desc="run-format.sh (local: apply; CI: --check)" \
    -- bash scripts/run-format.sh ${CI:+--check}

sched_gate LINT res=gradle defer=1 desc="run-lint.sh (errorprone warnings-as-errors + checkstyle, zero findings)" \
    -- bash scripts/run-lint.sh

# ---- §C1 doc/example/CLI execution gates ------------------------------------
# SNIPPET-COMPILE typechecks every documented java fence WITH the SDK jar + deps on
# the classpath (deleted/renamed SDK symbols fail); it shells out to ./gradlew to
# build the jar → res=gradle, mutually exclusive with the other Gradle gates. It is
# otherwise cheap → NOT deferred, but nightly-tier. DOC-CLI line-detects documented
# swaig-test invocations (java's AOT CLI is not built/probed by the gate) →
# pure-python, cheap.
sched_gate SNIPPET-COMPILE tier=nightly res=gradle desc="documented code snippets compile against the real SDK jar" \
    -- python3 "$PORTING_SDK_DIR/scripts/snippet_compile.py" --port java --repo "$PORT_ROOT"

sched_gate DOC-CLI desc="documented swaig-test invocations parse against the real CLI" \
    -- python3 "$PORTING_SDK_DIR/scripts/doc_cli.py" --port java --repo "$PORT_ROOT"

# DEAD-PUBLIC-ERROR stays standalone (source analysis of exported error types — not
# a doc-truth/behavioral rule). ERROR-ENVELOPE/PAGINATION-WIRED/DOC-WIRE run under
# the BEHAVIORAL suite; DOC-ENV/COUNT-CLAIM/ACCESSOR-TRUTH/STATUS-CLAIM under
# DOC-TRUTH.
sched_gate DEAD-PUBLIC-ERROR desc="exported error types are raised/caught/user-signalled (no dead error surface)" \
    -- python3 "$PORTING_SDK_DIR/scripts/dead_public_error.py" --port java --repo "$PORT_ROOT"

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

# ROOT-HYGIENE + PUBLIC-JARGON stay standalone (source/root analysis, not a suite
# family).
sched_gate ROOT-HYGIENE res=dayone desc="no audit/scratch clutter tracked at repo root (allowlist ROOT_HYGIENE_ALLOW.md)" \
    -- python3 "$PORTING_SDK_DIR/scripts/root_hygiene.py" --port java --repo .

sched_gate PUBLIC-JARGON res=dayone desc="no internal porting jargon in public doc comments" \
    -- python3 "$PORTING_SDK_DIR/scripts/public_jargon.py" --port java --repo .

# DOC-SURFACE (plan §6.3): javadoc /** coverage floor on the public API surface.
# The floor is pinned in .doc_surface_floor (51.8% today) and ratchets up via
# --write-floor; report-only at graduation, so a doc regression is visible without
# failing the run yet (never-regress is enforced once the floor flips blocking).
# GUARDED: doc_surface.py ships on the porting-sdk plan branch; until it merges to
# porting-sdk main (which CI clones), skip-with-pass rather than red on a not-yet-
# landed sibling script. Remove the guard once it's on porting-sdk main.
sched_gate DOC-SURFACE res=dayone desc="javadoc coverage floor on the public API surface (report-only, ratchets via .doc_surface_floor)" \
    -- bash -c 'if [ -f "$1/scripts/doc_surface.py" ]; then python3 "$1/scripts/doc_surface.py" --port java --repo "$2" --report-only; else echo "[doc-surface] doc_surface.py not on porting-sdk main yet — skip-pass (plan-branch dep)"; fi' _ "$PORTING_SDK_DIR" "$PORT_ROOT"

# WIRED-MODES (Part 1.6 / D7): the merge-coherence guard — greps this run-ci.sh for
# every load-bearing env/mode line declared in WIRED_MODES.md (the strict-mocks
# exports MOCK_SIGNALWIRE_STRICT / MOCK_RELAY_STRICT). The strict-mocks × Part-5 merge
# race silently DROPPED exactly such lines from several ports (java lost all three);
# nothing caught it because they aren't gates themselves. This fails LOUD if a future
# merge drops one here.
sched_gate WIRED-MODES res=dayone desc="run-ci exports every load-bearing strict-mode line declared in WIRED_MODES.md" \
    -- python3 "$PORTING_SDK_DIR/scripts/check_wired_modes.py" --port java --repo "$PORT_ROOT"

# GATE-INVENTORY NOTE (§2.16): §1.11b (GATE-INVENTORY freshness) is intentionally
# NOT wired here. gen_gate_inventory.py resolves its reference port as a SIBLING
# checkout (DEFAULT_REFERENCE=signalwire-typescript), which does not exist in a
# port's CI layout (porting-sdk is a subdir of the port workspace, so
# ../signalwire-typescript is absent → exit 2). The check is inherently
# porting-sdk-side and already runs in porting-sdk's own CI
# (.github/workflows/test.yml). Wiring it per-port would require each port to also
# check out the TS reference — not worth it.

# ---- summary ----------------------------------------------------------------
sched_run
rc=$?
if [ "$rc" -eq 0 ]; then
    echo "==> CI PASS"
else
    echo "==> CI FAIL (gates:$FAILED_GATES )"
fi
exit "$rc"
