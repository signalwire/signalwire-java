/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;

import java.util.Map;

/**
 * REST namespace for SignalWire Fabric resources.
 *
 * <p>Mirrors {@code signalwire.rest.namespaces.fabric.FabricNamespace}: the
 * sub-resources expose typed access to addresses, generic resources,
 * subscribers (with sip-endpoint sub-resource ops), call-flow / conference-room
 * address sub-paths, cxml-applications (read-only), and tokens. The
 * {@code resources} entry-point handles cross-resource list/get/delete plus
 * domain-application assignment.
 */
public class FabricNamespace {

    private final FabricSubscribers subscribers;
    private final FabricAddresses addresses;
    private final GenericResources resources;
    private final CrudResource aiAgents;
    private final CallFlowsResource callFlows;
    private final ConferenceRoomsResource conferenceRooms;
    private final CxmlApplicationsResource cxmlApplications;
    private final CrudResource cxmlScripts;
    private final CrudResource cxmlWebhooks;
    private final CrudResource freeswitchConnectors;
    private final CrudResource relayApplications;
    private final CrudResource sipEndpoints;
    private final CrudResource sipGateways;
    private final CrudResource swmlScripts;
    private final CrudResource swmlWebhooks;
    private final FabricTokens tokens;

    public FabricNamespace(HttpClient httpClient) {
        String resourcesBase = "/fabric/resources";
        this.subscribers = new FabricSubscribers(httpClient, resourcesBase + "/subscribers");
        this.addresses = new FabricAddresses(httpClient, "/fabric/addresses");
        this.resources = new GenericResources(httpClient, resourcesBase);
        this.aiAgents = new CrudResource(httpClient, resourcesBase + "/ai_agents");
        this.callFlows = new CallFlowsResource(httpClient, resourcesBase + "/call_flows");
        this.conferenceRooms = new ConferenceRoomsResource(
                httpClient, resourcesBase + "/conference_rooms");
        this.cxmlApplications = new CxmlApplicationsResource(
                httpClient, resourcesBase + "/cxml_applications");
        this.cxmlScripts = new CrudResource(httpClient, resourcesBase + "/cxml_scripts");
        this.cxmlWebhooks = new CrudResource(httpClient, resourcesBase + "/cxml_webhooks");
        this.freeswitchConnectors = new CrudResource(
                httpClient, resourcesBase + "/freeswitch_connectors");
        this.relayApplications = new CrudResource(
                httpClient, resourcesBase + "/relay_applications");
        this.sipEndpoints = new CrudResource(httpClient, resourcesBase + "/sip_endpoints");
        this.sipGateways = new CrudResource(httpClient, resourcesBase + "/sip_gateways");
        this.swmlScripts = new CrudResource(httpClient, resourcesBase + "/swml_scripts");
        this.swmlWebhooks = new CrudResource(httpClient, resourcesBase + "/swml_webhooks");
        this.tokens = new FabricTokens(httpClient);
    }

    public FabricSubscribers subscribers() { return subscribers; }
    public FabricAddresses addresses() { return addresses; }
    public GenericResources resources() { return resources; }
    public CrudResource aiAgents() { return aiAgents; }
    public CallFlowsResource callFlows() { return callFlows; }
    public ConferenceRoomsResource conferenceRooms() { return conferenceRooms; }
    public CxmlApplicationsResource cxmlApplications() { return cxmlApplications; }
    public CrudResource cxmlScripts() { return cxmlScripts; }
    public CrudResource cxmlWebhooks() { return cxmlWebhooks; }
    public CrudResource freeswitchConnectors() { return freeswitchConnectors; }
    public CrudResource relayApplications() { return relayApplications; }
    public CrudResource sipEndpoints() { return sipEndpoints; }
    public CrudResource sipGateways() { return sipGateways; }
    public CrudResource swmlScripts() { return swmlScripts; }
    public CrudResource swmlWebhooks() { return swmlWebhooks; }
    public FabricTokens tokens() { return tokens; }

    // ────────────────────────────────────────────────────────────────────
    // Sub-resource classes
    // ────────────────────────────────────────────────────────────────────

