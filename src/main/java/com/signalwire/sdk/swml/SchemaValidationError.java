/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import java.util.Collections;
import java.util.List;

/** Raised when SWML schema validation of a verb config fails. */
public class SchemaValidationError extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String verbName;
  private final List<String> errors;

  /**
   * Construct a SchemaValidationError.
   *
   * @param verbName the verb whose validation failed
   * @param errors the list of human-readable error messages
   */
  public SchemaValidationError(String verbName, List<String> errors) {
    super(buildMessage(verbName, errors));
    this.verbName = verbName;
    this.errors = errors == null ? Collections.emptyList() : Collections.unmodifiableList(errors);
  }

  /**
   * The SWML verb whose arguments failed validation.
   *
   * @return the verb name.
   */
  public String getVerbName() {
    return verbName;
  }

  /**
   * Every validation failure found, not just the first.
   *
   * @return the error messages.
   */
  public List<String> getErrors() {
    return errors;
  }

  private static String buildMessage(String verbName, List<String> errors) {
    return "Schema validation failed for '"
        + verbName
        + "': "
        + (errors == null ? "" : String.join("; ", errors));
  }
}
