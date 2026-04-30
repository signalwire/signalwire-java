/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.CrudResource;
import com.signalwire.sdk.rest.HttpClient;

/**
 * REST namespace for SignalWire Fabric resources.
 */
public class FabricNamespace {

    private final CrudResource subscribers;
    private final CrudResource addresses;
    private final CrudResource resources;
    // Python parity: signalwire/rest/namespaces/fabric.py::FabricNamespace
    private final CrudResource aiAgents;
    private final CrudResource callFlows;
    private final CrudResource conferenceRooms;
    private final CrudResource cxmlApplications;
    private final CrudResource cxmlScripts;
    private final CrudResource cxmlWebhooks;
    private final CrudResource freeswitchConnectors;
    private final CrudResource relayApplications;
    private final CrudResource sipEndpoints;
    private final CrudResource sipGateways;
    private final CrudResource swmlScripts;
    private final CrudResource swmlWebhooks;
    private final CrudResource tokens;

    public FabricNamespace(HttpClient httpClient) {
        this.subscribers = new CrudResource(httpClient, "/fabric/subscribers");
        this.addresses = new CrudResource(httpClient, "/fabric/addresses");
        this.resources = new CrudResource(httpClient, "/fabric/resources");
        this.aiAgents = new CrudResource(httpClient, "/fabric/ai_agents");
        this.callFlows = new CrudResource(httpClient, "/fabric/call_flows");
        this.conferenceRooms = new CrudResource(httpClient, "/fabric/conference_rooms");
        this.cxmlApplications = new CrudResource(httpClient, "/fabric/cxml_applications");
        this.cxmlScripts = new CrudResource(httpClient, "/fabric/cxml_scripts");
        this.cxmlWebhooks = new CrudResource(httpClient, "/fabric/cxml_webhooks");
        this.freeswitchConnectors = new CrudResource(httpClient, "/fabric/freeswitch_connectors");
        this.relayApplications = new CrudResource(httpClient, "/fabric/relay_applications");
        this.sipEndpoints = new CrudResource(httpClient, "/fabric/sip_endpoints");
        this.sipGateways = new CrudResource(httpClient, "/fabric/sip_gateways");
        this.swmlScripts = new CrudResource(httpClient, "/fabric/swml_scripts");
        this.swmlWebhooks = new CrudResource(httpClient, "/fabric/swml_webhooks");
        this.tokens = new CrudResource(httpClient, "/fabric/tokens");
    }

    public CrudResource subscribers() { return subscribers; }
    public CrudResource addresses() { return addresses; }
    public CrudResource resources() { return resources; }
    public CrudResource aiAgents() { return aiAgents; }
    public CrudResource callFlows() { return callFlows; }
    public CrudResource conferenceRooms() { return conferenceRooms; }
    public CrudResource cxmlApplications() { return cxmlApplications; }
    public CrudResource cxmlScripts() { return cxmlScripts; }
    public CrudResource cxmlWebhooks() { return cxmlWebhooks; }
    public CrudResource freeswitchConnectors() { return freeswitchConnectors; }
    public CrudResource relayApplications() { return relayApplications; }
    public CrudResource sipEndpoints() { return sipEndpoints; }
    public CrudResource sipGateways() { return sipGateways; }
    public CrudResource swmlScripts() { return swmlScripts; }
    public CrudResource swmlWebhooks() { return swmlWebhooks; }
    public CrudResource tokens() { return tokens; }
}