    /**
     * Read-only fabric addresses collection (top-level).
     */
    public static class FabricAddresses {

        private final HttpClient httpClient;
        private final String basePath;

        public FabricAddresses(HttpClient httpClient, String basePath) {
            this.httpClient = httpClient;
            this.basePath = basePath;
        }

        public String getBasePath() { return basePath; }

        public Map<String, Object> list() {
            return httpClient.get(basePath);
        }

        public Map<String, Object> list(Map<String, String> queryParams) {
            return httpClient.get(basePath, queryParams);
        }

        public Map<String, Object> get(String addressId) {
            return httpClient.get(basePath + "/" + addressId);
        }
    }

    /**
     * Subscribers resource with SIP-endpoint sub-resource. Update uses PUT.
     */
    public static class FabricSubscribers extends CrudResource {

        public FabricSubscribers(HttpClient httpClient, String basePath) {
            super(httpClient, basePath);
        }

        @Override
        public Map<String, Object> update(String resourceId, Map<String, Object> body) {
            return getHttpClient().put(getBasePath() + "/" + resourceId, body);
        }

        public Map<String, Object> listSipEndpoints(String subscriberId) {
            return getHttpClient().get(getBasePath() + "/" + subscriberId + "/sip_endpoints");
        }

        public Map<String, Object> listSipEndpoints(String subscriberId, Map<String, String> queryParams) {
            return getHttpClient().get(
                    getBasePath() + "/" + subscriberId + "/sip_endpoints", queryParams);
        }

        public Map<String, Object> createSipEndpoint(String subscriberId, Map<String, Object> body) {
            return getHttpClient().post(
                    getBasePath() + "/" + subscriberId + "/sip_endpoints", body);
        }

        public Map<String, Object> getSipEndpoint(String subscriberId, String endpointId) {
            return getHttpClient().get(
                    getBasePath() + "/" + subscriberId + "/sip_endpoints/" + endpointId);
        }

        public Map<String, Object> updateSipEndpoint(
                String subscriberId, String endpointId, Map<String, Object> body) {
            return getHttpClient().patch(
                    getBasePath() + "/" + subscriberId + "/sip_endpoints/" + endpointId, body);
        }

        public Map<String, Object> deleteSipEndpoint(String subscriberId, String endpointId) {
            return getHttpClient().delete(
                    getBasePath() + "/" + subscriberId + "/sip_endpoints/" + endpointId);
        }

        public Map<String, Object> listAddresses(String subscriberId) {
            return getHttpClient().get(getBasePath() + "/" + subscriberId + "/addresses");
        }

        public Map<String, Object> listAddresses(String subscriberId, Map<String, String> queryParams) {
            return getHttpClient().get(
                    getBasePath() + "/" + subscriberId + "/addresses", queryParams);
        }
    }

    /**
     * Call flows resource — uses PUT for update and rewrites the path
     * segment to singular {@code call_flow} for sub-collection paths
     * (per the API spec).
     */
    public static class CallFlowsResource extends CrudResource {

        public CallFlowsResource(HttpClient httpClient, String basePath) {
            super(httpClient, basePath);
        }

        @Override
        public Map<String, Object> update(String resourceId, Map<String, Object> body) {
            return getHttpClient().put(getBasePath() + "/" + resourceId, body);
        }

        private String singularBase() {
            return getBasePath().replace("/call_flows", "/call_flow");
        }

        public Map<String, Object> listAddresses(String resourceId) {
            return getHttpClient().get(singularBase() + "/" + resourceId + "/addresses");
        }

        public Map<String, Object> listAddresses(String resourceId, Map<String, String> queryParams) {
            return getHttpClient().get(
                    singularBase() + "/" + resourceId + "/addresses", queryParams);
        }

        public Map<String, Object> listVersions(String resourceId) {
            return getHttpClient().get(singularBase() + "/" + resourceId + "/versions");
        }

        public Map<String, Object> listVersions(String resourceId, Map<String, String> queryParams) {
            return getHttpClient().get(
                    singularBase() + "/" + resourceId + "/versions", queryParams);
        }

