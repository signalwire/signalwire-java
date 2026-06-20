/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

/**
 * The lifecycle state of a RELAY {@link Call}, as a typed, compile-time-checked closed set.
 *
 * <p>The RELAY server pushes the current call state as a bare string on every {@code
 * calling.call.state} event ({@code created} → {@code ringing} → {@code answered} → {@code ending}
 * → {@code ended}). This enum mirrors the five values the Python reference declares in {@code
 * signalwire/relay/constants.py} as {@code CALL_STATE_*} ({@link Constants#CALL_STATE_CREATED} …
 * {@link Constants#CALL_STATE_ENDED}).
 *
 * <p>It is exposed <em>alongside</em> the existing string getter, never instead of it: {@link
 * Call#getState()} keeps returning the raw wire string (parity with Python, forward-compatible with
 * any value the server later adds), while {@link Call#getCallState()} returns {@code
 * Optional<CallState>} for callers who want a typed, switch-exhaustive handle and a {@link
 * #isTerminal()} predicate.
 *
 * <pre>{@code
 * String raw = call.getState();              // "answered" (always present, parity)
 * Optional<CallState> typed = call.getCallState();
 * if (typed.map(CallState::isTerminal).orElse(false)) {
 *     // call has reached ENDED
 * }
 * }</pre>
 *
 * <p><strong>Server-emitted, growable.</strong> These values mirror what the server sends and the
 * set can grow in a future protocol revision. So {@link #fromWire(String)} returns {@code null}
 * (and {@link Call#getCallState()} {@code Optional.empty()}) for an unrecognised value rather than
 * throwing — an unknown state must never crash event dispatch. This is the Java analog of Rust's
 * {@code #[non_exhaustive]} + a fallible {@code from_str}.
 *
 * <p><strong>This is a DISTINCT vocabulary from {@link DialState} and {@link
 * MessageState}.</strong> A {@code Call}'s lifecycle state ({@code
 * created/ringing/answered/ending/ended}) is not the dial <em>outcome</em> ({@code
 * dialing/answered/failed}) and not a message delivery state ({@code queued/…/delivered}). The
 * three are modelled as three separate enums and are never conflated — mirroring the three separate
 * {@code *_STATE_*} blocks in the Python reference's {@code constants.py}.
 */
public enum CallState {

  /** The call object has been created but no leg is ringing yet. */
  CREATED(Constants.CALL_STATE_CREATED),
  /** The call is ringing (alerting). */
  RINGING(Constants.CALL_STATE_RINGING),
  /** The call has been answered and media is flowing. */
  ANSWERED(Constants.CALL_STATE_ANSWERED),
  /** The call is in the process of tearing down. */
  ENDING(Constants.CALL_STATE_ENDING),
  /** Terminal: the call has ended. */
  ENDED(Constants.CALL_STATE_ENDED);

  private final String value;

  CallState(String value) {
    this.value = value;
  }

  /**
   * The canonical wire string for this state ({@code "created"} / {@code "ringing"} / {@code
   * "answered"} / {@code "ending"} / {@code "ended"}) — exactly the value the server sends on
   * {@code calling.call.state}. Equivalent to PHP's backed-enum {@code ->value}.
   *
   * @return the lower-case state name as it appears on the wire.
   */
  public String getValue() {
    return value;
  }

  /**
   * Whether this is a <em>terminal</em> state — i.e. the call has reached the end of its lifecycle
   * and will not transition further. Only {@link #ENDED} is terminal (matching {@link
   * Call#isEnded()} and the reference's {@code CALL_STATE_ENDED} terminal check); {@link #ENDING}
   * is <em>not</em> terminal because an {@code ended} event still follows.
   *
   * @return {@code true} iff this state is {@link #ENDED}.
   */
  public boolean isTerminal() {
    return this == ENDED;
  }

  /**
   * Parse a wire string into a {@link CallState}, or return {@code null} if it is not one of the
   * five recognised states. Because the set mirrors server-emitted values that may grow, an unknown
   * string is tolerated (returns {@code null}) rather than rejected with an exception — the Java
   * analog of a fallible {@code from_str} over a {@code #[non_exhaustive]} enum.
   *
   * @param wire the candidate wire string (case-sensitive), may be {@code null}.
   * @return the matching constant, or {@code null} if none matches.
   */
  public static CallState fromWire(String wire) {
    if (wire == null) {
      return null;
    }
    for (CallState s : values()) {
      if (s.value.equals(wire)) {
        return s;
      }
    }
    return null;
  }
}
