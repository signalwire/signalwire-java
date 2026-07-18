# WIRE_VIOLATIONS_ALLOW.md — signed exceptions to the STRICT-MOCKS wire-truth gate

The STRICT-MOCKS consumer (`porting-sdk/scripts/assert_no_wire_violations.py`, wired
into REST-COVERAGE / EXAMPLES-RUN / SNIPPET-RUN) reads the mock journal after a run
and fails on ANY `wire_violation` — a request/frame that put a shape on the wire the
OpenAPI/RELAY spec does not declare (an undeclared query param, an unknown body key,
an unknown frame field). A wire violation is a spec bug or a real defect; the fix is
to make the wire match the spec, NOT to allowlist it.

This file exists for the rare, genuinely-justified exception, and each entry needs a
human-signed reason. Format (one per line):

    - <kind>:<name> — reason (approver, date)

where `<kind>` is the violation kind (`unknown_query_param`, `unknown_body_key`,
`unknown_frame_field`, `duplicate_command_id`) and `<name>` is the offending
key/param name. A bare `kind:name` with no ` — reason` is NOT matched, so it cannot
silently widen the allowlist.

## Currently empty

No entries.

Two known spec gaps surface as journaled violations under the wired REST-COVERAGE
gate today, but are deliberately NOT allowlisted here (a name-only token would
over-broadly mask any future real violation on the many endpoints that legitimately
declare these same param names elsewhere):

  * `page_size` on `relay-rest.list_recordings` — the spec's `list_recordings` op has
    no declared `page_size` param while every sibling `list_*` op does. Owner-approved
    to FIX THE SPEC; tracked on porting-sdk branch `fix/recordings-pagination-spec`.
    Do NOT strip the covering test (`SmallNamespacesMockTest.Recordings.list`).
  * `cursor` on `fabric.list_fabric_addresses` — same class: the fabric list ops
    declare no `cursor` param, but the server returns a `links.next` cursor URL that
    the SDK's generic `PaginatedIterator` replays as a `?cursor=` param. Tracked
    separately as a test-fixture adjudication. Do NOT strip the covering tests
    (`PaginationMockTest.pagesThroughAllItems` / `.resourcePaginateWalksAllPages`).

Until those upstream fixes land, REST-COVERAGE will show exactly these 3 violations
(2x cursor + 1x page_size) and no others — any additional violation is a real defect.
