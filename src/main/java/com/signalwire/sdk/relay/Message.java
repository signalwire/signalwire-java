/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import com.signalwire.sdk.logging.Logger;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Represents an SMS/MMS message with state tracking.
 *
 * <p>Messages are simpler than calls: they have a {@code message_id}, progress through states
 * (queued, initiated, sent, delivered), and support completion waiting.
 *
 * <p>State progression:
 *
 * <ul>
 *   <li>Success: queued -> initiated -> sent -> delivered
 *   <li>Failure: queued -> initiated -> failed/undelivered
 * </ul>
 *
 * Terminal states: delivered, undelivered, failed
 */
public class Message {

  private static final Logger log = Logger.getLogger(Message.class);

  private final String messageId;
  private volatile String context;
  private volatile String direction;
  private volatile String fromNumber;
  private volatile String toNumber;
  private volatile String body;
  private volatile List<String> media;
  private volatile int segments;
  private volatile String state;
  private volatile String reason;
  private volatile List<String> tags;

  private volatile boolean done;
  private volatile RelayEvent result;
  private final CompletableFuture<RelayEvent> completionFuture;
  private Consumer<Message> onCompleted;
  private final List<Consumer<RelayEvent>> stateListeners = new ArrayList<>();

  public Message(String messageId) {
    this.messageId = messageId;
    this.media = Collections.emptyList();
    this.tags = Collections.emptyList();
    this.done = false;
    this.completionFuture = new CompletableFuture<>();
  }

  // ── Getters ──────────────────────────────────────────────────────

  /**
   * The platform's identifier for this message, returned by {@code messaging.send} and echoed on
   * every {@code messaging.state} event — the key state updates are routed on.
   *
   * @return the message id.
   */
  public String getMessageId() {
    return messageId;
  }

  /**
   * URLs of the MMS attachments carried by the message.
   *
   * @return the media URLs, never {@code null} (empty when there are none).
   */
  public List<String> getMedia() {
    return media;
  }

  /**
   * How many SMS segments the message occupied — the unit billing is charged in.
   *
   * @return the segment count.
   */
  public int getSegments() {
    return segments;
  }

  /**
   * The message's current delivery state as sent on the wire, progressing {@code queued} to {@code
   * initiated} to {@code sent} to {@code delivered}, or ending at {@code failed} / {@code
   * undelivered}. Kept as a string so a server-side addition to the set does not break dispatch.
   *
   * @return the raw state, or {@code null} before the first event.
   */
  public String getState() {
    return state;
  }

  /**
   * Client-supplied correlation tags carried on the message.
   *
   * @return the tags, never {@code null} (empty when none were set).
   */
  public List<String> getTags() {
    return tags;
  }

  /**
   * Whether the message has reached a terminal state ({@code delivered}, {@code undelivered}, or
   * {@code failed}).
   *
   * @return {@code true} once the message has settled.
   */
  public boolean isDone() {
    return done;
  }

  /**
   * The current message state as a typed {@link MessageState}, <em>alongside</em> the raw string
   * {@link #getState()} (which stays canonical and forward-compatible). Returns {@code
   * Optional.empty()} when the live wire state is not one of the seven known {@link MessageState}
   * values — the set mirrors server-emitted values that can grow, so an unrecognised state is
   * tolerated here rather than crashing the caller. The present value always agrees with {@code
   * getState()}: {@code getMessageState().get().getValue().equals(getState())}.
   *
   * @return the typed state, or empty if the raw state is unknown/unset.
   */
  public Optional<MessageState> getMessageState() {
    return Optional.ofNullable(MessageState.fromWire(state));
  }

  // ── Optional accessors for nullable scalar state ─────────────────
  //
  // context / direction / from_number / to_number / body / reason are
  // only set as the message is built (outbound) or as inbound/state
  // events arrive — a bare Message has them null. reason in particular
  // is only populated on a failed/undelivered terminal event, and result
  // only once the message completes. Exposing them as Optional<T> states
  // the "may be absent" contract in the type (the Java idiom for nullable)
  // rather than handing back a bare null. getMessageId()/getState() stay
  // non-Optional: the id is supplied at construction and state always
  // carries a lifecycle value (queued/…); getSegments()/isDone() are
  // primitives.

  /** Messaging context (the RELAY protocol/context the message rode on). */
  public Optional<String> getContext() {
    return Optional.ofNullable(context);
  }

