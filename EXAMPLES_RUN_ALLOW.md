# EXAMPLES_RUN allowlist

Examples that LEGITIMATELY require real credentials or a live/driver harness the
mock SignalWire environment cannot provide, and so are skipped by the EXAMPLES-RUN
gate (`porting-sdk/scripts/examples_run.py`). Each entry names the missing
dependency and the approver. The mock injects only `SIGNALWIRE_*` creds + a
loopback REST endpoint; it does NOT provide third-party API keys, real datasphere
document IDs, or the audit-driver fixture env (`REST_OPERATION` / `SKILL_NAME` /
`SIGNALWIRE_RELAY_HOST` + a loopback fixture port), which these examples require.
This is NOT a place to hide a real example bug; every entry names a concrete
external requirement. Mirrors the python + ruby EXAMPLES_RUN_ALLOW precedent.

- examples/QuickstartRest.java — issues a real fabric.aiAgents().create() REST call on load against the hardcoded quickstart space; needs real SignalWire project creds (fails against mock), mirrors python/ruby quickstart_rest allow (approver: mike, 2026-07-09)
- examples/JokeAgent.java — requires a real API_NINJAS_KEY (jokes API); example System.exit(1)s without it by design (approver: mike, 2026-07-09)
- examples/JokeSkillDemo.java — same: requires real API_NINJAS_KEY for the joke skill (approver: mike, 2026-07-09)
- examples/WebSearchAgent.java — requires real GOOGLE_SEARCH_API_KEY + GOOGLE_SEARCH_ENGINE_ID (Google Custom Search); System.exit(1)s without them (approver: mike, 2026-07-09)
- examples/WebSearchMultiInstanceDemo.java — same: requires real GOOGLE_SEARCH_API_KEY + GOOGLE_SEARCH_ENGINE_ID (approver: mike, 2026-07-09)
- examples/DatasphereServerlessEnv.java — requires real DATASPHERE_DOCUMENT_ID + datasphere creds, not mockable (approver: mike, 2026-07-09)
- examples/DatasphereWebhookEnvDemo.java — requires real DATASPHERE_DOCUMENT_ID + datasphere creds, not mockable (approver: mike, 2026-07-09)
- examples/RelayAuditHarness.java — RELAY audit driver probe; needs porting-sdk audit_relay_handshake to inject SIGNALWIRE_RELAY_HOST + a loopback WS fixture, not a standalone run (approver: mike, 2026-07-09)
- examples/RestAuditHarness.java — REST audit driver probe; needs porting-sdk audit_rest_transport to inject REST_OPERATION + REST_FIXTURE_URL (orFail), not a standalone run (approver: mike, 2026-07-09)
- examples/SkillsAuditHarness.java — skills audit driver probe; needs porting-sdk audit_skills_dispatch to inject SKILL_NAME + fixture (orFail), not a standalone run (approver: mike, 2026-07-09)
