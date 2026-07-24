/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */

/**
 * Client for the SignalWire AI Chat service — the JSON-RPC 2.0 front-door protocol at {@code POST
 * /api/ai/chat}.
 *
 * <p>{@link com.signalwire.sdk.aichat.AIChatClient} drives the six methods (create_conversation,
 * chat, end_conversation, delete, chat_log, summarize) and maps the service's typed error space to
 * the {@link com.signalwire.sdk.aichat.AIChatError} family. Mirrors the python reference {@code
 * signalwire.ai_chat}.
 */
package com.signalwire.sdk.aichat;
