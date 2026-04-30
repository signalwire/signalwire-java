/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk;

import com.signalwire.sdk.swml.Service;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Service#onRequest(Map, String)} and
 * {@link Service#onSwmlRequest(Map, String)} — Python WebMixin parity.
 *
 * <p>Python parity:
 * <pre>
 *   tests/unit/core/mixins/test_web_mixin.py::
 *     test_on_request_delegates_to_on_swml_request
 *     test_on_swml_request_called
 * </pre>
 */
class WebMixinTest {

    /** Subclass that captures inputs and returns a configured map. */
    static class CustomSwmlService extends Service {
        Map<String, Object> lastRequestData;
        String lastCallbackPath;
        Map<String, Object> customReturn;

        CustomSwmlService(String name) {
            super(name);
        }

        @Override
        public Map<String, Object> onSwmlRequest(Map<String, Object> requestData,
                                                  String callbackPath) {
            this.lastRequestData = requestData;
            this.lastCallbackPath = callbackPath;
            return customReturn;
        }
    }

    @Test
    void onRequest_delegatesToOnSwmlRequest() {
        var svc = new CustomSwmlService("t");
        svc.customReturn = new HashMap<>(Map.of("custom", true));

        var rd = new HashMap<String, Object>(Map.of("data", "val"));
        var result = svc.onRequest(rd, "/cb");

        assertEquals(rd, svc.lastRequestData);
        assertEquals("/cb", svc.lastCallbackPath);
        assertNotNull(result);
        assertEquals(Boolean.TRUE, result.get("custom"));
    }

    @Test
    void onSwmlRequest_defaultReturnsNull() {
        var svc = new Service("t");
        // Default onSwmlRequest returns null — caller falls back to
        // standard SWML rendering.
        assertNull(svc.onSwmlRequest(null, null));
    }

    @Test
    void onRequest_defaultReturnsNull() {
        var svc = new Service("t");
        assertNull(svc.onRequest(null, null));
    }

    @Test
    void onRequest_passesNullsThroughToHook() {
        var svc = new CustomSwmlService("t");
        svc.customReturn = null;
        var result = svc.onRequest(null, null);
        assertNull(result);
        assertNull(svc.lastRequestData);
        assertNull(svc.lastCallbackPath);
    }
}
