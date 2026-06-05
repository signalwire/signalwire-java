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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link RelayClient} honours {@link AutoCloseable}: a
 * try-with-resources block actually tears down the live RELAY WebSocket.
 *
 * <p>This drives the REAL client against the shared {@code mock_relay}
 * WebSocket server (no Mockito, no transport stub). The strong assertion is
 * server-side: the mock {@code unregister}s a session when its socket closes,
 * so {@code harness.sessions()} dropping back to zero after the block proves
 * {@code close()} really released the connection — not just that a flag flipped.
 */
class AutoCloseableMockTest {

    @Test
    @DisplayName("RelayClient.close() (try-with-resources) closes the live WebSocket")
    void closeTearsDownWebSocket() throws Exception {
        RelayMockTest.Harness mock = RelayMockTest.harness();

        // The mock_relay server is shared per-process; a preceding test's
        // disconnect may still be settling. Measure the settled baseline and
        // assert the +1 / back-to-baseline delta our own connect/close causes,
        // rather than assuming a global zero.
        int baseline = waitForSettledSessionCount(mock);

        RelayClient escaped;
        try (RelayClient client = RelayClient.builder()
                .project("test_proj")
                .token("test_tok")
                .space(mock.wsUrl())
                .contexts(List.of("default"))
                .build()) {

            escaped = client;
            client.connect(10_000);

            // Inside the block: the WS is up and the mock sees one MORE session.
            assertTrue(client.isConnected(), "client should be connected inside the block");
            assertEquals(baseline + 1, waitForSessionCount(mock, baseline + 1),
                    "mock should register one more live session while connected");
        } // close() runs here

        // After the block: close() must tear the socket down. Both signals are
        // eventually-consistent — the WS close handshake is asynchronous — so we
        // wait for each. The server-side session teardown is the definitive
        // proof the socket actually closed; isConnected() flipping off is the
        // client-side mirror.
        assertEquals(baseline, waitForSessionCount(mock, baseline),
                "the session opened by this client must be unregistered after close()");
        assertFalse(waitForConnected(escaped, false),
                "close() should have disconnected the client");
    }

    @Test
    @DisplayName("RelayClient.close() is idempotent (double-close is a harmless no-op)")
    void closeIsIdempotent() throws Exception {
        RelayMockTest.Harness mock = RelayMockTest.harness();
        int baseline = waitForSettledSessionCount(mock);

        RelayClient client = RelayClient.builder()
                .project("test_proj")
                .token("test_tok")
                .space(mock.wsUrl())
                .contexts(List.of("default"))
                .build();
        client.connect(10_000);
        assertTrue(client.isConnected());

        client.close();
        // Second close() must not throw and the client stays closed.
        client.close();
        assertEquals(baseline, waitForSessionCount(mock, baseline));
        assertFalse(waitForConnected(client, false));
    }

    /**
     * Poll the mock's live-session count until it reaches {@code want} or the
     * budget runs out, returning the last observed value. The WS close handshake
     * is asynchronous, so the post-close session teardown isn't instantaneous.
     */
    /**
     * Wait for the shared mock's live-session count to stop changing (two
     * identical consecutive reads), returning that settled value. Used to take
     * a baseline that absorbs any in-flight teardown from a preceding test.
     */
    private static int waitForSettledSessionCount(RelayMockTest.Harness mock) {
        int prev = mock.sessions().size();
        for (int i = 0; i < 100; i++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            int now = mock.sessions().size();
            if (now == prev) {
                return now;
            }
            prev = now;
        }
        return prev;
    }

    private static int waitForSessionCount(RelayMockTest.Harness mock, int want) {
        int last = -1;
        for (int i = 0; i < 100; i++) {
            last = mock.sessions().size();
            if (last == want) {
                return last;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return last;
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
