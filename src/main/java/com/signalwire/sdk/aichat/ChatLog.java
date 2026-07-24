/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import java.util.List;
import java.util.Map;

/** Result of {@link AIChatClient#log}. */
public final class ChatLog {

  private final List<Map<String, Object>> messages;
  private final List<Map<String, Object>> callTimeline;

  /**
   * @param messages the full message history (the wire {@code chat_log} field).
   * @param callTimeline the call timeline (the wire {@code call_timeline} field).
   */
  public ChatLog(List<Map<String, Object>> messages, List<Map<String, Object>> callTimeline) {
    this.messages = messages;
    this.callTimeline = callTimeline;
  }

  /**
   * The full message history (the wire {@code chat_log} field).
   *
   * @return the message list.
   */
  public List<Map<String, Object>> getMessages() {
    return messages;
  }

  /**
   * The call timeline (the wire {@code call_timeline} field).
   *
   * @return the call timeline.
   */
  public List<Map<String, Object>> getCallTimeline() {
    return callTimeline;
  }
}
