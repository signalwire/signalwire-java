#!/usr/bin/env bash
# run-tests.sh — the TEST entry point for signalwire-java (tool: gradle test).
#
# The SINGLE entry point for running the test suite, callable from ANY directory
# by run-ci, an agent, or a human — the tool environment is self-bootstrapped
# (scripts/_env.sh: Gradle wrapper + JAVA_HOME) so it never depends on the
# caller's shell setup or CWD. See porting-sdk/RUN_LINT_FORMAT_SPEC.md.
#
# Modes:
#   bash scripts/run-tests.sh              # run the full suite (gradle test).
#   bash scripts/run-tests.sh <filter>     # run a subset — the filter is passed
#                                          #   through to gradle as
#                                          #   `--tests <filter>` (e.g. a test
#                                          #   class or fully-qualified method:
#                                          #   com.signalwire.sdk.AgentBaseTest,
#                                          #   or 'com.signalwire.sdk.rest.*').
#   bash scripts/run-tests.sh [<filter>] --rerun-tasks …
#                                          # any argument beginning with `-` is
#                                          #   passed straight through to gradle,
#                                          #   so gradle flags are reachable
#                                          #   without bypassing this entry point.
#
# Exits non-zero on any test failure. Mock hygiene: any mocks spun up by the tests
# self-terminate on parent death (the shared harness contract), so a filtered or
# interrupted run leaves no squatting listener.
#
# PROOF OF EXECUTION (the reason this script is more than a one-line wrapper):
# `gradle test` exits 0 for three different outcomes — the task EXECUTED, the task
# was UP-TO-DATE, or the task came FROM-CACHE — and only the first ran a test. A
# bare exit-status check therefore cannot tell "2331 tests passed" from "nothing
# ran, here is a replay of last time", which is precisely the distinction that
# matters on a commit that changed generated types. So this script requires an
# EXECUTION RECEIPT that only a real run can produce (see the `test {}` block in
# build.gradle for the mechanism and why the alternatives do not work) and reports
# the counts the run actually produced. A replayed green is now a gate FAILURE
# with an actionable message, not a silent pass.
#
# This does NOT disable Gradle's caching, and it must not: cache/up-to-date reuse
# across the ~17 gradle invocations run-ci makes is a legitimate and large
# speedup, and the defect was never that the cache exists — it was a gate that
# treated a cache hit as evidence of execution. The ordinary path stays cached;
# it just has to say so instead of claiming a pass.
#
# COST, stated plainly (measured 2026-08-03, 8-core Apple Silicon):
#   * The assertion itself costs NOTHING — it is one `rm -f` and one `[ -f ]`
#     around an otherwise unchanged gradle invocation. No flag is forced: run-ci
#     still calls this script bare, so the normal first-run-per-change path is
#     byte-for-byte the gradle command it always was.
#   * A genuine full run is ~22s (`--rerun-tasks`, 5 tasks executed, 2331 tests).
#     A replayed no-op "pass" was ~0.6s. That ~21s is not a new cost the
#     assertion adds; it is the cost of actually testing, which the gate was
#     previously skipping while reporting success.
#   * The BEHAVIOUR CHANGE the owner should weigh: a SECOND consecutive run over
#     an UNCHANGED tree now EXITS 1 ("did NOT execute") where it used to print a
#     hollow PASS. That is deliberate — the alternative is a gate that cannot
#     fail — but it means a local re-run of run-ci with no edits in between now
#     reports red on TEST. The remedy is in the failure message itself
#     (`--rerun-tasks`), and any real edit to a test or source input makes the
#     task execute normally with no flag at all.
#   * On CI this is a non-event for ordinary PR runs: the pre-build warm-up step
#     uses `-x test`, so it never populates a `:test` cache entry, and a commit
#     with any change has different `:test` inputs. The one exposed case is
#     re-running CI on an already-cached, byte-identical commit — which would
#     now go red instead of green-without-testing. That is the correct verdict,
#     but it is a real difference from today's behaviour.

set -euo pipefail

# shellcheck source=scripts/_env.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_env.sh"

# Split argv: the first non-flag argument is the --tests filter (the long-standing
# contract); everything starting with `-` is a gradle flag passed through.
FILTER=""
GRADLE_FLAGS=()
for arg in "$@"; do
    case "$arg" in
        -*) GRADLE_FLAGS+=("$arg") ;;
        *)
            if [ -n "$FILTER" ]; then
                echo "FATAL: more than one test filter given ('$FILTER' and '$arg')." >&2
                echo "       Pass ONE filter; use a wildcard for a set, e.g. 'com.signalwire.sdk.rest.*'." >&2
                exit 2
            fi
            FILTER="$arg"
            ;;
    esac
done

TEST_ARGS=(test)
if [ -n "$FILTER" ]; then
    TEST_ARGS+=(--tests "$FILTER")
fi
if [ ${#GRADLE_FLAGS[@]} -gt 0 ]; then
    TEST_ARGS+=("${GRADLE_FLAGS[@]}")
fi

# The receipt is written by the test task's doLast, which Gradle runs ONLY when the
# task really executes. It is not a declared task output, so the build cache
# neither stores nor restores it — unlike build/test-results/, which FROM-CACHE
# faithfully reproduces (files and fresh mtimes and all), and which therefore
# cannot serve as an execution signal.
RECEIPT="$REPO_ROOT/build/sw-test-execution-receipt.txt"
rm -f "$RECEIPT"

echo "==> TEST (gradle ${TEST_ARGS[*]}) — repo: $REPO_ROOT"
sw_gradle "${TEST_ARGS[@]}"

if [ ! -f "$RECEIPT" ]; then
    echo "" >&2
    echo "FATAL: the test task did NOT execute — gradle reported success by reusing a" >&2
    echo "       previous result (\`:test UP-TO-DATE\` or \`:test FROM-CACHE\`). No test" >&2
    echo "       ran, so this build is ZERO evidence that the suite passes." >&2
    echo "" >&2
    echo "       This is not a build failure to work around: it means the inputs are" >&2
    echo "       unchanged since the last real run. If you need a genuine run anyway" >&2
    echo "       (verifying regenerated code, chasing a suspected stale result):" >&2
    echo "         bash scripts/run-tests.sh --rerun-tasks" >&2
    echo "" >&2
    exit 1
fi

echo "==> TEST executed for real (receipt: $RECEIPT)"
cat "$RECEIPT"

# The receipt's own failure count is a second, independent check on the verdict:
# gradle's exit status is the primary signal, but a listener that counted failures
# while the build still reported success would be a contradiction worth failing on.
RECEIPT_FAILED="$(sed -n 's/^failed=//p' "$RECEIPT")"
if [ -n "$RECEIPT_FAILED" ] && [ "$RECEIPT_FAILED" != "0" ]; then
    echo "FATAL: $RECEIPT_FAILED test(s) failed (per the execution receipt) although gradle" >&2
    echo "       reported success. Treating this as a failure." >&2
    exit 1
fi

RECEIPT_EXECUTED="$(sed -n 's/^executed=//p' "$RECEIPT")"
if [ "${RECEIPT_EXECUTED:-0}" = "0" ]; then
    echo "FATAL: the test task ran but executed ZERO tests. A suite that selects nothing" >&2
    echo "       cannot pass. Check the filter${FILTER:+ ('$FILTER')} and the test discovery config." >&2
    exit 1
fi
