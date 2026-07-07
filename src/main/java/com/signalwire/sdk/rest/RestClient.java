/*
 * Copyright (c) 2025 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.rest;

import com.signalwire.sdk.rest.namespaces.generated.ResourceTree;
import java.util.Objects;

/**
 * SignalWire REST API client.
 *
 * <p>Uses {@code java.net.http.HttpClient} with Basic Auth. Extends the generated {@link
 * ResourceTree} (in {@code com.signalwire.sdk.rest.namespaces.generated}) which supplies every REST
 * resource and namespace-container accessor. This client adds the auth / HTTP construction and the
 * {@link Builder}.
 *
 * <pre>{@code
 * var client = RestClient.builder()
 *     .project("project-id")
 *     .token("api-token")
 *     .space("example.signalwire.com")
 *     .build();
 *
 * var numbers = client.phoneNumbers().list();
 * var docs = client.datasphere().documents().list();
 * }</pre>
 */
public class RestClient extends ResourceTree {

  private final String project;
  private final String space;
  private final HttpClient httpClient;

  private RestClient(Builder builder) {
    this.project = builder.project;
    this.space = builder.space;
    this.httpClient =
        builder.httpClient != null
            ? builder.httpClient
            : new HttpClient(builder.space, builder.project, builder.token);
  }

  /** Supplies the HttpClient to the generated {@link ResourceTree} accessors. */
  @Override
  protected HttpClient generatedHttpClient() {
    return httpClient;
  }

  /**
   * Build a {@link RestClient} pointed at an explicit base URL — typically a loopback fixture used
   * for testing. The returned client signs requests with the given {@code project}/{@code token}
   * pair via Basic Auth and routes every namespace's HTTP through the fixture instead of the live
   * SignalWire space.
   *
   * @param baseUrl fully qualified base URL (e.g. {@code "http://127.0.0.1:NNNN/api"}); {@code
   *     "/api"} is appended if not already present
   * @param project project ID used as the Basic Auth username
   * @param token API token used as the Basic Auth password
   */
  public static RestClient withBaseUrl(String baseUrl, String project, String token) {
    Objects.requireNonNull(baseUrl, "baseUrl is required");
    Objects.requireNonNull(project, "project is required");
    Objects.requireNonNull(token, "token is required");
    String normalized =
        baseUrl.endsWith("/api") || baseUrl.endsWith("/api/")
            ? baseUrl.replaceAll("/$", "")
            : baseUrl.replaceAll("/$", "") + "/api";
    return builder()
        .project(project)
        .token(token)
        .space(baseUrl)
        .httpClient(HttpClient.withBaseUrl(normalized, project, token))
        .build();
  }

  // ── Builder ──────────────────────────────────────────────────────

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String project;
    private String token;
    private String space;
    private HttpClient httpClient;

    public Builder project(String project) {
      this.project = project;
      return this;
    }

    public Builder token(String token) {
      this.token = token;
      return this;
    }

    public Builder space(String space) {
      this.space = space;
      return this;
    }

    /**
     * Use a pre-built {@link HttpClient}. Useful when pointing the client at an explicit base URL
     * (e.g. via {@link HttpClient#withBaseUrl}).
     */
    Builder httpClient(HttpClient httpClient) {
      this.httpClient = httpClient;
      return this;
    }

    public RestClient build() {
      Objects.requireNonNull(project, "project is required");
      Objects.requireNonNull(token, "token is required");
      Objects.requireNonNull(space, "space is required");
      return new RestClient(this);
    }
  }

  // ── Accessors ────────────────────────────────────────────────────

  public String getProject() {
    return project;
  }

  public String getSpace() {
    return space;
  }

  public HttpClient getHttpClient() {
    return httpClient;
  }
}
