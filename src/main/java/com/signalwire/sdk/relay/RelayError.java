/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

/**
 * Runtime exception for RELAY-level failures (dial timeout, dial failed,
 * connect rejected, etc.). Mirrors the Python {@code RelayError} class.
 */
public class RelayError extends RuntimeException {

    public RelayError(String message) {
        super(message);
    }

    public RelayError(String message, Throwable cause) {
        super(message, cause);
    }
}
