/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest.namespaces;

import com.signalwire.sdk.rest.FabricResource;
import com.signalwire.sdk.rest.FabricResourcePUT;
import com.signalwire.sdk.rest.HttpClient;
import java.util.Map;

/**
 * REST namespace for SignalWire Fabric resources.
 *
 * <p>Mirrors {@code signalwire.rest.namespaces.fabric.FabricNamespace}: the sub-resources expose
 * typed access to addresses, generic resources, subscribers (with sip-endpoint sub-resource ops),
 * call-flow / conference-room address sub-paths, cxml-applications (read-only), and tokens. The
 * {@code resources} entry-point handles cross-resource list/get/delete plus domain-application
 * assignment.
 */
public class FabricNamespace {

  private final FabricSubscribers subscribers;
  private final FabricAddresses addresses;
  private final GenericResources resources;
  private final FabricResource aiAgents;
  private final CallFlowsResource callFlows;
  private final ConferenceRoomsResource conferenceRooms;
  private final CxmlApplicationsResource cxmlApplications;
  private final FabricResourcePUT cxmlScripts;
  private final FabricResource cxmlWebhooks;
  private final FabricResourcePUT freeswitchConnectors;
  private final FabricResourcePUT relayApplications;
  private final FabricResourcePUT sipEndpoints;
  private final FabricResource sipGateways;
  private final FabricResourcePUT swmlScripts;
  private final FabricResource swmlWebhooks;
  private final FabricTokens tokens;

  public FabricNamespace(HttpClient httpClient) {
    String resourcesBase = "/fabric/resources";
    this.subscribers = new FabricSubscribers(httpClient, resourcesBase + "/subscribers");
    this.addresses = new FabricAddresses(httpClient, "/fabric/addresses");
    this.resources = new GenericResources(httpClient, resourcesBase);
    // PATCH-update resources (FabricResource): ai_agents, sip_gateways, and the
    // auto-materialized webhooks. sip_gateways inherits list_addresses on the
    // standard /{id}/addresses path; per Python this route is a doubled-path
    // spec artifact and not reachable in practice, but the method is present
    // for parity (matching Python's plain FabricResource).
    this.aiAgents = new FabricResource(httpClient, resourcesBase + "/ai_agents");
    this.sipGateways = new FabricResource(httpClient, resourcesBase + "/sip_gateways");
    this.cxmlWebhooks = new FabricResource(httpClient, resourcesBase + "/cxml_webhooks");
    this.swmlWebhooks = new FabricResource(httpClient, resourcesBase + "/swml_webhooks");
    // PUT-update resources (FabricResourcePUT).
    this.cxmlScripts = new FabricResourcePUT(httpClient, resourcesBase + "/cxml_scripts");
    this.freeswitchConnectors =
        new FabricResourcePUT(httpClient, resourcesBase + "/freeswitch_connectors");
    this.relayApplications =
        new FabricResourcePUT(httpClient, resourcesBase + "/relay_applications");
    this.sipEndpoints = new FabricResourcePUT(httpClient, resourcesBase + "/sip_endpoints");
    this.swmlScripts = new FabricResourcePUT(httpClient, resourcesBase + "/swml_scripts");
    // PUT-update resources with singular sub-resource paths / sip-endpoint ops.
    this.callFlows = new CallFlowsResource(httpClient, resourcesBase + "/call_flows");
    this.conferenceRooms =
        new ConferenceRoomsResource(httpClient, resourcesBase + "/conference_rooms");
    this.cxmlApplications =
        new CxmlApplicationsResource(httpClient, resourcesBase + "/cxml_applications");
    this.tokens = new FabricTokens(httpClient);
  }

  public FabricSubscribers subscribers() {
    return subscribers;
  }

  public FabricAddresses addresses() {
    return addresses;
  }

  public GenericResources resources() {
    return resources;
  }

  public FabricResource aiAgents() {
    return aiAgents;
  }

  public CallFlowsResource callFlows() {
    return callFlows;
  }

  public ConferenceRoomsResource conferenceRooms() {
    return conferenceRooms;
  }

  public CxmlApplicationsResource cxmlApplications() {
    return cxmlApplications;
  }

  public FabricResourcePUT cxmlScripts() {
    return cxmlScripts;
  }

  public FabricResource cxmlWebhooks() {
    return cxmlWebhooks;
  }

  public FabricResourcePUT freeswitchConnectors() {
    return freeswitchConnectors;
  }

  public FabricResourcePUT relayApplications() {
    return relayApplications;
  }

  public FabricResourcePUT sipEndpoints() {
    return sipEndpoints;
  }

  public FabricResource sipGateways() {
    return sipGateways;
  }

  public FabricResourcePUT swmlScripts() {
    return swmlScripts;
  }

  public FabricResource swmlWebhooks() {
    return swmlWebhooks;
  }

  public FabricTokens tokens() {
    return tokens;
  }

  // ────────────────────────────────────────────────────────────────────
  // Sub-resource classes
  // ────────────────────────────────────────────────────────────────────

  /** Read-only fabric addresses collection (top-level). */
  public static class FabricAddresses {

    private final HttpClient httpClient;
    private final String basePath;

    public FabricAddresses(HttpClient httpClient, String basePath) {
      this.httpClient = httpClient;
      this.basePath = basePath;
    }

    public String getBasePath() {
      return basePath;
    }

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
   * Subscribers resource with SIP-endpoint sub-resource. Update uses PUT; inherits {@code
   * listAddresses} from {@link FabricResourcePUT}.
   */
  public static class FabricSubscribers extends FabricResourcePUT {

