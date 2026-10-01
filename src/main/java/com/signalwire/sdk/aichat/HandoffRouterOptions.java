/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Construction options for {@link HandoffRouter} — the Java named-parameter form of the reference's
 * keyword-only constructor. Build with {@link #builder(ChatGateway)}; every field but the gateway
 * is optional and takes the reference's default.
 */
public final class HandoffRouterOptions {

  private final ChatGateway gateway;
  private final BiFunction<String, String, Boolean> captureLeg;
  private final Consumer<String> endCall;
  private final BiFunction<String, String, Boolean> sendMessage;
  private final Function<String, String> nextConversationId;
  private final int nonceTtl;
  private final int maxMessagesPerCall;
  private final double captureTimeout;
  private final Map<String, NonceEntry> registry;

  private HandoffRouterOptions(Builder b) {
    this.gateway = b.gateway;
    this.captureLeg = b.captureLeg;
    this.endCall = b.endCall;
    this.sendMessage = b.sendMessage;
    this.nextConversationId = b.nextConversationId;
    this.nonceTtl = b.nonceTtl;
    this.maxMessagesPerCall = b.maxMessagesPerCall;
    this.captureTimeout = b.captureTimeout;
    this.registry = b.registry;
  }

  /**
   * A new builder for a router beside {@code gateway}.
   *
   * @param gateway the gateway that owns the conversations.
   * @return a builder.
   */
  public static Builder builder(ChatGateway gateway) {
    return new Builder(gateway);
  }

  /**
   * The gateway that owns the conversations.
   *
   * @return the gateway.
   */
  public ChatGateway getGateway() {
    return gateway;
  }

  /**
   * The capture callback, {@code (conversationId, medium) -> durable}.
   *
   * @return the callback, or {@code null}.
   */
  public BiFunction<String, String, Boolean> getCaptureLeg() {
    return captureLeg;
  }

  /**
   * The hang-up callback, {@code callId -> void}.
   *
   * @return the callback, or {@code null}.
   */
  public Consumer<String> getEndCall() {
    return endCall;
  }

  /**
   * The typing callback, {@code (callId, text) -> delivered}.
   *
   * @return the callback, or {@code null}.
   */
  public BiFunction<String, String, Boolean> getSendMessage() {
    return sendMessage;
  }

  /**
   * The new-leg id function.
   *
   * @return the function, or {@code null} for the default {@code .N} suffix.
   */
  public Function<String, String> getNextConversationId() {
    return nextConversationId;
  }

  /**
   * Seconds a nonce stays usable after its first registration.
   *
   * @return the TTL.
   */
  public int getNonceTtl() {
    return nonceTtl;
  }

  /**
   * Ceiling on typed messages for one call.
   *
   * @return the cap.
   */
  public int getMaxMessagesPerCall() {
    return maxMessagesPerCall;
  }

  /**
   * Seconds to wait for the capture callback.
   *
   * @return the ceiling.
   */
  public double getCaptureTimeout() {
    return captureTimeout;
  }

  /**
   * The shared nonce table.
   *
   * @return the registry, or {@code null} for a private one.
   */
  public Map<String, NonceEntry> getRegistry() {
    return registry;
  }

  /** Builder for {@link HandoffRouterOptions}. */
  public static final class Builder {
    private final ChatGateway gateway;
    private BiFunction<String, String, Boolean> captureLeg;
    private Consumer<String> endCall;
    private BiFunction<String, String, Boolean> sendMessage;
    private Function<String, String> nextConversationId;
    private int nonceTtl = HandoffRouter.DEFAULT_NONCE_TTL;
    private int maxMessagesPerCall = HandoffRouter.DEFAULT_MAX_MESSAGES_PER_CALL;
    private double captureTimeout = HandoffRouter.DEFAULT_CAPTURE_TIMEOUT;
    private Map<String, NonceEntry> registry;

    private Builder(ChatGateway gateway) {
      this.gateway = gateway;
    }

    /**
     * @param captureLeg called as {@code captureLeg(conversationId, medium)} to end a leg and write
     *     its record; returns {@code true} only once that record is durable.
     * @return this builder.
     */
    public Builder captureLeg(BiFunction<String, String, Boolean> captureLeg) {
      this.captureLeg = captureLeg;
      return this;
    }

    /**
     * @param endCall called as {@code endCall(callId)} to hang the call up server-side.
     * @return this builder.
     */
    public Builder endCall(Consumer<String> endCall) {
      this.endCall = endCall;
      return this;
    }

    /**
     * @param sendMessage called as {@code sendMessage(callId, text)} for {@code /say}; omit to
     *     leave typing disabled.
     * @return this builder.
     */
    public Builder sendMessage(BiFunction<String, String, Boolean> sendMessage) {
      this.sendMessage = sendMessage;
      return this;
    }

    /**
     * @param nextConversationId produces the id for the NEW leg from the old one.
     * @return this builder.
     */
    public Builder nextConversationId(Function<String, String> nextConversationId) {
      this.nextConversationId = nextConversationId;
      return this;
    }

    /**
     * @param nonceTtl seconds a nonce stays usable after its first registration.
     * @return this builder.
     */
    public Builder nonceTtl(int nonceTtl) {
      this.nonceTtl = nonceTtl;
      return this;
    }

    /**
     * @param maxMessagesPerCall ceiling on typed messages for one call.
     * @return this builder.
     */
    public Builder maxMessagesPerCall(int maxMessagesPerCall) {
      this.maxMessagesPerCall = maxMessagesPerCall;
      return this;
    }

    /**
     * @param captureTimeout seconds to wait for {@code captureLeg}.
     * @return this builder.
     */
    public Builder captureTimeout(double captureTimeout) {
      this.captureTimeout = captureTimeout;
      return this;
    }

    /**
     * @param registry a shared nonce table, for running more than one replica.
     * @return this builder.
     */
    public Builder registry(Map<String, NonceEntry> registry) {
      this.registry = registry;
      return this;
    }

    /**
     * Build the options.
     *
     * @return the options.
     */
    public HandoffRouterOptions build() {
      return new HandoffRouterOptions(this);
    }
  }
}
