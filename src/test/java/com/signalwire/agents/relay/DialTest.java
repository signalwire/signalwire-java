package com.signalwire.agents.relay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RELAY dial-related constants and states.
 */
class DialTest {

    @Test
    void testDialStates() {
        assertEquals("dialing", Constants.DIAL_STATE_DIALING);
        assertEquals("answered", Constants.DIAL_STATE_ANSWERED);
        assertEquals("failed", Constants.DIAL_STATE_FAILED);
    }

    @Test
    void testDialRpcMethod() {
        assertEquals("calling.dial", Constants.METHOD_DIAL);
    }

    @Test
    void testDialEventType() {
        assertEquals("calling.call.dial", Constants.EVENT_CALL_DIAL);
    }

    @Test
    void testDeviceTypes() {
        assertEquals("phone", Constants.DEVICE_TYPE_PHONE);
        assertEquals("sip", Constants.DEVICE_TYPE_SIP);
        assertEquals("webrtc", Constants.DEVICE_TYPE_WEBRTC);
    }

    @Test
    void testCallAnswerMethod() {
        assertEquals("calling.answer", Constants.METHOD_ANSWER);
    }

    @Test
    void testCallEndMethod() {
        assertEquals("calling.end", Constants.METHOD_END);
    }

    @Test
    void testCallPassMethod() {
        assertEquals("calling.pass", Constants.METHOD_PASS);
    }

    @Test
    void testCallTransferMethod() {
        assertEquals("calling.transfer", Constants.METHOD_TRANSFER);
    }
}
