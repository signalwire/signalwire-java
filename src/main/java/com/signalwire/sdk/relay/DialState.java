/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

/**
 * The outcome state of an outbound {@code calling.dial}, as a typed, compile-time-checked closed
 * set.
 *
 * <p>A dial progresses {@code dialing} → {@code answered} (a leg won) or {@code dialing} → {@code
 * failed} (every leg gave up). The RELAY server reports it as the bare {@code dial_state} field on
 * each {@code calling.call.dial} event. This enum mirrors the three values the Java port declares
 * in {@code Constants} as {@code DIAL_STATE_*} ({@link Constants#DIAL_STATE_DIALING} / {@link
 * Constants#DIAL_STATE_ANSWERED} / {@link Constants#DIAL_STATE_FAILED}), which back the
 * dial-completion logic in {@code RelayClient.handleDialEvent}.
 *
 * <p>It is exposed <em>alongside</em> the existing string getter, never instead of it: {@link
 * RelayEvent.CallDialEvent#getDialState()} keeps returning the raw wire string
 * (forward-compatible), while {@link RelayEvent.CallDialEvent#getDialStateEnum()} returns {@code
 * Optional<DialState>} for callers who want a typed handle and an {@link #isTerminal()} predicate.
 *
 * <p><strong>Terminal semantics.</strong> Unlike {@link CallState} (where only {@code ended} is
 * terminal), <em>both</em> {@link #ANSWERED} and {@link #FAILED} are terminal dial outcomes — each
 * resolves the pending dial (one completes the {@code CompletableFuture<Call>}, the other fails
 * it). Only {@link #DIALING} is non-terminal. So "terminal" here means "the dial has a final
 * outcome", matching the {@code answered}/{@code failed} branches that resolve the dial in {@code
 * RelayClient}.
 *
 * <pre>{@code
 * event.getDialState();                       // "answered" (always present)
 * event.getDialStateEnum()
 *      .filter(DialState::isTerminal)          // ANSWERED or FAILED
 *      .ifPresent(s -> ...);
 * }</pre>
 *
 * <p><strong>Server-emitted, growable</strong> — {@link #fromWire(String)} returns {@code null} for
 * an unrecognised value rather than throwing (the Java analog of Rust's {@code #[non_exhaustive]} +
 * fallible {@code from_str}); an unknown dial state must never crash event dispatch.
 *
 * <p><strong>This is a DISTINCT vocabulary from {@link CallState} and {@link
 * MessageState}.</strong> A dial outcome ({@code dialing/answered/failed}) is not a call lifecycle
 * state and not a message delivery state. Note in particular {@code answered} appears in
 * <em>both</em> {@code DialState} and {@code CallState} but means different things (the dial won
 * vs. the call leg is up) — the two enums are never shared.
 */
public enum DialState {

  /** The dial is in progress; legs are ringing. Non-terminal. */
  DIALING(Constants.DIAL_STATE_DIALING),
  /** Terminal: a leg answered and the dial resolved to a {@link Call}. */
  ANSWERED(Constants.DIAL_STATE_ANSWERED),
  /** Terminal: every leg failed and the dial did not connect. */
  FAILED(Constants.DIAL_STATE_FAILED);

  private final String value;

  DialState(String value) {
    this.value = value;
  }

  /**
   * The canonical wire string for this dial outcome ({@code "dialing"} / {@code "answered"} /
   * {@code "failed"}) — exactly the {@code dial_state} value the server sends on {@code
   * calling.call.dial}. Equivalent to PHP's backed-enum {@code ->value}.
   *
   * @return the lower-case dial-state name as it appears on the wire.
   */
  public String getValue() {
    return value;
  }

  /**
   * Whether this is a <em>terminal</em> dial outcome — i.e. the dial has a final result and will
   * not transition further. Both {@link #ANSWERED} and {@link #FAILED} are terminal (each resolves
   * the pending dial); only {@link #DIALING} is non-terminal.
   *
   * @return {@code true} iff this state is {@link #ANSWERED} or {@link #FAILED}.
   */
  public boolean isTerminal() {
    return this == ANSWERED || this == FAILED;
  }

  /**
   * Parse a wire string into a {@link DialState}, or return {@code null} if it is not one of the
   * three recognised dial states. Because the set mirrors server-emitted values that may grow, an
   * unknown string is tolerated (returns {@code null}) rather than rejected with an exception.
   *
   * @param wire the candidate wire string (case-sensitive), may be {@code null}.
   * @return the matching constant, or {@code null} if none matches.
   */
  public static DialState fromWire(String wire) {
    if (wire == null) {
      return null;
    }
    for (DialState s : values()) {
      if (s.value.equals(wire)) {
        return s;
      }
    }
    return null;
  }
}
