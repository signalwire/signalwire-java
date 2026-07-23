/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

/** Result of {@link AIChatClient#createConversation}. */
public final class ConversationInfo {

  private final String id;
  private final String status;
  private final String initialMessage;

  /**
   * @param id the conversation id (echoed back — the caller's own input).
   * @param status the lifecycle status the service reported (e.g. {@code "created"}).
   * @param initialMessage the opening assistant message, or {@code null} if the config produced
   *     none.
   */
  public ConversationInfo(String id, String status, String initialMessage) {
    this.id = id;
    this.status = status;
    this.initialMessage = initialMessage;
  }

  /**
   * The conversation id (the caller's own input, echoed).
   *
   * @return the conversation id.
   */
  public String getId() {
    return id;
  }

  /**
   * The lifecycle status the service reported.
   *
   * @return the status.
   */
  public String getStatus() {
    return status;
  }

  /**
   * The opening assistant message, if the config produced one.
   *
   * @return the opening message, or {@code null}.
   */
  public String getInitialMessage() {
    return initialMessage;
  }
}
