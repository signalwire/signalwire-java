/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import java.util.Set;

/**
 * Resolution + retry-policy helpers for the REST request-options envelope (plan 4.2).
 *
 * <p>Java has no module-level free functions, so the reference's module-level {@code resolve} and
 * {@code status_is_retryable} (in {@code signalwire.rest._request_options}) are hosted here as
 * static methods and projected back to their canonical free-function homes by the signature/surface
 * enumerators (FREE_FUNCTION_PROJECTIONS). {@link EffectiveOptions} is the port's stand-in for the
 * reference's private {@code _EffectiveOptions} — every field concrete, so the request loop reads
 * values without re-checking defaults on every attempt.
 */
public final class RequestOptionsSupport {

  private RequestOptionsSupport() {}

  // The built-in defaults (the contract floor). A null RequestOptions field means
  // "inherit"; these are what an unset field resolves to at apply-time.
  static final double DEFAULT_TIMEOUT = 30.0;
  static final int DEFAULT_RETRIES = 0;
  static final Set<Integer> DEFAULT_RETRY_ON_STATUS = Set.of(429, 500, 502, 503, 504);
  static final double DEFAULT_RETRY_BACKOFF = 0.5;

  // Methods with no server-side side effect — safe to retry on any retryable status.
  // POST/PATCH are excluded: they may create/mutate, so they retry ONLY on a throttle
  // (429/503), never blindly on 500/502/504, to avoid duplicate side effects. This
  // asymmetry is part of the pinned contract.
  private static final Set<String> IDEMPOTENT_METHODS =
      Set.of("GET", "PUT", "DELETE", "HEAD", "OPTIONS");

  /**
   * A {@link RequestOptions} with every field resolved to a concrete value. Produced by {@link
   * #resolve}; no {@code null} remains. Mirrors the reference's private {@code _EffectiveOptions}.
   */
  public record EffectiveOptions(
      double timeout,
      int retries,
      Set<Integer> retryOnStatus,
      double retryBackoff,
      AbortSignal abortSignal) {}

  /**
   * Resolve the effective options: per-request over client-default over built-in.
   *
   * <p>A {@code null} field inherits the next level down; the built-in defaults are the floor. The
   * result has every field concrete. Mirrors the reference's module-level {@code resolve}.
   *
   * @param clientDefault the client-level default options (may be {@code null})
   * @param perRequest the per-request override (may be {@code null})
   * @return the effective options with every field resolved
   */
  public static EffectiveOptions resolve(RequestOptions clientDefault, RequestOptions perRequest) {
    RequestOptions base = clientDefault != null ? clientDefault : RequestOptions.builder().build();
    RequestOptions merged = base.merge(perRequest);
    double timeout = merged.timeout() != null ? merged.timeout() : DEFAULT_TIMEOUT;
    int retries = merged.retries() != null ? merged.retries() : DEFAULT_RETRIES;
    Set<Integer> retryOnStatus =
        merged.retryOnStatus() != null ? merged.retryOnStatus() : DEFAULT_RETRY_ON_STATUS;
    double retryBackoff =
        merged.retryBackoff() != null ? merged.retryBackoff() : DEFAULT_RETRY_BACKOFF;
    return new EffectiveOptions(
        timeout, retries, retryOnStatus, retryBackoff, merged.abortSignal());
  }

  /**
   * Whether an HTTP {@code status} for {@code method} should trigger a retry.
   *
   * <p>Idempotent methods (GET/PUT/DELETE) retry on the full {@code retryOnStatus} set.
   * Non-idempotent methods (POST/PATCH) retry only on 429/503 (the Retry-After-bearing throttles,
   * which mean "the request was NOT processed, back off"), never on 500/502/504, to avoid replaying
   * a side effect that may have partially applied. Mirrors the reference's {@code
   * status_is_retryable}.
   *
   * @param method the HTTP method
   * @param status the HTTP status returned
   * @param opts the resolved effective options
   * @return {@code true} if the request should be retried
   */
  public static boolean statusIsRetryable(String method, int status, EffectiveOptions opts) {
    if (!opts.retryOnStatus().contains(status)) {
      return false;
    }
    if (IDEMPOTENT_METHODS.contains(method.toUpperCase(java.util.Locale.ROOT))) {
      return true;
    }
    // Non-idempotent: only the throttle statuses.
    return status == 429 || status == 503;
  }
}
