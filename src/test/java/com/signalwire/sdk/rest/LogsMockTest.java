/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mock-backed unit tests translated from
 * signalwire-python/tests/unit/rest/test_logs_mock.py.
 *
 * <p>Covers Logs sub-resources for messages, voice, fax, and conferences.
 */
class LogsMockTest {

    private RestClient client;
    private MockTest.Harness mock;

    @BeforeEach
    void setUp() {
        MockTest.Bound bound = MockTest.newClient();
        this.client = bound.client;
        this.mock = bound.harness;
    }

    @Nested
    @DisplayName("Logs.messages")
    class Messages {

        @Test
        void listReturnsDict() {
            Map<String, Object> body = client.logs().messages().list();
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/messaging/logs", j.path);
            assertEquals("message.list_message_logs", j.getMatchedRoute());
        }

        @Test
        void getUsesIdInPath() {
            Map<String, Object> body = client.logs().messages().get("ml-42");
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/messaging/logs/ml-42", j.path);
            assertNotNull(j.getMatchedRoute(), "spec gap: message log retrieve");
        }
    }

    @Nested
    @DisplayName("Logs.voice")
    class Voice {

        @Test
        void listReturnsDict() {
            Map<String, Object> body = client.logs().voice().list();
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/voice/logs", j.path);
            assertEquals("voice.list_voice_logs", j.getMatchedRoute());
        }

        @Test
        void getUsesIdInPath() {
            Map<String, Object> body = client.logs().voice().get("vl-99");
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/voice/logs/vl-99", j.path);
        }
    }

    @Nested
    @DisplayName("Logs.fax")
    class Fax {

        @Test
        void listReturnsDict() {
            Map<String, Object> body = client.logs().fax().list();
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/fax/logs", j.path);
            assertEquals("fax.list_fax_logs", j.getMatchedRoute());
        }

        @Test
        void getUsesIdInPath() {
            Map<String, Object> body = client.logs().fax().get("fl-7");
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/fax/logs/fl-7", j.path);
        }
    }

    @Nested
    @DisplayName("Logs.conferences")
    class Conferences {

        @Test
        void listReturnsDict() {
            Map<String, Object> body = client.logs().conferences().list();
            assertNotNull(body);

            MockTest.JournalEntry j = mock.last();
            assertEquals("GET", j.method);
            assertEquals("/api/logs/conferences", j.path);
            assertEquals("logs.list_conferences", j.getMatchedRoute());
        }
    }
}
