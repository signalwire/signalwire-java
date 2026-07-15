# Changelog

## [3.1.0] - 2026-07-14

- REST: added the `Projects` resource (`client.projects()`) — full CRUD over
  `/api/projects` (list/get/create/update/delete) plus `rotateSigningKey`, with
  generated typed request/response shapes and wire tests covering each route
  (success and error) against the shared mock. Distinct from the singular
  `project` token namespace (`client.project()`).

## [3.0.2] - 2026-07-13

- REST: the client's resource surface is now fully generated from the shared
  SignalWire OpenAPI specs, replacing the hand-written resource classes — every
  resource, route, and typed request/response shape derives from the canonical
  wire spec, with generated wire tests covering each route (success and error)
  against the shared mock.
- REST: `list()` on paginated resources wires the paginator into an
  iterator-protocol paginator so callers can page through all results
  (follows `links.next`).
- REST errors carry the full `(status, body, url, method)` envelope and are
  raised on any `>= 400` response.
- Wave-1/2/3 hardening: error-envelope, pagination-wired, and dead-public-error
  parity gates; documentation-truth gates (env-var coverage, numeric-count
  claims, accessor references); and package/release-readiness gates
  (SemVer floor, ignore-ledger strict, publish-gated-on-CI, metadata
  consistency).
