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
 * <p>Mirrors Python's {@code signalwire.rest.namespaces._base.FabricResource} (the reference's
 * {@code CrudWithAddresses}): standard CRUD where {@link #update(String, Map)} sends PATCH, plus
 * {@link #listAddresses(String)} → GET {@code {base}/{id}/addresses}. The {@code list_addresses}
 * route is defined by the spec for EVERY fabric resource and is present in the reference's runtime
 * route registry (and every other port's fabric base — e.g. Go's {@code CrudWithAddresses}); it is
 * an ORACLE BLIND SPOT in {@code python_signatures.json} (which records only create/update on the
 * subclass because the enumerator drops the inherited base method), reconciled per changeset L12.
 * Fabric resources whose address sub-path is non-standard (singular collection: {@code CallFlows},
 * {@code ConferenceRooms}) OVERRIDE this with their own generated {@code listAddresses}.
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
   */
  public Map<String, Object> listAddresses(String resourceId, Map<String, String> queryParams) {
    return restGet(getBasePath() + "/" + resourceId + "/addresses", queryParams);
  }
}
