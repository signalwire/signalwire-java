# ACCESSOR_TRUTH allowlist

Backtick `method()` mentions in this repo's Markdown that the ACCESSOR-TRUTH gate
flags but that DO resolve to a real Java method — a false positive from the gate's
source method-definition regex, not a doc lie. Each entry names the specific method
and a real reason. This is NOT a place to hide a phantom (a documented method the
SDK truly lacks must be renamed to the real one or dropped). Consumed by
`porting-sdk/scripts/accessor_truth.py`; format: `- <method> — reason (approver, date)`.

- serve — `Service.serve()` is a real public method (src/main/java/com/signalwire/sdk/swml/Service.java:1159), but its signature is `public void serve() throws IOException {`. The gate's Java `def_re` (`\)\s*\{`) requires the opening brace to immediately follow the parameter list and cannot match a method with a `throws` clause between `)` and `{`, so it does not see this genuinely-defined method. Wire-neutral gate-regex limitation, not a doc mismatch (approver: mike, 2026-07-11)
