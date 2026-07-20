/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import java.util.Map;

/**
 * Root of the generated REST resource hierarchy.
 *
 * <p>Holds the {@link HttpClient} and this resource's {@code basePath}, and exposes the low-level
 * verb receivers ({@code restGet}/{@code restPost}/{@code restPut}/{@code restPatch}/{@code
 * restDelete}) the generated resource classes (in {@code
 * com.signalwire.sdk.rest.namespaces.generated}) call to build their declared/command/set methods.
 * These receivers are {@code protected} so a generated subclass in a different package can reach
 * them (Java protected access spans packages for subclasses). They carry a {@code rest} prefix so a
 * generated resource that declares its OWN {@code get(id, params)} / {@code delete(id)} (or a CRUD
 * subclass that inherits a public {@code delete(id)}) neither recurses into itself nor shadows the
 * raw receiver — the semantic CRUD verbs and the raw path receivers are distinct methods.
 *
 * <p>Mirrors Python's {@code signalwire.rest.namespaces._base.BaseResource}: a base that carries
 * the HTTP plumbing and base path, over which the CRUD / read / fabric bases and the per-resource
 * classes are layered. The base contributes no public route surface of its own — every route is
 * declared by the concrete resource (explicitly, for {@code BaseResource} subclasses) or by the
 * CRUD / read / fabric bases below.
 */
public class BaseResource {

  private final HttpClient httpClient;
  private final String basePath;

  /**
   * @param httpClient the HTTP client
   * @param basePath base path for this resource, relative to the client's {@code /api} base (e.g.
   *     {@code /fabric/resources/ai_agents})
   */
  public BaseResource(HttpClient httpClient, String basePath) {
    this.httpClient = httpClient;
    this.basePath = basePath;
  }

  /** GET {@code path} with query parameters. */
  protected Map<String, Object> restGet(String path, Map<String, String> params) {
    return httpClient.get(path, params);
  }

  /** GET {@code path} with query parameters and a per-request {@link RequestOptions} override. */
  protected Map<String, Object> restGet(
      String path, Map<String, String> params, RequestOptions requestOptions) {
    return httpClient.get(path, params, requestOptions);
  }

  /** POST {@code path} with a JSON body. */
  protected Map<String, Object> restPost(String path, Map<String, Object> body) {
    return httpClient.post(path, body);
  }

  /** POST {@code path} with a JSON body and a per-request {@link RequestOptions} override. */
  protected Map<String, Object> restPost(
      String path, Map<String, Object> body, RequestOptions requestOptions) {
    return httpClient.post(path, body, requestOptions);
  }

  /** PUT {@code path} with a JSON body. */
  protected Map<String, Object> restPut(String path, Map<String, Object> body) {
    return httpClient.put(path, body);
  }

  /** PUT {@code path} with a JSON body and a per-request {@link RequestOptions} override. */
  protected Map<String, Object> restPut(
      String path, Map<String, Object> body, RequestOptions requestOptions) {
    return httpClient.put(path, body, requestOptions);
  }

  /** PATCH {@code path} with a JSON body. */
  protected Map<String, Object> restPatch(String path, Map<String, Object> body) {
    return httpClient.patch(path, body);
  }

  /** PATCH {@code path} with a JSON body and a per-request {@link RequestOptions} override. */
  protected Map<String, Object> restPatch(
      String path, Map<String, Object> body, RequestOptions requestOptions) {
    return httpClient.patch(path, body, requestOptions);
  }

  /** DELETE {@code path}. */
  protected Map<String, Object> restDelete(String path) {
    return httpClient.delete(path);
  }

  /** DELETE {@code path} with a per-request {@link RequestOptions} override. */
  protected Map<String, Object> restDelete(String path, RequestOptions requestOptions) {
    return httpClient.delete(path, requestOptions);
  }

  /** The base path for this resource. */
  public String getBasePath() {
    return basePath;
  }

  /** The underlying HTTP client. */
  public HttpClient getHttpClient() {
    return httpClient;
  }
}
