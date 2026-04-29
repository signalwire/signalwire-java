/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.cli.fixtures;

import com.signalwire.sdk.swml.Service;

/**
 * Service fixture with no SWAIG tools registered, used to verify the
 * "No tools found." branch of the file-loader path.
 */
public class EmptyServiceFixture extends Service {

    public EmptyServiceFixture() {
        super("empty-fixture", "/empty");
    }
}
