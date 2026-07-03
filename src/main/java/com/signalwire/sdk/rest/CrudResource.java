/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import java.util.Map;

/**
 * Generic CRUD resource for REST API namespaces.
 *
 * <p>Provides standard list, get, create, update, and delete operations against a base path. Serves
 * as the base for the generated per-resource classes in {@code
 * com.signalwire.sdk.rest.namespaces.generated} and as a standalone helper.
 *
 * <p>Extends {@link BaseResource} for the HTTP plumbing + the low-level {@code
 * restGet/restPost/restPut/restPatch/restDelete} verb receivers the generated subclasses call;
 * layers the public CRUD surface on top. The public {@code delete(String id)} and the raw {@code
 * restDelete(String path)} receiver are DISTINCT methods (different names), so a generated
 * subclass's declared sub-resource delete (which calls {@code restDelete(fullPath)}) is never
 * shadowed by the basePath-prepending {@code delete(id)}.
 *
 * <pre>{@code
 * var numbers = new CrudResource(httpClient, "/phone_numbers");
 * var all = numbers.list();
 * var one = numbers.get("pn-abc-123");
 * }</pre>
 */
public class CrudResource extends BaseResource {

  private final UpdateMethod updateMethod;

  /** HTTP verb a CRUD resource uses for {@link #update(String, Map)}. */
  protected enum UpdateMethod {
    PUT,
    PATCH
  }

  /**
   * Create a CRUD resource whose {@link #update(String, Map)} uses PUT.
   *
   * @param httpClient the HTTP client
   * @param basePath base path for this resource (e.g., "/phone_numbers")
   */
  public CrudResource(HttpClient httpClient, String basePath) {
    this(httpClient, basePath, UpdateMethod.PUT);
  }

  /**
   * Create a CRUD resource with an explicit update verb. Mirrors Python's {@code
   * CrudResource._update_method} class attribute: the base default here is PUT (preserving the
   * historical Java behavior of every namespace), and subclasses that map onto a PATCH route (e.g.
   * Fabric resources, Datasphere documents) opt in via {@link UpdateMethod#PATCH}.
   *
   * @param httpClient the HTTP client
   * @param basePath base path for this resource
   * @param updateMethod HTTP verb used by {@link #update(String, Map)}
   */
  protected CrudResource(HttpClient httpClient, String basePath, UpdateMethod updateMethod) {
    super(httpClient, basePath);
    this.updateMethod = updateMethod;
  }

  /** List all resources. */
  public Map<String, Object> list() {
    return restGet(getBasePath(), null);
  }

  /** List resources with query parameters (e.g., pagination, filters). */
  public Map<String, Object> list(Map<String, String> queryParams) {
    return restGet(getBasePath(), queryParams);
  }

  /** Get a single resource by ID. */
  public Map<String, Object> get(String id) {
    return restGet(getBasePath() + "/" + id, null);
  }

  /** Create a new resource. */
  public Map<String, Object> create(Map<String, Object> body) {
    return restPost(getBasePath(), body);
  }

  /** Update an existing resource by ID, using this resource's configured verb (PUT or PATCH). */
  public Map<String, Object> update(String id, Map<String, Object> body) {
    if (updateMethod == UpdateMethod.PATCH) {
      return restPatch(getBasePath() + "/" + id, body);
    }
    return restPut(getBasePath() + "/" + id, body);
  }

  /** Delete a resource by ID. */
  public Map<String, Object> delete(String id) {
    return restDelete(getBasePath() + "/" + id);
  }
}
