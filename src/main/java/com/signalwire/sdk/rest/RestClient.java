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
            : new HttpClient(builder.space, builder.project, builder.token, builder.requestOptions);
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
    private RequestOptions requestOptions;

    public Builder project(String project) {
      this.project = project;
      return this;
    }

    /**
     * Client-default {@link RequestOptions} (timeout / retries / retry policy / abort signal)
     * applied to every request this client makes, overridable per call. Mirrors the Python
     * reference's {@code RestClient(request_options=...)}.
     */
    public Builder requestOptions(RequestOptions requestOptions) {
      this.requestOptions = requestOptions;
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
      // Env-var fallback for any credential not set explicitly — parity with
      // Python's RestClient() (rest/client.py), which reads SIGNALWIRE_PROJECT_ID
      // / SIGNALWIRE_API_TOKEN / SIGNALWIRE_SPACE from the environment when the
      // corresponding argument is absent. A builder with no explicit creds is a
      // supported usage ("reads env vars automatically"), not an error.
      if (project == null) {
        project = envOrNull("SIGNALWIRE_PROJECT_ID");
      }
      if (token == null) {
        token = envOrNull("SIGNALWIRE_API_TOKEN");
      }
      if (space == null) {
        space = envOrNull("SIGNALWIRE_SPACE");
      }
      if (project == null || token == null || space == null) {
        throw new IllegalArgumentException(
            "project, token, and space are required. Provide them via the builder or set "
                + "SIGNALWIRE_PROJECT_ID, SIGNALWIRE_API_TOKEN, and SIGNALWIRE_SPACE "
                + "environment variables.");
      }
      return new RestClient(this);
    }

    private static String envOrNull(String key) {
      String v = System.getenv(key);
      return (v != null && !v.isEmpty()) ? v : null;
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
