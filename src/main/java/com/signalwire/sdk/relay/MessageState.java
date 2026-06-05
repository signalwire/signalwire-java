/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

/**
 * The delivery state of a RELAY {@link Message}, as a typed,
 * compile-time-checked closed set.
 *
 * <p>An outbound message progresses {@code queued} → {@code initiated} →
 * {@code sent} → {@code delivered} (success) or → {@code undelivered} /
 * {@code failed} (failure); an inbound message arrives as {@code received}. The
 * RELAY server reports the state as the bare {@code message_state} field on
 * {@code messaging.state} (outbound) and {@code messaging.receive} (inbound)
 * events. This enum mirrors the seven values the Python reference declares in
 * {@code signalwire/relay/constants.py} as {@code MESSAGE_STATE_*}
 * ({@link Constants#MESSAGE_STATE_QUEUED} … {@link Constants#MESSAGE_STATE_RECEIVED}).
 * The {@code messaging.state} wire schema enumerates the six outbound values;
 * {@code received} is the inbound-only seventh.
 *
 * <p>It is exposed <em>alongside</em> the existing string getter, never instead
 * of it: {@link Message#getState()} keeps returning the raw wire string (parity
 * with Python, forward-compatible), while {@link Message#getMessageState()}
 * returns {@code Optional<MessageState>} for callers who want a typed handle and
 * an {@link #isTerminal()} predicate.
 *
 * <pre>{@code
 * String raw = msg.getState();                // "delivered" (always present, parity)
 * msg.getMessageState()
 *    .filter(MessageState::isTerminal)        // DELIVERED / UNDELIVERED / FAILED
 *    .ifPresent(s -> ...);
 * }</pre>
 *
 * <p><strong>Terminal semantics.</strong> The terminal set is
 * {@link #DELIVERED}, {@link #UNDELIVERED}, {@link #FAILED} — exactly the
 * reference's {@code MESSAGE_TERMINAL_STATES} and the set
 * {@link Constants#isTerminalMessageState(String)} checks (the state at which
 * {@link Message} resolves its completion future). {@code received} is NOT
 * terminal: it is an inbound arrival, not the end of an outbound delivery
 * lifecycle.
 *
 * <p><strong>Server-emitted, growable</strong> — {@link #fromWire(String)}
 * returns {@code null} for an unrecognised value rather than throwing (the Java
 * analog of Rust's {@code #[non_exhaustive]} + fallible {@code from_str}); an
 * unknown message state must never crash event dispatch.
 *
 * <p><strong>This is a DISTINCT vocabulary from {@link CallState} and
 * {@link DialState}.</strong> A message delivery state is neither a call
 * lifecycle state nor a dial outcome; the three enums are never conflated.
 */
public enum MessageState {

    /** The message is queued for sending. */
    QUEUED(Constants.MESSAGE_STATE_QUEUED),
    /** Sending has been initiated. */
    INITIATED(Constants.MESSAGE_STATE_INITIATED),
    /** The message has left for the carrier. */
    SENT(Constants.MESSAGE_STATE_SENT),
    /** Terminal: the message was delivered. */
    DELIVERED(Constants.MESSAGE_STATE_DELIVERED),
    /** Terminal: the carrier reported non-delivery. */
    UNDELIVERED(Constants.MESSAGE_STATE_UNDELIVERED),
    /** Terminal: the send failed. */
    FAILED(Constants.MESSAGE_STATE_FAILED),
    /** An inbound message was received (inbound-only; not a terminal delivery state). */
    RECEIVED(Constants.MESSAGE_STATE_RECEIVED);

    private final String value;

    MessageState(String value) {
        this.value = value;
    }

    /**
     * The canonical wire string for this state ({@code "queued"} /
     * {@code "initiated"} / {@code "sent"} / {@code "delivered"} /
     * {@code "undelivered"} / {@code "failed"} / {@code "received"}) — exactly
     * the {@code message_state} value the server sends. Equivalent to PHP's
     * backed-enum {@code ->value}.
     *
     * @return the lower-case state name as it appears on the wire.
     */
    public String getValue() {
        return value;
    }

    /**
     * Whether this is a <em>terminal</em> delivery state — i.e. the outbound
     * message has a final delivery outcome and will not transition further. The
     * terminal set is {@link #DELIVERED}, {@link #UNDELIVERED}, {@link #FAILED}
     * (matching the reference's {@code MESSAGE_TERMINAL_STATES} and
     * {@link Constants#isTerminalMessageState(String)}). {@link #RECEIVED} is
     * NOT terminal (it is an inbound arrival, not an outbound completion).
     *
     * @return {@code true} iff this state is {@link #DELIVERED},
     *         {@link #UNDELIVERED}, or {@link #FAILED}.
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == UNDELIVERED || this == FAILED;
    }

    /**
     * Parse a wire string into a {@link MessageState}, or return {@code null}
     * if it is not one of the seven recognised states. Because the set mirrors
     * server-emitted values that may grow, an unknown string is tolerated
     * (returns {@code null}) rather than rejected with an exception.
     *
     * @param wire the candidate wire string (case-sensitive), may be {@code null}.
     * @return the matching constant, or {@code null} if none matches.
     */
    public static MessageState fromWire(String wire) {
        if (wire == null) {
            return null;
        }
        for (MessageState s : values()) {
            if (s.value.equals(wire)) {
                return s;
            }
        }
        return null;
    }
}
