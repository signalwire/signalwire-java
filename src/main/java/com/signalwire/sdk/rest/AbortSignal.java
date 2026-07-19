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
 * <p>Fidelity is the language's business (see the cross-port design): Java's REST client is
 * synchronous and {@code java.net.http.HttpClient.send} cannot be interrupted mid-flight without a
 * separate thread, so — like the Python reference's {@code threading.Event} check — cancellation is
 * checked cooperatively BETWEEN attempts (the honest, portable minimum). The FIELD exists in every
 * port; how deeply it cuts is per-port.
 *
 * <p>This is a {@link FunctionalInterface}, so an {@link java.util.concurrent.atomic.AtomicBoolean}
 * or any boolean-valued predicate can be adapted inline:
 *
 * <pre>{@code
 * var cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
 * var opts = RequestOptions.builder().abortSignal(cancelled::get).build();
 * }</pre>
 *
 * <p>Mirrors the Python reference's {@code _AbortSignal} protocol ({@code is_set() -> bool}).
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
