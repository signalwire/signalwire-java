/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import com.google.gson.Gson;
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

  /**
   * Shared JSON codec used to project a decoded wire {@code Map<String,Object>} onto a generated
   * typed response DTO. The generated resource methods return the closed spec-typed {@code
   * *Response} DTOs (JAVA-1 typed-returns flip); each still hits the {@code rest*} verb receiver
   * (which decodes the body to a {@code Map}) and then re-projects that Map onto the DTO here.
   * Mirrors the Python reference, whose typed methods {@code cast(...)} the same runtime dict to
   * the response type — a static view over the wire JSON, never a validating parse.
   */
  private static final Gson RESPONSE_GSON = new Gson();

  private final HttpClient httpClient;
  private final String basePath;

  /**
   * Project a decoded wire {@code Map} onto a generated response DTO of type {@code T}. A {@code
   * null} raw (e.g. a bodyless DELETE) yields {@code null}. Unknown wire keys are ignored and
   * absent DTO fields stay {@code null} — the same lenient, non-validating view the Python
   * reference's {@code cast()} gives (the server response shape is never asserted at the SDK
   * boundary).
   */
  protected static <T> T asType(Map<String, Object> raw, Class<T> type) {
    if (raw == null) {
      return null;
    }
    return RESPONSE_GSON.fromJson(RESPONSE_GSON.toJsonTree(raw), type);
  }

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
