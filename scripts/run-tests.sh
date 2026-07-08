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
#
# Exits non-zero on any test failure. Mock hygiene: any mocks spun up by the tests
# self-terminate on parent death (the shared harness contract), so a filtered or
# interrupted run leaves no squatting listener.

set -euo pipefail

# shellcheck source=scripts/_env.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_env.sh"

FILTER="${1:-}"

if [ -n "$FILTER" ]; then
    echo "==> TEST (gradle test --tests '$FILTER') — repo: $REPO_ROOT"
    sw_gradle test --tests "$FILTER"
else
    echo "==> TEST (gradle test) — repo: $REPO_ROOT"
    sw_gradle test
fi