  /** Direction (inbound/outbound) once known; empty until set. */
  public Optional<String> getDirection() {
    return Optional.ofNullable(direction);
  }

  /** Sender E.164 number; empty until set. */
  public Optional<String> getFromNumber() {
    return Optional.ofNullable(fromNumber);
  }

  /** Recipient E.164 number; empty until set. */
  public Optional<String> getToNumber() {
    return Optional.ofNullable(toNumber);
  }

  /** Message body; empty for media-only messages or before set. */
  public Optional<String> getBody() {
    return Optional.ofNullable(body);
  }

  /** Failure reason; present only on a failed/undelivered terminal state. */
  public Optional<String> getReason() {
    return Optional.ofNullable(reason);
  }

  /** Terminal completion event; empty until the message reaches a terminal state. */
  public Optional<RelayEvent> getResult() {
    return Optional.ofNullable(result);
  }

  // ── Setters ──────────────────────────────────────────────────────

  /**
   * Update the context from a later event frame.
   *
   * @param context the messaging context.
   */
  public void setContext(String context) {
    this.context = context;
  }

  /**
   * Update the direction ({@code inbound} or {@code outbound}) from a later event frame.
   *
   * @param direction the message direction.
   */
  public void setDirection(String direction) {
    this.direction = direction;
  }

  /**
   * Update the sender number (E.164) from a later event frame.
   *
   * @param fromNumber the sender number.
   */
  public void setFromNumber(String fromNumber) {
    this.fromNumber = fromNumber;
  }

  /**
   * Update the destination number (E.164) from a later event frame.
   *
   * @param toNumber the destination number.
   */
  public void setToNumber(String toNumber) {
    this.toNumber = toNumber;
  }

  /**
   * Update the message text from a later event frame. For an inbound message this is untrusted
   * end-user input.
   *
   * @param body the message text.
   */
  public void setBody(String body) {
    this.body = body;
  }

  /**
   * Update the MMS attachment URLs from a later event frame.
   *
   * @param media the media URLs; {@code null} is stored as an empty list.
   */
  public void setMedia(List<String> media) {
    this.media = media != null ? media : Collections.emptyList();
  }

  /**
   * Update the segment count from a later event frame.
   *
   * @param segments the segment count.
   */
  public void setSegments(int segments) {
    this.segments = segments;
  }

  /**
   * Update the correlation tags from a later event frame.
   *
   * @param tags the tags; {@code null} is stored as an empty list.
   */
  public void setTags(List<String> tags) {
    this.tags = tags != null ? tags : Collections.emptyList();
  }

  /**
   * Overwrite the delivery state directly. Note this does NOT evaluate terminality — it will not
   * resolve the message or fire the completion callback the way an incoming state event does.
   *
   * @param state the raw wire state.
   */
  public void setState(String state) {
    this.state = state;
  }

  /**
   * Register a callback to fire when the message reaches a terminal state.
   *
   * <p>Safe against the genuine race where the terminal event lands on the RELAY reader thread
   * before this registration: if the message has ALREADY resolved, the callback fires immediately
   * rather than being silently dropped. It fires exactly once either way. Matches {@link
   * com.signalwire.sdk.relay.Action#setOnCompleted(java.util.function.Consumer)}.
   *
   * @param onCompleted the callback, invoked with this message.
   */
  public void setOnCompleted(Consumer<Message> onCompleted) {
    // If the message has ALREADY resolved (the terminal event landed on the RELAY
    // reader thread before this registration — a genuine race for a caller that
    // sets the callback after dispatching the message), fire immediately so a late
    // registration is never silently dropped. Otherwise store it for resolve() to
    // fire. Guarded on `done` (set inside resolve()) so exactly one fire happens.
    synchronized (this) {
      this.onCompleted = onCompleted;
      if (done && onCompleted != null) {
        Consumer<Message> cb = this.onCompleted;
        this.onCompleted = null; // prevent a double-fire if resolve() also races
        cb.accept(this);
      }
    }
  }

  /** Register a state change listener. */
  public void on(Consumer<RelayEvent> listener) {
    stateListeners.add(listener);
  }

