/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-unit tests (no mocks, no transport) for the Tier-3 typed RELAY state
 * enums ({@link CallState} / {@link DialState} / {@link MessageState}) and the
 * {@link Device} struct.
 *
 * <p>What these pin, against real directly-constructed / directly-dispatched
 * objects:
 * <ul>
 *   <li>{@code fromWire} round-trips every constant's {@code getValue()} and
 *       returns {@code null} (never throws) for an unknown / null string;</li>
 *   <li>{@code isTerminal()} matches the reference's terminal sets exactly
 *       (CallState: only ENDED; DialState: ANSWERED+FAILED; MessageState:
 *       DELIVERED+UNDELIVERED+FAILED — NOT RECEIVED) and agrees with the
 *       existing {@code Constants.isTerminal*} string predicates;</li>
 *   <li>the typed accessors ({@code Call.getCallState()} /
 *       {@code Message.getMessageState()} / {@code CallDialEvent.getDialStateEnum()})
 *       agree with the parity string getter on a real dispatched event;</li>
 *   <li>the three vocabularies are never conflated (a CallState value is not a
 *       MessageState value, etc.);</li>
 *   <li>{@code Device.toMap()} is byte-identical to the hand-written
 *       {@code {type, params}} map every call site builds.</li>
 * </ul>
 */
class RelayStateEnumTest {

    // ─────────────────────────────────────────────────────────────────
    // CallState
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CallState")
    class CallStateTests {

        @Test
        @DisplayName("getValue() is the wire string for every constant")
        void valuesAreWireStrings() {
            assertEquals("created", CallState.CREATED.getValue());
            assertEquals("ringing", CallState.RINGING.getValue());
            assertEquals("answered", CallState.ANSWERED.getValue());
            assertEquals("ending", CallState.ENDING.getValue());
            assertEquals("ended", CallState.ENDED.getValue());
        }

        @Test
        @DisplayName("fromWire round-trips every constant's getValue()")
        void fromWireRoundTrips() {
            for (CallState s : CallState.values()) {
                assertSame(s, CallState.fromWire(s.getValue()),
                        "round-trip failed for " + s);
            }
        }

        @Test
        @DisplayName("fromWire returns null (never throws) for unknown/null")
        void fromWireUnknownIsNull() {
            assertNull(CallState.fromWire("teleporting"));   // future server value
            assertNull(CallState.fromWire(""));
            assertNull(CallState.fromWire(null));
            // a Dial/Message-only value is NOT a CallState
            assertNull(CallState.fromWire("dialing"));       // DialState-only
            assertNull(CallState.fromWire("delivered"));     // MessageState-only
        }

        @Test
        @DisplayName("isTerminal(): only ENDED is terminal")
        void isTerminal() {
            assertTrue(CallState.ENDED.isTerminal());
            assertFalse(CallState.CREATED.isTerminal());
            assertFalse(CallState.RINGING.isTerminal());
            assertFalse(CallState.ANSWERED.isTerminal());
            // ENDING is NOT terminal — an "ended" event still follows.
            assertFalse(CallState.ENDING.isTerminal());
        }

