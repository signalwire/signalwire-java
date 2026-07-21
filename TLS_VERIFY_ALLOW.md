# TLS-VERIFY allowlist (java)

Format: `- <check-id> — reason (approver, date)` where `<check-id>` is
`tls-verify-off:<relpath>:<line>` (see `porting-sdk/scripts/tls_verify.py`).

A config-gated opt-in with a SECURE DEFAULT is the ONLY legitimate allowlist
reason. NEVER allowlist a hardcoded verify-off to go green (RULES.md §4).

- tls-verify-off:src/main/java/com/signalwire/sdk/skills/builtin/McpGatewaySkill.java:576 — mcp_gateway skill's `verify_ssl` opt-out. The all-trusting `InsecureTrustManager` is constructed ONLY inside the `if (!verifySsl)` guard in `httpClient()`, and `verify_ssl` defaults TRUE (verification ON — the secure default the python reference endorses via `verify=self.verify_ssl`). The verify_ssl_parity gate independently proves the flag defaults secure. This is a named nested class (not inline) so the surface enumerator scopes its interface methods to the class, not the public skill surface; the class-def line is what the line-based gate flags, but the site is genuinely secure-default-gated. (burn-java-mcp, 2026-07-20)
