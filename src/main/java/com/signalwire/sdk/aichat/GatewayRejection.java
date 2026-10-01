/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

/**
 * A request the {@link ChatGateway} refused, with the HTTP status the browser should see.
 *
 * <p>Deliberately coarse: the browser is told <em>that</em> it was refused and, at most, which of a
 * handful of buckets it fell into. Anything finer would let a caller map out the caps and the
 * allowlist by probing.
 */
public class GatewayRejection extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** HTTP status to send back. */
  private final int status;

  /** Short, fixed explanation that reaches the browser. */
  private final String reason;

  /**
   * Refuse a browser request with the HTTP status the route should return.
   *
   * @param status HTTP status to send back (401 bad key, 403 origin/handle, 400 disallowed method,
   *     413 a request, message or metadata over its size limit, 429 a cap was hit).
   * @param reason short, fixed explanation. It reaches the browser, so it names only the bucket:
   *     for a handle, "malformed handle" (it doesn't parse), "invalid handle" (its signature
   *     doesn't verify) or "expired handle" (it verified but is past its expiry), and never the
   *     caps' values or the allowlist.
   */
  public GatewayRejection(int status, String reason) {
    super(status + ": " + reason);
    this.status = status;
    this.reason = reason;
  }

  /**
   * The HTTP status the browser should see.
   *
   * @return the status code.
   */
  public int getStatus() {
    return status;
  }

  /**
   * The fixed, coarse reason the browser receives as {@code {"error": reason}}.
   *
   * @return the reason.
   */
  public String getReason() {
    return reason;
  }
}
