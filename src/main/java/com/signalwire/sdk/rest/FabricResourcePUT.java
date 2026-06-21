/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

/**
 * Fabric CRUD resource (with address listing) whose {@link #update(String, java.util.Map)} sends
 * PUT instead of PATCH.
 *
 * <p>Mirrors Python's {@code signalwire.rest.namespaces.fabric.FabricResourcePUT} (which overrides
 * {@code _update_method = "PUT"}). Used for resources whose canonical OpenAPI update route is PUT:
 * cxml_scripts, freeswitch_connectors, relay_applications, sip_endpoints, swml_scripts.
 */
public class FabricResourcePUT extends FabricResource {

  /**
   * Create a Fabric resource whose update sends PUT.
   *
   * @param httpClient the HTTP client
   * @param basePath base path for this resource
   */
  public FabricResourcePUT(HttpClient httpClient, String basePath) {
    super(httpClient, basePath, UpdateMethod.PUT);
  }
}
