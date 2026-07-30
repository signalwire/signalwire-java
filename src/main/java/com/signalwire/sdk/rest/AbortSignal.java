/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

/**
 * Cooperative-cancellation primitive for the REST request-options envelope (plan 4.2).
 *
 * <p>An {@code AbortSignal} is anything that can answer "has this request been cancelled?". The
 * {@link HttpClient} retry loop checks {@link #isSet()} BEFORE each attempt; when it returns {@code
 * true} the request raises the typed transport error ({@link SignalWireRestTransportError}) instead
 * of sending, so a cancelled request never hits the wire.
 *
 * <p><strong>Cancellation is cooperative, not pre-emptive.</strong> This SDK's REST client is
 * synchronous and {@code java.net.http.HttpClient.send} cannot be interrupted mid-flight without a
 * separate thread, so the signal is checked BETWEEN attempts. Setting it will not tear down a
 * request that is already in flight; it prevents the next attempt from being sent.
 *
 * <p>This is a {@link FunctionalInterface}, so an {@link java.util.concurrent.atomic.AtomicBoolean}
 * or any boolean-valued predicate can be adapted inline:
 *
 * <pre>{@code
 * var cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
 * var opts = RequestOptions.builder().abortSignal(cancelled::get).build();
 * }</pre>
 */
@FunctionalInterface
public interface AbortSignal {

  /**
   * Whether cancellation has been requested. Checked before each REST attempt; a {@code true}
   * result aborts the request with a typed transport error before it is sent.
   *
   * @return {@code true} if the request should be cancelled
   */
  boolean isSet();
}
