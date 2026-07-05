/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.relay;

/**
 * Constants for the SignalWire RELAY protocol.
 *
 * <p>Includes call states, end reasons, message states, event types, and action terminal states
 * used throughout the RELAY client.
 */
public final class Constants {

  private Constants() {
    // Utility class
  }

  // ── SDK metadata ──────────────────────────────────────────────────
  public static final String SDK_AGENT = "signalwire-agents-java/1.0.0";
  public static final int PROTOCOL_MAJOR = 2;
  public static final int PROTOCOL_MINOR = 0;
  public static final int PROTOCOL_REVISION = 0;

  // ── JSON-RPC ──────────────────────────────────────────────────────
  public static final String JSONRPC_VERSION = "2.0";

  // ── SignalWire RPC methods ────────────────────────────────────────
  public static final String METHOD_CONNECT = "signalwire.connect";
  public static final String METHOD_EVENT = "signalwire.event";
  public static final String METHOD_PING = "signalwire.ping";
  public static final String METHOD_DISCONNECT = "signalwire.disconnect";
  public static final String METHOD_RECEIVE = "signalwire.receive";
  public static final String METHOD_UNRECEIVE = "signalwire.unreceive";

  // ── Calling RPC methods ──────────────────────────────────────────
  public static final String METHOD_DIAL = "calling.dial";
  public static final String METHOD_ANSWER = "calling.answer";
  public static final String METHOD_END = "calling.end";
  public static final String METHOD_PASS = "calling.pass";
  public static final String METHOD_CONNECT_CALL = "calling.connect";
  public static final String METHOD_DISCONNECT_CALL = "calling.disconnect";
  public static final String METHOD_PLAY = "calling.play";
  public static final String METHOD_PLAY_PAUSE = "calling.play.pause";
  public static final String METHOD_PLAY_RESUME = "calling.play.resume";
  public static final String METHOD_PLAY_STOP = "calling.play.stop";
  public static final String METHOD_PLAY_VOLUME = "calling.play.volume";
  public static final String METHOD_RECORD = "calling.record";
  public static final String METHOD_RECORD_PAUSE = "calling.record.pause";
  public static final String METHOD_RECORD_RESUME = "calling.record.resume";
  public static final String METHOD_RECORD_STOP = "calling.record.stop";
  public static final String METHOD_DETECT = "calling.detect";
  public static final String METHOD_DETECT_STOP = "calling.detect.stop";
  public static final String METHOD_COLLECT = "calling.collect";
  public static final String METHOD_COLLECT_STOP = "calling.collect.stop";
  public static final String METHOD_COLLECT_START_INPUT_TIMERS =
      "calling.collect.start_input_timers";
  public static final String METHOD_PLAY_AND_COLLECT = "calling.play_and_collect";
  public static final String METHOD_PLAY_AND_COLLECT_STOP = "calling.play_and_collect.stop";
  public static final String METHOD_PLAY_AND_COLLECT_PAUSE = "calling.play_and_collect.pause";
  public static final String METHOD_PLAY_AND_COLLECT_RESUME = "calling.play_and_collect.resume";
  public static final String METHOD_PLAY_AND_COLLECT_VOLUME = "calling.play_and_collect.volume";
  public static final String METHOD_PAY = "calling.pay";
  public static final String METHOD_PAY_STOP = "calling.pay.stop";
  public static final String METHOD_SEND_FAX = "calling.send_fax";
  public static final String METHOD_SEND_FAX_STOP = "calling.send_fax.stop";
  public static final String METHOD_RECEIVE_FAX = "calling.receive_fax";
  public static final String METHOD_RECEIVE_FAX_STOP = "calling.receive_fax.stop";
  public static final String METHOD_TAP = "calling.tap";
  public static final String METHOD_TAP_STOP = "calling.tap.stop";
  public static final String METHOD_STREAM = "calling.stream";
  public static final String METHOD_STREAM_STOP = "calling.stream.stop";
  public static final String METHOD_TRANSCRIBE = "calling.transcribe";
  public static final String METHOD_TRANSCRIBE_STOP = "calling.transcribe.stop";
  public static final String METHOD_AI = "calling.ai";
  public static final String METHOD_AI_STOP = "calling.ai.stop";
  public static final String METHOD_HOLD = "calling.hold";
  public static final String METHOD_UNHOLD = "calling.unhold";
  public static final String METHOD_DENOISE = "calling.denoise";
  public static final String METHOD_DENOISE_STOP = "calling.denoise.stop";
  public static final String METHOD_TRANSFER = "calling.transfer";
  public static final String METHOD_JOIN_CONFERENCE = "calling.join_conference";
  public static final String METHOD_LEAVE_CONFERENCE = "calling.leave_conference";
  public static final String METHOD_ECHO = "calling.echo";
  public static final String METHOD_BIND_DIGIT = "calling.bind_digit";
  public static final String METHOD_CLEAR_DIGIT_BINDINGS = "calling.clear_digit_bindings";
  public static final String METHOD_LIVE_TRANSCRIBE = "calling.live_transcribe";
  public static final String METHOD_LIVE_TRANSLATE = "calling.live_translate";
  public static final String METHOD_JOIN_ROOM = "calling.join_room";
  public static final String METHOD_LEAVE_ROOM = "calling.leave_room";
  public static final String METHOD_AMAZON_BEDROCK = "calling.amazon_bedrock";
  public static final String METHOD_AI_MESSAGE = "calling.ai_message";
  public static final String METHOD_AI_HOLD = "calling.ai_hold";
  public static final String METHOD_AI_UNHOLD = "calling.ai_unhold";
  public static final String METHOD_USER_EVENT = "calling.user_event";
  public static final String METHOD_QUEUE_ENTER = "calling.queue.enter";
  public static final String METHOD_QUEUE_LEAVE = "calling.queue.leave";
  public static final String METHOD_REFER = "calling.refer";
  public static final String METHOD_SEND_DIGITS = "calling.send_digits";

