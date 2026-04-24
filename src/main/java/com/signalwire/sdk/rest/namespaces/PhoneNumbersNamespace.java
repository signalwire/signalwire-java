/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;
import com.signalwire.sdk.rest.PhoneCallHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST namespace for phone number management.
 * <p>
 * Supports the standard CRUD surface plus typed helpers for binding an
 * inbound call to a handler (SWML webhook, cXML webhook, AI agent, call
 * flow, RELAY application/topic). The binding model is: set
 * {@code call_handler} + the handler-specific companion field on the phone
 * number; the server auto-materializes the matching Fabric resource.
 * See {@link PhoneCallHandler} for the enum and {@code rest/docs/phone-binding.md}
 * for the full model.
 */
public class PhoneNumbersNamespace {

    private final CrudResource numbers;
    private final HttpClient httpClient;

    public PhoneNumbersNamespace(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.numbers = new CrudResource(httpClient, "/phone_numbers");
    }

    /** List all phone numbers. */
    public Map<String, Object> list() { return numbers.list(); }

    /** List phone numbers with query parameters. */
    public Map<String, Object> list(Map<String, String> queryParams) { return numbers.list(queryParams); }

    /** Get a single phone number. */
    public Map<String, Object> get(String id) { return numbers.get(id); }

    /** Purchase a phone number. */
    public Map<String, Object> create(Map<String, Object> body) { return numbers.create(body); }

    /** Update a phone number. */
    public Map<String, Object> update(String id, Map<String, Object> body) { return numbers.update(id, body); }

    /** Release a phone number. */
    public Map<String, Object> delete(String id) { return numbers.delete(id); }

    /** Search available phone numbers. */
    public Map<String, Object> search(Map<String, String> queryParams) {
        return httpClient.get("/phone_numbers/search", queryParams);
    }

    /** Get the underlying CRUD resource. */
    public CrudResource getResource() { return numbers; }

    // ── Typed binding helpers ────────────────────────────────────────────
    //
    // Each helper is a one-line wrapper over update() with the right
    // call_handler + companion field already set. The server materializes
    // the matching Fabric resource (swml_webhook, cxml_webhook, ai_agent,
    // call_flow, ...) as a side-effect.
    //
    // You do NOT need to pre-create a Fabric webhook resource, and you do
    // NOT call assign_phone_route — those paths are for different use-cases
    // and will leave orphans or 422 against the live API.

    /**
     * Route inbound calls on this phone number to an SWML webhook URL.
     * <p>
     * Your backend returns an SWML document per call. The server
     * auto-creates a {@code swml_webhook} Fabric resource keyed off this URL.
     *
     * @param id phone number SID
     * @param url SWML endpoint the server should fetch per call
     * @return the updated phone number representation
     */
    public Map<String, Object> setSwmlWebhook(String id, String url) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("call_handler", PhoneCallHandler.RELAY_SCRIPT.wireValue());
        body.put("call_relay_script_url", url);
        return update(id, body);
    }

    /**
     * Route inbound calls to a cXML (Twilio-compat / LAML) webhook.
     * <p>
     * Despite the wire value {@code laml_webhooks} being plural, this
     * creates a single {@code cxml_webhook} Fabric resource.
     *
     * @param id phone number SID
     * @param url primary cXML endpoint
     */
    public Map<String, Object> setCxmlWebhook(String id, String url) {
        return setCxmlWebhook(id, url, null, null);
    }

    /**
     * Route inbound calls to a cXML webhook with a fallback URL.
     *
     * @param id phone number SID
     * @param url primary cXML endpoint
     * @param fallbackUrl URL the server falls back to when the primary fails
     */
    public Map<String, Object> setCxmlWebhook(String id, String url, String fallbackUrl) {
        return setCxmlWebhook(id, url, fallbackUrl, null);
    }

    /**
     * Route inbound calls to a cXML webhook with optional fallback and
     * status-callback URLs. Pass {@code null} to omit either.
     *
     * @param id phone number SID
     * @param url primary cXML endpoint
     * @param fallbackUrl fallback URL (may be {@code null})
     * @param statusCallbackUrl call-status callback (may be {@code null})
     */
    public Map<String, Object> setCxmlWebhook(String id, String url, String fallbackUrl, String statusCallbackUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("call_handler", PhoneCallHandler.LAML_WEBHOOKS.wireValue());
        body.put("call_request_url", url);
        if (fallbackUrl != null) {
            body.put("call_fallback_url", fallbackUrl);
        }
        if (statusCallbackUrl != null) {
            body.put("call_status_callback_url", statusCallbackUrl);
        }
        return update(id, body);
    }

    /**
     * Route inbound calls to an existing cXML application by ID.
     *
     * @param id phone number SID
     * @param applicationId cXML application ID
     */
    public Map<String, Object> setCxmlApplication(String id, String applicationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("call_handler", PhoneCallHandler.LAML_APPLICATION.wireValue());
        body.put("call_laml_application_id", applicationId);
        return update(id, body);
    }

    /**
     * Route inbound calls to an AI Agent Fabric resource by ID.
     *
     * @param id phone number SID
     * @param agentId AI agent resource ID
     */
    public Map<String, Object> setAiAgent(String id, String agentId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("call_handler", PhoneCallHandler.AI_AGENT.wireValue());
        body.put("call_ai_agent_id", agentId);
        return update(id, body);
    }

    /**
     * Route inbound calls to a Call Flow by ID (server default version).
     *
     * @param id phone number SID
     * @param flowId call-flow resource ID
     */
    public Map<String, Object> setCallFlow(String id, String flowId) {
        return setCallFlow(id, flowId, null);
    }

    /**
     * Route inbound calls to a Call Flow by ID with an explicit version.
     *
     * @param id phone number SID
     * @param flowId call-flow resource ID
     * @param version {@code "working_copy"}, {@code "current_deployed"},
     *                or {@code null} for the server default
     */
    public Map<String, Object> setCallFlow(String id, String flowId, String version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("call_handler", PhoneCallHandler.CALL_FLOW.wireValue());
        body.put("call_flow_id", flowId);
        if (version != null) {
            body.put("call_flow_version", version);
        }
        return update(id, body);
    }

    /**
     * Route inbound calls to a named RELAY application.
     *
     * @param id phone number SID
     * @param name RELAY application name
     */
    public Map<String, Object> setRelayApplication(String id, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("call_handler", PhoneCallHandler.RELAY_APPLICATION.wireValue());
        body.put("call_relay_application", name);
        return update(id, body);
    }

    /**
     * Route inbound calls to a RELAY topic (client subscription).
     *
     * @param id phone number SID
     * @param topic RELAY topic name
     */
    public Map<String, Object> setRelayTopic(String id, String topic) {
        return setRelayTopic(id, topic, null);
    }

    /**
     * Route inbound calls to a RELAY topic with a status-callback URL.
     *
     * @param id phone number SID
     * @param topic RELAY topic name
     * @param statusCallbackUrl call-status callback URL (may be {@code null})
     */
    public Map<String, Object> setRelayTopic(String id, String topic, String statusCallbackUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("call_handler", PhoneCallHandler.RELAY_TOPIC.wireValue());
        body.put("call_relay_topic", topic);
        if (statusCallbackUrl != null) {
            body.put("call_relay_topic_status_callback_url", statusCallbackUrl);
        }
        return update(id, body);
    }
}
