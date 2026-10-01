/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One finished conversation leg, in a shape that does not vary by engine (voice or chat). Built by
 * {@link PostPrompt#normalizePostPrompt(Object)}.
 *
 * @param medium {@code conversation_type} as reported ({@code "voice"} / {@code "chat"}); empty
 *     when the engine did not say
 * @param conversationId present on chat, absent on voice ({@code null})
 * @param summary the parsed {@code post_prompt_data}, whatever keys the application's post-prompt
 *     asked for; empty when there was none
 * @param dialogue {@code user}/{@code assistant} turns only, with tool calls and the chat engine's
 *     summary echo removed
 * @param callId the platform call id, when present
 * @param raw the complete request body, untouched
 */
public record NormalizedPostPrompt(
    String medium,
    String conversationId,
    Map<String, Object> summary,
    List<Map<String, String>> dialogue,
    String callId,
    Map<String, Object> raw) {

  /** An empty leg: every field at its default. */
  public NormalizedPostPrompt() {
    this("", null, Collections.emptyMap(), Collections.emptyList(), null, Collections.emptyMap());
  }
}