  // ── Messaging RPC methods ────────────────────────────────────────
  public static final String METHOD_MESSAGING_SEND = "messaging.send";

  // ── Call states ──────────────────────────────────────────────────
  public static final String CALL_STATE_CREATED = "created";
  public static final String CALL_STATE_RINGING = "ringing";
  public static final String CALL_STATE_ANSWERED = "answered";
  public static final String CALL_STATE_ENDING = "ending";
  public static final String CALL_STATE_ENDED = "ended";

  // ── Call end reasons ─────────────────────────────────────────────
  public static final String END_REASON_HANGUP = "hangup";
  public static final String END_REASON_CANCEL = "cancel";
  public static final String END_REASON_BUSY = "busy";
  public static final String END_REASON_NO_ANSWER = "noAnswer";
  public static final String END_REASON_DECLINE = "decline";
  public static final String END_REASON_ERROR = "error";

  // ── Dial states ──────────────────────────────────────────────────
  public static final String DIAL_STATE_DIALING = "dialing";
  public static final String DIAL_STATE_ANSWERED = "answered";
  public static final String DIAL_STATE_FAILED = "failed";

  // ── Event types ──────────────────────────────────────────────────
  public static final String EVENT_CALL_STATE = "calling.call.state";
  public static final String EVENT_CALL_RECEIVE = "calling.call.receive";
  public static final String EVENT_CALL_DIAL = "calling.call.dial";
  public static final String EVENT_CALL_PLAY = "calling.call.play";
  public static final String EVENT_CALL_RECORD = "calling.call.record";
  public static final String EVENT_CALL_DETECT = "calling.call.detect";
  public static final String EVENT_CALL_COLLECT = "calling.call.collect";
  public static final String EVENT_CALL_FAX = "calling.call.fax";
  public static final String EVENT_CALL_TAP = "calling.call.tap";
  public static final String EVENT_CALL_STREAM = "calling.call.stream";
  public static final String EVENT_CALL_TRANSCRIBE = "calling.call.transcribe";
  public static final String EVENT_CALL_CONNECT = "calling.call.connect";
  public static final String EVENT_CALL_REFER = "calling.call.refer";
  public static final String EVENT_CALL_SEND_DIGITS = "calling.call.send_digits";
  public static final String EVENT_CALL_PAY = "calling.call.pay";
  public static final String EVENT_CALL_DENOISE = "calling.call.denoise";
  public static final String EVENT_CALL_ECHO = "calling.call.echo";
  public static final String EVENT_CALL_HOLD = "calling.call.hold";
  public static final String EVENT_CALLING_ERROR = "calling.error";
  public static final String EVENT_CONFERENCE = "calling.conference";
  public static final String EVENT_QUEUE = "calling.queue";
  public static final String EVENT_AUTHORIZATION_STATE = "signalwire.authorization.state";

