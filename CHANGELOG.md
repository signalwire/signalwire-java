# Changelog

## [4.0.0] - 2026-07-15

- REST (BREAKING): `RestError` now carries the full request `url` (including the
  query string) alongside the existing `path`, so a caller can see the exact
  endpoint that failed. The `RestError` constructor gained a `url` parameter
  (`RestError(statusCode, method, path, url, responseBody)`) and a `getUrl()`
  accessor; the error message now prints the full url instead of the bare path.
  Callers constructing `RestError` directly must pass the url. This mirrors the
  reference decision that an error envelope carries the full url, not a path.
- REST: transport-level failures (connection refused, DNS failure, reset, TLS
  error) that never reach an HTTP response are now wrapped in the typed
  `SignalWireRestTransportError` (a `RestError` subclass) instead of leaking a
  bare `IOException`. A single `catch (RestError)` now handles both an HTTP-error
  response and a transport failure; `getStatusCode()` returns `0` (the port's
  sentinel for "no HTTP status") and the underlying transport exception is
  preserved as the cause. Mirrors the reference `SignalWireRestTransportError`.
- RELAY (fix): a `java.net.http.HttpClient.send()` `InterruptedException` in the
  REST layer now restores the thread interrupt status before rethrowing, instead
  of swallowing the cancellation.
- RELAY (fix): `Action.waitForCompletion()` (and its timeout overload) no longer
  swallow an `InterruptedException` — the thread's interrupt status is re-asserted
  so a cancelled wait propagates up the stack.
- RELAY: `RelayClient` now honors the `RELAY_MAX_ACTIVE_CALLS` env var and a
  `maxActiveCalls(int)` builder option — inbound calls past the cap are logged and
  dropped, matching the reference `Client(max_active_calls=...)`.

## [3.2.0] - 2026-07-14

- REST: added the `Messages` resource (`client.messages()`) — send and redact
  messages over `/api/messaging/messages` (`create` → POST, `update` → PATCH
  `/{message_id}` for redaction), with generated typed request shapes (builder +
  `extras` door) and wire tests covering each route (success and error) against
  the shared mock. Distinct from the message logs namespace (`client.logs()`
  `.messages()`).

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
