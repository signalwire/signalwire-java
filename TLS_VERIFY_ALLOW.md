# TLS-VERIFY allowlist (java)

Format: `- <check-id> — reason (approver, date)` where `<check-id>` is
`tls-verify-off:<relpath>:<line>` (see `porting-sdk/scripts/tls_verify.py`).

A config-gated opt-in with a SECURE DEFAULT is the ONLY legitimate allowlist
reason. NEVER allowlist a hardcoded verify-off to go green (RULES.md §4).

Currently EMPTY — and it should stay that way.

The one entry this file used to carry (the mcp_gateway `verify_ssl` opt-out in
`McpGatewaySkill`) was deleted on 2026-07-30: `tls_verify.py` now recognizes the
secure-default-gated java idiom directly, so the site passes on its own merits
and needs no exception. The gate proves the `InsecureTrustManager` class is
instantiated ONLY inside the `if (!verifySsl)` guard, and `verify_ssl_parity.py`
independently proves the flag defaults TRUE (verification ON).

That entry was also a standing liability: an allowlist keyed by
`<relpath>:<line>` silently stops matching when anything above the site shifts
the line number, and a stale entry is not an error — it just quietly stops
covering. That is exactly what happened here (the site moved 576 → 617 when
javadoc was added), turning a documented, approved exception into a surprise
RED with no signal about why. Recognizing the idiom in the gate has no such
failure mode.