        @Test
        @DisplayName("isTerminal() agrees with Constants.isTerminalCallState(String)")
        void isTerminalAgreesWithStringPredicate() {
            for (CallState s : CallState.values()) {
                assertEquals(Constants.isTerminalCallState(s.getValue()), s.isTerminal(),
                        "enum/string terminal disagreement for " + s);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // DialState
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DialState")
    class DialStateTests {

        @Test
        @DisplayName("getValue() is the wire string for every constant")
        void valuesAreWireStrings() {
            assertEquals("dialing", DialState.DIALING.getValue());
            assertEquals("answered", DialState.ANSWERED.getValue());
            assertEquals("failed", DialState.FAILED.getValue());
        }

        @Test
        @DisplayName("fromWire round-trips every constant's getValue()")
        void fromWireRoundTrips() {
            for (DialState s : DialState.values()) {
                assertSame(s, DialState.fromWire(s.getValue()),
                        "round-trip failed for " + s);
            }
        }

        @Test
        @DisplayName("fromWire returns null (never throws) for unknown/null")
        void fromWireUnknownIsNull() {
            assertNull(DialState.fromWire("connecting"));
            assertNull(DialState.fromWire(""));
            assertNull(DialState.fromWire(null));
            // CallState-only values are not DialStates
            assertNull(DialState.fromWire("ringing"));
            assertNull(DialState.fromWire("ended"));
        }

        @Test
        @DisplayName("isTerminal(): both ANSWERED and FAILED are terminal, DIALING is not")
        void isTerminal() {
            assertTrue(DialState.ANSWERED.isTerminal());
            assertTrue(DialState.FAILED.isTerminal());
            assertFalse(DialState.DIALING.isTerminal());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // MessageState
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MessageState")
    class MessageStateTests {

        @Test
        @DisplayName("getValue() is the wire string for every constant")
        void valuesAreWireStrings() {
            assertEquals("queued", MessageState.QUEUED.getValue());
            assertEquals("initiated", MessageState.INITIATED.getValue());
            assertEquals("sent", MessageState.SENT.getValue());
            assertEquals("delivered", MessageState.DELIVERED.getValue());
            assertEquals("undelivered", MessageState.UNDELIVERED.getValue());
            assertEquals("failed", MessageState.FAILED.getValue());
            assertEquals("received", MessageState.RECEIVED.getValue());
        }

        @Test
        @DisplayName("fromWire round-trips every constant's getValue()")
        void fromWireRoundTrips() {
            for (MessageState s : MessageState.values()) {
                assertSame(s, MessageState.fromWire(s.getValue()),
                        "round-trip failed for " + s);
            }
        }

        @Test
        @DisplayName("fromWire returns null (never throws) for unknown/null")
        void fromWireUnknownIsNull() {
            assertNull(MessageState.fromWire("read"));     // plausible future value
            assertNull(MessageState.fromWire(""));
            assertNull(MessageState.fromWire(null));
            // a Call/Dial-only value is not a MessageState
            assertNull(MessageState.fromWire("ringing"));
            assertNull(MessageState.fromWire("dialing"));
        }

        @Test
        @DisplayName("isTerminal(): delivered/undelivered/failed are terminal; received is NOT")
        void isTerminal() {
            assertTrue(MessageState.DELIVERED.isTerminal());
            assertTrue(MessageState.UNDELIVERED.isTerminal());
            assertTrue(MessageState.FAILED.isTerminal());

            assertFalse(MessageState.QUEUED.isTerminal());
            assertFalse(MessageState.INITIATED.isTerminal());
            assertFalse(MessageState.SENT.isTerminal());
            // received is an inbound arrival, not an outbound completion
            assertFalse(MessageState.RECEIVED.isTerminal());
        }

        @Test
        @DisplayName("isTerminal() agrees with Constants.isTerminalMessageState(String)")
        void isTerminalAgreesWithStringPredicate() {
            for (MessageState s : MessageState.values()) {
                assertEquals(Constants.isTerminalMessageState(s.getValue()), s.isTerminal(),
                        "enum/string terminal disagreement for " + s);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // The three vocabularies never conflate
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CallState / DialState / MessageState are three distinct vocabularies")
    void threeVocabulariesNeverConflated() {
        // 'answered' is the one overlap (CallState + DialState) and means
        // different things; it is NEVER a MessageState.
        assertNotNull(CallState.fromWire("answered"));
        assertNotNull(DialState.fromWire("answered"));
        assertNull(MessageState.fromWire("answered"));

        // 'ringing' (CallState) is not a dial outcome nor a message state.
        assertNotNull(CallState.fromWire("ringing"));
        assertNull(DialState.fromWire("ringing"));
        assertNull(MessageState.fromWire("ringing"));

        // 'dialing' (DialState) is not a call lifecycle nor a message state.
        assertNull(CallState.fromWire("dialing"));
        assertNotNull(DialState.fromWire("dialing"));
        assertNull(MessageState.fromWire("dialing"));

        // 'delivered' (MessageState) is neither a call nor a dial state.
        assertNull(CallState.fromWire("delivered"));
        assertNull(DialState.fromWire("delivered"));
        assertNotNull(MessageState.fromWire("delivered"));
    }

    // ─────────────────────────────────────────────────────────────────
    // Typed accessors agree with the string getter (direct construction)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Call.getCallState() agrees with getState() across a dispatched lifecycle")
    void callTypedAccessorAgreesWithStringGetter() {
        Call call = new Call("c-1", "node-1");
        // fresh call defaults to CREATED
        assertEquals("created", call.getState());
        assertEquals(CallState.CREATED, call.getCallState().orElseThrow());
        assertEquals(call.getState(), call.getCallState().orElseThrow().getValue());

        // drive it through a real CallStateEvent dispatch
        for (CallState expected : CallState.values()) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("call_id", "c-1");
            params.put("call_state", expected.getValue());
            call.dispatchEvent(new RelayEvent.CallStateEvent(
                    Constants.EVENT_CALL_STATE, 0.0, params));

            assertEquals(expected.getValue(), call.getState());
            assertEquals(expected, call.getCallState().orElseThrow());
            // the two agree
            assertEquals(call.getState(), call.getCallState().orElseThrow().getValue());
        }
    }

    @Test
    @DisplayName("Call.getCallState() is empty for an unknown raw state (getState() still returns it)")
    void callTypedAccessorEmptyOnUnknown() {
        Call call = new Call("c-2", "node-1");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("call_id", "c-2");
        params.put("call_state", "warp_speed");          // unknown to the SDK
        call.dispatchEvent(new RelayEvent.CallStateEvent(
                Constants.EVENT_CALL_STATE, 0.0, params));

        // raw string getter preserves the unknown value (parity / forward-compat)
        assertEquals("warp_speed", call.getState());
        // typed accessor declines rather than guessing or throwing
        assertTrue(call.getCallState().isEmpty());
    }

    @Test
    @DisplayName("Message.getMessageState() agrees with getState() on a failed terminal event")
    void messageTypedAccessorAgreesWithStringGetter() {
        Message msg = new Message("m-1");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("message_id", "m-1");
        params.put("message_state", MessageState.FAILED.getValue());
        params.put("reason", "carrier_blocked");
        msg.updateFromEvent(new RelayEvent.MessagingStateEvent(
                Constants.EVENT_MESSAGING_STATE, 0.0, params));

        assertEquals("failed", msg.getState());
        assertEquals(MessageState.FAILED, msg.getMessageState().orElseThrow());
        assertEquals(msg.getState(), msg.getMessageState().orElseThrow().getValue());
        // terminal -> message resolved
        assertTrue(msg.isDone());
        assertTrue(msg.getMessageState().orElseThrow().isTerminal());
    }

    @Test
    @DisplayName("Message.getMessageState() tracks an intermediate (non-terminal) state")
    void messageTypedAccessorIntermediate() {
        Message msg = new Message("m-2");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("message_id", "m-2");
        params.put("message_state", MessageState.SENT.getValue());
        msg.updateFromEvent(new RelayEvent.MessagingStateEvent(
                Constants.EVENT_MESSAGING_STATE, 0.0, params));

        assertEquals("sent", msg.getState());
        assertEquals(MessageState.SENT, msg.getMessageState().orElseThrow());
        assertFalse(msg.getMessageState().orElseThrow().isTerminal());
        assertFalse(msg.isDone());
    }

    @Test
    @DisplayName("CallDialEvent.getDialStateEnum() agrees with getDialState()")
    void dialEventTypedAccessorAgreesWithStringGetter() {
        for (DialState expected : DialState.values()) {
            Map<String, Object> inner = new LinkedHashMap<>();
            inner.put("tag", "t-1");
            inner.put("dial_state", expected.getValue());
            inner.put("call", new LinkedHashMap<>());
            RelayEvent.CallDialEvent event = new RelayEvent.CallDialEvent(
                    Constants.EVENT_CALL_DIAL, 0.0, inner);

            assertEquals(expected.getValue(), event.getDialState());
            assertEquals(expected, event.getDialStateEnum().orElseThrow());
            assertEquals(event.getDialState(), event.getDialStateEnum().orElseThrow().getValue());
        }
    }

    @Test
    @DisplayName("CallDialEvent.getDialStateEnum() is empty for an unknown dial_state")
    void dialEventTypedAccessorEmptyOnUnknown() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("tag", "t-1");
        inner.put("dial_state", "negotiating");           // unknown
        inner.put("call", new LinkedHashMap<>());
        RelayEvent.CallDialEvent event = new RelayEvent.CallDialEvent(
                Constants.EVENT_CALL_DIAL, 0.0, inner);

        assertEquals("negotiating", event.getDialState());
        assertTrue(event.getDialStateEnum().isEmpty());
    }

    // ─────────────────────────────────────────────────────────────────
    // Device.toMap() byte-identical to the hand-written map
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Device.toMap() is identical to the hand-written {type, params} map")
    void deviceToMapMatchesHandWritten() {
        // The exact shape every dial/connect call site builds today.
        Map<String, Object> handParams = new LinkedHashMap<>();
        handParams.put("to_number", "+15551112222");
        handParams.put("from_number", "+15553334444");
        Map<String, Object> handWritten = new LinkedHashMap<>();
        handWritten.put("type", "phone");
        handWritten.put("params", handParams);

        Device device = Device.of("phone", Map.of(
                "to_number", "+15551112222",
                "from_number", "+15553334444"));
        Map<String, Object> fromDevice = device.toMap();

        // value-equal as maps...
        assertEquals(handWritten, fromDevice);
        // ...and key-order identical (type before params) — matters for any
        // canonical-JSON / byte comparison of the wire frame.
        assertEquals(List.copyOf(handWritten.keySet()),
                List.copyOf(fromDevice.keySet()));
        @SuppressWarnings("unchecked")
        Map<String, Object> fromParams = (Map<String, Object>) fromDevice.get("params");
        assertEquals(List.copyOf(handParams.keySet()),
                List.copyOf(fromParams.keySet()));
    }

    @Test
    @DisplayName("Device.phone() factory produces the canonical phone shape")
    void devicePhoneFactory() {
        Device d = Device.phone("+15551112222", "+15553334444");
        assertEquals("phone", d.getType());

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("type", "phone");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("to_number", "+15551112222");
        p.put("from_number", "+15553334444");
        expected.put("params", p);
        assertEquals(expected, d.toMap());
    }

    @Test
    @DisplayName("Device routes through dial identically to a raw map (wire shape parity)")
    void deviceWireShapeParityForDial() {
        // typed
        Device typed = Device.phone("+15551110001", "+15553334444");
        List<List<Map<String, Object>>> typedDevices =
                List.of(List.of(typed.toMap()));

        // raw, as a call site writes today
        Map<String, Object> rawParams = new LinkedHashMap<>();
        rawParams.put("to_number", "+15551110001");
        rawParams.put("from_number", "+15553334444");
        Map<String, Object> rawDev = new LinkedHashMap<>();
        rawDev.put("type", "phone");
        rawDev.put("params", rawParams);
        List<List<Map<String, Object>>> rawDevices = List.of(List.of(rawDev));

        assertEquals(rawDevices, typedDevices);
    }

    @Test
    @DisplayName("Device: null type rejected, null params -> empty, params defensively copied")
    void deviceNullHandlingAndDefensiveCopy() {
        assertThrows(IllegalArgumentException.class, () -> new Device(null, Map.of()));

        Device noParams = Device.of("sip", null);
        assertEquals("sip", noParams.getType());
        assertTrue(noParams.getParams().isEmpty());
        // toMap still carries an (empty) params object
        assertEquals(Map.of("type", "sip", "params", Map.of()), noParams.toMap());

        // defensive copy: mutating the source map after construction does not
        // leak into the Device.
        Map<String, Object> mutable = new LinkedHashMap<>();
        mutable.put("to", "sip:alice@example.com");
        Device d = Device.of("sip", mutable);
        mutable.put("to", "sip:mallory@evil.com");
        assertEquals("sip:alice@example.com", d.getParams().get("to"));
        assertEquals("sip:alice@example.com", d.toMap().get("params") instanceof Map
                ? ((Map<?, ?>) d.toMap().get("params")).get("to") : null);
    }

    @Test
    @DisplayName("Device.sip() factory omits a null from")
    void deviceSipFactoryOmitsNullFrom() {
        Device withFrom = Device.sip("sip:bob@example.com", "sip:alice@example.com");
        assertEquals("sip:alice@example.com", withFrom.getParams().get("from"));

        Device noFrom = Device.sip("sip:bob@example.com", null);
        assertEquals("sip", noFrom.getType());
        assertEquals("sip:bob@example.com", noFrom.getParams().get("to"));
        assertFalse(noFrom.getParams().containsKey("from"),
                "null from must be omitted, not stored as null");
    }
}
