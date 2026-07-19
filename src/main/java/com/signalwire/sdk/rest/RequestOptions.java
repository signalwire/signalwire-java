/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * RequestOptions — the REST request-options envelope (plan 4.2).
 *
 * <p>An immutable value object controlling per-request transport behavior: timeout, retries (with
 * an idempotency-aware retry policy + exponential backoff), and cooperative cancellation. Supplied
 * at two levels:
 *
 * <ul>
 *   <li><b>Client default</b>: {@code RestClient.builder().requestOptions(...)} stored on the
 *       {@link HttpClient} and applied to every request.
 *   <li><b>Per-request override</b>: each HTTP verb accepts an optional {@code RequestOptions} that
 *       <i>shallow-overrides</i> the client default for that one call — an unset ({@code null})
 *       field falls back to the client default, then the built-in default.
 * </ul>
 *
 * <p>Every field is optional; a {@code null} field means "inherit" and resolves at apply-time to
 * the client default and then the built-in floor (see {@link RequestOptionsSupport#resolve}). The
 * timeout + retry semantics are the oracle-pinned, wire-observable contract (the mock sees N
 * attempts and honors the backoff ordering + the POST/PATCH idempotency asymmetry). {@code
 * abortSignal} fidelity is per-port idiom (see {@link AbortSignal}).
 *
 * <p>Instances are created via {@link #builder()} (all fields optional) and are deeply immutable
 * (the {@code retryOnStatus} set is defensively copied and returned unmodifiable). Mirrors the
 * Python reference's frozen {@code RequestOptions} dataclass.
 */
public final class RequestOptions {

  private final Double timeout;
  private final Integer retries;
  private final Set<Integer> retryOnStatus;
  private final Double retryBackoff;
  private final AbortSignal abortSignal;

  private RequestOptions(Builder b) {
    this.timeout = b.timeout;
    this.retries = b.retries;
    this.retryOnStatus =
        b.retryOnStatus == null
            ? null
            : Collections.unmodifiableSet(new LinkedHashSet<>(b.retryOnStatus));
    this.retryBackoff = b.retryBackoff;
    this.abortSignal = b.abortSignal;
  }

  /**
   * Max wall-clock seconds per attempt; on exceed the request raises the typed transport error.
   * {@code null} = inherit (built-in default {@code 30.0}).
   */
  public Double timeout() {
    return timeout;
  }

  /**
   * Number of RETRY attempts (total attempts = {@code retries + 1}) on a retryable failure. {@code
   * null} = inherit (built-in default {@code 0} — retries are opt-in resilience; the no-retry
   * behavior stays the default).
   */
  public Integer retries() {
    return retries;
  }

  /**
   * HTTP statuses that trigger a retry for an idempotent method. {@code null} = inherit (built-in
   * default {@code {429, 500, 502, 503, 504}}). The returned set is unmodifiable.
   */
  public Set<Integer> retryOnStatus() {
    return retryOnStatus;
  }

  /**
   * Base seconds for exponential backoff between retries ({@code backoff * 2^(attempt-1)}),
   * honoring {@code Retry-After} when present. {@code null} = inherit (built-in default {@code
   * 0.5}).
   */
  public Double retryBackoff() {
    return retryBackoff;
  }

  /**
   * Cooperative-cancellation object checked before each attempt. {@code null} = inherit (built-in
   * default none).
   */
  public AbortSignal abortSignal() {
    return abortSignal;
  }

  /**
   * Return a copy of this with every set (non-{@code null}) field of {@code override} applied.
   *
   * <p>This is the per-request-over-client-default shallow merge: an unset field on {@code
   * override} leaves this instance's value intact. A {@code null} {@code override} returns this
   * unchanged. Mirrors the Python reference's {@code RequestOptions.merge}.
   *
   * @param override the per-request options whose set fields win (may be {@code null})
   * @return the merged options
   */
  public RequestOptions merge(RequestOptions override) {
    if (override == null) {
      return this;
    }
    return new Builder()
        .timeout(override.timeout != null ? override.timeout : this.timeout)
        .retries(override.retries != null ? override.retries : this.retries)
        .retryOnStatus(override.retryOnStatus != null ? override.retryOnStatus : this.retryOnStatus)
        .retryBackoff(override.retryBackoff != null ? override.retryBackoff : this.retryBackoff)
        .abortSignal(override.abortSignal != null ? override.abortSignal : this.abortSignal)
        .build();
  }

  /** A new {@link Builder} with every field unset (inherit). */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for {@link RequestOptions}. Every setter is optional; an unset field is {@code
   * null} ("inherit"). Mirrors the Python reference's keyword-only dataclass constructor.
   */
  public static final class Builder {
    private Double timeout;
    private Integer retries;
    private Set<Integer> retryOnStatus;
    private Double retryBackoff;
    private AbortSignal abortSignal;

    /** Per-attempt timeout in seconds ({@code null} = inherit). */
    public Builder timeout(Double timeout) {
      this.timeout = timeout;
      return this;
    }

    /** Number of retry attempts ({@code null} = inherit; total attempts = {@code retries + 1}). */
    public Builder retries(Integer retries) {
      this.retries = retries;
      return this;
    }

    /** Statuses to retry an idempotent method on ({@code null} = inherit). */
    public Builder retryOnStatus(Set<Integer> retryOnStatus) {
      this.retryOnStatus = retryOnStatus;
      return this;
    }

    /** Base backoff seconds ({@code null} = inherit). */
    public Builder retryBackoff(Double retryBackoff) {
      this.retryBackoff = retryBackoff;
      return this;
    }

    /** Cooperative-cancellation signal ({@code null} = inherit / none). */
    public Builder abortSignal(AbortSignal abortSignal) {
      this.abortSignal = abortSignal;
      return this;
    }

    /** Build an immutable {@link RequestOptions}. */
    public RequestOptions build() {
      return new RequestOptions(this);
    }
  }
}
