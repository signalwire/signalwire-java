/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

/**
 * Connection + credential options for {@link AIChatClient}.
 *
 * <p>Build with {@link #builder()}. Any unset field falls back to the standard environment
 * variables ({@code SIGNALWIRE_PROJECT_ID} / {@code SIGNALWIRE_API_TOKEN} / {@code
 * SIGNALWIRE_SPACE}); {@code url} (if set) is used verbatim and takes precedence over {@code
 * space}.
 */
public final class AIChatClientOptions {

  private final String project;
  private final String token;
  private final String space;
  private final String url;

  private AIChatClientOptions(Builder b) {
    this.project = b.project;
    this.token = b.token;
    this.space = b.space;
    this.url = b.url;
  }

  /**
   * A new builder.
   *
   * @return a builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Project id (Basic-auth username); falls back to {@code SIGNALWIRE_PROJECT_ID}.
   *
   * @return the project id, or {@code null}.
   */
  public String getProject() {
    return project;
  }

  /**
   * API token (Basic-auth password); falls back to {@code SIGNALWIRE_API_TOKEN}.
   *
   * @return the token, or {@code null}.
   */
  public String getToken() {
    return token;
  }

  /**
   * Space name; builds {@code https://{space}.signalwire.com/api/ai/chat}. Falls back to {@code
   * SIGNALWIRE_SPACE}.
   *
   * @return the space, or {@code null}.
   */
  public String getSpace() {
    return space;
  }

  /**
   * Fully-qualified endpoint URL, used verbatim (highest precedence).
   *
   * @return the url, or {@code null}.
   */
  public String getUrl() {
    return url;
  }

  /** Builder for {@link AIChatClientOptions}. */
  public static final class Builder {
    private String project;
    private String token;
    private String space;
    private String url;

    private Builder() {}

    /**
     * @param project the project id (Basic-auth username).
     * @return this builder.
     */
    public Builder project(String project) {
      this.project = project;
      return this;
    }

    /**
     * @param token the API token (Basic-auth password).
     * @return this builder.
     */
    public Builder token(String token) {
      this.token = token;
      return this;
    }

    /**
     * @param space the space name.
     * @return this builder.
     */
    public Builder space(String space) {
      this.space = space;
      return this;
    }

    /**
     * @param url the fully-qualified endpoint URL (highest precedence).
     * @return this builder.
     */
    public Builder url(String url) {
      this.url = url;
      return this;
    }

    /**
     * Build the options.
     *
     * @return the built options.
     */
    public AIChatClientOptions build() {
      return new AIChatClientOptions(this);
    }
  }
}
