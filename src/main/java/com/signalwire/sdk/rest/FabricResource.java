/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import java.util.Map;

/**
 * Fabric CRUD resource with address listing.
 *
 * <p>Standard CRUD where {@link #update(String, Map)} sends PATCH, plus {@link
 * #listAddresses(String)} → GET {@code {base}/{id}/addresses}. The address-listing route is
 * available on every fabric resource. Fabric resources whose address sub-path is non-standard
 * (singular collection: {@code CallFlows}, {@code ConferenceRooms}) OVERRIDE this with their own
 * generated {@code listAddresses}.
 *
 * <p>The PUT-update variant is {@link FabricResourcePUT}.
 */
public class FabricResource extends CrudResource {

  /**
   * Create a Fabric resource whose update sends PATCH.
   *
   * @param httpClient the HTTP client
   * @param basePath base path for this resource (e.g., {@code /fabric/resources/ai_agents})
   */
  public FabricResource(HttpClient httpClient, String basePath) {
    this(httpClient, basePath, UpdateMethod.PATCH);
  }

  /**
   * Create a Fabric resource with an explicit update verb. Used by {@link FabricResourcePUT}.
   *
   * @param httpClient the HTTP client
   * @param basePath base path for this resource
   * @param updateMethod HTTP verb used by {@link #update(String, Map)}
   */
  protected FabricResource(HttpClient httpClient, String basePath, UpdateMethod updateMethod) {
    super(httpClient, basePath, updateMethod);
  }

  /**
   * List the addresses bound to this resource: GET {@code {base}/{id}/addresses}.
   *
   * <p>Single {@code (id, params)} form (matching the generated per-resource {@code listAddresses}
   * and the reference/Go {@code ListAddresses(id, params)}), so a fabric resource that OVERRIDES
   * this with a non-standard address path ({@code CallFlows}, {@code ConferenceRooms}) fully
   * replaces it — no inherited overload leaks the standard plural path.
   *
   * <p>Returns {@code Object} rather than a fixed {@code Map} so a subclass whose spec declares a
   * NAMED address-list response schema ({@code CallFlows}, {@code ConferenceRooms}, {@code
   * CxmlApplications}) can COVARIANTLY override with its typed {@code *AddressListResponse} DTO
   * (JAVA-1 typed-returns flip). The plain base copy still yields the decoded wire {@code
   * Map<String,Object>} at runtime; callers that want the typed view use the concrete resource's
   * override. (This base method is a PORT_ADDITION — the reference records {@code list_addresses}
   * only on the concrete subclasses.)
   */
  public Object listAddresses(String resourceId, Map<String, String> queryParams) {
    return restGet(getBasePath() + "/" + resourceId + "/addresses", queryParams);
  }

  /**
   * List the addresses bound to this resource with a per-request {@link RequestOptions} override.
   */
  public Object listAddresses(
      String resourceId, Map<String, String> queryParams, RequestOptions requestOptions) {
    return restGet(getBasePath() + "/" + resourceId + "/addresses", queryParams, requestOptions);
  }
}
