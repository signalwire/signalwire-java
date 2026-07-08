#!/usr/bin/env bash
# run-format.sh — the FMT entry point for signalwire-java
# (tool: gradle spotlessApply / spotlessCheck, google-java-format).
#
# The SINGLE entry point for formatting, callable from ANY directory by run-ci, an
# agent, or a human — the tool environment is self-bootstrapped (scripts/_env.sh:
# Gradle wrapper + JAVA_HOME) so it never depends on the caller's shell setup or
# CWD. See porting-sdk/RUN_LINT_FORMAT_SPEC.md.
#
# Modes:
#   bash scripts/run-format.sh            # DEFAULT: APPLY — reformat the tree in
#                                         #   place (spotlessApply). Exit 0 on
#                                         #   success even if it changed files.
#   bash scripts/run-format.sh --check    # VERIFY-ONLY (CI) — do not modify; exit
#                                         #   non-zero if anything is unformatted
#                                         #   (spotlessCheck).
#
# google-java-format is the canonical Java formatter (analogous to gofmt/rustfmt),
# so there is no style to bikeshed. Formats BOTH hand-written and generated code;
# the generated REST/SWML/RELAY/SWAIG trees are emitted already-formatted (their
# generators pipe through google-java-format), so --check stays green on them.
# Scope + config live in build.gradle's spotless block.
#
# Idempotent: a second run right after the first produces no diff.

set -euo pipefail

# shellcheck source=scripts/_env.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_env.sh"

MODE="apply"
if [ "${1:-}" = "--check" ]; then
    MODE="check"
elif [ -n "${1:-}" ]; then
    echo "usage: $0 [--check]" >&2
    exit 2
fi

if [ "$MODE" = "check" ]; then
    echo "==> FMT check (spotlessCheck, read-only) — repo: $REPO_ROOT"
    sw_gradle -q spotlessCheck
else
    echo "==> FMT apply (spotlessApply) — repo: $REPO_ROOT"
    sw_gradle -q spotlessApply
    if ! (cd "$REPO_ROOT" && git diff --quiet 2>/dev/null); then
        echo "    (FMT applied formatting to your working tree — review & stage)"
    fi
    # A residual issue spotlessApply can't fix must still fail the gate.
    sw_gradle -q spotlessCheck
fi
