/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * RequestOptions envelope — behavioral contract over the real mock (plan 4.2).
 *
 * <p>These drive a real {@link RestClient}'s {@link HttpClient} through the real {@code
 * java.net.http} transport into the shared {@code mock_signalwire} and assert on the recorded
 * journal — the same journal the REST-COVERAGE gate reads. Retry / timeout are wire-observable: the
 * mock sees N attempts and honors the backoff ordering, so the contract is proven over the real
 * mock, NOT a transport stub. Mirrors signalwire-python {@code
 * tests/unit/rest/test_request_options.py}.
 *
 * <p>Contract pinned here (the oracle):
 *
 * <ul>
 *   <li>{@code retries}: a retryable failure is retried up to {@code retries} extra times; the mock
 *       sees {@code retries + 1} attempts; the final success is returned.
 *   <li>idempotency asymmetry: GET/PUT/DELETE retry on the full {@code retryOnStatus} set;
 *       POST/PATCH retry only on 429/503 (throttles), never 500/502/504.
 *   <li>{@code timeout}: a server-side delay exceeding the timeout raises the transport-error
 *       family.
 *   <li>{@code abortSignal}: set before a request raises the transport-error family.
 *   <li>per-request options shallow-override the client default.
 * </ul>
 */
class RequestOptionsTest {

  private static final String ADDRESSES_ENDPOINT_ID = "fabric.list_fabric_addresses";
  private static final String GET_PATH = "/fabric/addresses";
  private static final String GET_JOURNAL_PATH = "/api/fabric/addresses";
  private static final String CREATE_ENDPOINT_ID = "relay-rest.create_address";
  private static final String POST_PATH = "/relay/rest/addresses";
  private static final String POST_JOURNAL_PATH = "/api/relay/rest/addresses";

  private static Map<String, Object> errorsBody() {
    return Map.of("errors", List.of(Map.of("code", "X")));
  }

  private static long countJournal(MockTest.Harness mock, String journalPath, String method) {
    return mock.journal().stream()
        .filter(e -> journalPath.equals(e.path) && method.equals(e.method))
        .count();
  }

  // ── Retry contract ──────────────────────────────────────────────

  @Test
  void getRetries503ThenSucceeds() {
    MockTest.Bound b = MockTest.newClient();
    // Arm a single 503; the default synthesized 200 follows it. With retries=1 the
    // client retries the 503 into the 200 -> 2 attempts.
    b.harness.scenarioSetRaw(ADDRESSES_ENDPOINT_ID, 503, errorsBody());
    Map<String, Object> result =
        b.client
            .getHttpClient()
            .get(GET_PATH, null, RequestOptions.builder().retries(1).retryBackoff(0.0).build());
    assertNotNull(result);
    assertEquals(
        2, countJournal(b.harness, GET_JOURNAL_PATH, "GET"), "expected 2 attempts (503 then 200)");
  }

  @Test
  void noRetriesByDefaultRaisesOnFirstFailure() {
    MockTest.Bound b = MockTest.newClient();
    // Default retries=0: the first non-2xx raises immediately (retries are opt-in).
    b.harness.scenarioSetRaw(ADDRESSES_ENDPOINT_ID, 503, errorsBody());
    RestError e =
        assertThrows(RestError.class, () -> b.client.getHttpClient().get(GET_PATH, null, null));
    assertEquals(503, e.getStatusCode());
    assertEquals(1, countJournal(b.harness, GET_JOURNAL_PATH, "GET"), "default must not retry");
  }

  @Test
  void retriesExhaustedRaisesLastError() {
    MockTest.Bound b = MockTest.newClient();
    // Two 503s + retries=1 => attempts = 2, both 503 => raise the 503.
    b.harness.scenarioSetRaw(ADDRESSES_ENDPOINT_ID, 503, errorsBody());
    b.harness.scenarioSetRaw(ADDRESSES_ENDPOINT_ID, 503, errorsBody());
    RestError e =
        assertThrows(
            RestError.class,
            () ->
                b.client
                    .getHttpClient()
                    .get(
                        GET_PATH,
                        null,
                        RequestOptions.builder().retries(1).retryBackoff(0.0).build()));
    assertEquals(503, e.getStatusCode());
    assertEquals(
        2, countJournal(b.harness, GET_JOURNAL_PATH, "GET"), "retries=1 => exactly 2 attempts");
  }

