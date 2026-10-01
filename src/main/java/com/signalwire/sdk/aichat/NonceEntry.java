/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

/**
 * What a handoff nonce is a capability for: the conversation (and the call, once known) of the dial
 * that carried it.
 *
 * <p>{@code redeemed} marks a nonce {@code /handoff} has exchanged for a handle. The entry is kept
 * until its TTL passes, so the nonce can be neither redeemed nor registered again. A shared
 * registry stores a change only when the entry is assigned back to its key, so {@link
 * HandoffRouter} always writes a changed entry back.
 */
public class NonceEntry {

  private String conversationId;
  private String callId;
  private double issuedAt;
  private int messages;
  private boolean redeemed;

  /**
   * An entry for a conversation with no call yet, registered now.
   *
   * @param conversationId the conversation the nonce belongs to.
   */
  public NonceEntry(String conversationId) {
    this(conversationId, null);
  }

  /**
   * An entry for a conversation and its call, registered now.
   *
   * @param conversationId the conversation the nonce belongs to.
   * @param callId the call that carried the nonce, or {@code null}.
   */
  public NonceEntry(String conversationId, String callId) {
    this(conversationId, callId, monotonicNow(), 0, false);
  }

  /**
   * An entry with every field given.
   *
   * @param conversationId the conversation the nonce belongs to.
   * @param callId the call that carried the nonce, or {@code null}.
   * @param issuedAt monotonic seconds at first registration ({@code System.nanoTime() / 1e9}).
   * @param messages typed messages delivered so far.
   * @param redeemed whether {@code /handoff} has exchanged the nonce.
   */
  public NonceEntry(
      String conversationId, String callId, double issuedAt, int messages, boolean redeemed) {
    this.conversationId = conversationId;
    this.callId = callId;
    this.issuedAt = issuedAt;
    this.messages = messages;
    this.redeemed = redeemed;
  }

  /** The monotonic clock {@code issuedAt} is measured on: {@code System.nanoTime() / 1e9}. */
  static double monotonicNow() {
    return System.nanoTime() / 1e9;
  }

  /**
   * The conversation the nonce belongs to.
   *
   * @return the conversation id.
   */
  public String getConversationId() {
    return conversationId;
  }

  /**
   * Set the conversation the nonce belongs to.
   *
   * @param conversationId the conversation id.
   */
  public void setConversationId(String conversationId) {
    this.conversationId = conversationId;
  }

  /**
   * The call that carried the nonce.
   *
   * @return the call id, or {@code null}.
   */
  public String getCallId() {
    return callId;
  }

  /**
   * Set the call that carried the nonce.
   *
   * @param callId the call id, or {@code null}.
   */
  public void setCallId(String callId) {
    this.callId = callId;
  }

  /**
   * Monotonic seconds at first registration.
   *
   * @return the registration time.
   */
  public double getIssuedAt() {
    return issuedAt;
  }

  /**
   * Set the registration time.
   *
   * @param issuedAt monotonic seconds.
   */
  public void setIssuedAt(double issuedAt) {
    this.issuedAt = issuedAt;
  }

  /**
   * Typed messages delivered (or in flight) for this call.
   *
   * @return the count.
   */
  public int getMessages() {
    return messages;
  }

  /**
   * Set the typed-message count.
   *
   * @param messages the count.
   */
  public void setMessages(int messages) {
    this.messages = messages;
  }

  /**
   * Whether {@code /handoff} has exchanged the nonce for a handle.
   *
   * @return {@code true} once redeemed.
   */
  public boolean isRedeemed() {
    return redeemed;
  }

  /**
   * Mark the nonce redeemed (or not).
   *
   * @param redeemed the flag.
   */
  public void setRedeemed(boolean redeemed) {
    this.redeemed = redeemed;
  }
}
