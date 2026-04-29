/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.cli.fixtures;

/**
 * Plain class that does NOT extend {@code com.signalwire.sdk.swml.Service}.
 * Used to verify the file-loader rejects non-Service classes with a
 * clear error message.
 */
public class NotAServiceFixture {
    public NotAServiceFixture() {
    }
}