  // ── Messaging event types ────────────────────────────────────────
  public static final String EVENT_MESSAGING_RECEIVE = "messaging.receive";
  public static final String EVENT_MESSAGING_STATE = "messaging.state";

  // ── Message states ───────────────────────────────────────────────
  public static final String MESSAGE_STATE_QUEUED = "queued";
  public static final String MESSAGE_STATE_INITIATED = "initiated";
  public static final String MESSAGE_STATE_SENT = "sent";
  public static final String MESSAGE_STATE_DELIVERED = "delivered";
  public static final String MESSAGE_STATE_UNDELIVERED = "undelivered";
  public static final String MESSAGE_STATE_FAILED = "failed";
  public static final String MESSAGE_STATE_RECEIVED = "received";

  // ── Action states (play, record, etc.) ───────────────────────────
  public static final String ACTION_STATE_PLAYING = "playing";
  public static final String ACTION_STATE_PAUSED = "paused";
  public static final String ACTION_STATE_FINISHED = "finished";
  public static final String ACTION_STATE_ERROR = "error";
  public static final String ACTION_STATE_NO_INPUT = "no_input";
  public static final String ACTION_STATE_NO_MATCH = "no_match";
  public static final String ACTION_STATE_RECORDING = "recording";

  // ── Media types ──────────────────────────────────────────────────
  public static final String MEDIA_TYPE_AUDIO = "audio";
  public static final String MEDIA_TYPE_TTS = "tts";
  public static final String MEDIA_TYPE_SILENCE = "silence";
  public static final String MEDIA_TYPE_RINGTONE = "ringtone";

  // ── Device types ─────────────────────────────────────────────────
  public static final String DEVICE_TYPE_PHONE = "phone";
  public static final String DEVICE_TYPE_SIP = "sip";
  public static final String DEVICE_TYPE_WEBRTC = "webrtc";

  // ── Play direction ───────────────────────────────────────────────
  public static final String DIRECTION_LISTEN = "listen";
  public static final String DIRECTION_SPEAK = "speak";
  public static final String DIRECTION_BOTH = "both";

  // ── Reconnection ─────────────────────────────────────────────────
  public static final long RECONNECT_INITIAL_DELAY_MS = 1000;
  public static final long RECONNECT_MAX_DELAY_MS = 30000;
  public static final double RECONNECT_BACKOFF_MULTIPLIER = 2.0;

  // ── HTTP response codes (string, as returned by the server) ─────
  public static final String CODE_SUCCESS = "200";
  public static final String CODE_NOT_FOUND = "404";
  public static final String CODE_GONE = "410";
  public static final String CODE_CONFLICT = "409";

  /** Check if a call state is terminal. */
  public static boolean isTerminalCallState(String state) {
    return CALL_STATE_ENDED.equals(state);
  }

  /** Check if an action state is terminal (applies to play, record, etc.). */
  public static boolean isTerminalActionState(String state) {
    return ACTION_STATE_FINISHED.equals(state)
        || ACTION_STATE_ERROR.equals(state)
        || ACTION_STATE_NO_INPUT.equals(state)
        || ACTION_STATE_NO_MATCH.equals(state);
  }

  /** Check if a message state is terminal. */
  public static boolean isTerminalMessageState(String state) {
    return MESSAGE_STATE_DELIVERED.equals(state)
        || MESSAGE_STATE_UNDELIVERED.equals(state)
        || MESSAGE_STATE_FAILED.equals(state);
  }

  /** Check if an HTTP code indicates a "call gone" condition. */
  public static boolean isCallGoneCode(String code) {
    return CODE_NOT_FOUND.equals(code) || CODE_GONE.equals(code);
  }
}
