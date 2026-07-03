#!/usr/bin/env bash
# run-lint.sh — the LINT entry point for signalwire-java
# (tools: Error Prone + Checkstyle).
#
# The SINGLE entry point for linting, callable from ANY directory by run-ci, an
# agent, or a human — the tool environment is self-bootstrapped (scripts/_env.sh:
# Gradle wrapper + JAVA_HOME) so it never depends on the caller's shell setup or
# CWD. See porting-sdk/RUN_LINT_FORMAT_SPEC.md.
#
# Two blocking layers, both burned to zero findings:
#   * Error Prone  — compile-time bug patterns, warnings-as-errors (driven by the
#                    `build -x test` compile).
#   * Checkstyle   — config/checkstyle/checkstyle.xml (checkstyleMain +
#                    checkstyleTest tasks).
# `clean` forces Error Prone to re-run over the full source (no incremental skip).
# Reports findings and exits non-zero on any finding.
#
# --no-build-cache is LOAD-BEARING here: Error Prone runs as a javac plugin inside
# compileJava, and compileJava's OUTPUT (the .class files) is cacheable. With the
# build cache on (sw_gradle adds --build-cache; gradle.properties sets
# org.gradle.caching=true), a `clean` would be defeated — Gradle would restore the
# compiled classes FROM-CACHE and Error Prone would NOT re-execute, silently
# masking a new lint finding. Disabling the cache for THIS invocation keeps the
# "clean forces a real recompile → Error Prone actually runs" guarantee. The
# daemon (warm JVM) is unaffected and still applies. Command-line --no-build-cache
# wins over both --build-cache and the gradle.properties setting.
#
# Java's linters (Error Prone / Checkstyle) have no safe autofix, so this is
# report-only — there is no --fix mode (per RUN_LINT_FORMAT_SPEC.md: "otherwise
# report-only"). Run `bash scripts/run-format.sh` for formatting fixes.

set -euo pipefail

# shellcheck source=scripts/_env.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_env.sh"

if [ -n "${1:-}" ]; then
    echo "usage: $0            # Java lint is report-only (no autofix)" >&2
    exit 2
fi

echo "==> LINT (errorprone warnings-as-errors + checkstyle, zero findings) — repo: $REPO_ROOT"
sw_gradle -q --no-build-cache clean build -x test checkstyleMain checkstyleTest
