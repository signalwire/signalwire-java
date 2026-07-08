/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import java.util.List;
import java.util.Map;

/**
 * Base interface for SWML verb handlers.
 *
 * <p>Verb handlers provide specialized logic for complex SWML verbs that cannot be handled
 * generically. Mirrors the Python reference {@code signalwire.core.swml_handler.SWMLVerbHandler}
 * (an ABC) and the Ruby {@code SignalWire::SWML::SWMLVerbHandler}.
 *
 * <p>This is an abstract class whose methods throw {@link UnsupportedOperationException} — the Java
 * analog of Python's {@code @abstractmethod} / Ruby's {@code NotImplementedError}: a subclass that
 * forgets to override them fails loudly.
 */
public abstract class SWMLVerbHandler {

  /**
   * Get the name of the verb this handler handles.
   *
   * @return the verb name
   */
  public String getVerbName() {
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + "#getVerbName must be implemented");
  }

  /**
   * Validate the configuration for this verb.
   *
   * @param config the configuration for this verb
   * @return a {@link ValidationResult} of (isValid, errorMessages)
   */
  public ValidationResult validateConfig(Map<String, Object> config) {
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + "#validateConfig must be implemented");
  }

  /**
   * Build a configuration for this verb from the provided arguments.
   *
   * @param kwargs keyword arguments specific to this verb
   * @return the configuration map
   */
  public Map<String, Object> buildConfig(Map<String, Object> kwargs) {
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + "#buildConfig must be implemented");
  }

  /**
   * Result of {@link #validateConfig(Map)} — the Java analog of the reference's {@code (bool,
   * list[str])} tuple: a validity flag plus the list of error messages.
   */
  public static final class ValidationResult {
    private final boolean valid;
    private final List<String> errors;

    public ValidationResult(boolean valid, List<String> errors) {
      this.valid = valid;
      this.errors = errors;
    }

    /** Whether the config is valid (no errors). */
    public boolean isValid() {
      return valid;
    }

    /** The list of error messages (empty when valid). */
    public List<String> getErrors() {
      return errors;
    }
  }
}