        public Map<String, Object> deployVersion(String resourceId, Map<String, Object> body) {
            return getHttpClient().post(singularBase() + "/" + resourceId + "/versions", body);
        }
    }

    /**
     * Conference rooms — singular {@code conference_room} for sub-collections.
     */
    public static class ConferenceRoomsResource extends CrudResource {

        public ConferenceRoomsResource(HttpClient httpClient, String basePath) {
            super(httpClient, basePath);
        }

        @Override
        public Map<String, Object> update(String resourceId, Map<String, Object> body) {
            return getHttpClient().put(getBasePath() + "/" + resourceId, body);
        }

        private String singularBase() {
            return getBasePath().replace("/conference_rooms", "/conference_room");
        }

        public Map<String, Object> listAddresses(String resourceId) {
            return getHttpClient().get(singularBase() + "/" + resourceId + "/addresses");
        }

        public Map<String, Object> listAddresses(String resourceId, Map<String, String> queryParams) {
            return getHttpClient().get(
                    singularBase() + "/" + resourceId + "/addresses", queryParams);
        }
    }

    /**
     * cXML applications — read/update/delete only (no create endpoint exists).
     * Calling {@code create} raises {@link UnsupportedOperationException}.
     */
    public static class CxmlApplicationsResource extends CrudResource {

        public CxmlApplicationsResource(HttpClient httpClient, String basePath) {
            super(httpClient, basePath);
        }

        @Override
        public Map<String, Object> create(Map<String, Object> body) {
            throw new UnsupportedOperationException(
                    "cXML applications cannot be created via this API");
        }

        @Override
        public Map<String, Object> update(String resourceId, Map<String, Object> body) {
            return getHttpClient().put(getBasePath() + "/" + resourceId, body);
        }
    }

    /**
     * Generic operations against {@code /api/fabric/resources}: list, get,
     * delete, plus address listing and domain-application assignment.
     */
    public static class GenericResources {

        private final HttpClient httpClient;
        private final String basePath;

        public GenericResources(HttpClient httpClient, String basePath) {
            this.httpClient = httpClient;
            this.basePath = basePath;
        }

        public String getBasePath() { return basePath; }

        public Map<String, Object> list() {
            return httpClient.get(basePath);
        }

        public Map<String, Object> list(Map<String, String> queryParams) {
            return httpClient.get(basePath, queryParams);
        }

        public Map<String, Object> get(String resourceId) {
            return httpClient.get(basePath + "/" + resourceId);
        }

        public Map<String, Object> delete(String resourceId) {
            return httpClient.delete(basePath + "/" + resourceId);
        }

        public Map<String, Object> listAddresses(String resourceId) {
            return httpClient.get(basePath + "/" + resourceId + "/addresses");
        }

        public Map<String, Object> listAddresses(String resourceId, Map<String, String> queryParams) {
            return httpClient.get(basePath + "/" + resourceId + "/addresses", queryParams);
        }

        public Map<String, Object> assignDomainApplication(
                String resourceId, Map<String, Object> body) {
            return httpClient.post(basePath + "/" + resourceId + "/domain_applications", body);
        }
    }

    /**
     * Subscriber, guest, invite, and embed token endpoints (all POST).
     */
    public static class FabricTokens {

        private final HttpClient httpClient;

        public FabricTokens(HttpClient httpClient) {
            this.httpClient = httpClient;
        }

        public Map<String, Object> createSubscriberToken(Map<String, Object> body) {
            return httpClient.post("/fabric/subscribers/tokens", body);
        }

        public Map<String, Object> refreshSubscriberToken(Map<String, Object> body) {
            return httpClient.post("/fabric/subscribers/tokens/refresh", body);
        }

        public Map<String, Object> createInviteToken(Map<String, Object> body) {
            return httpClient.post("/fabric/subscriber/invites", body);
        }

        public Map<String, Object> createGuestToken(Map<String, Object> body) {
            return httpClient.post("/fabric/guests/tokens", body);
        }

        public Map<String, Object> createEmbedToken(Map<String, Object> body) {
            return httpClient.post("/fabric/embeds/tokens", body);
        }
    }
}
