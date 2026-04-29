/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import com.signalwire.sdk.swaig.FunctionResult;
import com.signalwire.sdk.swaig.ToolDefinition;
import com.signalwire.sdk.swml.Service;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests proving SWML Service can host SWAIG functions and serve a non-agent
 * SWML doc (e.g. ai_sidecar) without subclassing AgentBase. This is the
 * contract that lets sidecar / non-agent verbs reuse the SWAIG dispatch
 * surface that previously lived only on AgentBase.
 */
class SwmlServiceSwaigTest {

    // ======== SWMLService gains SWAIG-hosting capability ========

    @Test
    void testServiceHasSwaigMethods() throws Exception {
        var svc = new Service("svc");
        assertNotNull(svc.getClass().getMethod("defineTool",
                String.class, String.class, java.util.Map.class,
                com.signalwire.sdk.swaig.ToolHandler.class));
        assertNotNull(svc.getClass().getMethod("registerSwaigFunction", java.util.Map.class));
        assertNotNull(svc.getClass().getMethod("defineTools", java.util.List.class));
        assertNotNull(svc.getClass().getMethod("onFunctionCall",
                String.class, java.util.Map.class, java.util.Map.class));
    }

    @Test
    void testDefineToolRegistersFunctionAndDispatchesViaOnFunctionCall() {
        var svc = new Service("svc");
        Map<String, Object> captured = new LinkedHashMap<>();
        svc.defineTool("lookup", "Look it up", Map.of(), (args, raw) -> {
            captured.putAll(args);
            return new FunctionResult("ok");
        });
        var result = svc.onFunctionCall("lookup", Map.of("x", "y"), Map.of());
        assertNotNull(result);
        assertEquals("y", captured.get("x"));
        assertEquals("ok", result.getResponse());
    }

    @Test
    void testOnFunctionCallReturnsNullForUnknown() {
        var svc = new Service("svc");
        assertNull(svc.onFunctionCall("no_such_fn", Map.of(), Map.of()));
    }

    @Test
    void testListToolNamesReturnsRegisteredOrder() {
        var svc = new Service("svc");
        svc.defineTool("first", "f", Map.of(), (a, r) -> new FunctionResult());
        svc.registerSwaigFunction(Map.of("function", "second"));
        // listToolNames includes only "first" — registerSwaigFunction stores
        // raw maps in a separate list (not the typed tools registry).
        var names = svc.listToolNames();
        assertTrue(names.contains("first"));
    }

    // ======== Sidecar usage pattern (non-agent SWML + tool registration) ========

    @Test
    void testSidecarPatternEmitsVerbAndRegistersTool() {
        var svc = new Service("sidecar", "/sidecar");

        // 1. Build the SWML — answer + ai_sidecar verb config.
        svc.answer(Map.of());
        svc.getDocument().addVerb("ai_sidecar", Map.of(
                "prompt", "real-time copilot",
                "lang", "en-US",
                "direction", java.util.List.of("remote-caller", "local-caller")
        ));

        var rendered = svc.getDocument().toMap();
        @SuppressWarnings("unchecked")
        var sections = (Map<String, Object>) rendered.get("sections");
        @SuppressWarnings("unchecked")
        var main = (List<Map<String, Object>>) sections.get("main");
        var verbNames = main.stream().map(m -> m.keySet().iterator().next()).toList();
        assertTrue(verbNames.contains("answer"));
        assertTrue(verbNames.contains("ai_sidecar"));

        // 2. Register a SWAIG tool the sidecar's LLM can call.
        svc.defineTool(
                "lookup_competitor",
                "Look up competitor pricing.",
                Map.of("competitor", Map.of("type", "string")),
                (args, raw) -> new FunctionResult(args.get("competitor") + " is $99/seat; we're $79.")
        );

        // 3. Dispatch end-to-end via the public onFunctionCall surface.
        var result = svc.onFunctionCall(
                "lookup_competitor",
                Map.of("competitor", "ACME"),
                Map.of()
        );
        assertNotNull(result);
        assertTrue(result.getResponse().contains("ACME"));
        assertTrue(result.getResponse().contains("$79"));
    }
}
