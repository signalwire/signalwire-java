/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import java.util.Map;

/** Result of {@link AIChatClient#chat}. */
public final class ChatResponse {

  private final String text;
  private final String conversationId;
  private final Map<String, Object> userEvent;

  /**
   * @param text the assistant's reply text (the wire {@code response} field).
   * @param conversationId the conversation id this reply belongs to.
   * @param userEvent an optional structured event the turn emitted, else {@code null}.
   */
  public ChatResponse(String text, String conversationId, Map<String, Object> userEvent) {
    this.text = text;
    this.conversationId = conversationId;
    this.userEvent = userEvent;
  }

  /**
   * The assistant's reply text (the wire {@code response} field).
   *
   * @return the reply text.
   */
  public String getText() {
    return text;
  }

  /**
   * The conversation id this reply belongs to.
   *
   * @return the conversation id.
   */
  public String getConversationId() {
    return conversationId;
  }

  /**
   * An optional structured event the turn emitted.
   *
   * @return the user event map, or {@code null}.
   */
  public Map<String, Object> getUserEvent() {
    return userEvent;
  }
}
