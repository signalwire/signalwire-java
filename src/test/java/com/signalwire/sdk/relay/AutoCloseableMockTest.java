/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link RelayClient} honours {@link AutoCloseable}: a
 * try-with-resources block actually tears down the live RELAY WebSocket.
 *
 * <p>This drives the REAL client against the shared {@code mock_relay}
 * WebSocket server (no Mockito, no transport stub). The strong assertion is
 * server-side: the mock {@code unregister}s a session when its socket closes,
 * so THIS client's own session id disappearing from {@code harness.sessions()}
 * after the block proves {@code close()} really released the connection — not
 * just that a flag flipped. We match on the client's own session id rather than
 * a global session count, so the assertion is deterministic under parallel
 * execution (other tests' sessions come and go on the shared mock).
 */
class AutoCloseableMockTest {

    @Test
    @DisplayName("RelayClient.close() (try-with-resources) closes the live WebSocket")
    void closeTearsDownWebSocket() throws Exception {
        RelayMockTest.Harness mock = RelayMockTest.harness();

        RelayClient escaped;
        String sid;
        try (RelayClient client = RelayClient.builder()
                .project("test_proj")
                .token("test_tok")
                .space(mock.wsUrl())
                .contexts(List.of("default"))
                .build()) {

            escaped = client;
            client.connect(10_000);

            // Inside the block: the WS is up and the mock registers THIS
            // client's session id among its live sessions.
            assertTrue(client.isConnected(), "client should be connected inside the block");
            sid = waitForSessionId(client);
            assertNotNull(sid, "client should have captured a handshake session id");
            assertTrue(waitForSessionPresence(mock, sid, true),
                    "mock should register this client's session while connected");
        } // close() runs here

        // After the block: close() must tear the socket down. Both signals are
        // eventually-consistent — the WS close handshake is asynchronous — so we
        // wait for each. The server-side session teardown is the definitive
        // proof the socket actually closed; isConnected() flipping off is the
        // client-side mirror.
        assertFalse(waitForSessionPresence(mock, sid, false),
                "this client's session must be unregistered after close()");
        assertFalse(waitForConnected(escaped, false),
                "close() should have disconnected the client");
    }

    @Test
    @DisplayName("RelayClient.close() is idempotent (double-close is a harmless no-op)")
    void closeIsIdempotent() throws Exception {
        RelayMockTest.Harness mock = RelayMockTest.harness();

        RelayClient client = RelayClient.builder()
                .project("test_proj")
                .token("test_tok")
                .space(mock.wsUrl())
                .contexts(List.of("default"))
                .build();
        client.connect(10_000);
        assertTrue(client.isConnected());
        String sid = waitForSessionId(client);
        assertNotNull(sid);

        client.close();
        // Second close() must not throw and the client stays closed.
        client.close();
        assertFalse(waitForSessionPresence(mock, sid, false),
                "this client's session must be unregistered after close()");
        assertFalse(waitForConnected(client, false));
    }

    /** Poll for the client's handshake session id, returning it once captured. */
    private static String waitForSessionId(RelayClient client) {
        String sid = client.sessionIdForTesting();
        for (int i = 0; i < 100 && sid == null; i++) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            sid = client.sessionIdForTesting();
        }
        return sid;
    }

    /**
     * Poll the mock's live-session list until {@code sid}'s presence matches
     * {@code want} or the budget runs out, returning the last observed
     * presence. Scoped to a specific session id so concurrent tests' sessions
     * on the shared mock don't perturb the result.
     */
    private static boolean waitForSessionPresence(
            RelayMockTest.Harness mock, String sid, boolean want) {
        boolean present = sessionPresent(mock, sid);
        for (int i = 0; i < 100 && present != want; i++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            present = sessionPresent(mock, sid);
        }
        return present;
    }

    private static boolean sessionPresent(RelayMockTest.Harness mock, String sid) {
        for (Map<String, Object> s : mock.sessions()) {
            if (sid.equals(String.valueOf(s.get("id")))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Poll {@link RelayClient#isConnected()} until it reaches {@code want} or
     * the budget runs out, returning the last observed value. The connection
     * flag flips on the asynchronous WS onClose callback, so close() doesn't
     * update it synchronously.
     */
    private static boolean waitForConnected(RelayClient client, boolean want) {
        boolean last = client.isConnected();
        for (int i = 0; i < 100 && last != want; i++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            last = client.isConnected();
        }
        return last;
    }
}
