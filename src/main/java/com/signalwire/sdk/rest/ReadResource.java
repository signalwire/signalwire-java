/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import java.util.Map;

/**
 * Read-only REST resource: {@code list} + {@code get}, no write verbs.
 *
 * <p>Mirrors Python's {@code signalwire.rest.namespaces._base.ReadResource}. Used by log/read
 * resources (fax logs, message logs, voice logs, conference logs, video room sessions, fabric
 * addresses) whose canonical surface is list + get only.
 */
public class ReadResource extends BaseResource {

  /**
   * @param httpClient the HTTP client
   * @param basePath base path for this resource
   */
  public ReadResource(HttpClient httpClient, String basePath) {
    super(httpClient, basePath);
  }

  /** List all resources. */
  public Map<String, Object> list() {
    return restGet(getBasePath(), null);
  }

  /** List resources with query parameters. */
  public Map<String, Object> list(Map<String, String> queryParams) {
    return restGet(getBasePath(), queryParams);
  }

  /** Get a single resource by ID. */
  public Map<String, Object> get(String id) {
    return restGet(getBasePath() + "/" + id, null);
  }

  /**
   * Iterate every item across all pages of this resource's list endpoint.
   *
   * <p>Mirrors Python's {@code ReadResource.paginate(**params)}. Where {@link #list()} returns a
   * single raw page (the server's first response), {@code paginate()} returns a {@link
   * PaginatedIterator} that follows the {@code links.next} cursor and yields each item across all
   * pages, so callers no longer hand-build the path + token loop:
   *
   * <pre>{@code
   * for (Map<String, Object> address : client.fabric().addresses().paginate()) {
   *     // ...
   * }
   * }</pre>
   *
   * @return an {@link Iterable}/{@link java.util.Iterator} over every item in the list endpoint
   */
  public PaginatedIterator paginate() {
    return paginate(null);
  }

  /**
   * Iterate every item across all pages, seeding the first request with {@code queryParams}.
   *
   * @param queryParams initial query parameters for the first page (may be {@code null})
   * @return an {@link Iterable}/{@link java.util.Iterator} over every item in the list endpoint
   */
  public PaginatedIterator paginate(Map<String, String> queryParams) {
    return new PaginatedIterator(getHttpClient(), getBasePath(), queryParams, "data");
  }
}
