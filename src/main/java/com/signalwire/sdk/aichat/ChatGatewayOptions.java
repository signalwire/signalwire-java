/*
 * Copyright (c) 2026 SignalWire
 *
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */
package com.signalwire.sdk.aichat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Construction options for {@link ChatGateway} — the Java named-parameter form of the reference's
 * keyword-only constructor. Build with {@link #builder(String)}; every field but {@code configUrl}
 * is optional and takes the reference's default.
 */
public final class ChatGatewayOptions {

  private final String configUrl;
  private final String key;
  private final List<String> allowedOrigins;
  private final AIChatClient client;
  private final byte[] secret;
  private final int handleTtl;
  private final Integer conversationTimeout;
  private final int maxNewConversations;
  private final int maxTurns;
  private final int windowSeconds;

  private ChatGatewayOptions(Builder b) {
    this.configUrl = b.configUrl;
    this.key = b.key;
    this.allowedOrigins = List.copyOf(b.allowedOrigins);
    this.client = b.client;
    this.secret = b.secret == null ? null : b.secret.clone();
    this.handleTtl = b.handleTtl;
    this.conversationTimeout = b.conversationTimeout;
    this.maxNewConversations = b.maxNewConversations;
    this.maxTurns = b.maxTurns;
    this.windowSeconds = b.windowSeconds;
  }

  /**
   * A new builder for a gateway scoped to {@code configUrl}.
   *
   * @param configUrl the agent this key may talk to; injected on every upstream call.
   * @return a builder.
   */
  public static Builder builder(String configUrl) {
    return new Builder(configUrl);
  }

  /**
   * @return the SWML config URL every upstream call carries.
   */
  public String getConfigUrl() {
    return configUrl;
  }

  /**
   * @return the publishable key, or {@code null} to take it from the environment / generate one.
   */
  public String getKey() {
    return key;
  }

  /**
   * @return the origins permitted besides localhost.
   */
  public List<String> getAllowedOrigins() {
    return allowedOrigins;
  }

  /**
   * @return the client to reuse, or {@code null} for one built from the environment.
   */
  public AIChatClient getClient() {
    return client;
  }

  /**
   * @return a copy of the handle-signing secret, or {@code null} for the environment / random.
   */
  public byte[] getSecret() {
    return secret == null ? null : secret.clone();
  }

  /**
   * @return seconds a signed handle stays valid.
   */
  public int getHandleTtl() {
    return handleTtl;
  }

  /**
   * @return idle seconds before the service ends a conversation, or {@code null}.
   */
  public Integer getConversationTimeout() {
    return conversationTimeout;
  }

  /**
   * @return cap on conversations minted per window.
   */
  public int getMaxNewConversations() {
    return maxNewConversations;
  }

  /**
   * @return cap on turns per conversation.
   */
  public int getMaxTurns() {
    return maxTurns;
  }

  /**
   * @return length of the rolling window the mint cap counts over.
   */
  public int getWindowSeconds() {
    return windowSeconds;
  }

  /** Builder for {@link ChatGatewayOptions}. */
  public static final class Builder {
    private final String configUrl;
    private String key;
    private final List<String> allowedOrigins = new ArrayList<>();
    private AIChatClient client;
    private byte[] secret;
    private int handleTtl = ChatGateway.DEFAULT_HANDLE_TTL;
    private Integer conversationTimeout;
    private int maxNewConversations = ChatGateway.DEFAULT_MAX_NEW_CONVERSATIONS;
    private int maxTurns = ChatGateway.DEFAULT_MAX_TURNS;
    private int windowSeconds = ChatGateway.DEFAULT_WINDOW_SECONDS;

    private Builder(String configUrl) {
      this.configUrl = configUrl;
    }

    /**
     * @param key the publishable key the browser presents.
     * @return this builder.
     */
    public Builder key(String key) {
      this.key = key;
      return this;
    }

    /**
     * @param origins origins permitted to call in (localhost is always allowed).
     * @return this builder.
     */
    public Builder allowedOrigins(Collection<String> origins) {
      this.allowedOrigins.clear();
      this.allowedOrigins.addAll(origins);
      return this;
    }

    /**
     * @param client an {@link AIChatClient} to reuse; the caller keeps ownership.
     * @return this builder.
     */
    public Builder client(AIChatClient client) {
      this.client = client;
      return this;
    }

    /**
     * @param secret HMAC key for signing conversation handles.
     * @return this builder.
     */
    public Builder secret(byte[] secret) {
      this.secret = secret == null ? null : secret.clone();
      return this;
    }

    /**
     * @param secret HMAC key for signing conversation handles, UTF-8 encoded.
     * @return this builder.
     */
    public Builder secret(String secret) {
      this.secret = secret == null ? null : secret.getBytes(StandardCharsets.UTF_8);
      return this;
    }

    /**
     * @param handleTtl seconds a signed handle stays valid.
     * @return this builder.
     */
    public Builder handleTtl(int handleTtl) {
      this.handleTtl = handleTtl;
      return this;
    }

    /**
     * @param conversationTimeout idle seconds before the service ends a conversation.
     * @return this builder.
     */
    public Builder conversationTimeout(Integer conversationTimeout) {
      this.conversationTimeout = conversationTimeout;
      return this;
    }

    /**
     * @param maxNewConversations cap on conversations minted per window.
     * @return this builder.
     */
    public Builder maxNewConversations(int maxNewConversations) {
      this.maxNewConversations = maxNewConversations;
      return this;
    }

    /**
     * @param maxTurns cap on turns per conversation.
     * @return this builder.
     */
    public Builder maxTurns(int maxTurns) {
      this.maxTurns = maxTurns;
      return this;
    }

    /**
     * @param windowSeconds length of the rolling window the mint cap counts over.
     * @return this builder.
     */
    public Builder windowSeconds(int windowSeconds) {
      this.windowSeconds = windowSeconds;
      return this;
    }

    /**
     * Build the options.
     *
     * @return the options.
     */
    public ChatGatewayOptions build() {
      return new ChatGatewayOptions(this);
    }
  }
}
