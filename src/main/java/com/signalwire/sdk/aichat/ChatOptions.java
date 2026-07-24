/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import java.util.Map;

/**
 * Options for {@link AIChatClient#chat}. All fields are optional. Build with {@link #builder()}.
 */
public final class ChatOptions {

  private final String role;
  private final String configUrl;
  private final Integer timeout;
  private final Boolean reinit;
  private final Map<String, Object> userMetadata;

  private ChatOptions(Builder b) {
    this.role = b.role != null ? b.role : "user";
    this.configUrl = b.configUrl;
    this.timeout = b.timeout;
    this.reinit = b.reinit;
    this.userMetadata = b.userMetadata;
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
   * Message role ({@code "user"} or {@code "system"}); default {@code "user"} (wire {@code role}).
   *
   * @return the role.
   */
  public String getRole() {
    return role;
  }

  /**
   * Config URL that auto-creates the conversation if absent (wire {@code config_url}).
   *
   * @return the config URL, or {@code null}.
   */
  public String getConfigUrl() {
    return configUrl;
  }

  /**
   * Conversation inactivity timeout in seconds for the auto-create (wire {@code
   * conversation_timeout}).
   *
   * @return the timeout, or {@code null}.
   */
  public Integer getTimeout() {
    return timeout;
  }

  /**
   * Whether to reinitialize an existing conversation on auto-create (wire {@code reinit}).
   *
   * @return {@code true} to reinit.
   */
  public boolean isReinit() {
    return reinit != null && reinit;
  }

  /**
   * Arbitrary caller metadata (wire {@code user_meta_data}).
   *
   * @return the metadata map, or {@code null}.
   */
  public Map<String, Object> getUserMetadata() {
    return userMetadata;
  }

  /** Builder for {@link ChatOptions}. */
  public static final class Builder {
    private String role;
    private String configUrl;
    private Integer timeout;
    private Boolean reinit;
    private Map<String, Object> userMetadata;

    private Builder() {}

    /**
     * @param role the message role ({@code "user"} or {@code "system"}).
     * @return this builder.
     */
    public Builder role(String role) {
      this.role = role;
      return this;
    }

    /**
     * @param configUrl config URL that auto-creates the conversation if absent.
     * @return this builder.
     */
    public Builder configUrl(String configUrl) {
      this.configUrl = configUrl;
      return this;
    }

    /**
     * @param timeout the conversation inactivity timeout in seconds.
     * @return this builder.
     */
    public Builder timeout(Integer timeout) {
      this.timeout = timeout;
      return this;
    }

    /**
     * @param reinit whether to reinitialize an existing conversation.
     * @return this builder.
     */
    public Builder reinit(boolean reinit) {
      this.reinit = reinit;
      return this;
    }

    /**
     * @param userMetadata arbitrary caller metadata.
     * @return this builder.
     */
    public Builder userMetadata(Map<String, Object> userMetadata) {
      this.userMetadata = userMetadata;
      return this;
    }

    /**
     * Build the options.
     *
     * @return the built options.
     */
    public ChatOptions build() {
      return new ChatOptions(this);
    }
  }
}
