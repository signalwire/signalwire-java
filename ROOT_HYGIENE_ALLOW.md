# Root Hygiene Allowlist

Each line excuses a specific repo-root file that `scripts/root_hygiene.py` flags.
These are LOAD-BEARING porting-audit contract files: `porting-sdk` audit scripts
(and this port's own `scripts/`) read them at the repo root by relative path, so
they cannot move to `eng/` without breaking the shared audit pipeline.

- ROOT_HYGIENE_ALLOW.md — this allowlist file itself (root_hygiene.py contract file) (orchestrator, 2026-07-06)
- ARTIFACT_DENY_ALLOW.md — artifact_deny.py allowlist contract file (orchestrator, 2026-07-06)
- GEN_TYPE_DEGENERACY_ALLOW.md — gen_type_degeneracy.py allowlist contract file, read at repo root by the GEN-TYPE-DEGENERACY gate (user-approved 728fd38, 2026-07-07)
- ROUTE_COLLISION_ALLOW.md — route_collision.py allowlist contract file, read at repo root by the ROUTE-COLLISION gate (user-approved 728fd38, 2026-07-07)
- CHECKLIST.md — required audit-contract file read by porting-sdk audit scripts (orchestrator, 2026-07-06)
- DOC_AUDIT_IGNORE.md — required audit-contract file read by porting-sdk audit scripts (orchestrator, 2026-07-06)
- INTENTIONAL_THIN_TESTS.md — required audit-contract file read by porting-sdk audit_no_cheat_tests.py (orchestrator, 2026-07-06)
- PORT_ADDITIONS.md — required audit-contract file read by porting-sdk audit scripts (orchestrator, 2026-07-06)
- PORT_EXAMPLE_OMISSIONS.md — required audit-contract file read by porting-sdk audit_example_parity.py (orchestrator, 2026-07-06)
- PORT_OMISSIONS.md — required audit-contract file read by porting-sdk audit scripts (orchestrator, 2026-07-06)
- PORT_SIGNATURE_OMISSIONS.md — required audit-contract file read by porting-sdk audit scripts (orchestrator, 2026-07-06)
- PORT_TEST_OMISSIONS.md — required audit-contract file read by porting-sdk audit_test_parity.py (orchestrator, 2026-07-06)
- REST_COVERAGE_GAPS.md — required audit-contract file read by porting-sdk audit scripts (orchestrator, 2026-07-06)
- audit_coverage.json — required audit-contract file read/written by porting-sdk audit_coverage_map.py at port root (orchestrator, 2026-07-06)
- audit_coverage_baseline.json — required audit-contract baseline read by porting-sdk audit_coverage_map.py at port root (orchestrator, 2026-07-06)
- port_signatures.json — required audit-contract file read by porting-sdk diff/drift scripts at port root (orchestrator, 2026-07-06)
- port_surface.json — required audit-contract file read by porting-sdk audit_docs/ignore_ledger/freshness scripts at port root (orchestrator, 2026-07-06)
- port_surface_native.json — required audit-contract file read/written by this port's scripts/enumerate_surface.py at port root (orchestrator, 2026-07-06)
