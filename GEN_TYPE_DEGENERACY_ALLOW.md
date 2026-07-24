<!-- ══════════════════════════════════════════════════════════════════════════
BEFORE YOU ADD AN ENTRY TO THIS FILE — READ THIS.

Every entry here is a place the parity checker STOPS comparing. That is a real cost:
a divergence you list is a divergence no gate will ever catch again. So entries must
be RARE, and each one must earn its place. Default to skepticism: assume the entry is
NOT needed and make the case that it is.

The order of preference, always:
  1. FIX THE PORT so it matches the reference (add the missing member; make the
     signature match).
  2. FIX THE EMISSION so idiom folds onto the reference shape — the enumerator/emitter
     canonicalizes your language's spelling onto the oracle's (builder → __init__,
     getters → attributes, Result<T,E> → the plain return, CamelCase → the reference
     name, options-object/kwargs → the expanded param list, RAII/dispose → close).
     MOST divergences are idiom and belong here, not in this file.
  3. FIX THE REFERENCE if the oracle itself is wrong or stale (a Python-only symbol
     that leaked into the contract, a param the reference added and the oracle never
     re-enumerated). Fix Python / the oracle, then re-drift — do not paper over a
     broken reference with a per-port entry.
  4. Only when 1–3 genuinely cannot apply does an entry here become justified.

An entry is JUSTIFIED ONLY IF it is irreducible after correct emission — i.e. the
divergence survives because the two languages genuinely cannot express the same thing,
not because the emitter hasn't folded the idiom yet. If emission COULD fold it, the
entry is a bug in this file; go fix the emitter.

Each entry MUST state WHY, concretely, in one of these forms:
  • ADDITION — this symbol exists in the port but not the reference. Answer: is it
    genuine port-only surface with NO reference twin (say what it is and why the
    reference has no equivalent), or is it IDIOM the emitter should have folded (then
    it does not belong here — fold it)? A convenience/alias/back-compat wrapper is NOT
    a justification.
  • OMISSION — this reference symbol has no port member. Answer: WHY can it not exist
    here — what specific language feature is absent (e.g. no async-context-manager
    protocol, no __init__ method protocol)? "impossible:" means the construct cannot
    be expressed at all; if it merely LOOKS different, that's idiom → fold it, don't
    omit it. Cite a precedent when one exists (e.g. RelayClient omits the same dunder).
  • SIGNATURE — the symbol matches by name but its parameters differ. Answer: is the
    difference a foldable idiom collapse (options-object, leading context/self,
    builder) — then EXPAND it in the signature emitter so names+count match, don't list
    it — or a genuine reference-only parameter with no cross-language analogue?

If you cannot write a crisp, specific WHY that survives the "could emission fold this?"
test, the entry is not ready. Prove it's needed before you add it.
═══════════════════════════════════════════════════════════════════════════════ -->

# GEN-TYPE-DEGENERACY allowlist (java)

Each entry excuses one generated `path:kind:name` finding with a genuine reason.
The gate (`porting-sdk/scripts/gen_type_degeneracy.py`) reads the `- <finding-key> — reason` lines.

## `type: null` always-null placeholder fields

These four public fields carry a JSON-Schema `type: null` in the REST spec — the wire
value is ALWAYS literal `null` for that resource variant (each schema's own description
says so: "Always null for this call type." / "Always null for Fabric subscriber device
legs."). A `null`-only field has no narrower static type than "any JSON value"; the go
port emits the equivalent bare `any` here (go's gate flags only degenerate type-ALIASES,
not `any` FIELDS, so go passes with the same shape). `Object` is the honest Java type for
an always-null field — not a loose escape hatch hiding a real struct, but the actual
`type: null` schema faithfully rendered. Regenerating cannot narrow these without
inventing a type the spec does not declare.

- src/main/java/com/signalwire/sdk/rest/namespaces/generated/types/calling/FabricDeviceLeg.java:loose-alias:status — spec `type: null` ("Always null for Fabric subscriber device legs"); go emits bare `any`; no narrower type exists.
- src/main/java/com/signalwire/sdk/rest/namespaces/generated/types/voice/DialogflowVoiceLog.java:loose-alias:url — spec `type: null` ("Always null for this call type"); go emits bare `any`; no narrower type exists.
- src/main/java/com/signalwire/sdk/rest/namespaces/generated/types/voice/FabricVoiceLog.java:loose-alias:url — spec `type: null` ("Always null for this call type"); go emits bare `any`; no narrower type exists.
- src/main/java/com/signalwire/sdk/rest/namespaces/generated/types/voice/VideoRoomVoiceLog.java:loose-alias:url — spec `type: null` ("Always null for this call type"); go emits bare `any`; no narrower type exists.
