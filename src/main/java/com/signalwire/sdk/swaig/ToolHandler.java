package com.signalwire.sdk.swaig;

import java.util.Map;

/**
 * Functional interface for SWAIG tool handlers. Called when the platform invokes a tool during a
 * conversation.
 */
@FunctionalInterface
public interface ToolHandler {
  /**
   * Handle a SWAIG tool invocation.
   *
   * @param args Parsed arguments from the tool call
   * @param rawData Full raw request payload for accessing call info, global data, etc.
   * @return A FunctionResult with the response text and optional actions
   */
  FunctionResult handle(Map<String, Object> args, Map<String, Object> rawData);
}
