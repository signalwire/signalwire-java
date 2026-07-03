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
}
