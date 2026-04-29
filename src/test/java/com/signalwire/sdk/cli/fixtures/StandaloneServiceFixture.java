/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.cli.fixtures;

import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swml.Service;

import java.util.Map;

/**
 * Test fixture for the {@code --class} file-loader mode: a non-AgentBase
 * {@link Service} subclass that registers a single SWAIG tool in its
 * constructor. Exercises the same shape as
 * {@code examples/SwmlServiceSwaigStandalone.java} (the production example
 * the file-loader path is meant to support).
 *
 * <p>The CLI invokes the public no-arg constructor via reflection — no
 * static initialiser, no {@code main()}.
 */
public class StandaloneServiceFixture extends Service {

    public StandaloneServiceFixture() {
        super("standalone-fixture", "/standalone");

        defineTool(
                "lookup_competitor",
                "Look up competitor pricing by company name.",
                Map.of(
                        "competitor", Map.of(
                                "type", "string",
                                "description", "The competitor's company name."
                        )
                ),
                (toolArgs, rawData) -> {
                    Object competitor = toolArgs.getOrDefault("competitor", "<unknown>");
                    return new FunctionResult(competitor + " pricing is $99/seat.");
                }
        );
    }
}