    public FabricSubscribers(HttpClient httpClient, String basePath) {
      super(httpClient, basePath);
    }

    public Map<String, Object> listSipEndpoints(String subscriberId) {
      return getHttpClient().get(getBasePath() + "/" + subscriberId + "/sip_endpoints");
    }

    public Map<String, Object> listSipEndpoints(
        String subscriberId, Map<String, String> queryParams) {
      return getHttpClient()
          .get(getBasePath() + "/" + subscriberId + "/sip_endpoints", queryParams);
    }

    public Map<String, Object> createSipEndpoint(String subscriberId, Map<String, Object> body) {
      return getHttpClient().post(getBasePath() + "/" + subscriberId + "/sip_endpoints", body);
    }

    public Map<String, Object> getSipEndpoint(String subscriberId, String endpointId) {
      return getHttpClient()
          .get(getBasePath() + "/" + subscriberId + "/sip_endpoints/" + endpointId);
    }

    public Map<String, Object> updateSipEndpoint(
        String subscriberId, String endpointId, Map<String, Object> body) {
      return getHttpClient()
          .patch(getBasePath() + "/" + subscriberId + "/sip_endpoints/" + endpointId, body);
    }

    public Map<String, Object> deleteSipEndpoint(String subscriberId, String endpointId) {
      return getHttpClient()
          .delete(getBasePath() + "/" + subscriberId + "/sip_endpoints/" + endpointId);
    }
  }

  /**
   * Call flows resource — uses PUT for update and rewrites the path segment to singular {@code
   * call_flow} for sub-collection paths (per the API spec).
   */
  public static class CallFlowsResource extends FabricResourcePUT {

    public CallFlowsResource(HttpClient httpClient, String basePath) {
      super(httpClient, basePath);
    }

    private String singularBase() {
      return getBasePath().replace("/call_flows", "/call_flow");
    }

    @Override
    public Map<String, Object> listAddresses(String resourceId) {
      return getHttpClient().get(singularBase() + "/" + resourceId + "/addresses");
    }

    @Override
    public Map<String, Object> listAddresses(String resourceId, Map<String, String> queryParams) {
      return getHttpClient().get(singularBase() + "/" + resourceId + "/addresses", queryParams);
    }

    public Map<String, Object> listVersions(String resourceId) {
      return getHttpClient().get(singularBase() + "/" + resourceId + "/versions");
    }

    public Map<String, Object> listVersions(String resourceId, Map<String, String> queryParams) {
      return getHttpClient().get(singularBase() + "/" + resourceId + "/versions", queryParams);
    }

    public Map<String, Object> deployVersion(String resourceId, Map<String, Object> body) {
      return getHttpClient().post(singularBase() + "/" + resourceId + "/versions", body);
    }
  }

  /** Conference rooms — singular {@code conference_room} for sub-collections. */
  public static class ConferenceRoomsResource extends FabricResourcePUT {

    public ConferenceRoomsResource(HttpClient httpClient, String basePath) {
      super(httpClient, basePath);
    }

    private String singularBase() {
      return getBasePath().replace("/conference_rooms", "/conference_room");
    }

    @Override
    public Map<String, Object> listAddresses(String resourceId) {
      return getHttpClient().get(singularBase() + "/" + resourceId + "/addresses");
    }

    @Override
    public Map<String, Object> listAddresses(String resourceId, Map<String, String> queryParams) {
      return getHttpClient().get(singularBase() + "/" + resourceId + "/addresses", queryParams);
    }
  }

  /**
   * cXML applications — read/update/delete only (no create endpoint exists). Calling {@code create}
   * raises {@link UnsupportedOperationException}.
   */
  public static class CxmlApplicationsResource extends FabricResourcePUT {

    public CxmlApplicationsResource(HttpClient httpClient, String basePath) {
      super(httpClient, basePath);
    }

    @Override
    public Map<String, Object> create(Map<String, Object> body) {
      throw new UnsupportedOperationException("cXML applications cannot be created via this API");
    }
  }

  /**
   * Generic operations against {@code /api/fabric/resources}: list, get, delete, plus address
   * listing and domain-application assignment.
   */
  public static class GenericResources {

    private final HttpClient httpClient;
    private final String basePath;

    public GenericResources(HttpClient httpClient, String basePath) {
      this.httpClient = httpClient;
      this.basePath = basePath;
    }

    public String getBasePath() {
      return basePath;
    }

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

    /**
     * Assign a phone route to a resource ({@code POST /api/fabric/resources/{id}/phone_routes}).
     *
     * <p><b>Deprecated for the common binding cases.</b> This endpoint accepts only a narrow set of
     * legacy resource types as the attach target. It does <em>not</em> work for {@code
     * swml_webhook} / {@code cxml_webhook} / {@code ai_agent} bindings — those are configured on
     * the phone number (see {@code phoneNumbers().setSwmlWebhook} / {@code setCxmlWebhook}) and the
     * Fabric resource is auto-materialized. See porting-sdk's phone-binding.md. Mirrors Python's
     * {@code GenericResources.assign_phone_route}, which emits a {@code DeprecationWarning}.
     */
    @Deprecated
    public Map<String, Object> assignPhoneRoute(String resourceId, Map<String, Object> body) {
      return httpClient.post(basePath + "/" + resourceId + "/phone_routes", body);
    }
  }

  /** Subscriber, guest, invite, and embed token endpoints (all POST). */
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
