/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import java.util.regex.Pattern;

/**
 * Masks credential VALUES in a raw RELAY frame before it is logged, so a {@code
 * SIGNALWIRE_LOG_LEVEL=debug} session never emits live credentials or the {@code
 * authorization_state} re-auth blob (the SECRET-SCRUB contract, PSDK-5).
 *
 * <p>Mirrors the Python reference {@code _scrub_frame} / {@code _SCRUB_RE}
 * (relay/client.py:108-127): the string values of {@code token} / {@code project} / {@code
 * jwt_token} / {@code authorization_state} keys are replaced with {@code "***"} wherever they
 * appear in the JSON frame; all structural / non-credential content is preserved so the frame stays
 * diagnostic.
 */
final class FrameScrub {

  private FrameScrub() {}

  private static final Pattern SCRUB_RE =
      Pattern.compile(
          "(\"(?:token|project|jwt_token|authorization_state)\"\\s*:\\s*)\"(?:\\\\.|[^\"\\\\])*\"");

  /** Return a log-safe copy of {@code raw} with credential values masked. */
  static String scrub(String raw) {
    if (raw == null) {
      return "null";
    }
    return SCRUB_RE.matcher(raw).replaceAll("$1\"***\"");
  }
}
