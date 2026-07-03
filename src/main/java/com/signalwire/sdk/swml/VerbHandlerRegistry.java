/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry for SWML verb handlers.
 *
 * <p>Maintains a registry of handlers for special SWML verbs and provides methods for accessing
 * them. The "ai" verb handler is registered automatically on construction (Python parity). Mirrors
 * the Python reference {@code signalwire.core.swml_handler.VerbHandlerRegistry} and the Ruby {@code
 * SignalWire::SWML::VerbHandlerRegistry}.
 */
public class VerbHandlerRegistry {

  private final Map<String, SWMLVerbHandler> handlers = new LinkedHashMap<>();

  /** Initialize the registry with default handlers ({@link AIVerbHandler}). */
  public VerbHandlerRegistry() {
    registerHandler(new AIVerbHandler());
  }

  /**
   * Register a verb handler, replacing any existing handler for the same verb name.
   *
   * @param handler the handler to register
   */
  public void registerHandler(SWMLVerbHandler handler) {
    handlers.put(handler.getVerbName(), handler);
  }

  /**
   * Get the handler for a specific verb, or {@code null} when none is registered.
   *
   * @param verbName the verb name
   * @return the handler, or {@code null}
   */
  public SWMLVerbHandler getHandler(String verbName) {
    return handlers.get(verbName);
  }

  /**
   * Whether a handler exists for a specific verb.
   *
   * @param verbName the verb name
   * @return {@code true} if a handler is registered for the verb
   */
  public boolean hasHandler(String verbName) {
    return handlers.containsKey(verbName);
  }
}
