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

  /** List resources with query parameters and a per-request {@link RequestOptions} override. */
  public Map<String, Object> list(Map<String, String> queryParams, RequestOptions requestOptions) {
    return restGet(getBasePath(), queryParams, requestOptions);
  }

  /** Get a single resource by ID. */
  public Map<String, Object> get(String id) {
    return restGet(getBasePath() + "/" + pathSegment(id), null);
  }

  /** Get a single resource by ID with a per-request {@link RequestOptions} override. */
  public Map<String, Object> get(String id, RequestOptions requestOptions) {
    return restGet(getBasePath() + "/" + pathSegment(id), null, requestOptions);
  }

  /** Create a new resource. */
  public Map<String, Object> create(Map<String, Object> body) {
    return restPost(getBasePath(), body);
  }

  /** Create a new resource with a per-request {@link RequestOptions} override. */
  public Map<String, Object> create(Map<String, Object> body, RequestOptions requestOptions) {
    return restPost(getBasePath(), body, requestOptions);
  }

  /** Update an existing resource by ID, using this resource's configured verb (PUT or PATCH). */
  public Map<String, Object> update(String id, Map<String, Object> body) {
    return update(id, body, null);
  }

  /**
   * Update an existing resource by ID, using this resource's configured verb (PUT or PATCH), with a
   * per-request {@link RequestOptions} override.
   */
  public Map<String, Object> update(
      String id, Map<String, Object> body, RequestOptions requestOptions) {
    if (updateMethod == UpdateMethod.PATCH) {
      return restPatch(getBasePath() + "/" + pathSegment(id), body, requestOptions);
    }
    return restPut(getBasePath() + "/" + pathSegment(id), body, requestOptions);
  }

  /** Delete a resource by ID. */
  public Map<String, Object> delete(String id) {
    return restDelete(getBasePath() + "/" + pathSegment(id));
  }

  /** Delete a resource by ID with a per-request {@link RequestOptions} override. */
  public Map<String, Object> delete(String id, RequestOptions requestOptions) {
    return restDelete(getBasePath() + "/" + pathSegment(id), requestOptions);
  }

  /**
   * Percent-encode a resource id for safe use as a single URL PATH segment (NB-5). A raw id
   * containing a space / {@code #} / non-ASCII would make {@code URI.create} throw, and one
   * containing {@code /} or {@code ?} would silently reroute the request; encoding the segment
   * matches how the Python reference (requests) treats path input. {@code /} and other reserved
   * characters are encoded so the id can never escape its segment; {@code null} is left as the
   * literal path (a missing-id error surfaces from the server, not a client-side NPE).
   */
  static String pathSegment(String id) {
    if (id == null) {
      return "";
    }
    // URLEncoder targets application/x-www-form-urlencoded (space -> "+", and it does not encode
    // "*"), which is wrong for a path segment. Post-process to RFC-3986 path-segment semantics:
    // "+" -> "%20", and encode "*" -> "%2A". "~" is unreserved; URLEncoder leaves it as-is.
    String enc = java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8);
    return enc.replace("+", "%20").replace("*", "%2A");
  }
}
