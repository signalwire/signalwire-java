# ROUTE-COLLISION allowlist (java)

Each entry excuses one proven, human-approved (a) route-split / (b) crud-dup finding.
Gate: `porting-sdk/scripts/route_collision.py`. Key form: `<Class>.<canonical_op>`.

## (a) list_addresses singular-path override — the override is the SOLE live route

`CallFlows` and `ConferenceRooms` declare `list_addresses` on the SINGULAR sub-path
(`/fabric/resources/call_flow/{id}/addresses` and `.../conference_room/{id}/addresses`),
which the fabric REST spec marks as an intentional platform quirk:

    rest-apis/fabric/openapi.yaml:801  "Versions AND addresses live under the SINGULAR
        call_flow sub-path (a real platform quirk) ... Declaring list_addresses here
        overrides the FabricResource base method, which would otherwise use the plural
        collection path."
    rest-apis/fabric/openapi.yaml:1074 (same for conference_room)

In Java this is a TRUE method override: `CallFlows.listAddresses(String, Map<String,String>)`
carries `@Override` with a signature IDENTICAL to `FabricResource.listAddresses(String,
Map<String,String>)`, so the base plural-path method is REPLACED — it is not reachable
through a `CallFlows` / `ConferenceRooms` instance. There is exactly ONE live route for
`list_addresses` on each class: the spec's canonical singular path. The gate flags it
because the surface enumerator still records the inherited base member statically; that is
a static-analysis artifact of Java inheritance, not a real dual route (unlike go/cpp, where
both routes can be dispatched). Resolution per §3a ("pick ONE canonical route") is already
satisfied: the override wins, giving the single spec-canonical URL.

<!-- HUMAN SIGN-OFF REQUIRED before this gate flips enforcing: the two entries below are a
     proven-real exception (Java @Override collapses to one live route = the spec's singular
     path), but per AGENT_RULES §3 an allowlist entry needs explicit written approval. -->

- CallFlows.list_addresses — spec-declared singular-path override (openapi.yaml:801); Java `@Override` replaces the base, single live route = canonical singular path. (pending approval)
- ConferenceRooms.list_addresses — spec-declared singular-path override (openapi.yaml:1074); Java `@Override` replaces the base, single live route = canonical singular path. (pending approval)
