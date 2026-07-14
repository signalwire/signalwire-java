# Artifact Deny Allowlist

The AUTHORITATIVE artifact_deny check is `--listing` mode against the real
published package. The Java published artifacts are the Gradle `jar` and
`-sources.jar` built from `sourceSets.main` (`src/main/java` + `src/main/resources`).
None of the porting-audit artifacts below live under `src/main`, so they never
enter either jar — verified clean:

    jar tf build/libs/signalwire-sdk-<ver>.jar         | artifact_deny.py --port java --listing -   # clean
    jar tf build/libs/signalwire-sdk-<ver>-sources.jar | artifact_deny.py --port java --listing -   # clean

The entries below are only flagged by the `git ls-files` PROXY mode because they
are tracked in-repo (they are load-bearing porting-audit contract files read by
porting-sdk audit scripts). They are excluded from the published package by the
publish task's `sourceSets.main` scope, not by deletion. Allowlisted so the proxy
mode agrees with the authoritative listing.

- CHECKLIST.md — porting-audit contract file; outside src/main, not in the published jar (orchestrator, 2026-07-06)
- DOC_AUDIT_IGNORE.md — porting-audit contract file; outside src/main, not in the published jar (orchestrator, 2026-07-06)
- PORT_ADDITIONS.md — porting-audit contract file; outside src/main, not in the published jar (orchestrator, 2026-07-06)
- PORT_EXAMPLE_OMISSIONS.md — porting-audit contract file; outside src/main, not in the published jar (orchestrator, 2026-07-06)
- PORT_OMISSIONS.md — porting-audit contract file; outside src/main, not in the published jar (orchestrator, 2026-07-06)
- PORT_SIGNATURE_OMISSIONS.md — porting-audit contract file; outside src/main, not in the published jar (orchestrator, 2026-07-06)
- PORT_TEST_OMISSIONS.md — porting-audit contract file; outside src/main, not in the published jar (orchestrator, 2026-07-06)
- REST_COVERAGE_GAPS.md — porting-audit contract file; outside src/main, not in the published jar (orchestrator, 2026-07-06)
- audit_coverage.json — porting-audit contract file; outside src/main, not in the published jar (orchestrator, 2026-07-06)
- audit_coverage_baseline.json — porting-audit contract file; outside src/main, not in the published jar (orchestrator, 2026-07-06)
- port_signatures.json — porting-audit contract file; outside src/main, not in the published jar (orchestrator, 2026-07-06)
- port_signatures.baseline.json — load-bearing SEMVER-DIFF release-floor file; mirrors port_signatures.json; must be at root, must not ship in package (orchestrator, 2026-07-13)
- port_surface.json — porting-audit contract file; outside src/main, not in the published jar (orchestrator, 2026-07-06)
- port_surface_native.json — porting-audit contract file; outside src/main, not in the published jar (orchestrator, 2026-07-06)
- examples/RelayAuditHarness.java — cross-port audit harness; in examples/, not in the published jar (orchestrator, 2026-07-06)
- examples/RestAuditHarness.java — cross-port audit harness; in examples/, not in the published jar (orchestrator, 2026-07-06)
- examples/SkillsAuditHarness.java — cross-port audit harness; in examples/, not in the published jar (orchestrator, 2026-07-06)
