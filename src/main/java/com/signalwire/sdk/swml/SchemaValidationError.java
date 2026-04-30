/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.swml;

import java.util.Collections;
import java.util.List;

/**
 * SchemaValidationError — Java port of
 * {@code signalwire.utils.schema_utils.SchemaValidationError}.
 *
 * <p>Raised when SWML schema validation of a verb config fails.</p>
 */
public class SchemaValidationError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String verbName;
    private final List<String> errors;

    /**
     * Construct a SchemaValidationError. Mirrors Python's
     * {@code SchemaValidationError(verb_name, errors)} signature.
     *
     * @param verbName the verb whose validation failed
     * @param errors the list of human-readable error messages
     */
    public SchemaValidationError(String verbName, List<String> errors) {
        super(buildMessage(verbName, errors));
        this.verbName = verbName;
        this.errors = errors == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(errors);
    }

    public String getVerbName() {
        return verbName;
    }

    public List<String> getErrors() {
        return errors;
    }

    private static String buildMessage(String verbName, List<String> errors) {
        return "Schema validation failed for '" + verbName + "': "
                + (errors == null ? "" : String.join("; ", errors));
    }
}
