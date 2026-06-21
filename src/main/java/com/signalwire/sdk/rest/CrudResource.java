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
 * <p>Provides standard list, get, create, update, and delete operations against a base path. Used
 * by namespace classes to avoid repetitive HTTP boilerplate.
 *
 * <pre>{@code
 * var numbers = new CrudResource(httpClient, "/phone_numbers");
 * var all = numbers.list();
 * var one = numbers.get("pn-abc-123");
 * }</pre>
 */
public class CrudResource {

  private final HttpClient httpClient;
  private final String basePath;
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
    this.httpClient = httpClient;
    this.basePath = basePath;
    this.updateMethod = updateMethod;
  }

  /** List all resources. */
  public Map<String, Object> list() {
    return httpClient.get(basePath);
  }

  /** List resources with query parameters (e.g., pagination, filters). */
  public Map<String, Object> list(Map<String, String> queryParams) {
    return httpClient.get(basePath, queryParams);
  }

  /** Get a single resource by ID. */
  public Map<String, Object> get(String id) {
    return httpClient.get(basePath + "/" + id);
  }

  /** Create a new resource. */
  public Map<String, Object> create(Map<String, Object> body) {
    return httpClient.post(basePath, body);
  }

  /** Update an existing resource by ID, using this resource's configured verb (PUT or PATCH). */
  public Map<String, Object> update(String id, Map<String, Object> body) {
    if (updateMethod == UpdateMethod.PATCH) {
      return httpClient.patch(basePath + "/" + id, body);
    }
    return httpClient.put(basePath + "/" + id, body);
  }

  /** Delete a resource by ID. */
  public Map<String, Object> delete(String id) {
    return httpClient.delete(basePath + "/" + id);
  }

  /** Get the base path for this resource. */
  public String getBasePath() {
    return basePath;
  }

  /** Get the underlying HTTP client. */
  public HttpClient getHttpClient() {
    return httpClient;
  }
}
