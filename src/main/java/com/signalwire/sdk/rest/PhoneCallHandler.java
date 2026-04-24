/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

/**
 * Values accepted for {@code call_handler} on
 * {@link com.signalwire.sdk.rest.namespaces.PhoneNumbersNamespace#update(String, java.util.Map) phoneNumbers().update}.
 * <p>
 * Named {@code PhoneCallHandler} (not {@code CallHandler}) to avoid colliding
 * with the RELAY client's inbound-call-handler callback type.
 * <p>
 * Setting a phone number's {@code call_handler} + the handler-specific
 * companion field routes inbound calls and auto-materializes the matching
 * Fabric resource on the server. See the typed helpers on
 * {@link com.signalwire.sdk.rest.namespaces.PhoneNumbersNamespace}
 * ({@code setSwmlWebhook}, {@code setCxmlWebhook}, ...).
 *
 * <p>Binding table:
 * <pre>
 * Enum member        Companion field (required)        Auto-creates resource
 * -----------------  --------------------------------  ---------------------
 * RELAY_SCRIPT       call_relay_script_url             swml_webhook
 * LAML_WEBHOOKS      call_request_url                  cxml_webhook
 * LAML_APPLICATION   call_laml_application_id          cxml_application
 * AI_AGENT           call_ai_agent_id                  ai_agent
 * CALL_FLOW          call_flow_id                      call_flow
 * RELAY_APPLICATION  call_relay_application            relay_application
 * RELAY_TOPIC        call_relay_topic                  (routes via RELAY)
 * RELAY_CONTEXT      call_relay_context                (legacy, prefer topic)
 * RELAY_CONNECTOR    (connector config)                (internal)
 * VIDEO_ROOM         call_video_room_id                (routes to Video API)
 * DIALOGFLOW         call_dialogflow_agent_id          (none)
 * </pre>
 *
 * <p>Note: {@link #LAML_WEBHOOKS} (wire value {@code laml_webhooks}) produces
 * a <b>cXML</b> handler, not a generic webhook. For SWML, use
 * {@link #RELAY_SCRIPT}.
 */
public enum PhoneCallHandler {

    RELAY_SCRIPT("relay_script"),
    LAML_WEBHOOKS("laml_webhooks"),
    LAML_APPLICATION("laml_application"),
    AI_AGENT("ai_agent"),
    CALL_FLOW("call_flow"),
    RELAY_APPLICATION("relay_application"),
    RELAY_TOPIC("relay_topic"),
    RELAY_CONTEXT("relay_context"),
    RELAY_CONNECTOR("relay_connector"),
    VIDEO_ROOM("video_room"),
    DIALOGFLOW("dialogflow");

    private final String wireValue;

    PhoneCallHandler(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * The wire value sent on the {@code call_handler} field.
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Returns the wire value so this enum serializes transparently into
     * request bodies without an explicit {@code .wireValue()} indirection.
     */
    @Override
    public String toString() {
        return wireValue;
    }
}
