# Doc-surface floor — signalwire-java (plan 6.3-doccov)

The A-bar addendum (§6.3) sets a **javadoc coverage floor** on the port's public
surface: a `DOC-SURFACE` rule (report-only at the current %, ratcheted upward, never
regressing) that lives in the shared `DOC-TRUTH` suite so it runs the same way for
every port. This file records the java floor the rule enforces.

## Current floor (report-only baseline)

Measured over hand-written (non-generated) `src/main/java/**` public members —
generated resource DTOs/builders are excluded (they carry their own generator-emitted
javadoc and are ratcheted by the generator, not this floor):

- **public members:** 1612
- **documented:** 926
- **coverage:** **57.4 %**  ← the report-only floor; the ratchet never drops below this.

The rule is **report-only** at this baseline (never blocking at introduction, per
§6.3) and the recorded floor only moves up. Adding a public member without javadoc
lowers the live % below the floor and is the report-only signal to document it.

## Wiring status (porting-sdk dependency — NOT wired in this lane)

The `DOC-SURFACE` measurement + ratchet engine belongs in
`porting-sdk/scripts/suites/doc_truth.py` (a new per-port rule reading each port's
`port_surface.json` for the public-member set and its source for the doc comments),
mirroring how DOC-AUDIT/DOC-ENV already work off the shared surface. That is a
cross-port engine change (it must land on the porting-sdk `plan/a-bar-2026-07-18`
branch and be added to the DOC-TRUTH suite for all ports), so it is left to the
porting-sdk lane. Once the engine lands, it reads this file's floor value and the
`run-ci.sh` DOC-TRUTH line already invokes it — no additional java run-ci wiring is
needed beyond that.
