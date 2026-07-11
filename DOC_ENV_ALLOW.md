# DOC_ENV allowlist

Env-var tokens that appear in this repo's Markdown but are legitimately NOT read
by the Java SDK's own configuration code — an illustrative or other-context
mention rather than a knob the SDK promises. Each entry names the specific var and
a real reason. This is NOT a place to silence a genuine doc/code mismatch (a real
knob the code reads must be documented; a doc lie must be removed). Consumed by
`porting-sdk/scripts/doc_env.py`; format: `- <VAR_NAME> — reason (approver, date)`.

- SIGNALWIRE_RELAY_HOST — not an SDK config knob; the RELAY client resolves its host from `SIGNALWIRE_SPACE` or the `DEFAULT_RELAY_HOST` literal (relay/RelayClient.java), never from this var. The only mentions are in EXAMPLES_RUN_ALLOW.md prose describing the porting-sdk RELAY audit-driver fixture, which injects `SIGNALWIRE_RELAY_HOST` + a loopback WS port into the audit harness — a test-fixture env, not SDK-read (approver: mike, 2026-07-11)