  /** Update state from an incoming event. */
  public void updateFromEvent(RelayEvent event) {
    if (event instanceof RelayEvent.MessagingStateEvent) {
      RelayEvent.MessagingStateEvent stateEvent = (RelayEvent.MessagingStateEvent) event;
      this.state = stateEvent.getMessageState();
      if (stateEvent.getReason() != null) {
        this.reason = stateEvent.getReason();
      }
    }

    // Notify state listeners
    for (Consumer<RelayEvent> listener : stateListeners) {
      try {
        listener.accept(event);
      } catch (Exception e) {
        log.error("Error in message state listener", e);
      }
    }

    // Check for terminal state
    if (Constants.isTerminalMessageState(this.state)) {
      resolve(event);
    }
  }

  /** Block until the message reaches a terminal state. */
  public RelayEvent waitForCompletion() {
    try {
      return completionFuture.get();
    } catch (Exception e) {
      return result;
    }
  }

  /** Block until the message reaches a terminal state, with timeout. */
  public RelayEvent waitForCompletion(long timeoutMs) {
    try {
      return completionFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      return result;
    }
  }

  /**
   * The terminal {@link RelayEvent}, or {@code null} if the message has not yet reached a terminal
   * state. Python-surface name for the reference's {@code Message.result} property (the {@link
   * #getResult()} accessor returns the same value wrapped in an {@link Optional}).
   */
  public RelayEvent result() {
    return done ? result : null;
  }

  /**
   * Block until the message reaches a terminal state, returning the terminal event. Java-idiom name
   * for the reference's {@code Message.wait}: the bare name {@code wait} collides with {@code
   * java.lang.Object.wait()} (final, non-overridable), so this port names it {@code await} and the
   * enumerator's rename table maps {@code await} → {@code wait} (adapter rename, not omission).
   */
  public RelayEvent await() {
    return waitForCompletion();
  }

  /**
   * Block until the message reaches a terminal state, with a timeout. Java-idiom name for the
   * reference's {@code Message.wait(timeout)} (see {@link #await()}).
   *
   * <p>The unit is SECONDS and the type is boxed {@code Double}, matching the reference's {@code
   * timeout: float | None = None} and the port's {@code RequestOptions.timeout} spelling; {@code
   * null} means "wait indefinitely". {@link #waitForCompletion(long)} takes milliseconds.
   *
   * @param timeout timeout in seconds ({@code null} = no timeout)
   * @return the terminal event, or null on timeout
   */
  public RelayEvent await(Double timeout) {
    if (timeout == null || timeout <= 0.0) {
      return waitForCompletion();
    }
    return waitForCompletion((long) (timeout * 1000.0));
  }

  /** Resolve the message completion. */
  void resolve(RelayEvent event) {
    // Set `done` + fire the callback atomically vs setOnCompleted(): the terminal
    // event arrives on the RELAY reader thread and can race a caller registering
    // its callback on another thread. The lock guarantees exactly one of the two
    // fires the callback (resolve() here, or setOnCompleted() when it observes
    // done==true), never zero and never twice.
    synchronized (this) {
      if (done) {
        return;
      }
      this.done = true;
      this.result = event;
      // Fire onCompleted BEFORE completing the future. The Python reference is
      // async single-threaded, so its `_done.set_result()` then `_on_completed()`
      // ordering guarantees any `await`-er resumes only after the callback has run.
      // Firing first preserves that guarantee (callback observed no later than
      // completion) race-free.
      Consumer<Message> cb = this.onCompleted;
      this.onCompleted = null; // one-shot: a later setOnCompleted sees done and fires itself
      if (cb != null) {
        try {
          cb.accept(this);
        } catch (Exception e) {
          log.error("Error in onCompleted callback for message " + messageId, e);
        }
      }
    }
    this.completionFuture.complete(event);
  }

  /** Create a Message from an inbound receive event. */
  public static Message fromReceiveEvent(RelayEvent.MessagingReceiveEvent event) {
    Message msg = new Message(event.getMessageId());
    msg.setContext(event.getContext());
    msg.setDirection(event.getDirection());
    msg.setFromNumber(event.getFromNumber());
    msg.setToNumber(event.getToNumber());
    msg.setBody(event.getBody());
    msg.setMedia(event.getMedia());
    msg.setSegments(event.getSegments());
    msg.setTags(event.getTags());
    msg.state = event.getMessageState();
    return msg;
  }

  /**
   * A short diagnostic rendering carrying the message id, state, and the two numbers — deliberately
   * excludes the body, which for an inbound message is end-user content.
   *
   * @return the diagnostic string.
   */
  @Override
  public String toString() {
    return String.format(
        "Message{id=%s, state=%s, from=%s, to=%s}", messageId, state, fromNumber, toNumber);
  }
}
