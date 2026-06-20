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

  /**
   * Create a CRUD resource.
   *
   * @param httpClient the HTTP client
   * @param basePath base path for this resource (e.g., "/phone_numbers")
   */
  public CrudResource(HttpClient httpClient, String basePath) {
    this.httpClient = httpClient;
    this.basePath = basePath;
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

  /** Update an existing resource by ID. */
  public Map<String, Object> update(String id, Map<String, Object> body) {
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
