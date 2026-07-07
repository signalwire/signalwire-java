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