  // ── Idempotency asymmetry ───────────────────────────────────────

  @Test
  void postDoesNotRetry500() {
    MockTest.Bound b = MockTest.newClient();
    // 500 is NOT retryable for a non-idempotent method even with retries armed.
    b.harness.scenarioSetRaw(CREATE_ENDPOINT_ID, 500, Map.of("error", "x"));
    RestError e =
        assertThrows(
            RestError.class,
            () ->
                b.client
                    .getHttpClient()
                    .post(
                        POST_PATH,
                        Map.of("label", "x"),
                        RequestOptions.builder().retries(2).retryBackoff(0.0).build()));
    assertEquals(500, e.getStatusCode());
    assertEquals(
        1,
        countJournal(b.harness, POST_JOURNAL_PATH, "POST"),
        "POST must not retry a 500 (side-effect safety)");
  }

  @Test
  void postDoesRetry503() {
    MockTest.Bound b = MockTest.newClient();
    // 503 (throttle) IS retryable even for a non-idempotent method -> retries into 200/201.
    b.harness.scenarioSetRaw(CREATE_ENDPOINT_ID, 503, Map.of("error", "x"));
    b.client
        .getHttpClient()
        .post(
            POST_PATH,
            Map.of("label", "x"),
            RequestOptions.builder().retries(1).retryBackoff(0.0).build());
    assertEquals(
        2,
        countJournal(b.harness, POST_JOURNAL_PATH, "POST"),
        "POST retries a 503 throttle (safe): 503 then success");
  }

  // ── Timeout ─────────────────────────────────────────────────────

  @Test
  void slowResponseTimesOut() {
    MockTest.Bound b = MockTest.newClient();
    // Arm a 200 delayed 400ms; a 100ms timeout must fire -> transport error.
    b.harness.scenarioSetFull(
        ADDRESSES_ENDPOINT_ID, 200, Map.of("data", List.of(), "links", Map.of()), null, 400);
    assertThrows(
        SignalWireRestTransportError.class,
        () ->
            b.client
                .getHttpClient()
                .get(GET_PATH, null, RequestOptions.builder().timeout(0.1).build()));
  }

  // ── Abort signal ────────────────────────────────────────────────

  @Test
  void presetAbortRaisesTransportError() {
    MockTest.Bound b = MockTest.newClient();
    AtomicBoolean cancelled = new AtomicBoolean(true);
    assertThrows(
        SignalWireRestTransportError.class,
        () ->
            b.client
                .getHttpClient()
                .get(GET_PATH, null, RequestOptions.builder().abortSignal(cancelled::get).build()));
    // Nothing reached the mock — cancelled before the send.
    assertEquals(
        0,
        countJournal(b.harness, GET_JOURNAL_PATH, "GET"),
        "aborted request must not reach the server");
  }

  // ── Per-request override ────────────────────────────────────────

  @Test
  void perRequestRetriesOverrideClientDefault() {
    // Client default = no retries; per-request opts in to 1 retry.
    MockTest.Bound b = MockTest.newClient(RequestOptions.builder().retries(0).build());
    b.harness.scenarioSetRaw(ADDRESSES_ENDPOINT_ID, 503, errorsBody());
    Map<String, Object> result =
        b.client
            .getHttpClient()
            .get(GET_PATH, null, RequestOptions.builder().retries(1).retryBackoff(0.0).build());
    assertNotNull(result);
    assertEquals(
        2,
        countJournal(b.harness, GET_JOURNAL_PATH, "GET"),
        "per-request retries=1 overrides the client default retries=0");
  }

  @Test
  void clientDefaultRetriesAppliesWithoutPerRequest() {
    // Client default = 1 retry; no per-request opts. The 503 retries into the 200.
    MockTest.Bound b =
        MockTest.newClient(RequestOptions.builder().retries(1).retryBackoff(0.0).build());
    b.harness.scenarioSetRaw(ADDRESSES_ENDPOINT_ID, 503, errorsBody());
    Map<String, Object> result = b.client.getHttpClient().get(GET_PATH, null, null);
    assertNotNull(result);
    assertEquals(
        2,
        countJournal(b.harness, GET_JOURNAL_PATH, "GET"),
        "client-default retries=1 applies when no per-request override is given");
  }
}
