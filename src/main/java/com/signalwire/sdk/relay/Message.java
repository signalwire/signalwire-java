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
 * <p>
 * Messages are simpler than calls: they have a {@code message_id}, progress through
 * states (queued, initiated, sent, delivered), and support completion waiting.
 * <p>
 * State progression:
 * <ul>
 *   <li>Success: queued -> initiated -> sent -> delivered</li>
 *   <li>Failure: queued -> initiated -> failed/undelivered</li>
 * </ul>
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

    public String getMessageId() { return messageId; }
    public String getContext() { return context; }
    public String getDirection() { return direction; }
    public String getFromNumber() { return fromNumber; }
    public String getToNumber() { return toNumber; }
    public String getBody() { return body; }
    public List<String> getMedia() { return media; }
    public int getSegments() { return segments; }
    public String getState() { return state; }
    public String getReason() { return reason; }
    public List<String> getTags() { return tags; }
    public boolean isDone() { return done; }
    public RelayEvent getResult() { return result; }

    // ── Setters ──────────────────────────────────────────────────────

    public void setContext(String context) { this.context = context; }
    public void setDirection(String direction) { this.direction = direction; }
    public void setFromNumber(String fromNumber) { this.fromNumber = fromNumber; }
    public void setToNumber(String toNumber) { this.toNumber = toNumber; }
    public void setBody(String body) { this.body = body; }
    public void setMedia(List<String> media) { this.media = media != null ? media : Collections.emptyList(); }
    public void setSegments(int segments) { this.segments = segments; }
    public void setTags(List<String> tags) { this.tags = tags != null ? tags : Collections.emptyList(); }
    public void setState(String state) { this.state = state; }

    public void setOnCompleted(Consumer<Message> onCompleted) {
        this.onCompleted = onCompleted;
    }

    /**
     * Register a state change listener.
     */
    public void on(Consumer<RelayEvent> listener) {
        stateListeners.add(listener);
    }

    /**
     * Update state from an incoming event.
     */
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

    /**
     * Block until the message reaches a terminal state.
     */
    public RelayEvent waitForCompletion() {
        try {
            return completionFuture.get();
        } catch (Exception e) {
            return result;
        }
    }

    /**
     * Block until the message reaches a terminal state, with timeout.
     */
    public RelayEvent waitForCompletion(long timeoutMs) {
        try {
            return completionFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return result;
        }
    }

    /**
     * Resolve the message completion.
     */
    void resolve(RelayEvent event) {
        if (!done) {
            this.done = true;
            this.result = event;
            this.completionFuture.complete(event);
            fireOnCompleted();
        }
    }

    private void fireOnCompleted() {
        if (onCompleted != null) {
            try {
                onCompleted.accept(this);
            } catch (Exception e) {
                log.error("Error in onCompleted callback for message " + messageId, e);
            }
        }
    }

    /**
     * Create a Message from an inbound receive event.
     */
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

    @Override
    public String toString() {
        return String.format("Message{id=%s, state=%s, from=%s, to=%s}",
                messageId, state, fromNumber, toNumber);
    }
}
