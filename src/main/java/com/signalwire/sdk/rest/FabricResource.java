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
 * <p>Mirrors Python's {@code signalwire.rest.namespaces.fabric.FabricResource} (which extends
 * {@code CrudWithAddresses}): standard CRUD where {@link #update(String, Map)} sends PATCH, plus
 * {@link #listAddresses(String)} → GET {@code {base}/{id}/addresses}.
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

  /** List the addresses bound to this resource: GET {@code {base}/{id}/addresses}. */
  public Map<String, Object> listAddresses(String resourceId) {
    return getHttpClient().get(getBasePath() + "/" + resourceId + "/addresses");
  }

  /** List addresses with query parameters. */
  public Map<String, Object> listAddresses(String resourceId, Map<String, String> queryParams) {
    return getHttpClient().get(getBasePath() + "/" + resourceId + "/addresses", queryParams);
  }
}
